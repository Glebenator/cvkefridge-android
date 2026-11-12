package com.cvke.fridge

import android.app.Application
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.data.ChangeLogStore
import com.cvke.fridge.data.PhoneId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Holds the objects that live as long as the app. */
class FridgeApplication : Application() {
    /** The change log in the private files of the app. Backup is off, so it stays on the phone. */
    val changeLog: ChangeLogStore by lazy { ChangeLogStore(File(filesDir, "changes.jsonl")) }

    /** The identifier of this phone, in the private files of the app. */
    val phoneId: PhoneId by lazy { PhoneId(File(filesDir, "phone-id")) }

    /**
     * Reads the table of foods. It is the file `spec/foods.json` of the shared specification, which the build puts in
     * the assets.
     *
     * @throws java.io.IOException when the asset cannot be read.
     * @throws IllegalArgumentException when the asset is not a table of foods.
     */
    suspend fun readFoodTable(): FoodTable = withContext(Dispatchers.IO) {
        FoodTable.decode(assets.open("foods.json").use { it.readBytes().decodeToString() })
    }
}
