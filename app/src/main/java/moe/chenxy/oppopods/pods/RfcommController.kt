package moe.chenxy.oppopods.pods

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.SystemClock
import moe.chenxy.oppopods.hook.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.xiuxiu391.motobuds.BuildConfig
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.utils.MediaControl
import moe.chenxy.oppopods.utils.SystemApisUtils
import moe.chenxy.oppopods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.oppopods.utils.BatteryNotificationHelper
import moe.chenxy.oppopods.utils.miuiStrongToast.MiuiStrongToastUtil
import moe.chenxy.oppopods.utils.miuiStrongToast.MiuiStrongToastUtil.cancelPodsNotificationByMiuiBt
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.oppopods.utils.miuiStrongToast.data.OppoPodsAction
import moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams
import java.io.IOException
import java.io.InputStream
import android.content.SharedPreferences
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("MissingPermission", "StaticFieldLeak")
object RfcommController {
    private const val TAG = "MotoBuds-RfcommController"
    private const val AUTO_RECONNECT_DELAY_MS = 120_000L
    private const val APP_UI_ACTIVE_TIMEOUT_MS = 75_000L

    // Basic Objects
    private var socket: BluetoothSocket? = null
    private var mContext: Context? = null
    lateinit var mDevice: BluetoothDevice
    private lateinit var mPrefs: SharedPreferences

    private var scanToken: MediaRouter2.ScanToken? = null
    var routes: List<MediaRoute2Info> = listOf()
    private lateinit var mediaRouter: MediaRouter2

    // Status
    private var mShowedConnectedToast = false
    private var isConnected = false
    private var lastTempBatt = 0
    lateinit var currentBatteryParams: BatteryParams
    private var currentAnc: Int = 1
    /** -1 = unknown / not in smart mode; otherwise a [NoiseControlMode].ordinal of the
     *  level smart mode is currently auto-applying (light/medium/deep). */
    private var currentSmartAncLevel: Int = -1
    private var currentGameMode: Boolean = false
    private var currentVolumeBoost: Boolean = false
    private var currentTransparencyVocalEnhancement: Boolean = false
    private var currentSpatialAudioMode: Int = SpatialAudioMode.OFF
    /** -1 = unknown; otherwise one of [EqPreset.ALL]. */
    private var currentEqPreset: Int = -1
    private var currentDualDeviceConnection: Boolean = false
    private var currentInEarLeft: Boolean = false
    private var currentInEarRight: Boolean = false
    private var currentInCase: Boolean = false
    private var autoGameModeEnabled: Boolean = false
    private var gameModeImplementation: GameModeImplementation = GameModeImplementation.STANDARD
    private var lastGameModeStatusUpdateMs: Long = 0L
    private var lastKnownCaseBattery: Int = 0
    private var lastKnownCaseCharging: Boolean = false
    private var cachedDeviceName: String = ""
    private var receiverRegistered = false
    private var routeScanStarted = false
    private var appUiActive = false
    private var appUiActiveUntilMs = 0L

    data class StatusSnapshot(
        val battery: BatteryParams?,
        val anc: Int,
        val transparencyVocalEnhancement: Boolean,
        val address: String?,
        val deviceName: String?,
        val connected: Boolean,
        val connecting: Boolean,
        val reconnectPending: Boolean,
    )

