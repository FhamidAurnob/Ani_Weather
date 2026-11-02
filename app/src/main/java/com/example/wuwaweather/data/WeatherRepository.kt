package com.example.wuwaweather.data

import com.example.wuwaweather.BuildConfig
import com.example.wuwaweather.ForecastItem
import com.example.wuwaweather.ForecastResponse
import com.example.wuwaweather.WeatherApi
import com.example.wuwaweather.WeatherClient
import com.example.wuwaweather.i18n.I18n
import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Thrown with a message that is safe to show to the user. */
class WeatherException(message: String) : Exception(message)

class WeatherRepository {

    private val api = WeatherClient.api
    private val openMeteo = WeatherClient.openMeteo
    private val key = BuildConfig.OPENWEATHER_API_KEY

    suspend fun load(place: Place, units: UnitSystem): WeatherBundle {
        if (key.isBlank()) throw WeatherException(tr("API key missing. Add OPENWEATHER_API_KEY to local.properties.", "API কী নেই। local.properties-এ OPENWEATHER_API_KEY যোগ করুন।"))
        val city = (place as? Place.City)?.name
        val lat = (place as? Place.Coords)?.lat
        val lon = (place as? Place.Coords)?.lon
        val label = city ?: tr("your location", "আপনার অবস্থান")

        return try {
            coroutineScope {
                // Current + forecast in parallel
                val currentCall = async { api.getWeatherData(city = city, lat = lat, lon = lon, apiKey = key) }
                // Extras shouldn't hide the main weather if they fail
                val forecastCall = async {
                    runCatching { api.getForecast(city = city, lat = lat, lon = lon, apiKey = key).body() }.getOrNull()
                }
                val current = currentCall.await().bodyOrThrow(label)

                // Air quality + UV need coordinates, which the current response gives us
                val cLat = current.coord.lat
                val cLon = current.coord.lon
                val airCall = async { runCatching { api.getAirPollution(cLat, cLon, key).body() }.getOrNull() }
                val extrasCall = async { runCatching { openMeteo.getExtras(cLat, cLon).body() }.getOrNull() }

                val airItem = airCall.await()?.list?.firstOrNull()
                val air = airItem?.main?.aqi?.let { AirQuality(it, airItem.components?.pm25) }
                val extras = extrasCall.await()
                val uv = extras?.current?.uvIndex?.let { UvInfo(it, extras.daily?.uvIndexMax?.firstOrNull()) }
                val nowcast = NowcastAnalyzer.nowcast(extras, System.currentTimeMillis() / 1000, current.timezone)

                buildBundle(current, forecastCall.await(), air, uv, units).copy(nowcast = nowcast)
            }
        } catch (e: IOException) {
            throw WeatherException(tr("No internet connection", "ইন্টারনেট সংযোগ নেই"))
        }
    }

    private fun <T> Response<T>.bodyOrThrow(label: String): T {
        val body = body()
        if (isSuccessful && body != null) return body
        throw WeatherException(
            when (code()) {
                404 -> tr("Couldn't find \"$label\"", "\"$label\" খুঁজে পাওয়া যায়নি")
                401 -> tr("Invalid API key", "API কী সঠিক নয়")
                429 -> tr("Too many requests. Try again in a minute.", "অনেক বেশি অনুরোধ। এক মিনিট পর আবার চেষ্টা করুন।")
                else -> tr("Couldn't load weather (error ${code()})", "আবহাওয়া লোড করা যায়নি (ত্রুটি ${code()})")
            }
        )
    }

