package com.example.wuwaweather.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wuwaweather.data.Condition
import com.example.wuwaweather.ui.sky.LiveSky
import com.example.wuwaweather.ui.theme.Manrope
import com.example.wuwaweather.ui.theme.Velora

/** Brand moment shown while the first forecast loads. */
@Composable
fun SplashOverlay() {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Box(Modifier.fillMaxSize()) {
        LiveSky(Condition.CLEAR, isDay = false)

        Column(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = reveal.value
                    val s = 0.92f + 0.08f * reveal.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - reveal.value) * 24.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "VELORA",
                fontFamily = Manrope,
                fontWeight = FontWeight.ExtraLight,
                fontSize = 46.sp,
                letterSpacing = 14.sp,
                color = Velora.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "W E A T H E R",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 4.sp,
                color = Velora.Accent
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingDots()
            Spacer(Modifier.height(16.dp))
            Text(
                "Developed by Fhamid",
                style = MaterialTheme.typography.labelSmall,
                color = Velora.TextTertiary
            )
        }
    }
}

@Composable
private fun LoadingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 180),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .graphicsLayer { alpha = a }
                    .clip(CircleShape)
                    .background(Velora.TextPrimary)
            )
        }
    }
}
