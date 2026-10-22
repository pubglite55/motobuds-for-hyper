package moe.chenxy.oppopods.hook

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import moe.chenxy.oppopods.utils.miuiStrongToast.data.OppoPodsPrefsKey
import java.util.Collections

/**
 * HyperOS 4 / Android 17 keeps the `wireless_headset` status bar icon under the control of
 * `HeadsetIconShowManager` (hosted by MiuiNearbyServiceV2, obfuscated as
 * `com.android.bluetooth.ble.app.i`) inside the `com.xiaomi.bluetooth` process. For devices it
 * cannot recognise as Xiaomi ecosystem devices it calls
 * `StatusBarManager.setIconVisibility("wireless_headset", false)` roughly 500 ms after connect.
 * That call never enters `com.android.bluetooth`, so HeadsetStateDispatcher cannot guard it and
 * the icon lit by the module disappears again after a short flash. This hook suppresses the hide
 * call from inside `com.xiaomi.bluetooth`.
 */
object XiaomiBluetoothHeadsetIconGuard : HookContext() {
    private const val TAG = "MotoBuds-Bluetooth"
    private const val HEADSET_ICON_SLOT = "wireless_headset"

    @Volatile
    private var appContext: Context? = null

    private val connectedAddresses = Collections.synchronizedSet(mutableSetOf<String>())
    private var connectionReceiver: BroadcastReceiver? = null
    private var connectionReceiverContext: Context? = null

    override fun onHook() {
        // Application.onCreate is declared on android.app.Application (stable framework API) and
        // always runs early in the process; after a hot reload currentApplication() covers it.
        runCatching {
            hookAfter(findMethod("android.app.Application", "onCreate")) {
                val app = instance as? Application ?: return@hookAfter
                onContextAvailable(app)
            }
        }.onFailure { Log.w(TAG, "hook Application.onCreate for icon guard failed", it) }

        // Hook every setIconVisibility overload whose first parameter is String so ROM signature
        // differences cannot bind us to the wrong overload, then filter by slot name.
        runCatching {
            val overloads = findClass("android.app.StatusBarManager").declaredMethods
                .filter { it.name == "setIconVisibility" && it.parameterTypes.firstOrNull() == String::class.java }
            overloads.forEach { method ->
                method.isAccessible = true
                hookBefore(method) {
                    val slot = args.getOrNull(0) as? String ?: return@hookBefore
                    if (slot != HEADSET_ICON_SLOT) return@hookBefore
                    val visible = args.filterIsInstance<Boolean>().firstOrNull() ?: return@hookBefore
                    if (visible) return@hookBefore
                    if (shouldSuppressHide()) {
                        result = null
                    }
                }
            }
            if (overloads.isEmpty()) {
                Log.w(TAG, "no setIconVisibility(String,*) overload found in com.xiaomi.bluetooth")
            } else {
                Log.d(TAG, "hooked " + overloads.size + " setIconVisibility(String,*) overload(s) in com.xiaomi.bluetooth")
            }
        }.onFailure { Log.w(TAG, "hook wireless_headset icon guard (xiaomi.bluetooth) skipped", it) }
    }

    override fun onHotReloading() {
        connectionReceiver?.let { receiver ->
            runCatching { connectionReceiverContext?.unregisterReceiver(receiver) }
        }
        connectionReceiver = null
        connectionReceiverContext = null
        connectedAddresses.clear()
        appContext = null
    }

    private fun shouldSuppressHide(): Boolean {
        val context = ensureContext()
        val podConnected = isMotobudsConnected(context)
        reloadRemotePrefs()
        val showHeadsetIcon = prefs.getBoolean(
            OppoPodsPrefsKey.SHOW_HEADSET_STATUS_BAR_ICON,
            OppoPodsPrefsKey.DEFAULT_SHOW_HEADSET_STATUS_BAR_ICON
        )
        val suppress = context != null && podConnected && showHeadsetIcon
        Log.i(
            TAG,
            "wireless_headset hide " + (if (suppress) "suppressed" else "passed through") +
                ": context=" + (context != null) + " podConnected=" + podConnected +
                " showSetting=" + showHeadsetIcon
        )
        return suppress
    }

    private fun ensureContext(): Context? {
        appContext?.let { return it }
        val context = currentApplication() ?: return null
        onContextAvailable(context)
        return appContext
    }

    private fun onContextAvailable(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext ?: context
        }
        registerConnectionReceiver(context)
    }

    private fun registerConnectionReceiver(context: Context) {
        if (connectionReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent) {
                val device = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                ) ?: return
                if (!isMotobudsPod(device)) return
                val state = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED
                )
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> connectedAddresses += device.address
                    BluetoothProfile.STATE_DISCONNECTING,
                    BluetoothProfile.STATE_DISCONNECTED -> connectedAddresses -= device.address
                }
            }
        }
        // Both are protected system broadcasts only the Bluetooth stack can send.
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }
        runCatching {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        }.onFailure { Log.w(TAG, "register connection receiver failed", it) }
        connectionReceiver = receiver
        connectionReceiverContext = context.applicationContext ?: context
    }

    private fun isMotobudsConnected(context: Context?): Boolean {
        if (connectedAddresses.isNotEmpty()) return true
        if (context == null) return false
        val bluetoothManager = runCatching {
            context.getSystemService(BluetoothManager::class.java)
        }.getOrNull() ?: return false
        return listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).any { profile ->
            runCatching { bluetoothManager.getConnectedDevices(profile) }
                .getOrNull()
                .orEmpty()
                .any(::isMotobudsPod)
        }
    }

    /** Any app process can resolve the current Application through ActivityThread. */
    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .apply { isAccessible = true }
            .invoke(null) as? Context
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun isMotobudsPod(device: BluetoothDevice): Boolean {
        val name = (device.name ?: device.alias)?.lowercase() ?: return false
        return (name.contains("moto") && name.contains("buds")) ||
            name.contains("guitar") ||
            name.contains("xt2443")
    }
}
