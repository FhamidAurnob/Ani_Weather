package com.example.wuwaweather.ui.sky

import androidx.compose.ui.graphics.Color
import com.example.wuwaweather.data.Condition

/** Three-stop vertical gradient (top, middle, bottom) for every condition, day and night. */
data class SkyPalette(val top: Color, val mid: Color, val bottom: Color)

fun skyPaletteFor(condition: Condition, isDay: Boolean): SkyPalette = if (isDay) {
    when (condition) {
        Condition.CLEAR -> SkyPalette(Color(0xFF1B5FBF), Color(0xFF3E8BDB), Color(0xFF8CC2EE))
        Condition.PARTLY_CLOUDY -> SkyPalette(Color(0xFF2A62A8), Color(0xFF5390CF), Color(0xFF9BC4E6))
        Condition.CLOUDS -> SkyPalette(Color(0xFF45607E), Color(0xFF6E87A1), Color(0xFF9DB0C2))
        Condition.DRIZZLE -> SkyPalette(Color(0xFF3B5168), Color(0xFF5B7188), Color(0xFF8295A8))
        Condition.RAIN -> SkyPalette(Color(0xFF26384B), Color(0xFF3F5468), Color(0xFF61758A))
        Condition.THUNDER -> SkyPalette(Color(0xFF1C1A2E), Color(0xFF34304D), Color(0xFF4F4A69))
        Condition.SNOW -> SkyPalette(Color(0xFF4D6888), Color(0xFF7891AD), Color(0xFFA6B8CC))
        Condition.FOG -> SkyPalette(Color(0xFF5E636D), Color(0xFF80817F), Color(0xFFA39E94))
    }
} else {
    when (condition) {
        Condition.CLEAR -> SkyPalette(Color(0xFF050817), Color(0xFF0F1838), Color(0xFF263064))
        Condition.PARTLY_CLOUDY -> SkyPalette(Color(0xFF080C1E), Color(0xFF151F40), Color(0xFF2C3762))
        Condition.CLOUDS -> SkyPalette(Color(0xFF0C111E), Color(0xFF1B2436), Color(0xFF313D54))
        Condition.DRIZZLE -> SkyPalette(Color(0xFF080D16), Color(0xFF162131), Color(0xFF2A384B))
        Condition.RAIN -> SkyPalette(Color(0xFF05080F), Color(0xFF101A28), Color(0xFF213044))
        Condition.THUNDER -> SkyPalette(Color(0xFF07060F), Color(0xFF171427), Color(0xFF2C2744))
        Condition.SNOW -> SkyPalette(Color(0xFF0E1525), Color(0xFF213049), Color(0xFF3B4A66))
        Condition.FOG -> SkyPalette(Color(0xFF15171C), Color(0xFF282A31), Color(0xFF41434B))
    }
}
