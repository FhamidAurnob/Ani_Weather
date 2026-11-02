package com.example.wuwaweather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wuwaweather.data.BackgroundMode
import com.example.wuwaweather.data.CitySuggestion
import com.example.wuwaweather.data.CurrentWeather
import com.example.wuwaweather.data.PageState
import com.example.wuwaweather.data.UnitSystem
import com.example.wuwaweather.data.WeatherBundle
import com.example.wuwaweather.data.WeatherRepository
import com.example.wuwaweather.data.WeatherUiState
import com.example.wuwaweather.ui.components.DailyCard
import com.example.wuwaweather.ui.components.DetailsSection
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.components.HourlyCard
import com.example.wuwaweather.ui.theme.Velora
import kotlinx.coroutines.delay

/** Weather tab: shows the selected place. Tap the city name to switch or add places. */
@Composable
fun WeatherTab(
    state: WeatherUiState,
    bottomPadding: Dp,
    onOpenCities: () -> Unit,
    onRefresh: (String) -> Unit,
    onToggleMode: () -> Unit,
    onToggleUnits: () -> Unit
) {
    val page = state.current ?: return
    val bundle = page.bundle
    when {
        bundle != null -> WeatherContent(state, page, bundle, bottomPadding, onOpenCities, onRefresh, onToggleMode, onToggleUnits)
        page.error != null -> EmptyState(page, page.error, onRetry = { onRefresh(page.key) }, onOpenCities = onOpenCities)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Velora.TextPrimary, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun WeatherContent(
    state: WeatherUiState,
    page: PageState,
    bundle: WeatherBundle,
    bottomPadding: Dp,
    onOpenCities: () -> Unit,
    onRefresh: (String) -> Unit,
    onToggleMode: () -> Unit,
    onToggleUnits: () -> Unit
) {
    // Fresh scroll position per place
    val listState = remember(page.key) { androidx.compose.foundation.lazy.LazyListState() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val c = bundle.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "header") {
            Header(
                current = c,
                isCurrentLocation = page.isCurrentLocation,
                isLoading = page.isLoading,
                placeCount = state.pages.size,
                mode = state.backgroundMode,
                units = state.units,
                onOpenCities = onOpenCities,
                onToggleMode = onToggleMode,
                onToggleUnits = onToggleUnits
            )
        }
        item(key = "hero") {
            Hero(
                c,
                Modifier.graphicsLayer {
                    val offset = when (listState.firstVisibleItemIndex) {
                        0 -> 0f
                        1 -> listState.firstVisibleItemScrollOffset.toFloat()
                        else -> 1000f
                    }
                    alpha = (1f - offset / 600f).coerceIn(0f, 1f)
                    translationY = offset * 0.35f
                }
            )
        }
        item(key = "hourly") { HourlyCard(bundle.hourly) }
        item(key = "daily") { DailyCard(bundle.daily) }
        item(key = "details") { DetailsSection(bundle) }
        item(key = "footer") {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Updated ${WeatherRepository.format(bundle.fetchedAtMillis / 1000, c.tzOffsetSeconds, "HH:mm")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Velora.TextTertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    TextLink(if (page.isLoading) "Updating…" else "Refresh") { onRefresh(page.key) }
                }
                Spacer(Modifier.height(6.dp))
                Text("Weather & air: OpenWeather · UV: Open-Meteo", style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary)
            }
        }
    }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Velora.TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun Header(
    current: CurrentWeather,
    isCurrentLocation: Boolean,
    isLoading: Boolean,
    placeCount: Int,
    mode: BackgroundMode,
    units: UnitSystem,
    onOpenCities: () -> Unit,
    onToggleMode: () -> Unit,
    onToggleUnits: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenCities)
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.LocationOn, contentDescription = null,
                tint = if (isCurrentLocation) Velora.Accent else Velora.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (current.country.isNullOrBlank()) current.city else "${current.city}, ${current.country}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Velora.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change city", tint = Velora.TextSecondary)
                }
                val time = WeatherRepository.format(System.currentTimeMillis() / 1000, current.tzOffsetSeconds, "EEE, d MMM · HH:mm")
                Text(
                    when {
                        isLoading -> "Updating…"
                        isCurrentLocation -> "My location · $time"
                        placeCount > 1 -> "$time · $placeCount places"
                        else -> time
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Velora.TextSecondary,
                    maxLines = 1
                )
            }
        }
        Pill(if (mode == BackgroundMode.LIVE) "✦ Live" else "◐ Real") {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleMode()
        }
        Spacer(Modifier.width(6.dp))
        Pill(units.temp) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggleUnits()
        }
    }
}

