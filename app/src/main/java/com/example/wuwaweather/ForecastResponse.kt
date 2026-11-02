package com.example.wuwaweather

import com.google.gson.annotations.SerializedName

/** Response of OpenWeather `/forecast` (5 days, 3-hour steps). Fields are nullable for safety. */
data class ForecastResponse(
    val list: List<ForecastItem>?,
    val city: ForecastCity?
)

data class ForecastItem(
    val dt: Long,
    val main: ForecastMain?,
    val weather: List<Weather>?,
    val pop: Double?,            // probability of precipitation, 0..1
    val sys: ForecastSys?
)

data class ForecastMain(
    val temp: Double,
    @SerializedName("temp_min") val tempMin: Double,
    @SerializedName("temp_max") val tempMax: Double
)

data class ForecastSys(
    val pod: String?             // "d" = day, "n" = night
)

data class ForecastCity(
    val name: String?,
    val country: String?,
    val timezone: Int?,
    val sunrise: Long?,
    val sunset: Long?
)
