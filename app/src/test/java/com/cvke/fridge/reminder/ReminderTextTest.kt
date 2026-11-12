package com.cvke.fridge.reminder

import com.cvke.fridge.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element

/**
 * The text of the reminder notification, with the English string resources of `src/main/res/values/strings.xml`. The
 * test reads the resource names from the `R` class of the app, so it checks the resources that the code uses.
 */
class ReminderTextTest {
    private val text = EnglishResources()

    private fun food(name: String, daysLeft: Int, estimated: Boolean = false) = ReminderFood("id-$name", name, daysLeft, estimated)

    @Test
    fun noFoodsGiveNoReminder() {
        assertNull(ReminderText.message(emptyList(), text))
    }

    @Test
    fun oneFoodHasTheSingularTitle() {
        val message = checkNotNull(ReminderText.message(listOf(food("Whole milk", 1)), text))
        assertEquals("1 item needs attention", message.title)
        assertEquals("Whole milk", message.text)
        assertEquals(listOf("Whole milk: expires tomorrow"), message.lines)
    }

    @Test
    fun eachLineSaysTheDaysLeftAndAnEstimate() {
        val message = checkNotNull(
            ReminderText.message(
                listOf(food("Chicken thighs", -1, estimated = true), food("Baby spinach", 0), food("Milk", 1), food("Greek yoghurt", 2)),
                text,
            ),
        )
        assertEquals("4 items need attention", message.title)
        assertEquals(
            listOf(
                "Chicken thighs: expired yesterday, estimated",
                "Baby spinach: expires today",
                "Milk: expires tomorrow",
                "Greek yoghurt: expires in 2 days",
            ),
            message.lines,
        )
        // The collapsed notification names two foods, and counts the others.
        assertEquals("Chicken thighs, Baby spinach and 2 more", message.text)
    }

    @Test
    fun fiveFoodsHaveFiveLinesAndMoreFoodsHaveALastLineWithTheirNumber() {
        val five = (1..5).map { food("Food $it", 0) }
        assertEquals((1..5).map { "Food $it: expires today" }, checkNotNull(ReminderText.message(five, text)).lines)

        val seven = (1..7).map { food("Food $it", 0) }
        val message = checkNotNull(ReminderText.message(seven, text))
        assertEquals("7 items need attention", message.title)
        assertEquals((1..4).map { "Food $it: expires today" } + "And 3 more", message.lines)
        assertEquals("Food 1, Food 2 and 5 more", message.text)

        val six = (1..6).map { food("Food $it", 0) }
        assertEquals("And 2 more", checkNotNull(ReminderText.message(six, text)).lines.last())
        val two = (1..2).map { food("Food $it", 0) }
        assertEquals("Food 1, Food 2", checkNotNull(ReminderText.message(two, text)).text)
        val three = (1..3).map { food("Food $it", 0) }
        assertEquals("Food 1, Food 2 and 1 more", checkNotNull(ReminderText.message(three, text)).text)
    }

    /**
     * The English resources: the strings and the plurals of `values/strings.xml`, with the plural rule of English (the
     * form "one" for 1, and "other" for each other number) and the formats of Android.
     */
    private class EnglishResources : TextSource {
        private val strings = HashMap<String, String>()
        private val plurals = HashMap<String, Map<String, String>>()

        init {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
            val nodes = document.documentElement.childNodes
            for (index in 0 until nodes.length) {
                val node = nodes.item(index) as? Element ?: continue
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "string" -> strings[name] = unescape(node.textContent)
                    "plurals" -> {
                        val items = node.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).associate { item ->
                            val element = items.item(item) as Element
                            element.getAttribute("quantity") to unescape(element.textContent)
                        }
                    }
                }
            }
        }

        override fun string(id: Int, vararg args: Any): String =
            String.format(Locale.ENGLISH, checkNotNull(strings[name(R.string::class.java, id)]), *args)

        override fun plural(id: Int, count: Int, vararg args: Any): String {
            val forms = checkNotNull(plurals[name(R.plurals::class.java, id)])
            return String.format(Locale.ENGLISH, checkNotNull(forms[if (count == 1) "one" else "other"]), *args)
        }

        private fun name(type: Class<*>, id: Int): String = type.fields.first { it.getInt(null) == id }.name

        /**
         * The text as Android reads it: a text in double quotes keeps its spaces, and the spaces at the two ends of
         * other text go away. The escapes `\'` and `\"` become the character.
         */
        private fun unescape(text: String): String {
            val kept = if (text.length >= 2 && text.startsWith('"') && text.endsWith('"')) text.substring(1, text.length - 1) else text.trim()
            return kept.replace("\\'", "'").replace("\\\"", "\"")
        }
    }
}
