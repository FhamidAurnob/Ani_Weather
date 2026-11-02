package com.example.wuwaweather.prayer

import com.example.wuwaweather.i18n.tr
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Calculation methods (twilight angles for Fajr and Isha). */
enum class PrayerMethod(private val en: String, private val bn: String, val fajrAngle: Double, val ishaAngle: Double?, val ishaMinutes: Int?) {
    KARACHI("Karachi (Islamic Foundation BD)", "করাচি (ইসলামিক ফাউন্ডেশন বাংলাদেশ)", 18.0, 18.0, null),
    MWL("Muslim World League", "মুসলিম ওয়ার্ল্ড লীগ", 18.0, 17.0, null),
    ISNA("ISNA (North America)", "ইসনা (উত্তর আমেরিকা)", 15.0, 15.0, null),
    EGYPT("Egyptian Authority", "মিশরীয় কর্তৃপক্ষ", 19.5, 17.5, null),
    UMM_AL_QURA("Umm al-Qura (Makkah)", "উম্মুল কুরা (মক্কা)", 18.5, null, 90);

    val label: String get() = tr(en, bn)
}

/** Asr shadow factor: Shafi'i (and Maliki/Hanbali) = 1, Hanafi = 2. */
enum class Madhab(private val en: String, private val bn: String, val asrFactor: Int) {
    HANAFI("Hanafi", "হানাফি", 2),
    SHAFI("Shafi'i", "শাফেয়ি", 1);

    val label: String get() = tr(en, bn)
}

enum class Prayer(private val en: String, private val bn: String) {
    FAJR("Fajr", "ফজর"), SUNRISE("Sunrise", "সূর্যোদয়"), DHUHR("Dhuhr", "যোহর"),
    ASR("Asr", "আসর"), MAGHRIB("Maghrib", "মাগরিব"), ISHA("Isha", "এশা");

    val label: String get() = tr(en, bn)
}

/** Prayer times for one day as epoch milliseconds (UTC instants). */
data class PrayerDay(val date: LocalDate, val times: Map<Prayer, Long>) {
    operator fun get(p: Prayer): Long = times.getValue(p)
}

/**
 * Astronomical prayer-time calculation (the well-known PrayTimes.org algorithm).
 * Works fully offline. Accuracy is typically within a minute of official tables.
 */
object PrayerCalculator {

    fun calculate(
        date: LocalDate,
        lat: Double,
        lon: Double,
        tzOffsetSeconds: Int,
        method: PrayerMethod,
        madhab: Madhab
    ): PrayerDay {
        val tz = tzOffsetSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - lon / (15.0 * 24.0)

        // Initial guesses as fractions of a day, then one refinement pass
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                sunAngleTime(jDate, lat, method.fajrAngle, p[0], ccw = true),
                sunAngleTime(jDate, lat, RISE_SET_ANGLE, p[1], ccw = true),
                midDay(jDate, p[2]),
                asrTime(jDate, lat, madhab.asrFactor.toDouble(), p[3]),
                sunAngleTime(jDate, lat, RISE_SET_ANGLE, p[4], ccw = false),
                sunAngleTime(jDate, lat, RISE_SET_ANGLE, p[5], ccw = false),
                if (method.ishaAngle != null) sunAngleTime(jDate, lat, method.ishaAngle, p[6], ccw = false) else 18.0
            )
        }

        // Shift from local solar time to the city's clock time
        val adj = t.map { it + tz - lon / 15.0 }.toMutableList()

        // Isha as a fixed interval after Maghrib (Umm al-Qura)
        if (method.ishaMinutes != null) adj[6] = adj[5] + method.ishaMinutes / 60.0

        // High-latitude safety net (angle-based) when twilight never reaches the angle
        val night = timeDiff(adj[4], adj[1])
        adj[0] = adjustHighLat(adj[0], adj[1], method.fajrAngle, night, ccw = true)
        if (method.ishaAngle != null) adj[6] = adjustHighLat(adj[6], adj[4], method.ishaAngle, night, ccw = false)

        // Common safety margins used by most timetables
        adj[2] += 1 / 60.0   // Dhuhr: 1 min after zenith
        adj[5] += 1 / 60.0   // Maghrib: 1 min after sunset

        val midnightUtcMillis = date.toEpochDay() * 86_400_000L - tzOffsetSeconds * 1000L
        fun toMillis(hours: Double) = midnightUtcMillis + Math.round(hours * 3_600_000.0)

        return PrayerDay(
            date,
            mapOf(
                Prayer.FAJR to toMillis(adj[0]),
                Prayer.SUNRISE to toMillis(adj[1]),
                Prayer.DHUHR to toMillis(adj[2]),
                Prayer.ASR to toMillis(adj[3]),
                Prayer.MAGHRIB to toMillis(adj[5]),
                Prayer.ISHA to toMillis(adj[6]),
            )
        )
    }

    private const val RISE_SET_ANGLE = 0.833

    // ---------- astronomy ----------

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Returns (declination degrees, equation of time hours). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(dAtan2(dCos(e) * dSin(l), dCos(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = dAsin(dSin(e) * dSin(l))
        return decl to eqt
    }

    private fun midDay(jDate: Double, t: Double): Double {
        val eqt = sunPosition(jDate + t).second
        return fixHour(12 - eqt)
    }

    private fun sunAngleTime(jDate: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + t).first
        val noon = midDay(jDate, t)
        val x = (-dSin(angle) - dSin(decl) * dSin(lat)) / (dCos(decl) * dCos(lat))
        if (x < -1 || x > 1) return Double.NaN
        val tt = dAcos(x) / 15.0
        return noon + if (ccw) -tt else tt
    }

    private fun asrTime(jDate: Double, lat: Double, factor: Double, t: Double): Double {
        val decl = sunPosition(jDate + t).first
        val angle = -dAcot(factor + dTan(abs(lat - decl)))
        return sunAngleTime(jDate, lat, angle, t, ccw = false)
    }

    private fun adjustHighLat(time: Double, base: Double, angle: Double, night: Double, ccw: Boolean): Double {
        val portion = angle / 60.0 * night
        val diff = if (ccw) timeDiff(time, base) else timeDiff(base, time)
        return if (time.isNaN() || diff > portion) base + if (ccw) -portion else portion else time
    }

    private fun timeDiff(t1: Double, t2: Double) = fixHour(t2 - t1)

    private fun dSin(d: Double) = sin(Math.toRadians(d))
    private fun dCos(d: Double) = cos(Math.toRadians(d))
    private fun dTan(d: Double) = tan(Math.toRadians(d))
    private fun dAsin(x: Double) = Math.toDegrees(asin(x))
    private fun dAcos(x: Double) = Math.toDegrees(acos(x))
    private fun dAtan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun dAcot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)
}