/** Small glass pill button. */
@Composable
fun Pill(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color(0xFF1A1A1A) else Velora.TextPrimary,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Velora.Accent else Velora.GlassFillStrong)
            .border(1.dp, if (selected) Color.Transparent else Velora.GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

// ---------------- City picker ----------------

/** Content of the city picker sheet: search with suggestions, my location, saved places. */
@Composable
fun CityPicker(
    state: WeatherUiState,
    onQueryChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSuggestion: (CitySuggestion) -> Unit,
    onUseLocation: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Places", style = MaterialTheme.typography.headlineSmall, color = Velora.TextPrimary)
        Spacer(Modifier.height(12.dp))
        SearchField(
            text = text,
            onTextChange = { text = it; onQueryChanged(it) },
            isBusy = state.isSearching,
            onSubmit = { onSubmit(it); text = "" },
            onClear = { text = ""; onQueryChanged("") }
        )

        // Live suggestions: city, region, country
        if (state.suggestions.isNotEmpty() && text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                state.suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestion(s); text = "" }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Velora.TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, color = Velora.TextPrimary)
                            if (s.detail.isNotBlank()) {
                                Text(s.detail, style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Velora.GlassFill)
                .border(1.dp, Velora.GlassBorder, RoundedCornerShape(20.dp))
                .clickable(onClick = onUseLocation)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Velora.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Use my current location", style = MaterialTheme.typography.labelLarge, color = Velora.TextPrimary)
        }

        Spacer(Modifier.height(16.dp))
        Text("SAVED", style = MaterialTheme.typography.labelMedium, color = Velora.TextTertiary)
        Spacer(Modifier.height(8.dp))
        state.pages.forEachIndexed { i, p ->
            val selected = i == state.currentPage
            val c = p.bundle?.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Velora.GlassFillStrong else Velora.GlassFill)
                    .clickable { onSelect(i) }
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (p.isCurrentLocation) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Velora.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary, maxLines = 1)
                    Text(
                        when {
                            c != null -> "${c.description} · H ${c.tempMax}° L ${c.tempMin}°"
                            p.error != null -> p.error
                            else -> "Loading…"
                        },
                        style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary, maxLines = 1
                    )
                }
                if (c != null) Text("${c.temp}°", style = MaterialTheme.typography.headlineSmall, color = Velora.TextPrimary)
                if (state.pages.size > 1) {
                    IconButton(onClick = { onRemove(p.key) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = Velora.TextTertiary, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    isBusy: Boolean,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Velora.GlassFillStrong)
            .border(1.dp, Velora.GlassBorder, RoundedCornerShape(26.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Velora.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Search any city", style = MaterialTheme.typography.bodyLarge, color = Velora.TextTertiary)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Velora.TextPrimary),
                cursorBrush = SolidColor(Velora.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    if (text.isNotBlank()) onSubmit(text)
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        when {
            isBusy -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Velora.TextPrimary, strokeWidth = 2.dp)
            }
            text.isNotEmpty() -> IconButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Velora.TextPrimary)
            }
            else -> Spacer(Modifier.width(12.dp))
        }
    }
}

// ---------------- Hero, empty state, banner ----------------

@Composable
private fun Hero(c: CurrentWeather, modifier: Modifier = Modifier) {
    val temp by animateIntAsState(c.temp, animationSpec = tween(900), label = "temp")
    val shadowed = MaterialTheme.typography.displayLarge.copy(shadow = Velora.TextShadow)
    Column(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(c.description, style = MaterialTheme.typography.titleLarge.copy(shadow = Velora.TextShadow), color = Velora.TextPrimary)
        Row(verticalAlignment = Alignment.Top) {
            Text("°", style = shadowed, color = Color.Transparent)
            Text("$temp", style = shadowed, color = Velora.TextPrimary)
            Text("°", style = shadowed, color = Velora.TextPrimary)
        }
        Text(
            "H ${c.tempMax}°   ·   L ${c.tempMin}°",
            style = MaterialTheme.typography.bodyLarge.copy(shadow = Velora.TextShadow, fontSize = 17.sp),
            color = Velora.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text("Feels like ${c.feelsLike}°", style = MaterialTheme.typography.bodyMedium.copy(shadow = Velora.TextShadow), color = Velora.TextSecondary)
    }
}

@Composable
private fun EmptyState(page: PageState, message: String, onRetry: () -> Unit, onOpenCities: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Velora.Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(page.displayName, style = MaterialTheme.typography.labelMedium, color = Velora.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onRetry)
            Spacer(Modifier.height(8.dp))
            Text(
                "Change city", style = MaterialTheme.typography.labelLarge, color = Velora.TextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenCities).padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFF1A1A1A),
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Velora.Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

@Composable
fun ErrorBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var lastMessage by remember { mutableStateOf("") }
    if (message != null) lastMessage = message
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE61B2033))
                .border(1.dp, Velora.GlassBorder, RoundedCornerShape(20.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Velora.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(lastMessage, style = MaterialTheme.typography.bodyMedium, color = Velora.TextPrimary)
        }
    }
}
