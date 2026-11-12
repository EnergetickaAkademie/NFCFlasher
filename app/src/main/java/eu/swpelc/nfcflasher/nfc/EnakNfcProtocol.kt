package eu.swpelc.nfcflasher.nfc

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

enum class ProtocolMode(val versionByte: Byte, val displayName: String) {
    V2(2, "Protocol v2"),
    LEGACY_V1(1, "Legacy v1")
}

data class NfcRecordSpec(
    val tnf: Short,
    val type: ByteArray,
    val payload: ByteArray
)

data class DecodedBuilding(
    val buildingType: Byte,
    val protocolMode: ProtocolMode
)

enum class EnakTagKind(val displayName: String) {
    BUILDING("Building"),
    BUILDING_RESET("Building reset card"),
    DEBUG("Debug card"),
    BULLDOZER("Bulldozer card"),
    WIFI("Wi-Fi provisioning tag"),
    UNKNOWN_COMMAND("Unknown command card")
}

data class DecodedEnakTag(
    val kind: EnakTagKind,
    val protocolMode: ProtocolMode,
    val payload: ByteArray,
    val buildingType: Byte? = null
)

object EnakNfcProtocol {
    const val TNF_WELL_KNOWN: Short = 0x01
    const val TNF_EXTERNAL_TYPE: Short = 0x04
    const val MAX_BUILDING_TYPE = 17

    private const val BUILDING_RECORD_TYPE = "cz.enak:building"
    private const val COMMAND_RECORD_TYPE = "cz.enak:cmd"
    private const val WIFI_RECORD_TYPE = "cz.enak:wifi"
    private const val LEGACY_BUILDING_RECORD_TYPE = "B"
    private const val RESET_BUILDINGS: Byte = 1
    private const val ENTER_DEBUG: Byte = 2
    private const val BULLDOZER: Byte = 3
    private const val SECURITY_OPEN = 0
    private const val SECURITY_WPA = 1

    fun createBuildingRecord(buildingType: Byte, mode: ProtocolMode): NfcRecordSpec {
        requireValidBuildingType(buildingType)
        return when (mode) {
            ProtocolMode.V2 -> externalRecord(
                BUILDING_RECORD_TYPE,
                byteArrayOf(mode.versionByte, buildingType)
            )
            ProtocolMode.LEGACY_V1 -> NfcRecordSpec(
                TNF_WELL_KNOWN,
                LEGACY_BUILDING_RECORD_TYPE.toByteArray(Charsets.US_ASCII),
                byteArrayOf(buildingType)
            )
        }
    }

    fun createResetRecord(mode: ProtocolMode): NfcRecordSpec = externalRecord(
        COMMAND_RECORD_TYPE,
        byteArrayOf(mode.versionByte, RESET_BUILDINGS)
    )

    /** Debug mode is supported only by the current protocol-v2 mainboard. */
    fun createDebugRecord(): NfcRecordSpec = externalRecord(
        COMMAND_RECORD_TYPE,
        byteArrayOf(ProtocolMode.V2.versionByte, ENTER_DEBUG)
    )

    /** Bulldozer mode arms the mainboard to remove presented buildings briefly. */
    fun createBulldozerRecord(): NfcRecordSpec = externalRecord(
        COMMAND_RECORD_TYPE,
        byteArrayOf(ProtocolMode.V2.versionByte, BULLDOZER)
    )

    fun createWifiRecord(
        ssidText: String,
        passwordText: String,
        isOpen: Boolean,
        mode: ProtocolMode
    ): NfcRecordSpec {
        val ssid = ssidText.toByteArray(Charsets.UTF_8)
        val password = if (isOpen) ByteArray(0) else passwordText.toByteArray(Charsets.UTF_8)

        require(ssid.size in 1..32) { "SSID must contain 1–32 UTF-8 bytes" }
        require(isOpen || password.size in 8..63) {
            "Password must contain 8–63 UTF-8 bytes"
        }

        val body = ByteArrayOutputStream().apply {
            write(mode.versionByte.toInt())
            write(if (isOpen) SECURITY_OPEN else SECURITY_WPA)
            write(ssid.size)
            write(ssid)
            write(password.size)
            write(password)
        }.toByteArray()

        val checksum = CRC32().apply { update(body) }.value
        val payload = ByteArrayOutputStream().apply {
            write(body)
            write((checksum ushr 24).toInt() and 0xff)
            write((checksum ushr 16).toInt() and 0xff)
            write((checksum ushr 8).toInt() and 0xff)
            write(checksum.toInt() and 0xff)
        }.toByteArray()

        return externalRecord(WIFI_RECORD_TYPE, payload)
    }

    fun decodeBuilding(tnf: Short, type: ByteArray, payload: ByteArray): DecodedBuilding? {
        if (tnf == TNF_EXTERNAL_TYPE &&
            type.contentEquals(BUILDING_RECORD_TYPE.toByteArray(Charsets.US_ASCII)) &&
            payload.size == 2 &&
            payload[0] == ProtocolMode.V2.versionByte &&
            isValidBuildingType(payload[1])
        ) {
            return DecodedBuilding(payload[1], ProtocolMode.V2)
        }

        if (tnf == TNF_WELL_KNOWN &&
            type.contentEquals(LEGACY_BUILDING_RECORD_TYPE.toByteArray(Charsets.US_ASCII)) &&
            payload.size == 1 &&
            isValidBuildingType(payload[0])
        ) {
            return DecodedBuilding(payload[0], ProtocolMode.LEGACY_V1)
        }

        return null
    }

    /** Decodes every ENAK record type understood by the flasher, not just buildings. */
    fun decodeTag(tnf: Short, type: ByteArray, payload: ByteArray): DecodedEnakTag? {
        decodeBuilding(tnf, type, payload)?.let { building ->
            return DecodedEnakTag(
                EnakTagKind.BUILDING,
                building.protocolMode,
                payload.copyOf(),
                building.buildingType
            )
        }

        if (tnf != TNF_EXTERNAL_TYPE) return null

        val protocolMode = payload.firstOrNull()?.let(::protocolModeForVersion) ?: return null
        if (type.contentEquals(COMMAND_RECORD_TYPE.toByteArray(Charsets.US_ASCII)) &&
            payload.size == 2
        ) {
            val kind = when (payload[1]) {
                RESET_BUILDINGS -> EnakTagKind.BUILDING_RESET
                ENTER_DEBUG -> EnakTagKind.DEBUG
                BULLDOZER -> EnakTagKind.BULLDOZER
                else -> EnakTagKind.UNKNOWN_COMMAND
            }
            return DecodedEnakTag(kind, protocolMode, payload.copyOf())
        }

        if (type.contentEquals(WIFI_RECORD_TYPE.toByteArray(Charsets.US_ASCII))) {
            return DecodedEnakTag(
                EnakTagKind.WIFI,
                protocolMode,
                payload.copyOf()
            )
        }

        return null
    }

    private fun externalRecord(type: String, payload: ByteArray) = NfcRecordSpec(
        TNF_EXTERNAL_TYPE,
        type.toByteArray(Charsets.US_ASCII),
        payload
    )

    private fun requireValidBuildingType(buildingType: Byte) {
        require(isValidBuildingType(buildingType)) {
            "Building type must be between 0 and $MAX_BUILDING_TYPE"
        }
    }

    private fun isValidBuildingType(buildingType: Byte): Boolean =
        buildingType.toUByte().toInt() in 0..MAX_BUILDING_TYPE

    private fun protocolModeForVersion(version: Byte): ProtocolMode? =
        ProtocolMode.entries.firstOrNull { it.versionByte == version }
}
