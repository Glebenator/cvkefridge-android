package com.cvke.fridge.ui.theme

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import kotlin.random.Random

// The brand parts of My Design Language, the design system of the owner: the signature gradient, the slice of the
// house brand (cvke), and the motion values. The rules for them are in `references/brand-layer.md` and
// `references/motion.md` of the design system. The interface itself stays Material 3.

/**
 * The colours of the brand parts. The blue, the apricot, and the ink are the house colours of the slice
 * (`tokens/tokens.json` of the design system). [gradientEnd] is `gradient-end` in `design/product.css` of the
 * cvkefridge repository: the green blended 70% to apricot.
 */
@Immutable
data class BrandColors(val houseBlue: Color, val apricot: Color, val houseInk: Color, val gradientEnd: Color)

internal val LightBrand = BrandColors(
    houseBlue = Color(0xFF0A5CFF),
    apricot = Color(0xFFD9661F),
    houseInk = Color(0xFF161A1F),
    gradientEnd = Color(0xFFC6904B),
)

internal val DarkBrand = BrandColors(
    houseBlue = Color(0xFF6F9BFF),
    apricot = Color(0xFFF5A578),
    houseInk = Color(0xFFE9EDF1),
    gradientEnd = Color(0xFFDBA75E),
)

/** The motion values of the design system. No motion of the interface is longer than 400 ms. */
object Motion {
    /** Things that arrive, and content after loading. */
    const val ENTER_MILLIS = 340

    /** One direction of the brand loop. */
    const val BRAND_LOOP_MILLIS = 2400

    /** `easing-standard`. */
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/** False when the device has animations off. Then the brand loop and the spinner do not move. */
@Composable
fun motionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

// The signature gradient goes from the green of the product to the green blended to apricot, and back. The two
// colours are the values of the dark appearance in `design/product.css`, because the seed green is too light for a
// shape in white. A shape on the gradient has the ink colour, which has a contrast of 3:1 or more with the two stops.
private val GradientStart = Color(0xFF53D181)
private val GradientEnd = Color(0xFFDBA75E)
private val GradientInk = Color(0xFF161A1F)

/**
 * The stops of the sweep `conic-gradient(from 210deg at 55% 55%, start, end, start)`. A sweep of Compose starts at
 * 3 o'clock, which is 120 degrees after the start of the CSS sweep. `lerp` mixes the colours in Oklab.
 */
private val SweepStops: Array<Pair<Float, Color>> = Array(25) { index ->
    val offset = index / 24f
    val position = ((offset - 1f / 3f) % 1f + 1f) % 1f
    val toEnd = if (position < 0.5f) 2f * position else 2f - 2f * position
    offset to lerp(GradientStart, GradientEnd, toEnd)
}

/** The grain of the gradient: a fine noise at about 7% opacity that makes the colour bands soft. */
private val Grain: ShaderBrush by lazy {
    val side = 64
    val random = Random(7)
    val pixels = IntArray(side * side) { random.nextInt(36) shl 24 }
    val noise = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
}

/** The signature gradient with its grain, as the fill of a [shape]. Use it one time on a screen, with no text on it. */
fun Modifier.brandGradient(shape: Shape): Modifier = clip(shape).drawWithCache {
    val sweep = Brush.sweepGradient(*SweepStops, center = Offset(size.width * 0.55f, size.height * 0.55f))
    onDrawBehind {
        drawRect(sweep)
        drawRect(Grain)
    }
}

/** The icon of the product in the app: the fridge on the signature gradient, in the shape of the house icon. */
@Composable
fun BrandTile(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Box(modifier.size(size).brandGradient(RoundedCornerShape(size * 0.22f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_fridge), contentDescription = null, Modifier.size(size * 0.5f), tint = GradientInk)
    }
}

/**
 * A composition of the house slice, for an empty state: the disc with its slice cut out, the slice beside it, a dot,
 * and a bar. The product has no motif of its own, so the slice is in its place. The slice moves out and back, which
 * is the brand loop of the design system. Use one composition on a screen.
 */
@Composable
fun SliceComposition(modifier: Modifier = Modifier) {
    val brand = CvkeFridgeTheme.brand
    val drift: State<Float> = if (motionEnabled()) {
        rememberInfiniteTransition(label = "brand loop").animateFloat(
            initialValue = 5f,
            targetValue = 11f,
            animationSpec = infiniteRepeatable(tween(Motion.BRAND_LOOP_MILLIS, easing = EaseInOut), RepeatMode.Reverse),
            label = "slice",
        )
    } else {
        remember { mutableFloatStateOf(6f) }
    }
    Canvas(modifier.size(96.dp, 64.dp)) {
        // One unit is 1 dp at the size of 96 x 64 dp.
        val unit = size.width / 96f
        val radius = 22f * unit
        val centre = Offset(26f * unit, 32f * unit)
        val disc = Size(radius * 2, radius * 2)
        // The cut is 70 degrees wide, with its opening to the right.
        drawArc(brand.houseBlue, startAngle = 35f, sweepAngle = 290f, useCenter = true, topLeft = centre - Offset(radius, radius), size = disc)
        drawArc(
            brand.apricot,
            startAngle = -35f,
            sweepAngle = 70f,
            useCenter = true,
            topLeft = centre + Offset(drift.value * unit - radius, -radius),
            size = disc,
        )
        drawCircle(brand.houseInk, radius = 3.5f * unit, center = Offset(84f * unit, 14f * unit))
        drawRoundRect(
            brand.houseInk,
            topLeft = Offset(74f * unit, 46f * unit),
            size = Size(18f * unit, 6f * unit),
            cornerRadius = CornerRadius(3f * unit),
        )
    }
}

/**
 * The spinner of the design system: a ring with an arc that goes from transparent to the green and then to the end
 * colour of the gradient. It shows that the app is busy.
 */
@Composable
fun BrandSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val green = MaterialTheme.colorScheme.primary
    val end = CvkeFridgeTheme.brand.gradientEnd
    val turn: State<Float> = if (motionEnabled()) {
        rememberInfiniteTransition(label = "spinner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "turn",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier.size(size).progressSemantics()) {
        val stroke = this.size.minDimension * 0.12f
        rotate(turn.value) {
            drawArc(
                Brush.sweepGradient(0f to green.copy(alpha = 0f), 0.7f to green, 1f to end),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
        }
    }
}

/**
 * A fade to [color] over the last 40 dp of a row that scrolls sideways, at each edge where the row continues. Put it
 * before `horizontalScroll` in the modifier chain.
 */
fun Modifier.fadingEdges(state: ScrollState, color: Color, width: Dp = 40.dp): Modifier = drawWithContent {
    drawContent()
    val fade = width.toPx()
    val clear = color.copy(alpha = 0f)
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun edge(left: Boolean) {
        val x = if (left) 0f else size.width - fade
        drawRect(
            Brush.horizontalGradient(if (left) listOf(color, clear) else listOf(clear, color), startX = x, endX = x + fade),
            topLeft = Offset(x, 0f),
            size = Size(fade, size.height),
        )
    }
    if (state.canScrollForward) edge(left = rtl)
    if (state.canScrollBackward) edge(left = !rtl)
}
