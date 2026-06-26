package moe.chenxy.oppopods.hook

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import com.xiuxiu391.motobuds.BuildConfig
import moe.chenxy.oppopods.pods.RfcommController
import moe.chenxy.oppopods.utils.SystemApisUtils.setIconVisibility
import moe.chenxy.oppopods.utils.miuiStrongToast.data.OppoPodsAction

object HeadsetStateDispatcher : HookContext() {
    private var appRequestReceiverRegistered = false

    override fun onHook() {
        // Register receiver immediately when module loads
        // The Bluetooth process context should be available via the Xposed module context
        runCatching {
            // Try to get context from the current process
            val context = module?.javaClass?.let { null } // XposedModule doesn't expose context directly
            Log.d("OppoPods", "HeadsetStateDispatcher.onHook called")
        }

        runCatching {
            hookAfter(findMethod("com.android.bluetooth.btservice.AdapterService", "onCreate")) {
                val context = instance as? Context
                if (context != null) {
                    registerAppRequestReceiver(context)
                    checkAndConnectExistingDevice(context)
                }
            }
        }.onFailure {
            Log.w("OppoPods", "AdapterService.onCreate hook skipped", it)
        }

        hookAfter(findMethodByParamCount("com.android.bluetooth.a2dp.A2dpService", "handleConnectionStateChanged", 3)) {
            val currState = args[2] as Int
            val fromState = args[1] as Int
            val device = args[0] as BluetoothDevice?
            val handler = getObjectField(instance, "mHandler") as Handler
            if (device == null || currState == fromState) {
                return@hookAfter
            }
            handler.post {
                Log.d("OppoPods", "A2DP Connection State: $currState, isOppoPod ${isOppoPod(device)}")
                val context = instance as ContextWrapper
                registerAppRequestReceiver(context)
                if (!isOppoPod(device)) return@post

                val statusBarManager = context.getSystemService("statusbar") as StatusBarManager
                if (currState == BluetoothHeadset.STATE_CONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", true)
                    RfcommController.connectPod(context, device, prefs)
                } else if (currState == BluetoothHeadset.STATE_DISCONNECTING || currState == BluetoothHeadset.STATE_DISCONNECTED) {
                    statusBarManager.setIconVisibility("wireless_headset", false)
                    RfcommController.disconnectedPod(context, device)
                }
            }
        }
    }

    private fun registerAppRequestReceiver(context: Context?) {
        if (context == null || appRequestReceiverRegistered) return
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (context == null) return
                when (intent?.action) {
                    OppoPodsAction.ACTION_PODS_UI_INIT,
                    OppoPodsAction.ACTION_REFRESH_STATUS -> {
                        context.sendBroadcast(Intent(OppoPodsAction.ACTION_MODULE_BLUETOOTH_SERVICE_ALIVE).apply {
                            setPackage(BuildConfig.APPLICATION_ID)
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        })
                        // Check for existing connections on each UI init
                        checkAndConnectExistingDevice(context)
                    }
                    OppoPodsAction.ACTION_CONNECT_POD_REQUEST -> {
                        val device = intent.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        Log.d("OppoPods", "connect request from app device=${device.name}/${device.address}")
                        RfcommController.connectPod(context, device, prefs, appRequested = true)
                    }
                    OppoPodsAction.ACTION_DISCONNECT_POD_REQUEST -> {
                        val device = intent.getParcelableExtra("device", BluetoothDevice::class.java) ?: return
                        Log.d("OppoPods", "disconnect request from app device=${device.name}/${device.address}")
                        RfcommController.disconnectedPod(context, device)
                    }
                }
            }
        }, IntentFilter().apply {
            addAction(OppoPodsAction.ACTION_PODS_UI_INIT)
            addAction(OppoPodsAction.ACTION_REFRESH_STATUS)
            addAction(OppoPodsAction.ACTION_CONNECT_POD_REQUEST)
            addAction(OppoPodsAction.ACTION_DISCONNECT_POD_REQUEST)
        }, Context.RECEIVER_EXPORTED)
        appRequestReceiverRegistered = true
    }

    @SuppressLint("MissingPermission")
    private fun checkAndConnectExistingDevice(context: Context) {
        // If already connected to a device, skip
        if (RfcommController.currentStatusSnapshot().connected) return

        try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager?.adapter ?: return

            // Get connected A2DP devices
            adapter.getProfileProxy(context, object : android.bluetooth.BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                    val devices = proxy.connectedDevices
                    adapter.closeProfileProxy(profile, proxy)

                    // Check if any connected device is a MotoBuds
                    for (device in devices) {
                        if (isOppoPod(device)) {
                            Log.d("OppoPods", "Found existing MotoBuds connection: ${device.name}, initiating BLE GATT")
                            RfcommController.connectPod(context, device, prefs)
                            return
                        }
                    }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, android.bluetooth.BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.e("OppoPods", "Failed to check existing connections", e)
        }
    }

    /**
     * Detect Moto Buds earphones by checking if the device name contains "moto" (case insensitive).
     */
    @SuppressLint("MissingPermission")
    fun isOppoPod(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase() ?: return false
        // Only match MotoBuds earphones, exclude speakers and other devices
        return (name.contains("moto") && name.contains("buds")) ||
               name.contains("guitar") ||
               name.contains("xt2443")
    }

    /**
     * Register receiver and check for existing connections.
     * Called from HookEntry when module loads.
     */
    @SuppressLint("MissingPermission")
    fun registerReceiverAndCheckConnections(context: Context) {
        Log.d("OppoPods", "registerReceiverAndCheckConnections called")
        registerAppRequestReceiver(context)

        // Check for existing MotoBuds connections
        try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager?.adapter ?: return

            adapter.getProfileProxy(context, object : android.bluetooth.BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                    val devices = proxy.connectedDevices
                    adapter.closeProfileProxy(profile, proxy)

                    for (device in devices) {
                        if (isOppoPod(device)) {
                            Log.d("OppoPods", "Found existing MotoBuds connection: ${device.name}, initiating BLE GATT")
                            RfcommController.connectPod(context, device, prefs)
                            return
                        }
                    }
                    Log.d("OppoPods", "No existing MotoBuds connection found")
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, android.bluetooth.BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.e("OppoPods", "Failed to check existing connections", e)
        }
    }
}
