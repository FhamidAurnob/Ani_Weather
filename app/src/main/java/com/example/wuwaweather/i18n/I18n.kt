package com.example.wuwaweather.i18n

import java.util.Locale

/**
 * Tiny app-wide translation helper. The whole UI is rebuilt when the language changes,
 * so plain functions are enough — no resource lookups needed.
 */
object I18n {
    @Volatile var bangla: Boolean = false

    val locale: Locale get() = if (bangla) Locale("bn", "BD") else Locale.ENGLISH
}

/** Picks the English or Bangla text for the current language. */
fun tr(en: String, bn: String): String = if (I18n.bangla) bn else en

/** Converts 0-9 to Bangla digits (০-৯) when Bangla is on. */
fun String.localDigits(): String {
    if (!I18n.bangla) return this
    val sb = StringBuilder(length)
    for (ch in this) sb.append(if (ch in '0'..'9') ('০' + (ch - '0')) else ch)
    return sb.toString()
}

fun Int.loc(): String = toString().localDigits()
