package com.example.wuwaweather.calendar

import com.example.wuwaweather.i18n.localDigits
import com.example.wuwaweather.i18n.tr
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A date in Bangladesh's national (revised 2019) Bangla calendar. */
data class BanglaDate(val day: Int, val month: Int, val year: Int) {
    val monthName: String get() = tr(Bangabda.MONTHS_EN[month - 1], Bangabda.MONTHS_BN[month - 1])
    override fun toString(): String = "$day $monthName $year".localDigits()
}

/**
 * Revised Bangla calendar (Bangladesh, 2019): year starts 14 April; Boishakh–Ashwin 31 days,
 * Kartik–Magh 30, Falgun 29 (30 when the Gregorian year containing it is a leap year), Chaitra 30.
 */
object Bangabda {
    val MONTHS_BN = listOf("বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন", "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র")
    val MONTHS_EN = listOf("Boishakh", "Joishtho", "Asharh", "Srabon", "Bhadro", "Ashwin", "Kartik", "Ogrohayon", "Poush", "Magh", "Falgun", "Choitro")

    fun of(date: LocalDate): BanglaDate {
        val startYear = if (date >= LocalDate.of(date.year, 4, 14)) date.year else date.year - 1
        val start = LocalDate.of(startYear, 4, 14)
        var days = ChronoUnit.DAYS.between(start, date).toInt()   // 0-based day of the Bangla year
        val falgunLeap = java.time.Year.isLeap((startYear + 1).toLong())
        val lengths = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, if (falgunLeap) 30 else 29, 30)
        var month = 0
        while (month < 11 && days >= lengths[month]) {
            days -= lengths[month]
            month++
        }
        return BanglaDate(day = days + 1, month = month + 1, year = startYear - 593)
    }
}
