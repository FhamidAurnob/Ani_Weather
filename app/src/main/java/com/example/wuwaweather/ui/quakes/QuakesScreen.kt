package com.example.wuwaweather.ui.quakes

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.data.SettingsData
import com.example.wuwaweather.data.VeloraSettings
import com.example.wuwaweather.quakes.Quake
import com.example.wuwaweather.quakes.QuakeFeed
import com.example.wuwaweather.quakes.QuakeRepository
import com.example.wuwaweather.quakes.QuakeWorker
import com.example.wuwaweather.ui.FeatureScreen
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.TextLink
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.theme.Velora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class QuakesState(
    val feed: QuakeFeed = QuakeFeed.DAY,
    val nearFirst: Boolean = false,
    val quakes: List<Quake> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long? = null
)

class QuakesViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(QuakesState())
    val state: StateFlow<QuakesState> = _state.asStateFlow()
    val settings = VeloraSettings.flow(app)

    init { load() }

    fun setFeed(feed: QuakeFeed) { _state.update { it.copy(feed = feed) }; load() }
    fun toggleNearFirst() = _state.update { it.copy(nearFirst = !it.nearFirst) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val home = HomeStore.get(getApplication())
            runCatching { QuakeRepository.load(_state.value.feed, home?.lat, home?.lon) }
                .onSuccess { list -> _state.update { it.copy(quakes = list, loading = false, updatedAt = System.currentTimeMillis()) } }
                .onFailure { _state.update { it.copy(loading = false, error = tr("Couldn't load earthquakes. Check your connection.", "ভূমিকম্পের তথ্য লোড হয়নি। ইন্টারনেট সংযোগ দেখুন।")) } }
        }
    }

    fun updateSettings(transform: (SettingsData) -> SettingsData) {
        VeloraSettings.update(getApplication(), transform)
        QuakeWorker.schedule(getApplication())
    }
}

@Composable
fun QuakesScreen(bottomPadding: Dp) {
    val vm: QuakesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: SettingsData()
    val home by HomeStore.home.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { QuakeWorker.schedule(context) }

    val list = if (state.nearFirst) state.quakes.sortedBy { it.distanceKm ?: Double.MAX_VALUE } else state.quakes

    FeatureScreen(
        title = tr("Earthquakes", "ভূমিকম্প"),
        subtitle = tr("Worldwide · USGS", "বিশ্বজুড়ে · USGS") + (state.updatedAt?.let { tr(" · updated ", " · হালনাগাদ ") + time(it) } ?: ""),
        bottomPadding = bottomPadding,
        trailing = {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Velora.TextPrimary, strokeWidth = 2.dp)
            else TextLink(tr("Refresh", "রিফ্রেশ")) { vm.load() }
        }
    ) {
        // Alert settings
        item(key = "alerts") {
            GlassCard(Modifier.fillMaxWidth(), title = tr("Nearby alerts", "কাছাকাছি ভূমিকম্পের সতর্কতা")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Notify me about M${String.format(Locale.US, "%.1f", s.quakeMinMagnitude)}+ within ${s.quakeRadiusKm} km",
                                "${s.quakeRadiusKm} কিমির মধ্যে M${String.format(Locale.US, "%.1f", s.quakeMinMagnitude)}+ হলে জানাবে"),
                            style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary
                        )
                        Text(
                            home?.let { tr("Around ${it.name} · checked every 15 min", "${it.name}-এর আশেপাশে · প্রতি ১৫ মিনিটে দেখা হয়") }
                                ?: tr("Open the Weather tab once to set your location", "লোকেশন সেট করতে একবার আবহাওয়া ট্যাব খুলুন"),
                            style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary
                        )
                    }
                    Switch(
                        checked = s.quakeAlerts,
                        onCheckedChange = { on ->
                            if (on && !Notifications.canPost(context) && Build.VERSION.SDK_INT >= 33) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.updateSettings { it.copy(quakeAlerts = on) }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Velora.Accent, checkedThumbColor = Color(0xFF1A1A1A))
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 1000, 2000).forEach { km ->
                        Pill(tr("$km km", "$km কিমি"), selected = s.quakeRadiusKm == km) { vm.updateSettings { it.copy(quakeRadiusKm = km) } }
                    }
                }
            }
        }

        // Feed filters
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuakeFeed.values().forEach { f -> Pill(f.label, selected = state.feed == f) { vm.setFeed(f) } }
                Pill(tr("Nearest first", "নিকটতম আগে"), selected = state.nearFirst) { vm.toggleNearFirst() }
            }
        }

        if (state.error != null) {
            item(key = "error") {
                GlassCard(Modifier.fillMaxWidth()) { Text(state.error!!, color = Velora.TextPrimary, style = MaterialTheme.typography.bodyMedium) }
            }
        } else if (!state.loading && list.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth()) { Text(tr("No earthquakes in this list right now.", "এই তালিকায় এখন কোনো ভূমিকম্প নেই।"), color = Velora.TextSecondary) }
            }
        }

        items(list, key = { it.id }) { q ->
            QuakeRow(q) { q.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }
        }
    }
}

@Composable
private fun QuakeRow(q: Quake, onClick: () -> Unit) {
    val color = when {
        q.magnitude >= 6 -> Color(0xFFE5677A)
        q.magnitude >= 5 -> Color(0xFFFF9B5E)
        q.magnitude >= 4 -> Color(0xFFFFD36B)
        else -> Color(0xFF6BD68A)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Velora.GlassFill)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(String.format(Locale.US, "%.1f", q.magnitude), color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(q.place, style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            val parts = listOfNotNull(
                timeAgo(q.time),
                tr("depth ${q.depthKm.roundToInt()} km", "গভীরতা ${q.depthKm.roundToInt()} কিমি"),
                q.distanceKm?.let { tr("${it.roundToInt()} km away", "${it.roundToInt()} কিমি দূরে") },
                if (q.tsunami) tr("tsunami alert", "সুনামি সতর্কতা") else null
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
        }
    }
}

private fun timeAgo(t: Long): String {
    val mins = (System.currentTimeMillis() - t) / 60_000
    return when {
        mins < 1 -> tr("just now", "এইমাত্র")
        mins < 60 -> tr("${mins}m ago", "${mins} মিনিট আগে")
        mins < 48 * 60 -> tr("${mins / 60}h ago", "${mins / 60} ঘণ্টা আগে")
        else -> tr("${mins / 1440}d ago", "${mins / 1440} দিন আগে")
    }
}

private fun time(t: Long) = SimpleDateFormat("HH:mm", com.example.wuwaweather.i18n.I18n.locale).format(Date(t))
