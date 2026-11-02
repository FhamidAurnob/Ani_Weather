package com.example.wuwaweather.quakes

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---------- USGS GeoJSON models ----------
data class QuakeCollection(val features: List<QuakeFeature>?)
data class QuakeFeature(val id: String?, val properties: QuakeProps?, val geometry: QuakeGeometry?)
data class QuakeProps(val mag: Double?, val place: String?, val time: Long?, val url: String?, val tsunami: Int?, val felt: Int?)
data class QuakeGeometry(val coordinates: List<Double>?)   // [lon, lat, depthKm]

interface UsgsApi {
    @GET("earthquakes/feed/v1.0/summary/{feed}.geojson")
    suspend fun feed(@Path("feed") feed: String): Response<QuakeCollection>
}

/** A cleaned-up earthquake ready for the UI. */
data class Quake(
    val id: String,
    val magnitude: Double,
    val place: String,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val depthKm: Double,
    val url: String?,
    val tsunami: Boolean,
    val distanceKm: Double?
)

/** USGS real-time feeds (free, no key). */
enum class QuakeFeed(val path: String, private val en: String, private val bn: String) {
    DAY("2.5_day", "Past day · M2.5+", "গত ২৪ ঘণ্টা · M2.5+"),
    WEEK("4.5_week", "Past week · M4.5+", "গত সপ্তাহ · M4.5+"),
    SIGNIFICANT("significant_month", "Significant · 30 days", "উল্লেখযোগ্য · ৩০ দিন");

    val label: String get() = com.example.wuwaweather.i18n.tr(en, bn)
}

object QuakeRepository {
    private val api: UsgsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://earthquake.usgs.gov/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsgsApi::class.java)
    }

    suspend fun load(feed: QuakeFeed, homeLat: Double?, homeLon: Double?): List<Quake> {
        val res = api.feed(feed.path)
        val body = res.body()
        if (!res.isSuccessful || body == null) throw IllegalStateException("USGS error ${res.code()}")
        return body.features.orEmpty().mapNotNull { f ->
            val p = f.properties ?: return@mapNotNull null
            val c = f.geometry?.coordinates ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            val lat = c[1]
            val lon = c[0]
            Quake(
                id = f.id ?: return@mapNotNull null,
                magnitude = p.mag ?: return@mapNotNull null,
                place = p.place ?: "Unknown location",
                time = p.time ?: 0L,
                lat = lat, lon = lon,
                depthKm = c.getOrNull(2) ?: 0.0,
                url = p.url,
                tsunami = p.tsunami == 1,
                distanceKm = if (homeLat != null && homeLon != null) distanceKm(homeLat, homeLon, lat, lon) else null
            )
        }.sortedByDescending { it.time }
    }

    /** Great-circle distance (haversine). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
