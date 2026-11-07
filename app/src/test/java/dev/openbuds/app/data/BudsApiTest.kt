package dev.openbuds.app.data

import dev.openbuds.app.protocol.Cmd
import dev.openbuds.app.protocol.CycleBit
import dev.openbuds.app.protocol.Eq
import dev.openbuds.app.protocol.Feature
import dev.openbuds.app.protocol.Frame
import dev.openbuds.app.protocol.KeyFn
import dev.openbuds.app.protocol.Noise
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake device: answers a request with the payload keyed by (cmd, first request byte), or null. */
private class FakeRequester(
    private val responses: Map<Pair<Int, Int>, ByteArray>,
) : Requester {
    override suspend fun request(
        cmd: Int,
        data: ByteArray,
    ): Frame? {
        val first = data.firstOrNull()?.toInt()?.and(0xff) ?: -1
        val payload = responses[cmd to first] ?: return null
        return Frame(cmd or 0x8000, 0x41, payload)
    }
}

class BudsApiTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

    /** Key for a request with no payload (first byte -1) or with the given first byte. */
    private fun key(
        cmd: Int,
        first: Int = -1,
    ): Pair<Int, Int> = cmd to first

    @Test
    fun readStateParsesEveryResponseSection() {
        val mac = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val requester =
            FakeRequester(
                mapOf(
                    // Battery: status byte 00, level 0x5a = 90.
                    key(Cmd.QUERY_STATUS) to bytes(0x00, 0x5a),
                    // Firmware: status byte 00, then ASCII version.
                    key(Cmd.QUERY_VERSION) to bytes(0x00) + ascii("1.2.3"),
                    // Noise mode (request 01 01) and button cycle mask (request 02 01).
                    key(Cmd.QUERY_NOISE, 0x01) to bytes(0x00, 0x01, 0x01, 0x08),
                    key(Cmd.QUERY_NOISE, 0x02) to bytes(0x00, 0x02, 0x01, 0x0b),
                    // EQ preset: byte 1 is the preset id (2 = clear vocals).
                    key(Cmd.QUERY_EQ) to bytes(0x00, 0x02),
                    // Volume enhancer: byte 1 nonzero means on.
                    key(Cmd.QUERY_VOLUME) to bytes(0x00, 0x01),
                    // Features: n = 2, then (id, flag) pairs.
                    key(Cmd.QUERY_FEATURES, 8) to bytes(0x00, 0x02, 0x09, 0x01, 0x1a, 0x00),
                    // Keys: n = 2, then 4-byte entries (unused, button, action, function).
                    key(Cmd.QUERY_KEYS, 0x0e) to
                        bytes(0x00, 0x02, 0x0e, 0x01, 0x01, 0x04, 0x0e, 0x02, 0x02, 0x11),
                    // Paired devices: n = 1, then mac(6) total state flag nameLen name.
                    key(Cmd.QUERY_PAIRED) to
                        bytes(0x00, 0x01) + mac + bytes(0x07, 0x02, 0x01, 0x04) + ascii("Buds"),
                    // Dynamic sliders: n = 3, then min max current type.
                    key(Cmd.QUERY_DYNAMIC) to
                        bytes(
                            0x00,
                            0x03,
                            0xf6,
                            0x0a,
                            0xfd,
                            0x01,
                            0xf6,
                            0x0a,
                            0x00,
                            0x02,
                            0x00,
                            0x14,
                            0x14,
                            0x03,
                        ),
                    // All EQ: n = 2. Entry 1 is preset 0 with no bands; entry 2 is custom preset 4.
                    key(Cmd.QUERY_ALL_EQ) to
                        bytes(
                            0x00,
                            0x02,
                            0x00,
                            0x00,
                            0x00,
                            0x00,
                            0x03,
                            0x4e,
                            0x61,
                            0x74,
                            0x00,
                            0x01,
                            0x00,
                            0x00,
                            0x04,
                            0x03,
                            0x43,
                            0x75,
                            0x73,
                            0x06,
                            0x3e,
                            0x00,
                            0x06,
                            0xfa,
                            0x00,
                            0xfc,
                            0xe8,
                            0x03,
                            0x00,
                            0xa0,
                            0x0f,
                            0x03,
                            0x40,
                            0x1f,
                            0xfa,
                            0x80,
                            0x3e,
                            0x02,
                        ),
                ),
            )

        val state = runBlocking { BudsApi.readState(requester) }

        assertEquals(90, state.battery)
        assertEquals("1.2.3", state.firmware)
        assertEquals(0x08, state.noise)
        assertEquals(0x0b, state.noiseCycle)
        assertEquals(2, state.eq)
        assertEquals(true, state.volumeEnhancer)
        assertEquals(mapOf(Feature.ENHANCE_VOICES to true, Feature.SMART_DEWIND to false), state.features)
        assertEquals(
            mapOf(BudsApi.keyId(1, 1) to 0x04, BudsApi.keyId(2, 2) to KeyFn.GAME_MODE),
            state.keys,
        )

        assertEquals(1, state.devices.size)
        val device = state.devices[0]
        assertEquals("Buds", device.name)
        assertEquals(2, device.state)
        assertTrue(device.isThisDevice)
        assertArrayEquals(mac, device.mac)
        assertEquals("06:05:04:03:02:01", device.macText)

        assertEquals(mapOf(1 to -3, 2 to 0, 3 to 20), state.dynamic)

        assertEquals(listOf(6, -4, 0, 3, -6, 2), state.customEq)
        assertEquals(listOf(62, 250, 1000, 4000, 8000, 16000), state.customEqFreqs)
    }

    @Test
    fun readStateReturnsEmptyFieldsWhenEveryRequestFails() {
        val state = runBlocking { BudsApi.readState(FakeRequester(emptyMap())) }

        assertNull(state.battery)
        assertNull(state.firmware)
        assertNull(state.noise)
        assertNull(state.noiseCycle)
        assertNull(state.eq)
        assertNull(state.volumeEnhancer)
        assertTrue(state.features.isEmpty())
        assertTrue(state.keys.isEmpty())
        assertTrue(state.devices.isEmpty())
        assertTrue(state.dynamic.isEmpty())
        assertNull(state.customEq)
        assertNull(state.customEqFreqs)
    }

    @Test
    fun setNoiseWritesModeWithNoiseHeader() {
        val command = BudsApi.setNoise(Noise.MAX)
        assertEquals(Cmd.SET_NOISE, command.cmd)
        assertArrayEquals(bytes(0x01, 0x01, 0x08), command.data)
    }

    @Test
    fun setNoiseCycleWritesMaskWithCycleHeader() {
        val command = BudsApi.setNoiseCycle(CycleBit.OFF or CycleBit.NOISE_CANCELLATION)
        assertEquals(Cmd.SET_NOISE, command.cmd)
        assertArrayEquals(bytes(0x02, 0x01, 0x09), command.data)
    }

    @Test
    fun setEqWritesPresetId() {
        val command = BudsApi.setEq(Eq.BASS_BOOST)
        assertEquals(Cmd.SET_EQ, command.cmd)
        assertArrayEquals(bytes(0x01), command.data)
    }

    @Test
    fun setHighVolumeWritesFlag() {
        assertEquals(Cmd.SET_HIGH_VOLUME, BudsApi.setHighVolume(true).cmd)
        assertArrayEquals(bytes(0x01), BudsApi.setHighVolume(true).data)
        assertArrayEquals(bytes(0x00), BudsApi.setHighVolume(false).data)
    }

    @Test
    fun setFeatureWritesIdAndFlag() {
        val command = BudsApi.setFeature(Feature.SPATIAL_AUDIO, true)
        assertEquals(Cmd.SET_FEATURE, command.cmd)
        assertArrayEquals(bytes(0x1b, 0x01), command.data)
    }

    @Test
    fun setDynamicSliderWritesPrefixValueAndType() {
        val command = BudsApi.setDynamicSlider(1, -3)
        assertEquals(Cmd.SET_DYNAMIC_SLIDER, command.cmd)
        assertArrayEquals(bytes(0xfb, 0x05, 0xfd, 0x01), command.data)
    }

    @Test
    fun setKeyWritesButtonActionAndFunction() {
        val command = BudsApi.setKey(1, 2, KeyFn.NEXT)
        assertEquals(Cmd.SET_KEYS, command.cmd)
        assertArrayEquals(bytes(0x01, 0x01, 0x01, 0x02, 0x06), command.data)
    }

    @Test
    fun fitTestWritesStartFlag() {
        assertEquals(Cmd.FIT_TEST, BudsApi.fitTest(true).cmd)
        assertArrayEquals(bytes(0x01), BudsApi.fitTest(true).data)
        assertArrayEquals(bytes(0x00), BudsApi.fitTest(false).data)
    }

    @Test
    fun multiConnectWritesMacAndAction() {
        val mac = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        val command = BudsApi.multiConnect(mac, 1)
        assertEquals(Cmd.SET_MULTI_CONNECT, command.cmd)
        assertArrayEquals(bytes(0x01, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x01), command.data)
    }

    @Test
    fun sendReturnsTrueOnOkFrame() {
        val requester = FakeRequester(mapOf(key(Cmd.SET_EQ, 1) to bytes(0x00)))
        assertTrue(runBlocking { BudsApi.send(requester, BudsApi.setEq(1)) })
    }

    @Test
    fun sendReturnsFalseWhenNoFrameArrives() {
        assertFalse(runBlocking { BudsApi.send(FakeRequester(emptyMap()), BudsApi.setEq(1)) })
    }

    @Test
    fun sendReturnsFalseOnNonOkStatus() {
        val requester = FakeRequester(mapOf(key(Cmd.SET_EQ, 1) to bytes(0x01)))
        assertFalse(runBlocking { BudsApi.send(requester, BudsApi.setEq(1)) })
    }
}
