# OpenBuds

An open-source Android app (package `dev.openbuds.app`) to control the **realme Buds Wireless 5 ANC**
without the official realme Link app and without any account.

License: [MIT](LICENSE.md)

## Why this exists

realme Link needs you to sign in with an account before you can use the earbuds' settings
(noise control, EQ, button gestures...). Nothing about adjusting your own earbuds requires 
a cloud account, so this project talks to them directly over Bluetooth instead, so that you 
do not depend on the vendor app.

The app declares no `INTERNET` permission: it cannot reach any server.

This is an independent, unofficial project. It is not affiliated with or endorsed by realme.
Use it at your own risk. It only writes ordinary settings and never touches firmware.

## What it does

- Reads and shows the current state: battery, firmware version, noise mode, EQ, toggles, buttons.
- The screen updates by itself when the earbuds change something (ANC button, battery, dual-device).
  Pull down on any screen to refresh.
- Noise control: Off, Transparency, and the four noise-cancellation levels (Smart, Max, Moderate, Mild).
- Enhance voices, Smart de-wind, Spatial audio, Dynamic audio (with its three frequency sliders),
  Volume enhancer, Game mode, Auto answer.
- Find my phone really works: with the setting on, pressing the earbuds' button rings the phone
  (system alarm tone, phone speaker, up to 30 s; press again or tap Stop to silence it), even if
  the app is closed. It runs as a foreground service with a persistent low-priority notification,
  and it reconnects to the earbuds with a 5 s, 15 s, 60 s backoff.
- EQ presets (Clear Vocals, Nature Balance, Clear Bass, Bass Boost) and a six-band custom
  equalizer. Moving a slider switches the earbuds to the custom EQ; one button resets it.
- Button gestures: single, double, triple tap and touch-and-hold, plus which modes the
  noise-control button cycles through.
- Dual-device connection: list of known devices, with connect, disconnect and remove.
- Earbud fit test: shows the result for each earbud, good seal or adjust fit (the same statuses
  as realme Link).

Tested with a realme Buds Wireless 5 ANC (it reports version text `1,2,1.1.0.56`) on Android 11.
Other realme earbuds may share the protocol, but nothing else has been tested.

## Permissions

- **Bluetooth connect**: "Nearby devices" on Android 12 and later, to talk to the earbuds.
- **Notifications**: Android 13 and later, asked when you switch Find my phone on.
- **Foreground service**: keeps Find my phone working while the app is closed.
- **Vibrate**.

The app does not request `INTERNET`, and `allowBackup` is off.

## How it works

The earbuds expose a simple request/response protocol over classic Bluetooth (RFCOMM). The
protocol was worked out by capturing the vendor app's traffic and reading its code; the full
description is in [docs/PROTOCOL.md](docs/PROTOCOL.md).

Every message looks like this:

```
AA | len | 00 00 | cmd (u16 LE) | seq | dlen (u16 LE) | data
```

A response repeats the command with bit 15 set and the same `seq`; its first data byte is the status.

The code lives in `app/src/main/java/dev/openbuds/app/`:

| package | role |
|---------|------|
| `protocol/` | `Protocol.kt`: frames, command ids, setting values. `Events.kt`: parsers for the earbuds' spontaneous events |
| `data/` | `BudsConnection.kt`: RFCOMM socket and coroutines. `BudsApi.kt`: queries and commands. `BudsRepository.kt`: single owner of the connection and the UI state. `BudsUiState.kt` |
| `service/` | `BudsService.kt`: foreground service. `PhoneRinger.kt`: rings the phone for Find my phone |
| `ui/` | Compose + Material 3 (no XML layouts): `MainActivity.kt`, `BudsApp.kt` navigation, `BudsViewModel.kt`, `screens/`, `components/`, `theme/` |
| `util/` | `Log.kt` (debug-only logging), `Permissions.kt` |
| (root) | `OpenBudsApp.kt`: application class |

Logs go to logcat with the tag `OpenBuds`, in debug builds only. Nothing technical is shown on screen.

Connecting: the app looks for a paired device whose name contains "buds", then tries three ways
to open the RFCOMM socket and keeps the first one that answers a status query: the standard
serial UUID, the Qualcomm serial UUID that realme Link also uses, and RFCOMM channel 1 directly.
The earbuds must already be paired in Android's Bluetooth settings.

After every change, the app re-reads the state from the earbuds, so the screen shows what they
actually report and not what the app last sent.

## Building the app

You need Android Studio (or the Android SDK plus a JDK).

1. Open the project folder in Android Studio and let Gradle sync. The project uses Gradle 9.6,
   Android Gradle Plugin 9.4.1, `minSdk` 24 and `targetSdk` 37. `gradle/gradle-daemon-jvm.properties`
   asks for a JDK 25 toolchain for the Gradle daemon; Gradle can download it for you.
2. Run the `app` configuration on a phone, or from a terminal:

   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Pair the earbuds in Android's Bluetooth settings, close realme Link if it is running, and open
   the app. On Android 12 and later it asks for the "Nearby devices" permission.

### Quality gate

