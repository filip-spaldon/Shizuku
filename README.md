<div align="center">

# Shizuku (filip-spaldon fork)

An Android app that allows other apps to use system-level APIs that require ADB/root privileges.

**Personal fork of [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) with Android 17 fixes and app-triggered start/stop.**

</div>

## 🍴 About This Fork

This fork is maintained by [filip-spaldon](https://github.com/filip-spaldon). It is based on [thedjchi/Shizuku](https://github.com/thedjchi/Shizuku) 13.7.0, which itself is a fork of [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku). Both upstream projects are currently not maintained.

### Changes compared to thedjchi/Shizuku 13.7.0

* **Android 17 fix: false "USB debugging is not enabled"**: Android 17 always reports `Settings.Global.ADB_ENABLED` as `0` to third-party apps, so the start button refused to start even with USB debugging on. On SDK 37+ a `0` is now treated as unknown. Fix by [RazGame](https://github.com/RazGame/Shizuku) (thedjchi/Shizuku#301).
* **Android 17 QPR3 Beta 3 fix: crash on start**: the system now requires `MANAGE_DEBUGGING` to change adb related settings, so `WRITE_SECURE_SETTINGS` is no longer enough and the start button crashed the app. These writes are now allowed to fail. Shizuku can't turn wireless debugging on by itself anymore: a background start (on boot or for selected apps) shows a notification asking you to turn it on and continues once it is on.
* **Built against the Android 17 SDK** (`targetSdk 37`), also from [RazGame](https://github.com/RazGame/Shizuku).
* **Start with selected apps** (Settings → Behavior): pick apps from a list, and Shizuku starts in the background whenever one of them is opened. Uses a small accessibility service that only receives window changes of the selected apps and never reads window content. If Shizuku has `WRITE_SECURE_SETTINGS`, the service is enabled automatically.
* **Stop when the apps are closed**: if Shizuku was started by one of the selected apps, it is stopped again about a minute after none of them is in the foreground or running a foreground service (e.g. music playback). A Shizuku started manually is never stopped this way.
* **Status notification** (Settings → Behavior, on by default): a silent notification while Shizuku is starting, running or stopping, shown as a Live Update chip on Android 16+, with a Stop action. It disappears once Shizuku is stopped, and turns into a dismissible "stopped unexpectedly" notification if Shizuku crashes or is killed.
* **App trigger self-check**: the system can turn the "Start with selected apps" accessibility service off, for example after an OS update. Shizuku now turns it back on after boot, after its own update and whenever the app starts, and shows a notification when it can't.
* **Toggle USB debugging with Shizuku** (Settings → Behavior, replaces "Auto-disable USB debugging"): turns USB debugging on when Shizuku starts and off when it stops. On Android 17 QPR3 apps can't change it anymore, so Shizuku does it through its own shell user while it is running. Wireless debugging must be on to start Shizuku.
* In-app updates, source code and bug report links point to this fork. While Shizuku is running, updates are installed through Shizuku's shell user (`pm install`), so they work even where installing from apps is blocked, e.g. by a work profile policy.

Tested on a Pixel 9 Pro running Android 17 QPR3 beta.

> [!IMPORTANT]
> Releases of this fork are signed with a different key than the official, thedjchi or RazGame builds. To switch, uninstall the existing Shizuku first. Wireless debugging pairing and app authorizations have to be set up again.

### Background start requirements

Starting Shizuku automatically (on boot or for selected apps) uses wireless debugging and needs `WRITE_SECURE_SETTINGS`, granted once over adb:

```
adb shell pm grant moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS
```

## ⚠️ Disclaimer

This is a **FORK** of Shizuku. If you are looking for the original version, please visit the [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) repository.

## ⬇️ Download

Get the latest release of this fork from [GitHub Releases](https://github.com/filip-spaldon/Shizuku/releases/latest).

## ✨ Added Features

This version of Shizuku includes some extra features over the original version, such as:
* **More robust "start on boot":** waits for a Wi-Fi connection before starting the Shizuku service
* **TCP mode:** (i.e., the `adb tcpip` command) once Shizuku successfully starts with Wi-Fi after a reboot, you can stop/restart Shizuku without a Wi-Fi connection!
* **Watchdog service:** automatically restarts Shizuku if it stops unexpectedly, and can alert you of crashes/potential fixes
* **Start/stop intents:** toggle Shizuku on-demand using automation apps (e.g., Tasker, MacroDroid, Automate)
* **[BETA] Stealth mode:** hide Shizuku from other apps that don't work when Shizuku is installed
* **[BETA] In-app updates:** option to automatically check for new updates, and can automatically download/install the latest version from GitHub
* **Android/Google TV and VR headset support:** UI is now compatible with D-Pad remotes, all TVs are supported (including Android 14+ TVs that require pairing), and the multi-window pairing dialog is toggleable in settings for VR headsets
* **MediaTek support:** fixes a critical bug in the original v13.6.0 which prevented Shizuku from working on MediaTek devices
* And more!

## 📝 User Guide

Please read the [wiki](https://github.com/thedjchi/Shizuku/wiki) for setup, info, and troubleshooting steps.

## ☑️ Requirements

**Minimum Version: Android 7+**
- **Root mode:** Requires a rooted device
- **Wireless Debugging mode:** Works on Android 11+ and all Android TVs
- **PC mode:** Works on all devices
- **Start on boot:** Available only when using Wireless Debugging or Root mode

## 🔒 Privacy

Shizuku takes user privacy very seriously.

* No tracking or analytics
* No telemetry
* No proprietary libraries
* No Google Play Services
* Open-source codebase
* Reproducible builds
* Internet access is only used for wireless debugging connections and to fetch updates from GitHub
* Only required permissions are declared

### Permissions

* **INTERNET:** required for the wireless debugging start mode to work. Also used to fetch updates from GitHub
* **ACCESS_NETWORK_STATE:** used to determine when Wi-Fi is available for background start via wireless debugging
* **POST_NOTIFICATIONS:** required for pairing notification and other alerts
* **RECEIVE_BOOT_COMPLETED:** required for start on boot
* **FOREGROUND_SERVICE:** prevents watchdog from being killed
* **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS:** prevents start on boot and watchdog services from being killed
* **WRITE_SECURE_SETTINGS:** used to toggle USB and wireless debugging in the background when starting/stopping Shizuku
* **REQUEST_DELETE_PACKAGES:** used to request uninstall for Shizuku/stub when using stealth mode
* **REQUEST_INSTALL_PACKAGES:** used to request install for app updates, as well as Shizuku stub when using stealth mode
* **Accessibility service (optional):** used only by "Start with selected apps" to notice when a selected app is opened
* **Package visibility of launcher apps:** used to list apps in the "Start with selected apps" picker

## 🌎 Translations

Contribute translations through the [Crowdin project](https://crowdin.com/project/shizuku).

## 🎁 Donations

This Shizuku fork and all of its features will always be free, and there will never be ads. If you've found any of the added features to be useful, consider [donating](https://www.buymeacoffee.com/thedjchi) to help me maintain the project!

## 📱 Developer Guide

### API & Demo Project
The API guide and a demo project are available in the [Shizuku-API](https://github.com/thedjchi/Shizuku-API) repository

### Notes

1. Shizuku has different permissions in root and ADB mode. You can see permissions granted to ADB [here](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/packages/Shell/AndroidManifest.xml).
   If your app requires root permission, use `ShizukuService#getUid` to check if Shizuku is running as root or ADB, or use `ShizukuService#checkPermission` to check if the server has sufficient permissions.
2. On devices running Android 8 or lower, if you need to use Shizuku in a Service or Broadcast Receiver that might not be started by an Activity, please trigger the send binder by starting a transparent activity.
3. Please prefer using `ShizukuBinderWrapper` instead of directly using `transactRemote` when possible, as API calls can change across Android versions.

## 🤝 Contritbuting

### Building the App

- Clone with `git clone --recurse-submodules`
- Run gradle task `:manager:assembleDebug` or `:manager:assembleRelease`

The `:manager:assembleDebug` task generates a debuggable server. You can attach a debugger to `shizuku_server` to debug the server. In Android Studio, ensure `Run/Debug configurations > Always install with package manager` is checked, so that the server will use the latest code.

### Submitting Changes

1. Fork the repository
2. Create a feature branch (`git checkout -b branch-name`)
3. Make your changes
4. Commit your changes (`git commit -m 'Commit message'`)
5. Push to the branch (`git push origin branch-name`)
6. Open a Pull Request

## 📃 License

All code files in this project are licensed under [Apache 2.0](LICENSE)
