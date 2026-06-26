package moe.chenxy.oppopods.pods

/**
 * MotoBuds (guitar/XT2443-1) RFCOMM protocol packet definitions.
 *
 * Frame format:
 *   HEAD(4B "HEAD") + total_len(2B LE) + PDU(8+N B) + CRC32(4B LE) + TAIL(4B "TAIL")
 *
 * PDU format (8-byte header):
 *   opcode(2B BE) | type(1B) | result(1B) | payload_len(2B LE) | seq(2B LE) | payload(N B)
 *
 * Type byte:
 *   0x80 = Command Ack (sent by app)
 *   0xA0 = Response Ack (earphone reply)
 *   0x40 = Notification Ack (earphone push)
 */
object MotoBudsPackets {

    private val HEAD = byteArrayOf(0x48, 0x45, 0x41, 0x44) // "HEAD"
    private val TAIL = byteArrayOf(0x54, 0x41, 0x49, 0x4C) // "TAIL"

    const val TYPE_COMMAND_ACK: Byte = 0x80.toByte()
    const val TYPE_RESPONSE_ACK: Byte = 0xA0.toByte()
    const val TYPE_NOTIFICATION_ACK: Byte = 0x40.toByte()

    /**
     * Build a complete protocol packet.
     *
     * @param opcode Command opcode (e.g., [Cmd.GET_BATTERY_LEVEL])
     * @param type Packet type (default: [TYPE_COMMAND_ACK])
     * @param result Response result code (default: 0x00 for success)
     * @param seq Sequence number for request-response matching
     * @param payload Command payload data
     * @return Complete packet bytes ready to send
     */
    fun buildPacket(opcode: Int, type: Byte = TYPE_COMMAND_ACK, result: Byte = 0x00, seq: Int = 0, payload: ByteArray = byteArrayOf()): ByteArray {
        val pduLen = 8 + payload.size
        val totalLen = pduLen

        val pdu = ByteArray(pduLen)
        // opcode (2B BE)
        pdu[0] = ((opcode shr 8) and 0xFF).toByte()
        pdu[1] = (opcode and 0xFF).toByte()
        // type
        pdu[2] = type
        // result
        pdu[3] = result
        // payload_len (2B LE)
        pdu[4] = (payload.size and 0xFF).toByte()
        pdu[5] = ((payload.size shr 8) and 0xFF).toByte()
        // seq (2B LE)
        pdu[6] = (seq and 0xFF).toByte()
        pdu[7] = ((seq shr 8) and 0xFF).toByte()
        // payload
        payload.copyInto(pdu, 8)

        // Full frame: HEAD + total_len + PDU + CRC32 + TAIL
        val frame = ByteArray(4 + 2 + pduLen + 4 + 4)
        HEAD.copyInto(frame, 0)
        frame[4] = (totalLen and 0xFF).toByte()
        frame[5] = ((totalLen shr 8) and 0xFF).toByte()
        pdu.copyInto(frame, 6)

        // CRC32 over HEAD to end of PDU
        val crcData = frame.copyOfRange(0, 6 + pduLen)
        val crc = crc32(crcData)
        frame[6 + pduLen] = (crc and 0xFF).toByte()
        frame[6 + pduLen + 1] = ((crc shr 8) and 0xFF).toByte()
        frame[6 + pduLen + 2] = ((crc shr 16) and 0xFF).toByte()
        frame[6 + pduLen + 3] = ((crc shr 24) and 0xFF).toByte()

        TAIL.copyInto(frame, 6 + pduLen + 4)
        return frame
    }

    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (j in 0 until 8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc xor 0xFFFFFFFF.toInt()
    }
}

/** ANC mode values for MotoBuds (used in SET_ANC commands). */
object MotoBudsAncMode {
    const val OFF = 0x00
    const val ADAPTIVE = 0x01
    const val TRANSPARENCY = 0x02
    const val NOISE_CANCELLATION = 0x03
    const val NC_STRONG = 0x01  // sub_mode for strong NC
    const val NC_ADAPTIVE = 0x01 // sub_mode for adaptive NC
}

