# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this app is

Wear OS app ("Resting HR", `com.simonludwig.restinghr`), single Gradle module `:app`, one screen.

The user taps **Start measuring** and sits still for three minutes. Nothing is shown but a spinner —
deliberately no countdown. Only the **last 30 seconds** are averaged into the result; the sensor is
switched on 20 s before that window so it is locked on when readings start counting. The settling
period is what makes the number a *resting* rate, so those timings are the feature, not arbitrary
constants. They live in one place: `MeasureRestingHeartRateUseCase.Companion`.

The Android Studio template's tile and complication have been deleted — this is a single-surface app,
and the tiles/protolayout/complications dependencies are gone with them.

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected watch/emulator (adb is on PATH)
./gradlew testDebugUnitTest      # unit tests
./gradlew lint                   # Android Lint; report at app/build/reports/lint-results-debug.html
./gradlew build                  # assemble + lint + tests
```

```bash
# single unit test class or method
./gradlew testDebugUnitTest --tests 'com.simonludwig.restinghr.domain.MeasureRestingHeartRateUseCaseTest'
./gradlew testDebugUnitTest --tests '*MeasureRestingHeartRateUseCaseTest.takes exactly three minutes'
```

There are no instrumentation tests (`app/src/androidTest` does not exist), though
`androidTestImplementation` deps for Compose UI tests are declared.

### Testing the sensor on an emulator

The emulator has no real heart-rate sensor, so a measurement correctly ends in "no readings" unless
Health Services is fed synthetic data:

```bash
adb shell am broadcast -a "whs.USE_SYNTHETIC_PROVIDERS" com.google.android.wearable.healthservices
adb shell am broadcast -a "whs.synthetic.user.START_EXERCISE" \
  --ei exercise_options_heart_rate 62 com.google.android.wearable.healthservices
adb shell am broadcast -a "whs.USE_SENSOR_PROVIDERS" com.google.android.wearable.healthservices
```

## Architecture

Three thin layers under `com.simonludwig.restinghr`, one class each — deliberately no mappers, no
repository wrapping a repository, no second module:

- `domain/` — `HeartRateSensor` (interface), `HeartRateSample`, and
  `MeasureRestingHeartRateUseCase`, which owns the whole protocol: lead-in, warm-up, 30 s window,
  reliability filter, average. **It contains no Android or Health Services types**, which is why
  `app/src/test` is a plain JVM source set with a hand-written `FakeHeartRateSensor` and no
  Robolectric. The three minutes run in milliseconds on `runTest` virtual time because the protocol
  is expressed purely in `delay`.
- `data/HealthServicesHeartRateSensor.kt` — the only file that knows about Health Services. Wraps
  `MeasureClient` + `MeasureCallback` in a `callbackFlow`. Two non-obvious details: the
  `.buffer(Channel.UNLIMITED)` is load-bearing (readings arrive in batches from a callback that
  cannot suspend, and `callbackFlow`'s default RENDEZVOUS channel would drop them), and `awaitClose`
  is not a suspend context, so unregistering uses `unregisterMeasureCallbackAsync`, not the
  suspending ktx extension.
- `presentation/` — `MainActivity` (`@AndroidEntryPoint`, ~15 lines), `RestingHrViewModel`
  (`@HiltViewModel`), `RestingHrUiState`, and `RestingHrScreen`. The screen splits into a stateful
  `RestingHrScreen` (permission launcher, lifecycle, keep-screen-on) and a stateless
  `RestingHrContent` that the `@WearPreviewDevices` previews render. `presentation/theme/Theme.kt`
  is a pass-through `MaterialTheme` wrapper, the intended place for app colors/typography.

UI is **Wear Compose Material 3** (`androidx.wear.compose.material3`, not
`androidx.compose.material3`): `AppScaffold` → `ScreenScaffold`. The spinner is the
`CircularProgressIndicator` overload *without* a `progress` parameter. Splash screen comes from
`MainActivityTheme.Starting` (`res/values/styles.xml`) via androidx core-splashscreen.

### Heart rate: two constraints that shape the code

1. **Permission is `android.permission.health.READ_HEART_RATE`.** `BODY_SENSORS` is not usable at
   targetSdk 36 (Wear OS 6), and since `minSdk = 36` the legacy `maxSdkVersion="35"` back-compat
   entry would be dead code. Requested with the standard `ActivityResultContracts.RequestPermission`
   flow; no rationale activity, no background permission. The check runs at button-press time and is
   never cached, so granting it in system settings and returning just works.
2. **MeasureClient only delivers while the app is in the foreground.** Hence
   `FLAG_KEEP_SCREEN_ON` for the duration of a measurement (scoped by `DisposableEffect` to the
   measuring state only), and `LifecycleEventEffect(ON_STOP)` ends the run as `INTERRUPTED` rather
   than averaging the handful of readings that arrived before the screen went dark. Screen-off
   measurement would mean `ExerciseClient` plus a foreground service — a much bigger feature.

The app is declared standalone (`com.google.android.wearable.standalone=true`) — it must work
without a paired phone.

## Build system — the parts that will bite you

`minSdk = targetSdk = 36`, `compileSdk = release(36) { minorApiLevel = 1 }`, plus
`useLibrary("wear-sdk")`. This targets Wear OS 6 only and deliberately excludes older watches; don't
lower `minSdk` to pick up a library without flagging that trade-off. Java/Kotlin target is 11.

Dependencies are declared only through the version catalog at `gradle/libs.versions.toml` — add a
library there first, then reference it as `libs.x.y` in `app/build.gradle.kts`.

- **Never add the `org.jetbrains.kotlin.android` plugin.** AGP 9 has built-in Kotlin support and
  hard-errors if that plugin is present. AGP 9.2.1 embeds KGP 2.2.10, which is why `kotlin` in the
  catalog is 2.2.10 and `ksp` is `2.2.10-2.0.2` — the KSP version must track the Kotlin version AGP
  embeds, and that is the only KSP built for 2.2.10.
- **`android.disallowKotlinSourceSets=false` in `gradle.properties` is required, not cosmetic.**
  The KSP release this project must use registers its generated source directories through the `kotlin.sourceSets` DSL, which
  AGP 9's built-in Kotlin rejects by default. Remove the flag only once a KSP release for this
  Kotlin version uses `android.sourceSets`.
- **Hilt must stay ≥ 2.60.** Earlier versions' Gradle plugin reaches for AGP's legacy variant API
  and fails at configuration time under AGP 9.
- **`compileSdk 36` caps some AndroidX versions.** `androidx.hilt` 1.4.0 and `androidx.lifecycle`
  2.11.0 both require compiling against API 37, so this project pins 1.3.0 and 2.10.0. Bumping them
  means bumping `compileSdk`, which is a deliberate decision here, not a routine upgrade.
