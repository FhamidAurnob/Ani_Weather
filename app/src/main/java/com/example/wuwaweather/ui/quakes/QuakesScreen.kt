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
import androidx.compose.material3.Text
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
                .onFailure { _state.update { it.copy(loading = false, error = "Couldn't load earthquakes. Check your connection.") } }
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
        title = "Earthquakes",
        subtitle = "Worldwide · USGS" + (state.updatedAt?.let { " · updated ${time(it)}" } ?: ""),
        bottomPadding = bottomPadding,
        trailing = {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), color = Velora.TextPrimary, strokeWidth = 2.dp)
            else TextLink("Refresh") { vm.load() }
        }
    ) {
        // Alert settings
        item(key = "alerts") {
            GlassCard(Modifier.fillMaxWidth(), title = "Nearby alerts") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Notify me about M${String.format(Locale.US, "%.1f", s.quakeMinMagnitude)}+ within ${s.quakeRadiusKm} km",
                            style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary
                        )
                        Text(
                            home?.let { "Around ${it.name} · checked every 15 min" } ?: "Open the Weather tab once to set your location",
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
                        Pill("$km km", selected = s.quakeRadiusKm == km) { vm.updateSettings { it.copy(quakeRadiusKm = km) } }
                    }
                }
            }
        }

        // Feed filters
        item(key = "filters") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuakeFeed.values().forEach { f -> Pill(f.label, selected = state.feed == f) { vm.setFeed(f) } }
                Pill("Nearest first", selected = state.nearFirst) { vm.toggleNearFirst() }
            }
        }

        if (state.error != null) {
            item(key = "error") {
                GlassCard(Modifier.fillMaxWidth()) { Text(state.error!!, color = Velora.TextPrimary, style = MaterialTheme.typography.bodyMedium) }
            }
        } else if (!state.loading && list.isEmpty()) {
            item(key = "empty") {
                GlassCard(Modifier.fillMaxWidth()) { Text("No earthquakes in this list right now.", color = Velora.TextSecondary) }
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
                "depth ${q.depthKm.roundToInt()} km",
                q.distanceKm?.let { "${it.roundToInt()} km away" },
                if (q.tsunami) "tsunami alert" else null
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
        }
    }
}

private fun timeAgo(t: Long): String {
    val mins = (System.currentTimeMillis() - t) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 48 * 60 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

private fun time(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