/** Noise control mode enum for UI. */
enum class NoiseControlMode {
    OFF,
    NOISE_CANCELLATION,
    ADAPTIVE,
    TRANSPARENCY
}

fun NoiseControlMode.isNoiseCancellation(): Boolean {
    return this == NoiseControlMode.NOISE_CANCELLATION
}

/** Battery component index in response payload. */
object BatteryComponent {
    const val LEFT = 1
    const val RIGHT = 2
    const val CASE = 3
}

/** Wearing-detection component/status values. */
object WearComponent {
    const val LEFT = 1
    const val RIGHT = 2
    const val CASE = 3
}

enum class WearState(val value: Int) {
    DISCONNECTED(0x00),
    IN_CASE(0x04),
    REMOVED(0x05),
    WEARING(0x07);

    companion object {
        fun fromValue(value: Int): WearState? = entries.firstOrNull { it.value == value }
    }
}

data class WearStatus(
    val left: WearState? = null,
    val right: WearState? = null,
    val case: WearState? = null
)

/** Feature IDs used by the switch-feature command/query. */
object GameModeFeature {
    const val LOW_LATENCY = 0x06
    const val DUAL_DEVICE_CONNECTION = 0x11
    const val MAIN = 0x28
}

/** Spatial audio mode values. */
object SpatialAudioMode {
    const val OFF = 0x00
    const val FIXED = 0x01
    const val HEAD_TRACKING = 0x02
}

/** EQ preset IDs for MotoBuds. */
object EqPreset {
    const val AUTHENTIC = 0
    const val DETAIL = 1
    const val VOCAL = 2
    const val BASS = 3
    const val CUSTOM = 0x3F
    val ALL: List<Int> = listOf(AUTHENTIC, DETAIL, VOCAL, BASS, CUSTOM)
}

/**
 * MotoBuds protocol opcodes.
 *
 * Opcodes are organized by functionality:
 * - Device info (0x000-0x010): Basic device information queries
 * - Toggle Config (0x100-0x106): Gesture and toggle configuration
 * - ANC (0x200-0x20A): Active Noise Cancellation control
 * - EQ (0x300-0x31E): Equalizer and audio enhancement
 * - Functions (0x400-0x410): Device functions like find my device
 * - Advanced (0x500-0x702): OTA, logging, and advanced features
 */
object Cmd {
    // Device info (0x000-0x010)
    const val GET_PROFILE_VERSION = 0x000
    const val GET_SUPPORT_FEATURES = 0x001
    const val GET_SUPPORT_CONFIGURATIONS = 0x002
    const val GET_DEVICE_NAME = 0x003
    const val GET_HARDWARE_INFO = 0x004
    const val GET_BATTERY_LEVEL = 0x005
    const val GET_PRIMARY_EARBUD = 0x006
    const val SET_DEVICE_NAME = 0x007
    const val PRIMARY_EARBUD_CHANGED = 0x008
    const val BATTERY_LEVEL_CHANGED = 0x009
    const val GET_EARBUDS_COLOR = 0x00A
    const val LIST_SUPPORT_INFO_AND_CONFIGS = 0x00B
    const val GET_CHANNEL_ID = 0x00C
    const val SET_SUPPORT_CONFIGURATIONS = 0x00D
    const val SUPPORT_CONFIGURATIONS_CHANGED = 0x00E

    // Toggle Config (0x100-0x106)
    const val GET_TOGGLE_CONFIGS = 0x100
    const val GET_TOGGLE_CONFIG = 0x101
    const val SET_TOGGLE_CONFIG = 0x102
    const val GET_DEMO_STATE = 0x103
    const val SET_DEMO_STATE = 0x104
    const val TOGGLE_CONFIG_STATUS_CHANGED = 0x105
    const val DEMO_STATE_CHANGED = 0x106

