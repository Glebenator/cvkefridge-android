# CVKE Fridge for Android

[![Android CI](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml?query=branch%3Amain)

A native Android app for food inventory in the fridge. It keeps the inventory on one phone: a person scans a barcode
or adds food by hand, edits it, records it as used or as thrown away, and sees the history. It uses Kotlin, Jetpack
Compose, and Material 3.

CVKE Fridge has three repositories:

| Repository | Contents |
|---|---|
| [cvkefridge](https://github.com/Glebenator/cvkefridge) | The product: plan, glossary, and decisions |
| **cvkefridge-android** | This repository: the Android app |
| [cvkefridge-ios](https://github.com/Glebenator/cvkefridge-ios) | The iOS app, with the same product rules and behaviour |

## Status

- **Home and My Fridge.** Home shows the totals and the first expired and expiring batches. My Fridge shows one card
  for each food in each storage area, with tabs for the storage areas, a search field, one row of chips for the
  category and the freshness, and a sort control. A tap on the number of expiring or expired batches on Home opens My
  Fridge with that freshness. A tap on My Fridge in the navigation bar opens the Fridge with no filter and no search
  text; the sort order stays. The two screens have the contents of FridgeFriend (the `basic_ff` repository), with
  Material 3 components. On Android 12 and later, the colours come from the wallpaper of the phone, unless the person
  turns "Wallpaper colours" off in Settings. On Android 10 and 11, and with the setting off, they are a Material 3
  colour scheme from the green of FridgeFriend. Fresh is green and expiring is amber with each wallpaper. The brand
  parts come from the design system of the owner. The decision records are ADR 0006, ADR 0007, and ADR 0008 in the
  cvkefridge repository.
- **The navigation bar.** Home, My Fridge, the add button, History, and Settings (ADR 0008). The Material navigation
  bar is for three to five destinations, so it is full.
- **One add button in the middle of the tab bar.** The add button is larger than a tab and goes 20 dp above the
  navigation bar. It opens the add form from each tab. The form starts with a large "Scan a barcode" card that opens
  the scanner. Two floating buttons on one screen were too much. A long press on the button shows its name.
- **Add food by hand.** One short form: the name, the category, the quantity and the unit, the storage area, and the
  expiry date. The name field suggests the foods of the table of foods and the products in the inventory. A name of a
  product in the inventory adds a batch to it. The expiry date starts as the estimated date for the food and the
  storage kind, and the form shows it as an estimate. The person can enter the printed date with the Material date
  picker, or select no expiry date. The date is never today by default. "Save and add another" keeps the form open
  with the storage area. The name field of an empty form has a button that opens the scanner.
- **Add with a barcode.** See [The scanner and the product lookup](#the-scanner-and-the-product-lookup).
- **Reminders.** One notification a day, at a time that the person sets, names the food that expires soon. See
  [Reminders](#reminders).
- **The screen of a food.** A tap on a card opens the product with its batches in the use-first order. The person can
  edit the product (name, category, unit, note) and each batch (quantity, storage area, date added, expiry date), and
  add a batch. Below the batches is the history of the food. The product can have a photo. See [History, add again,
  and the product photo](#history-add-again-and-the-product-photo).
- **Note.** A product can have one note (ADR 0010 in the cvkefridge repository). The screen of the food shows it below
  the product, and a tap edits it in place. A product with no note has "Add a note". The add form has a note field:
  for a food that the person has, it shows the note of that food, a different text replaces it, and an empty field
  keeps it. The card on My Fridge shows the first line, and the search of My Fridge finds it. An empty note on the
  screen of the food or in the edit form is removed (`inventory/Notes`).
- **Still good?** A batch that is expiring or expired has "+1 day", "+1 week", and "Other date" on the screen of
  the food. A date on a package or an estimate is not always right. An expired batch starts from today, so "+1 day"
  means good until tomorrow (`inventory/LaterExpiry`). The message has "Undo". The new date is a chosen date (ADR 0009
  in the cvkefridge repository), and the screen shows it as "Your date". The edit form keeps it while the person does
  not select another date.
- **History.** The third tab shows what was used and what was thrown away, with a summary. See
  [History, add again, and the product photo](#history-add-again-and-the-product-photo).
- **Used and thrown away, with undo.** A swipe on a card records the oldest batch as used (to the end) or as thrown
  away (to the start). The menu of a card and the screen of a food record a quantity that the person types. The
  quantity comes from the oldest batch first. Each of these actions, each delete, and each add shows a message with
  "Undo". A delete is for a mistake, and it is only in a menu.
- **Messages.** The result of an action shows as a small card that floats above the content (`ui/NoticeHost.kt`): an
  icon for the kind of the message (done, deleted, information, problem), the text, and "Undo" or "Try again". It
  comes up from below. A swipe to the side removes it. Its time stops while a finger is on it: 4 seconds, or 8 seconds
  with a button, and longer when the accessibility settings of the device ask for more time. A done or a problem
  message gives a short vibration.
- **Storage areas.** The menu of My Fridge opens the screen that creates, edits, and deletes storage areas with their
  storage kind. Fridge and Freezer are fixed. A storage area with food cannot be deleted, and the screen says why.
- **The change log.** The inventory is the replay of a change log in the private files of the app. Each action of the
  person becomes changes with random identifiers. The app runs the replay before the write, and it does not write a
  change that the core rejects. A write that fails leaves a form as it is, with a message. The table of foods is
  `spec/foods.json`: the build copies it into the assets.
- **Navigation.** Jetpack Navigation 3 keeps the back stack. The three tabs (Home, My Fridge, and History) are the
  first screen, and the other screens open above them with no navigation bar.
- **Application ID.** The application ID is `com.cvke.fridge`. The debug build has the ID `com.cvke.fridge.debug` and the name
  CVKE Fridge Debug, so it can be on the same phone as the Play build.
- **CI.** The badge above shows the result of the newest run on `main`. Select it to see all runs.

### What CI checks

The workflow [`android.yml`](.github/workflows/android.yml) runs for each push to `main` and each pull request, on
`ubuntu-latest` with JDK 21.

| Check | Gradle task | Job |
|---|---|---|
| Copy of the shared specification is equal to the cvkefridge repository | `python3 scripts/update-spec.py --check` | `build` |
| Unit tests of the `:core` module, with the shared test vectors | `:core:test` | `build` |
| Debug build | `:app:assembleDebug` | `build` |
| Device test build | `:app:assembleDebugAndroidTest` | `build` (compiled, not run) |
| Lint, warnings as errors | `:app:lintDebug` | `build` |
| Unit tests of the app | `:app:testDebugUnitTest` | `build` |
| Release APK, minified and unsigned | `:app:assembleRelease` | `build` |
| Release bundle for Google Play, unsigned | `:app:bundleRelease` | `build` |
| Device tests on an emulator (API 35) | `:app:connectedDebugAndroidTest` | `device` |

The reports are in the artifacts `android-reports` and `android-device-reports` of each run.

## The scanner and the product lookup

- **The scanner.** A camera preview (CameraX, `CameraXViewfinder` of `camera-compose`) and the barcode reader of ML Kit
  read EAN-13, EAN-8, UPC-A, and UPC-E all the time. A barcode counts when the camera reads the same number two times
  in one second, and its check digit is correct. One package gives one scan: the scanner ignores the same barcode for
  3 seconds. A read gives a short vibration, and screen readers hear "Looking up barcode …". The top app bar has a
  light button when the camera has a flash. A tap on the picture focuses the camera there. After 10 seconds with no
  barcode, a hint says what helps. On a wide screen, the panel is at the side of the camera.
- **No camera and no permission.** "Type the barcode" and "Add by hand" are always below the camera, so the feature
  works with no camera. Before Android asks for the camera, the screen explains why the app needs it. When the person
  does not allow the camera, the screen says so and can ask again. When Android does not ask again, "Open settings"
  opens the settings of the app, and the screen reads the permission again when the person comes back. The manifest
  declares the camera as not required.
- **The product lookup.** First the inventory: when a product has the barcode, the form adds a batch to it, with no
  network. Then the cache on the phone, and then Open Food Facts (API version 2, HTTPS, only the fields that the app
  uses). The add form opens with the name (the brand and the product name), the category, the food of the table of
  foods when the name clearly names one (then the date is an estimate), and the quantity of the package when it is
  reliable. The form says "From Open Food Facts" and shows the barcode that the food keeps. Each value stays editable.
  "Save and scan next" saves and shows the scanner again, with the message "… added" and "Undo" above the panel, so
  the message does not cover the buttons of the panel. "Save" closes the form and the scanner.
- **Failures.** A barcode that Open Food Facts does not have: the form opens with the barcode, and the empty name has
  the focus. No connection, a timeout, too many lookups, or a server error: the scanner says which, and offers "Try
  again", "Enter by hand" (the form with the barcode), and "Scan again". A typed number with a wrong check digit gets
  a message. The app never sends a lookup again by itself, and it sends not more than 30 lookups in one minute.
- **What leaves the phone.** Only the barcode number, in the request to `world.openfoodfacts.org`. The User-Agent is
  `CVKEFridge-Android/<version>`, with no data about the person. The scanner says this below the camera. A camera
  picture stays in memory for one read. The app writes no names, barcodes, or pictures to logs.
- **The cache.** A found product goes into `product-lookups.json` in the private files of the app (up to 1000
  products). "Not found" and a failed lookup do not go into it. Backup is off, so the cache stays on the phone.
- **The HTTP client.** The lookup is one GET request, so the app uses the HTTPS client of Android
  (`HttpsURLConnection`) with timeouts of 10 seconds, and `kotlinx-serialization-json` for the answer. It has no HTTP
  library. A cancelled lookup closes its connection.
- **ML Kit.** The barcode model is in the app (`com.google.mlkit:barcode-scanning`), so the scanner works with no
  connection and with no Google Play services, and the app downloads no model. It costs about 1 MB of model files and
  a native library of 3 to 6 MB for each ABI. The universal release APK has all four ABIs. A Play download has one.
  ML Kit sends data about its use to Google through the transport "cct" of the Firebase transport runtime. The
  manifest removes that transport, so the runtime does not keep or send the data. Logcat then shows "Transport backend
  'cct' is not registered".
- **Rules for the shared specification.** The shared specification has no rules for barcodes or for the product
  lookup yet. The package `lookup/` has the smallest rules that work, in plain Kotlin with unit tests. They must go
  into `spec/` of the cvkefridge repository, so that the iOS app gives the same results: the one form of a barcode
  and its check digit (`Barcodes`), the category from the tags of Open Food Facts, the food that a name clearly names,
  the name with the brand, the quantity of a package (`ProductMapping.kt`), and one barcode for one product
  (`InventoryActions`).

## History, add again, and the product photo

- **Where History is.** History is the third tab of the navigation bar, after Home and My Fridge. It is a destination
  at the top level: it shows all food that ended, not a part of one food or of one screen. The Material navigation bar
  is for three to five destinations of the same importance, and a destination in a menu or in Settings is hard to
  find. Home and My Fridge do not change. The add button is in the middle of the bar on each tab, also on History.
- **The list.** One entry for each use and each throw away, the newest first, with a header for each day: "Today",
  "Yesterday", or the date. An entry has the photo or the category icon, the name, the quantity with its unit, and the
  outcome in words with its icon: the outcome is never only a colour. Screen readers hear an entry as one item, for
  example "Whole milk, 1 l, Used, Today". A tap opens the screen of the food. Three Material segmented buttons show all
  entries, the used food, or the thrown-away food. They stay at the top of the screen while the list scrolls. A delete
  is for a mistake, so a deleted batch or product is not in the history (rule 7.2 of the shared specification).
- **The summary.** A card at the top counts the batches that were used and the batches that were thrown away in this
  month, the last month, or all of the history, with the quantities: one sum for each unit, so 1 l and 500 g are not
  added. A batch with two entries counts one time. A batch that was partly used and partly thrown away counts for each
  of the two outcomes. The card also names the three foods with the most thrown-away batches. It has counts and
  quantities only: the app has no prices, so it does not show money or other values. A month is a calendar month on
  the phone. With no entry in the period, the card says so.
- **Empty, loading, and failure.** With no history, the screen says "Nothing used or thrown away yet", and it says when
  entries come. A filter with no entry says "Nothing used yet" or "Nothing thrown away yet". While the change log
  loads, the screen shows the spinner. When the log cannot be read, it says so and has "Try again".
- **Speed.** The view model calculates the list and the summaries off the main thread, together with the content of
  Home and My Fridge, so History is ready when it opens. The list is lazy. With a change log of 3000 ended batches,
  History opened at once on the emulator, and it scrolled with the same frame times as My Fridge with the sample. The
  emulator draws with an emulated GPU and the build was the debug build, so this test does not show the frame times
  of a phone.
- **The history of one food.** The screen of a food shows its history below its batches: for each outcome the
  quantity and the number of batches, the first five entries, and "Show all".
- **Add again.** Each history entry has a button that adds the food again, and screen readers have "Add again" as an
  action of the entry. The core keeps a product when all its batches end, so the screen of such a food says "You have
  none of this food now" and has "Add again". "Add again" and "Add a batch" open the add form with the name of the
  product, the storage area of its newest batch (when that storage area is still there), the quantity that most of its
  batches had when they were added, and the estimated date from today. The form never takes a date of an earlier
  batch. A food with no estimate has no expiry date in the form, and the person can enter the printed date. The new
  batch goes to the same product: the app makes no second product.
- **Recent foods.** An empty name field of the add form suggests the three foods that the person had before and has
  none of now, the last one first, below the word "Recent". In the other suggestions, such a food says "You had this
  before". A selected recent food gets its storage area and its usual quantity. A form after a scan does not show
  recent foods.
- **The product photo.** The screen of a food has "Take a photo" and "Choose a photo", and "Change photo" has the
  same two (ADR 0008). "Choose a photo" opens the photo picker of Android (`ActivityResultContracts.PickVisualMedia`,
  pictures only). The picker gives the app only the picture that the person selects, so the app needs no storage
  permission. On a phone with no photo picker, Android opens its document picker, which also needs no permission.
- **Take a photo.** "Take a photo" opens the camera app of the phone (`ActivityResultContracts.TakePicture`). The
  manifest declares the camera permission for the scanner, so Android refuses `ACTION_IMAGE_CAPTURE` until the person
  allows the camera. Before Android asks, a dialog explains why. When Android does not ask again, a dialog says so and
  opens the settings of the app. "Choose a photo" is always there. With no camera or no camera app, only "Choose a
  photo" is there. The camera app writes the picture into a new file in `cache/camera` through the FileProvider of
  the app (`res/xml/file_paths.xml`), which gives it only that one file. The app makes the photo from the file, and
  then deletes the file. A file of a capture that did not come back goes at the next start, when it is older than one
  day. The manifest names `IMAGE_CAPTURE` in `<queries>`, so that Android 11 and later show the app the camera app.
- **A photo in the add form.** The add form has a photo field at its end, for a new food and for a food in the
  inventory with no photo. The app makes the photo at once into `cache/prepared-photos`, and the form keeps its path,
  also when Android stops the app in the background. When the form saves, the product gets the photo. "Remove" and a
  new photo delete the earlier prepared file. The prepared file of a form that closed with no save goes at the next
  start, when it is older than one day.
- **The photo file.** The app reads the picture one time, off the main thread, and keeps no reference to it.
  `ImageDecoder` turns the picture as its EXIF orientation says and decodes it with its longest edge at 1024 pixels,
  so a very large picture needs little memory. The app writes it as a JPEG (quality 85) with no metadata, so the
  location and the time of the picture do not go into it. The file is `photos/<product>.<random>.jpg` in the private
  files of the app. Backup is off, so the photos stay on the phone, and the app never sends them. The picker result
  also arrives after Android stopped the app while the picker was open. While the app makes the photo, the screen says
  "Adding the photo". A picture that cannot be read gives a message, and the product keeps its photo.
- **Change, remove, and delete.** The photo is at the top of the screen of the food, with "Change photo" and "Remove
  photo". A remove has "Undo". A removed photo, and the photo of a deleted product, stay in the folder while the
  message with "Undo" is on the screen, so that "Undo" brings them back. When the message closes, and at each start,
  the app deletes the files that no product needs: removed photos, photos of products that are not in the inventory,
  older photos of a product, and files of a write that stopped.
- **Thumbnails.** The cards of My Fridge, the cards of Home that need attention, the entries of History, and the
  foods that were thrown away most often show the photo in the place of the category icon. The icon shows while the
  photo loads and when the product has no photo. A photo is decorative, because the name of the food is next to it.
- **The loader.** A small loader of the app decodes each photo with a sample size for the size on the screen, off the
  main thread, and keeps the results in an `LruCache` of an eighth of the memory of the app. A photo in the cache is
  there in the first frame. The app does not use Coil: the photos are its own JPEGs of not more than 1024 pixels on
  the phone, so one decode with a sample size and a cache do the work, with no new library and no network code.
- **Rules for the shared specification.** The shared specification has no rules for this piece yet. These rules are
  in plain Kotlin with unit tests, and they must go into `spec/` of the cvkefridge repository, so that the iOS app
  gives the same results: the order, the day headers, the filter, the periods, the summary, and the ranking of the
  thrown-away foods (`history/HistoryContent`), the values of "Add again" and the list of recent foods
  (`inventory/AddAgain`), and the photo of a product. The `Product` of the core has no photo, as the glossary says it
  can have, so the app keeps the photo in its own folder by the identifier of the product (`photo/PhotoFiles`). A
  photo is not in the change log yet.

## Reminders

- **What the reminder says.** Not more than one notification a day, at a time that the person sets (09:00 at the
  start). It names the foods with a batch that expires within the lead time or expired yesterday: "3 items need
  attention" and "Chicken thighs, Baby spinach and 1 more". The expanded notification has one line for each food,
  for example "Expires today, estimated: Baby spinach", up to five lines. The days left are first, because Android
  cuts a long line at its end. A food with more than one batch is one line.
  When no food needs attention, there is no notification. A new reminder replaces the reminder of an earlier day.
- **Lead time.** "On the expiry date", "1 day before", or "2 days before" (the default). Two days is the "expiring"
  rule of the shared specification, so each food in the reminder is in the filters "Expiring" and "Expired" of My
  Fridge. A longer lead time would name food that this list does not show.
- **An expired batch is in one reminder only.** It is in the reminder of the first day after its expiry date. After
  that, Home and My Fridge show it, and the reminder does not name it again each day.
- **A tap on the reminder** opens My Fridge with "Expiring" and "Expired" on, in all storage areas, also when the app
  is open: the screens above the tabs close. The notification goes away after the tap. When the person uses or throws
  away the food in the app, a reminder that is shown changes, or goes away when no food needs attention. The
  notification has no actions: a reminder names more than one food, and "used" or "thrown away" needs a quantity and
  an undo, which the app has and a notification does not. On a locked screen, the notification shows only the number
  of foods.
- **Where the settings are.** Settings is a tab of the navigation bar (ADR 0008). It has the reminders and, on
  Android 12 and later, "Wallpaper colours" (on at the start). The setting is in `appearance.json`, a typed DataStore
  in the private files of the app. The activity waits for this small file before it draws, so the colours do not
  change after the start. The reminder part of the screen has the switch, the time (the
  Material time picker, with the 12-hour or the 24-hour clock of the phone, and a keyboard input), the lead time, the
  date and the time of the next reminder, and the words "The time is approximate". The debug build also has "Send a
  test reminder". The release build does not have this button.
- **The permission.** The app never asks for notifications at the first start. When the inventory has food with an
  expiry date, Home offers reminders once, below the totals ("Turn on" and "Not now"). Before Android asks, a dialog
  explains the reminders. When the person does not allow notifications, reminders stay off, and Home does not offer
  them again: the switch in Settings stays. When Android does not ask again, the switch opens a dialog with "Open
  settings", which opens the notification settings of the app. When the person comes back with notifications on,
  reminders go on. When the person turns off the notifications of the app or the category "Expiry reminders" later,
  Settings says so each time it opens, with the action that fixes it. Before Android 13 there is no question, and the
  same checks apply.
- **The schedule.** An inexact alarm of `AlarmManager` (`setAndAllowWhileIdle`, `RTC_WAKEUP`), not WorkManager and not
  an exact alarm. The Android guidance for "a user-specified action that should happen after a specific time, even if
  the system is in idle state" is this call. Android delivers it not before the time, and on Android 12 and later
  within one hour after it when no battery saving is on. In Doze it can still go off, but later. A job of WorkManager
  waits for a maintenance window in Doze, and Android can delay the jobs of an app that the person does not open for
  days by up to a day. An alarm has a quota of at least one each hour, and one each day for a restricted app. The app
  does not need the permission for exact alarms. When the alarm goes off, a broadcast receiver reads the change log,
  runs the replay of the core, and decides off the main thread, in a few milliseconds. It does not need the app to be
  open that day.
- **Dates and clocks.** The app keeps the date of the next reminder, not an instant. The instant comes from that date,
  the time of the reminder, and the time zone of the device. After a restart of the phone, an update of the app, a
  change of the clock or of the time zone, and each start of the app, the receiver sets the alarm again, so the
  reminder stays at the same time on the clock of the phone, also across daylight saving time. When the phone was
  off at the time of the reminder, the reminder of today comes when the phone is on again; the reminders of earlier
  days do not come. The receiver for these broadcasts is on only while reminders are on.
- **Storage.** A typed DataStore (`androidx.datastore:datastore-core`) with a JSON serializer of
  `kotlinx-serialization`, in `reminders.json` in the private files of the app: one small immutable value with its
  defaults and its checks in one tested place, and no protobuf plugin. A missing value gets its default. A damaged
  file becomes the default settings. Backup is off, so the settings stay on the phone.
- **Rules for the shared specification.** The shared specification has no rule for reminders yet. The package
  `reminder/` has the smallest rules that work, in plain Kotlin with unit tests. They must go into `spec/` of the
  cvkefridge repository, so that the iOS app sends the same reminders: which batches are in the reminder of a date
  and in which order (`ReminderRules`), the lead times and their default, the dates of the schedule (`ReminderSchedule`),
  and the text of the notification (`ReminderText`).

## Build and run

1. Open this folder in Android Studio.
2. Use JDK 21 or later for Gradle. The wrapper uses Gradle 9.6.0 and Android Gradle Plugin 9.4.1.
3. Install Android SDK platform 37. The minimum supported API is 29.
4. Run the `app` configuration on a device or an emulator.

From a terminal:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

On Windows, use `.\gradlew.bat`.

## Release build

Google Play needs an Android App Bundle (AAB) signed with an upload key. Without an upload key, `assembleRelease` and
`bundleRelease` make unsigned output. CI builds them this way.

1. Make an upload key once. Keep it outside this folder, and keep a backup copy and the passwords in a safe place.
2. Make `keystore.properties` in this folder. Git ignores it. It has the keys `storeFile`, `storePassword`,
   `keyAlias`, and `keyPassword`. You can also use the environment variables `CVKE_UPLOAD_STORE_FILE`,
   `CVKE_UPLOAD_STORE_PASSWORD`, `CVKE_UPLOAD_KEY_ALIAS`, and `CVKE_UPLOAD_KEY_PASSWORD`. They have priority over the
   file.
3. Increase `versionCode` in `app/build.gradle.kts` for each upload.
4. Run `./gradlew :app:bundleRelease`. The bundle is `app/build/outputs/bundle/release/app-release.aab`.

## Tests

- **Unit tests** (`core/src/test`) cover the product rules in `:core`. Each file in `spec/vectors/` has one test class,
  for example `CalendarDateVectorsTest`. It runs each case of the file through the core and compares the complete
  expected result. A test fails when its vector file or section has no cases. `ChangeCodecTest` checks the JSON codec
  of the change log. `AppVersionTest` checks the version label.

  ```sh
  ./gradlew :core:test
  ```
- **Unit tests of the app** (`app/src/test`). `InventoryContentTest` checks what the two screens show for an inventory
  and a date: the batches and the totals of Home, and the cards, the filters with their counts, and the sort orders of
  My Fridge. The tests in `inventory/` check the logic of the forms and the actions with no device: the quantity
  text, the food of a name and the estimated date (`AddFoodFormTest`), the edit forms (`EditFormsTest`), the changes
  of each action and their result in the replay (`InventoryActionsTest`), and the instants of new changes
  (`NewChangesTest`). The tests in `lookup/` check the barcodes and their check digit (`BarcodesTest`), the answers of
  Open Food Facts and the request (`OpenFoodFactsTest`, with answers of the API in `src/test/resources/openfoodfacts`),
  the name, the category, the food, and the quantity of a looked-up product (`ProductMappingTest`), and the cache and
  the limit of requests (`ProductLookupTest`). `ScanDebouncerTest` checks which reads of the camera count. The tests in
  `history/` check the order, the days, the filter, the periods, the summary, and the thrown-away foods of the
  history (`HistoryContentTest`). `AddAgainTest` checks the values of "Add again", the recent foods, and the form that
  they make. The tests in `photo/` check the names of the photo files, the files that no product needs, the sizes
  (`PhotoFilesTest`), and the save, remove, undo, new read, and cleanup of the photo store in a folder of the computer
  (`PhotoStoreTest`). The tests in `reminder/` check which batches are in a reminder (`ReminderRulesTest`), its text
  with the English string resources and their plurals (`ReminderTextTest`), the dates and the instants of the schedule
  across midnight, daylight saving time, and time zone changes (`ReminderScheduleTest`), the default settings and their
  file (`ReminderSettingsTest`), and the notification access (`NotificationAccessTest`).

  ```sh
  ./gradlew :app:testDebugUnitTest
  ```
- **Device tests** (`app/src/androidTest`) run the real app. A test writes a change log on the device and then starts
  the activity. `InventoryScreensDeviceTest` checks the screens with no food, the totals and the batches of Home, and
  the tabs, the chips, the search field, and the sort control of My Fridge. `InventoryChangesDeviceTest` adds food by
  hand, records food as used with a swipe and undoes it, throws away a quantity, edits a batch, and creates a storage
  area. It reads the screen and the replay of the change log on the device. `BarcodeDeviceTest` goes from a typed
  barcode to a saved food through the scanner, the lookup, and the form, and checks "not found" and "no connection".
  `InventoryScreensDeviceTest` also checks that a tap on My Fridge in the navigation bar shows the Fridge with no filter.
  The test runner (`DeviceTestRunner`) gives the app a fake server in the place of the internet, so the device tests
  never call Open Food Facts. `HistoryDeviceTest` records food as used and as thrown away and finds it in History
  with its outcome, uses the filter, adds a food again from a history entry and reads the new batch in the change log,
  and gives a product a photo, finds it again after a restart, removes it with undo, and waits until its file is gone.
  It also takes a photo with the camera after the explanation of the permission, checks that the picture of the camera
  is gone, and saves a new food with a photo from the add form. The test runner gives the app a photo picker and a
  camera app with no screen (`TestPhotoPicker`, `TestPhotoCamera`), so the device tests do not drive other apps. The
  camera test gives the app the camera permission with the shell. `SettingsDeviceTest` turns the wallpaper colours off
  in the Settings tab and finds them off after a restart. `BarcodeReaderDeviceTest` draws barcodes in a picture and reads them with the ML
  Kit model of the app. `ReminderDeviceTest` goes from the offer on Home through the explanation to the question of
  Android, closes it with no answer, and turns reminders on through the settings of Android. While it waits for a
  window of Android, it closes a dialog of the system about another app, for example "Play Store keeps stopping". It
  changes the settings and starts the app again, runs the work of the alarm with the real change log, and checks the
  notification and a tap on it. A test cannot take the notification permission away (Android then stops the test), so
  the first test needs a new install: remove the debug app before you run the device tests on your own device. Gradle
  removes the debug app from the device after these tests, with its change log and its photos.

  ```sh
  ./gradlew :app:connectedDebugAndroidTest
  ```

## Sample of the debug build

To see the screens with food in each freshness, the debug build can replace its change log with a sample. The
release build does not have the sample and ignores the request. The screens show that the food is a sample.

```sh
adb shell am start -S -n com.cvke.fridge.debug/com.cvke.fridge.MainActivity --es inventory sample
adb shell am start -S -n com.cvke.fridge.debug/com.cvke.fridge.MainActivity --es inventory empty
```

`-S` stops the app before the start. Without it, Android does not give the request to an app that is open.

Each date of the sample is a number of days from today, so the app calculates the days left as for real food. The
sample also has a history of the last five weeks: food that was used and thrown away, and five foods that the person
had before and has none of now. It has no photos: the photos stay on the phone.

## Project structure

```text
core/src/main/kotlin/com/cvke/fridge/core/   Product rules in pure Kotlin, with no Android dependency
core/src/test/kotlin/com/cvke/fridge/core/   Unit tests, and the tests that run the vectors of spec/
spec/                                         Copy of the shared specification (rules, vectors, food table)
spec.ref                                      The commit of cvkefridge from which spec/ comes
scripts/update-spec.py                        Updates or checks the copy in spec/
app/src/main/java/com/cvke/fridge/
  MainActivity.kt                             The only activity, with edge-to-edge display
  FridgeApplication.kt                        The objects that live as long as the app
  data/                                       The change log, the identifier of the phone, and the HTTPS client
  history/                                    The list and the summary of the history, in plain Kotlin
  inventory/                                  The actions, the forms, "Add again", and their checks, in plain Kotlin
  lookup/                                     Barcodes, the product lookup, its cache, and the values of a scan
  photo/                                      The product photos: the picker, the file rules, the store, and the loader
  reminder/                                   The daily reminder: its rules, its settings, the alarm, and the notification
  scan/                                       The camera and the barcode reader of the scanner
  ui/                                         The screens, the back stack, and the view model
  ui/theme/                                   The colour scheme, the theme, and the brand parts of the design system
```

Each file in `:core` has a file of the same name in the `CVKEFridgeCore` Swift package of cvkefridge-ios, with the same
behaviour and the same test cases.

## Shared specification

The folder `spec/` is a copy of the shared specification in the
[cvkefridge](https://github.com/Glebenator/cvkefridge) repository. It has the core rules (`rules.md`), the test
vectors, and the table of foods. The file `spec.ref` names the commit from which the copy comes. CI checks that the
copy is equal to that commit. To get a new version, run `python scripts/update-spec.py <commit>`. Never edit `spec/`
by hand. To change a rule, change it in the cvkefridge repository first.

## Documents

- The product documents (plan, glossary, and decisions) are in the
  [cvkefridge](https://github.com/Glebenator/cvkefridge) repository.
- [AGENTS.md](AGENTS.md): the rules for work on this app.

Report a problem or a request in [GitHub Issues](https://github.com/Glebenator/cvkefridge-android/issues).
