package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource

/** What the person selected for the expiry date in a form. */
sealed interface ExpiryInput {
    /** The estimated date of the app. It follows the food, the storage area, and the date added. */
    data object Suggested : ExpiryInput

    /** The printed date that the person entered. The app does not change it. */
    data class Printed(val date: CalendarDate) : ExpiryInput

    /**
     * The date that the person chose because the food was still good (see [LaterExpiry]). It stays a chosen date while
     * the person does not select another date in the form.
     */
    data class Chosen(val date: CalendarDate) : ExpiryInput

    /** No expiry date. */
    data object None : ExpiryInput

    companion object {
        /** The selection that shows the expiry date of a batch as it is. */
        fun of(expiry: ExpiryDate?): ExpiryInput = when (expiry?.source) {
            null -> None
            ExpirySource.PRINTED -> Printed(expiry.date)
            ExpirySource.ESTIMATED -> Suggested
            ExpirySource.CHOSEN -> Chosen(expiry.date)
        }
    }
}

/** The expiry date that a form saves. */
object ExpiryChoice {
    /** The expiry date for the selection. [suggested] is the estimated date, or null when there is no estimate. */
    fun expiry(input: ExpiryInput, suggested: ExpiryDate?): ExpiryDate? = when (input) {
        ExpiryInput.Suggested -> suggested
        is ExpiryInput.Printed -> ExpiryDate(input.date, ExpirySource.PRINTED)
        is ExpiryInput.Chosen -> ExpiryDate(input.date, ExpirySource.CHOSEN)
        ExpiryInput.None -> null
    }

    /**
     * The estimated date for a batch that the person edits. While the storage area and the date added are those of
     * the [original] batch, it is the estimated date that the batch has. After a change of one of them, it is the new
     * [estimate]. When there is no new estimate, the estimated date of the batch stays.
     */
    fun suggestionForEdit(original: Batch, storageAreaId: String, dateAdded: CalendarDate, estimate: ExpiryDate?): ExpiryDate? {
        val kept = original.expiry?.takeIf { it.source == ExpirySource.ESTIMATED }
        val unchanged = storageAreaId == original.storageAreaId && dateAdded == original.dateAdded
        return if (unchanged) kept ?: estimate else estimate ?: kept
    }
}
