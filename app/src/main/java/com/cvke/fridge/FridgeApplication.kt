package com.cvke.fridge

import android.app.Application
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.data.ChangeLogStore
import com.cvke.fridge.data.PhoneId
import com.cvke.fridge.data.PlatformHttpTransport
import com.cvke.fridge.lookup.HttpTransport
import com.cvke.fridge.lookup.OpenFoodFactsClient
import com.cvke.fridge.lookup.ProductLookup
import com.cvke.fridge.lookup.ProductLookupCache
import com.cvke.fridge.photo.CameraCaptures
import com.cvke.fridge.photo.PhotoEncoder
import com.cvke.fridge.photo.PhotoLoader
import com.cvke.fridge.photo.PhotoStore
import com.cvke.fridge.photo.PickPhoto
import com.cvke.fridge.reminder.ReminderAlarm
import com.cvke.fridge.reminder.ReminderNotifier
import com.cvke.fridge.reminder.ReminderSettingsSerializer
import com.cvke.fridge.reminder.Reminders
import com.cvke.fridge.settings.AppearanceSettings
import com.cvke.fridge.settings.AppearanceSettingsSerializer
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Holds the objects that live as long as the app. The device tests use a subclass with a different [httpTransport],
 * a different [photoPicker], and a different [photoCamera].
 */
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

    /**
     * The daily reminder. Its settings are in a typed DataStore file in the private files of the app. Backup is off, so
     * they stay on the phone.
     */
    val reminders: Reminders by lazy {
        Reminders(
            store = DataStoreFactory.create(
                serializer = ReminderSettingsSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { ReminderSettingsSerializer.defaultValue },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { File(filesDir, "reminders.json") },
            ),
            changeLog = changeLog,
            notifier = ReminderNotifier(this),
            alarm = ReminderAlarm(this),
        )
    }

    /**
     * The photos of the products, in the folder `photos` of the private files of the app. Backup is off, so they stay
     * on the phone.
     */
    val photos: PhotoStore by lazy {
        PhotoStore(File(filesDir, "photos"), preparedDirectory = File(cacheDir, "prepared-photos"))
    }

    /**
     * The files into which the camera app writes a picture for a product photo, in the cache of the app. The camera
     * app gets them through the FileProvider of the app (`res/xml/file_paths.xml`).
     */
    val cameraCaptures: CameraCaptures by lazy {
        val authority = "$packageName.photos"
        CameraCaptures(File(cacheDir, "camera"), authority, uriOf = { FileProvider.getUriForFile(this, authority, it) })
    }

    /**
     * How the app looks, in a typed DataStore file in the private files of the app. Backup is off, so the settings
     * stay on the phone.
     */
    val appearance: DataStore<AppearanceSettings> by lazy {
        DataStoreFactory.create(
            serializer = AppearanceSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { AppearanceSettingsSerializer.defaultValue },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(filesDir, "appearance.json") },
        )
    }

    /** Makes a product photo from a picture that the person selected. */
    val photoEncoder: PhotoEncoder by lazy { PhotoEncoder(contentResolver) }

    /** Decodes the product photos for the screens, with a cache in memory. */
    val photoLoader: PhotoLoader by lazy { PhotoLoader() }

    /** The connection to the internet for the product lookup. */
    protected open fun httpTransport(): HttpTransport = PlatformHttpTransport()

    /** The source of a picture for a product photo: the photo picker of Android. */
    open fun photoPicker(): ActivityResultContract<Unit, Uri?> = PickPhoto()

    /**
     * Takes a picture for a product photo with the camera app of the phone. The input is the address of the file for
     * the picture. The result is true when the camera app wrote the picture.
     */
    open fun photoCamera(): ActivityResultContract<Uri, Boolean> = ActivityResultContracts.TakePicture()

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
