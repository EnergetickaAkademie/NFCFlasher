package eu.swpelc.nfcflasher

import eu.swpelc.nfcflasher.nfc.EnakNfcProtocol
import eu.swpelc.nfcflasher.nfc.EnakTagKind
import eu.swpelc.nfcflasher.nfc.ProtocolMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnakNfcProtocolTest {
    @Test
    fun v2BuildingRecordUsesExternalTypeAndVersionedPayload() {
        val record = EnakNfcProtocol.createBuildingRecord(17, ProtocolMode.V2)

        assertEquals(EnakNfcProtocol.TNF_EXTERNAL_TYPE, record.tnf)
        assertArrayEquals("cz.enak:building".toByteArray(), record.type)
        assertArrayEquals(byteArrayOf(2, 17), record.payload)
    }

    @Test
    fun legacyBuildingRecordReproducesOldEncoding() {
        val record = EnakNfcProtocol.createBuildingRecord(7, ProtocolMode.LEGACY_V1)

        assertEquals(EnakNfcProtocol.TNF_WELL_KNOWN, record.tnf)
        assertArrayEquals(byteArrayOf('B'.code.toByte()), record.type)
        assertArrayEquals(byteArrayOf(7), record.payload)
    }

    @Test
    fun resetRecordUsesSelectedProtocolVersion() {
        val v2 = EnakNfcProtocol.createResetRecord(ProtocolMode.V2)
        val legacy = EnakNfcProtocol.createResetRecord(ProtocolMode.LEGACY_V1)

        assertArrayEquals("cz.enak:cmd".toByteArray(), v2.type)
        assertArrayEquals(byteArrayOf(2, 1), v2.payload)
        assertArrayEquals(byteArrayOf(1, 1), legacy.payload)
    }

    @Test
    fun debugRecordAlwaysUsesV2CommandPayload() {
        val record = EnakNfcProtocol.createDebugRecord()

        assertEquals(EnakNfcProtocol.TNF_EXTERNAL_TYPE, record.tnf)
        assertArrayEquals("cz.enak:cmd".toByteArray(), record.type)
        assertArrayEquals(byteArrayOf(2, 2), record.payload)
    }

    @Test
    fun bulldozerRecordAlwaysUsesV2CommandPayload() {
        val record = EnakNfcProtocol.createBulldozerRecord()

        assertEquals(EnakNfcProtocol.TNF_EXTERNAL_TYPE, record.tnf)
        assertArrayEquals("cz.enak:cmd".toByteArray(), record.type)
        assertArrayEquals(byteArrayOf(2, 3), record.payload)
    }

    @Test
    fun v2WifiPayloadIncludesVersionAndBigEndianCrc32() {
        val record = EnakNfcProtocol.createWifiRecord(
            "ENAK",
            "password",
            false,
            ProtocolMode.V2
        )

        assertEquals(EnakNfcProtocol.TNF_EXTERNAL_TYPE, record.tnf)
        assertArrayEquals("cz.enak:wifi".toByteArray(), record.type)
        assertArrayEquals(
            byteArrayOf(
                2, 1, 4,
                'E'.code.toByte(), 'N'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte(),
                8,
                'p'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 's'.code.toByte(),
                'w'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'd'.code.toByte(),
                0x28, 0x18, 0x7D, 0x4F
            ),
            record.payload
        )
    }

    @Test
    fun buildingDecoderRecognizesV2AndLegacyButRejectsMalformedRecords() {
        val v2 = EnakNfcProtocol.decodeBuilding(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:building".toByteArray(),
            byteArrayOf(2, 9)
        )
        val legacy = EnakNfcProtocol.decodeBuilding(
            EnakNfcProtocol.TNF_WELL_KNOWN,
            byteArrayOf('B'.code.toByte()),
            byteArrayOf(9)
        )

        assertEquals(ProtocolMode.V2, v2?.protocolMode)
        assertEquals(ProtocolMode.LEGACY_V1, legacy?.protocolMode)
        assertNull(
            EnakNfcProtocol.decodeBuilding(
                EnakNfcProtocol.TNF_EXTERNAL_TYPE,
                "cz.enak:building".toByteArray(),
                byteArrayOf(1, 9)
            )
        )
        assertNull(
            EnakNfcProtocol.decodeBuilding(
                EnakNfcProtocol.TNF_EXTERNAL_TYPE,
                "cz.enak:building".toByteArray(),
                byteArrayOf(2, 18)
            )
        )
    }

    @Test
    fun tagDecoderRecognizesAdministrativeCards() {
        val reset = EnakNfcProtocol.decodeTag(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:cmd".toByteArray(),
            byteArrayOf(1, 1)
        )
        val debug = EnakNfcProtocol.decodeTag(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:cmd".toByteArray(),
            byteArrayOf(2, 2)
        )
        val bulldozer = EnakNfcProtocol.decodeTag(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:cmd".toByteArray(),
            byteArrayOf(2, 3)
        )

        assertEquals(EnakTagKind.BUILDING_RESET, reset?.kind)
        assertEquals(ProtocolMode.LEGACY_V1, reset?.protocolMode)
        assertEquals(EnakTagKind.DEBUG, debug?.kind)
        assertEquals(EnakTagKind.BULLDOZER, bulldozer?.kind)
    }

    @Test
    fun tagDecoderRecognizesWifiAndUnknownCommands() {
        val wifi = EnakNfcProtocol.decodeTag(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:wifi".toByteArray(),
            byteArrayOf(2, 1, 0)
        )
        val unknownCommand = EnakNfcProtocol.decodeTag(
            EnakNfcProtocol.TNF_EXTERNAL_TYPE,
            "cz.enak:cmd".toByteArray(),
            byteArrayOf(2, 99)
        )

        assertEquals(EnakTagKind.WIFI, wifi?.kind)
        assertEquals(EnakTagKind.UNKNOWN_COMMAND, unknownCommand?.kind)
        assertNull(
            EnakNfcProtocol.decodeTag(
                EnakNfcProtocol.TNF_EXTERNAL_TYPE,
                "cz.enak:cmd".toByteArray(),
                byteArrayOf(9, 1)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun writerRejectsBuildingTypesAboveSeventeen() {
        EnakNfcProtocol.createBuildingRecord(18, ProtocolMode.V2)
    }
}
