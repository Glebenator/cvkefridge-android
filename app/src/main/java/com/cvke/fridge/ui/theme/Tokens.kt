package com.cvke.fridge.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The colours of the app are a Material 3 colour scheme from the green of FridgeFriend, #53D181 (`primary` in
// `styles/theme.ts` of the `basic_ff` repository). They come from material-color-utilities 0.3.0 with the scheme
// "fidelity", which keeps the green as it is in `primaryContainer`. Each pair of a colour and its "on" colour has a
// contrast of 4.5:1 or more. Do not change one value by hand: make the scheme again from the seed colour.

internal val LightScheme = lightColorScheme(
    primary = Color(0xFF006D38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF53D181),
    onPrimaryContainer = Color(0xFF00562B),
    secondary = Color(0xFF3C6848),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBAECC3),
    onSecondaryContainer = Color(0xFF406C4C),
    tertiary = Color(0xFF924B2B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFA37D),
    onTertiaryContainer = Color(0xFF783719),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBF2),
    onBackground = Color(0xFF171D17),
    surface = Color(0xFFF4FBF2),
    onSurface = Color(0xFF171D17),
    surfaceVariant = Color(0xFFD8E6D7),
    onSurfaceVariant = Color(0xFF3D4A3F),
    outline = Color(0xFF6D7A6E),
    outlineVariant = Color(0xFFBCCABC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B322C),
    inverseOnSurface = Color(0xFFECF3E9),
    inversePrimary = Color(0xFF61DE8C),
    surfaceDim = Color(0xFFD5DCD3),
    surfaceBright = Color(0xFFF4FBF2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF6EC),
    surfaceContainer = Color(0xFFE9F0E6),
    surfaceContainerHigh = Color(0xFFE3EAE1),
    surfaceContainerHighest = Color(0xFFDDE4DB),
    surfaceTint = Color(0xFF006D38),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF71EE9B),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF53D181),
    onPrimaryContainer = Color(0xFF00562B),
    secondary = Color(0xFFA2D2AB),
    onSecondary = Color(0xFF09381D),
    secondaryContainer = Color(0xFF265234),
    onSecondaryContainer = Color(0xFF94C49D),
    tertiary = Color(0xFFFFCAB5),
    onTertiary = Color(0xFF571E03),
    tertiaryContainer = Color(0xFFFFA37D),
    onTertiaryContainer = Color(0xFF783719),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E150F),
    onBackground = Color(0xFFDDE4DB),
    surface = Color(0xFF0E150F),
    onSurface = Color(0xFFDDE4DB),
    surfaceVariant = Color(0xFF3D4A3F),
    onSurfaceVariant = Color(0xFFBCCABC),
    outline = Color(0xFF879487),
    outlineVariant = Color(0xFF3D4A3F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDDE4DB),
    inverseOnSurface = Color(0xFF2B322C),
    inversePrimary = Color(0xFF006D38),
    surfaceDim = Color(0xFF0E150F),
    surfaceBright = Color(0xFF343B34),
    surfaceContainerLowest = Color(0xFF09100A),
    surfaceContainerLow = Color(0xFF171D17),
    surfaceContainer = Color(0xFF1B211B),
    surfaceContainerHigh = Color(0xFF252C26),
    surfaceContainerHighest = Color(0xFF303630),
    surfaceTint = Color(0xFF61DE8C),
)

/**
 * The status colours that Material has no role for. They are the "fresh" green and the "expiring" amber of
 * FridgeFriend (#22C55E and #D97706) as Material custom colours, in harmony with the green of the scheme. "Expired"
 * is the error role of the scheme.
 */
@Immutable
data class StatusColors(
    /** Fresh: text and icons on a surface. */
    val success: Color,
    /** Expiring: text and icons on a surface. */
    val warning: Color,
    /** Expiring: the fill of a card or a tile. */
    val warningContainer: Color,
    /** Text and icons on [warningContainer]. */
    val onWarningContainer: Color,
)

internal val LightStatus = StatusColors(
    success = Color(0xFF006D34),
    warning = Color(0xFF835500),
    warningContainer = Color(0xFFFFDDB5),
    onWarningContainer = Color(0xFF2A1800),
)

internal val DarkStatus = StatusColors(
    success = Color(0xFF3EE27B),
    warning = Color(0xFFFFB955),
    warningContainer = Color(0xFF633F00),
    onWarningContainer = Color(0xFFFFDDB5),
)

/** Fixed sizes. */
object Size {
    /** The maximum width of the content of a screen, so that lines stay short on a wide screen. */
    val contentMax = 720.dp

    /**
     * The space at the bottom of a list on the tabs, so that the last item can scroll above the two buttons that add
     * food: the margin of 16 dp, the button of 56 dp, the space of 16 dp, the small button of 40 dp, and 16 dp.
     */
    val addButtonsClearance = 144.dp
}