    // ANC (0x200-0x20A)
    const val GET_CURRENT_ANC_MODE = 0x200
    const val SET_CURRENT_ANC_MODE = 0x201
    const val GET_ADAPTATION_STATUS = 0x202
    const val SET_ADAPTATION_STATUS = 0x203
    const val ANC_MODE_CHANGED = 0x204
    const val ADAPTATION_STATUS_CHANGED = 0x205
    const val SET_EAR_CANAL_STATE = 0x206
    const val EAR_CANAL_STATUS_INDICATION = 0x207
    const val GET_DANGER_DETECTION_STATE = 0x208
    const val SET_DANGER_DETECTION_STATE = 0x209
    const val DANGER_DETECTION_STATE_CHANGED = 0x20A

    // EQ (0x300-0x31E)
    const val GET_EQ_STATE = 0x300
    const val GET_AVAILABLE_EQ_SETS = 0x301
    const val GET_EQ_SET = 0x302
    const val SET_EQ_SET = 0x303
    const val EQ_STATE_CHANGED = 0x307
    const val EQ_SET_CHANGED = 0x308
    const val EQ_USER_BANDS_CHANGED = 0x309
    const val GET_SPATIAL_AUDIO_STATE = 0x30A
    const val SET_SPATIAL_AUDIO_STATE = 0x30B
    const val GET_HI_RES_MODE = 0x30C
    const val SET_HI_RES_MODE = 0x30D
    const val GET_GAME_MODE = 0x30E
    const val SET_GAME_MODE = 0x30F
    const val SPATIAL_AUDIO_STATE_CHANGED = 0x310
    const val HI_RES_MODE_STATE_CHANGED = 0x311
    const val GAME_MODE_STATE_CHANGED = 0x312
    const val GET_VOLUME_BOOST_STATE = 0x313
    const val SET_VOLUME_BOOST_STATE = 0x314
    const val VOLUME_BOOST_STATE_CHANGED = 0x315
    const val GET_BASS_ENHANCEMENT_STATE = 0x31C
    const val SET_BASS_ENHANCEMENT_STATE = 0x31D
    const val BASS_ENHANCEMENT_STATE_CHANGED = 0x31E

    // Functions (0x400-0x410)
    const val SET_FIT_STATE = 0x400
    const val FIT_STATUS_CHANGED = 0x401
    const val GET_IN_EAR_DETECTION_STATE = 0x402
    const val SET_IN_EAR_DETECTION_STATE = 0x403
    const val IN_EAR_STATUS_CHANGED = 0x404
    const val FIND_MY_DEVICE = 0x405
    const val GET_DUAL_CONNECTION_STATE = 0x406
    const val SET_DUAL_CONNECTION_STATE = 0x407
    const val IN_CASE_STATUS_INDICATION = 0x40C
    const val IN_EAR_DETECTION_STATE_NOTIFICATION = 0x40D
    const val FIND_MY_DEVICE_STATE_NOTIFICATION = 0x40E
    const val DUAL_CONNECTION_STATE_CHANGED = 0x40F
    const val LE_AUDIO_STATE_CHANGED = 0x410

    // Advanced (0x500-0x702)
    const val SET_CURRENT_TIME = 0x500
    const val GET_FMD_CONFIG_VALUES = 0x600
    const val SET_FMD_CONFIG_VALUES = 0x601
}

