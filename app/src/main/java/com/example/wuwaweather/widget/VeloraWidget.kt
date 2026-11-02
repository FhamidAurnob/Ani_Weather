package com.example.wuwaweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.wuwaweather.MainActivity
import com.example.wuwaweather.R
import com.example.wuwaweather.data.Condition

/** Velora home-screen widget: city, temperature, condition and high/low. */
class VeloraWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshot.loadFresh(context)
        provideContent { WidgetContent(snapshot) }
    }
}

class VeloraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VeloraWidget()
}

private val White = ColorProvider(Color.White)
private val WhiteSoft = ColorProvider(Color(0xCCFFFFFF))
private val WhiteFaint = ColorProvider(Color(0x99FFFFFF))

@Composable
private fun WidgetContent(s: WidgetSnapshot?) {
    val bg = when {
        s == null -> R.drawable.widget_bg_night
        !s.isDay -> R.drawable.widget_bg_night
        s.conditionEnum in setOf(Condition.RAIN, Condition.DRIZZLE, Condition.THUNDER) -> R.drawable.widget_bg_rain
        s.conditionEnum in setOf(Condition.CLOUDS, Condition.FOG, Condition.SNOW) -> R.drawable.widget_bg_cloudy
        else -> R.drawable.widget_bg_clear
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ImageProvider(bg))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        if (s == null) {
            Text("Velora", style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            Text("Open the app to load weather", style = TextStyle(color = WhiteSoft, fontSize = 13.sp))
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.city, style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text("${s.temp}°", style = TextStyle(color = White, fontSize = 44.sp, fontWeight = FontWeight.Normal))
        Text(s.description, style = TextStyle(color = WhiteSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Text("H ${s.high}°  ·  L ${s.low}°", style = TextStyle(color = WhiteFaint, fontSize = 12.sp))
    }
}
