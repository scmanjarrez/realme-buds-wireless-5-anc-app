package dev.openbuds.app.data

import dev.openbuds.app.protocol.Cmd
import dev.openbuds.app.protocol.EventId
import dev.openbuds.app.protocol.Events
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.Frame
import dev.openbuds.app.protocol.Protocol
import dev.openbuds.app.util.Log

/** Sends requests to the earbuds and returns their response frames. */
fun interface Requester {
    /**
     * Sends a request and waits for its response.
     *
     * Args:
     *     cmd: Command id.
     *     data: Request payload.
     *
     * Returns:
     *     The response frame, or null if none arrived.
     */
    suspend fun request(
        cmd: Int,
        data: ByteArray,
    ): Frame?
}

/** Sends a request with an empty payload. */
suspend fun Requester.request(cmd: Int): Frame? = request(cmd, ByteArray(0))

/** A ready-to-send protocol command: command id plus payload. */
class Command(
    val cmd: Int,
    val data: ByteArray,
)

/** State read from the earbuds. Fields are null or empty when not readable. */
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

/** Device from the Dual-device list. [mac] is in protocol byte order. */
class PairedDevice(
    val mac: ByteArray,
    val name: String,
    val state: Int,
    val isThisDevice: Boolean,
) {
    /** Address in the usual format (the protocol sends it reversed). */
    val macText: String get() = mac.reversed().joinToString(":") { "%02X".format(it) }
}

object BudsApi {
    /** Feature ids known to be readable with 0x010d. */
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

    fun keyId(
        button: Int,
        action: Int,
    ): Int = (button shl 8) or action

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun flag(on: Boolean): Int = if (on) 1 else 0

    private fun last(d: ByteArray?): Int? = d?.lastOrNull()?.toInt()?.and(0xff)

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

    /** Reads the full device state, one request per section, in fixed order. */
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

    /** Sends [command] and returns true only if the response is present and OK. */
    suspend fun send(
        r: Requester,
        command: Command,
    ): Boolean {
        val ok = r.request(command.cmd, command.data)?.isOk == true
        if (!ok) Log.w({ "Command 0x%04x failed".format(command.cmd) })
        return ok
    }

    /**
     * Asks the earbuds which events they can send (0x0200) and registers the ones this app knows (0x0205).
     *
     * @return true if registration was acknowledged.
     */
    suspend fun registerEvents(r: Requester): Boolean {
        val supported = Events.parseSupported(r.request(Cmd.QUERY_EVENTS) ?: return false)
        val ids = supported.filter { it in EventId.ALL }
        if (ids.isEmpty()) {
            Log.d({ "No known events to register" })
            return false
        }
        Log.d({ "Registering events: ${ids.joinToString { "0x%02x".format(it) }}" })
        return send(r, Command(Cmd.REGISTER_EVENTS, Events.registerData(ids)))
    }

    /** Noise-control mode (0x0404, action 01). */
    fun setNoise(mode: Int): Command = Command(Cmd.SET_NOISE, bytes(1, 1, mode))

    /** Noise-control button cycle mask (0x0404, action 02). */
    fun setNoiseCycle(mask: Int): Command = Command(Cmd.SET_NOISE, bytes(2, 1, mask))

    /** EQ preset (0x0406). */
    fun setEq(preset: Int): Command = Command(Cmd.SET_EQ, bytes(preset))

    /** Volume enhancer on or off (0x0407). */
    fun setHighVolume(on: Boolean): Command = Command(Cmd.SET_HIGH_VOLUME, bytes(flag(on)))

    /** Switch feature [id] on or off (0x0403). */
    fun setFeature(
        id: Int,
        on: Boolean,
    ): Command = Command(Cmd.SET_FEATURE, bytes(id, flag(on)))

    /** Dynamic slider value for [type] (0x041b). */
    fun setDynamicSlider(
        type: Int,
        value: Int,
    ): Command = Command(Cmd.SET_DYNAMIC_SLIDER, bytes(0xfb, 5, value, type))

    /** Button function [fn] for [button] and [action] (0x0401). */
    fun setKey(
        button: Int,
        action: Int,
        fn: Int,
    ): Command = Command(Cmd.SET_KEYS, bytes(1, 1, button, action, fn))

    /** Starts or stops the fit test (0x0405). */
    fun fitTest(start: Boolean): Command = Command(Cmd.FIT_TEST, bytes(flag(start)))

    /** Connect (1) or delete (2) a paired device (0x040b). Action 0 disconnects, untested. */
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

    /** Creates the custom EQ with the given band frequencies (0x0418, op 1). */
    fun createCustomEq(freqs: List<Int>): Command {
        val zeros = IntArray(freqs.size)
        return Command(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(1, 0, zeros, freqs.toIntArray()))
    }

    /** Updates the custom EQ gains for the given band frequencies (0x0418, op 2). */
    fun updateCustomEq(
        gains: List<Int>,
        freqs: List<Int>,
    ): Command = Command(Cmd.SET_CUSTOM_EQ, Protocol.customEqData(2, 4, gains.toIntArray(), freqs.toIntArray()))
}
