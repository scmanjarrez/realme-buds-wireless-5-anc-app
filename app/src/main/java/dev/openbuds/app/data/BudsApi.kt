package dev.openbuds.app.data

import dev.openbuds.app.protocol.Cmd
import dev.openbuds.app.protocol.EventId
import dev.openbuds.app.protocol.Events
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.Frame
import dev.openbuds.app.protocol.Protocol
import dev.openbuds.app.util.Log

/**
 * Sends requests to the earbuds and returns their response frames.
 *
 * A response carries the same command id as the request with bit 15 set, and the same sequence
 * number. Its first data byte is the status, 00 when OK (see 'Frame anatomy' in docs/PROTOCOL.md).
 */
fun interface Requester {
    /**
     * Sends a request and waits for its response.
     *
     * @param cmd command id, see [Cmd].
     * @param data request payload, empty when the command takes no arguments.
     * @return the response frame, or null if none arrived.
     */
    suspend fun request(
        cmd: Int,
        data: ByteArray,
    ): Frame?
}

/**
 * Sends a request with an empty payload.
 *
 * @param cmd command id, see [Cmd].
 * @return the response frame, or null if none arrived.
 */
suspend fun Requester.request(cmd: Int): Frame? = request(cmd, ByteArray(0))

/**
 * A ready-to-send protocol command: command id plus payload.
 *
 * @property cmd command id, see [Cmd].
 * @property data payload bytes, empty when the command takes no arguments.
 */
class Command(
    val cmd: Int,
    val data: ByteArray,
)

/**
 * State read from the earbuds. Fields are null or empty when not readable.
 *
 * @property battery battery level in percent (last byte of the 0x0106 response), or null.
 * @property firmware version text from the 0x0105 response, or null.
 * @property noise current noise-control mode: 01 off, 02 transparency, 04/08/10/20 ANC levels.
 * @property noiseCycle mask of the noise-control button cycle: 01 off, 02 transparency, 08 ANC.
 * @property eq active equalizer id: 00..03 presets, 04 custom, or null.
 * @property volumeEnhancer true when the volume enhancer is on, or null.
 * @property features feature id to enabled state, from the 0x010d response. Empty when not read.
 * @property keys (button shl 8 or action) to function id, see [BudsApi.keyId]. Empty when not read.
 * @property devices paired devices from the Dual-device list. Empty when not read.
 * @property dynamic Dynamic audio slider type (1 low, 2 mid, 3 high) to its current signed value.
 *     Empty when not read.
 * @property customEq custom EQ gains in dB, one per band, or null when no custom EQ (id 4) was
 *     reported.
 * @property customEqFreqs band frequencies in Hz, in the same order as [customEq], or null.
 */
data class BudsState(
    val battery: Int?,
    val firmware: String?,
    val noise: Int?, // 01 normal, 02 transparency, 04/08/10/20 ANC levels
    val noiseCycle: Int?, // mask of the noise-control button cycle
    val eq: Int?, // 00..03 presets, 04 custom
    val volumeEnhancer: Boolean?,
    val features: Map<Int, Boolean>,
    val keys: Map<Int, Int>, // (button shl 8 or action) -> function
    val devices: List<PairedDevice>,
    val dynamic: Map<Int, Int>, // type (1 low, 2 med, 3 high) -> current signed value
    val customEq: List<Int>?, // custom EQ gains, if present
    val customEqFreqs: List<Int>?,
)

/**
 * Device from the Dual-device list. [mac] is in protocol byte order.
 *
 * @property mac six address bytes, in protocol byte order.
 * @property name device name, decoded as UTF-8.
 * @property state connection state: 0 disconnected, 1 connecting, 2 connected.
 * @property isThisDevice true when the earbuds flag this entry as this device.
 */
class PairedDevice(
    val mac: ByteArray,
    val name: String,
    val state: Int,
    val isThisDevice: Boolean,
) {
    /** Address in the usual format (the protocol sends it reversed). */
    val macText: String get() = mac.reversed().joinToString(":") { "%02X".format(it) }
}

