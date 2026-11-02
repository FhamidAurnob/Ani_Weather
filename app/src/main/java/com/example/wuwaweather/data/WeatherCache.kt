package com.example.wuwaweather.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Last successful weather per place, so the app can show something instantly on launch. */
class WeatherCache(context: Context) {
    private val file = File(context.filesDir, "weather_cache.json")
    private val gson = Gson()
    private var map: MutableMap<String, WeatherBundle> = load()

    fun get(key: String): WeatherBundle? = map[key]

    @Synchronized
    fun put(key: String, bundle: WeatherBundle) {
        map[key] = bundle
        runCatching { file.writeText(gson.toJson(map)) }
    }

    @Synchronized
    fun retain(keys: Collection<String>) {
        if (map.keys.retainAll(keys.toSet())) runCatching { file.writeText(gson.toJson(map)) }
    }

    private fun load(): MutableMap<String, WeatherBundle> = runCatching {
        if (!file.exists()) return@runCatching mutableMapOf()
        val type = object : TypeToken<MutableMap<String, WeatherBundle>>() {}.type
        gson.fromJson<MutableMap<String, WeatherBundle>>(file.readText(), type) ?: mutableMapOf()
    }.getOrDefault(mutableMapOf())
}