/** Pre-built packets for MotoBuds. */
object Enums {
    /** Get battery level (opcode 0x005) */
    val QUERY_BATTERY: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_BATTERY_LEVEL
    )

    /** Get ANC mode (opcode 0x200) */
    val QUERY_ANC: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_CURRENT_ANC_MODE
    )

    /** Get EQ state (opcode 0x300) */
    val QUERY_EQ: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_EQ_STATE
    )

    /** Get available EQ sets (opcode 0x301) */
    val QUERY_EQ_SETS: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_AVAILABLE_EQ_SETS
    )

    /** Get game mode (opcode 0x30E) */
    val QUERY_GAME_MODE: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_GAME_MODE
    )

    /** Get spatial audio (opcode 0x30A) */
    val QUERY_SPATIAL_AUDIO: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_SPATIAL_AUDIO_STATE
    )

    /** Get volume boost (opcode 0x313) */
    val QUERY_VOLUME_BOOST: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_VOLUME_BOOST_STATE
    )

    /** Get dual connection (opcode 0x406) */
    val QUERY_DUAL_CONNECTION: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_DUAL_CONNECTION_STATE
    )

    /** Get in-ear detection (opcode 0x402) */
    val QUERY_IN_EAR: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_IN_EAR_DETECTION_STATE
    )

    /** Get LE audio (opcode 0x40A) - query LE audio state */
    val QUERY_LE_AUDIO: ByteArray = MotoBudsPackets.buildPacket(
        opcode = 0x40A
    )

    /** Get device name (opcode 0x003) */
    val QUERY_DEVICE_NAME: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_DEVICE_NAME
    )

    /** Get hardware info (opcode 0x004) */
    val QUERY_HARDWARE_INFO: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_HARDWARE_INFO
    )

    /** Get primary earbud (opcode 0x006) */
    val QUERY_PRIMARY_EARBUD: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_PRIMARY_EARBUD
    )

    /** Get channel id (opcode 0x00C) */
    val QUERY_CHANNEL_ID: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_CHANNEL_ID
    )

    /** Get earbuds color (opcode 0x00A) */
    val QUERY_EARBUDS_COLOR: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_EARBUDS_COLOR
    )

    /** Get support configs (opcode 0x002) */
    val QUERY_SUPPORT_CONFIGS: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_SUPPORT_CONFIGURATIONS
    )

    /** Get toggle configs (opcode 0x100) */
    val QUERY_TOGGLE_CONFIGS: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_TOGGLE_CONFIGS
    )

    /** Get adaptation status (opcode 0x202) */
    val QUERY_ADAPTATION_STATUS: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_ADAPTATION_STATUS
    )

    /** Get profile version (opcode 0x000) */
    val QUERY_PROFILE_VERSION: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_PROFILE_VERSION
    )

    /** Get support features (opcode 0x001) */
    val QUERY_SUPPORT_FEATURES: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_SUPPORT_FEATURES
    )

    /** Get bass enhancement (opcode 0x31C) */
    val QUERY_BASS_ENHANCEMENT: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_BASS_ENHANCEMENT_STATE
    )

    // Set commands

    /** Set ANC mode: OFF (mode=0x00, sub_mode=0x00) */
    val ANC_OFF: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_CURRENT_ANC_MODE,
        payload = byteArrayOf(0x00, 0x00)
    )

    /** Set ANC mode: Adaptive (mode=0x01, sub_mode=0x01) */
    val ANC_ADAPTIVE: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_CURRENT_ANC_MODE,
        payload = byteArrayOf(0x01, 0x01)
    )

    /** Set ANC mode: Transparency (mode=0x02, sub_mode=0x00) */
    val ANC_TRANSPARENCY: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_CURRENT_ANC_MODE,
        payload = byteArrayOf(0x02, 0x00)
    )

    /** Set ANC mode: Noise Cancellation (mode=0x01, sub_mode=0x02) */
    val ANC_NOISE_CANCEL: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_CURRENT_ANC_MODE,
        payload = byteArrayOf(0x01, 0x02)
    )

    /** Enable game mode (opcode 0x30F) */
    val GAME_MODE_ON: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_GAME_MODE,
        payload = byteArrayOf(0x01)
    )

    /** Disable game mode (opcode 0x30F) */
    val GAME_MODE_OFF: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_GAME_MODE,
        payload = byteArrayOf(0x00)
    )

    /** Enable volume boost (opcode 0x314) */
    val VOLUME_BOOST_ON: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_VOLUME_BOOST_STATE,
        payload = byteArrayOf(0x01)
    )

    /** Disable volume boost (opcode 0x314) */
    val VOLUME_BOOST_OFF: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_VOLUME_BOOST_STATE,
        payload = byteArrayOf(0x00)
    )

    /** Set spatial audio (opcode 0x30B) */
    fun spatialAudioPacket(mode: Int): ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_SPATIAL_AUDIO_STATE,
        payload = byteArrayOf(
            mode.coerceIn(SpatialAudioMode.OFF, SpatialAudioMode.HEAD_TRACKING).toByte(),
            0x00 // head_tracking byte
        )
    )

    /** Set EQ preset (opcode 0x303) */
    fun eqPresetPacket(presetId: Int): ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_EQ_SET,
        payload = byteArrayOf(presetId.toByte())
    )

    /** Set spatial sound switch (not supported on MotoBuds, stub) */
    fun spatialSoundSwitchPacket(enabled: Boolean): ByteArray = if (enabled) {
        spatialAudioPacket(SpatialAudioMode.FIXED)
    } else {
        spatialAudioPacket(SpatialAudioMode.OFF)
    }

    /** Set dual connection (opcode 0x407) */
    fun dualDeviceConnectionPacket(enabled: Boolean): ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.SET_DUAL_CONNECTION_STATE,
        payload = byteArrayOf(0x01, if (enabled) 0x01 else 0x00)
    )

    fun gameModePackets(enabled: Boolean, implementation: GameModeImplementation): List<ByteArray> {
        return when (implementation) {
            GameModeImplementation.STANDARD -> listOf(if (enabled) GAME_MODE_ON else GAME_MODE_OFF)
            GameModeImplementation.COMPATIBLE -> if (enabled) {
                listOf(GAME_MODE_ON, GAME_MODE_ON)
            } else {
                listOf(GAME_MODE_OFF, GAME_MODE_OFF)
            }
        }
    }

    /** Query status: send all status queries */
    val QUERY_STATUS: ByteArray = MotoBudsPackets.buildPacket(
        opcode = Cmd.GET_BATTERY_LEVEL
    )
}