/**
 * Builders and parsers for the earbuds control protocol, one function per command.
 *
 * Builders return a [Command] to pass to [send]. Queries take a [Requester] and return parsed
 * values or null. Wire details are in docs/PROTOCOL.md.
 */
object BudsApi {
    /** Feature ids known to be readable with 0x010d, requested by [readState]. */
    val FEATURE_IDS =
        intArrayOf(
            Feature.ENHANCE_VOICES,
            Feature.SMART_DEWIND,
            Feature.SPATIAL_AUDIO,
            Feature.DYNAMIC_AUDIO,
            Feature.GAME_MODE,
            Feature.AUTO_ANSWER,
            Feature.DUAL_CONNECTION,
            Feature.FIND_MY_PHONE,
        )

    /** Same list realme Link sends when asking for the button functions. */
    private val KEYS_REQUEST =
        byteArrayOf(
            0x0e,
            0x01,
            0x03,
            0x04,
            0x05,
            0x06,
            0x0b,
            0x0c,
            0x08,
            0x0d,
            0x11,
            0x1f,
            0x1a,
            0x1b,
            0x00,
        )

    /**
     * Packs a button and an action into the key used in [BudsState.keys].
     *
     * @param button button id, for example 01 multifunction or 04 noise control.
     * @param action action id: 01 single tap, 02 double tap, 03 triple tap, 04 touch and hold.
     * @return `(button shl 8) or action`.
     */
    fun keyId(
        button: Int,
        action: Int,
    ): Int = (button shl 8) or action

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun flag(on: Boolean): Int = if (on) 1 else 0

    /**
     * Returns the last byte of a response payload as an unsigned value.
     *
     * The first byte is the status, so a payload with only the status byte has no value.
     *
     * @param d response payload, or null when there is no response.
     * @return the last byte (0..255), or null if [d] is null or has fewer than 2 bytes.
     */
    private fun last(d: ByteArray?): Int? {
        if (d == null || d.size < 2) return null
        return d[d.size - 1].toInt() and 0xff
    }

    /** Logs a missing or malformed response by command id only. */
    private fun logMalformed(cmd: Int): Nothing? {
        Log.w({ "Missing or malformed response to 0x%04x".format(cmd) })
        return null
    }

    /** Returns the payload of a successful response, logging when there is none. */
    private suspend fun query(
        r: Requester,
        cmd: Int,
        data: ByteArray = ByteArray(0),
    ): ByteArray? {
        val f = r.request(cmd, data)
        if (f != null && f.isOk) return f.data
        Log.w({ "No valid response to 0x%04x".format(cmd) })
        return null
    }

