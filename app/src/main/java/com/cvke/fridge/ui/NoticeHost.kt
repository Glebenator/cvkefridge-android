package com.cvke.fridge.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import com.cvke.fridge.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.delay

/** The kind of a message. It gives the icon of the message, the colour of the icon, and the haptic signal. */
enum class NoticeTone {
    /** The action worked: food added, food used. */
    DONE,

    /** The action deleted something. */
    REMOVED,

    /** Information with no result of an action. */
    INFO,

    /** The action failed, or the app refused it. */
    PROBLEM,
}

/**
 * A message with its [tone]. The [NoticeHost] sets the time on the screen, so the duration here is not used.
 */
class NoticeVisuals(
    override val message: String,
    override val actionLabel: String?,
    val tone: NoticeTone,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/** The time on the screen of a message with no button. */
private const val PLAIN_MILLIS = 4_000L

/** The time on the screen of a message with a button ("Undo", "Try again"). */
private const val ACTION_MILLIS = 8_000L

/**
 * Shows the message of the [state] as a small card that floats above the content: an icon for the kind of the
 * message, the text, and the button of the message. It takes the place of the Material `SnackbarHost`.
 *
 * - The card comes up from below with a fade, and a new message replaces the one on the screen.
 * - A swipe to the side removes the card.
 * - The time does not count down while a finger is on the card. It starts again when the finger goes up, so "Undo"
 *   does not go away below the finger.
 * - The accessibility settings of the device can make the time longer.
 */
@Composable
fun NoticeHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = state.currentSnackbarData
    var held by remember { mutableStateOf(false) }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(current, held) {
        if (current == null || held) return@LaunchedEffect
        val hasAction = current.visuals.actionLabel != null
        val time = if (hasAction) ACTION_MILLIS else PLAIN_MILLIS
        delay(
            accessibility?.calculateRecommendedTimeoutMillis(time, containsIcons = true, containsText = true, containsControls = hasAction)
                ?: time,
        )
        current.dismiss()
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(current) {
        when ((current?.visuals as? NoticeVisuals)?.tone) {
            NoticeTone.DONE -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            NoticeTone.PROBLEM -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }
    val travel = with(LocalDensity.current) { 16.dp.roundToPx() }
    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(tween(Motion.ENTER_MILLIS, easing = Motion.standard)) +
                slideInVertically(tween(Motion.ENTER_MILLIS, easing = Motion.standard)) { travel } +
                scaleIn(tween(Motion.ENTER_MILLIS, easing = Motion.standard), initialScale = 0.94f)
            val exit = fadeOut(tween(Motion.EXIT_MILLIS, easing = Motion.standard)) +
                slideOutVertically(tween(Motion.EXIT_MILLIS, easing = Motion.standard)) { travel }
            (enter togetherWith exit) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomCenter,
        label = "notice",
    ) { data ->
        if (data != null) NoticeCard(data, onHeld = { held = it })
    }
}

@Composable
private fun NoticeCard(data: SnackbarData, onHeld: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tone = (data.visuals as? NoticeVisuals)?.tone ?: NoticeTone.INFO
    val label = data.visuals.actionLabel
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val flickSpeed = with(LocalDensity.current) { 800.dp.toPx() }
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 480.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                translationX = offset
                alpha = 1f - (abs(offset) / width).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onHeld(true)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        onHeld(false)
                    }
                }
            }
            .draggable(
                state = rememberDraggableState { offset += it },
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    // A slow drag of a third of the width removes the card, and so does a fast flick.
                    val flick = abs(velocity) > flickSpeed && sign(velocity) == sign(offset)
                    val away = abs(offset) > width / 3f || flick
                    val target = if (away) sign(offset) * width * 1.2f else 0f
                    animate(offset, target, velocity, tween(Motion.EXIT_MILLIS, easing = Motion.standard)) { value, _ -> offset = value }
                    if (away) data.dismiss()
                },
            )
            .semantics {
                liveRegion = LiveRegionMode.Polite
                dismiss {
                    data.dismiss()
                    true
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).padding(start = 16.dp, end = if (label == null) 20.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(
                    when (tone) {
                        NoticeTone.DONE -> R.drawable.ic_check_circle
                        NoticeTone.REMOVED -> R.drawable.ic_trash
                        NoticeTone.INFO -> R.drawable.ic_info
                        NoticeTone.PROBLEM -> R.drawable.ic_alert
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = when (tone) {
                    NoticeTone.DONE -> CvkeFridgeTheme.status.success
                    NoticeTone.REMOVED -> scheme.onSurfaceVariant
                    NoticeTone.INFO -> scheme.primary
                    NoticeTone.PROBLEM -> scheme.error
                },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                data.visuals.message,
                Modifier.weight(1f, fill = false).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (label != null) {
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(onClick = data::performAction, contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text(label)
                }
            }
        }
    }
}
