package dev.krishnagurram.hometodo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The palette from the mockups. One place to change, like CSS custom properties. */
object HomeColors {
    val Ground = Color(0xFFF3F4F2)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF16181A)
    val InkMuted = Color(0xFF5B6168)
    val Hairline = Color(0xFFECEDEA)
    val Ring = Color(0xFF9AA0A6)
    val Accent = Color(0xFF1F5F46)
    val Urgent = Color(0xFFB45309)
}

@Composable
fun HomeTodoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HomeColors.Accent,
            background = HomeColors.Ground,
            surface = HomeColors.Card,
            onBackground = HomeColors.Ink,
            onSurface = HomeColors.Ink,
        ),
        content = content,
    )
}
