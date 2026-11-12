package com.cvke.fridge.ui

import androidx.navigation3.runtime.NavKey
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
     */
    @Serializable
    data class AddFood(val productId: String? = null, val openAreaId: String? = null) : Screen

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
