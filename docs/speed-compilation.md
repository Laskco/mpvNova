# Compile For Speed After Sideloading

Android still optimizes sideloaded apps through its runtime and background compilation. However, installing an APK from GitHub does not get Google Play's cloud-profile delivery. You can ask Android to compile mpvNova's Java/Kotlin code ahead of time using the `speed` mode, without waiting for usage profiles or background optimization.

This may help startup and menu responsiveness, but improvements vary by device and are not guaranteed. It takes some processing time and extra storage. It does **not** rebuild mpv/FFmpeg, improve network speeds, or fix decoding, HDR, or Dolby Vision compatibility.

**Connect using ADB**

1. Install mpvNova, then download Google's [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) on your computer. Open a terminal in the extracted folder. In Windows PowerShell, use `./adb.exe` instead of `adb` in the commands below.
2. Enable Developer options and ADB/USB debugging or Network debugging on your TV. The names vary by device; on Fire OS, look for **ADB Debugging**. For a network connection, keep the computer and TV on the same trusted local network.
3. Connect using the TV's address and debugging port, replacing `TV_IP:PORT` below. Shield and many older TV devices use port `5555` when network debugging is enabled. Accept the authorization prompt on the TV.

```sh
adb connect TV_IP:PORT
adb devices
```

If the TV offers **Wireless debugging > Pair device with pairing code**, first run `adb pair TV_IP:PAIRING_PORT` and enter its code. Then use `adb connect TV_IP:CONNECTION_PORT` with the address on the main Wireless debugging screen; the connection and pairing ports can differ. A supported USB ADB connection works too. See the [ADB connection guide](https://developer.android.com/tools/adb).

**Run the speed compiler**

Stop playback and keep the TV powered on. With the device listed as `device` by `adb devices`, run:

```sh
adb shell pm compile -m speed -f app.mpvnova.player
```

Wait for `Success`, then open mpvNova normally. `-m speed` selects full ahead-of-time compilation; `-f` forces the compilation pass. Root is not required on devices that permit this ADB command, and it does not clear your settings or media.

- Run it after installing mpvNova, and rerun it after an app update if you want to apply the same optimization to the new APK. There is no need to run it before every playback session or reboot. Android may later manage the compiled code itself.
- If more than one device is connected, target the TV explicitly: `adb -s TV_IP:PORT shell pm compile -m speed -f app.mpvnova.player`. Use its serial from `adb devices` for USB connections.
- If `pm compile` reports an unknown command, try `adb shell cmd package compile -m speed -f app.mpvnova.player`. Availability varies by Android/Fire OS version and firmware; if both forms are unsupported or denied, skip this optional step.
- If an ADB app has already opened a shell on the TV, enter only `pm compile -m speed -f app.mpvnova.player`, without the `adb shell` prefix. An ordinary terminal app without an ADB shell is not equivalent.
- `unauthorized` means the TV still needs to approve the connection. `Unknown package` means mpvNova is not installed under the official package name shown above.
- When finished, disconnect ADB and turn off debugging if you do not need it. Never expose the debugging port to the internet.

Background: [Android runtime compilation](https://source.android.com/docs/core/runtime/jit-compiler#force-compilation) and [Baseline and Cloud Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview).
