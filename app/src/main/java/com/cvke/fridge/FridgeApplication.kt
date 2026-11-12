package com.cvke.fridge

import android.app.Application
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.data.ChangeLogStore
import com.cvke.fridge.data.PhoneId
import com.cvke.fridge.data.PlatformHttpTransport
import com.cvke.fridge.lookup.HttpTransport
import com.cvke.fridge.lookup.OpenFoodFactsClient
import com.cvke.fridge.lookup.ProductLookup
import com.cvke.fridge.lookup.ProductLookupCache
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Holds the objects that live as long as the app. The device tests use a subclass with a different [httpTransport]. */
open class FridgeApplication : Application() {
    /** The change log in the private files of the app. Backup is off, so it stays on the phone. */
    val changeLog: ChangeLogStore by lazy { ChangeLogStore(File(filesDir, "changes.jsonl")) }

    /** The identifier of this phone, in the private files of the app. */
    val phoneId: PhoneId by lazy { PhoneId(File(filesDir, "phone-id")) }

    /**
     * The product lookup: the cache in the private files of the app, and then Open Food Facts. The User-Agent has the
     * name and the version of the app, as Open Food Facts asks. It has no data about the person.
     */
    val productLookup: ProductLookup by lazy {
        ProductLookup(
            client = OpenFoodFactsClient(httpTransport(), "CVKEFridge-Android/${BuildConfig.VERSION_NAME}"),
            cache = ProductLookupCache(File(filesDir, "product-lookups.json")),
            language = { Locale.getDefault().language },
        )
    }

    /** The connection to the internet for the product lookup. */
    protected open fun httpTransport(): HttpTransport = PlatformHttpTransport()

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
