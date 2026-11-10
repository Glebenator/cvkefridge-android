# CVKE Fridge for Android

[![Android CI](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/Glebenator/cvkefridge-android/actions/workflows/android.yml?query=branch%3Amain)

A native Android app for food inventory in the fridge. This is a first scaffold. It has one start screen and no
features yet. It uses Kotlin, Jetpack Compose, and Material 3.

CVKE Fridge has three repositories:

| Repository | Contents |
|---|---|
| [cvkefridge](https://github.com/Glebenator/cvkefridge) | The product: plan, glossary, and decisions |
| **cvkefridge-android** | This repository: the Android app |
| [cvkefridge-ios](https://github.com/Glebenator/cvkefridge-ios) | The iOS app, with the same product rules and behaviour |

## Status

- **Scaffold only.** The app shows a start screen with the name, a short note, and the version. It has no other
  feature. The application ID is `com.cvke.fridge`. The debug build has the ID `com.cvke.fridge.debug` and the name
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
- **Device tests** (`app/src/androidTest`) run the real app. `StartScreenDeviceTest` checks that the start screen
  shows the title and the version.

  ```sh
  ./gradlew :app:connectedDebugAndroidTest
  ```

## Project structure

```text
core/src/main/kotlin/com/cvke/fridge/core/   Product rules in pure Kotlin, with no Android dependency
core/src/test/kotlin/com/cvke/fridge/core/   Unit tests, and the tests that run the vectors of spec/
spec/                                         Copy of the shared specification (rules, vectors, food table)
spec.ref                                      The commit of cvkefridge from which spec/ comes
scripts/update-spec.py                        Updates or checks the copy in spec/
app/src/main/java/com/cvke/fridge/
  MainActivity.kt                             The only activity, with edge-to-edge display
  ui/                                         The start screen
  ui/theme/                                   Design tokens, the theme, and shared components
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
