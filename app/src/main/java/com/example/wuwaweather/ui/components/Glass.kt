package com.example.wuwaweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.example.wuwaweather.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.wuwaweather.ui.theme.Velora

val GlassShape = RoundedCornerShape(28.dp)

/** Frosted-glass card: translucent fill with a light gradient edge. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(GlassShape)
            .background(
                Brush.verticalGradient(listOf(Velora.GlassFillStrong, Velora.GlassFill))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x0FFFFFFF))),
                shape = GlassShape
            )
            .padding(contentPadding)
    ) {
        if (title != null) {
            SectionLabel(title)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Velora.TextTertiary,
        modifier = modifier.padding(start = 2.dp)
    )
}
