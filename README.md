# CVKE Fridge for Android

[![Android CI](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml?query=branch%3Amain)

A native Android app for food inventory in the fridge. It keeps the inventory on one phone: a person adds food by
hand, edits it, and records it as used or as thrown away. It uses Kotlin, Jetpack Compose, and Material 3.

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
  Fridge with that freshness. The two screens have the contents of FridgeFriend (the `basic_ff` repository), with
  Material 3 components. The colours are a Material 3 colour scheme from its green. The app does not use the
  wallpaper colours. The brand parts come from the design system of the owner. The decision record is ADR 0006 in the
  cvkefridge repository.
- **Add food by hand.** The add button on the two tabs opens one short form: the name, the category, the quantity and
  the unit, the storage area, and the expiry date. The name field suggests the foods of the table of foods and the
  products in the inventory. A name of a product in the inventory adds a batch to it. The expiry date starts as the
  estimated date for the food and the storage kind, and the form shows it as an estimate. The person can enter the
  printed date with the Material date picker, or select no expiry date. The date is never today by default. "Save and
  add another" keeps the form open with the storage area.
- **The screen of a food.** A tap on a card opens the product with its batches in the use-first order. The person can
  edit the product (name, category, unit) and each batch (quantity, storage area, date added, expiry date), and add
  a batch.
- **Used and thrown away, with undo.** A swipe on a card records the oldest batch as used (to the end) or as thrown
  away (to the start). The menu of a card and the screen of a food record a quantity that the person types. The
  quantity comes from the oldest batch first. Each of these actions, each delete, and each add shows a snackbar with
  "Undo". A delete is for a mistake, and it is only in a menu.
- **Storage areas.** The menu of My Fridge opens the screen that creates, edits, and deletes storage areas with their
  storage kind. Fridge and Freezer are fixed. A storage area with food cannot be deleted, and the screen says why.
- **The change log.** The inventory is the replay of a change log in the private files of the app. Each action of the
  person becomes changes with random identifiers. The app runs the replay before the write, and it does not write a
  change that the core rejects. A write that fails leaves a form as it is, with a message. The table of foods is
  `spec/foods.json`: the build copies it into the assets.
- **Navigation.** Jetpack Navigation 3 keeps the back stack. The two tabs are the first screen, and the other screens
  open above them with no navigation bar.
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
  (`NewChangesTest`).

  ```sh
  ./gradlew :app:testDebugUnitTest
  ```
- **Device tests** (`app/src/androidTest`) run the real app. A test writes a change log on the device and then starts
  the activity. `InventoryScreensDeviceTest` checks the screens with no food, the totals and the batches of Home, and
  the tabs, the chips, the search field, and the sort control of My Fridge. `InventoryChangesDeviceTest` adds food by
  hand, records food as used with a swipe and undoes it, throws away a quantity, edits a batch, and creates a storage
  area. It reads the screen and the replay of the change log on the device. Gradle removes the debug app from the
  device after these tests, with its change log.

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

Each date of the sample is a number of days from today, so the app calculates the days left as for real food.

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
  data/                                       The change log and the identifier of the phone
  inventory/                                  The actions, the forms, and their checks, in plain Kotlin
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
