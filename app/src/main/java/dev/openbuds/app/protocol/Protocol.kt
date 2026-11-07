package dev.openbuds.app.protocol

/**
 * Protocol of the realme Buds Wireless 5 ANC. See docs/PROTOCOL.md.
 *
 * Constants are command ids, sent as the cmd field of a frame. A response uses the same id with
 * bit 15 set (0x8xxx).
 */
object Cmd {
    /** Version text query. Wire value 0x0105, see 'Queries (0x01xx)' in docs/PROTOCOL.md. */
    const val QUERY_VERSION = 0x0105

    /** Battery level query. Wire value 0x0106, see 'Queries (0x01xx)'. */
    const val QUERY_STATUS = 0x0106

    /** Button function query. Wire value 0x0108, see 'Queries (0x01xx)'. */
    const val QUERY_KEYS = 0x0108

    /** Paired devices query. Wire value 0x0112, see 'Queries (0x01xx)'. */
    const val QUERY_PAIRED = 0x0112

    /** Noise control mode query. Wire value 0x010c, see 'Queries (0x01xx)'. */
    const val QUERY_NOISE = 0x010c

    /** State query of the 0x0403 features. Wire value 0x010d, see 'Queries (0x01xx)'. */
    const val QUERY_FEATURES = 0x010d

    /** Active equalizer id query. Wire value 0x010f, see 'Queries (0x01xx)'. */
    const val QUERY_EQ = 0x010f

    /** Volume enhancer state query. Wire value 0x0110, see 'Queries (0x01xx)'. */
    const val QUERY_VOLUME = 0x0110

    /** Equalizer list query, custom equalizer included. Wire value 0x0122, see 'Queries (0x01xx)'. */
    const val QUERY_ALL_EQ = 0x0122

    /** Dynamic audio slider query. Wire value 0x012c, see 'Queries (0x01xx)'. */
    const val QUERY_DYNAMIC = 0x012c

    /** Supported event ids query. Wire value 0x0200, see 'Event registration (0x02xx)'. */
    const val QUERY_EVENTS = 0x0200

    /** Button function setter. Wire value 0x0401, see 'Settings (0x04xx)'. */
    const val SET_KEYS = 0x0401

    /** Feature switch setter (see [Feature]). Wire value 0x0403, see 'Settings (0x04xx)'. */
    const val SET_FEATURE = 0x0403

    /** Noise control setter. Wire value 0x0404, see 'Settings (0x04xx)'. */
    const val SET_NOISE = 0x0404

    /** Earbud fit test start or stop. Wire value 0x0405, see 'Settings (0x04xx)'. */
    const val FIT_TEST = 0x0405

    /** Dual device connect, disconnect or remove. Wire value 0x040b, see 'Settings (0x04xx)'. */
    const val SET_MULTI_CONNECT = 0x040b

    /** EQ mode setter (see [Eq]). Wire value 0x0406, see 'Settings (0x04xx)'. */
    const val SET_EQ = 0x0406

    /** Volume enhancer setter. Wire value 0x0407, see 'Settings (0x04xx)'. */
    const val SET_HIGH_VOLUME = 0x0407

    /** Custom equalizer create, update or confirm. Wire value 0x0418, see 'Settings (0x04xx)'. */
    const val SET_CUSTOM_EQ = 0x0418

    /** Dynamic audio frequency slider setter. Wire value 0x041b, see 'Settings (0x04xx)'. */
    const val SET_DYNAMIC_SLIDER = 0x041b

    /** Unsolicited notification from the earbuds. Wire value 0x0204, see 'Event registration (0x02xx)'. */
    const val NOTIFY = 0x0204

    /** Event registration. Wire value 0x0205, see 'Event registration (0x02xx)'. */
    const val REGISTER_EVENTS = 0x0205

    /**
     * Sent by the earbuds when Find my phone is on and the button is pressed.
     *
     * data[0] 5 = ring, which also acts as a toggle (the second press stops the ringing). data[0] 6 =
     * stop appears only in the vendor app code and is unconfirmed, see [FindPhone.STOP].
     *
     * Wire value 0x0502, see 'Unsolicited frames (0x05xx)' in docs/PROTOCOL.md.
     */
    const val FIND_PHONE_REQUEST = 0x0502
}

/**
 * Payload values (data[0]) of [Cmd.FIND_PHONE_REQUEST].
 *
 * See 'Unsolicited frames (0x05xx)' in docs/PROTOCOL.md.
 */
object FindPhone {
    /** Stop ringing. Wire value 6, seen only in the vendor app code (unconfirmed). */
    const val STOP = 6
}

/**
 * Functions assignable to the buttons (0x0401).
 *
 * See 'Settings (0x04xx)' in docs/PROTOCOL.md.
 */
object KeyFn {
    /** No function. Wire value 0x00. */
    const val NONE = 0x00

