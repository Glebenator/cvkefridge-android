package com.cvke.fridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.StorageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The screens that change the inventory, with the real change log on the device. Each test does what a person does,
 * and then reads the screen and the replay of the change log.
 */
class InventoryChangesDeviceTest : DeviceTest() {
    @Test
    fun aFoodThatIsAddedByHandIsOnMyFridgeAndOnHome() {
        start(emptyList())
        rule.onNodeWithTag("add_food").performClick()
        waitFor(hasTestTag("add_food_screen"))

        // A name with no food in the table has no estimate. "Save and add another" keeps the form open.
        rule.onNodeWithTag("field_name").performTextInput("Rice")
        rule.onNodeWithTag("field_expiry").assertTextContains("No expiry date")
        rule.onNodeWithTag("save_another").performClick()
        waitFor(hasText("Rice added"))
        waitUntilGone(hasTestTag("field_name") and hasText("Rice"))

        // A food of the table gives its category and an estimated date. The date is not today.
        rule.onNodeWithTag("field_name").performTextInput("but")
        waitFor(hasTestTag("suggestion_food_fk-1"))
        rule.onNodeWithTag("suggestion_food_fk-1").performClick()
        rule.onNodeWithTag("field_name").assertTextContains("Butter")
        rule.onNodeWithTag("field_category").assertTextContains("Dairy")
        rule.onNodeWithTag("field_expiry").assertTextContains("estimated", substring = true)
        rule.onNodeWithTag("field_quantity").performTextReplacement("2")
        rule.onNodeWithTag("save").performClick()

        waitFor(hasTestTag("total_items") and hasText("2"))
        rule.onNodeWithTag("tab_my_fridge").performClick()
        rule.onNodeWithText("Butter").assertIsDisplayed()
        rule.onNodeWithText("Rice").assertIsDisplayed()

        val inventory = inventory()
        val butter = inventory.products.single { it.name == "Butter" }
        val batch = inventory.batches.single { it.productId == butter.id }
        assertEquals("fk-1", butter.foodId)
        assertEquals(2_000L, batch.quantity)
        assertEquals(today(), batch.dateAdded)
        assertEquals(ExpirySource.ESTIMATED, batch.expiry?.source)
        assertNotEquals(today(), batch.expiry?.date)
        assertEquals(emptyList<Any>(), inventory.rejected)
    }

    @Test
    fun aSwipeRecordsTheOldestBatchAsUsedAndUndoBringsItBack() {
        start(SampleInventory.changes(today()))
        // The sample has a history. The test reads only the entries that it makes.
        val before = inventory().history
        rule.onNodeWithTag("tab_my_fridge").performClick()
        val milk = hasTestTag("food_sample-milk/fridge")
        rule.onNodeWithTag("my_fridge_screen").performScrollToNode(milk)
        rule.onNode(milk and hasText("3 l", substring = true)).assertIsDisplayed()

        // The oldest batch of the milk has 1 l.
        rule.onNode(milk).performTouchInput { swipeRight() }
        waitFor(hasText("1 l of Whole milk used"))
        waitFor(milk and hasText("2 l", substring = true))
        assertEquals(listOf(Outcome.USED), (inventory().history - before.toSet()).map { it.outcome })

        rule.onNodeWithText("Undo").performClick()
        waitFor(milk and hasText("3 l", substring = true))
        assertEquals(before, inventory().history)
    }

    @Test
    fun theMenuOfACardRecordsAQuantityAsThrownAway() {
        start(SampleInventory.changes(today()))
        val before = inventory().history.toSet()
        rule.onNodeWithTag("tab_my_fridge").performClick()
        val cheddar = hasTestTag("food_sample-cheddar/fridge")
        // The search brings the card to the top of the list, clear of the button that adds food.
        rule.onNodeWithTag("search_field").performTextInput("ched")
        waitFor(cheddar)
        rule.onNodeWithTag("food_menu_sample-cheddar/fridge").performClick()
        rule.onNodeWithTag("menu_thrown_away").performClick()
        waitFor(hasTestTag("take_dialog"))

        // The dialog does not take more than the food has.
        rule.onNodeWithTag("field_quantity").performTextReplacement("400")
        rule.onNodeWithTag("take_confirm").performClick()
        rule.onNodeWithText("You have only 350 g.").assertIsDisplayed()
        rule.onNodeWithTag("field_quantity").performTextReplacement("100")
        rule.onNodeWithTag("take_confirm").performClick()

        waitFor(hasText("100 g of Cheddar thrown away"))
        waitFor(cheddar and hasText("250 g", substring = true))
        val entry = (inventory().history - before).single()
        assertEquals(Outcome.THROWN_AWAY to 100_000L, entry.outcome to entry.quantity)
    }

