package com.example.wuwaweather.calendar

import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val monthName: String get() = tr(Hijri.MONTHS[month - 1], Hijri.MONTHS_BN[month - 1])
    val isRamadan: Boolean get() = month == 9
    override fun toString() = "$day $monthName $year".localDigits()
}

/** Hijri dates from Java's Umm al-Qura calendar, shifted by a local moon-sighting offset. */
object Hijri {
    val MONTHS = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah"
    )

    val MONTHS_BN = listOf(
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    )

    fun of(date: LocalDate, offsetDays: Int): HijriDate {
        val h = HijrahDate.from(date.plusDays(offsetDays.toLong()))
        return HijriDate(
            day = h.get(ChronoField.DAY_OF_MONTH),
            month = h.get(ChronoField.MONTH_OF_YEAR),
            year = h.get(ChronoField.YEAR)
        )
    }

    /** First day of the next Ramadan on or after [from] (null if out of the calendar's range). */
    fun nextRamadanStart(from: LocalDate, offsetDays: Int): LocalDate? {
        var d = from
        repeat(400) {
            val h = runCatching { of(d, offsetDays) }.getOrNull() ?: return null
            if (h.month == 9 && h.day == 1) return d
            d = d.plusDays(1)
        }
        return null
    }
}
