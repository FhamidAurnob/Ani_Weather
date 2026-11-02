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

/** Open-Meteo extras: UV, 15-minute precipitation and hourly storm signals (unix times). */
data class OpenMeteoUvResponse(
    val current: UvCurrent?,
    val daily: UvDaily?,
    @SerializedName("minutely_15") val minutely15: Minutely15?,
    val hourly: HourlyExtras?
)

data class UvCurrent(
    @SerializedName("uv_index") val uvIndex: Double?,
    val precipitation: Double?
)

data class Minutely15(
    val time: List<Long>?,
    val precipitation: List<Double?>?
)

data class HourlyExtras(
    val time: List<Long>?,
    val precipitation: List<Double?>?,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int?>?,
    @SerializedName("weather_code") val weatherCode: List<Int?>?,
    @SerializedName("wind_gusts_10m") val windGusts: List<Double?>?,
    @SerializedName("apparent_temperature") val apparentTemperature: List<Double?>?
)

data class UvDaily(@SerializedName("uv_index_max") val uvIndexMax: List<Double?>?)

/** OpenWeather geocoding result. */
data class GeoResult(
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    val country: String?,   // ISO code, e.g. "BD"
    val state: String?
)
