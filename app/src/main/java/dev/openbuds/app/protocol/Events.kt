package dev.openbuds.app.protocol

/** Ids of the spontaneous events (first data byte of a 0x0204 frame). */
object EventId {
    /** Battery level report. */
    const val BATTERY = 0x01

    /** Device state values. */
    const val STATE = 0x02

    /** Noise control report. */
    const val NOISE = 0x03

    /** Fit test result. */
    const val FIT_TEST = 0x04

    /** Dual device change; the caller reloads its state. */
    const val DUAL_DEVICE = 0x06

    /** Unidentified event. */
    const val UNKNOWN_0D = 0x0d

    /** Button interaction. */
    const val INTERACTION = 0xf1

    /** Unidentified event. */
    const val UNKNOWN_F4 = 0xf4

    /** All known event ids, in protocol order. */
    val ALL: List<Int> =
        listOf(BATTERY, STATE, NOISE, FIT_TEST, DUAL_DEVICE, UNKNOWN_0D, INTERACTION, UNKNOWN_F4)
}

/** Result of a fit test for one earbud. */
enum class FitStatus {
    /** The seal is not ideal; the user should try another ear tip. */
    NOT_IDEAL,

    /** The seal is good. */
    GOOD,

    /** The fit test failed (code 2 or 3). */
    FAILED,

    /** The earbud was not inserted in the ear. */
    NOT_INSERTED,

    /** The test was interrupted by the app. */
    INTERRUPTED,

    /** Unknown or missing status code. */
    UNKNOWN,
    ;

    /** Maps the raw status codes sent by the earbuds to a [FitStatus]. */
    companion object {
        /**
         * Converts a raw fit test code.
         *
         * @param code raw code: 0 not ideal, 1 good, 2 or 3 failed, 4 not inserted, 5 interrupted by the app.
         * @return the matching status, or [UNKNOWN] for any other code.
         */
        fun fromCode(code: Int): FitStatus =
            when (code) {
                0 -> NOT_IDEAL
                1 -> GOOD
                2, 3 -> FAILED
                4 -> NOT_INSERTED
                5 -> INTERRUPTED
                else -> UNKNOWN
            }
    }
}

/** A decoded spontaneous event sent by the earbuds. */
sealed interface BudsEvent {
    /** Battery level, 0..255 as reported. */
    data class Battery(
        val level: Int,
    ) : BudsEvent

    /** State values keyed by type. */
    data class State(
        val values: Map<Int, Int>,
    ) : BudsEvent

    /** Noise control values keyed by type. */
    data class Noise(
        val values: Map<Int, Int>,
    ) : BudsEvent

    /** Fit test status keyed by device type. */
    data class FitTest(
        val results: Map<Int, Int>,
    ) : BudsEvent {
        /**
         * Returns the fit status of one earbud.
         *
         * @param deviceType 1 for the left earbud, 2 for the right earbud.
         * @return the status, or [FitStatus.UNKNOWN] if the device type is missing from [results].
         */
        fun statusOf(deviceType: Int): FitStatus = FitStatus.fromCode(results[deviceType] ?: -1)
    }

    /** Dual device change notification. The payload is ignored. */
    data object DualDevice : BudsEvent

    /**
     * Find-my-phone request sent by the earbuds (raw action byte of cmd 0x0502).
     *
     * @property action raw action: 5 ring (toggle), 6 stop, -1 missing.
     */
    data class FindPhoneRequest(
        val action: Int,
    ) : BudsEvent

    /** Button interaction; [options] are little-endian 16-bit values. */
    data class Interaction(
        val deviceType: Int,
        val button: Int,
        val action: Int,
        val function: Int,
        val scene: Int,
        val options: List<Int>,
    ) : BudsEvent

    /** Event with an unknown id or a malformed payload. */
    data class Unknown(
        val id: Int,
        val payload: List<Int>,
    ) : BudsEvent
}

/** Decoders for the event notifications and the supported-events query. */
object Events {
    private const val BYTE_MASK = 0xff
    private const val STATUS_OK = 0
    private const val COUNT_SIZE = 1
    private const val PAIR_SIZE = 2
    private const val NOISE_SHORT_SIZE = 2
    private const val NOISE_VALUE_INDEX = 1
    private const val FIT_TEST_SIZE = 4
    private const val INTERACTION_HEADER_SIZE = 5
    private const val BYTE_BITS = 8

