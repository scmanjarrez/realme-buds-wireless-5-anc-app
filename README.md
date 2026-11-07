# RealmeBuds

An open-source Android app to control the **realme Buds Wireless 5 ANC** without the official
realme Link app and without any account.

## Why this exists

realme Link needs you to sign in with an account before you can use the earbuds' settings
(noise control, EQ, button gestures...). In the author's case it even asked for a phone number
just to pair and control a pair of headphones, after an update that could not be rolled back.
Nothing about adjusting your own earbuds requires a cloud account, so this project talks to them
directly over Bluetooth instead, so that you do not depend on the vendor app.

The app declares no `INTERNET` permission: it cannot reach any server.

This is an independent, unofficial project. It is not affiliated with or endorsed by realme.
Use it at your own risk. It only writes ordinary settings and never touches firmware.

## What it does

- Reads and shows the current state: battery, firmware version, noise mode, EQ, toggles, buttons.
- Noise control: Off, Transparency, and the four noise-cancellation levels (Smart, Max, Moderate, Mild).
- Enhance voices, Smart de-wind, Spatial audio, Dynamic audio (with its three frequency sliders),
  Volume enhancer, Game mode, Auto answer, Find my phone.
- EQ presets (Clear Vocals, Nature Balance, Clear Bass, Bass Boost) and a six-band custom
  equalizer. Moving a slider switches the earbuds to the custom EQ; one button resets it.
- Button gestures: single, double, triple tap and touch-and-hold, plus which modes the
  noise-control button cycles through.
- Dual-device connection: list of known devices, with connect, disconnect and remove.
- Earbud fit test (it starts the test; showing the result is not implemented yet).

Tested with a realme Buds Wireless 5 ANC (it reports version text `1,2,1.1.0.56`) on Android 11.
Other realme earbuds may share the protocol, but nothing else has been tested.

## How it works

The earbuds expose a simple request/response protocol over classic Bluetooth (RFCOMM). The
protocol was worked out by capturing the vendor app's traffic and reading its code; the full
description is in [docs/PROTOCOL.md](docs/PROTOCOL.md).

Every message looks like this:

```
AA | len | 00 00 | cmd (u16 LE) | seq | dlen (u16 LE) | data
```

A response repeats the command with bit 15 set and the same `seq`; its first data byte is the status.

The code lives in `app/src/main/java/com/example/realmebuds/`:

| file | role |
|------|------|
| `Protocol.kt` | command ids, setting values, and the frame builder / parser |
| `BudsConnection.kt` | RFCOMM socket, a reader thread, and request/response matching by `seq` |
| `BudsApi.kt` | reads the whole device state with the query commands |
| `MainActivity.kt` | the UI (built in code, no XML layouts), debounced writes, state display |

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

Captures and bug reports contain personal data: phone numbers from the hands-free traffic, and the
names and Bluetooth addresses of every device your earbuds know. The `.gitignore` excludes the
usual file names, but check before you publish anything.

## Repository layout

```
app/        the Android app
docs/       PROTOCOL.md, the protocol description
debugging/  buds_client.py (Python test client) and hook_packets.js (Frida script)
```

This repository contains no code or assets from the vendor app; everything here was written from
observing how the earbuds behave.

## Known limitations

- Pressing the earbuds' buttons, or other changes made on the earbuds themselves, do not refresh
  the screen: the app does not register for the earbuds' notifications yet (press "Update state").
- Find my phone can be switched on, but the app does not ring the phone when you press the button.
- The earbud fit test result is not shown.
- Parts of the vendor app that were not explored (its overflow menu, the sub-screens of Spatial
  audio) may use commands that are not documented.
- Some UI text and code comments are still in Spanish.
