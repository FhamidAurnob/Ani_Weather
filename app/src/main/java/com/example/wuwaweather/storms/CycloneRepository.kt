package com.example.wuwaweather.storms

import com.example.wuwaweather.quakes.QuakeRepository
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ---------- GDACS (Global Disaster Alert and Coordination System) models ----------
data class GdacsCollection(val features: List<GdacsFeature>?)
data class GdacsFeature(val properties: GdacsProps?, val geometry: GdacsGeometry?)
data class GdacsProps(
    val eventid: Long?,
    val eventname: String?,
    val alertlevel: String?,      // Green / Orange / Red
    val fromdate: String?,
    val todate: String?,
    val iscurrent: String?,       // "true"/"false"
    val country: String?,
    val severitydata: GdacsSeverity?
)
data class GdacsSeverity(val severity: Double?, val severitytext: String?, val severityunit: String?)
data class GdacsGeometry(val coordinates: List<Double>?)

interface GdacsApi {
    @GET("gdacsapi/api/events/geteventlist/SEARCH")
    suspend fun search(
        @Query("eventlist") eventList: String = "TC",
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageNumber") pageNumber: Int = 1
    ): Response<GdacsCollection>
}

/** An active tropical cyclone ready for the UI. */
data class Cyclone(
    val id: Long,
    val name: String,
    val alertLevel: String,   // Green / Orange / Red
    val severity: String?,
    val fromDate: String?,
    val toDate: String?,
    val countries: String?,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double?
) {
    val reportUrl: String get() = "https://www.gdacs.org/report.aspx?eventtype=TC&eventid=$id"
    /** Roughly the Bay of Bengal / north Indian Ocean basin. */
    val inBayOfBengal: Boolean get() = lat in 0.0..25.0 && lon in 78.0..100.0
}

object CycloneRepository {
    private val api: GdacsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.gdacs.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GdacsApi::class.java)
    }

    /** Current tropical cyclones worldwide, nearest first. */
    suspend fun active(homeLat: Double?, homeLon: Double?): List<Cyclone> {
        val res = api.search()
        val body = res.body()
        if (!res.isSuccessful || body == null) throw IllegalStateException("GDACS error ${res.code()}")
        return body.features.orEmpty().mapNotNull { f ->
            val p = f.properties ?: return@mapNotNull null
            if (p.iscurrent?.equals("true", ignoreCase = true) != true) return@mapNotNull null
            val c = f.geometry?.coordinates ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            Cyclone(
                id = p.eventid ?: return@mapNotNull null,
                name = p.eventname?.takeIf { it.isNotBlank() } ?: "Tropical cyclone",
                alertLevel = p.alertlevel ?: "Green",
                severity = p.severitydata?.severitytext,
                fromDate = p.fromdate?.take(10),
                toDate = p.todate?.take(10),
                countries = p.country?.takeIf { it.isNotBlank() },
                lat = c[1], lon = c[0],
                distanceKm = if (homeLat != null && homeLon != null) QuakeRepository.distanceKm(homeLat, homeLon, c[1], c[0]) else null
            )
        }.distinctBy { it.id }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }
}
