package moe.chenxy.oppopods.pods

import moe.chenxy.oppopods.config.ConfigManager

private val ADAPTIVE_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco Free4",
    "moto buds",
    "Moto Buds",
)

// Moto Buds+ (XT2441-1 / "flute") is the only Moto variant with spatial audio + head tracking;
// standard Moto Buds and Moto Buds Bass do not expose it. Name matching cannot rely on
// "Moto Buds+" because it normalizes to the same token as "Moto Buds", so the codename and
// model id are used and users can still force the capability from the module settings.
private val SPATIAL_AUDIO_SUPPORTED_DEVICES = arrayOf(
    "flute",
    "XT2441",
)

private val SPATIAL_SOUND_SWITCH_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco Free4",
    "OPPO Enco Air5",
)

private val LEGACY_ANC_DEVICES: Array<String> = emptyArray()

data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
)

/**
 * Resolves device capabilities.
 *
 * When the model id from opcode 0x0000 is known, the feature table in [DeviceModelRegistry]
 * drives the result; otherwise the advertised Bluetooth name is used as a fallback.
 * Every capability can still be forced from the module settings.
 */
fun detectDeviceCapabilities(
    deviceName: String,
    adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    ancImplementationOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    modelId: String? = null,
): DeviceCapabilities {
    val model = DeviceModelRegistry.find(modelId = modelId, deviceName = deviceName)

    val adaptiveAuto = model?.supports(DeviceModelRegistry.FEATURE_ADAPTIVE_HEARING)
        ?: isAdaptiveSupportedByName(deviceName)
    val spatialAudioAuto = model?.supports(DeviceModelRegistry.FEATURE_SPATIAL_AUDIO)
        ?: isSpatialAudioSupportedByName(deviceName)
    val spatialSoundSwitchAuto = if (model != null) {
        model.supports(DeviceModelRegistry.FEATURE_SPATIAL_AUDIO)
    } else {
        isSpatialSoundSwitchSupportedByName(deviceName)
    }
    // Known models are not "legacy" ANC devices; only the name fallback can report legacy.
    val legacyAncAuto = if (model != null) false else isLegacyAncDeviceByName(deviceName)

    return DeviceCapabilities(
        adaptiveSupported = resolveCapability(
            override = adaptiveOverride,
            autoDetected = adaptiveAuto,
        ),
        spatialAudioSupported = resolveCapability(
            override = spatialAudioOverride,
            autoDetected = spatialAudioAuto,
        ),
        spatialSoundSwitchSupported = resolveCapability(
            override = spatialSoundSwitchOverride,
            autoDetected = spatialSoundSwitchAuto,
        ),
        ancImplementation = resolveAncImplementation(
            override = ancImplementationOverride,
            autoDetected = legacyAncAuto,
        )
    )
}

fun isAdaptiveSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, ADAPTIVE_SUPPORTED_DEVICES)
}

fun isSpatialAudioSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, SPATIAL_AUDIO_SUPPORTED_DEVICES)
}

fun isSpatialSoundSwitchSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, SPATIAL_SOUND_SWITCH_SUPPORTED_DEVICES)
}

fun isLegacyAncDeviceByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, LEGACY_ANC_DEVICES)
}

private fun resolveCapability(override: Int, autoDetected: Boolean): Boolean {
    return when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> true
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> false
        else -> autoDetected
    }
}

private fun resolveAncImplementation(override: Int, autoDetected: Boolean): AncImplementation {
    return when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> AncImplementation.COMPATIBLE
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> AncImplementation.STANDARD
        else -> if (autoDetected) AncImplementation.COMPATIBLE else AncImplementation.STANDARD
    }
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}

private fun isDeviceInCapabilityList(deviceName: String, supportedDevices: Array<String>): Boolean {
    val normalizedName = normalizeDeviceName(deviceName)
    return supportedDevices.any { supportedDevice ->
        val normalizedSupportedDevice = normalizeDeviceName(supportedDevice)
        normalizedSupportedDevice in normalizedName || normalizedName in normalizedSupportedDevice
    }
}
