package com.cvke.fridge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

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
 * The app theme: a Material 3 theme for the light and the dark appearance of the system, with the Material type scale
 * and shapes.
 *
 * On Android 12 and later, the colour scheme comes from the wallpaper colours of the phone (dynamic colour). On
 * Android 10 and 11, it is the scheme from the green of FridgeFriend. The status colours and the brand colours do not
 * follow the wallpaper: fresh stays green, expiring stays amber, and the signature gradient keeps its colours.
 */
@Composable
fun CvkeFridgeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalBrandColors provides if (darkTheme) DarkBrand else LightBrand,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
