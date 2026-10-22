package moe.chenxy.oppopods.pods

/**
 * Device model registry derived from the Motorola app's `buds_features.json`
 * (reverse engineered by ThisWasAryan/MotoBudsDesktopUtility, GPL-3.0).
 *
 * The earbuds never negotiate capabilities over the protocol: the firmware reports a model id
 * (`XT####-#`) in the 0x0000 profile-version payload and the app gates every feature on this
 * static table. This is more reliable than matching the advertised Bluetooth name.
 */
object DeviceModelRegistry {
    const val FEATURE_MOTO_AI_TRIGGER = 101
    const val FEATURE_CASE_RECORDING = 102
    const val FEATURE_LIVE_TRANSLATION = 103
    const val FEATURE_ANC = 104
    const val FEATURE_ADAPTIVE_HEARING = 105
    const val FEATURE_SPATIAL_AUDIO = 106
    const val FEATURE_HEAD_TRACKING = 107
    const val FEATURE_DEFAULT_EQUALISER = 108
    const val FEATURE_HI_RES = 109
    const val FEATURE_GAME_MODE = 110
    const val FEATURE_AUTO_VOLUME = 111
    const val FEATURE_BASS_ENHANCEMENT = 112
    const val FEATURE_VOLUME_BOOST = 113
    const val FEATURE_CRYSTAL_TALK = 114
    const val FEATURE_FIND_MY_DEVICE = 115
    const val FEATURE_IN_EAR_DETECTION = 116
    const val FEATURE_FIT_TEST = 117
    const val FEATURE_DANGER_DETECTION = 118
    const val FEATURE_AUDIO_SHARING = 119
    const val FEATURE_DUAL_CONNECTION = 120
    const val FEATURE_RING_BUDS = 121

    data class ModelInfo(
        val modelId: String,
        val internalName: String,
        val marketName: String,
        val features: Set<Int>,
    ) {
        fun supports(feature: Int): Boolean = feature in features
    }

    private val FEATURE_NAMES: Map<Int, String> = mapOf(
        101 to "moto_ai_trigger",
        102 to "case_recording",
        103 to "live_translation",
        104 to "active_noise_cancellation",
        105 to "adaptive_hearing",
        106 to "spatial_audio",
        107 to "head_tracking",
        108 to "default_equaliser",
        109 to "hi_res",
        110 to "game_mode",
        111 to "auto_volume",
        112 to "bass_enhancement",
        113 to "volume_boost",
        114 to "crystal_talk",
        115 to "fmd",
        116 to "in_ear",
        117 to "fit_test",
        118 to "danger_detection",
        119 to "audio_sharing",
        120 to "dual_connection",
        121 to "ring_buds",
        122 to "fw_logging",
        123 to "fw_analytics",
    )

    private val MODELS: List<ModelInfo> = listOf(
        ModelInfo(
            modelId = "XT2545-1",
            internalName = "oboe",
            marketName = "moto buds loop",
            features = setOf(101, 102, 116, 121, 111, 110, 114, 120, 115, 122, 123),
        ),
        ModelInfo(
            modelId = "XT2641-1",
            internalName = "bass",
            marketName = "Moto Buds 2 Plus",
            features = setOf(101, 104, 118, 103, 116, 117, 121, 106, 109, 110, 112, 114, 120, 115, 122, 123),
        ),
        ModelInfo(
            modelId = "XT2643-1",
            internalName = "uke",
            marketName = "Moto Buds 2",
            features = setOf(101, 104, 116, 117, 121, 109, 110, 112, 113, 114, 120, 115, 122, 123),
        ),
        ModelInfo(
            modelId = "XT2443-1",
            internalName = "guitar",
            marketName = "Moto Buds",
            features = setOf(104, 116, 117, 121, 109, 110, 112, 113, 114, 120, 105),
        ),
        ModelInfo(
            modelId = "XT2443-1",
            internalName = "guitar25",
            marketName = "Moto Buds Bass",
            features = setOf(104, 116, 117, 121, 109, 110, 112, 113, 114, 120, 105),
        ),
        ModelInfo(
            modelId = "XT2441-1",
            internalName = "flute",
            marketName = "Moto Buds +",
            features = setOf(104, 116, 117, 121, 106, 109, 110, 112, 113, 114, 120),
        ),
    )

    fun featureName(feature: Int): String = FEATURE_NAMES[feature] ?: "feature_" + feature

    fun featureNames(features: Set<Int>): List<String> = features.sorted().map(::featureName)

    /**
     * Resolves the connected model. [modelId] (from opcode 0x0000) wins over the Bluetooth name;
     * the name only disambiguates models that share one model id (Moto Buds / Moto Buds Bass).
     */
    fun find(modelId: String? = null, deviceName: String? = null): ModelInfo? {
        val normalizedId = modelId?.trim()?.uppercase()?.takeIf { it.startsWith("XT") }
        if (normalizedId != null) {
            val candidates = MODELS.filter { it.modelId == normalizedId }
            if (candidates.size == 1) return candidates.first()
            if (candidates.isNotEmpty()) {
                return candidates.firstOrNull { matchesName(it, deviceName) } ?: candidates.first()
            }
        }
        return findByName(deviceName)
    }

    private fun findByName(deviceName: String?): ModelInfo? {
        val name = normalize(deviceName) ?: return null
        return when {
            name.contains("flute") || name.contains("xt2441") || name.contains("budsplus") ->
                MODELS.first { it.internalName == "flute" }
            name.contains("bass") -> MODELS.first { it.internalName == "guitar25" }
            name.contains("oboe") || name.contains("xt2545") -> MODELS.first { it.internalName == "oboe" }
            name.contains("uke") || name.contains("xt2643") -> MODELS.first { it.internalName == "uke" }
            name.contains("xt2641") -> MODELS.first { it.internalName == "bass" }
            name.contains("guitar") || name.contains("xt2443") || name.contains("motobuds") ->
                MODELS.first { it.internalName == "guitar" }
            else -> null
        }
    }

    private fun matchesName(info: ModelInfo, deviceName: String?): Boolean {
        val name = normalize(deviceName) ?: return false
        return when (info.internalName) {
            "guitar" -> !name.contains("bass") && (name.contains("moto") || name.contains("guitar"))
            "guitar25" -> name.contains("bass")
            else -> name.contains(info.internalName)
        }
    }

    private fun normalize(deviceName: String?): String? =
        deviceName?.lowercase()?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }
}
