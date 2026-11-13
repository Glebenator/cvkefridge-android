package com.cvke.fridge.history

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome

/**
 * One use or one throw away of a batch, as the history shows it: the history entry of the core with the name, the
 * category, and the unit of its product now.
 *
 * @property changeId The change of the entry. It is different for each entry.
 * @property quantity The quantity that was taken, in thousandths.
 */
data class HistoryLine(
    val changeId: String,
    val batchId: String,
    val productId: String,
    val name: String,
    val category: Category,
    val unit: MeasureUnit?,
    val quantity: Long,
    val outcome: Outcome,
    val date: CalendarDate,
)

/** The entries that the list of the history shows. */
enum class HistoryFilter {
    ALL,
    USED,
    THROWN_AWAY;

    fun accepts(outcome: Outcome): Boolean = when (this) {
        ALL -> true
        USED -> outcome == Outcome.USED
        THROWN_AWAY -> outcome == Outcome.THROWN_AWAY
    }
}

/** How the list names a day: "Today", "Yesterday", or the date. */
enum class DayName { TODAY, YESTERDAY, DATE }

/** One row of the list of the history: the header of a day, or one entry of that day. */
sealed interface HistoryItem {
    /** A key that is different for each row of the list. */
    val key: String

    data class Day(val date: CalendarDate, val name: DayName) : HistoryItem {
        override val key: String get() = "day-${date.text}"
    }

    /** One entry. [day] is the name of its day, for screen readers. */
    data class Entry(val line: HistoryLine, val day: DayName) : HistoryItem {
        override val key: String get() = line.changeId
    }
}

/**
 * The time of the summary: a calendar month in the time zone of the device, or all of the history. The months follow
 * the calendar, so that two summaries do not count the same entry.
 */
enum class HistoryPeriod {
    THIS_MONTH,
    LAST_MONTH,
    ALL;

    /** True when the [date] is in the period, for the date [today]. */
    fun contains(date: CalendarDate, today: CalendarDate): Boolean = when (this) {
        THIS_MONTH -> date.year == today.year && date.month == today.month
        LAST_MONTH -> {
            val year = if (today.month == 1) today.year - 1 else today.year
            val month = if (today.month == 1) 12 else today.month - 1
            date.year == year && date.month == month
        }
        ALL -> true
    }
}

/**
 * The sum of quantities in one unit. A sum never adds two units: 1 l and 500 g are two sums.
 *
 * @property unit The unit, or null for the products that have no unit.
 * @property quantity In thousandths.
 */
data class QuantityTotal(val unit: MeasureUnit?, val quantity: Long)

/**
 * What one outcome came to in a period.
 *
 * @property batchCount The number of batches with an entry of the outcome. A batch with two entries counts one time.
 *   A batch that was partly used and partly thrown away counts for each of the two outcomes.
 * @property quantities The quantities of the entries, one sum for each unit, in the order of the units.
 */
data class OutcomeTotal(val batchCount: Int, val quantities: List<QuantityTotal>)

/** A product, with the number of its batches that were thrown away in a period. */
data class ThrownAwayProduct(val productId: String, val name: String, val category: Category, val batchCount: Int)

/**
 * The summary of a period: what was used, what was thrown away, and the products that were thrown away most often.
 * It has counts and quantities only. The app does not calculate money or other values that it does not have.
 */
data class HistorySummary(val used: OutcomeTotal, val thrownAway: OutcomeTotal, val mostThrownAway: List<ThrownAwayProduct>) {
    /** True when nothing was used or thrown away in the period. */
    val isEmpty: Boolean
        get() = used.batchCount == 0 && thrownAway.batchCount == 0
}

/**
 * What the history shows, calculated from the inventory and the date of today. The history of the core has an entry
 * for each use and each throw away. A delete removes the entries of the deleted batch or product, and an undo removes
 * the entry of the undone change, so they are not here.
 *
 * @property lines All entries, the newest first: the later date first, and on one date the later change first.
 */
