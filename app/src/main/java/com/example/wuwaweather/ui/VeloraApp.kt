package com.example.wuwaweather.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.wuwaweather.data.BackgroundMode
import com.example.wuwaweather.data.Condition
import com.example.wuwaweather.data.WeatherUiState
import com.example.wuwaweather.ui.calendar.CalendarScreen
import com.example.wuwaweather.ui.components.WeatherGlyph
import com.example.wuwaweather.ui.maps.MapsScreen
import com.example.wuwaweather.ui.prayer.PrayerScreen
import com.example.wuwaweather.ui.quakes.QuakesScreen
import com.example.wuwaweather.ui.sky.LiveSky
import com.example.wuwaweather.ui.sky.RealisticSky
import com.example.wuwaweather.ui.theme.Velora
import kotlinx.coroutines.launch

enum class Tab(val label: String) { WEATHER("Weather"), MAPS("Maps"), QUAKES("Quakes"), PRAYER("Prayer"), CALENDAR("Calendar") }

/** Everything the weather tab and city picker can ask the ViewModel to do. */
class WeatherActions(
    val onRefresh: (String) -> Unit,
    val onToggleMode: () -> Unit,
    val onToggleUnits: () -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSearch: (String) -> Unit,
    val onSuggestion: (com.example.wuwaweather.data.CitySuggestion) -> Unit,
    val onUseLocation: () -> Unit,
    val onSelectPlace: (Int) -> Unit,
    val onRemovePlace: (String) -> Unit,
    val onDismissMessage: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VeloraApp(state: WeatherUiState, actions: WeatherActions) {
    val pager = rememberPagerState { Tab.values().size }
    val scope = rememberCoroutineScope()
    var showCities by rememberSaveable { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = navBottom + 84.dp   // room for the tab bar

    // Sky follows the selected place; while it loads, borrow any place that has data
    val sky = state.current?.bundle?.current ?: state.pages.firstNotNullOfOrNull { it.bundle?.current }
    val condition = sky?.condition ?: Condition.CLEAR
    val isDay = sky?.isDay ?: false

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0F24))) {
        Crossfade(targetState = state.backgroundMode, animationSpec = tween(700), label = "skyMode") { mode ->
            when (mode) {
                BackgroundMode.LIVE -> LiveSky(condition, isDay)
                BackgroundMode.REALISTIC -> RealisticSky(condition, isDay)
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            // Maps need horizontal drags for panning, so swiping tabs is off there
            userScrollEnabled = pager.currentPage != Tab.MAPS.ordinal,
            beyondBoundsPageCount = 1
        ) { index ->
            when (Tab.values()[index]) {
                Tab.WEATHER -> WeatherTab(
                    state = state,
                    bottomPadding = contentBottom,
                    onOpenCities = { showCities = true },
                    onRefresh = actions.onRefresh,
                    onToggleMode = actions.onToggleMode,
                    onToggleUnits = actions.onToggleUnits
                )
                Tab.MAPS -> MapsScreen(bottomPadding = contentBottom)
                Tab.QUAKES -> QuakesScreen(bottomPadding = contentBottom)
                Tab.PRAYER -> PrayerScreen(bottomPadding = contentBottom)
                Tab.CALENDAR -> CalendarScreen(bottomPadding = contentBottom)
            }
        }

        TabBar(
            selected = pager.currentPage,
            isDay = isDay,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 10.dp, start = 16.dp, end = 16.dp)
        )

        ErrorBanner(
            message = state.message,
            onDismiss = actions.onDismissMessage,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
        )
    }

    if (showCities) {
        ModalBottomSheet(
            onDismissRequest = { showCities = false; actions.onQueryChanged("") },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xF2121833),
            contentColor = Velora.TextPrimary
        ) {
            CityPicker(
                state = state,
                onQueryChanged = actions.onQueryChanged,
                onSubmit = { actions.onSearch(it); showCities = false },
                onSuggestion = { actions.onSuggestion(it); showCities = false },
                onUseLocation = { actions.onUseLocation(); showCities = false },
                onSelect = { actions.onSelectPlace(it); showCities = false },
                onRemove = actions.onRemovePlace
            )
        }
    }
}

@Composable
private fun TabBar(selected: Int, isDay: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(Color(0xCC0E1430))
            .border(1.dp, Velora.GlassBorder, shape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.values().forEachIndexed { i, tab ->
            val active = i == selected
            val tint by animateColorAsState(if (active) Velora.Accent else Velora.TextTertiary, label = "tabTint")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Color(0x1AFFFFFF) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when (tab) {
                        Tab.WEATHER -> WeatherGlyph(Condition.PARTLY_CLOUDY, isDay = true, size = 22.dp)
                        Tab.MAPS -> Icon(Icons.Rounded.Place, null, tint = tint, modifier = Modifier.size(22.dp))
                        Tab.QUAKES -> Icon(Icons.Rounded.Warning, null, tint = tint, modifier = Modifier.size(20.dp))
                        Tab.PRAYER -> WeatherGlyph(Condition.CLEAR, isDay = false, size = 22.dp)
                        Tab.CALENDAR -> Icon(Icons.Rounded.DateRange, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

/** Standard scrolling layout for feature tabs: big title, subtitle, then content. */
@Composable
fun FeatureScreen(
    title: String,
    subtitle: String?,
    bottomPadding: Dp,
    trailing: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall.copy(shadow = Velora.TextShadow), color = Velora.TextPrimary)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
                }
                trailing()
            }
        }
        content()
    }
}
