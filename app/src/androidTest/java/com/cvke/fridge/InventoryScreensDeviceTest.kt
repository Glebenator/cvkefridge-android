package com.cvke.fridge

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Test

/** Home and My Fridge with a change log that the test wrote: what the two screens show, and the filters. */
class InventoryScreensDeviceTest : DeviceTest() {
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
        // On a small screen, Home scrolled down to the empty state. The button is at the top.
        rule.onNodeWithTag("about_button").performScrollTo().performClick()
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
        // The row of cards scrolls sideways in a screen that scrolls down. Bring the row into view first.
        rule.onNodeWithTag("attention_list").performScrollTo()
        rule.onNode(
            hasTestTag("attention_sample-batch-1") and hasText("Chicken thighs") and hasText("Expired yesterday, estimated"),
        ).performScrollTo().assertIsDisplayed()
        rule.onNode(hasTestTag("attention_sample-batch-5") and hasText("Expires in 2d")).performScrollTo().assertIsDisplayed()
        // A fresh batch is not on Home.
        rule.onAllNodesWithTag("attention_sample-batch-6").assertCountEquals(0)

        // The number of expiring batches opens My Fridge with the food that is expiring.
        rule.onNodeWithTag("metric_expiring").performScrollTo().performClick()
        rule.onNodeWithTag("my_fridge_screen").assertIsDisplayed()
        rule.onNodeWithTag("area_all").assertIsSelected()
        rule.onNodeWithTag("filter_expiring").assertIsSelected()
        rule.onNodeWithTag("filter_expired").assertIsNotSelected()
        rule.onNodeWithText("Baby spinach").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-chicken/fridge").assertCountEquals(0)

        // "View all" opens My Fridge with no filter. On a small screen, the button is clear of the two buttons that add
        // food when the row of cards below it is in view.
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithTag("attention_list").performScrollTo()
        rule.onNodeWithTag("view_all").performScrollTo().performClick()
        rule.onNodeWithTag("my_fridge_screen").assertIsDisplayed()
        rule.onNodeWithTag("filter_expiring").assertIsNotSelected()
        rule.onNodeWithText("Chicken thighs").assertIsDisplayed()
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

        // The storage areas are tabs. The search text stays when the tab changes.
        rule.onNodeWithTag("area_freezer").performClick().assertIsSelected()
        rule.onNodeWithText("No matching items").assertIsDisplayed()
        rule.onAllNodesWithTag("filter_clear").assertCountEquals(0)
        rule.onNodeWithTag("search_clear").performClick()
        rule.onNodeWithText("Garden peas").assertIsDisplayed()
        rule.onNodeWithText("Sourdough bread").assertIsDisplayed()

        // The menu of the category chip has the categories with food in the Freezer, with the number of foods.
        rule.onNodeWithTag("filter_category").performClick()
        rule.onNode(hasTestTag("category_all") and hasContentDescription("All categories, 2 items")).assertIsDisplayed()
        rule.onAllNodesWithTag("category_dairy").assertCountEquals(0)
        rule.onNodeWithTag("category_bakery").performClick()
        rule.onNode(hasTestTag("filter_category") and hasText("Bakery")).assertIsSelected()
        rule.onNodeWithText("Sourdough bread").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-peas/freezer").assertCountEquals(0)

        // No bakery food in the Freezer is expired. "Clear filters" shows the food of the Freezer again.
        rule.onNode(hasTestTag("filter_expired") and hasContentDescription("Freshness: Expired, 0 items")).performClick().assertIsSelected()
        rule.onNodeWithText("No matching items").assertIsDisplayed()
        rule.onNodeWithTag("filter_clear").performClick()
        rule.onNodeWithTag("filter_expired").assertIsNotSelected()
        rule.onNode(hasTestTag("filter_category") and hasText("Category")).assertIsNotSelected()
        rule.onNodeWithText("Garden peas").assertIsDisplayed()

        // The two freshness chips together show the food that is expiring or expired.
        rule.onNodeWithTag("area_all").performClick().assertIsSelected()
        rule.onNodeWithTag("filter_expired").performClick()
        rule.onNodeWithText("Chicken thighs").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-spinach/fridge").assertCountEquals(0)
        rule.onNodeWithTag("filter_expiring").performClick()
        rule.onNodeWithText("Chicken thighs").assertIsDisplayed()
        rule.onNodeWithText("Baby spinach").assertIsDisplayed()
        rule.onAllNodesWithTag("food_sample-cheddar/fridge").assertCountEquals(0)
        rule.onNodeWithTag("filter_expired").performClick()
        rule.onNodeWithTag("filter_expiring").performClick()

        rule.onNodeWithTag("sort_key").performClick()
        rule.onNodeWithTag("sort_option_name").performClick()
        list.performScrollToNode(hasTestTag("food_sample-spinach/fridge"))
        rule.onNodeWithText("Baby spinach").assertIsDisplayed()

        // Back on My Fridge goes to Home.
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("home_screen").assertIsDisplayed()
    }
}
