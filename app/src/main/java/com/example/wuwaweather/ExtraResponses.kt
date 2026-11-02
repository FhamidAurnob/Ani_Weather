package com.example.wuwaweather

import com.google.gson.annotations.SerializedName

/** OpenWeather `/air_pollution`. */
data class AirPollutionResponse(val list: List<AirItem>?)

data class AirItem(
    val main: AirMain?,
    val components: AirComponents?
)

data class AirMain(val aqi: Int?)          // 1 = Good … 5 = Very poor

data class AirComponents(
    @SerializedName("pm2_5") val pm25: Double?,
    val pm10: Double?
)

/** Open-Meteo UV index. */
data class OpenMeteoUvResponse(
    val current: UvCurrent?,
    val daily: UvDaily?
)

data class UvCurrent(@SerializedName("uv_index") val uvIndex: Double?)

data class UvDaily(@SerializedName("uv_index_max") val uvIndexMax: List<Double?>?)

/** OpenWeather geocoding result. */
data class GeoResult(
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,   // ISO code, e.g. "BD"
    val state: String?
)
