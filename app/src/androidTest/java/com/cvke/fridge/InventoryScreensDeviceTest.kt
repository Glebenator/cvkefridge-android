package com.cvke.fridge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Change
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * The two screens with the real change log on the device. Each test writes the log first and then starts the activity,
 * so the screens read it through the production code path.
 */
class InventoryScreensDeviceTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val changeLog get() = ApplicationProvider.getApplicationContext<FridgeApplication>().changeLog
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun start(changes: List<Change>) {
        runBlocking { changeLog.replace(changes) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun today(): CalendarDate {
        val now = System.currentTimeMillis()
        return checkNotNull(CalendarDate.ofInstant(now, TimeZone.getDefault().getOffset(now) / 1000))
    }

    @After
    fun close() {
        scenario?.close()
        runBlocking { changeLog.replace(emptyList()) }
    }

    @Test
    fun anEmptyInventoryShowsZeroOnHomeAndNoCardOnMyFridge() {
        start(emptyList())
        rule.onNodeWithText("Your Kitchen").assertIsDisplayed()
        rule.onNode(hasTestTag("total_items") and hasText("0")).assertIsDisplayed()
        rule.onNodeWithTag("no_items").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("sample_note").assertCountEquals(0)

        rule.onNodeWithTag("tab_my_fridge").performClick()
        rule.onNodeWithTag("my_fridge_screen").assertIsDisplayed()
        rule.onNodeWithText("No items yet").assertIsDisplayed()

        // About names the product and the maker.
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithTag("about_button").performClick()
        rule.onNodeWithTag("about").assertIsDisplayed()
        rule.onNodeWithText("Made by").assertIsDisplayed()
        rule.onNodeWithContentDescription("cvke").assertIsDisplayed()
    }

    @Test
    fun homeShowsTheTotalsAndTheBatchesThatNeedAttention() {
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("sample_note").assertIsDisplayed()
        rule.onNode(hasTestTag("total_items") and hasText("12")).assertIsDisplayed()
        rule.onNode(hasTestTag("metric_expiring") and hasText("3")).assertIsDisplayed()
        rule.onNode(hasTestTag("metric_expired") and hasText("1")).assertIsDisplayed()

        // The expired batch is first. Its card says that the date is an estimate.
        rule.onNodeWithText("Needs attention").performScrollTo().assertIsDisplayed()
        rule.onNode(
            hasTestTag("attention_sample-batch-1") and hasText("Chicken thighs") and hasText("Expired yesterday, estimated"),
        ).performScrollTo().assertIsDisplayed()
        rule.onNode(hasTestTag("attention_sample-batch-5") and hasText("Expires in 2d")).performScrollTo().assertIsDisplayed()
        // A fresh batch is not on Home.
        rule.onAllNodesWithTag("attention_sample-batch-6").assertCountEquals(0)

        rule.onNodeWithTag("view_all").performScrollTo().performClick()
        rule.onNodeWithTag("my_fridge_screen").assertIsDisplayed()
    }

    @Test
    fun myFridgeFiltersSearchesAndSorts() {
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("tab_my_fridge").performClick()
        val list = rule.onNodeWithTag("my_fridge_screen")

        // The list starts with the food that expires first. One card has both batches of the milk in the Fridge.
        list.performScrollToNode(hasTestTag("food_sample-milk/fridge"))
        rule.onNode(hasTestTag("food_sample-milk/fridge") and hasText("2 expiry dates")).assertIsDisplayed()

        list.performScrollToNode(hasTestTag("search_field"))
        rule.onNodeWithTag("search_field").performTextInput("ket")
        rule.onNodeWithText("Tomato ketchup").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-milk/fridge").assertCountEquals(0)

        rule.onNodeWithTag("chip_area_freezer").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("No matching items").assertIsDisplayed()
        rule.onNodeWithTag("search_clear").performClick()
        rule.onNodeWithText("Garden peas").assertIsDisplayed()
        rule.onNodeWithText("Sourdough bread").assertIsDisplayed()

        rule.onNodeWithTag("chip_category_bakery").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("Sourdough bread").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-peas/freezer").assertCountEquals(0)

        rule.onNodeWithTag("chip_area_all").performScrollTo().performClick()
        rule.onNodeWithTag("chip_category_all").performScrollTo().performClick()
        rule.onNodeWithTag("sort_key").performClick()
        rule.onNodeWithTag("sort_option_name").performClick()
        list.performScrollToNode(hasTestTag("food_sample-spinach/fridge"))
        rule.onNodeWithText("Baby spinach").assertIsDisplayed()

        // Back on My Fridge goes to Home.
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
