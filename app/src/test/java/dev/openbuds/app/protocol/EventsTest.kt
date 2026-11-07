package dev.openbuds.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventsTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun notify(vararg values: Int): Frame = Frame(Cmd.NOTIFY, 0, bytes(*values))

    @Test
    fun parseReturnsNullForNonNotifyFrame() {
        val frame = Frame(0x0106, 0, bytes(0x01, 0x00, 0x55))
        assertNull(Events.parse(frame))
    }

    @Test
    fun parseReturnsNullForEmptyData() {
        assertNull(Events.parse(notify()))
    }

    @Test
    fun findPhoneRequestRingDecodesActionFive() {
        assertEquals(BudsEvent.FindPhoneRequest(5), Events.parse(Frame(Cmd.FIND_PHONE_REQUEST, 0, bytes(0x05))))
    }

    @Test
    fun findPhoneRequestStopDecodesActionSix() {
        assertEquals(BudsEvent.FindPhoneRequest(6), Events.parse(Frame(Cmd.FIND_PHONE_REQUEST, 0, bytes(0x06))))
    }

    @Test
    fun findPhoneRequestWithoutDataDecodesActionMinusOne() {
        assertEquals(BudsEvent.FindPhoneRequest(-1), Events.parse(Frame(Cmd.FIND_PHONE_REQUEST, 0, bytes())))
    }

    @Test
    fun batteryDecodesSingleEarbudLevel() {
        assertEquals(BudsEvent.Battery(85), Events.parse(notify(0x01, 0x01, 0x01, 0x55)))
    }

    @Test
    fun batteryMasksChargingBit() {
        assertEquals(BudsEvent.Battery(85), Events.parse(notify(0x01, 0x01, 0x01, 0xd5)))
    }

    @Test
    fun batteryReturnsMinimumOfEarbudEntries() {
        assertEquals(BudsEvent.Battery(70), Events.parse(notify(0x01, 0x02, 0x01, 0x50, 0x02, 0x46)))
    }

    @Test
    fun batteryIgnoresCaseEntry() {
        assertEquals(BudsEvent.Battery(70), Events.parse(notify(0x01, 0x02, 0x01, 0x46, 0x03, 0x64)))
    }

    @Test
    fun batteryWithOnlyCaseEntryReturnsUnknown() {
        assertEquals(BudsEvent.Unknown(1, listOf(1, 3, 0x64)), Events.parse(notify(0x01, 0x01, 0x03, 0x64)))
    }

    @Test
    fun batteryTruncatedPairsReturnUnknown() {
        assertEquals(BudsEvent.Unknown(1, listOf(2, 1, 0x50)), Events.parse(notify(0x01, 0x02, 0x01, 0x50)))
    }

    @Test
    fun batteryZeroCountReturnsUnknown() {
        assertEquals(BudsEvent.Unknown(1, listOf(0)), Events.parse(notify(0x01, 0x00)))
    }

    @Test
    fun batteryWithEmptyPayloadReturnsUnknown() {
        assertEquals(BudsEvent.Unknown(1, emptyList()), Events.parse(notify(0x01)))
    }

    @Test
    fun stateDecodesTypeValuePairs() {
        assertEquals(
            BudsEvent.State(mapOf(9 to 1, 0x1a to 0)),
            Events.parse(notify(0x02, 0x02, 0x09, 0x01, 0x1a, 0x00)),
        )
    }

    @Test
    fun truncatedStateReturnsUnknown() {
        assertEquals(
            BudsEvent.Unknown(2, listOf(2, 9, 1)),
            Events.parse(notify(0x02, 0x02, 0x09, 0x01)),
        )
    }

    @Test
    fun shortNoiseDecodesSingleValueAsTypeOne() {
        assertEquals(BudsEvent.Noise(mapOf(1 to 8)), Events.parse(notify(0x03, 0x01, 0x08)))
    }

    @Test
    fun longNoiseDecodesTypeValuePairs() {
        assertEquals(
            BudsEvent.Noise(mapOf(1 to 4, 2 to 1)),
            Events.parse(notify(0x03, 0x02, 0x01, 0x04, 0x02, 0x01)),
        )
    }

    @Test
    fun truncatedNoiseReturnsUnknown() {
        assertEquals(
            BudsEvent.Unknown(3, listOf(2, 1, 4)),
            Events.parse(notify(0x03, 0x02, 0x01, 0x04)),
        )
    }

    @Test
    fun fitTestDecodesTwoResults() {
        assertEquals(
            BudsEvent.FitTest(mapOf(1 to 2, 2 to 0)),
            Events.parse(notify(0x04, 0x01, 0x02, 0x02, 0x00)),
        )
    }

    @Test
    fun fitStatusFromCodeMapsKnownCodes() {
        assertEquals(FitStatus.NOT_IDEAL, FitStatus.fromCode(0))
        assertEquals(FitStatus.GOOD, FitStatus.fromCode(1))
        assertEquals(FitStatus.FAILED, FitStatus.fromCode(2))
        assertEquals(FitStatus.FAILED, FitStatus.fromCode(3))
        assertEquals(FitStatus.NOT_INSERTED, FitStatus.fromCode(4))
        assertEquals(FitStatus.INTERRUPTED, FitStatus.fromCode(5))
    }

    @Test
    fun fitStatusFromCodeReturnsUnknownForOtherCodes() {
        assertEquals(FitStatus.UNKNOWN, FitStatus.fromCode(9))
        assertEquals(FitStatus.UNKNOWN, FitStatus.fromCode(-1))
    }

    @Test
    fun fitTestStatusOfReturnsPerDeviceStatus() {
        val event = BudsEvent.FitTest(mapOf(1 to 1, 2 to 0))
        assertEquals(FitStatus.GOOD, event.statusOf(1))
        assertEquals(FitStatus.NOT_IDEAL, event.statusOf(2))
    }

    @Test
    fun fitTestStatusOfMissingDeviceReturnsUnknown() {
        assertEquals(FitStatus.UNKNOWN, BudsEvent.FitTest(mapOf(1 to 1)).statusOf(2))
    }

    @Test
    fun dualDeviceReturnsDualDeviceEvent() {
        assertEquals(BudsEvent.DualDevice, Events.parse(notify(0x06, 0x01, 0x02)))
    }

    @Test
    fun interactionWithoutOptionsDecodesHeaderOnly() {
        assertEquals(
            BudsEvent.Interaction(1, 2, 1, 8, 5, emptyList()),
            Events.parse(notify(0xf1, 0x01, 0x02, 0x01, 0x08, 0x05)),
        )
    }

    @Test
    fun interactionDecodesLittleEndianOption() {
        assertEquals(
            BudsEvent.Interaction(1, 2, 1, 8, 5, listOf(0x1234)),
            Events.parse(notify(0xf1, 0x01, 0x02, 0x01, 0x08, 0x05, 0x34, 0x12)),
        )
    }

    @Test
    fun interactionIgnoresDanglingOddOptionByte() {
        assertEquals(
            BudsEvent.Interaction(1, 2, 1, 8, 5, listOf(0x1234)),
            Events.parse(notify(0xf1, 0x01, 0x02, 0x01, 0x08, 0x05, 0x34, 0x12, 0x99)),
        )
    }

    @Test
    fun interactionWithFewerThanFiveBytesReturnsUnknown() {
        assertEquals(
            BudsEvent.Unknown(0xf1, listOf(1, 2, 1, 8)),
            Events.parse(notify(0xf1, 0x01, 0x02, 0x01, 0x08)),
        )
    }

    @Test
    fun unknownIdReturnsUnknownWithPayload() {
        assertEquals(BudsEvent.Unknown(0x0d, listOf(1, 2)), Events.parse(notify(0x0d, 0x01, 0x02)))
    }

    @Test
    fun unknownIdWithoutPayloadReturnsUnknownEmpty() {
        assertEquals(BudsEvent.Unknown(0x77, emptyList()), Events.parse(notify(0x77)))
    }

    @Test
    fun parseSupportedReturnsIdsOnOkStatus() {
        assertEquals(listOf(1, 2, 4), Events.parseSupported(Frame(0x0200, 0, bytes(0x00, 0x03, 0x01, 0x02, 0x04))))
    }

    @Test
    fun parseSupportedReturnsEmptyOnNonZeroStatus() {
        assertEquals(emptyList<Int>(), Events.parseSupported(Frame(0x0200, 0, bytes(0x01, 0x03, 0x01, 0x02, 0x04))))
    }

    @Test
    fun parseSupportedReturnsEmptyOnTruncatedList() {
        assertEquals(emptyList<Int>(), Events.parseSupported(Frame(0x0200, 0, bytes(0x00, 0x03, 0x01, 0x02))))
    }

    @Test
    fun registerDataEncodesCountAndIds() {
        assertArrayEquals(bytes(3, 1, 3, 4), Events.registerData(listOf(1, 3, 4)))
    }
}
