package com.cvke.fridge

import android.content.Intent
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.data.ChangeLogStore

/** The release build has no debug requests and no sample. The debug build has its own file with this name. */
@Suppress("UNUSED_PARAMETER")
object DebugRequests {
    /** Does nothing. The result is false: the change log did not change. */
    suspend fun apply(intent: Intent, changeLog: ChangeLogStore, today: CalendarDate): Boolean = false
}
