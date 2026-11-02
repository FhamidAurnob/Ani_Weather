package com.example.wuwaweather.ui.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wuwaweather.BuildConfig
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.i18n.Text
import com.example.wuwaweather.i18n.tr
import com.example.wuwaweather.storms.Cyclone
import com.example.wuwaweather.storms.CycloneRepository
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.PrimaryButton
import com.example.wuwaweather.ui.TextLink
import com.example.wuwaweather.ui.components.GlassCard
import com.example.wuwaweather.ui.theme.Velora
import java.util.Locale
import kotlin.math.roundToInt

private enum class MapSource(private val en: String, private val bn: String) {
    WIND("Wind · earth", "বাতাস · earth"),
    WINDY("Windy", "Windy"),
    CYCLONES("Cyclones", "ঘূর্ণিঝড়");

    val label: String get() = tr(en, bn)
}

/** Windy embed layers (Windy's official embeddable map). */
private enum class WindyLayer(val id: String, private val en: String, private val bn: String) {
    WIND("wind", "Wind", "বাতাস"),
    RAIN("rain", "Rain", "বৃষ্টি"),
    GUST("gust", "Gusts", "দমকা"),
    CLOUDS("clouds", "Clouds", "মেঘ"),
    TEMP("temp", "Temperature", "তাপমাত্রা");

    val label: String get() = tr(en, bn)
}

@Composable
fun MapsScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val home by HomeStore.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { HomeStore.get(context) }
    val lat = home?.lat ?: 23.81
    val lon = home?.lon ?: 90.41

    var source by rememberSaveable { mutableStateOf(MapSource.WIND) }
    var layer by rememberSaveable { mutableStateOf(WindyLayer.WIND) }

    val windUrl = String.format(Locale.US, "https://earth.nullschool.net/#current/wind/surface/level/orthographic=%.2f,%.2f,1100", lon, lat)
    val windyUrl = String.format(
        Locale.US,
        "https://embed.windy.com/embed2.html?lat=%.3f&lon=%.3f&detailLat=%.3f&detailLon=%.3f&zoom=5&level=surface" +
            "&overlay=%s&product=ecmwf&menu=&message=true&marker=true&calendar=now&pressure=true&type=map" +
            "&location=coordinates&detail=&metricWind=km%%2Fh&metricTemp=%%C2%%B0C&radarRange=-1",
        lat, lon, lat, lon, layer.id
    )
    val windyFullUrl = String.format(Locale.US, "https://www.windy.com/?%.3f,%.3f,5", lat, lon)

    val wind = rememberMapPage(windUrl)
    val windy = rememberMapPage(windyUrl)

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapSource.values().forEach { s -> Pill(s.label, selected = source == s) { source = s } }
            Spacer(Modifier.weight(1f))
            if (source != MapSource.CYCLONES) {
                TextLink(tr("Open ↗", "খুলুন ↗")) {
                    val url = if (source == MapSource.WIND) windUrl else windyFullUrl
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
        if (source == MapSource.WINDY) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WindyLayer.values().forEach { l -> Pill(l.label, selected = layer == l) { layer = l } }
            }
        }

        Box(Modifier.fillMaxSize()) {
            // No rounded clip here: clipping a WebView can make it render black on many phones
            MapWebView(wind, visible = source == MapSource.WIND)
            MapWebView(windy, visible = source == MapSource.WINDY)

            val page = when (source) {
                MapSource.WIND -> wind
                MapSource.WINDY -> windy
                MapSource.CYCLONES -> null
            }
            if (page != null) MapOverlay(page, onOpenBrowser = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (source == MapSource.WIND) windUrl else windyFullUrl)))
            })
            if (source == MapSource.CYCLONES) CycloneList(lat, lon, home?.name)
        }
    }
}

// ---------------- WebView handling ----------------

/** State for one map WebView: the view itself plus loading progress and errors. */
private class MapPage(context: Context) {
    var progress by mutableIntStateOf(0)
    var error by mutableStateOf<String?>(null)
    private var loadedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    val view: WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(AndroidColor.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mediaPlaybackRequiresUserGesture = false
        // Some map sites serve a stripped-down page to in-app WebViews; present as normal Chrome
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
            override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                if (request.isForMainFrame) error = err.description?.toString() ?: "Error ${err.errorCode}"
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
        }
    }

    fun load(url: String, force: Boolean = false) {
        if (force || url != loadedUrl) {
            loadedUrl = url
            error = null
            progress = 0
            view.loadUrl(url)
        }
    }

    fun retry() { loadedUrl?.let { load(it, force = true) } }
}

@Composable
private fun rememberMapPage(url: String): MapPage {
    val context = LocalContext.current
    val page = remember { MapPage(context) }
    LaunchedEffect(url) { page.load(url) }
    DisposableEffect(Unit) {
        onDispose {
            (page.view.parent as? ViewGroup)?.removeView(page.view)
            page.view.destroy()
        }
    }
    return page
}