/**
 * Parser for MotoBuds battery response packets.
 *
 * Response: opcode 0x005 (GET_BATTERY_LEVEL response)
 * Payload: [left_battery, right_battery, case_battery]
 * Values: 0x00-0x64 (0-100%), 0xFF = unknown
 */
object BatteryParser {

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean
    )

    data class BatteryResult(
        val left: BatteryInfo?,
        val right: BatteryInfo?,
        val case: BatteryInfo?
    )

    /**
     * Parse a raw packet for battery response.
     * MotoBuds frame: HEAD(4) + len(2) + PDU(8+N) + CRC(4) + TAIL(4)
     * PDU: opcode(2 BE) + type(1) + result(1) + payload_len(2 LE) + seq(2 LE) + payload(N)
     *
     * Battery byte format: bit 7 = charging flag, bits 0-6 = level (0-100)
     * 0xFF = unknown/not present
     */
    fun parse(data: ByteArray): BatteryResult? {
        if (data.size < 18) return null // minimum frame size
        if (data[0] != 0x48.toByte() || data[1] != 0x45.toByte()) return null // "HE"

        // Parse PDU
        val pduStart = 6 // after HEAD(4) + len(2)
        if (data.size < pduStart + 8) return null

        val opcode = ((data[pduStart].toInt() and 0xFF) shl 8) or (data[pduStart + 1].toInt() and 0xFF)
        if (opcode != Cmd.GET_BATTERY_LEVEL) return null

        val type = data[pduStart + 2]
        if (type != MotoBudsPackets.TYPE_RESPONSE_ACK) return null

        val payloadLen = (data[pduStart + 4].toInt() and 0xFF) or ((data[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8

        if (data.size < payloadStart + payloadLen || payloadLen < 3) return null

        val leftRaw = data[payloadStart].toInt() and 0xFF
        val rightRaw = data[payloadStart + 1].toInt() and 0xFF
        val caseRaw = data[payloadStart + 2].toInt() and 0xFF

        val left = parseBatteryByte(leftRaw)
        val right = parseBatteryByte(rightRaw)
        val case = parseBatteryByte(caseRaw)

        return BatteryResult(left, right, case)
    }

    /**
     * Parse battery notification (opcode 0x009).
     */
    fun parseNotification(data: ByteArray): BatteryResult? {
        if (data.size < 18) return null
        if (data[0] != 0x48.toByte() || data[1] != 0x45.toByte()) return null

        val pduStart = 6
        if (data.size < pduStart + 8) return null

        val opcode = ((data[pduStart].toInt() and 0xFF) shl 8) or (data[pduStart + 1].toInt() and 0xFF)
        if (opcode != Cmd.BATTERY_LEVEL_CHANGED) return null

        val payloadLen = (data[pduStart + 4].toInt() and 0xFF) or ((data[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8

        if (data.size < payloadStart + payloadLen || payloadLen < 3) return null

        val leftRaw = data[payloadStart].toInt() and 0xFF
        val rightRaw = data[payloadStart + 1].toInt() and 0xFF
        val caseRaw = data[payloadStart + 2].toInt() and 0xFF

        val left = parseBatteryByte(leftRaw)
        val right = parseBatteryByte(rightRaw)
        val case = parseBatteryByte(caseRaw)

        return BatteryResult(left, right, case)
    }

    /**
     * Parse a single battery byte.
     * Format: bit 7 = charging flag, bits 0-6 = level (0-100)
     * 0xFF = unknown
     */
    fun parseBatteryByte(raw: Int): BatteryInfo? {
        if (raw == 0xFF) return null
        val isCharging = (raw and 0x80) != 0
        val level = (raw and 0x7F).coerceIn(0, 100)
        return BatteryInfo(level, isCharging)
    }
}

/** Parser for spatial audio. */
object SpatialAudioParser {
    fun parseModeNotify(packet: ByteArray): Int? {
        if (packet.size < 18) return null
        if (packet[0] != 0x48.toByte() || packet[1] != 0x45.toByte()) return null

        val pduStart = 6
        val opcode = ((packet[pduStart].toInt() and 0xFF) shl 8) or (packet[pduStart + 1].toInt() and 0xFF)
        if (opcode != Cmd.SPATIAL_AUDIO_STATE_CHANGED) return null

        val payloadLen = (packet[pduStart + 4].toInt() and 0xFF) or ((packet[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8
        if (packet.size < payloadStart + payloadLen || payloadLen < 1) return null

        val mode = packet[payloadStart].toInt() and 0xFF
        return mode.takeIf { it in SpatialAudioMode.OFF..SpatialAudioMode.HEAD_TRACKING }
    }

    fun parseSetResponseStatus(packet: ByteArray): Int? {
        if (packet.size < 18) return null
        if (packet[0] != 0x48.toByte() || packet[1] != 0x45.toByte()) return null

        val pduStart = 6
        val opcode = ((packet[pduStart].toInt() and 0xFF) shl 8) or (packet[pduStart + 1].toInt() and 0xFF)
        if (opcode != Cmd.SET_SPATIAL_AUDIO_STATE) return null

        val payloadLen = (packet[pduStart + 4].toInt() and 0xFF) or ((packet[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8
        if (packet.size < payloadStart + payloadLen || payloadLen < 1) return null
        return packet[payloadStart].toInt() and 0xFF
    }

    fun parseSpatialSoundSwitchSetResponse(packet: ByteArray): Boolean? {
        return null
    }
}

/** Parser for EQ preset. */
object EqPresetParser {
    fun parse(data: ByteArray): Int? {
        if (data.size < 18) return null
        if (data[0] != 0x48.toByte() || data[1] != 0x45.toByte()) return null

        val pduStart = 6
        val opcode = ((data[pduStart].toInt() and 0xFF) shl 8) or (data[pduStart + 1].toInt() and 0xFF)

        val payloadLen = (data[pduStart + 4].toInt() and 0xFF) or ((data[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8
        if (data.size < payloadStart + payloadLen || payloadLen < 1) return null

        return when (opcode) {
            Cmd.EQ_SET_CHANGED, Cmd.GET_EQ_SET -> {
                (data[payloadStart].toInt() and 0xFF).takeIf { it in EqPreset.ALL }
            }
            else -> null
        }
    }
}

/** Parser for ANC mode. */
object AncModeParser {

    fun parse(data: ByteArray, implementation: AncImplementation = AncImplementation.STANDARD): NoiseControlMode? {
        if (data.size < 18) return null
        if (data[0] != 0x48.toByte() || data[1] != 0x45.toByte()) return null

        val pduStart = 6
        val opcode = ((data[pduStart].toInt() and 0xFF) shl 8) or (data[pduStart + 1].toInt() and 0xFF)

        if (opcode != Cmd.GET_CURRENT_ANC_MODE && opcode != Cmd.ANC_MODE_CHANGED) return null

        val payloadLen = (data[pduStart + 4].toInt() and 0xFF) or ((data[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8
        if (data.size < payloadStart + payloadLen || payloadLen < 2) return null

        val mode = data[payloadStart].toInt() and 0xFF
        val subMode = data[payloadStart + 1].toInt() and 0xFF

        // According to decompiled APK:
        // mode=0 → OFF
        // mode=1, sub=1 → Adaptive
        // mode=1, sub=2 → NC
        // mode=2 → Transparency
        return when (mode) {
            0x00 -> NoiseControlMode.OFF
            0x01 -> when (subMode) {
                0x01 -> NoiseControlMode.ADAPTIVE
                0x02 -> NoiseControlMode.NOISE_CANCELLATION
                else -> NoiseControlMode.NOISE_CANCELLATION
            }
            0x02 -> NoiseControlMode.TRANSPARENCY
            else -> null
        }
    }
}

/** Parser for game mode status. */
object GameModeParser {

    data class Status(
        val mainEnabled: Boolean?,
        val lowLatencyEnabled: Boolean?,
        val dualDeviceConnectionEnabled: Boolean? = null
    ) {
        fun enabledFor(implementation: GameModeImplementation): Boolean? {
            return when (implementation) {
                GameModeImplementation.STANDARD -> mainEnabled
                GameModeImplementation.COMPATIBLE -> lowLatencyEnabled ?: mainEnabled
            }
        }
    }

    fun parse(data: ByteArray, implementation: GameModeImplementation = GameModeImplementation.STANDARD): Boolean? {
        return parseStatus(data)?.enabledFor(implementation)
    }

    fun parseStatus(data: ByteArray): Status? {
        if (data.size < 18) return null
        if (data[0] != 0x48.toByte() || data[1] != 0x45.toByte()) return null

        val pduStart = 6
        val opcode = ((data[pduStart].toInt() and 0xFF) shl 8) or (data[pduStart + 1].toInt() and 0xFF)

        val payloadLen = (data[pduStart + 4].toInt() and 0xFF) or ((data[pduStart + 5].toInt() and 0xFF) shl 8)
        val payloadStart = pduStart + 8
        if (data.size < payloadStart + payloadLen || payloadLen < 1) return null

        return when (opcode) {
            Cmd.GET_GAME_MODE, Cmd.GAME_MODE_STATE_CHANGED -> {
                val enabled = (data[payloadStart].toInt() and 0xFF) == 0x01
                Status(mainEnabled = enabled, lowLatencyEnabled = enabled)
            }
            else -> null
        }
    }
}

/** Parser for switch feature set response. */
/** ANC protocol implementation variant. */
enum class AncImplementation {
    STANDARD,
    COMPATIBLE
}
