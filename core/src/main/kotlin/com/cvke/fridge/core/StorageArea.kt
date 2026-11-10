package com.cvke.fridge.core

/**
 * The kind of a storage area.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/StorageArea.swift` must give the same results for the same
 * input.
 *
 * @property key The text of the value in the shared specification.
 */
enum class StorageKind(val key: String) {
    PANTRY("pantry"),
    FRIDGE("fridge"),
    FREEZER("freezer");

    companion object {
        fun fromKey(key: String): StorageKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A place where batches are kept.
 *
 * @property fixed True for the two storage areas that always exist. The user cannot change or delete them.
 */
data class StorageArea(val id: String, val name: String, val kind: StorageKind, val fixed: Boolean) {
    companion object {
        val fridge = StorageArea("fridge", "Fridge", StorageKind.FRIDGE, fixed = true)
        val freezer = StorageArea("freezer", "Freezer", StorageKind.FREEZER, fixed = true)
    }
}
