package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LabDarkColorScheme = darkColorScheme(
    primary = LabPrimary,
    onPrimary = LabOnPrimary,
    secondary = LabSecondary,
    onSecondary = LabOnSecondary,
    tertiary = LabTertiary,
    onTertiary = LabOnTertiary,
    background = LabBackground,
    onBackground = LabTextPrimary,
    surface = LabSurface,
    onSurface = LabTextPrimary,
    surfaceVariant = LabSurfaceVariant,
    onSurfaceVariant = LabTextSecondary,
    outline = LabBorder,
    error = LabError
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LabDarkColorScheme,
        typography = Typography,
        content = content
    )
}

