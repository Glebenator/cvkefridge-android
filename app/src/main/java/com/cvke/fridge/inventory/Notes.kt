package com.cvke.fridge.inventory

/** The note of a product, as the person typed it. The core keeps a note as it is, so the app cleans it first. */
object Notes {
    /** The note to save: the text without spaces at the start and at the end, or null for an empty text. */
    fun clean(text: String): String? = text.trim().ifEmpty { null }
}
