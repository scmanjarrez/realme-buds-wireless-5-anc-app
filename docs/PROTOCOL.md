# realme Buds Wireless 5 ANC: control protocol ("tl" channel)

Sources: HCI capture (btsnoop), Frida hooks on `com.realme.link:link.monitor`, the realme Link APK
5.8.811.2814 (`com.realme.iot.headset.tl.protocol.*`) and a recorded sequence of UI actions.
None of the commands below is encrypted. Verified: the 0x0105 / 0x0106 queries from a standalone
client over RFCOMM channel 1 (battery 90 % confirmed against `0x5a`), and most of the settings
listed below from the Android app in this repository.

## Transport
- Classic Bluetooth, RFCOMM. Channel 1 worked from the standalone client (in the phone capture the
  DLCI was 2, which corresponds to channel 1). Resolving it through SDP is safer than hardcoding it.
- There is a second channel (DLCI 10, `a5 a5` frames) that exchanges 16-byte blocks. None of the
  commands below need it.

## Frame
```
AA | len | 00 00 | cmd (u16 LE) | seq | dlen (u16 LE) | data
```
- `len` = total frame size - 2.
- Response: same `cmd` with bit 15 set (0x8xxx) and the same `seq`; the first data byte is the
  status (00 = OK).
- Unsolicited notifications from the earbuds: `cmd` 0x0204, header `08 00`, their own `seq`.

## Settings
Confidence is marked per entry. "high" means the value matched the captured traffic and its
effect was checked on a real device.

### 0x0403 setSwitchFeature `[id, 0/1]`
| id   | function                  | confidence |
|------|---------------------------|------------|
| 0x1a | Smart de-wind technology  | high |
| 0x09 | Enhance voices            | high |
| 0x1b | Spatial audio             | high |
| 0x1d | Dynamic audio             | high |
| 0x06 | Game mode                 | high |
| 0x11 | Dual-device connection    | high |
| 0x26 | Find my phone             | high |
| 0x08 | Auto answer               | high |

Find my phone (0x26): while enabled, pressing the center button on the earbuds makes the earbuds
send an unsolicited `cmd 0x0502` with data[0] = 5, and the app rings the phone. See the 0x0502
entry under Notifications.

### 0x0404 noise control `[action, type, value]`
- `01 01 20` Smart, `01 01 08` Max, `01 01 10` Moderate, `01 01 04` Mild. The order in the capture
  (20, 08, 10, 04, 08) matches the order the levels were selected in the vendor app.
- `01 01 01` Off (normal), `01 01 02` Transparency.
- `02 01 xx`: bit mask of the modes in the noise-control button cycle:
  Off = 0x01, Transparency = 0x02, Noise cancellation = 0x08.

### Other commands
| cmd    | function | data | confidence |
|--------|----------|------|------------|
| 0x0405 | Earbud fit test | `[1]` | high |
| 0x0406 | EQ mode | `00` Nature balance, `01` Bass boost, `02` Clear vocals, `03` Clear bass | high |
| 0x0407 | Volume enhancer | `[0/1]` | high |
| 0x040b | Dual-device: action on a listed device | `[1, MAC(6), action]`: 0 = disconnect, 1 = connect, 2 = remove | high |
| 0x0418 | Custom equalizer | see below | high |
| 0x041b | Dynamic audio frequency sliders (`setBassEngineValue` in the APK) | `min(i8) max(i8) value(i8) id`: always `fb 05` (-5..+5); id 1 = low, 2 = mid, 3 = high | high |
| 0x0401 | Button functions | see below | high |

### 0x0418 Custom equalizer
```
op | min(i8) | max(i8) | eq_id | name_len | "Custom1" | band_count | (freq u16 LE, gain i8) * 6
```
- `min` / `max` = `fa 06`, i.e. -6..+6 dB. `eq_id` is 4 for the custom equalizer and 0 when creating it.
- Bands: 62 Hz (the earbuds report it as 50 Hz), 250, 1000, 4000, 8000 and 16000 Hz. Gains are
  signed dB values.
- `op`: 1 = create, 2 = update, 3 = confirm. The APK defines actions 0..5; action 5 takes a
  different code path and may be "delete".
- Behavior seen in practice: updating with `op` 2 switches the earbuds to the custom EQ on its
  own. If no custom EQ exists yet, create it first with `op` 1 and all gains at 0, then update it.
  Setting a preset with 0x0406 switches back to a preset without erasing the custom values.

### 0x0401 Button functions
```
n | [device_type, button, action, function] * n        (KeyFunctionInfo in the APK)
```
- Button 01 = multifunction button (the tap gestures): seen as `01 01 action function`.
- Button 04 = noise-control button: `01 04 01 08` (single tap = function 0x08, noise control).
- Action: 01 single tap, 02 double tap, 03 triple tap, 04 touch and hold.
- Function: 00 none, 01 play/pause, 04 voice assistant, 05 previous track, 06 next track,
  0x11 game mode, 0x08 noise control.

## State queries
Decoded from the APK and checked against captures. The response payloads below start after the
status byte (00 = OK).

