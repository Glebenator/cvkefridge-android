package com.cvke.fridge

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.Product
import com.cvke.fridge.photo.PhotoStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * History, "Add again", and the product photo, with the real change log and the real photo folder on the device. The
 * photo comes from [TestPhotoPicker] in the place of the photo picker of Android.
 */
class HistoryDeviceTest : DeviceTest() {
    private fun log(vararg bodies: ChangeBody): List<Change> =
        bodies.mapIndexed { index, body -> Change("t-change-${index + 1}", at = index.toLong(), origin = "test", body = body) }

    private fun product(id: String, name: String, unit: MeasureUnit, foodId: String? = null) =
        ChangeBody.ProductSaved(Product(id, name, Category.DAIRY, unit, null, foodId))

    private fun batch(id: String, product: String, quantity: Long, area: String, added: Int) =
        ChangeBody.BatchAdded(Batch(id, product, quantity, area, checkNotNull(today().plusDays(-added)), null))

    private val photos: File get() = File(app.filesDir, "photos")

    @Test
    fun usedAndThrownAwayFoodIsInHistoryWithItsOutcome() {
        start(
            log(
                product("t-milk", "Milk", MeasureUnit.L),
                product("t-cheese", "Cheese", MeasureUnit.G),
                batch("t-milk-1", "t-milk", 2_000, "fridge", added = 2),
                batch("t-cheese-1", "t-cheese", 200_000, "fridge", added = 2),
            ),
        )
        rule.onNodeWithTag("tab_history").performClick()
        // Nothing ended yet. The screen says so plainly.
        waitFor(hasTestTag("history_empty"))
        rule.onNodeWithText("Nothing used or thrown away yet").assertIsDisplayed()

        // A swipe to the end records the milk as used, and a swipe to the start records the cheese as thrown away.
        rule.onNodeWithTag("tab_my_fridge").performClick()
        rule.onNodeWithTag("food_t-milk/fridge").performTouchInput { swipeRight() }
        waitFor(hasText("2 l of Milk used"))
        // The card of the milk goes, because the milk has no active batch now. On a small screen, the message is above
        // the floating buttons, in front of the middle of the card. So the swipe is near the top edge of the card.
        waitUntilGone(hasTestTag("food_t-milk/fridge"))
        rule.onNodeWithTag("food_t-cheese/fridge").performTouchInput {
            swipe(start = Offset(centerX, top + EDGE_PIXELS), end = Offset(left, top + EDGE_PIXELS))
        }
        waitFor(hasText("200 g of Cheese thrown away"))
        val history = inventory().history
        val milk = history.single { it.productId == "t-milk" }.changeId
        val cheese = history.single { it.productId == "t-cheese" }.changeId

        rule.onNodeWithTag("tab_history").performClick()
        waitFor(hasTestTag("history_summary"))
        rule.onNode(hasTestTag("summary_used") and hasText("1") and hasText("batch used")).assertIsDisplayed()
        rule.onNode(hasTestTag("summary_thrown_away") and hasText("1") and hasText("batch thrown away")).assertIsDisplayed()
        rule.onNodeWithTag("most_thrown_away_t-cheese").assertIsDisplayed()

        // Screen readers hear each entry as one item with its outcome and its day.
        val list = rule.onNodeWithTag("history_screen")
        list.performScrollToNode(hasTestTag("history_day_${today().text}") and hasText("Today"))
        list.performScrollToNode(hasTestTag("history_$milk") and hasContentDescription("Milk, 2 l, Used, Today"))
        list.performScrollToNode(hasTestTag("history_$cheese") and hasContentDescription("Cheese, 200 g, Thrown away, Today"))

        // The segmented buttons show one outcome.
        rule.onNodeWithTag("history_filter_used").performClick()
        waitUntilGone(hasTestTag("history_$cheese"))
        list.performScrollToNode(hasTestTag("history_$milk"))
        rule.onNodeWithTag("history_filter_thrown_away").performClick()
        waitUntilGone(hasTestTag("history_$milk"))
        list.performScrollToNode(hasTestTag("history_$cheese"))
        rule.onNodeWithTag("history_filter_all").performClick()
        list.performScrollToNode(hasTestTag("history_$milk"))

        // A tap on an entry opens the screen of the food, with its history. On a small screen, the message of the throw
        // away can be in front of the entry, so the test uses the click action of the entry, as a screen reader does.
        rule.onNodeWithTag("history_$milk").performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasTestTag("food_screen"))
        rule.onNodeWithTag("no_batches").assertIsDisplayed()
        rule.onNodeWithTag("food_list").performScrollToNode(hasTestTag("food_history_$milk"))
    }

    @Test
    fun addAgainFromAHistoryEntrySavesANewBatchOfTheSameProduct() {
        start(
            log(
                // Yogurt of the table of foods: 7 days in the fridge, 30 days in the freezer.
                product("t-yoghurt", "Yoghurt", MeasureUnit.PCS, foodId = "fk-33"),
                batch("t-yoghurt-1", "t-yoghurt", 4_000, "fridge", added = 20),
                batch("t-yoghurt-2", "t-yoghurt", 4_000, "freezer", added = 10),
                ChangeBody.BatchUsed("t-yoghurt-1", 4_000, checkNotNull(today().plusDays(-18))),
                ChangeBody.BatchThrownAway("t-yoghurt-2", 4_000, checkNotNull(today().plusDays(-2))),
            ),
        )
        val entry = inventory().history.single { it.outcome == Outcome.THROWN_AWAY }.changeId
        rule.onNodeWithTag("tab_history").performClick()
        waitFor(hasTestTag("history_screen"))
        rule.onNodeWithTag("history_screen").performScrollToNode(hasTestTag("add_again_$entry"))
        rule.onNodeWithTag("add_again_$entry").performClick()

        // The form has the product, the storage area of its newest batch, its usual quantity, and an estimate from today.
        waitFor(hasTestTag("add_food_screen"))
        rule.onNodeWithTag("field_name").assertTextContains("Yoghurt")
        rule.onNodeWithTag("name_existing", useUnmergedTree = true).assertTextContains("You had this food before", substring = true)
        rule.onNodeWithTag("field_quantity").assertTextContains("4")
        rule.onNodeWithTag("field_storage_area").assertTextContains("Freezer")
        rule.onNodeWithTag("field_expiry").assertTextContains("estimated", substring = true)
        rule.onNodeWithTag("save").performClick()

        waitFor(hasTestTag("history_screen"))
        val inventory = inventory()
        assertEquals(1, inventory.products.count { it.name == "Yoghurt" })
        val batch = inventory.activeBatches.single()
        assertEquals("t-yoghurt", batch.productId)
        assertEquals(4_000L to "freezer", batch.quantity to batch.storageAreaId)
        assertEquals(today(), batch.dateAdded)
        assertEquals(ExpiryDate(checkNotNull(today().plusDays(30)), ExpirySource.ESTIMATED), batch.expiry)
        assertEquals(emptyList<Any>(), inventory.rejected)
    }

    @Test
    fun aPhotoStaysAfterARestartAndARemoveWithNoUndoDeletesItsFile() {
        start(log(product("t-milk", "Milk", MeasureUnit.L), batch("t-milk-1", "t-milk", 1_000, "fridge", added = 1)))
        fun openMilk() {
            rule.onNodeWithTag("tab_my_fridge").performClick()
            rule.onNodeWithTag("food_t-milk/fridge").performClick()
            waitFor(hasTestTag("food_screen"))
        }
        openMilk()
        rule.onNodeWithTag("photo_add").performClick()
        waitFor(hasTestTag("food_photo"))

        // The photo is a copy in the private files, with its longest edge at 1024 pixels.
        val file = checkNotNull(photos.listFiles()).single()
        assertTrue(file.name, file.name.startsWith("t-milk.") && file.name.endsWith(".jpg"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals(1024 to 768, bounds.outWidth to bounds.outHeight)

        // After a restart, the app finds the photo in its folder.
        scenario?.close()
        assertEquals(setOf("t-milk"), runBlocking { PhotoStore(photos).also { it.read() }.photos.value.keys })
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("add_food"))
        openMilk()
        waitFor(hasTestTag("food_photo"))

        // "Undo" brings the photo back.
        rule.onNodeWithTag("photo_remove").performClick()
        waitFor(hasTestTag("photo_add"))
        rule.onNodeWithText("Undo").performClick()
        waitFor(hasTestTag("food_photo"))

        // With no undo, the file goes when the message closes.
        rule.onNodeWithTag("photo_remove").performClick()
        waitFor(hasTestTag("photo_add"))
        rule.waitUntil(MESSAGE_MILLIS) { photos.list().isNullOrEmpty() }
    }

    private companion object {
        /** A message with "Undo" stays for 10 seconds, or longer with an accessibility service on. */
        const val MESSAGE_MILLIS = 30_000L

        /** The distance of a swipe from the top edge of a card. */
        const val EDGE_PIXELS = 8f
    }
}