    // RFCOMM jobs
    private var connectionJob: kotlinx.coroutines.Job? = null
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var readerJob: kotlinx.coroutines.Job? = null
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectPending = false
    private val MOTOBUDS_RFCOMM_UUID: UUID = UUID.fromString("fc9d9fe0-4899-11ee-be56-0242ac120002")

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            handleUIEvent(p1!!)
        }
    }

    private fun changeUIAncStatus(status: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_ANC_CHANGED) {
            this.putExtra("status", status)
        }
        saveState()
    }

    private fun changeUIBatteryStatus(status: BatteryParams) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            if (::mDevice.isInitialized) this.putExtra("address", mDevice.address)
            this.putExtra("status", status)
            putBatteryExtras(status)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED) {
            putExtra("status", status)
            putBatteryExtras(status)
        }
    }

    private var currentWearStatus = WearStatus()

    private fun changeUIWearStatus(status: WearStatus) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED) {
            if (::mDevice.isInitialized) this.putExtra("address", mDevice.address)
            putWearStatusExtras(status)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_WEAR_STATUS_CHANGED) {
            putWearStatusExtras(status)
        }
    }

    private fun changeUIGameModeStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_GAME_MODE_CHANGED) {
            this.putExtra("enabled", enabled)
        }
        saveState()
    }

    private fun changeUIVolumeBoostStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_VOLUME_BOOST_CHANGED) {
            this.putExtra("enabled", enabled)
        }
        saveState()
    }

    private fun changeUIHiResModeStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_HI_RES_MODE_CHANGED) {
            this.putExtra("enabled", enabled)
        }
        saveState()
    }

    private fun saveState() {
        mContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences(ConfigManager.PREFS_NAME, Context.MODE_PRIVATE)
            ConfigManager.saveLastState(prefs, currentAnc, currentGameMode, currentVolumeBoost, false)
        }
    }

    private fun changeUITransparencyVocalEnhancementStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED) {
            this.putExtra("enabled", enabled)
        }
        sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED) {
            putExtra("enabled", enabled)
        }
    }

    private fun changeUISpatialAudioStatus(mode: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED) {
            this.putExtra("mode", mode)
        }
    }

    private fun changeUIEqPreset(presetId: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_EQ_PRESET_CHANGED) {
            this.putExtra("preset", presetId)
        }
    }

    private fun changeUISmartAncLevel(ordinal: Int) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_SMART_ANC_LEVEL_CHANGED) {
            this.putExtra("ordinal", ordinal)
        }
    }

    private fun changeUIDualDeviceConnectionStatus(enabled: Boolean) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_DUAL_DEVICE_CONNECTION_CHANGED) {
            this.putExtra("enabled", enabled)
        }
    }

    fun handleUIEvent(intent: Intent) {
        when (intent.action) {
            OppoPodsAction.ACTION_PODS_UI_INIT -> {
                markAppUiActive()
                Log.i(TAG, "UI Init")
                changeUIConnectionState(currentConnectionState())
                if (::currentBatteryParams.isInitialized)
                    changeUIBatteryStatus(currentBatteryParams)
                changeUIWearStatus(currentWearStatus)
                changeUIAncStatus(currentAnc)
                changeUISmartAncLevel(currentSmartAncLevel)
                changeUIGameModeStatus(currentGameMode)
                changeUIVolumeBoostStatus(currentVolumeBoost)
                changeUIHiResModeStatus(false)
                changeUITransparencyVocalEnhancementStatus(currentTransparencyVocalEnhancement)
                changeUISpatialAudioStatus(currentSpatialAudioMode)
                changeUIEqPreset(currentEqPreset)
                changeUIDualDeviceConnectionStatus(currentDualDeviceConnection)
                if (::mDevice.isInitialized && isConnected) {
                    sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                        this.putExtra("address", mDevice.address)
                        this.putExtra("device_name", mDevice.name ?: cachedDeviceName)
                    }
                    sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                        putExtra("device_name", mDevice.name ?: cachedDeviceName)
                    }
                }
            }
            OppoPodsAction.ACTION_PODS_UI_CLOSED -> {
                appUiActive = false
                appUiActiveUntilMs = 0L
                Log.i(TAG, "UI Closed")
            }
            OppoPodsAction.ACTION_ANC_SELECT -> {
                val status = intent.getIntExtra("status", 0)
                setANCMode(status)
            }
            OppoPodsAction.ACTION_REFRESH_STATUS -> {
                queryStatus(immediateReconnect = true)
            }
            OppoPodsAction.ACTION_GAME_MODE_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setGameMode(enabled)
            }
            OppoPodsAction.ACTION_VOLUME_BOOST_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setVolumeBoost(enabled)
            }
            OppoPodsAction.ACTION_HI_RES_MODE_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setHiResMode(enabled)
            }
            OppoPodsAction.ACTION_AUTO_GAME_MODE_CHANGED -> {
                autoGameModeEnabled = intent.getBooleanExtra("enabled", autoGameModeEnabled)
                Log.d(TAG, "Auto game mode synced: $autoGameModeEnabled")
            }
            OppoPodsAction.ACTION_GAME_MODE_IMPLEMENTATION_CHANGED -> {
                gameModeImplementation = GameModeImplementation.fromPreference(
                    intent.getStringExtra(GameModeImplementation.PREF_KEY)
                )
                Log.d(TAG, "Game mode implementation synced: ${gameModeImplementation.preferenceValue}")
            }
            OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setTransparencyVocalEnhancement(enabled)
            }
            OppoPodsAction.ACTION_SPATIAL_AUDIO_SET -> {
                val mode = intent.getIntExtra("mode", SpatialAudioMode.OFF)
                setSpatialAudioMode(mode)
            }
            OppoPodsAction.ACTION_EQ_PRESET_SET -> {
                val preset = intent.getIntExtra("preset", -1)
                if (preset in EqPreset.ALL) setEqPreset(preset)
            }
            OppoPodsAction.ACTION_DUAL_DEVICE_CONNECTION_SET -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setDualDeviceConnection(enabled)
            }
            OppoPodsAction.ACTION_CYCLE_ANC -> {
                cycleAnc()
            }
            OppoPodsAction.ACTION_CONFIG_CHANGED -> {
                ConfigManager.refreshFromPrefs(mPrefs)
                Log.d(TAG, "Config synced")
                if (!currentCapabilities().adaptiveSupported && currentAnc == 4) {
                    setANCMode(2)
                }
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_CONNECT -> {
                if (!RfcommLog.isEnabled()) {
                    RfcommLog.setEnabled(true, mContext)
                }
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_DISCONNECT -> {
                RfcommLog.setEnabled(false)
            }
            OppoPodsAction.ACTION_RFCOMM_LOG_CLEAR -> {
                RfcommLog.clear()
            }
            OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND -> {
                val hex = intent.getStringExtra("hex").orEmpty()
                sendDebugHex(hex)
            }
        }
    }

    fun currentStatusSnapshot(): StatusSnapshot {
        return StatusSnapshot(
            battery = if (::currentBatteryParams.isInitialized) currentBatteryParams else null,
            anc = currentAnc,
            transparencyVocalEnhancement = currentTransparencyVocalEnhancement,
            address = if (::mDevice.isInitialized) mDevice.address else null,
            deviceName = if (::mDevice.isInitialized) mDevice.name ?: cachedDeviceName else cachedDeviceName.takeIf { it.isNotEmpty() },
            connected = isConnected && socket != null,
            connecting = connectionJob?.isActive == true,
            reconnectPending = reconnectPending,
        )
    }

    private fun currentConnectionState(): String = when {
        isConnected && socket != null && ::currentBatteryParams.isInitialized -> "connected"
        connectionJob?.isActive == true || reconnectPending -> "connecting"
        isConnected && socket != null -> "connecting"
        else -> "disconnected"
    }

    private fun changeUIConnectionState(state: String) {
        sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTION_STATE_CHANGED) {
            if (::mDevice.isInitialized) {
                putExtra("address", mDevice.address)
                putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            putExtra("state", state)
        }
    }

    private fun sendAppStatusBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = mContext ?: return
        if (!isAppUiActive()) return
        Intent(action).apply {
            fill()
            this.`package` = BuildConfig.APPLICATION_ID
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun isAppUiActive(): Boolean {
        if (!appUiActive) return false
        if (SystemClock.elapsedRealtime() <= appUiActiveUntilMs) return true
        appUiActive = false
        appUiActiveUntilMs = 0L
        Log.d(TAG, "app UI active timeout, stop app status broadcasts")
        return false
    }

    private fun markAppUiActive() {
        appUiActive = true
        appUiActiveUntilMs = SystemClock.elapsedRealtime() + APP_UI_ACTIVE_TIMEOUT_MS
    }


    fun miuiRefreshPayload(battery: BatteryParams?, anc: Int, transparencyVocalEnhancement: Boolean = false): String {
        val values = MutableList(16) { "" }
        values[0] = miuiBatteryValue(battery?.left)
        values[1] = miuiBatteryValue(battery?.right)
        values[2] = miuiBatteryValue(battery?.case)
        values[7] = miuiAncLevel(anc, transparencyVocalEnhancement)
        values[8] = "true"
        values[11] = "00"
        values[13] = "00"
        values[14] = "00"
        return values.joinToString(",")
    }

    private fun miuiBatteryValue(params: PodParams?): String {
        if (params?.isConnected != true) return "255"
        val value = params.battery.coerceIn(0, 100)
        return (if (params.isCharging) value or 128 else value).toString()
    }

    private fun miuiAncLevel(anc: Int, transparencyVocalEnhancement: Boolean): String {
        return when (anc) {
            2 -> "0100"
            3 -> "0200"
            4 -> "0103"
            else -> "0000"
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun handleBatteryChanged(result: BatteryParser.BatteryResult) {
        val left = PodParams(
            result.left?.level ?: 0,
            result.left?.isCharging == true,
            result.left != null,
            0
        )
        val right = PodParams(
            result.right?.level ?: 0,
            result.right?.isCharging == true,
            result.right != null,
            0
        )
        val case = if (result.case != null) {
            lastKnownCaseBattery = result.case.level
            lastKnownCaseCharging = result.case.isCharging
            PodParams(
                result.case.level,
                result.case.isCharging,
                true,
                0
            )
        } else {
            PodParams(
                lastKnownCaseBattery,
                lastKnownCaseCharging,
                false,
                0
            )
        }

        Log.v(TAG, "batt left ${left.battery} right ${right.battery} case ${case.battery}")

        val shouldShowToast = !mShowedConnectedToast
        if (shouldShowToast) {
            // Wait until at least one connected ear has valid battery data
            val hasValidData = (left.isConnected && left.battery > 0) ||
                    (right.isConnected && right.battery > 0)
            if (!hasValidData) return
        }

        val batteryParams = BatteryParams(left, right, case)
        currentBatteryParams = batteryParams

        // Check low battery
        mContext?.let { ctx ->
            BatteryNotificationHelper.checkAndNotify(
                ctx,
                left.battery,
                right.battery,
                case.battery
            )
        }

        if (shouldShowToast) {
            changeUIConnectionState("connected")
            sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                this.putExtra("address", mDevice.address)
                this.putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            sendExternalPodsStatusBroadcast(OppoPodsAction.ACTION_PODS_CONNECTED) {
                putExtra("device_name", mDevice.name ?: cachedDeviceName)
            }
            if (shouldShowIsland(ConfigManager.ISLAND_SHOW_TIMING_CONNECTED)) {
                MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(mContext!!, batteryParams, mDevice)
            }
            mShowedConnectedToast = true
        }
        MiuiStrongToastUtil.showPodsNotificationByMiuiBt(mContext!!, batteryParams, mDevice)
        changeUIBatteryStatus(batteryParams)

        lastTempBatt = if (left.isConnected && right.isConnected)
            minOf(left.battery, right.battery)
        else if (left.isConnected)
            left.battery
        else if (right.isConnected)
            right.battery
        else SystemApisUtils.BATTERY_LEVEL_UNKNOWN

        setRegularBatteryLevel(lastTempBatt)
    }

    private val routeCallback = object : MediaRouter2.RouteCallback() {
        override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
            Log.v(TAG, "routes updated: $routes")
            this@RfcommController.routes = routes
        }
    }

    private fun startRoutesScan() {
        if (routeScanStarted) return
        val executor = Executor { p0 ->
            CoroutineScope(Dispatchers.IO).launch { p0?.run() }
        }
        val preferredFeature = listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO, MediaRoute2Info.FEATURE_LIVE_VIDEO)
        mediaRouter.registerRouteCallback(executor, routeCallback, RouteDiscoveryPreference.Builder(preferredFeature, true).build())
        scanToken = mediaRouter.requestScan(MediaRouter2.ScanRequest.Builder().build())
        routeScanStarted = true
    }

    private fun stopRoutesScan() {
        scanToken?.let { mediaRouter.cancelScanRequest(it) }
        if (routeScanStarted) {
            mediaRouter.unregisterRouteCallback(routeCallback)
            routeScanStarted = false
        }
    }

    private fun createRfcommSocket(device: BluetoothDevice): BluetoothSocket {
        return device.createRfcommSocketToServiceRecord(MOTOBUDS_RFCOMM_UUID)
    }

    fun connectPod(context: Context, device: BluetoothDevice, prefs: SharedPreferences, appRequested: Boolean = false) {
        connectionJob?.cancel()
        reconnectJob?.cancel()
        readerJob?.cancel()
        closeSocketOnly()
        mContext = context
        mDevice = device
        mPrefs = prefs
        cachedDeviceName = device.name ?: ""
        if (appRequested) {
            markAppUiActive()
        }
        autoGameModeEnabled = mPrefs.getBoolean("auto_game_mode", false)
        gameModeImplementation = GameModeImplementation.fromPreference(
            mPrefs.getString(GameModeImplementation.PREF_KEY, null)
        )
        ConfigManager.refreshFromPrefs(mPrefs)
        currentAnc = ConfigManager.getLastAncMode(mPrefs)
        currentGameMode = ConfigManager.getLastGameMode(mPrefs)
        currentVolumeBoost = ConfigManager.getLastVolumeBoost(mPrefs)
        Log.d(TAG, "Adaptive support initial: ${currentCapabilities().adaptiveSupported}")
        Log.d(TAG, "Auto game mode initial: $autoGameModeEnabled")
        Log.d(TAG, "Game mode implementation initial: ${gameModeImplementation.preferenceValue}")
        Log.d(TAG, "RFCOMM UUID initial: $MOTOBUDS_RFCOMM_UUID")
        Log.d(TAG, "Restored state: anc=$currentAnc game=$currentGameMode volume=$currentVolumeBoost")

        if (!receiverRegistered) {
            context.registerReceiver(broadcastReceiver, IntentFilter().apply {
                this.addAction(OppoPodsAction.ACTION_ANC_SELECT)
                this.addAction(OppoPodsAction.ACTION_PODS_UI_INIT)
                this.addAction(OppoPodsAction.ACTION_PODS_UI_CLOSED)
                this.addAction(OppoPodsAction.ACTION_REFRESH_STATUS)
                this.addAction(OppoPodsAction.ACTION_GAME_MODE_SET)
                this.addAction(OppoPodsAction.ACTION_AUTO_GAME_MODE_CHANGED)
                this.addAction(OppoPodsAction.ACTION_GAME_MODE_IMPLEMENTATION_CHANGED)
                this.addAction(OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET)
                this.addAction(OppoPodsAction.ACTION_SPATIAL_AUDIO_SET)
                this.addAction(OppoPodsAction.ACTION_EQ_PRESET_SET)
                this.addAction(OppoPodsAction.ACTION_DUAL_DEVICE_CONNECTION_SET)
                this.addAction(OppoPodsAction.ACTION_CYCLE_ANC)
                this.addAction(OppoPodsAction.ACTION_CONFIG_CHANGED)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_CONNECT)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_DISCONNECT)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_LOG_CLEAR)
                this.addAction(OppoPodsAction.ACTION_RFCOMM_DEBUG_SEND)
            }, Context.RECEIVER_EXPORTED)
            receiverRegistered = true
        }

        MediaControl.mContext = mContext
        mediaRouter = MediaRouter2.getInstance(mContext!!)
        startRoutesScan()

        isConnected = true
        changeUIConnectionState("connecting")

        connectRfcomm(initialDelayMs = 500L)

    }

    private fun sendExternalPodsStatusBroadcast(action: String, fill: Intent.() -> Unit = {}) {
        val ctx = mContext ?: return
        listOf("com.milink.service", "com.xiaomi.bluetooth", "com.android.settings").forEach { targetPackage ->
            Intent(action).apply {
                if (::mDevice.isInitialized) {
                    putExtra("address", mDevice.address)
                    putExtra("device_name", mDevice.name ?: cachedDeviceName)
                }
                fill()
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ctx.sendBroadcast(this)
            }
        }
    }

    private fun Intent.putBatteryExtras(status: BatteryParams) {
        putExtra("left_battery", status.left?.battery ?: 0)
        putExtra("left_charging", status.left?.isCharging == true)
        putExtra("left_connected", status.left?.isConnected == true)
        putExtra("right_battery", status.right?.battery ?: 0)
        putExtra("right_charging", status.right?.isCharging == true)
        putExtra("right_connected", status.right?.isConnected == true)
        putExtra("case_battery", status.case?.battery ?: 0)
        putExtra("case_charging", status.case?.isCharging == true)
        putExtra("case_connected", status.case?.isConnected == true)
    }

    private fun Intent.putWearStatusExtras(status: WearStatus) {
        putExtra("left_wear_status", status.left?.value ?: -1)
        putExtra("right_wear_status", status.right?.value ?: -1)
        putExtra("case_wear_status", status.case?.value ?: -1)
    }

    private fun mergeWearStatus(current: WearStatus, update: WearStatus): WearStatus {
        return WearStatus(
            left = update.left ?: current.left,
            right = update.right ?: current.right,
            case = update.case ?: current.case
        )
    }

    private fun showIslandForWearStatusChange(previous: WearStatus, current: WearStatus) {
        if (!::currentBatteryParams.isInitialized) return
        val changedTimings = setOfNotNull(
            islandShowTimingForChange(previous.left, current.left),
            islandShowTimingForChange(previous.right, current.right),
            islandShowTimingForChange(previous.case, current.case),
        )
        if (changedTimings.any { shouldShowIsland(it) }) {
            MiuiStrongToastUtil.showPodsBatteryToastByMiuiBt(mContext ?: return, currentBatteryParams)
        }
    }

    private fun shouldShowIsland(timing: Int): Boolean {
        return ConfigManager.islandMode() == ConfigManager.ISLAND_MODE_MODULE &&
                timing in ConfigManager.islandShowTimings()
    }

    private fun islandShowTimingForChange(previous: WearState?, current: WearState?): Int? {
        if (previous == current) return null
        return when (current) {
            WearState.WEARING -> ConfigManager.ISLAND_SHOW_TIMING_WEARING
            WearState.REMOVED -> ConfigManager.ISLAND_SHOW_TIMING_REMOVED
            WearState.IN_CASE -> ConfigManager.ISLAND_SHOW_TIMING_IN_CASE
            else -> null
        }
    }

    private fun connectRfcomm(initialDelayMs: Long = 0L) {
        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            if (initialDelayMs > 0) delay(initialDelayMs)
            if (!isConnected || !::mDevice.isInitialized) return@launch
            closeSocketOnly()
            try {
                // MotoBuds connection: try standard RFCOMM first, then insecure, then port 16
                var connected = false
                try {
                    val newSocket = createRfcommSocket(mDevice)
                    newSocket.connect()
                    socket = newSocket
                    connected = true
                } catch (e: IOException) {
                    Log.d(TAG, "Standard RFCOMM failed, trying insecure")
                    try {
                        val insecureSocket = mDevice.createInsecureRfcommSocketToServiceRecord(MOTOBUDS_RFCOMM_UUID)
                        insecureSocket.connect()
                        socket = insecureSocket
                        connected = true
                    } catch (e2: IOException) {
                        Log.d(TAG, "Insecure RFCOMM failed, trying port 16")
                        try {
                            val method = mDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            val portSocket = method.invoke(mDevice, 16) as BluetoothSocket
                            portSocket.connect()
                            socket = portSocket
                            connected = true
                        } catch (e3: Exception) {
                            Log.e(TAG, "All RFCOMM connection methods failed")
                        }
                    }
                }

                if (!connected) throw IOException("All connection methods failed")

                reconnectAttempts.set(0)
                reconnectPending = false
                Log.d(TAG, "RFCOMM connected! uuid=$MOTOBUDS_RFCOMM_UUID")
                RfcommLog.i(mContext, TAG, "connected uuid=$MOTOBUDS_RFCOMM_UUID")
                changeUIConnectionState("connecting")

                startPacketReader(socket!!.inputStream)

                delay(300)
                sendStatusQueryPackets(immediateReconnect = false)

                if (autoGameModeEnabled) {
                    enableGameModeOnConnect()
                }
            } catch (e: IOException) {
                Log.e(TAG, "RFCOMM connect failed", e)
                changeUIConnectionState("error")
                scheduleReconnect("connect failed")
            }
        }
    }

    private fun isAutoReconnectEnabled(): Boolean {
        return try {
            mPrefs.getBoolean(ConfigManager.PREF_KEY_AUTO_RECONNECT, true)
        } catch (_: Exception) {
            true
        }
    }

    private fun scheduleReconnect(reason: String, immediate: Boolean = false) {
        if (!isConnected || !::mDevice.isInitialized || mContext == null) return
        if (!isAutoReconnectEnabled() && !immediate) {
            Log.d(TAG, "auto-reconnect disabled, skipping reconnect for reason=$reason")
            RfcommLog.w(mContext, TAG, "auto-reconnect disabled, skip reason=$reason")
            closeSocketOnly()
            reconnectPending = false
            changeUIConnectionState("disconnected")
            return
        }
        RfcommLog.w(mContext, TAG, "schedule reconnect reason=$reason immediate=$immediate")
        closeSocketOnly()
        reconnectPending = true
        if (immediate) {
            if (connectionJob?.isActive == true) {
                Log.d(TAG, "immediate RFCOMM reconnect skipped: connecting reason=$reason")
                return
            }
            Log.d(TAG, "immediate RFCOMM reconnect reason=$reason")
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectPending = false
            connectRfcomm()
            return
        }
        if (reconnectJob?.isActive == true) {
            Log.d(TAG, "RFCOMM reconnect already scheduled reason=$reason")
            return
        }
        val attempt = reconnectAttempts.incrementAndGet()
        Log.d(TAG, "schedule RFCOMM reconnect reason=$reason attempt=$attempt delay=${AUTO_RECONNECT_DELAY_MS}ms")
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(AUTO_RECONNECT_DELAY_MS)
            reconnectJob = null
            reconnectPending = false
            connectRfcomm()
        }
    }

    private fun reconnectNowForRequest(reason: String) {
        if (socket != null && !reconnectPending) return
        scheduleReconnect(reason, immediate = true)
    }

    private fun closeSocketOnly() {
        readerJob?.cancel()
        readerJob = null
        try {
            socket?.close()
        } catch (_: IOException) {}
        socket = null
    }

    private fun startPacketReader(inputStream: InputStream) {
        readerJob?.cancel()
        readerJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            try {
                while (isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val packet = buffer.copyOfRange(0, bytesRead)
                        RfcommLog.d(mContext, "RFCOMM/RX", packet.toHexString(HexFormat.UpperCase))
                        handleMotoBudsPacket(packet)
                    } else if (bytesRead == -1) {
                        Log.d(TAG, "RFCOMM stream ended")
                        RfcommLog.w(mContext, TAG, "stream ended")
                        scheduleReconnect("stream ended")
                        break
                    }
                }
            } catch (e: IOException) {
                if (isConnected) {
                    Log.e(TAG, "RFCOMM read error", e)
                    RfcommLog.e(mContext, TAG, "read error: ${e.message.orEmpty()}")
                    scheduleReconnect("read error")
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun handleMotoBudsPacket(packet: ByteArray) {
        Log.v(TAG, "Received: ${packet.toHexString(HexFormat.UpperCase)}")
        if (packet.size < 14) return
        if (packet[0] != 0x48.toByte() || packet[1] != 0x45.toByte()) return

        val opcode = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        val payloadLen = ((packet[10].toInt() and 0xFF) or ((packet[11].toInt() and 0xFF) shl 8))
        val payload = if (payloadLen > 0 && packet.size >= 14 + payloadLen) {
            packet.copyOfRange(14, 14 + payloadLen)
        } else null

        Log.d(TAG, "opcode=0x${opcode.toString(16)} payloadLen=$payloadLen")

        when (opcode) {
            // Battery (query response 0x005 or notification 0x009)
            Cmd.GET_BATTERY_LEVEL, Cmd.BATTERY_LEVEL_CHANGED -> {
                if (payload != null && payload.size >= 3) {
                    val left = payload[0].toInt() and 0xFF
                    val right = payload[1].toInt() and 0xFF
                    val case = payload[2].toInt() and 0xFF
                    val leftInfo = if (left != 0xFF) BatteryParser.BatteryInfo(left.coerceIn(0, 100), false) else null
                    val rightInfo = if (right != 0xFF) BatteryParser.BatteryInfo(right.coerceIn(0, 100), false) else null
                    val caseInfo = if (case != 0xFF) BatteryParser.BatteryInfo(case.coerceIn(0, 100), false) else null
                    handleBatteryChanged(BatteryParser.BatteryResult(leftInfo, rightInfo, caseInfo))
                }
            }
            // ANC mode (query response 0x200 or notification 0x204)
            Cmd.GET_CURRENT_ANC_MODE, Cmd.ANC_MODE_CHANGED -> {
                if (payload != null && payload.size >= 2) {
                    val mode = payload[0].toInt() and 0xFF
                    val subMode = payload[1].toInt() and 0xFF
                    val ancMode = when (mode) {
                        0x00 -> 1 // OFF
                        0x01 -> if (subMode == 0x01) 4 else 2 // Adaptive or NC
                        0x02 -> 3 // Transparency
                        else -> currentAnc
                    }
                    if (ancMode != currentAnc) {
                        Log.d(TAG, "ANC mode received: $ancMode (mode=$mode sub=$subMode)")
                        currentAnc = ancMode
                        changeUIAncStatus(currentAnc)
                    }
                }
            }
            // Game mode (notification 0x312)
            Cmd.GAME_MODE_STATE_CHANGED -> {
                if (payload != null && payload.isNotEmpty()) {
                    val enabled = payload[0].toInt() == 0x01
                    Log.d(TAG, "Game mode received: $enabled")
                    currentGameMode = enabled
                    changeUIGameModeStatus(enabled)
                }
            }
            // Game mode query response (0x30E)
            Cmd.GET_GAME_MODE -> {
                if (payload != null && payload.isNotEmpty()) {
                    val enabled = payload[0].toInt() == 0x01
                    Log.d(TAG, "Game mode query response: $enabled")
                    currentGameMode = enabled
                    changeUIGameModeStatus(enabled)
                }
            }
            // Volume boost notification (0x315) or query response (0x313)
            Cmd.VOLUME_BOOST_STATE_CHANGED, Cmd.GET_VOLUME_BOOST_STATE -> {
                if (payload != null && payload.isNotEmpty()) {
                    val enabled = payload[0].toInt() == 0x01
                    Log.d(TAG, "Volume boost received: $enabled")
                    currentVolumeBoost = enabled
                    changeUIVolumeBoostStatus(enabled)
                }
            }
            // Hi-res mode notification (0x311) or query response (0x30D)
            Cmd.HI_RES_MODE_STATE_CHANGED, Cmd.GET_HI_RES_MODE -> {
                if (payload != null && payload.isNotEmpty()) {
                    val enabled = payload[0].toInt() == 0x01
                    Log.d(TAG, "Hi-res mode received: $enabled")
                    changeUIHiResModeStatus(enabled)
                }
            }
            // Dual connection notification (0x40F) or query response (0x406)
            Cmd.DUAL_CONNECTION_STATE_CHANGED, Cmd.GET_DUAL_CONNECTION_STATE -> {
                if (payload != null && payload.isNotEmpty()) {
                    val enabled = payload[0].toInt() == 0x01
                    Log.d(TAG, "Dual connection received: $enabled")
                    currentDualDeviceConnection = enabled
                    changeUIDualDeviceConnectionStatus(enabled)
                }
            }
            // EQ preset query response (0x302) or notification (0x308)
            Cmd.GET_EQ_SET, Cmd.EQ_SET_CHANGED -> {
                if (payload != null && payload.isNotEmpty()) {
                    val preset = payload[0].toInt() and 0xFF
                    if (preset in EqPreset.ALL) {
                        Log.d(TAG, "EQ preset received: $preset")
                        currentEqPreset = preset
                        changeUIEqPreset(preset)
                    }
                }
            }
            // Spatial audio notification (0x310) or query response (0x30A)
            Cmd.SPATIAL_AUDIO_STATE_CHANGED, Cmd.GET_SPATIAL_AUDIO_STATE -> {
                if (payload != null && payload.size >= 2) {
                    val enabled = payload[0].toInt() == 0x01
                    val headTracking = payload[1].toInt() == 0x01
                    val mode = when {
                        !enabled -> SpatialAudioMode.OFF
                        headTracking -> SpatialAudioMode.HEAD_TRACKING
                        else -> SpatialAudioMode.FIXED
                    }
                    Log.d(TAG, "Spatial audio received: mode=$mode")
                    currentSpatialAudioMode = mode
                    changeUISpatialAudioStatus(mode)
                }
            }
            // In-ear status notification (0x404)
            Cmd.IN_EAR_STATUS_CHANGED -> {
                if (payload != null && payload.size >= 2) {
                    currentInEarLeft = payload[0].toInt() != 0
                    currentInEarRight = payload[1].toInt() != 0
                    Log.d(TAG, "In-ear status: left=$currentInEarLeft right=$currentInEarRight")
                }
            }
            // In-case status notification (0x40C)
            Cmd.IN_CASE_STATUS_INDICATION -> {
                if (payload != null && payload.isNotEmpty()) {
                    currentInCase = payload[0].toInt() != 0
                    Log.d(TAG, "In-case status: $currentInCase")
                }
            }
            // Toggle configs response (0x100)
            Cmd.GET_TOGGLE_CONFIGS -> {
                if (payload != null && payload.size >= 2) {
                    Log.d(TAG, "Toggle configs: ${payload.toHexString(HexFormat.UpperCase)}")
                }
            }
            else -> {
                Log.d(TAG, "Unhandled opcode 0x${opcode.toString(16)}: ${packet.toHexString(HexFormat.UpperCase)}")
            }
        }
    }

    fun disconnectedPod(context: Context, device: BluetoothDevice) {
        isConnected = false
        connectionJob?.cancel()
        reconnectJob?.cancel()
        readerJob?.cancel()
        reconnectAttempts.set(0)
        reconnectPending = false

        closeSocketOnly()

        mContext?.let {
            stopRoutesScan()
            cancelPodsNotificationByMiuiBt(context, device)
            sendAppStatusBroadcast(OppoPodsAction.ACTION_PODS_DISCONNECTED) {
                putExtra("address", device.address)
            }
            if (receiverRegistered) {
                it.unregisterReceiver(broadcastReceiver)
                receiverRegistered = false
            }
        }

        mShowedConnectedToast = false
        currentWearStatus = WearStatus()
        currentAnc = 1
        currentSmartAncLevel = -1
        currentGameMode = false
        currentTransparencyVocalEnhancement = false
        currentSpatialAudioMode = SpatialAudioMode.OFF
        currentEqPreset = -1
        currentDualDeviceConnection = false
        lastKnownCaseBattery = 0
        lastKnownCaseCharging = false
        changeUIConnectionState("disconnected")
        cachedDeviceName = ""
        mContext = null
        MediaControl.mContext = null
    }

    private fun sendPacketSafe(packet: ByteArray, requestReason: String? = null) {
        if (requestReason != null) reconnectNowForRequest(requestReason)
        try {
            val currentSocket = socket ?: run {
                RfcommLog.w(mContext, "RFCOMM/TX", "socket null: ${packet.toHexString(HexFormat.UpperCase)}")
                scheduleReconnect("socket null before send", immediate = requestReason != null)
                return
            }
            RfcommLog.d(mContext, "RFCOMM/TX", packet.toHexString(HexFormat.UpperCase))
            currentSocket.outputStream.write(packet)
            currentSocket.outputStream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Send packet failed", e)
            RfcommLog.e(mContext, TAG, "send failed: ${e.message.orEmpty()}")
            scheduleReconnect("send error", immediate = requestReason != null)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun sendDebugHex(hex: String) {
        val normalized = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (normalized.isEmpty() || normalized.length % 2 != 0 || !normalized.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            RfcommLog.e(mContext, "RFCOMM/DEBUG", "invalid HEX: $hex")
            return
        }
        val packet = normalized.hexToByteArray()
        RfcommLog.i(mContext, "RFCOMM/DEBUG", "send ${packet.size} bytes")
        sendPacketSafe(packet, "rfcomm debug send")
    }

    fun setGameMode(enabled: Boolean) {
        Log.d(TAG, "setGameMode: $enabled")
        currentGameMode = enabled
        CoroutineScope(Dispatchers.IO).launch {
            sendGameModePackets(enabled, "game mode control")
        }
    }

    fun setVolumeBoost(enabled: Boolean) {
        Log.d(TAG, "setVolumeBoost: $enabled")
        currentVolumeBoost = enabled
        changeUIVolumeBoostStatus(enabled)
        val packet = if (enabled) Enums.VOLUME_BOOST_ON else Enums.VOLUME_BOOST_OFF
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "volume boost control")
            delay(350)
            sendStatusQueryPackets(immediateReconnect = false)
        }
    }

    fun setHiResMode(enabled: Boolean) {
        Log.d(TAG, "setHiResMode: $enabled")
        changeUIHiResModeStatus(enabled)
        val payload = byteArrayOf(if (enabled) 0x01 else 0x00)
        val packet = MotoBudsPackets.buildPacket(
            opcode = Cmd.SET_HI_RES_MODE,
            payload = payload
        )
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "hi-res mode control")
            delay(350)
            sendStatusQueryPackets(immediateReconnect = false)
        }
    }

    private suspend fun enableGameModeOnConnect() {
        delay(500)
        repeat(3) { attempt ->
            if (!isConnected || mContext == null) return

            val attemptStartedMs = SystemClock.elapsedRealtime()
            Log.d(TAG, "Auto game mode: enabling after connect, attempt=${attempt + 1}, implementation=$gameModeImplementation")
            currentGameMode = true
            changeUIGameModeStatus(true)
            sendGameModePackets(true, "auto game mode")

            delay(300)
            if (!isConnected) return
            sendPacketSafe(Enums.QUERY_STATUS)

            delay(if (attempt == 0) 700 else 1_500)
            if (lastGameModeStatusUpdateMs >= attemptStartedMs && currentGameMode) {
                return
            }
            Log.d(TAG, "Auto game mode: attempt ${attempt + 1} did not verify, retrying")
        }
    }

    private suspend fun sendGameModePackets(enabled: Boolean, requestReason: String? = null) {
        Enums.gameModePackets(enabled, gameModeImplementation).forEachIndexed { index, packet ->
            if (index > 0) delay(120)
            sendPacketSafe(packet, if (index == 0) requestReason else null)
        }
    }

    fun setTransparencyVocalEnhancement(enabled: Boolean) {
        Log.d(TAG, "setTransparencyVocalEnhancement: $enabled (no-op for MotoBuds)")
        currentTransparencyVocalEnhancement = enabled
        changeUITransparencyVocalEnhancementStatus(enabled)
    }

    fun setSpatialAudioMode(mode: Int) {
        val normalizedMode = mode.coerceIn(SpatialAudioMode.OFF, SpatialAudioMode.HEAD_TRACKING)
        val packet = if (currentCapabilities().spatialSoundSwitchSupported) {
            Enums.spatialSoundSwitchPacket(normalizedMode != SpatialAudioMode.OFF)
        } else {
            Enums.spatialAudioPacket(normalizedMode)
        }
        Log.i(TAG, "setSpatialAudioMode: $normalizedMode, packet=${packet.toHexString(HexFormat.UpperCase)}")
        currentSpatialAudioMode = normalizedMode
        changeUISpatialAudioStatus(normalizedMode)
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "spatial audio control")
        }
    }

    fun setEqPreset(presetId: Int) {
        if (presetId !in EqPreset.ALL) {
            Log.w(TAG, "setEqPreset ignored: invalid preset $presetId")
            return
        }
        val packet = Enums.eqPresetPacket(presetId)
        Log.i(TAG, "setEqPreset: $presetId, packet=${packet.toHexString(HexFormat.UpperCase)}")
        currentEqPreset = presetId
        changeUIEqPreset(presetId)
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "eq preset control")
            delay(1000)
            sendPacketSafe(Enums.QUERY_EQ, "eq confirm")
        }
    }

    fun setDualDeviceConnection(enabled: Boolean) {
        val packet = Enums.dualDeviceConnectionPacket(enabled)
        Log.i(TAG, "setDualDeviceConnection: $enabled, packet=${packet.toHexString(HexFormat.UpperCase)}")
        currentDualDeviceConnection = enabled
        changeUIDualDeviceConnectionStatus(enabled)
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "dual-device connection control")
            delay(1000)
            sendPacketSafe(Enums.QUERY_DUAL_CONNECTION, "dual connection confirm")
        }
    }

    fun cycleAnc() {
        val cycle = if (currentCapabilities().adaptiveSupported) {
            listOf(2, 4, 3, 1)
        } else {
            listOf(2, 3, 1)
        }
        val currentIndex = cycle.indexOf(if (currentAnc in 5..8) 2 else currentAnc)
        val next = cycle[(currentIndex + 1).floorMod(cycle.size)]
        setANCMode(next)
    }

    private fun currentCapabilities(): DeviceCapabilities {
        return detectDeviceCapabilities(
            deviceName = if (::mDevice.isInitialized) mDevice.name ?: cachedDeviceName else cachedDeviceName,
            adaptiveOverride = ConfigManager.adaptiveCapabilityOverride(),
            spatialAudioOverride = ConfigManager.spatialAudioCapabilityOverride(),
            spatialSoundSwitchOverride = ConfigManager.spatialSoundSwitchCapabilityOverride(),
            ancImplementationOverride = ConfigManager.ancImplementationCapabilityOverride(),
        )
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

    fun setANCMode(mode: Int) {
        Log.d(TAG, "setANCMode: $mode")
        currentAnc = mode
        var packet = when (mode) {
            1 -> Enums.ANC_OFF
            2 -> Enums.ANC_NOISE_CANCEL
            3 -> Enums.ANC_TRANSPARENCY
            4 -> Enums.ANC_ADAPTIVE
            else -> return
        }
        if (currentCapabilities().ancImplementation == AncImplementation.COMPATIBLE) {
            packet = when (mode) {
                1 -> Enums.ANC_NOISE_CANCEL
                2 -> Enums.ANC_OFF
                else -> packet
            }
        }
        changeUIAncStatus(mode)
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(packet, "anc control")
            delay(350)
            sendStatusQueryPackets(immediateReconnect = false)
        }
    }

    fun queryBattery() {
        CoroutineScope(Dispatchers.IO).launch {
            sendPacketSafe(Enums.QUERY_BATTERY, "battery query")
        }
    }

    /**
     * Combo query strategy: send batch query (wake + game mode), then battery, then ANC.
     */
    fun queryStatus(immediateReconnect: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            sendStatusQueryPackets(immediateReconnect)
        }
    }

    private suspend fun sendStatusQueryPackets(immediateReconnect: Boolean = true) {
        val reason = if (immediateReconnect) "status query" else null
        // Initial query (per protocol doc)
        sendPacketSafe(Enums.QUERY_PROFILE_VERSION, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_SUPPORT_FEATURES, reason)
        delay(50)
        // Full status query (per protocol doc)
        sendPacketSafe(Enums.QUERY_DEVICE_NAME, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_HARDWARE_INFO, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_BATTERY, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_PRIMARY_EARBUD, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_CHANNEL_ID, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_EARBUDS_COLOR, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_SUPPORT_CONFIGS, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_TOGGLE_CONFIGS, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_ANC, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_ADAPTATION_STATUS, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_EQ, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_EQ_SETS, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_GAME_MODE, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_VOLUME_BOOST, reason)
        delay(50)
        sendPacketSafe(Enums.QUERY_DUAL_CONNECTION, reason)
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        MediaControl.sendPause()

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            for (route in routes) {
                if (route.type == MediaRoute2Info.TYPE_BUILTIN_SPEAKER) {
                    Log.d(TAG, "found speaker route $route")
                    mediaRouter.transferTo(route)
                }
            }
        }

        setRegularBatteryLevel(lastTempBatt)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }
            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)

        for (route in routes) {
            if (route.type == MediaRoute2Info.TYPE_BLUETOOTH_A2DP && route.name == device!!.name) {
                Log.d(TAG, "found bt route $route")
                mediaRouter.transferTo(route)
            }
        }

        val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
        statusBarManager.setIconVisibility("wireless_headset", true)
        setRegularBatteryLevel(lastTempBatt)
    }

    fun setRegularBatteryLevel(level: Int) {
        try {
            val service = getObjectField(mContext, "mAdapterService")
            callMethod(service, "setBatteryLevel", mDevice, level, false)
        } catch (e: Exception) {
            Log.e(TAG, "setRegularBatteryLevel failed", e)
        }
    }

    private fun getObjectField(instance: Any?, fieldName: String): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            runCatching {
                return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(instance)
            }
            cls = cls.superclass
        }
        throw NoSuchFieldException(fieldName)
    }

    private fun callMethod(instance: Any?, methodName: String, vararg args: Any?): Any? {
        if (instance == null) return null
        var cls: Class<*>? = instance.javaClass
        while (cls != null) {
            cls.declaredMethods.firstOrNull { it.name == methodName && it.parameterTypes.size == args.size }?.let {
                it.isAccessible = true
                return it.invoke(instance, *args)
            }
            cls = cls.superclass
        }
        throw NoSuchMethodException(methodName)
    }
}