    /**
     * Reads the full device state, one request per section, in fixed order.
     *
     * Requests, in order: [Cmd.QUERY_STATUS] (battery), [Cmd.QUERY_VERSION] (firmware),
     * [Cmd.QUERY_NOISE] twice (mode, then cycle mask), [Cmd.QUERY_EQ], [Cmd.QUERY_VOLUME],
     * [Cmd.QUERY_FEATURES], [Cmd.QUERY_KEYS], [Cmd.QUERY_PAIRED], [Cmd.QUERY_DYNAMIC] and
     * [Cmd.QUERY_ALL_EQ]. Layouts are in 'Queries (0x01xx)' and 'Other queries (not used by the app)'
     * in docs/PROTOCOL.md.
     *
     * A section with no OK response, or a payload too short to parse, is left at its default: null
     * for scalar fields, empty for maps and lists. Entries that do not fit in the payload are
     * skipped; paired devices and the custom EQ stop at the first such entry and keep the ones
     * read before it. Parsing skips the first returned data byte before reading the fields.
     *
     * @param r requester used for every query.
     * @return the state read; fields that could not be read are null or empty.
     */
    suspend fun readState(r: Requester): BudsState {
        val battery = last(query(r, Cmd.QUERY_STATUS))

        val ver = query(r, Cmd.QUERY_VERSION)
        val firmware =
            ver?.let { d ->
                if (d.size > 1) String(d, 1, d.size - 1, Charsets.US_ASCII) else logMalformed(Cmd.QUERY_VERSION)
            }

        val noise = last(query(r, Cmd.QUERY_NOISE, bytes(1, 1)))
        val cycle = last(query(r, Cmd.QUERY_NOISE, bytes(2, 1)))

        val eq =
            query(r, Cmd.QUERY_EQ)?.let { d ->
                if (d.size > 1) d[1].toInt() and 0xff else logMalformed(Cmd.QUERY_EQ)
            }
        val vol =
            query(r, Cmd.QUERY_VOLUME)?.let { d ->
                if (d.size > 1) d[1].toInt() != 0 else logMalformed(Cmd.QUERY_VOLUME)
            }

        val features = HashMap<Int, Boolean>()
        val req = ByteArray(1 + FEATURE_IDS.size)
        req[0] = FEATURE_IDS.size.toByte()
        FEATURE_IDS.forEachIndexed { i, id -> req[1 + i] = id.toByte() }
        query(r, Cmd.QUERY_FEATURES, req)?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 2
                    if (p + 1 < d.size) features[d[p].toInt() and 0xff] = d[p + 1].toInt() != 0
                }
            } else {
                logMalformed(Cmd.QUERY_FEATURES)
            }
        }

        val keys = HashMap<Int, Int>()
        query(r, Cmd.QUERY_KEYS, KEYS_REQUEST)?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 4
                    if (p + 3 < d.size) {
                        val button = d[p + 1].toInt() and 0xff
                        val action = d[p + 2].toInt() and 0xff
                        keys[keyId(button, action)] = d[p + 3].toInt() and 0xff
                    }
                }
            } else {
                logMalformed(Cmd.QUERY_KEYS)
            }
        }

        val devices = ArrayList<PairedDevice>()
        query(r, Cmd.QUERY_PAIRED)?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                var p = 2
                for (i in 0 until n) {
                    // Per entry: mac (6) | state | flag | total | name length | name
                    if (p + 10 > d.size) {
                        logMalformed(Cmd.QUERY_PAIRED)
                        break
                    }
                    val total = d[p + 6].toInt() and 0xff
                    val state = d[p + 7].toInt() and 0xff
                    val thisFlag = d[p + 8].toInt() and 0xff
                    val nameLen = d[p + 9].toInt() and 0xff
                    if (p + 10 + nameLen > d.size) {
                        logMalformed(Cmd.QUERY_PAIRED)
                        break
                    }
                    devices.add(
                        PairedDevice(
                            d.copyOfRange(p, p + 6),
                            String(d, p + 10, nameLen, Charsets.UTF_8),
                            state,
                            thisFlag == 1,
                        ),
                    )
                    p += 7 + total
                }
            } else {
                logMalformed(Cmd.QUERY_PAIRED)
            }
        }

        // 0x012c: n, [min, max, current, type] * n
        val dynamic = HashMap<Int, Int>()
        query(r, Cmd.QUERY_DYNAMIC)?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                for (i in 0 until n) {
                    val p = 2 + i * 4
                    if (p + 3 < d.size) dynamic[d[p + 3].toInt() and 0xff] = d[p + 2].toInt()
                }
            } else {
                logMalformed(Cmd.QUERY_DYNAMIC)
            }
        }

        // 0x0122: n, [selected, min, max, id, len, name, nBands, (freq u16, gain i8) * nBands] * n
        var customEq: List<Int>? = null
        var customFreqs: List<Int>? = null
        query(r, Cmd.QUERY_ALL_EQ)?.let { d ->
            if (d.size > 1) {
                val n = d[1].toInt() and 0xff
                var p = 2
                for (i in 0 until n) {
                    if (p + 5 > d.size) {
                        logMalformed(Cmd.QUERY_ALL_EQ)
                        break
                    }
                    val eqId = d[p + 3].toInt() and 0xff
                    val nameLen = d[p + 4].toInt() and 0xff
                    val bandsAt = p + 5 + nameLen
                    if (bandsAt >= d.size) {
                        logMalformed(Cmd.QUERY_ALL_EQ)
                        break
                    }
                    val nb = d[bandsAt].toInt() and 0xff
                    val end = bandsAt + 1 + nb * 3
                    if (end > d.size) {
                        logMalformed(Cmd.QUERY_ALL_EQ)
                        break
                    }
                    if (eqId == 4) {
                        val g = ArrayList<Int>(nb)
                        val f = ArrayList<Int>(nb)
                        for (k in 0 until nb) {
                            val q = bandsAt + 1 + k * 3
                            f.add((d[q].toInt() and 0xff) or ((d[q + 1].toInt() and 0xff) shl 8))
                            g.add(d[q + 2].toInt())
                        }
                        customEq = g
                        customFreqs = f
                    }
                    p = end
                }
            } else {
                logMalformed(Cmd.QUERY_ALL_EQ)
            }
        }

        return BudsState(
            battery,
            firmware,
            noise,
            cycle,
            eq,
            vol,
            features,
            keys,
            devices,
            dynamic,
            customEq,
            customFreqs,
        )
    }

    /**
     * Sends [command] and checks that the earbuds acknowledged it.
     *
     * Only the response status is checked; the response data is not read. A missing or failed
     * response is logged by command id.
     *
     * @param r requester that sends the frame.
     * @param command command to send, usually built by one of the functions in this object.
     * @return true only if the response is present and OK.
     */
    suspend fun send(
        r: Requester,
        command: Command,
    ): Boolean {
        val ok = r.request(command.cmd, command.data)?.isOk == true
        if (!ok) Log.w({ "Command 0x%04x failed".format(command.cmd) })
        return ok
    }

    /**
     * Registers the known events: query 0x0200, then register 0x0205.
     *
     * Sends [Cmd.QUERY_EVENTS], keeps the supported ids that are in [EventId.ALL], and sends
     * [Cmd.REGISTER_EVENTS] for them. Returns false without registering when the query has no
     * response or no supported id is known. Notifications then arrive as frames with cmd 0x0204
     * (see 'Event registration (0x02xx)' in docs/PROTOCOL.md).
     *
     * @param r requester used for both requests.
     * @return true if registration was acknowledged.
     */
    suspend fun registerEvents(r: Requester): Boolean {
        val supported = Events.parseSupported(r.request(Cmd.QUERY_EVENTS) ?: return false)
        val ids = supported.filter { it in EventId.ALL }
        if (ids.isEmpty()) {
            Log.d { "No known events to register" }
            return false
        }
        Log.d { "Registering events: ${ids.joinToString { "0x%02x".format(it) }}" }
        return send(r, Command(Cmd.REGISTER_EVENTS, Events.registerData(ids)))
    }

    /**
     * Builds the noise-control mode command (0x0404, action 01).
     *
     * Request bytes: `01 01 mode`. Modes are in 'Settings (0x04xx)' in docs/PROTOCOL.md.
     *
     * @param mode mode byte: 01 off, 02 transparency, 04/08/10/20 ANC levels.
     * @return the command to pass to [send].
     */
    fun setNoise(mode: Int): Command = Command(Cmd.SET_NOISE, bytes(1, 1, mode))

    /**
     * Builds the noise-control button cycle command (0x0404, action 02).
     *
     * Request bytes: `02 01 mask`.
     *
     * @param mask bit mask of the modes in the cycle: 01 off, 02 transparency, 08 ANC.
     * @return the command to pass to [send].
     */
    fun setNoiseCycle(mask: Int): Command = Command(Cmd.SET_NOISE, bytes(2, 1, mask))

    /**
     * Builds the EQ preset command (0x0406).
     *
     * Request byte: the preset id. Setting a preset keeps the custom EQ values (see [createCustomEq]).
     *
     * @param preset preset id: 00 nature balance, 01 bass boost, 02 clear vocals, 03 clear bass.
     * @return the command to pass to [send].
     */
    fun setEq(preset: Int): Command = Command(Cmd.SET_EQ, bytes(preset))

    /**
     * Builds the volume enhancer command (0x0407).
     *
     * @param on true to turn the volume enhancer on.
     * @return the command to pass to [send].
     */
    fun setHighVolume(on: Boolean): Command = Command(Cmd.SET_HIGH_VOLUME, bytes(flag(on)))

    /**
     * Builds the command that switches feature [id] on or off (0x0403).
     *
     * Request bytes: `id 01` or `id 00`. Feature ids are in 'Settings (0x04xx)' in docs/PROTOCOL.md.
     *
     * @param id feature id, for example 0x1a smart de-wind or 0x26 find my phone.
     * @param on true to switch the feature on.
     * @return the command to pass to [send].
     */
    fun setFeature(
        id: Int,
        on: Boolean,
    ): Command = Command(Cmd.SET_FEATURE, bytes(id, flag(on)))

    /**
     * Builds the Dynamic audio slider command (0x041b).
     *
     * Request bytes: `fb 05 value type`, so the range is always -5 to +5.
     *
     * @param type slider id: 1 low, 2 mid, 3 high.
     * @param value signed slider value, expected in -5 to +5 (not checked here).
     * @return the command to pass to [send].
     */
    fun setDynamicSlider(
        type: Int,
        value: Int,
    ): Command = Command(Cmd.SET_DYNAMIC_SLIDER, bytes(0xfb, 5, value, type))

    /**
     * Builds the button function command (0x0401) for one button and action.
     *
     * Request bytes: `01 01 button action fn`, one entry with device type 01. Function ids are in
     * 'Settings (0x04xx)' in docs/PROTOCOL.md.
     *
     * @param button button id, for example 01 multifunction or 04 noise control.
     * @param action action id: 01 single tap, 02 double tap, 03 triple tap, 04 touch and hold.
     * @param fn function id, for example 00 none or 01 play/pause.
     * @return the command to pass to [send].
     */
    fun setKey(
        button: Int,
        action: Int,
        fn: Int,
    ): Command = Command(Cmd.SET_KEYS, bytes(1, 1, button, action, fn))

    /**
     * Builds the fit test command (0x0405).
     *
     * Request byte: `01` to start, `00` to stop. The result arrives as fit-test event 0x04 (see
     * 'Events' in docs/PROTOCOL.md).
     *
     * @param start true to start the test, false to stop it.
     * @return the command to pass to [send].
     */
    fun fitTest(start: Boolean): Command = Command(Cmd.FIT_TEST, bytes(flag(start)))

    /**
     * Builds the dual-device command for one paired device (0x040b).
     *
     * Request bytes: `01 mac action`. Action 1 connects, 2 removes the device. Action 0 disconnects,
     * which this app has not tested. The response can take more than 2 s (see 'Settings (0x04xx)'
     * in docs/PROTOCOL.md).
     *
     * @param mac six address bytes of the device, in protocol byte order.
     * @param action 0 disconnect, 1 connect, 2 remove.
     * @return the command to pass to [send].
     */
    fun multiConnect(
        mac: ByteArray,
        action: Int,
    ): Command {
        val out = ByteArray(8)
        out[0] = 1
        mac.copyInto(out, 1)
        out[7] = action.toByte()
        return Command(Cmd.SET_MULTI_CONNECT, out)
    }

    /**
     * Builds the command that creates the custom EQ (0x0418, op 1).
     *
     * All gains are sent as 0 dB and the eq id as 0, as described in 'Settings (0x04xx)' (block
     * 0x0418) in docs/PROTOCOL.md.
     *
     * @param freqs band frequencies in Hz, sent as u16 little endian.
     * @return the command to pass to [send].
     */
    fun createCustomEq(freqs: List<Int>): Command {
        val zeros = IntArray(freqs.size)
        return Command(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(1, 0, zeros, freqs.toIntArray()))
    }

    /**
     * Builds the command that updates the custom EQ gains (0x0418, op 2).
     *
     * The earbuds switch to the custom EQ on their own after this command, as described in
     * 'Settings (0x04xx)' (block 0x0418) in docs/PROTOCOL.md. Create the custom EQ first with
     * [createCustomEq].
     *
     * @param gains signed gain per band in dB, expected in -6 to +6 (not checked here).
     * @param freqs band frequencies in Hz, in the same order and length as [gains].
     * @return the command to pass to [send].
     */
    fun updateCustomEq(
        gains: List<Int>,
        freqs: List<Int>,
    ): Command = Command(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(2, 4, gains.toIntArray(), freqs.toIntArray()))
}
