package com.example.wuwaweather.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.ui.PrimaryButton
import com.example.wuwaweather.ui.TextLink
import com.example.wuwaweather.ui.theme.Velora

/**
 * Many Android brands (Xiaomi, Realme/Oppo, Vivo, Samsung, Huawei…) stop apps in the
 * background, which delays or kills prayer, Suhur and earthquake alerts. This card walks the
 * user through exempting Velora, with brand-specific steps.
 */
@Composable
fun ReliabilityCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("velora_prefs", Context.MODE_PRIVATE) }
    var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("reliability_done", false)) }
    val steps = remember { brandSteps() }

    // Re-check when the user comes back from Settings
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ignoring = isIgnoringBatteryOptimizations(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (dismissed || (ignoring && steps == null)) return

    GlassCard(modifier.fillMaxWidth(), title = tr("Reliable alerts", "নির্ভরযোগ্য অ্যালার্ট")) {
        Text(
            tr("Some phones stop apps in the background, so prayer, Suhur and earthquake alerts can be late or missed.",
                "কিছু ফোন ব্যাকগ্রাউন্ডে অ্যাপ বন্ধ করে দেয়, ফলে নামাজ, সাহরি ও ভূমিকম্পের অ্যালার্ট দেরিতে আসতে বা না আসতে পারে।"),
            style = MaterialTheme.typography.bodyMedium, color = Velora.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        if (!ignoring) {
            PrimaryButton(tr("Let Velora run in the background", "Velora-কে ব্যাকগ্রাউন্ডে চলতে দিন"), {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
                }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            })
        } else {
            Text("✓ " + tr("Battery optimisation is off for Velora", "Velora-এর জন্য ব্যাটারি অপ্টিমাইজেশন বন্ধ"),
                style = MaterialTheme.typography.bodyMedium, color = Velora.Accent)
        }
        if (steps != null) {
            Spacer(Modifier.height(12.dp))
            Text(steps.first, style = MaterialTheme.typography.labelMedium, color = Velora.TextTertiary)
            Spacer(Modifier.height(4.dp))
            Text(steps.second, style = MaterialTheme.typography.bodyMedium, color = Velora.TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextLink(tr("Open app settings", "অ্যাপ সেটিংস খুলুন")) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
            TextLink(tr("Done", "সম্পন্ন")) {
                prefs.edit().putBoolean("reliability_done", true).apply()
                dismissed = true
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Title + steps for the phone's brand, or null for brands that don't need extra steps. */
private fun brandSteps(): Pair<String, String>? {
    val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        listOf("xiaomi", "redmi", "poco").any { it in brand } ->
            tr("ON XIAOMI / REDMI / POCO", "শাওমি / রেডমি / পোকো ফোনে") to tr(
                "Settings → Apps → Manage apps → Velora → turn on Autostart, then Battery saver → No restrictions.",
                "সেটিংস → অ্যাপস → ম্যানেজ অ্যাপস → Velora → অটোস্টার্ট চালু করুন, তারপর ব্যাটারি সেভার → নো রেস্ট্রিকশনস।")
        listOf("oppo", "realme", "oneplus").any { it in brand } ->
            tr("ON REALME / OPPO / ONEPLUS", "রিয়েলমি / অপো / ওয়ানপ্লাস ফোনে") to tr(
                "Settings → Apps → Velora → Battery usage → turn on Allow background activity and Allow auto launch.",
                "সেটিংস → অ্যাপস → Velora → ব্যাটারি ইউসেজ → Allow background activity ও Allow auto launch চালু করুন।")
        listOf("vivo", "iqoo").any { it in brand } ->
            tr("ON VIVO / IQOO", "ভিভো / আইকিউও ফোনে") to tr(
                "Settings → Battery → Background power consumption management → Velora → Allow. Also turn on Autostart for Velora.",
                "সেটিংস → ব্যাটারি → Background power consumption management → Velora → Allow। Velora-এর অটোস্টার্টও চালু করুন।")
        "samsung" in brand ->
            tr("ON SAMSUNG", "স্যামসাং ফোনে") to tr(
                "Settings → Battery → Background usage limits → Never sleeping apps → add Velora.",
                "সেটিংস → ব্যাটারি → Background usage limits → Never sleeping apps → Velora যোগ করুন।")
        listOf("huawei", "honor").any { it in brand } ->
            tr("ON HUAWEI / HONOR", "হুয়াওয়ে / অনার ফোনে") to tr(
                "Settings → Battery → App launch → Velora → Manage manually, and turn on all three options.",
                "সেটিংস → ব্যাটারি → App launch → Velora → Manage manually, তিনটি অপশনই চালু করুন।")
        else -> null
    }
}
