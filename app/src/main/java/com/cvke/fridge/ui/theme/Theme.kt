package com.cvke.fridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }
private val LocalBrandColors = staticCompositionLocalOf { LightBrand }

/** The colours of the app that have no Material role. All other colours come from `MaterialTheme.colorScheme`. */
object CvkeFridgeTheme {
    val status: StatusColors
        @Composable @ReadOnlyComposable get() = LocalStatusColors.current

    val brand: BrandColors
        @Composable @ReadOnlyComposable get() = LocalBrandColors.current
}

/**
 * The app theme: a Material 3 theme with the colour scheme from the green of FridgeFriend, for the light and the dark
 * appearance of the system, and the Material type scale and shapes. The theme does not use the wallpaper colours,
 * because the green is a part of the look.
 */
@Composable
fun CvkeFridgeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
