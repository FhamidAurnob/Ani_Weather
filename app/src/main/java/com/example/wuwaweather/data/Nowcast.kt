package com.example.wuwaweather.data

import com.example.wuwaweather.OpenMeteoUvResponse
import com.example.wuwaweather.i18n.tr

/** One 15-minute slot of the rain nowcast. */
data class NowcastSlot(val epochSeconds: Long, val precipMm: Double)

/** Next ~2 hours of rain, plus a one-line summary. */
data class Nowcast(val slots: List<NowcastSlot>, val summary: String, val rainSoonAt: Long?)

/** A severe-weather signal found in the next 12 hours. */
data class SevereSignal(val kind: Kind, val atEpochSeconds: Long, val value: Double) {
    enum class Kind { HEAVY_RAIN, THUNDER, STRONG_WIND }
}

object NowcastAnalyzer {
    private const val WET_MM = 0.1               // per 15 min
    const val HEAVY_RAIN_MM_PER_HOUR = 10.0
    const val STRONG_GUST_KMH = 60.0

    fun nowcast(res: OpenMeteoUvResponse?, nowSec: Long, tzOffsetSeconds: Int): Nowcast? {
        val m = res?.minutely15 ?: return null
        val times = m.time ?: return null
        val values = m.precipitation ?: return null
        val start = times.indexOfFirst { it + 15 * 60 > nowSec }.takeIf { it >= 0 } ?: return null
        val slots = (start until minOf(start + 8, times.size)).map { NowcastSlot(times[it], values.getOrNull(it) ?: 0.0) }
        if (slots.isEmpty()) return null

        fun hhmm(t: Long) = WeatherRepository.format(t, tzOffsetSeconds, "h:mm a", I18nLocale.get())
        val wetNow = slots.first().precipMm >= WET_MM
        val firstWet = slots.firstOrNull { it.precipMm >= WET_MM }
        val firstDryAfter = if (wetNow) slots.firstOrNull { it.precipMm < WET_MM } else null
        val summary = when {
            wetNow && firstDryAfter != null -> tr("Rain now, easing around ${hhmm(firstDryAfter.epochSeconds)}", "এখন বৃষ্টি, ${hhmm(firstDryAfter.epochSeconds)} নাগাদ কমবে")
            wetNow -> tr("Rain continuing for the next 2 hours", "আগামী ২ ঘণ্টা বৃষ্টি চলবে")
            firstWet != null -> tr("Rain likely around ${hhmm(firstWet.epochSeconds)}", "${hhmm(firstWet.epochSeconds)} নাগাদ বৃষ্টির সম্ভাবনা")
            else -> tr("No rain expected in the next 2 hours", "আগামী ২ ঘণ্টায় বৃষ্টির সম্ভাবনা নেই")
        }
        return Nowcast(slots, summary, if (!wetNow) firstWet?.epochSeconds else null)
    }

    /** Heavy rain, thunderstorms or strong gusts in the next 12 hours (first of each kind). */
    fun severe(res: OpenMeteoUvResponse?, nowSec: Long): List<SevereSignal> {
        val h = res?.hourly ?: return emptyList()
        val times = h.time ?: return emptyList()
        val out = mutableListOf<SevereSignal>()
        for (i in times.indices) {
            val t = times[i]
            if (t < nowSec - 3600 || t > nowSec + 12 * 3600) continue
            val rain = h.precipitation?.getOrNull(i) ?: 0.0
            val code = h.weatherCode?.getOrNull(i) ?: 0
            val gust = h.windGusts?.getOrNull(i) ?: 0.0
            if (rain >= HEAVY_RAIN_MM_PER_HOUR && out.none { it.kind == SevereSignal.Kind.HEAVY_RAIN }) out += SevereSignal(SevereSignal.Kind.HEAVY_RAIN, t, rain)
            if (code in 95..99 && out.none { it.kind == SevereSignal.Kind.THUNDER }) out += SevereSignal(SevereSignal.Kind.THUNDER, t, code.toDouble())
            if (gust >= STRONG_GUST_KMH && out.none { it.kind == SevereSignal.Kind.STRONG_WIND }) out += SevereSignal(SevereSignal.Kind.STRONG_WIND, t, gust)
        }
        return out
    }
}

/** Indirection so data code can use the UI language locale. */
object I18nLocale {
    fun get(): java.util.Locale = com.example.wuwaweather.i18n.I18n.locale
}