    private fun buildBundle(
        res: WeatherApi,
        forecast: ForecastResponse?,
        air: AirQuality?,
        uv: UvInfo?,
        units: UnitSystem
    ): WeatherBundle {
        val imperial = units == UnitSystem.IMPERIAL
        fun t(celsius: Double): Int = (if (imperial) celsius * 9 / 5 + 32 else celsius).roundToInt()
        val tz = res.timezone
        val w = res.weather.firstOrNull()
        val current = CurrentWeather(
            city = res.name,
            country = res.sys.country,
            temp = t(res.main.temp),
            feelsLike = t(res.main.feels_like),
            tempMin = t(res.main.temp_min),
            tempMax = t(res.main.temp_max),
            description = (if (I18n.bangla) BanglaWeather.describe(w?.id) else null)
                ?: (w?.description ?: w?.main ?: "—").replaceFirstChar { it.titlecase(Locale.getDefault()) },
            condition = Condition.fromId(w?.id, w?.main),
            isDay = res.dt in res.sys.sunrise until res.sys.sunset,
            humidity = res.main.humidity,
            pressure = res.main.pressure,
            windSpeed = if (imperial) res.wind.speed * 2.23694 else res.wind.speed,
            windDeg = res.wind.deg,
            visibility = (res.visibility / 1000.0).let { if (imperial) it * 0.621371 else it },
            lat = res.coord.lat,
            lon = res.coord.lon,
            cloudiness = res.clouds?.all ?: 0,
            sunrise = res.sys.sunrise.toLong(),
            sunset = res.sys.sunset.toLong(),
            observedAt = res.dt.toLong(),
            tzOffsetSeconds = tz
        )

        val items = forecast?.list.orEmpty().filter { it.main != null }
        return WeatherBundle(
            current = current,
            hourly = buildHourly(current, items, tz, ::t),
            daily = buildDaily(current, items, tz, ::t),
            air = air,
            uv = uv,
            units = units,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    private fun buildHourly(
        current: CurrentWeather,
        items: List<ForecastItem>,
        tz: Int,
        t: (Double) -> Int
    ): List<HourlyItem> {
        val now = HourlyItem(tr("Now", "এখন"), current.temp, current.condition, current.isDay, 0)
        val next = items.take(8).map { item ->
            val w = item.weather?.firstOrNull()
            HourlyItem(
                label = format(item.dt, tz, "HH:mm"),
                temp = t(item.main!!.temp),
                condition = Condition.fromId(w?.id, w?.main),
                isDay = item.sys?.pod != "n",
                popPercent = ((item.pop ?: 0.0) * 100).roundToInt()
            )
        }
        return listOf(now) + next
    }

    private fun buildDaily(
        current: CurrentWeather,
        items: List<ForecastItem>,
        tz: Int,
        t: (Double) -> Int
    ): List<DailyItem> {
        val todayKey = format(current.observedAt, tz, "yyyyMMdd", Locale.US)
        val byDay = items.groupBy { format(it.dt, tz, "yyyyMMdd", Locale.US) }.toSortedMap()

        return byDay.entries.take(6).map { (dayKey, slots) ->
            // Representative condition: the slot closest to 13:00 local time
            val rep = slots.minByOrNull { abs(format(it.dt, tz, "HH", Locale.US).toInt() - 13) }!!
            val w = rep.weather?.firstOrNull()
            var min = t(slots.minOf { it.main!!.tempMin })
            var max = t(slots.maxOf { it.main!!.tempMax })
            val isToday = dayKey == todayKey
            if (isToday) {
                min = minOf(min, current.tempMin, current.temp)
                max = maxOf(max, current.tempMax, current.temp)
            }
            DailyItem(
                label = if (isToday) tr("Today", "আজ") else format(slots.first().dt, tz, "EEE"),
                min = min,
                max = max,
                condition = if (isToday) current.condition else Condition.fromId(w?.id, w?.main),
                popPercent = ((slots.maxOf { it.pop ?: 0.0 }) * 100).roundToInt()
            )
        }.take(5)
    }

    companion object {
        /** Formats a UTC epoch-seconds timestamp in a city's local time. */
        fun format(
            epochSeconds: Long,
            tzOffsetSeconds: Int,
            pattern: String,
            locale: Locale = I18n.locale
        ): String {
            val sdf = SimpleDateFormat(pattern, locale)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val out = sdf.format(Date((epochSeconds + tzOffsetSeconds) * 1000))
            // Keys (Locale.US) stay ASCII; display strings get Bangla digits when needed
            return if (locale == Locale.US) out else out.localDigits()
        }
    }
}