@Composable
private fun MapWebView(page: MapPage, visible: Boolean) {
    AndroidView(
        factory = { page.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
        modifier = Modifier.fillMaxSize(),
        update = {
            it.visibility = if (visible) View.VISIBLE else View.GONE
            if (visible) it.onResume() else it.onPause()
        }
    )
}

/** Loading spinner and error message, so a map is never just a black box. */
@Composable
private fun MapOverlay(page: MapPage, onOpenBrowser: () -> Unit) {
    val err = page.error
    when {
        err != null -> Box(Modifier.fillMaxSize().background(Color(0xFF0A0F24)), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.padding(24.dp).fillMaxWidth()) {
                Text(tr("Couldn't load this map", "মানচিত্রটি লোড করা যায়নি"), style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary)
                Text(err, style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(tr("Try again", "আবার চেষ্টা করুন"), { page.retry() })
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Open in browser", "ব্রাউজারে খুলুন"), style = MaterialTheme.typography.labelLarge, color = Velora.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onOpenBrowser).padding(vertical = 12.dp)
                )
            }
        }
        page.progress < 60 -> Box(Modifier.fillMaxSize().background(Color(0xCC0A0F24)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Velora.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(10.dp))
                Text(tr("Loading map… ${page.progress}%", "মানচিত্র লোড হচ্ছে… ${page.progress}%"), style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary)
            }
        }
    }
}

// ---------------- Cyclones ----------------

@Composable
private fun CycloneList(lat: Double, lon: Double, homeName: String?) {
    val context = LocalContext.current
    var cyclones by remember { mutableStateOf<List<Cyclone>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(lat, lon, reload) {
        error = null
        runCatching { CycloneRepository.active(lat, lon) }
            .onSuccess { cyclones = it }
            .onFailure { error = tr("Couldn't load cyclone data. Check your connection.", "ঘূর্ণিঝড়ের তথ্য লোড হয়নি। ইন্টারনেট সংযোগ দেখুন।") }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(tr("Active tropical cyclones", "সক্রিয় ঘূর্ণিঝড়"), style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary)
                Text(
                    tr("Worldwide from GDACS (UN / EU), nearest to ${homeName ?: "you"} first. For Bangladesh's official signal numbers, follow BMD.",
                        "GDACS (জাতিসংঘ / ইইউ) থেকে বিশ্বজুড়ে, ${homeName ?: "আপনার"} থেকে নিকটতমগুলো আগে। বাংলাদেশের সরকারি সংকেতের জন্য আবহাওয়া অধিদপ্তর (BMD) অনুসরণ করুন।"),
                    style = MaterialTheme.typography.labelSmall, color = Velora.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextLink(tr("Refresh", "রিফ্রেশ")) { reload++ }
                    TextLink(tr("BMD website ↗", "আবহাওয়া অধিদপ্তর ↗")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bmd.gov.bd/")))
                    }
                }
            }
        }
        val list = cyclones
        when {
            error != null -> item { GlassCard(Modifier.fillMaxWidth()) { Text(error!!, color = Velora.TextPrimary) } }
            list == null -> item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Velora.Accent, strokeWidth = 2.dp)
                }
            }
            list.isEmpty() -> item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("✓ " + tr("No active tropical cyclones anywhere right now.", "এই মুহূর্তে বিশ্বের কোথাও সক্রিয় ঘূর্ণিঝড় নেই।"), color = Velora.TextPrimary)
                }
            }
            else -> items(list, key = { it.id }) { c ->
                CycloneRow(c) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.reportUrl))) }
            }
        }
    }
}

@Composable
private fun CycloneRow(c: Cyclone, onClick: () -> Unit) {
    val color = when (c.alertLevel.lowercase()) {
        "red" -> Color(0xFFE5677A)
        "orange" -> Color(0xFFFF9B5E)
        else -> Color(0xFF6BD68A)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (c.inBayOfBengal) Color(0x29FF9B5E) else Velora.GlassFill)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium, color = Velora.TextPrimary, fontWeight = FontWeight.SemiBold)
            c.severity?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Velora.TextSecondary) }
            val parts = listOfNotNull(
                tr("${c.alertLevel} alert", "${alertBn(c.alertLevel)} সতর্কতা"),
                c.distanceKm?.let { tr("${it.roundToInt()} km away", "${it.roundToInt()} কিমি দূরে") },
                if (c.inBayOfBengal) tr("Bay of Bengal", "বঙ্গোপসাগর") else null,
                c.countries
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Velora.TextTertiary)
        }
    }
}

private fun alertBn(level: String) = when (level.lowercase()) {
    "red" -> "লাল"
    "orange" -> "কমলা"
    else -> "সবুজ"
}
