package moe.chenxy.oppopods.pods

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import moe.chenxy.oppopods.hook.Log

/**
 * BLE GATT controller for MotoBuds earphone communication.
 *
 * This controller manages the BLE GATT connection to MotoBuds earphones,
 * providing a low-level interface for sending commands and receiving notifications.
 *
 * UUIDs used:
 * - Service: fc9d9fe0-4899-11ee-be56-0242ac120002
 * - Write: fc9d9ff0-4899-11ee-be56-0242ac120002
 * - Notify: fc9d9ff1-4899-11ee-be56-0242ac120002
 * - Read: fc9d9ff2-4899-11ee-be56-0242ac120002
 * - Config: fc9d9ff3-4899-11ee-be56-0242ac120002
 *
 * Usage:
 * ```
 * BleGattController.connect(context, device, callback)
 * // Wait for onConnected callback
 * BleGattController.sendPacket(data)
 * ```
 */
@SuppressLint("MissingPermission")
object BleGattController {
    private const val TAG = "MotoBuds-BleGatt"

    // MotoBuds BLE GATT UUIDs
    private val SERVICE_UUID = java.util.UUID.fromString("fc9d9fe0-4899-11ee-be56-0242ac120002")
    private val WRITE_UUID = java.util.UUID.fromString("fc9d9ff0-4899-11ee-be56-0242ac120002")
    private val NOTIFY_UUID = java.util.UUID.fromString("fc9d9ff1-4899-11ee-be56-0242ac120002")
    private val READ_UUID = java.util.UUID.fromString("fc9d9ff2-4899-11ee-be56-0242ac120002")
    private val CONFIG_UUID = java.util.UUID.fromString("fc9d9ff3-4899-11ee-be56-0242ac120002")
    private val CCCD_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile var isConnected = false
        private set

    private var pendingPackets = mutableListOf<ByteArray>()
    private val packetQueue = mutableListOf<ByteArray>()

    interface BleGattCallback {
        fun onConnected()
        fun onDisconnected()
        fun onDataReceived(data: ByteArray)
        fun onError(error: String)
    }

    private var callback: BleGattCallback? = null

    fun connect(context: Context, device: BluetoothDevice, bleCallback: BleGattCallback) {
        callback = bleCallback
        Log.d(TAG, "Connecting to BLE GATT: ${device.name} (${device.address})")

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting BLE GATT")
        isConnected = false
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        callback = null
    }

    fun sendPacket(data: ByteArray) {
        if (!isConnected || writeCharacteristic == null) {
            Log.w(TAG, "Cannot send packet: not connected")
            return
        }

        scope.launch {
            try {
                writeCharacteristic?.let { char ->
                    char.value = data
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    bluetoothGatt?.writeCharacteristic(char)
                    Log.d(TAG, "Sent packet: ${data.joinToString(" ") { "%02X".format(it) }}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send packet failed", e)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "BLE connected, discovering services...")
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "BLE disconnected")
                    isConnected = false
                    callback?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                setupCharacteristics(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
                callback?.onError("Service discovery failed")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                if (it.uuid == NOTIFY_UUID) {
                    val data = it.value
                    Log.d(TAG, "Received data: ${data.joinToString(" ") { "%02X".format(it) }}")
                    callback?.onDataReceived(data)
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write success")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.let {
                    Log.d(TAG, "Characteristic read: ${it.value.joinToString(" ") { "%02X".format(it) }}")
                }
            }
        }
    }

    private fun setupCharacteristics(gatt: BluetoothGatt?) {
        gatt?.let { bluetoothGatt = it }

        val service = gatt?.getService(SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "MotoBuds service not found")
            callback?.onError("Service not found")
            return
        }

        writeCharacteristic = service.getCharacteristic(WRITE_UUID)
        notifyCharacteristic = service.getCharacteristic(NOTIFY_UUID)

        if (writeCharacteristic == null || notifyCharacteristic == null) {
            Log.e(TAG, "Required characteristics not found")
            callback?.onError("Characteristics not found")
            return
        }

        // Enable notifications
        gatt?.setCharacteristicNotification(notifyCharacteristic, true)
        val descriptor = notifyCharacteristic?.getDescriptor(CCCD_UUID)
        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt?.writeDescriptor(descriptor)

        isConnected = true
        Log.d(TAG, "BLE GATT ready")
        callback?.onConnected()

        // Process any pending packets
        synchronized(packetQueue) {
            pendingPackets.forEach { sendPacket(it) }
            pendingPackets.clear()
        }
    }

    fun sendPacketSafe(data: ByteArray) {
        if (isConnected) {
            sendPacket(data)
        } else {
            synchronized(packetQueue) {
                pendingPackets.add(data)
            }
        }
    }
}