    @Test
    fun aBatchGetsANewQuantityAndANewStorageArea() {
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("tab_my_fridge").performClick()
        val yoghurt = hasTestTag("food_sample-yoghurt/fridge")
        rule.onNodeWithTag("my_fridge_screen").performScrollToNode(yoghurt)
        rule.onNode(yoghurt).performClick()
        waitFor(hasTestTag("food_screen"))
        rule.onNodeWithText("4 pcs").assertIsDisplayed()

        rule.onNodeWithTag("batch_menu_sample-batch-5").performClick()
        rule.onNodeWithTag("menu_edit_batch").performClick()
        waitFor(hasTestTag("edit_batch_screen"))
        rule.onNodeWithTag("field_quantity").performTextReplacement("6")
        rule.onNodeWithTag("field_storage_area").performClick()
        rule.onNodeWithTag("area_option_freezer").performClick()
        rule.onNodeWithTag("save").performClick()

        // The form closes, and the screen of the food shows the batch with its new values.
        waitFor(hasText("6 pcs"))
        rule.onNodeWithText("Freezer", substring = true).assertIsDisplayed()
        val batch = inventory().batches.single { it.id == "sample-batch-5" }
        assertEquals(6_000L to "freezer", batch.quantity to batch.storageAreaId)
        // The printed date of the batch stays.
        assertEquals(ExpirySource.PRINTED, batch.expiry?.source)
    }

    @Test
    fun aNewStorageAreaHasATabOnMyFridge() {
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("tab_my_fridge").performClick()
        rule.onNodeWithTag("my_fridge_menu").performClick()
        rule.onNodeWithTag("menu_storage_areas").performClick()
        waitFor(hasTestTag("storage_areas_screen"))

        // The name of another storage area is not accepted. Case has no effect.
        rule.onNodeWithTag("add_area").performClick()
        rule.onNodeWithTag("field_area_name").performTextInput("pantry")
        rule.onNodeWithTag("area_save").performClick()
        rule.onNodeWithTag("error_area_name", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("field_area_name").performTextReplacement("Cellar")
        rule.onNodeWithTag("kind_fridge").performClick()
        rule.onNodeWithTag("area_save").performClick()
        waitUntilGone(hasTestTag("area_dialog"))
        waitFor(hasText("Cellar"))
        assertEquals(StorageKind.FRIDGE, inventory().storageAreas.single { it.name == "Cellar" }.kind)

        // A storage area with food cannot be deleted. The screen says why.
        rule.onNodeWithTag("area_menu_sample-pantry").performClick()
        rule.onNodeWithTag("menu_delete_area").performClick()
        waitFor(hasTestTag("area_in_use"))
        rule.onNodeWithText("OK").performClick()
        waitUntilGone(hasTestTag("area_in_use"))

        rule.onNodeWithTag("back").performClick()
        waitFor(hasTestTag("my_fridge_screen"))
        rule.onNodeWithText("Cellar").performClick().assertIsSelected()
        rule.onNodeWithText("No items yet").assertIsDisplayed()
    }

    @Test
    fun anExpiredFoodThatIsStillGoodGetsALaterExpiryDateWithUndo() {
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("tab_my_fridge").performClick()
        rule.onNodeWithTag("food_sample-chicken/fridge").performClick()
        waitFor(hasTestTag("food_screen"))
        val inBatch = hasAnyAncestor(hasTestTag("batch_sample-batch-1"))
        rule.onNode(inBatch and hasText("Expired yesterday, estimated", substring = true)).assertIsDisplayed()

        // An expired batch starts from today: one more day is good until tomorrow.
        rule.onNodeWithTag("later_expiry_1").performClick()
        waitFor(inBatch and hasText("Expires tomorrow", substring = true))
        assertEquals(ExpiryDate(checkNotNull(today().plusDays(1)), ExpirySource.CHOSEN), inventory().batches.single { it.id == "sample-batch-1" }.expiry)
        // The new date is not from the package, and the screen does not say so.
        rule.onNode(inBatch and hasText("Your date", substring = true)).assertIsDisplayed()

        rule.onNodeWithText("Undo").performClick()
        waitFor(inBatch and hasText("Expired yesterday, estimated", substring = true))
        assertEquals(ExpirySource.ESTIMATED, inventory().batches.single { it.id == "sample-batch-1" }.expiry?.source)
    }
}