    /**
     * Decodes a 0x0204 frame.
     *
     * @return null if [frame] is not a notification or has no data. Unknown ids and malformed payloads
     *   are returned as [BudsEvent.Unknown].
     */
    fun parse(frame: Frame): BudsEvent? {
        if (frame.cmd != Cmd.NOTIFY || frame.data.isEmpty()) return null
        val id = frame.data[0].toUnsigned()
        val payload = frame.data.unsignedFrom(1)
        return when (id) {
            EventId.BATTERY -> battery(id, payload)
            EventId.STATE -> state(id, payload)
            EventId.NOISE -> noise(id, payload)
            EventId.FIT_TEST -> fitTest(id, payload)
            EventId.DUAL_DEVICE -> BudsEvent.DualDevice
            EventId.INTERACTION -> interaction(id, payload)
            else -> BudsEvent.Unknown(id, payload)
        }
    }

    /**
     * Decodes the response of 0x0200 (data = status, n, ids...).
     *
     * @return the supported event ids, or an empty list if the response is malformed or the status is not OK.
     */
    fun parseSupported(frame: Frame): List<Int> {
        val data = frame.data
        if (data.size < COUNT_SIZE + 1 || data[0].toUnsigned() != STATUS_OK) return emptyList()
        val count = data[1].toUnsigned()
        if (data.size < COUNT_SIZE + 1 + count) return emptyList()
        return data.unsignedFrom(2).take(count)
    }

    /**
     * Data for 0x0205 (register events): n, ids...
     *
     * @param ids event ids to register.
     */
    fun registerData(ids: List<Int>): ByteArray {
        val out = ByteArray(COUNT_SIZE + ids.size)
        out[0] = ids.size.toByte()
        ids.forEachIndexed { i, id -> out[COUNT_SIZE + i] = id.toByte() }
        return out
    }

    private fun battery(
        id: Int,
        payload: List<Int>,
    ): BudsEvent = if (payload.isEmpty()) BudsEvent.Unknown(id, payload) else BudsEvent.Battery(payload.last())

    private fun state(
        id: Int,
        payload: List<Int>,
    ): BudsEvent {
        val values = pairs(payload) ?: return BudsEvent.Unknown(id, payload)
        return BudsEvent.State(values)
    }

    private fun noise(
        id: Int,
        payload: List<Int>,
    ): BudsEvent {
        if (payload.size == NOISE_SHORT_SIZE) {
            return BudsEvent.Noise(mapOf(1 to payload[NOISE_VALUE_INDEX]))
        }
        if (payload.size < NOISE_SHORT_SIZE) return BudsEvent.Unknown(id, payload)
        val values = pairs(payload) ?: return BudsEvent.Unknown(id, payload)
        return BudsEvent.Noise(values)
    }

    private fun fitTest(
        id: Int,
        payload: List<Int>,
    ): BudsEvent {
        if (payload.size < FIT_TEST_SIZE) return BudsEvent.Unknown(id, payload)
        val results =
            mapOf(
                payload[0] to payload[1],
                payload[2] to payload[3],
            )
        return BudsEvent.FitTest(results)
    }

    private fun interaction(
        id: Int,
        payload: List<Int>,
    ): BudsEvent {
        if (payload.size < INTERACTION_HEADER_SIZE) return BudsEvent.Unknown(id, payload)
        val options =
            (INTERACTION_HEADER_SIZE until payload.size - 1 step PAIR_SIZE).map { i ->
                payload[i] or (payload[i + 1] shl BYTE_BITS)
            }
        return BudsEvent.Interaction(
            deviceType = payload[0],
            button = payload[1],
            action = payload[2],
            function = payload[3],
            scene = payload[4],
            options = options,
        )
    }

    /**
     * Reads a count byte followed by that many (type, value) pairs.
     *
     * @return the map, or null if the payload is empty or shorter than the declared count.
     */
    private fun pairs(payload: List<Int>): Map<Int, Int>? {
        if (payload.isEmpty()) return null
        val count = payload[0]
        if (payload.size < COUNT_SIZE + count * PAIR_SIZE) return null
        return (0 until count).associate { i ->
            val at = COUNT_SIZE + i * PAIR_SIZE
            payload[at] to payload[at + 1]
        }
    }

    private fun Byte.toUnsigned(): Int = toInt() and BYTE_MASK

    private fun ByteArray.unsignedFrom(start: Int): List<Int> = (start until size).map { this[it].toUnsigned() }
}
