package com.example.wuwaweather.calendar

import com.example.wuwaweather.i18n.tr
import java.time.LocalDate
import java.time.MonthDay

/** Something worth marking on the calendar. */
data class CalendarEvent(
    val date: LocalDate,
    val title: String,
    val isHoliday: Boolean,
    /** True for nights of worship that begin after Maghrib on this date (Shab-e-Barat, Shab-e-Qadr…). */
    val isNight: Boolean = false,
    val isIslamic: Boolean = true
)

/**
 * Islamic dates (from the Hijri calendar with the local moon-sighting offset) and Bangladesh
 * public holidays. Lunar dates are estimates until the moon is sighted.
 */
object Events {

    private class IslamicDay(val month: Int, val day: Int, val en: String, val bn: String, val holiday: Boolean, val night: Boolean = false)

    private val islamic = listOf(
        IslamicDay(1, 1, "Islamic New Year", "হিজরি নববর্ষ", false),
        IslamicDay(1, 10, "Ashura", "আশুরা", true),
        IslamicDay(3, 12, "Eid-e-Miladunnabi", "ঈদে মিলাদুন্নবী", true),
        IslamicDay(7, 26, "Shab-e-Miraj (tonight)", "শবে মেরাজ (আজ রাতে)", false, night = true),
        IslamicDay(8, 14, "Shab-e-Barat (tonight)", "শবে বরাত (আজ রাতে)", false, night = true),
        IslamicDay(8, 15, "Shab-e-Barat holiday", "শবে বরাতের ছুটি", true),
        IslamicDay(9, 1, "First day of Ramadan", "রমজানের প্রথম দিন", false),
        IslamicDay(9, 26, "Shab-e-Qadr (tonight)", "শবে কদর (আজ রাতে)", true, night = true),
        IslamicDay(10, 1, "Eid al-Fitr", "ঈদুল ফিতর", true),
        IslamicDay(12, 9, "Day of Arafah", "আরাফার দিন", false),
        IslamicDay(12, 10, "Eid al-Adha", "ঈদুল আজহা", true),
    )

    /** Fixed-date national days (current Bangladesh list). */
    private val fixed = listOf(
        Triple(MonthDay.of(2, 21), "Language Martyrs' Day", "শহীদ দিবস ও আন্তর্জাতিক মাতৃভাষা দিবস"),
        Triple(MonthDay.of(3, 26), "Independence Day", "স্বাধীনতা দিবস"),
        Triple(MonthDay.of(4, 14), "Pohela Boishakh", "পহেলা বৈশাখ"),
        Triple(MonthDay.of(5, 1), "May Day", "মে দিবস"),
        Triple(MonthDay.of(8, 5), "July Mass Uprising Day", "জুলাই গণঅভ্যুত্থান দিবস"),
        Triple(MonthDay.of(11, 7), "National Revolution and Solidarity Day", "জাতীয় বিপ্লব ও সংহতি দিবস"),
        Triple(MonthDay.of(12, 16), "Victory Day", "বিজয় দিবস"),
        Triple(MonthDay.of(12, 25), "Christmas", "বড়দিন"),
    )

    /** Other religions' holidays follow lunar calendars we don't compute; these are from the published list. */
    private val published = listOf(
        Triple(LocalDate.of(2027, 5, 20), "Buddha Purnima", "বুদ্ধ পূর্ণিমা"),
        Triple(LocalDate.of(2027, 8, 25), "Janmashtami", "জন্মাষ্টমী"),
        Triple(LocalDate.of(2027, 10, 9), "Durga Puja (Bijoya Dashami)", "দুর্গাপূজা (বিজয়া দশমী)"),
    )

    fun on(date: LocalDate, hijriOffset: Int): List<CalendarEvent> {
        val out = mutableListOf<CalendarEvent>()
        runCatching { Hijri.of(date, hijriOffset) }.getOrNull()?.let { h ->
            islamic.filter { it.month == h.month && it.day == h.day }.forEach {
                out += CalendarEvent(date, tr(it.en, it.bn), it.holiday, it.night)
            }
        }
        fixed.filter { it.first == MonthDay.from(date) }.forEach { out += CalendarEvent(date, tr(it.second, it.third), true, isIslamic = false) }
        published.filter { it.first == date }.forEach { out += CalendarEvent(date, tr(it.second, it.third), true, isIslamic = false) }
        return out
    }

    /** Events from [from] (inclusive) up to [days] days ahead. */
    fun upcoming(from: LocalDate, hijriOffset: Int, days: Int = 400): List<CalendarEvent> =
        (0 until days).flatMap { on(from.plusDays(it.toLong()), hijriOffset) }
}
