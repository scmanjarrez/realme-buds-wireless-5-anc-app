package dev.openbuds.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Hex strings are copied from docs/PROTOCOL.md 'Worked examples'.
private const val EXAMPLE_1_BATTERY_QUERY = "aa 07 00 00 06 01 01 00 00"
private const val EXAMPLE_2_BATTERY_RESPONSE = "aa 0b 00 00 06 81 01 04 00 00 01 01 5a"
private const val EXAMPLE_3_SET_FEATURE = "aa 09 00 00 03 04 02 02 00 26 01"
private const val EXAMPLE_4_SET_NOISE = "aa 0a 00 00 04 04 03 03 00 01 01 08"
private const val EXAMPLE_5_CUSTOM_EQ =
    "aa 26 00 00 18 04 04 1f 00 02 fa 06 04 07 43 75 73 74 6f 6d 31 06 3e 00 fa " +
        "fa 00 00 e8 03 01 a0 0f 02 40 1f 03 80 3e 06"
private const val EXAMPLE_6_BATTERY_NOTIFY = "aa 0b 08 00 04 02 10 04 00 01 01 01 46"
private const val EXAMPLE_7_FIND_PHONE = "aa 08 08 00 02 05 20 01 00 05"
private const val EXAMPLE_8_SMART_ANC_MODE = "aa 0a 08 00 04 02 11 03 00 03 01 20"
private const val EXAMPLE_9_SMART_ANC_LEVEL = "aa 0b 08 00 04 02 12 04 00 03 04 01 10"

class ProtocolTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun buildWithoutDataWritesHeaderAndEmptyLength() {
        val frame = Protocol.build(0x0106, 0x41)
        assertArrayEquals(bytes(0xaa, 0x07, 0x00, 0x00, 0x06, 0x01, 0x41, 0x00, 0x00), frame)
    }

    @Test
    fun buildWithDataUpdatesLengthAndDlen() {
        val frame = Protocol.build(0x0106, 0x41, bytes(0x01, 0x02))
        assertArrayEquals(
            bytes(0xaa, 0x09, 0x00, 0x00, 0x06, 0x01, 0x41, 0x02, 0x00, 0x01, 0x02),
            frame,
        )
    }

    @Test
    fun parserRoundTripsOneFrame() {
        val frames = Protocol.Parser().feed(Protocol.build(0x0106, 0x41, bytes(0x00, 0x01)), 11)
        assertEquals(1, frames.size)
        assertEquals(0x0106, frames[0].cmd)
        assertEquals(0x41, frames[0].seq)
        assertArrayEquals(bytes(0x00, 0x01), frames[0].data)
    }

    @Test
    fun parserYieldsFrameOnlyAfterLastByteWhenFedByteByByte() {
        val raw = Protocol.build(0x0106, 0x41, bytes(0x00, 0x01))
        val parser = Protocol.Parser()
        for (i in 0 until raw.size - 1) {
            assertTrue(parser.feed(raw.copyOfRange(i, i + 1), 1).isEmpty())
        }
        val frames = parser.feed(raw.copyOfRange(raw.size - 1, raw.size), 1)
        assertEquals(1, frames.size)
        assertEquals(0x0106, frames[0].cmd)
    }

    @Test
    fun parserSkipsGarbageBeforeHeader() {
        val raw = bytes(0x01, 0x02, 0x00) + Protocol.build(0x0106, 0x41)
        val frames = Protocol.Parser().feed(raw, raw.size)
        assertEquals(1, frames.size)
        assertEquals(0x0106, frames[0].cmd)
        assertEquals(0x41, frames[0].seq)
    }

    @Test
    fun parserReturnsTwoFramesFromSingleFeed() {
        val raw = Protocol.build(0x0106, 0x01) + Protocol.build(0x0108, 0x02, bytes(0x07))
        val frames = Protocol.Parser().feed(raw, raw.size)
        assertEquals(2, frames.size)
        assertEquals(0x0106, frames[0].cmd)
        assertEquals(0x0108, frames[1].cmd)
        assertArrayEquals(bytes(0x07), frames[1].data)
    }

    @Test
    fun parserWaitsForIncompleteFrameThenCompletesOnNextFeed() {
        val raw = Protocol.build(0x0106, 0x41, bytes(0x00, 0x01))
        val parser = Protocol.Parser()
        val first = parser.feed(raw.copyOf(raw.size - 2), raw.size - 2)
        assertTrue(first.isEmpty())
        val second = parser.feed(raw.copyOfRange(raw.size - 2, raw.size), 2)
        assertEquals(1, second.size)
        assertArrayEquals(bytes(0x00, 0x01), second[0].data)
    }

    @Test
    fun frameIsResponseChecksHighBit() {
        assertTrue(Frame(0x8106, 0, ByteArray(0)).isResponse)
        assertFalse(Frame(0x0106, 0, ByteArray(0)).isResponse)
    }

    @Test
    fun frameIsOkChecksFirstByte() {
        assertTrue(Frame(0x0106, 0, bytes(0x00)).isOk)
        assertFalse(Frame(0x0106, 0, bytes(0x01)).isOk)
        assertFalse(Frame(0x0106, 0, ByteArray(0)).isOk)
    }

    @Test
    fun customEqDataLayoutMatchesSpec() {
        val actual = Protocol.customEqData(2, 4, intArrayOf(-6, 0, 1, 2, 3, 6))
        val expected =
            bytes(
                0x02,
                0xfa,
                0x06,
                0x04,
                0x07,
                0x43,
                0x75,
                0x73,
                0x74,
                0x6f,
                0x6d,
                0x31, // "Custom1"
                0x06,
                0x3e,
                0x00,
                0xfa, // 62 Hz, -6 dB
                0xfa,
                0x00,
                0x00, // 250 Hz, 0 dB
                0xe8,
                0x03,
                0x01, // 1000 Hz, +1 dB
                0xa0,
                0x0f,
                0x02, // 4000 Hz, +2 dB
                0x40,
                0x1f,
                0x03, // 8000 Hz, +3 dB
                0x80,
                0x3e,
                0x06, // 16000 Hz, +6 dB
            )
        assertArrayEquals(expected, actual)
    }

    @Test
    fun documentedBatteryQueryMatchesBuild() {
        assertArrayEquals(hex(EXAMPLE_1_BATTERY_QUERY), Protocol.build(0x0106, 0x01))
    }

    @Test
    fun documentedSetFeatureMatchesBuild() {
        assertArrayEquals(
            hex(EXAMPLE_3_SET_FEATURE),
            Protocol.build(0x0403, 0x02, bytes(0x26, 0x01)),
        )
    }

    @Test
    fun documentedNoiseControlMatchesBuild() {
        assertArrayEquals(
            hex(EXAMPLE_4_SET_NOISE),
            Protocol.build(0x0404, 0x03, bytes(0x01, 0x01, 0x08)),
        )
    }

    @Test
    fun documentedCustomEqMatchesBuild() {
        val data = Protocol.customEqData(2, 4, intArrayOf(-6, 0, 1, 2, 3, 6))
        assertArrayEquals(hex(EXAMPLE_5_CUSTOM_EQ), Protocol.build(0x0418, 0x04, data))
    }

    @Test
    fun documentedResponseAndNotificationsParse() {
        val response = parseOne(hex(EXAMPLE_2_BATTERY_RESPONSE))
        assertEquals(0x8106, response.cmd)
        assertEquals(1, response.seq)
        assertTrue(response.isResponse)
        assertTrue(response.isOk)
        assertArrayEquals(bytes(0x00, 0x01, 0x01, 0x5a), response.data)

        val battery = parseOne(hex(EXAMPLE_6_BATTERY_NOTIFY))
        assertEquals(0x0204, battery.cmd)
        assertEquals(0x10, battery.seq)
        assertFalse(battery.isResponse)
        assertArrayEquals(bytes(0x01, 0x01, 0x01, 0x46), battery.data)

        val findPhone = parseOne(hex(EXAMPLE_7_FIND_PHONE))
        assertEquals(0x0502, findPhone.cmd)
        assertEquals(0x20, findPhone.seq)
        assertArrayEquals(bytes(0x05), findPhone.data)
    }

    @Test
    fun documentedSmartAncEventsParse() {
        val smartMode = parseOne(hex(EXAMPLE_8_SMART_ANC_MODE))
        assertEquals(0x0204, smartMode.cmd)
        assertEquals(0x11, smartMode.seq)
        assertArrayEquals(bytes(0x03, 0x01, 0x20), smartMode.data)
        assertEquals(BudsEvent.Noise(mapOf(1 to 0x20)), Events.parse(smartMode))

        val smartLevel = parseOne(hex(EXAMPLE_9_SMART_ANC_LEVEL))
        assertEquals(0x0204, smartLevel.cmd)
        assertEquals(0x12, smartLevel.seq)
        assertArrayEquals(bytes(0x03, 0x04, 0x01, 0x10), smartLevel.data)
        assertEquals(BudsEvent.Unknown(3, listOf(4, 1, 16)), Events.parse(smartLevel))
    }

    @Test
    fun buildAcceptsMaxDataSizeAndLengthByteFits() {
        val frame = Protocol.build(0x0106, 0x41, ByteArray(248) { 0x11.toByte() })
        assertEquals(257, frame.size)
        assertEquals(0xff, frame[1].toInt() and 0xff)
        val frames = Protocol.Parser().feed(frame, frame.size)
        assertEquals(1, frames.size)
        assertEquals(248, frames[0].data.size)
    }

    @Test
    fun buildRejectsDataLargerThanMaxDataSize() {
        try {
            Protocol.build(0x0106, 0x41, ByteArray(249))
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("249"))
        }
    }

    private fun hex(text: String): ByteArray =
        text
            .trim()
            .split(Regex("\\s+"))
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private fun parseOne(raw: ByteArray): Frame {
        val frames = Protocol.Parser().feed(raw, raw.size)
        assertEquals(1, frames.size)
        return frames[0]
    }
}
