package com.example.wuwaweather.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** "Home" = your location page (or first saved city). Used by prayer times, maps and quake alerts. */
data class Home(val name: String, val lat: Double, val lon: Double, val tzOffsetSeconds: Int)

object HomeStore {
    private const val PREFS = "velora_home"
    private val _home = MutableStateFlow<Home?>(null)
    val home: StateFlow<Home?> = _home.asStateFlow()

    /** Loads the saved home once (call from any entry point: activity, receiver, worker). */
    fun get(context: Context): Home? {
        if (_home.value == null) _home.value = load(context)
        return _home.value
    }

    fun update(context: Context, home: Home) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", home.name)
            .putString("lat", home.lat.toString())
            .putString("lon", home.lon.toString())
            .putInt("tz", home.tzOffsetSeconds)
            .apply()
        _home.value = home
    }

    private fun load(context: Context): Home? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        return Home(p.getString("name", "Home") ?: "Home", lat, lon, p.getInt("tz", 6 * 3600))
    }
}