    /** Play or pause. Wire value 0x01. */
    const val PLAY_PAUSE = 0x01

    /** Voice assistant. Wire value 0x04. */
    const val VOICE_ASSISTANT = 0x04

    /** Previous track. Wire value 0x05. */
    const val PREVIOUS = 0x05

    /** Next track. Wire value 0x06. */
    const val NEXT = 0x06

    /** Noise control. Wire value 0x08. */
    const val NOISE_CONTROL = 0x08

    /** Game mode. Wire value 0x11. */
    const val GAME_MODE = 0x11
}

/**
 * Bit mask of the noise-control button cycle modes (0x0404 with action 02).
 *
 * Confirmed against realme Link. See 'Settings (0x04xx)' in docs/PROTOCOL.md.
 */
object CycleBit {
    /** Off (normal) mode in the cycle. Bit 0x01. */
    const val OFF = 0x01

    /** Transparency mode in the cycle. Bit 0x02. */
    const val TRANSPARENCY = 0x02

    /** Noise cancellation mode in the cycle. Bit 0x08. */
    const val NOISE_CANCELLATION = 0x08
}

/**
 * Ids for 0x0403 setSwitchFeature.
 *
 * See 'Settings (0x04xx)' in docs/PROTOCOL.md. All ids are high confidence there.
 */
object Feature {
    /** Enhance voices. Wire value 0x09. */
    const val ENHANCE_VOICES = 0x09

    /** Smart de-wind technology. Wire value 0x1a. */
    const val SMART_DEWIND = 0x1a

    /** Spatial audio. Wire value 0x1b. */
    const val SPATIAL_AUDIO = 0x1b

    /** Dynamic audio. Wire value 0x1d. */
    const val DYNAMIC_AUDIO = 0x1d

    /** Game mode. Wire value 0x06. */
    const val GAME_MODE = 0x06

    /** Dual-device connection. Wire value 0x11. */
    const val DUAL_CONNECTION = 0x11

    /** Auto answer. Wire value 0x08. */
    const val AUTO_ANSWER = 0x08

    /** Find my phone. Wire value 0x26. */
    const val FIND_MY_PHONE = 0x26
}

/**
 * Values of 0x0404 with the first two bytes 01 01.
 *
 * See 'Settings (0x04xx)' in docs/PROTOCOL.md.
 */
object Noise {
    /** Off (normal). Wire value 0x01. */
    const val NORMAL = 0x01

    /** Transparency. Wire value 0x02. */
    const val TRANSPARENT = 0x02

    /** Mild ANC level. Wire value 0x04. */
    const val MILD = 0x04

    /** Max ANC level. Wire value 0x08. */
    const val MAX = 0x08

    /** Moderate ANC level. Wire value 0x10. */
    const val MODERATE = 0x10

    /** Smart ANC. Wire value 0x20. */
    const val SMART = 0x20
}

/**
 * Values of 0x0406 (EQ mode).
 *
 * See 'Settings (0x04xx)' in docs/PROTOCOL.md.
 */
object Eq {
    /** Nature balance preset. Wire value 0x00. */
    const val NATURE_BALANCE = 0x00

    /** Bass boost preset. Wire value 0x01. */
    const val BASS_BOOST = 0x01

    /** Clear vocals preset. Wire value 0x02. */
    const val CLEAR_VOCALS = 0x02

    /** Clear bass preset. Wire value 0x03. */
    const val CLEAR_BASS = 0x03
}

/**
 * A decoded protocol frame.
 *
 * @property cmd command id; bit 15 is set for responses.
 * @property seq sequence number, echoed back in the response to the same request.
 * @property data payload after the 9-byte header; for a response the first byte is the status.
 */
class Frame(
    val cmd: Int,
    val seq: Int,
    val data: ByteArray,
) {
    /** True if this frame is a response, that is, bit 15 of [cmd] is set. */
    val isResponse: Boolean get() = (cmd and 0x8000) != 0

    /** True if the first data byte is 00, the OK status of a response. False if [data] is empty. */
    val isOk: Boolean get() = data.isNotEmpty() && data[0].toInt() == 0

    override fun toString() = "cmd=0x%04x seq=0x%02x data=%s".format(cmd, seq, data.toHex())
}

/**
 * Return the bytes as lowercase hex without separators.
 *
 * @return the hex string, two digits per byte.
 */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Frame encoding and decoding for the protocol, and the custom equalizer payload.
 *
 * See 'Frame anatomy' in docs/PROTOCOL.md for the layout.
 */
object Protocol {
    /**
     * Largest payload a frame can carry, in bytes.
     *
     * The len byte counts the 7 header bytes after it plus the data and must fit in one byte, so
     * the data is at most 255 - 7 = 248 bytes.
     */
    private const val MAX_DATA_SIZE = 248

