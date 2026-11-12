package com.cvke.fridge.inventory

import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind

/**
 * What the person typed and selected in the form of a storage area.
 *
 * @property id The storage area that the form edits, or null for a new one.
 * @property checked True after the person selected "Save" one time. The form then shows the problem of the name.
 */
data class StorageAreaForm(val id: String?, val name: String, val kind: StorageKind, val checked: Boolean = false) {
    /** The problem of the name. Two storage areas cannot have the same name, because a tab shows only the name. */
    fun nameError(areas: List<StorageArea>): NameError? {
        val cleaned = Names.clean(name)
        return when {
            cleaned.isEmpty() -> NameError.EMPTY
            cleaned.length > Names.MAX_AREA_LENGTH -> NameError.TOO_LONG
            areas.any { it.id != id && Names.same(it.name, cleaned) } -> NameError.IN_USE
            else -> null
        }
    }

    /** The action that saves the form, or null when the name has a problem. */
    fun action(areas: List<StorageArea>): InventoryAction.SaveStorageArea? =
        if (nameError(areas) != null) null else InventoryAction.SaveStorageArea(id, Names.clean(name), kind)

    companion object {
        /** The form for a new storage area, or for the [area]. A new storage area starts as a pantry. */
        fun of(area: StorageArea?) = StorageAreaForm(area?.id, area?.name.orEmpty(), area?.kind ?: StorageKind.PANTRY)
    }
}
