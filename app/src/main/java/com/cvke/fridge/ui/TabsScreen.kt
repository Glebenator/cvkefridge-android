package com.cvke.fridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.StorageArea
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
    SETTINGS(R.drawable.ic_settings, R.drawable.ic_settings_filled, R.string.settings, "tab_settings"),
}

/**
 * Home, My Fridge, History, and Settings: the screen of the selected tab, and the Material navigation bar below it
 * with the add button in its middle. A tab change is instant. Each tab keeps its scroll position, and My Fridge keeps
 * its filters, its search text, and its sort order while another screen is above the tabs. Back on a tab other than
 * Home goes to Home.
 *
 * A tap on My Fridge in the navigation bar starts My Fridge again: the Fridge tab, with no category, no freshness, and
 * no search text. The sort order stays, because it is a preference and not a filter.
 *
 * The add button is the one floating button of the tabs. It opens the form, and the form has a large "Scan a barcode"
 * card at the top. So each way to add food starts from one button. Two floating buttons were too much for one screen.
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
    onTurnOnReminders: () -> Unit,
    onDismissReminderOffer: () -> Unit,
    onPermissionAsked: () -> Unit,
    showRequest: ShowRequest?,
    onShowRequestDone: (id: Long) -> Unit,
    settings: @Composable (insets: PaddingValues) -> Unit,
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
    fun select(entry: Tab) {
        if (entry == Tab.MY_FRIDGE) showMyFridge(FoodFilter(areaId = StorageArea.fridge.id)) else tab = entry
    }
    Scaffold(
        bottomBar = { TabBar(tab, onSelect = ::select, addReady = state is FridgeUiState.Ready, onAdd = ::addFood) },
        // The raised add button goes 20 dp above the bar, so the message keeps clear of it.
        snackbarHost = { NoticeHost(snackbar, Modifier.padding(bottom = RAISE)) },
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
                    Tab.SETTINGS -> settings(insets)
                }
            }
        }
    }
}

/** How far the add button goes above the top edge of the navigation bar. */
private val RAISE = 20.dp

/**
 * The Material navigation bar with the add button in the middle: Home and My Fridge at the start, History and
 * Settings at the end.
 * The add button is larger than a tab and goes above the bar, because it is the main action of the app and not a
 * destination. Its name is below it, at the height of the names of the tabs. The button is there when the inventory
 * is loaded. Before that, the form cannot save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit, addReady: Boolean, onAdd: () -> Unit) {
    @Composable
    fun RowScope.Item(entry: Tab) {
        val selected = entry == tab
        NavigationBarItem(
            selected = selected,
            onClick = { onSelect(entry) },
            icon = { Icon(painterResource(if (selected) entry.selectedIcon else entry.icon), contentDescription = null) },
            modifier = Modifier.testTag(entry.tag),
            label = { Text(stringResource(entry.label)) },
        )
    }
    val add = stringResource(R.string.action_add)
    Box {
        NavigationBar {
            Item(Tab.HOME)
            Item(Tab.MY_FRIDGE)
            // The place of the add button. Its name is in the place of the name of a tab.
            Box(Modifier.weight(1f).align(Alignment.Bottom), contentAlignment = Alignment.BottomCenter) {
                Text(
                    add,
                    Modifier.padding(bottom = 16.dp).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Item(Tab.HISTORY)
            Item(Tab.SETTINGS)
        }
        AnimatedVisibility(
            visible = addReady,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = -RAISE),
            enter = scaleIn(tween(Motion.ENTER_MILLIS, easing = Motion.standard)) + fadeIn(tween(Motion.ENTER_MILLIS)),
            exit = fadeOut(tween(Motion.EXIT_MILLIS)),
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.add_food)) } },
                state = rememberTooltipState(),
            ) {
                FloatingActionButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag("add_food"),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 2.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_food))
                }
            }
        }
    }
}