data class HistoryContent(
    val lines: List<HistoryLine>,
    private val items: Map<HistoryFilter, List<HistoryItem>>,
    private val summaries: Map<HistoryPeriod, HistorySummary>,
) {
    /** The rows of the list for the [filter]. */
    fun items(filter: HistoryFilter): List<HistoryItem> = items.getValue(filter)

    fun summary(period: HistoryPeriod): HistorySummary = summaries.getValue(period)

    companion object {
        /** The largest number of products in the list of the products that were thrown away most often. */
        const val MOST_THROWN_AWAY = 3

        fun of(inventory: Inventory, today: CalendarDate): HistoryContent {
            val lines = lines(inventory)
            return HistoryContent(
                lines = lines,
                items = HistoryFilter.entries.associateWith { items(lines, it, today) },
                summaries = HistoryPeriod.entries.associateWith { summary(lines, it, today) },
            )
        }

        /** The entries of the history with their products, the newest first. An entry with no product is not shown. */
        fun lines(inventory: Inventory): List<HistoryLine> {
            val products = inventory.products.associateBy { it.id }
            // The history of the core is in the order of the replay. On one date, a later change comes first.
            return inventory.history.withIndex()
                .mapNotNull { (index, entry) ->
                    val product = products[entry.productId] ?: return@mapNotNull null
                    index to HistoryLine(
                        changeId = entry.changeId,
                        batchId = entry.batchId,
                        productId = product.id,
                        name = product.name,
                        category = product.category,
                        unit = product.unit,
                        quantity = entry.quantity,
                        outcome = entry.outcome,
                        date = entry.date,
                    )
                }
                .sortedWith(compareByDescending<Pair<Int, HistoryLine>> { it.second.date }.thenByDescending { it.first })
                .map { it.second }
        }

        /** The rows of the list: for each day with an entry for the [filter], its header and then its entries. */
        fun items(lines: List<HistoryLine>, filter: HistoryFilter, today: CalendarDate): List<HistoryItem> {
            val rows = ArrayList<HistoryItem>()
            var day: CalendarDate? = null
            for (line in lines) {
                if (!filter.accepts(line.outcome)) continue
                val name = dayName(line.date, today)
                if (line.date != day) {
                    rows += HistoryItem.Day(line.date, name)
                    day = line.date
                }
                rows += HistoryItem.Entry(line, name)
            }
            return rows
        }

        /** "Today" and "Yesterday" for these two dates. A date after today, for example after a change of the clock, is a date. */
        fun dayName(date: CalendarDate, today: CalendarDate): DayName = when (today.daysUntil(date)) {
            0 -> DayName.TODAY
            -1 -> DayName.YESTERDAY
            else -> DayName.DATE
        }

        fun summary(lines: List<HistoryLine>, period: HistoryPeriod, today: CalendarDate): HistorySummary {
            val inPeriod = lines.filter { period.contains(it.date, today) }
            return HistorySummary(
                used = total(inPeriod, Outcome.USED),
                thrownAway = total(inPeriod, Outcome.THROWN_AWAY),
                mostThrownAway = mostThrownAway(inPeriod, MOST_THROWN_AWAY),
            )
        }

        /** The batches and the quantities of the entries with the [outcome]. */
        fun total(lines: List<HistoryLine>, outcome: Outcome): OutcomeTotal {
            val matching = lines.filter { it.outcome == outcome }
            return OutcomeTotal(matching.distinctBy { it.batchId }.size, totals(matching))
        }

        /** One sum for each unit, in the order of [MeasureUnit]. The sum of the products with no unit is last. */
        fun totals(lines: List<HistoryLine>): List<QuantityTotal> = lines
            .groupBy { it.unit }
            .map { (unit, group) -> QuantityTotal(unit, group.sumOf { it.quantity }) }
            .sortedBy { it.unit?.ordinal ?: Int.MAX_VALUE }

        /**
         * The products with the most batches that were thrown away, not more than [limit]. With the same number, the
         * product that was thrown away last comes first, and then the name decides.
         */
        fun mostThrownAway(lines: List<HistoryLine>, limit: Int): List<ThrownAwayProduct> = lines
            .filter { it.outcome == Outcome.THROWN_AWAY }
            .groupBy { it.productId }
            .map { (id, group) ->
                val newest = group.maxBy { it.date }
                ThrownAwayProduct(id, newest.name, newest.category, group.distinctBy { it.batchId }.size) to newest.date
            }
            .sortedWith(
                compareByDescending<Pair<ThrownAwayProduct, CalendarDate>> { it.first.batchCount }
                    .thenByDescending { it.second }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name }
                    .thenBy { it.first.productId },
            )
            .take(limit)
            .map { it.first }
    }
}
