package com.arulm.loaner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GoldColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDA1),
    secondary = Color(0xFF6F5B2F),
    tertiary = Color(0xFF4D6543),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val DarkGoldColors = darkColorScheme(
    primary = Color(0xFFFFBA3B),
    onPrimary = Color(0xFF493000),
    primaryContainer = Color(0xFF684600),
    secondary = Color(0xFFDDC38E),
    tertiary = Color(0xFFB3CEA8),
    background = Color(0xFF1F1B16),
    surface = Color(0xFF1F1B16),
)

@Composable fun LoanerTheme(darkTheme: Boolean, content: @Composable () -> Unit) = MaterialTheme(colorScheme = if (darkTheme) DarkGoldColors else GoldColors, content = content)
