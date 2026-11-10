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
| Unit tests of the `:core` module | `:core:test` | `build` |
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

- **Unit tests** (`core/src/test`) cover the product rules in `:core`. Today this is `AppVersionTest`.
- **Device tests** (`app/src/androidTest`) run the real app. `StartScreenDeviceTest` checks that the start screen
  shows the title and the version.

  ```sh
  ./gradlew :app:connectedDebugAndroidTest
  ```

## Project structure

```text
core/src/main/kotlin/com/cvke/fridge/core/   Product rules in pure Kotlin, with no Android dependency
app/src/main/java/com/cvke/fridge/
  MainActivity.kt                             The only activity, with edge-to-edge display
  ui/                                         The start screen
  ui/theme/                                   Design tokens, the theme, and shared components
```

Each file in `:core` has a file of the same name in the `CVKEFridgeCore` Swift package of cvkefridge-ios, with the same
behaviour and the same test cases.

## Documents

- The product documents (plan, glossary, and decisions) are in the
  [cvkefridge](https://github.com/Glebenator/cvkefridge) repository.
- [AGENTS.md](AGENTS.md): the rules for work on this app.

Report a problem or a request in [GitHub Issues](https://github.com/Glebenator/cvkefridge-android/issues).
