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
 * The scanner opens the add form above itself. "Save and scan next" closes the form and shows the scanner again.
 * "Save" closes the form and the scanner. "Add by hand" on the scanner opens a form with no scan above the scanner.
 *
 * The screens get the state of the view model and call it for each action. No screen keeps a copy of the inventory.
 */
@Composable
fun FridgeApp(viewModel: FridgeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formWrite by viewModel.formWrite.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val lookup by viewModel.lookup.collectAsStateWithLifecycle()
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
    // A lookup stops when the scanner leaves the back stack, by its close button or by the back gesture. It does not
    // stop when the screen turns.
    val scanning = backStack.any { it is Screen.Scan }
    LaunchedEffect(scanning) { if (!scanning) viewModel.cancelLookup() }
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
                    onScan = { open(Screen.Scan(openAreaId = it)) },
                    onOpenFood = { open(Screen.Food(it)) },
                    onManageAreas = { open(Screen.StorageAreas) },
                    onPerform = viewModel::perform,
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
                    onManageAreas = { open(Screen.StorageAreas) },
                    onScan = {
                        // The scanner takes the place of the empty form.
                        close(screen)
                        open(Screen.Scan(screen.openAreaId))
                    },
                    onTryAgain = viewModel::load,
                    onClose = { close(screen) },
                    onSaved = { scanNext ->
                        close(screen)
                        // "Save" also closes the scanner below the form. "Save and scan next" shows it again.
                        if (!scanNext && backStack.lastOrNull() is Screen.Scan) backStack.removeAt(backStack.lastIndex)
                    },
                )
            }
            entry<Screen.Scan> { screen ->
                ScanScreen(
                    lookup = lookup,
                    snackbar = snackbar,
                    onLookUp = viewModel::lookUp,
                    onCancelLookup = viewModel::cancelLookup,
                    onLookupShown = viewModel::lookupShown,
                    onOpenForm = { scanned, productId -> open(Screen.AddFood(productId, screen.openAreaId, scanned)) },
                    onAddByHand = { open(Screen.AddFood(openAreaId = screen.openAreaId)) },
                    onClose = { close(screen) },
                )
            }
            entry<Screen.Food> { screen ->
                FoodDetailScreen(
                    screen = screen,
                    state = state,
                    snackbar = snackbar,
                    onEditProduct = { open(Screen.EditProduct(screen.productId)) },
                    onEditBatch = { open(Screen.EditBatch(it)) },
                    onAddBatch = { open(Screen.AddFood(productId = screen.productId)) },
                    onPerform = viewModel::perform,
                    onTryAgain = viewModel::load,
                    onBack = { close(screen) },
                )
            }
            entry<Screen.EditProduct> { screen ->
                EditProductScreen(
                    screen = screen,
                    state = state,
                    formWrite = formWrite,
                    snackbar = snackbar,
                    onPerform = viewModel::perform,
                    onFormWriteRead = viewModel::formWriteRead,
                    onTryAgain = viewModel::load,
                    onClose = { close(screen) },
                )
            }
            entry<Screen.EditBatch> { screen ->
                EditBatchScreen(
                    screen = screen,
                    state = state,
                    formWrite = formWrite,
                    snackbar = snackbar,
                    onPerform = viewModel::perform,
                    onFormWriteRead = viewModel::formWriteRead,
                    onManageAreas = { open(Screen.StorageAreas) },
                    onTryAgain = viewModel::load,
                    onClose = { close(screen) },
                )
            }
            entry<Screen.StorageAreas> { screen ->
                StorageAreasScreen(
                    state = state,
                    formWrite = formWrite,
                    snackbar = snackbar,
                    onPerform = viewModel::perform,
                    onFormWriteRead = viewModel::formWriteRead,
                    onTryAgain = viewModel::load,
                    onBack = { close(screen) },
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
