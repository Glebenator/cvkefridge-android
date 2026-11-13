package com.cvke.fridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.reminder.ReminderSettings
import com.cvke.fridge.ui.theme.Motion

/**
 * The tabs of the app. Home and My Fridge have the names of FridgeFriend. The icon of the selected tab is filled.
 *
 * History is a tab, not a screen that opens from Home or from a menu. It is a destination at the top level: it shows
 * all food that ended, not a part of one food or of one screen. The Material navigation bar is for three to five
 * destinations of the same importance, and a destination in a menu is hard to find.
 */
enum class Tab(
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val label: Int,
    val tag: String,
) {
    HOME(R.drawable.ic_home_outline, R.drawable.ic_home, R.string.tab_home, "tab_home"),
    MY_FRIDGE(R.drawable.ic_fridge_outline, R.drawable.ic_fridge, R.string.tab_my_fridge, "tab_my_fridge"),

    /** The icon has no filled form. The indicator of the navigation bar shows the selection. */
    HISTORY(R.drawable.ic_history, R.drawable.ic_history, R.string.tab_history, "tab_history"),
}

/**
 * Home, My Fridge, and History: the screen of the selected tab, the Material navigation bar below it, and the two
 * buttons that add food. A tab change is instant. Each tab keeps its scroll position, and My Fridge keeps its filters,
 * its search text, and its sort order, also while another screen is above the tabs. Back on My Fridge and on History
 * goes to Home.
 *
 * The two ways to add food are each one tap: the floating action button opens the scanner, and the small floating
 * action button above it opens the form for food with no barcode. A menu would add a tap to each way. A long press on
 * a button shows its name. History does not have the two buttons: a floating action button is for the main action of
 * a screen, and on History the main action is in each entry.
 *
 * The filter of My Fridge is here, because Home opens My Fridge with a filter: "View all" shows all food, and the
 * number of expiring or expired batches shows the food with that freshness in all storage areas. A tap on the reminder
 * notification ([showRequest]) opens My Fridge in the same way, with all storage areas, no search text, and the
 * freshness of the request.
 *
 * @param photos The photo of each product that has one. The cards and the entries show it in the place of the icon.
 * @param onAddFood Opens the form that adds food. It gets the storage area of the tab of My Fridge that is open.
 * @param onScan Opens the scanner. It gets the storage area of the tab of My Fridge that is open.
 * @param onAddAgain Opens the form that adds a batch to a product that the person had before.
 * @param onShowRequestDone My Fridge shows the request with this identifier.
 */
@Composable
fun TabsScreen(
    state: FridgeUiState,
    photos: Map<String, ProductPhoto>,
    snackbar: SnackbarHostState,
    onAddFood: (openAreaId: String?) -> Unit,
    onScan: (openAreaId: String?) -> Unit,
    onOpenFood: (productId: String) -> Unit,
    onAddAgain: (productId: String) -> Unit,
    onManageAreas: () -> Unit,
    onPerform: (InventoryAction) -> Unit,
    onTryAgain: () -> Unit,
    reminders: ReminderSettings?,
    onOpenSettings: () -> Unit,
    onTurnOnReminders: () -> Unit,
    onDismissReminderOffer: () -> Unit,
    onPermissionAsked: () -> Unit,
    showRequest: ShowRequest?,
    onShowRequestDone: (id: Long) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var filter by rememberSaveable(stateSaver = FoodFilterSaver) { mutableStateOf(FoodFilter()) }
    val screens = rememberSaveableStateHolder()
    fun showMyFridge(with: FoodFilter) {
        filter = with
        tab = Tab.MY_FRIDGE
    }
    LaunchedEffect(showRequest) {
        if (showRequest != null) {
            showMyFridge(FoodFilter(freshness = showRequest.freshness))
            onShowRequestDone(showRequest.id)
        }
    }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
    fun openArea() = filter.areaId.takeIf { tab == Tab.MY_FRIDGE }
    fun addFood() = onAddFood(openArea())
    fun scan() = onScan(openArea())

    // The navigation bar is at the bottom edge, so the screens get the insets of the other three edges.
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    val selected = entry == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = entry },
                        icon = { Icon(painterResource(if (selected) entry.selectedIcon else entry.icon), contentDescription = null) },
                        modifier = Modifier.testTag(entry.tag),
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // The buttons are there when the inventory is loaded. Before that, the form cannot save. They grow and
            // shrink when the person goes to History and back, as the Material motion of a floating button does.
            if (state is FridgeUiState.Ready) {
                AnimatedVisibility(
                    visible = tab != Tab.HISTORY,
                    enter = scaleIn(tween(Motion.ENTER_MILLIS, easing = Motion.standard)) + fadeIn(tween(Motion.ENTER_MILLIS)),
                    exit = scaleOut(tween(Motion.ENTER_MILLIS, easing = Motion.standard)) + fadeOut(tween(Motion.ENTER_MILLIS)),
                ) {
                    AddButtons(onScan = ::scan, onAddByHand = ::addFood)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            screens.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        state,
                        photos = photos,
                        onViewAll = { showMyFridge(FoodFilter()) },
                        onShowFreshness = { showMyFridge(FoodFilter(freshness = setOf(it))) },
                        onAddFood = ::addFood,
                        onScan = ::scan,
                        onOpenFood = onOpenFood,
                        onTryAgain = onTryAgain,
                        reminders = reminders,
                        onOpenSettings = onOpenSettings,
                        onTurnOnReminders = onTurnOnReminders,
                        onDismissReminderOffer = onDismissReminderOffer,
                        onPermissionAsked = onPermissionAsked,
                        insets = insets,
                    )
                    Tab.MY_FRIDGE -> MyFridgeScreen(
                        state,
                        photos = photos,
                        filter,
                        onFilterChange = { filter = it },
                        onAddFood = ::addFood,
                        onScan = ::scan,
                        onOpenFood = onOpenFood,
                        onManageAreas = onManageAreas,
                        onPerform = onPerform,
                        onTryAgain = onTryAgain,
                        insets = insets,
                    )
                    Tab.HISTORY -> HistoryScreen(
                        state,
                        photos = photos,
                        onOpenFood = onOpenFood,
                        onAddAgain = onAddAgain,
                        onTryAgain = onTryAgain,
                        insets = insets,
                    )
                }
            }
        }
    }
}

/**
 * The floating action button that opens the scanner, and the small floating action button above it that opens the
 * form. Each has a tooltip with its name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddButtons(onScan: () -> Unit, onAddByHand: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun WithTooltip(label: String, button: @Composable () -> Unit) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Start),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
            content = button,
        )
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val byHand = stringResource(R.string.action_add_by_hand)
        WithTooltip(byHand) {
            SmallFloatingActionButton(
                onClick = onAddByHand,
                modifier = Modifier.testTag("add_food"),
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            ) {
                Icon(painterResource(R.drawable.ic_pencil_plus), contentDescription = byHand)
            }
        }
        val scan = stringResource(R.string.action_scan_barcode)
        WithTooltip(scan) {
            FloatingActionButton(onClick = onScan, Modifier.testTag("scan_barcode")) {
                Icon(painterResource(R.drawable.ic_barcode_scan), contentDescription = scan)
            }
        }
    }
}