| cmd | request | response |
|-----|---------|----------|
| 0x0106 | none | `01 01 nn`: the last byte is the battery level in % |
| 0x0105 | none | ASCII version text (`1,2,1.1.0.56`) |
| 0x010c | `01 01` | `01 01 value`: current mode (01 off, 02 transparency, 04/08/10/20 ANC levels) |
| 0x010c | `02 01` | `02 01 mask`: noise-control button cycle (01 Off, 02 Transparency, 08 Noise cancellation) |
| 0x0112 | none | `n` + n * [MAC(6, reversed byte order), len, state (0 disconnected, 1 connecting, 2 connected), flag (1 = this device), name_len, name]; `len` = 3 + name_len |
| 0x010f | none | active equalizer `id` (00..03 presets, 04 custom) |
| 0x0110 | none | `0/1`: Volume enhancer |
| 0x010d | `n, id1..idn` | `n, (id, value) * n`: state of the 0x0403 features |
| 0x0108 | `0e 01 03 04 05 06 0b 0c 08 0d 11 1f 1a 1b 00` | `n, [device_type, button, action, function] * n` |
| 0x0122 | none | `n` + n * [selected, min, max, id, len, name, band_count, (freq u16, gain i8) * band_count]; the custom EQ has id 4 |
| 0x012c | none | `n` + n * [min, max, current(i8), type]: the three Dynamic audio sliders (type 1 low, 2 mid, 3 high) |

## Other queries
Read from the APK's response handlers; not used by the Android app. None takes request data.

| cmd | meaning (from the APK) |
|-----|------------------------|
| 0x010b | earbud color id (1 byte) |
| 0x0107 | upgrade capability: count + 2-byte entries |
| 0x0120 | language setting (1 byte) |

Seen in traffic but not decoded: 0x0100, 0x0109, 0x0115. 0x0124 (single bass-engine value) gets
no response on this model; use 0x012c instead.

## Notifications (partially decoded)
The earbuds only send unsolicited notifications for the events the host has registered.
- 0x0200 query: response `n` + supported event ids (this model: 01 02 03 04 05 06 0d f1 f4).
- 0x0205 register: request `n, id1..idn`, response `n, (id, status) * n` (status 00 = OK). Registration
  works from this app: query 0x0200, register the known ids with 0x0205, and the earbuds ack OK. The
  vendor app registers `01 02 03 04 05 06 f4 0d f1`.
- A notification is a frame with `cmd` 0x0204 and header `08 00`; the data is the event id followed by the payload.

Event ids. "confirmed" means seen in a real device capture; everything else comes from the realme Link APK and is unconfirmed.

| event | meaning |
|-------|---------|
| 0x01 | battery (confirmed): last payload byte is the level in %, seen 70 |
| 0x02 | earbud status change (in / out). Registered, never received in captures; APK layout `n, (type, value) * n` |
| 0x03 | noise-control mode (confirmed): payload `type value`, where value is the mode (seen 0x20 smart ANC, 0x01 off), sent on every button press. Seen 2 s after smart ANC engages: payload `04 01 10`, probably the ANC level applied by smart mode (0x10 = moderate; unconfirmed) |
| 0x04 | fit test (confirmed): payload `(deviceType, status) * 2`; deviceType 1 = left, 2 = right. Status (from the APK dialog): 0 not ideal, 1 good, 2 and 3 failed / try again, 4 earbuds not inserted, 5 interrupted by the app (ignore). Both 1 = perfect fit. Seen `{1=0,2=0}` and `{1=1,2=0}` |
| 0x06 | dual-device connection change. Registered, never received in captures; layout like the 0x0112 response. Follows a 0x040b command about 2 s later |
| 0x0d | registered, never received in captures; layout unknown |
| 0x0e | ear scan result (APK only, not registered; layout undecoded) |
| 0xf1 | user interaction (ANC button, multifunction taps / hold). Registered, never received in captures; APK layout `deviceType, button, action, function, scene, [u16 options]` |
| 0xf4 | registered, never received in captures; layout unknown |

Fit test (0x04) notes: the earbuds (neckband model) report no wear status, so "earbud not in the ear"
and "bad seal" both come as status 0. Status 4 was never seen on the device. When the vendor app's
test dialog is dismissed while a test is running, it sends `0x0405 00`; this app does the same.

Other unsolicited frames (not 0x0204):
- 0x0502 FIND PHONE request from the earbuds: data[0] = 5 starts ringing the phone. Sent when Find my
  phone (0x0403 id 0x26) is on and the button is pressed. No event registration is needed and the
  vendor app sends no response. Observed: two consecutive presses both sent `05`, never `06`, so 05
  is treated as a toggle (the second press stops the ringing). `06` = stop appears only in the vendor
  app code.

Dual-device timing:
- 0x040b (connect 1, disconnect 0, remove 2) can take more than 2 s to answer: the 0x840b response
  with data 00 arrived late. Use a longer timeout; this app uses 10 s.

## Open items
- Meaning of the `04 01 10` payload in the 0x03 event (ANC level applied by smart mode?). Unconfirmed.
- 0x02, 0x06, 0x0d, 0xf1 and 0xf4 were never received in captures, so their layouts are unverified
  (0x0d and 0xf4 layouts are unknown).
- What ends the find-phone ringing besides 0x0502 data 6, and whether the earbuds time it out.
- The undecoded queries above.
- The custom equalizer needs no delete command in practice: the vendor app appears to handle
  removal locally.
- Screens of the vendor app that were not walked through (the overflow menu, sub-screens such as
  Spatial audio) may use commands not listed here.
