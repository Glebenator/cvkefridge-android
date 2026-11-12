package com.cvke.fridge

import android.content.Intent
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.data.ChangeLogStore

/**
 * Requests that only the debug build accepts, from the intent that starts the activity. No control on a screen makes
 * them. The release build has its own file with this name, which does nothing.
 *
 * ```
 * adb shell am start -n com.cvke.fridge.debug/com.cvke.fridge.MainActivity --es inventory sample
 * adb shell am start -n com.cvke.fridge.debug/com.cvke.fridge.MainActivity --es inventory empty
 * ```
 */
object DebugRequests {
    private const val EXTRA = "inventory"

    /**
     * Replaces the change log with the sample, or removes it. The result is true when the change log changed.
     */
    suspend fun apply(intent: Intent, changeLog: ChangeLogStore, today: CalendarDate): Boolean {
        when (intent.getStringExtra(EXTRA)) {
            "sample" -> changeLog.replace(SampleInventory.changes(today))
            "empty" -> changeLog.replace(emptyList())
            else -> return false
        }
        intent.removeExtra(EXTRA)
        return true
    }
}
