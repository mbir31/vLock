package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SkyPrimary = Color(0xFF0284C7)      // Sky blue accent
val SkySecondary = Color(0xFF0EA5E9)    // Bright sky blue
val SkyBackground = Color(0xFFF0F4F8)   // Soft airy light blue-gray
val SkySurface = Color(0xFFFFFFFF)      // Pure white card surface
val SkySurfaceVariant = Color(0xFFE0F2FE)// Soft light sky blue tint
val Slate900 = Color(0xFF0F172A)         // Primary dark text
val Slate500 = Color(0xFF64748B)         // Caption secondary text

private val LightColorScheme = lightColorScheme(
    primary = SkyPrimary,
    primaryContainer = Color(0xFFE0F2FE),
    secondary = SkySecondary,
    secondaryContainer = Color(0xFFBAE6FD),
    background = SkyBackground,
    surface = SkySurface,
    surfaceVariant = SkySurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    onSurfaceVariant = Slate500,
    outline = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Forced Light theme per user request ("Light theme only")
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

