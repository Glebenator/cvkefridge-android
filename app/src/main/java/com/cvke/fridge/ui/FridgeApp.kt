package com.cvke.fridge.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.cvke.fridge.R
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.NoticeMessage
import com.cvke.fridge.ui.theme.Motion

/**
 * The app: the back stack of the screens, and the snackbar for the result of an action. The first screen has the two
 * tabs. The other screens open above the tabs, with no navigation bar, and the system back gesture closes them with
 * the preview of the screen below. The back stack stays when Android stops the app in the background.
 *
 * The screens get the state of the view model and call it for each action. No screen keeps a copy of the inventory.
 */
@Composable
fun FridgeApp(viewModel: FridgeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formWrite by viewModel.formWrite.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    NoticeSnackbar(notice, snackbar, onAction = viewModel::perform, onShown = viewModel::noticeShown)

    val backStack = rememberNavBackStack(Screen.Tabs)
    fun open(screen: Screen) {
        // Two fast taps do not open the same screen two times.
        if (backStack.lastOrNull() != screen) backStack.add(screen)
    }
    fun close(screen: Screen) {
        backStack.remove(screen)
    }
    val forward = screenChange(towardsEnd = true)
    val back = screenChange(towardsEnd = false)
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        transitionSpec = { forward },
        popTransitionSpec = { back },
        predictivePopTransitionSpec = { back },
        entryProvider = entryProvider<NavKey> {
            entry<Screen.Tabs> {
                TabsScreen(
                    state = state,
                    snackbar = snackbar,
                    onAddFood = { open(Screen.AddFood(openAreaId = it)) },
                    onTryAgain = viewModel::load,
                )
            }
            entry<Screen.AddFood> { screen ->
                AddFoodScreen(
                    screen = screen,
                    state = state,
                    formWrite = formWrite,
                    snackbar = snackbar,
                    onPerform = viewModel::perform,
                    onFormWriteRead = viewModel::formWriteRead,
                    onManageAreas = null,
                    onTryAgain = viewModel::load,
                    onClose = { close(screen) },
                )
            }
        },
    )
}

/**
 * The change from one screen to the next: a short slide with a fade, in the motion values of the design system. A
 * screen that opens comes from the end edge. A screen that closes goes back to it.
 */
private fun screenChange(towardsEnd: Boolean): ContentTransform {
    val spec = tween<Float>(Motion.ENTER_MILLIS, easing = Motion.standard)
    val slide = tween<androidx.compose.ui.unit.IntOffset>(Motion.ENTER_MILLIS, easing = Motion.standard)
    return if (towardsEnd) {
        (slideInHorizontally(slide) { it / 8 } + fadeIn(spec)) togetherWith fadeOut(spec)
    } else {
        fadeIn(spec) togetherWith (slideOutHorizontally(slide) { it / 8 } + fadeOut(spec))
    }
}

/**
 * Shows the message of the view model in the snackbar. A message for an action that the person can undo has the
 * button "Undo". A message for a failed write has the button "Try again". A new message replaces the one on the
 * screen.
 */
@Composable
private fun NoticeSnackbar(notice: Notice?, snackbar: SnackbarHostState, onAction: (InventoryAction) -> Unit, onShown: (Long) -> Unit) {
    val text = notice?.let { noticeText(it.message) }
    val action = notice?.undo ?: notice?.retry
    val label = when {
        notice?.undo != null -> stringResource(R.string.action_undo)
        notice?.retry != null -> stringResource(R.string.action_try_again)
        else -> null
    }
    LaunchedEffect(notice?.id) {
        if (notice == null || text == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(text, label, duration = if (label == null) SnackbarDuration.Short else SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed && action != null) onAction(action)
        onShown(notice.id)
    }
}

@Composable
private fun noticeText(message: NoticeMessage): String = when (message) {
    is NoticeMessage.Added -> stringResource(R.string.notice_added, message.name)
    is NoticeMessage.Taken -> stringResource(
        if (message.outcome == Outcome.USED) R.string.notice_used else R.string.notice_thrown_away,
        quantityText(message.quantity, message.unit),
        message.name,
    )
    is NoticeMessage.FoodDeleted -> stringResource(R.string.notice_deleted, message.name)
    is NoticeMessage.BatchDeleted -> pluralStringResource(R.plurals.notice_batches_deleted, message.count, message.count, message.name)
    is NoticeMessage.StorageAreaDeleted -> stringResource(R.string.notice_deleted, message.name)
    NoticeMessage.SaveFailed -> stringResource(R.string.save_failed)
    is NoticeMessage.Refused -> stringResource(message.reason.message())
}
