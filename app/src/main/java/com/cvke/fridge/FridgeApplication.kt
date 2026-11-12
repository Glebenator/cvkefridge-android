package com.cvke.fridge

import android.app.Application
import com.cvke.fridge.data.ChangeLogStore
import java.io.File

/** Holds the objects that live as long as the app. */
class FridgeApplication : Application() {
    /** The change log in the private files of the app. Backup is off, so it stays on the phone. */
    val changeLog: ChangeLogStore by lazy { ChangeLogStore(File(filesDir, "changes.jsonl")) }
}
