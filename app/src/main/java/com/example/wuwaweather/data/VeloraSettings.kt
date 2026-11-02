package com.example.wuwaweather.data

import android.content.Context
import com.example.wuwaweather.prayer.Madhab
import com.example.wuwaweather.prayer.Prayer
import com.example.wuwaweather.prayer.PrayerMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All feature settings in one place, persisted in SharedPreferences and observable. */
data class SettingsData(
    val method: PrayerMethod = PrayerMethod.KARACHI,
    val madhab: Madhab = Madhab.HANAFI,
    val prayerAlerts: Set<Prayer> = emptySet(),
    val suhurAlert: Boolean = false,
    val suhurMinutesBefore: Int = 30,
    val iftarAlert: Boolean = false,
    val fastingAlertsOnlyRamadan: Boolean = true,
    /** Days to shift the Umm al-Qura Hijri date. Bangladesh usually runs 1 day behind Saudi Arabia. */
    val hijriOffset: Int = -1,
    val quakeAlerts: Boolean = true,
    val quakeRadiusKm: Int = 1000,
    val quakeMinMagnitude: Double = 5.5
)

object VeloraSettings {
    private const val PREFS = "velora_settings"
    private val _state = MutableStateFlow<SettingsData?>(null)

    fun flow(context: Context): StateFlow<SettingsData?> { get(context); return _state.asStateFlow() }

    fun get(context: Context): SettingsData {
        _state.value?.let { return it }
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = SettingsData()
        val loaded = SettingsData(
            method = runCatching { PrayerMethod.valueOf(p.getString("method", null)!!) }.getOrDefault(d.method),
            madhab = runCatching { Madhab.valueOf(p.getString("madhab", null)!!) }.getOrDefault(d.madhab),
            prayerAlerts = p.getStringSet("prayer_alerts", emptySet())!!
                .mapNotNull { runCatching { Prayer.valueOf(it) }.getOrNull() }.toSet(),
            suhurAlert = p.getBoolean("suhur_alert", d.suhurAlert),
            suhurMinutesBefore = p.getInt("suhur_minutes", d.suhurMinutesBefore),
            iftarAlert = p.getBoolean("iftar_alert", d.iftarAlert),
            fastingAlertsOnlyRamadan = p.getBoolean("fasting_only_ramadan", d.fastingAlertsOnlyRamadan),
            hijriOffset = p.getInt("hijri_offset", d.hijriOffset),
            quakeAlerts = p.getBoolean("quake_alerts", d.quakeAlerts),
            quakeRadiusKm = p.getInt("quake_radius", d.quakeRadiusKm),
            quakeMinMagnitude = p.getFloat("quake_min_mag", d.quakeMinMagnitude.toFloat()).toDouble()
        )
        _state.value = loaded
        return loaded
    }

    fun update(context: Context, transform: (SettingsData) -> SettingsData): SettingsData {
        val next = transform(get(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("method", next.method.name)
            .putString("madhab", next.madhab.name)
            .putStringSet("prayer_alerts", next.prayerAlerts.map { it.name }.toSet())
            .putBoolean("suhur_alert", next.suhurAlert)
            .putInt("suhur_minutes", next.suhurMinutesBefore)
            .putBoolean("iftar_alert", next.iftarAlert)
            .putBoolean("fasting_only_ramadan", next.fastingAlertsOnlyRamadan)
            .putInt("hijri_offset", next.hijriOffset)
            .putBoolean("quake_alerts", next.quakeAlerts)
            .putInt("quake_radius", next.quakeRadiusKm)
            .putFloat("quake_min_mag", next.quakeMinMagnitude.toFloat())
            .apply()
        _state.value = next
        return next
    }
}
