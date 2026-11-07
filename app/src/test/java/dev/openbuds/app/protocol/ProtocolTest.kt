package dev.openbuds.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
