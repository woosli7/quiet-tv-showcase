# Building and setup

This source snapshot uses native Java and the Android SDK command-line tools. There is no Gradle project or wrapper. The supplied build script runs on Windows PowerShell and downloads nothing.

## Requirements

- A JDK with `java`, `javac`, and `keytool`. The snapshot build was checked using JDK 21 with Java 8 source/target compatibility.
- An Android SDK platform containing `android.jar`. Development used API 35 for compilation, with minimum API 23 and target API 28 in the manifest.
- Android SDK build tools containing `aapt2`, `zipalign`, `lib/d8.jar`, and `lib/apksigner.jar`.
- Android platform tools (`adb`) for optional device installation.

Obtain the SDK and platform tools from the [Android developer tools](https://developer.android.com/tools) or an existing Android Studio SDK. The script expects installed local paths; tool binaries are not bundled.

## Build

From the repository folder, replace these example paths with your own:

```powershell
./scripts/build.ps1 `
    -JavaHome 'C:\path\to\jdk' `
    -AndroidJar 'C:\path\to\Android\Sdk\platforms\android-35\android.jar' `
    -BuildTools 'C:\path\to\Android\Sdk\build-tools\YOUR_VERSION'
```

Alternatively set `JAVA_HOME`, `QUIETTV_ANDROID_JAR`, and `QUIETTV_BUILD_TOOLS` before running the script.

Output: `build/QuietTV-debug.apk`. The script generates a development signing key in the ignored `build/` folder. Keep that key locally if you want later builds to update the same installation. Build output and all keystores are excluded from version control.

This key is distinct from the original personal installation. A separately signed APK cannot update an existing Quiet TV installation signed with another key. Do not uninstall a personal installation just to test this snapshot unless you intend to lose its saved app data.

## Install on a test TV

Enable ADB debugging on the TV, connect to your own TV address, and approve the computer on the TV. Do not include private addresses or authorization files in commits.

```powershell
adb connect YOUR_TV_IP:5555
adb -s YOUR_TV_IP:5555 install -r build/QuietTV-debug.apk
adb -s YOUR_TV_IP:5555 shell am start -n com.quiettv.home/.MainActivity
```

The script does not install anything or modify TV settings automatically. The launcher can be opened like a normal app. First launch offers greeting, optional city, installed apps, and greeting/clock/weather widget choices. Counter-Strike is absent from setup and off by default. See [shared-version setup](PUBLIC-SETUP.md) for widget behavior and the separate -Preview build.

## Optional Home/app-return helper

Review [permissions and privacy](PRIVACY.md) first. On the tested Fire OS build, enabling the helper also requires granting the log permission through ADB:

```powershell
adb -s YOUR_TV_IP:5555 shell pm grant com.quiettv.home android.permission.READ_LOGS
```

Open Quiet TV and use its Home/app-return setting. This relies on system log events and background-service behavior specific to Fire OS; other firmware may not allow it. No command here disables the Amazon launcher or removes system packages.

To revoke the optional grant:

```powershell
adb -s YOUR_TV_IP:5555 shell pm revoke com.quiettv.home android.permission.READ_LOGS
```

## Personalization and integrations

- **Apps:** launch installed packages through their normal launch intents. The Chrome-themed shortcut targets Silk. Twitch is installed separately for native CS playback.
- **Location:** first-time setup and Settings → City and time provide optional city search and city/TV timezone selection. Weather settings offer Celsius/Fahrenheit. Profile changes invalidate incompatible caches and obsolete in-flight responses; no source edits or location permission are needed.
- **CS filtering:** edit `app/res/raw/cs_policy.json`. Verified organizer identities and references live separately in `cs_broadcasters.json`.
- **Lights:** optional H6097 support is included, but the personal provisioning tool/payload is not. `GoveeLights.loadConfig()` defines the encrypted import format. General setup is unfinished; without configuration the app simply shows that lights need setup. Never place a real API key or device ID in source control.
- **Night screen:** enable the overlay permission through Settings if desired. The optional device-specific DIRECTV companion used in the personal build is not distributed here; use Settings to toggle the filter.

Before posting logs or screenshots to an issue, remove account details, keys, network settings, and device identifiers.