    /**
     * Band center frequencies of the custom equalizer, in Hz.
     *
     * The earbuds report the 62 Hz band as 50 Hz. See 'Settings (0x04xx)' in docs/PROTOCOL.md.
     */
    val EQ_FREQS = intArrayOf(62, 250, 1000, 4000, 8000, 16000)

    /**
     * Build the data of 0x0418 (custom equalizer) for the create, update or confirm action.
     *
     * Layout: op, bytes `0xfa 0x06` (min -6 dB, max +6 dB), flag, name length and name ("Custom1"),
     * band count, then for each band the frequency (u16 LE) and the gain (i8). See 'Settings (0x04xx)' in
     * docs/PROTOCOL.md.
     *
     * @param op action: 1 create, 2 update, 3 confirm.
     * @param flag equalizer id: 4 for the custom equalizer, 0 when creating it.
     * @param gains gain of each band in dB, one value per entry of [freqs].
     * @param freqs center frequency of each band in Hz, see [EQ_FREQS].
     * @return the raw payload of the command.
     */
    fun customEqData(
        op: Int,
        flag: Int,
        gains: IntArray,
        freqs: IntArray = EQ_FREQS,
    ): ByteArray {
        val name = "Custom1".toByteArray(Charsets.US_ASCII)
        val out = java.io.ByteArrayOutputStream()
        out.write(op)
        out.write(0xfa)
        out.write(0x06)
        out.write(flag)
        out.write(name.size)
        out.write(name, 0, name.size)
        out.write(freqs.size)
        for (i in freqs.indices) {
            out.write(freqs[i] and 0xff)
            out.write((freqs[i] shr 8) and 0xff)
            out.write(gains[i] and 0xff)
        }
        return out.toByteArray()
    }

    /**
     * Build a frame: `AA`, len, `00 00`, cmd (u16 LE), seq, dlen (u16 LE), data.
     *
     * The len byte is the total frame size minus 2, so it does not count the `AA` and len bytes.
     * dlen is the size of [data].
     *
     * @param cmd command id, see [Cmd].
     * @param seq sequence number, echoed back in the response.
     * @param data payload of the command; empty by default, at most 248 bytes.
     * @return the encoded frame.
     * @throws IllegalArgumentException if [data] is larger than 248 bytes.
     */
    fun build(
        cmd: Int,
        seq: Int,
        data: ByteArray = ByteArray(0),
    ): ByteArray {
        require(data.size <= MAX_DATA_SIZE) { "Frame data too large: ${data.size} bytes (max $MAX_DATA_SIZE)" }
        val body = ByteArray(7 + data.size)
        body[0] = 0
        body[1] = 0
        body[2] = (cmd and 0xff).toByte()
        body[3] = ((cmd shr 8) and 0xff).toByte()
        body[4] = (seq and 0xff).toByte()
        body[5] = (data.size and 0xff).toByte()
        body[6] = ((data.size shr 8) and 0xff).toByte()
        data.copyInto(body, 7)
        val out = ByteArray(2 + body.size)
        out[0] = 0xAA.toByte()
        out[1] = body.size.toByte()
        body.copyInto(out, 2)
        return out
    }

    /**
     * Incremental frame parser for the socket byte stream.
     *
     * [feed] appends bytes to an internal buffer and returns each frame that is complete. Bytes
     * before an `AA` start byte are dropped to resync. A frame is complete when len + 2 bytes are
     * buffered. Frames shorter than the 9-byte header are skipped. The data is cut at the end of
     * the frame, so a dlen that runs past the frame is ignored.
     */
    class Parser {
        private var buf = ByteArray(0)

        /**
         * Append received bytes and return the frames they complete.
         *
         * Partial frames stay buffered until a later call completes them.
         *
         * @param bytes buffer holding the received bytes.
         * @param n number of valid bytes at the start of [bytes].
         * @return the complete frames, in order of arrival.
         */
        fun feed(
            bytes: ByteArray,
            n: Int,
        ): List<Frame> {
            buf += bytes.copyOf(n)
            val frames = ArrayList<Frame>()
            while (buf.size >= 2) {
                if (buf[0] != 0xAA.toByte()) {
                    buf = buf.copyOfRange(1, buf.size)
                    continue
                }
                val total = (buf[1].toInt() and 0xff) + 2
                if (buf.size < total) break
                if (total >= 9) {
                    val cmd = (buf[4].toInt() and 0xff) or ((buf[5].toInt() and 0xff) shl 8)
                    val seq = buf[6].toInt() and 0xff
                    val dlen = (buf[7].toInt() and 0xff) or ((buf[8].toInt() and 0xff) shl 8)
                    val end = minOf(total, 9 + dlen)
                    frames.add(Frame(cmd, seq, buf.copyOfRange(9, end)))
                }
                buf = buf.copyOfRange(total, buf.size)
            }
            return frames
        }
    }
}
