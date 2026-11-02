package com.example.wuwaweather.ui.maps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wuwaweather.data.HomeStore
import com.example.wuwaweather.ui.Pill
import com.example.wuwaweather.ui.TextLink
import com.example.wuwaweather.ui.theme.Velora
import java.util.Locale

private enum class MapSource(val label: String) { WIND("Wind · earth"), STORMS("Storms · Windy") }

/**
 * Live maps: earth.nullschool.net global wind animation and Windy (radar, storms and the
 * hurricane tracker). Both WebViews are kept alive so switching between them is instant.
 */
@Composable
fun MapsScreen(bottomPadding: Dp) {
    val context = LocalContext.current
    val home by HomeStore.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { HomeStore.get(context) }
    val lat = home?.lat ?: 23.81
    val lon = home?.lon ?: 90.41

    var source by rememberSaveable { mutableStateOf(MapSource.WIND) }
    val windUrl = String.format(Locale.US, "https://earth.nullschool.net/#current/wind/surface/level/orthographic=%.2f,%.2f,1100", lon, lat)
    val stormsUrl = String.format(Locale.US, "https://www.windy.com/?%.3f,%.3f,5", lat, lon)

    var windProgress by rememberSaveable { mutableIntStateOf(0) }
    var stormProgress by rememberSaveable { mutableIntStateOf(0) }
    val windView = rememberWebView(windUrl) { windProgress = it }
    val stormView = rememberWebView(stormsUrl) { stormProgress = it }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapSource.values().forEach { s -> Pill(s.label, selected = source == s) { source = s } }
            Spacer(Modifier.weight(1f))
            TextLink("Open ↗") {
                val url = if (source == MapSource.WIND) windUrl else stormsUrl
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            AndroidView(factory = { windView.detach() }, modifier = Modifier.fillMaxSize(),
                update = { it.visibility = if (source == MapSource.WIND) View.VISIBLE else View.GONE })
            AndroidView(factory = { stormView.detach() }, modifier = Modifier.fillMaxSize(),
                update = { it.visibility = if (source == MapSource.STORMS) View.VISIBLE else View.GONE })

            val progress = if (source == MapSource.WIND) windProgress else stormProgress
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                    color = Velora.Accent,
                    trackColor = Color.Transparent
                )
            }
            if (home == null) {
                Text(
                    "Showing Dhaka. Open the Weather tab once to centre maps on your place.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Velora.TextSecondary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                )
            }
        }
    }
}

/** A WebView that survives leaving and re-entering this tab. */
@Composable
private fun rememberWebView(url: String, onProgress: (Int) -> Unit): WebView {
    val context = LocalContext.current
    val holder = androidx.compose.runtime.remember { WebViewHolder(context) }
    LaunchedEffect(url) { holder.loadIfChanged(url) }
    holder.onProgress = onProgress
    DisposableEffect(Unit) { onDispose { (holder.view.parent as? ViewGroup)?.removeView(holder.view) } }
    return holder.view
}

private class WebViewHolder(context: Context) {
    var onProgress: (Int) -> Unit = {}
    private var loadedUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    val view: WebView = WebView(context).apply {
        setBackgroundColor(AndroidColor.parseColor("#0A0F24"))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
        }
    }

    fun loadIfChanged(url: String) {
        if (url != loadedUrl) {
            loadedUrl = url
            view.loadUrl(url)
        }
    }
}

/** Removes the WebView from any old parent before re-attaching it. */
private fun WebView.detach(): WebView = also { (parent as? ViewGroup)?.removeView(it) }
