# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Wear OS app ("Resting HR", `com.simonludwig.restinghr`), single Gradle module `:app`. The code is
currently the unmodified Android Studio Wear OS template: the UI shows a greeting and three no-op
buttons, and the complication reports the day of the week. **No resting-heart-rate logic exists yet**
— there is no Health Services / body-sensors dependency or permission, so adding heart-rate reading
means adding `androidx.health:health-services-client` (or Health Connect), the
`android.permission.BODY_SENSORS` permission, and a runtime permission flow.

Not a git repository.

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install on connected watch/emulator (adb is on PATH)
./gradlew lint                   # Android Lint; report at app/build/reports/lint-results-debug.html
./gradlew build                  # assemble + lint + tests
```

There are no test sources yet (`app/src/test` and `app/src/androidTest` do not exist), though
`androidTestImplementation` deps for Compose UI tests are already declared. Once tests are added:

```bash
./gradlew testDebugUnitTest --tests 'com.simonludwig.restinghr.SomeTest'          # single unit test
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.simonludwig.restinghr.SomeTest#someCase
```

Gradle 9.4.1 via wrapper, daemon JVM toolchain 21 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned
through the foojay resolver). Dependencies are declared only through the version catalog at
`gradle/libs.versions.toml` — add a library there first, then reference it as `libs.x.y` in
`app/build.gradle.kts`.

## Architecture

The app is three **independent Wear surfaces**, each registered separately in `AndroidManifest.xml`
and each rendered with a different toolkit. A change to what the app displays usually has to be made
in all three:

- `presentation/MainActivity.kt` — the launcher activity, **Wear Compose Material 3**
  (`androidx.wear.compose.material3`, not `androidx.compose.material3`). Layout uses the current Wear
  idiom: `AppScaffold` → `ScreenScaffold(scrollState, edgeButton)` → `TransformingLazyColumn`, where
  each item gets `transformedHeight(this, transformationSpec)` + `SurfaceTransformation(...)` from a
  shared `rememberTransformationSpec()` so items scale at the screen edge. `presentation/theme/Theme.kt`
  is a pass-through `MaterialTheme` wrapper, the intended place for app colors/typography.
  Splash screen comes from `MainActivityTheme.Starting` (`res/values/styles.xml`) via
  androidx core-splashscreen.
- `tile/MainTileService.kt` — the tile, built with **ProtoLayout**
  (`materialScope(context, requestParams.deviceConfiguration) { primaryLayout { ... } }`), not Compose.
  Tile services return `ListenableFuture` (Guava), so async work goes through Futures, not coroutines.
  Bump `RESOURCES_VERSION` whenever tile image/resource content changes, otherwise the system serves
  stale cached resources.
- `complication/MainComplicationService.kt` — a `SuspendingComplicationDataSourceService`, so this one
  is coroutine-based. The manifest constrains it: `SUPPORTED_TYPES=SHORT_TEXT` and
  `UPDATE_PERIOD_SECONDS=3600`. Supporting more complication types means editing both
  `getPreviewData`/`onComplicationRequest` and the manifest meta-data. `getPreviewData` must return
  data for the complication picker without touching sensors.

The app is declared standalone (`com.google.android.wearable.standalone=true`) — it must work without
a paired phone. `play-services-wearable` is on the classpath but unused.

## SDK constraints

`minSdk = targetSdk = 36`, `compileSdk = release(36) { minorApiLevel = 1 }`, plus
`useLibrary("wear-sdk")`. This targets Wear OS 6 only and deliberately excludes older watches; don't
lower `minSdk` to pick up a library without flagging that trade-off. Java/Kotlin target is 11.

`app/lint.xml` suppresses `IconLocation` for `res/drawable*/tile_preview.png` — keep tile previews at
those paths.
