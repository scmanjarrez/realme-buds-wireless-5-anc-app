package com.example.realmebuds

/** Protocolo "tl" de los realme Buds Wireless 5 ANC. Ver capture/PROTOCOL.md. */
object Cmd {
    const val QUERY_VERSION = 0x0105
    const val QUERY_STATUS = 0x0106
    const val QUERY_KEYS = 0x0108
    const val QUERY_PAIRED = 0x0112
    const val QUERY_NOISE = 0x010c
    const val QUERY_FEATURES = 0x010d
    const val QUERY_EQ = 0x010f
    const val QUERY_VOLUME = 0x0110
    const val QUERY_ALL_EQ = 0x0122
    const val QUERY_DYNAMIC = 0x012c
    const val SET_KEYS = 0x0401
    const val SET_FEATURE = 0x0403
    const val SET_NOISE = 0x0404
    const val FIT_TEST = 0x0405
    const val SET_MULTI_CONNECT = 0x040b
    const val SET_EQ = 0x0406
    const val SET_HIGH_VOLUME = 0x0407
    const val SET_CUSTOM_EQ = 0x0418
    const val SET_DYNAMIC_SLIDER = 0x041b
}

/** Funciones asignables a los botones (0x0401). */
object KeyFn {
    const val NONE = 0x00
    const val PLAY_PAUSE = 0x01
    const val VOICE_ASSISTANT = 0x04
    const val PREVIOUS = 0x05
    const val NEXT = 0x06
    const val NOISE_CONTROL = 0x08
    const val GAME_MODE = 0x11
}

/** Máscara de modos del ciclo del botón de cancelación (0x0404 con acción 02). */
object CycleBit {
    // Corregido con la comprobación del usuario en realme Link.
    const val OFF = 0x01
    const val TRANSPARENCY = 0x02
    const val NOISE_CANCELLATION = 0x08
}

/** Ids de 0x0403 setSwitchFeature. */
object Feature {
    const val ENHANCE_VOICES = 0x09
    const val SMART_DEWIND = 0x1a
    const val SPATIAL_AUDIO = 0x1b
    const val DYNAMIC_AUDIO = 0x1d
    const val GAME_MODE = 0x06
    const val DUAL_CONNECTION = 0x11
    const val AUTO_ANSWER = 0x08
    const val FIND_MY_PHONE = 0x26
}

/** Valores de 0x0404 con los dos primeros bytes 01 01. */
object Noise {
    const val NORMAL = 0x01
    const val TRANSPARENT = 0x02
    const val MILD = 0x04
    const val MAX = 0x08
    const val MODERATE = 0x10
    const val SMART = 0x20
}

/** Valores de 0x0406. */
object Eq {
    const val NATURE_BALANCE = 0x00
    const val BASS_BOOST = 0x01
    const val CLEAR_VOCALS = 0x02
    const val CLEAR_BASS = 0x03
}

class Frame(val cmd: Int, val seq: Int, val data: ByteArray) {
    val isResponse: Boolean get() = (cmd and 0x8000) != 0
    val isOk: Boolean get() = data.isNotEmpty() && data[0].toInt() == 0
    override fun toString() =
        "cmd=0x%04x seq=0x%02x datos=%s".format(cmd, seq, data.toHex())
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

object Protocol {
    val EQ_FREQS = intArrayOf(62, 250, 1000, 4000, 8000, 16000)

    /** 0x0418: op (1 crear, 2 actualizar, 3 confirmar) | fa 06 | flag | nombre | 6 bandas. */
    fun customEqData(op: Int, flag: Int, gains: IntArray, freqs: IntArray = EQ_FREQS): ByteArray {
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

    /** AA | len | 00 00 | cmd LE | seq | dlen LE | datos, con len = tamaño de lo que sigue. */
    fun build(cmd: Int, seq: Int, data: ByteArray = ByteArray(0)): ByteArray {
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

    /** Acumula bytes del socket y devuelve las tramas completas. */
    class Parser {
        private var buf = ByteArray(0)

        fun feed(bytes: ByteArray, n: Int): List<Frame> {
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