```bash
./gradlew ktlintCheck testDebugUnitTest assembleDebug lint
```

This uses ktlint (`ktlint_official` style) and strict Android Lint. Warnings are errors: Kotlin
warnings and Lint findings fail the build, and Lint also checks release builds.

### Release build

```bash
./gradlew assembleRelease
```

R8 is enabled. The release APK is unsigned until you add your own signing config.

## Troubleshooting

- **Find my phone stops working with the app closed (Xiaomi/MIUI and other vendors):** some
  vendors kill background services. Disable battery optimization for OpenBuds and allow autostart.
- **The app cannot connect:** close realme Link. It competes for the Bluetooth socket.
- **The fit test says "adjust fit" for an earbud:** the earbud may be dirty, or it may have the
  wrong ear tip. Clean it, try another ear tip, and run the test again.

## Trying the protocol from a computer (Python)

`debugging/buds_client.py` is a tiny RFCOMM client. It needs Linux with BlueZ, Python 3, and the
earbuds paired with that computer (for example with `bluetoothctl`).

```bash
# Read-only: battery/status, version text and one more query. Tries RFCOMM channels 1..10.
python3 debugging/buds_client.py AA:BB:CC:DD:EE:FF

# Skip the channel scan.
python3 debugging/buds_client.py AA:BB:CC:DD:EE:FF --channel 1

# Write a setting. Only noise control (0x0404) and the EQ preset (0x0406) are allowed.
python3 debugging/buds_client.py AA:BB:CC:DD:EE:FF --send 0x0404:010102   # Transparency
python3 debugging/buds_client.py AA:BB:CC:DD:EE:FF --send 0x0406:03       # Clear Bass
```

Replace `AA:BB:CC:DD:EE:FF` with your earbuds' address. The earbuds can usually keep only a couple
of simultaneous connections, so disconnect one device first if the connection is refused.
The valid values are in [docs/PROTOCOL.md](docs/PROTOCOL.md). The script prints its messages in
Spanish for now.

## Getting the protocol out of realme Link (Frida)

To find out what the vendor app sends, hook its packet class with [Frida](https://frida.re) on a
rooted phone.

1. Run `frida-server` on the phone (matching your Frida client version), install realme Link and
   connect it to the earbuds.
2. The protocol does **not** run in the app's main process. Find the right one:

   ```bash
   frida-ps -U | grep -i realme        # look for com.realme.link:link.monitor
   ```

3. Attach with the hook script and keep the output:

   ```bash
   frida -U -n "com.realme.link:link.monitor" -l debugging/hook_packets.js | tee capture.txt
   ```

   Over the network use `-H <phone-ip>:27042` instead of `-U`.
4. Use the app. Do one thing at a time and write down the order you did things in: the capture
   has no UI context, and matching your notes against the command order is how most commands in
   [docs/PROTOCOL.md](docs/PROTOCOL.md) were identified.

Each output line is JSON: `dir` (`TX` sent, `RX` received), `t` (timestamp), `cmd`, `seq`, `data`
(hex) and, for sent packets, `stack` (the app's call stack). The hook targets the class
`com.realme.iot.headset.tl.protocol.packet.Packet`. Class and method names are obfuscated and may
change between app versions, so you may have to adjust the script.

Two things help a lot besides Frida:

- **Reading the app's code.** Open the APK in [jadx](https://github.com/skylot/jadx) and look in
  `com.realme.iot.headset.tl.protocol`. `Protocol` has the command ids, and `SetCommandManager`
  and `PollCommandManager` build and parse them. Many methods log readable names.
- **A Bluetooth HCI capture, with no root needed.** Turn on "Enable Bluetooth HCI snoop log" in
  the phone's developer options, toggle Bluetooth, use the app, then run `adb bugreport`. The
  capture is inside the zip at `FS/data/misc/bluetooth/logs/btsnoop_hci.log`; open it in Wireshark
  (display filter `btrfcomm`). If you write your own parser, note that with credit-based flow
  control the RFCOMM credit byte is **not** counted in the RFCOMM length field.

## Privacy: do not commit captures

Captures, bug reports and logcat exports contain personal data: phone numbers from the hands-free
traffic, and the names and Bluetooth addresses of every device your earbuds know. The `.gitignore`
excludes the usual file names, but check before you publish anything.

## Repository layout

```
app/        the Android app
docs/       PROTOCOL.md, the protocol description
debugging/  buds_client.py (Python test client) and hook_packets.js (Frida script)
```

This repository contains no code or assets from the vendor app; everything here was written from
observing how the earbuds behave.

## Known limitations

- Parts of the vendor app that were not explored (its overflow menu, the sub-screens of Spatial
  audio) may use commands that are not documented.
- Only tested on realme Buds Wireless 5 ANC and Android 11.
- Find my phone does not start after a phone reboot until you open the app once.
- No wear detection on this neckband model, so the fit test cannot tell "not inserted" from a bad seal.
- The Python script (`debugging/buds_client.py`) still prints its messages in Spanish.

## License

OpenBuds is released under the MIT license (see LICENSE.md); it is an independent project and contains no code or assets from realme.
