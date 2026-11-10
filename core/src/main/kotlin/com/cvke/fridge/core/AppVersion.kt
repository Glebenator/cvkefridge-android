package com.cvke.fridge.core

/**
 * The version of the app, as the user sees it.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/AppVersion.swift` must give the same result for the same input.
 *
 * @property name The version name, for example "0.1.0".
 * @property build The build number, for example 1.
 */
data class AppVersion(val name: String, val build: Int) {
    /** The name and the build number, for example "0.1.0 (1)". */
    val label: String
        get() = "$name ($build)"
}
