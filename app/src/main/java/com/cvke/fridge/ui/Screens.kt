package com.cvke.fridge.ui

import androidx.navigation3.runtime.NavKey
import com.cvke.fridge.lookup.Scanned
import kotlinx.serialization.Serializable

/**
 * The screens of the app, as the keys of the back stack. Each key has the values that its screen needs to show its
 * content again after Android stops the app in the background.
 */
sealed interface Screen : NavKey {
    /** Home and My Fridge, with the navigation bar. It is the first screen, and it is always in the back stack. */
    @Serializable
    data object Tabs : Screen

    /**
     * The form that adds food.
     *
     * @property productId The product that gets a batch, or null for a form with no name.
     * @property openAreaId The storage area of the tab of My Fridge that was open, or null.
     * @property scan The values of a scan, or null for a form that the person fills in by hand. A form with a scan
     *   has the button "Save and scan next".
     */
    @Serializable
    data class AddFood(val productId: String? = null, val openAreaId: String? = null, val scan: Scanned? = null) : Screen

    /**
     * The barcode scanner, with the explanation of the camera permission and the product lookup.
     *
     * @property openAreaId The storage area of the tab of My Fridge that was open, or null. The add form gets it.
     */
    @Serializable
    data class Scan(val openAreaId: String? = null) : Screen

    /** One product with its batches. */
    @Serializable
    data class Food(val productId: String) : Screen

    /** The form that edits a product. */
    @Serializable
    data class EditProduct(val productId: String) : Screen

    /** The form that edits a batch. */
    @Serializable
    data class EditBatch(val batchId: String) : Screen

    /** The list of the storage areas. */
    @Serializable
    data object StorageAreas : Screen
}
