# Resting Heart Rate app
This Wear OS app uses a simple protocol to measure the resting heart rate of the user: When a 
user taps 'Start measuring', the app will wait for 2.5 minutes. After that, it will begin measuring
the users heart rate for 30 seconds. The average of these 30 seconds will then be displayed as the
resting heart rate result.

**Note: The results this app produces might be highly inaccurate and should never be used for 
any critical measurements.**

The app is built with plain Kotlin and Compose for Wear OS. It has been verified to work on a 
Google Pixel Watch 3. The app was developed as a (minor) part of my bachelor's thesis at the
[University of Applied Sciences Düsseldorf](https://www.hs-duesseldorf.de/).

---
## Getting Started

The app is built and installed using Gradle and the Android CLI. The commands below are written for 
Unix-based systems (i.e. macOS / Linux). On Windows, the steps are the same, but use `gradlew.bat`
instead of `./gradlew`, and set environment variables with `setx` (or via the system settings) 
instead of `export`.

It is assumed that your watch is set up and that the developer options are enabled on the watch. 
The app is standalone, so no companion app on the phone is required.

### Setting up the toolchain
1. Install a JDK. **The Android Gradle plugin requires JDK 17 or newer.**
```shell
   # Linux (Debian / Ubuntu)
   sudo apt install openjdk-17-jdk
   # macOS
   brew install --cask temurin@17
```
You can check the installed version with `java -version`.

2. Install the Android CLI and update it to the latest version
```shell
   # Linux
   curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
   # macOS (with Homebrew)
   brew tap android/tap
   brew install android-cli
   # Windows (with winget)
   winget install --id Google.AndroidCLI

   android update
```

3. Install the required SDK packages
```shell
   android sdk install platform-tools platforms/android-36 build-tools/36.0.0
```

You can list all available packages with `android sdk list --all`. Any further components required by the
project are downloaded automatically by Gradle during the first build.
4. Set the SDK location and add `adb` to your `PATH`
```shell
   export ANDROID_HOME="$(android info sdk)"
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
```
Gradle uses `ANDROID_HOME` to find the SDK. To make the change permanent, add the `export` lines to your
`~/.bashrc` or `~/.zshrc`.

### Building and installing the app
1. Clone the repository
```shell
   git clone https://github.com/simonldwg/resting-hr-helper
   cd resting-hr-helper
```
2. Connect to the watch via wireless debugging. Make sure the watch and your computer are on the same Wi-Fi network.
   On the watch, open *Settings → Developer options → Wireless debugging* and enable it. Then select
   *Pair new device* and run:
```shell
   adb pair <ip>:<pairing-port>
   # Enter the pairing code shown on the watch
   adb connect <ip>:<port>
```
Note: The pairing port and the connection port are different. The connection port is shown on the
*Wireless debugging* screen itself. Pairing is only necessary once, but the connection port can change after
a reboot of the watch.

Check that the watch is listed as `device`:
```shell
   adb devices
```
3. Build and install the app
```shell
   ANDROID_SERIAL=<ip>:<port> ./gradlew installDebug
```
Setting `ANDROID_SERIAL` makes sure the app is only installed on the watch. Without it, Gradle installs the app
on **all** connected devices, e.g. your phone if it is connected via USB as well.
Note: The first build can take several minutes since Gradle downloads all dependencies. If the wrapper is not
executable, run `chmod +x gradlew` first.
4. Start the app, either from the app list on the watch or via adb:
```shell
   adb -s <ip>:<port> shell monkey -p <application-id> -c android.intent.category.LAUNCHER 1
```
The application ID can be found as `applicationId` in `app/build.gradle(.kts)`.