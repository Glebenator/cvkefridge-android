package com.cvke.fridge.reminder

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.cvke.fridge.R

/**
 * The text of the string resources. The app gives the resources of the device. The unit tests give the resources of
 * `values/strings.xml`.
 */
interface TextSource {
    fun string(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/**
 * The text of a reminder notification.
 *
 * @property title The number of foods: "3 items need attention".
 * @property text The first names, for the notification when it is collapsed: "Chicken thighs, Baby spinach and 2 more".
 * @property lines One line for each food when the notification is expanded, and a last line for the foods that do not
 *   fit: "Baby spinach: expires today, estimated".
 */
data class ReminderMessage(val title: String, val text: String, val lines: List<String>)

/** Makes the text of a reminder from its foods, in the order of [ReminderRules.foods]. */
object ReminderText {
    /** The number of names in the collapsed notification. With more, the count of the others does not fit in one line. */
    const val NAMES_IN_TEXT = 2

    /** The largest number of lines in the expanded notification, with the line for the foods that do not fit. */
    const val MAX_LINES = 5

    /** The text for the [foods], or null when there are no foods. A reminder with no foods is not shown. */
    fun message(foods: List<ReminderFood>, text: TextSource): ReminderMessage? {
        if (foods.isEmpty()) return null
        val names = foods.take(NAMES_IN_TEXT).joinToString(text.string(R.string.reminder_name_separator)) { it.name }
        val hiddenNames = foods.size - NAMES_IN_TEXT
        // All foods have a line when they fit. Otherwise the last line says how many foods do not have a line.
        val shown = if (foods.size <= MAX_LINES) foods else foods.take(MAX_LINES - 1)
        val hiddenLines = foods.size - shown.size
        return ReminderMessage(
            title = text.plural(R.plurals.reminder_title, foods.size, foods.size),
            text = if (hiddenNames > 0) text.plural(R.plurals.reminder_names_more, hiddenNames, names, hiddenNames) else names,
            lines = shown.map { line(it, text) } +
                listOfNotNull(hiddenLines.takeIf { it > 0 }?.let { text.plural(R.plurals.reminder_lines_more, it, it) }),
        )
    }

    private fun line(food: ReminderFood, text: TextSource): String {
        val line = when {
            food.daysLeft < 0 -> text.string(R.string.reminder_line_expired, food.name)
            food.daysLeft == 0 -> text.string(R.string.reminder_line_today, food.name)
            food.daysLeft == 1 -> text.string(R.string.reminder_line_tomorrow, food.name)
            else -> text.plural(R.plurals.reminder_line_days, food.daysLeft, food.name, food.daysLeft)
        }
        return if (food.estimated) text.string(R.string.expiry_estimated, line) else line
    }
}
