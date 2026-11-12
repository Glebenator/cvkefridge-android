package com.cvke.fridge.ui

import androidx.activity.compose.BackHandler
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
import com.cvke.fridge.inventory.InventoryAction

/** The tabs of the app, with the names of FridgeFriend. The icon of the selected tab is filled. */
enum class Tab(
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val label: Int,
    val tag: String,
) {
    HOME(R.drawable.ic_home_outline, R.drawable.ic_home, R.string.tab_home, "tab_home"),
    MY_FRIDGE(R.drawable.ic_fridge_outline, R.drawable.ic_fridge, R.string.tab_my_fridge, "tab_my_fridge"),
}

/**
 * Home and My Fridge: the screen of the selected tab, the Material navigation bar below it, and the two buttons that
 * add food. A tab change is instant. Each tab keeps its scroll position, and My Fridge keeps its filters, its search
 * text, and its sort order, also while another screen is above the tabs. Back on My Fridge goes to Home.
 *
 * The two ways to add food are each one tap: the floating action button opens the scanner, and the small floating
 * action button above it opens the form for food with no barcode. A menu would add a tap to each way. A long press on
 * a button shows its name.
 *
 * The filter of My Fridge is here, because Home opens My Fridge with a filter: "View all" shows all food, and the
 * number of expiring or expired batches shows the food with that freshness in all storage areas.
 *
 * @param onAddFood Opens the form that adds food. It gets the storage area of the tab of My Fridge that is open.
 * @param onScan Opens the scanner. It gets the storage area of the tab of My Fridge that is open.
 */
@Composable
fun TabsScreen(
    state: FridgeUiState,
    snackbar: SnackbarHostState,
    onAddFood: (openAreaId: String?) -> Unit,
    onScan: (openAreaId: String?) -> Unit,
    onOpenFood: (productId: String) -> Unit,
    onManageAreas: () -> Unit,
    onPerform: (InventoryAction) -> Unit,
    onTryAgain: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var filter by rememberSaveable(stateSaver = FoodFilterSaver) { mutableStateOf(FoodFilter()) }
    val screens = rememberSaveableStateHolder()
    fun showMyFridge(with: FoodFilter) {
        filter = with
        tab = Tab.MY_FRIDGE
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
            // The buttons are there when the inventory is loaded. Before that, the form cannot save.
            if (state is FridgeUiState.Ready) AddButtons(onScan = ::scan, onAddByHand = ::addFood)
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            screens.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        state,
                        onViewAll = { showMyFridge(FoodFilter()) },
                        onShowFreshness = { showMyFridge(FoodFilter(freshness = setOf(it))) },
                        onAddFood = ::addFood,
                        onScan = ::scan,
                        onOpenFood = onOpenFood,
                        onTryAgain = onTryAgain,
                        insets = insets,
                    )
                    Tab.MY_FRIDGE -> MyFridgeScreen(
                        state,
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
