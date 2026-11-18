package com.arulm.loaner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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

@Composable fun LoanerTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = GoldColors, content = content)
