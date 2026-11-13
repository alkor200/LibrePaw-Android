# LibrePaw (Android)

The Android counterpart to [LibrePaw](https://github.com/alkor200/LibrePaw), an
open source pet GPS tracker client for self-hosted
[Traccar](https://www.traccar.org/) servers.

A feature-complete port of the iOS app: Authentication, Map, Pets, Pet
Detail (Edit/Share), Homes, Excursions, Trail, Settings, a Find Pet
compass screen, Navigate/View-on-Map, and a persistent floating bottom
bar. Developed independently from the iOS app going forward rather than
as a 1:1 mirror, the two are expected to drift apart over time.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Navigation 3 (`androidx.navigation3`)
- MVVM: `ViewModel` + `StateFlow`, matching the iOS app's own MVVM approach
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)

## Requirements

The app runs on Android 7.1.1 (API 25) and newer. Android 7.0 isn't supported: it can't validate
Let's Encrypt certificates, which almost every self-hosted Traccar server uses.

To build it:

- **JDK 17, specifically** (not whatever is newest/default on the machine):
  confirmed live, building under JDK 27 (the Homebrew default `openjdk`
  formula pulled in as a `gradle` dependency) fails outright with
  `Unsupported class file major version 71`, the `android` CLI's own
  bundled Gradle distribution doesn't support anything newer than 17 yet.
  `brew install openjdk@17` (the keg-only formula, not the `temurin@17`
  cask, which needs `sudo` to install the pkg) gets a working one without
  admin rights. Export `JAVA_HOME` to it before building:
  ```sh
  export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
  ```
- Android SDK: command-line tools are enough to build and test from the
  CLI (installed here via `brew install --cask android-commandlinetools`,
  plus Google's own agent-oriented `android` CLI, see
  `~/.claude/skills/android-cli`). Android Studio is the easier path if
  you want the IDE/emulator UI, Compose previews, and a debugger.
  Either way, `local.properties` (not committed, same reasoning as the
  iOS app's `Local.xcconfig`) needs an `sdk.dir` pointing at wherever the
  SDK lives:
  ```
  sdk.dir=/path/to/your/sdk
  ```

## Building and testing

```sh
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.21.0.3/libexec/openjdk.jdk/Contents/Home
./gradlew assembleDebug   # build the debug APK
./gradlew test            # unit tests
```

## Running on an emulator

```sh
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"
android emulator start medium_phone   # the AVD created for this project
android run app --device=emulator-5554
android screen capture -o screenshot.png
```

## Releasing

Release builds are minified with R8. They're signed only when a keystore is configured outside
this repository, e.g. in `~/.gradle/gradle.properties` (never commit a keystore or its passwords):

```properties
librepaw.release.storeFile=/path/to/librepaw-release.jks
librepaw.release.storePassword=…
librepaw.release.keyAlias=…
librepaw.release.keyPassword=…
```

Without it, release builds come out unsigned.

- **Google Play:** `./gradlew bundleRelease` builds an App Bundle
  (`app/build/outputs/bundle/release/app-release.aab`); Play splits it per device itself.
- **Direct downloads** (e.g. GitHub releases): `./gradlew assembleRelease` builds one APK per CPU
  architecture plus a universal one in `app/build/outputs/apk/release/`. Most phones want
  `arm64-v8a` (~15 MB, against ~50 MB for the universal APK, which carries MapLibre's native
  libraries for every architecture).

Build the two in separate Gradle invocations: AGP refuses per-architecture APKs and a bundle in the
same one.

## License

LibrePaw is available under the [GNU General Public License v3.0](LICENSE), the same as the iOS app: you're free to use, modify, and distribute it, including forks, but any distributed derivative work must stay open source under the same license.
