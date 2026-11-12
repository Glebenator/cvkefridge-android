package com.cvke.fridge.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cvke.fridge.R

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
 * The app: the screen of the selected tab, and the Material navigation bar below it. A tab change is instant. Each
 * tab keeps its scroll position, and My Fridge keeps its filters, its search text, and its sort order. Back on My
 * Fridge goes to Home.
 *
 * The filter of My Fridge is here, because Home opens My Fridge with a filter: "View all" shows all food, and the
 * number of expiring or expired batches shows the food with that freshness in all storage areas.
 */
@Composable
fun FridgeApp(state: FridgeUiState, onTryAgain: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var filter by rememberSaveable(stateSaver = FoodFilterSaver) { mutableStateOf(FoodFilter()) }
    val screens = rememberSaveableStateHolder()
    fun showMyFridge(with: FoodFilter) {
        filter = with
        tab = Tab.MY_FRIDGE
    }
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    // The navigation bar is at the bottom edge, so the screens get the insets of the other three edges.
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.weight(1f)) {
            screens.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        state,
                        onViewAll = { showMyFridge(FoodFilter()) },
                        onShowFreshness = { showMyFridge(FoodFilter(freshness = setOf(it))) },
                        onTryAgain = onTryAgain,
                        insets = insets,
                    )
                    Tab.MY_FRIDGE -> MyFridgeScreen(state, filter, onFilterChange = { filter = it }, onTryAgain = onTryAgain, insets = insets)
                }
            }
        }
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
    }
}
