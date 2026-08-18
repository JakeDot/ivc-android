package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBackground,
    secondary = TerminalDimGreen,
    onSecondary = TerminalWhite,
    background = TerminalBackground,
    onBackground = TerminalGreen,
    surface = TerminalBackground,
    onSurface = TerminalGreen,
    error = TerminalError,
    onError = TerminalBackground
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
