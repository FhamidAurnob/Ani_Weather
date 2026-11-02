package com.example.wuwaweather.ui.sky

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.wuwaweather.R
import com.example.wuwaweather.data.Condition

/**
 * Realistic looping video backgrounds ("Real" mode).
 * Videos live in res/raw as small H.264 MP4s (about 12 MB in total).
 */
@OptIn(UnstableApi::class)
@Composable
fun RealisticSky(condition: Condition, isDay: Boolean, modifier: Modifier = Modifier, playing: Boolean = true) {
    val context = LocalContext.current
    val video = videoFor(condition, isDay)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
        }
    }

    // Swap the video when the weather changes
    LaunchedEffect(video) {
        player.setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(video)))
        player.prepare()
    }

    // Pause while hidden (e.g. behind the Maps tab) or on battery saver
    LaunchedEffect(playing) { if (playing) player.play() else player.pause() }

    // Pause when the app goes to the background, release when this screen leaves
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (playing) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.sky_video_view, null) as PlayerView).also {
                    it.player = player
                }
            }
        )
        // Darken slightly so white text stays readable on bright footage
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.4f to Color.Black.copy(alpha = 0.05f),
                        1f to Color.Black.copy(alpha = 0.45f)
                    )
                )
        )
    }
}

@RawRes
private fun videoFor(condition: Condition, isDay: Boolean): Int = when (condition) {
    Condition.CLEAR, Condition.PARTLY_CLOUDY -> if (isDay) R.raw.sky_clear_day else R.raw.sky_clear_night
    Condition.CLOUDS, Condition.FOG -> if (isDay) R.raw.sky_clouds_day else R.raw.sky_clouds_night
    Condition.DRIZZLE -> if (isDay) R.raw.sky_drizzle_day else R.raw.sky_drizzle_night
    Condition.RAIN -> if (isDay) R.raw.sky_rain_day else R.raw.sky_rain_night
    Condition.THUNDER -> R.raw.sky_thunder
    Condition.SNOW -> if (isDay) R.raw.sky_snow_day else R.raw.sky_snow_night
}
