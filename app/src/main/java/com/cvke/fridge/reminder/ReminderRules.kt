package com.cvke.fridge.reminder

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.UseFirst

/**
 * One food in a reminder: a product with the batch that needs attention first.
 *
 * @property daysLeft The days left of that batch: -1 for a batch that expired yesterday, 0 for today.
 * @property estimated True when the expiry date of that batch is an estimated date.
 */
data class ReminderFood(val productId: String, val name: String, val daysLeft: Int, val estimated: Boolean)

/**
 * Which batches go into the daily reminder. The shared specification has no rule for reminders yet, so these are the
 * smallest rules that work. They must go into `spec/` of the cvkefridge repository, so that the iOS app gives the same
 * reminder for the same inventory.
 *
 * 1. A batch is in the reminder of a date when it is active, it has an expiry date, and its days left on that date are
 *    from -[EXPIRED_DAYS] to the lead time. With the default lead time, these are the batches that are expiring
 *    (rule 3 of `spec/rules.md`), and the batches that expired yesterday.
 * 2. An expired batch is in the reminder only on the first day after its expiry date. After that day, Home and My
 *    Fridge show it, and the reminder does not name it again each day. A batch that the person adds with an old date
 *    is then not in a reminder.
 * 3. The reminder has one entry for each product, not one for each batch: the batch with the fewest days left, then the
 *    batch that is first in the use-first order.
 * 4. The entries are in the order of their days left, then of their names (case has no effect), then of the product
 *    identifiers.
 */
object ReminderRules {
    /** The number of days after its expiry date that an expired batch is in the reminder. */
    const val EXPIRED_DAYS = 1

    /**
     * The lead times that the person can select, in days before the expiry date. They stop at the last day that is
     * "expiring" in the core rules, because a tap on the reminder opens My Fridge with the filters "Expiring" and
     * "Expired". A longer lead time would name food that this list does not show.
     */
    val leadDayChoices: IntRange = 0..Freshness.expiringWithinDays

    /** The lead time before the person selects one: all batches that are expiring. */
    const val DEFAULT_LEAD_DAYS = Freshness.expiringWithinDays

    /** The foods of the reminder of [today], for a lead time of [leadDays] days. The list is empty when nothing needs a reminder. */
    fun foods(inventory: Inventory, today: CalendarDate, leadDays: Int): List<ReminderFood> {
        val lead = leadDays.coerceIn(leadDayChoices)
        val products = inventory.products.associateBy { it.id }
        val areas = inventory.storageAreas.mapTo(HashSet()) { it.id }
        // A batch is in a reminder only with its product and its storage area, as on the screens.
        val batches = inventory.activeBatches.filter { it.productId in products && it.storageAreaId in areas }
        return UseFirst.order(batches)
            .mapNotNull { batch ->
                val expiry = batch.expiry ?: return@mapNotNull null
                val daysLeft = today.daysUntil(expiry.date)
                if (daysLeft !in -EXPIRED_DAYS..lead) return@mapNotNull null
                ReminderFood(batch.productId, products.getValue(batch.productId).name, daysLeft, expiry.source == ExpirySource.ESTIMATED)
            }
            // The use-first order puts the earlier expiry date first, so the first entry of a product is its first batch.
            .distinctBy { it.productId }
            .sortedWith(
                compareBy<ReminderFood> { it.daysLeft }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.productId },
            )
    }
}
