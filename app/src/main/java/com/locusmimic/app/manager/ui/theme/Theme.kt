package com.locusmimic.app.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Teal90,
    onPrimary = AppBackgroundDark,
    primaryContainer = TealContainerDark,
    onPrimaryContainer = Teal90,
    secondary = Blue85,
    onSecondary = ColorTokens.DarkInk,
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Blue85,
    tertiary = Amber85,
    onTertiary = ColorTokens.DarkInk,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Amber85,
    background = AppBackgroundDark,
    onBackground = AppOnSurfaceDark,
    surface = AppSurfaceDark,
    onSurface = AppOnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = ColorTokens.DarkMuted,
    outline = AppOutlineDark,
    outlineVariant = AppSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = ColorTokens.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = ColorTokens.TealInk,
    secondary = Blue40,
    onSecondary = ColorTokens.White,
    secondaryContainer = BlueContainerLight,
    onSecondaryContainer = ColorTokens.BlueInk,
    tertiary = Amber40,
    onTertiary = ColorTokens.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = ColorTokens.AmberInk,
    background = AppBackgroundLight,
    onBackground = AppOnSurfaceLight,
    surface = AppSurfaceLight,
    onSurface = AppOnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = ColorTokens.LightMuted,
    outline = AppOutlineLight,
    outlineVariant = ColorTokens.LightOutlineVariant
)

private object ColorTokens {
    val White = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val TealInk = androidx.compose.ui.graphics.Color(0xFF002019)
    val BlueInk = androidx.compose.ui.graphics.Color(0xFF071D35)
    val AmberInk = androidx.compose.ui.graphics.Color(0xFF2B1700)
    val DarkInk = androidx.compose.ui.graphics.Color(0xFF002019)
    val DarkMuted = androidx.compose.ui.graphics.Color(0xFFC0C9C2)
    val LightMuted = androidx.compose.ui.graphics.Color(0xFF414943)
    val LightOutlineVariant = androidx.compose.ui.graphics.Color(0xFFC2CBC5)
}

@Composable
fun LocusMimicTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
