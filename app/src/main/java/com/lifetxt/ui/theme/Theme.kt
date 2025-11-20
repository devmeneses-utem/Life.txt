package com.lifetxt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Neutral900,
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    outline = Neutral200
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Neutral900,
    background = Neutral900,
    onBackground = Neutral50,
    surface = Neutral800,
    onSurface = Neutral50,
    surfaceVariant = Neutral700,
    outline = Neutral600
)

@Composable
fun LifeTxtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
