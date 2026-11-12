# Project rules: CVKE Fridge for Android

CVKE Fridge has three repositories. This repository is the native Android app.

- [cvkefridge](https://github.com/Glebenator/cvkefridge): the product. The plan, the decisions (ADRs), the glossary,
  and the shared product rules. Product rules there apply to this app.
- [cvkefridge-android](https://github.com/Glebenator/cvkefridge-android): this repository.
- [cvkefridge-ios](https://github.com/Glebenator/cvkefridge-ios): the iOS app.

## Product

- Build a native Android app for publication. The iOS app in cvkefridge-ios has the same product behaviour.
- Use ASD-STE100 Simplified Technical English for instructions and documentation.
- Implement only the agreed feature scope. Do not add inactive controls or simulated production data.
- Commit and push only when the user asks.

## Structure

- Use Kotlin, Jetpack Compose, and Material 3.
- Keep the product rules in the `:core` module. It is pure Kotlin with no Android dependency. Each file in it has a
  file of the same name in the `CVKEFridgeCore` Swift package of cvkefridge-ios, with the same behaviour and the same
  test cases.
- The rules in `:core` follow `spec/rules.md`. `spec/` is a copy of the shared specification in the cvkefridge
  repository. Do not edit it. To change a rule, change it in the cvkefridge repository first. Then run
  `python scripts/update-spec.py <commit>` and make `:core` pass the new vectors.
- Use a ViewModel with immutable UI state and one-way data flow.
- Use constructor injection. Add a dependency injection framework or module only when it solves a current problem.
- Keep platform behaviour native. Share product rules and behaviour with iOS; do not force shared UI code.
- Keep dependencies stable, explicit, and versioned in the version catalog. Do not add unused libraries.
- Use coroutines with a defined scope. Do not use global jobs.

## Design and access

- Use Material components, theme colours, typography, and standard Android navigation.
- Support system light and dark themes, edge-to-edge insets, and screen rotation. The colour scheme comes
  from the green of FridgeFriend, and the app does not use dynamic colour (ADR 0006 in the cvkefridge repository).
  Take colours from `MaterialTheme.colorScheme`, and do not change one colour of the scheme by hand.
- The brand parts are in `ui/theme/Brand.kt`: the signature gradient, the slice, the spinner, and the motion values.
  Their rules are in the design system of the owner (My Design Language) and in `design/product.md` of the cvkefridge
  repository. Use the signature gradient one time on a screen, with no text on it. Do not draw the cvke letters again.
- Support large fonts, screen readers, and touch targets of at least 48 dp.
- Put visible text in string resources. Format numbers for the user's locale.
- Explain permission use before requesting it. Provide a recovery action when a permission is off.

## Performance and privacy

- Keep disk access, network access, and expensive calculations off the main thread.
- Avoid continuous animation and unnecessary UI updates. The two exceptions are the slice in an empty state and the
  spinner (`ui/theme/Brand.kt`). They stop when the device has animations off. Measure before adding performance
  complexity.
- Do not add analytics, network transmission, or cloud backup without an agreed product requirement.

## Tests and delivery

- The `:core` tests run every case of the vectors in `spec/vectors/`. Do not skip a case or weaken a comparison to make
  a test pass.
- Prefer a small set of end-to-end device tests for important user flows.
- Use the production code path in device tests where possible. Do not replace the whole app with a fake.
- Add unit tests only for calculations or failure cases that device tests cannot cover well.
- Do not add template tests, coverage targets, or tests that only repeat the implementation.
- Run the debug build, Android lint, and relevant device tests for functional changes.
- Check the release build before release. Inspect light mode, dark mode, and large text after layout changes.
- Report what was tested, what failed, and what still needs a real device.
- Keep generated output, local SDK paths, signing keys, IDE state, and design exports out of Git.
- The application ID is `com.cvke.fridge`. Do not change it: Google Play keeps it for the life of the app.
- Keep the upload key and `keystore.properties` out of Git. Increase `versionCode` for each upload to Google Play.

## CI

- The workflow `.github/workflows/android.yml` checks the copy of the shared specification, builds the app, runs lint
  and the unit tests, builds the release APK and bundle, and runs the device tests on an emulator for each push to
  `main` and each pull request. Check its result before you report a change as verified.
- The cvkefridge repository starts a progress report with the first feature. Its rules will be in the `AGENTS.md` file
  there.

## Agent skills

### Issue tracker

Issues for the Android app are in GitHub Issues for `Glebenator/cvkefridge-android`. Use the `gh` CLI. A feature that
both apps need has a parent issue in `Glebenator/cvkefridge` and one sub-issue for each app. See
`docs/agents/issue-tracker.md` in the cvkefridge repository.

### Triage labels

Use the five default triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See
`docs/agents/triage-labels.md` in the cvkefridge repository.

### Domain docs

The glossary (`GLOSSARY.md`) and the decisions (`docs/adr/`) are in the cvkefridge repository. See
`docs/agents/domain.md` there.
