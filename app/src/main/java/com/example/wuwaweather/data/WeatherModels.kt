package com.example.wuwaweather.data

/** The visual "family" of a weather condition. Drives the sky scene and the icons. */
enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDS, DRIZZLE, RAIN, THUNDER, SNOW, FOG;

    companion object {
        /** Maps OpenWeather condition ids (https://openweathermap.org/weather-conditions). */
        fun fromId(id: Int?, main: String? = null): Condition = when (id) {
            in 200..299 -> THUNDER
            in 300..399 -> DRIZZLE
            in 500..599 -> RAIN
            in 600..699 -> SNOW
            in 700..799 -> FOG
            800 -> CLEAR
            801, 802 -> PARTLY_CLOUDY
            in 803..899 -> CLOUDS
            else -> when (main) {
                "Thunderstorm" -> THUNDER
                "Drizzle" -> DRIZZLE
                "Rain" -> RAIN
                "Snow" -> SNOW
                "Clouds" -> CLOUDS
                "Clear" -> CLEAR
                else -> FOG
            }
        }
    }
}

/** Where to load weather for. */
sealed interface Place {
    data class City(val name: String) : Place
    data class Coords(val lat: Double, val lon: Double) : Place
}

/** Metric = °C, m/s, km. Imperial = °F, mph, mi. */
enum class UnitSystem(val temp: String, val wind: String, val distance: String) {
    METRIC("°C", "m/s", "km"),
    IMPERIAL("°F", "mph", "mi")
}

data class CurrentWeather(
    val city: String,
    val country: String?,
    val temp: Int,
    val feelsLike: Int,
    val tempMin: Int,
    val tempMax: Int,
    val description: String,
    val condition: Condition,
    val isDay: Boolean,
    val humidity: Int,
    val pressure: Int,
    val windSpeed: Double,      // in the bundle's unit system
    val windDeg: Int,
    val visibility: Double,     // km or miles
    val lat: Double,
    val lon: Double,
    val cloudiness: Int,
    val sunrise: Long,          // epoch seconds (UTC)
    val sunset: Long,
    val observedAt: Long,
    val tzOffsetSeconds: Int
)

data class HourlyItem(
    val label: String,          // "Now", "15:00"
    val temp: Int,
    val condition: Condition,
    val isDay: Boolean,
    val popPercent: Int
)

data class DailyItem(
    val label: String,          // "Today", "Tue"
    val min: Int,
    val max: Int,
    val condition: Condition,
    val popPercent: Int
)

/** OpenWeather air quality: index 1 (Good) … 5 (Very poor). */
data class AirQuality(val index: Int, val pm25: Double?)

data class UvInfo(val now: Double, val todayMax: Double?)

data class WeatherBundle(
    val current: CurrentWeather,
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>,
    val air: AirQuality?,
    val uv: UvInfo?,
    val units: UnitSystem,
    val fetchedAtMillis: Long
)
