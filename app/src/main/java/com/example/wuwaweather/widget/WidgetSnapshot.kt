package com.example.wuwaweather.widget

import android.content.Context
import com.example.wuwaweather.data.Condition
import com.example.wuwaweather.data.Place
import com.example.wuwaweather.data.UnitSystem
import com.example.wuwaweather.data.WeatherBundle
import com.example.wuwaweather.data.WeatherRepository
import com.google.gson.Gson
import kotlinx.coroutines.withTimeoutOrNull

/** The small slice of weather the widget shows, cached in SharedPreferences. */
data class WidgetSnapshot(
    val city: String,
    val temp: Int,
    val high: Int,
    val low: Int,
    val description: String,
    val condition: String,      // Condition name
    val isDay: Boolean,
    val units: String,          // UnitSystem name
    val lat: Double,
    val lon: Double,
    val savedAt: Long
) {
    val conditionEnum: Condition get() = runCatching { Condition.valueOf(condition) }.getOrDefault(Condition.CLEAR)

    companion object {
        private const val PREFS = "velora_widget"
        private const val KEY = "snapshot"
        private const val MAX_AGE_MS = 30 * 60 * 1000L
        private val gson = Gson()

        fun from(bundle: WeatherBundle): WidgetSnapshot {
            val c = bundle.current
            return WidgetSnapshot(
                city = c.city, temp = c.temp, high = c.tempMax, low = c.tempMin,
                description = c.description, condition = c.condition.name, isDay = c.isDay,
                units = bundle.units.name, lat = c.lat, lon = c.lon,
                savedAt = System.currentTimeMillis()
            )
        }

        fun save(context: Context, snapshot: WidgetSnapshot) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, gson.toJson(snapshot)).apply()
        }

        fun load(context: Context): WidgetSnapshot? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?.let { runCatching { gson.fromJson(it, WidgetSnapshot::class.java) }.getOrNull() }

        /** Returns the cached snapshot, refreshing it first if it's older than 30 minutes. */
        suspend fun loadFresh(context: Context): WidgetSnapshot? {
            val cached = load(context)
            if (cached != null && System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS) return cached
            val units = runCatching { UnitSystem.valueOf(cached?.units ?: "") }.getOrDefault(UnitSystem.METRIC)
            val place: Place = if (cached != null) Place.Coords(cached.lat, cached.lon) else Place.City("Dhaka")
            val fresh = withTimeoutOrNull(15_000) {
                runCatching { WeatherRepository().load(place, units) }.getOrNull()
            }?.let { from(it) }
            if (fresh != null) save(context, fresh)
            return fresh ?: cached
        }
    }
}
