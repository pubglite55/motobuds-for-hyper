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

    // Known Moto Buds BLE GATT layouts. The reverse-engineered protocol reference documents
    // fc9d0002 (write) / fc9d0003 (notify); the fc9d9ff0..ff3 set comes from the OppoPods
    // lineage and is kept as a fallback. Discovery below accepts either layout.
    private val SERVICE_UUIDS = listOf(
        "fc9d9fe0-4899-11ee-be56-0242ac120002",
        "fc9d0001-4899-11ee-be56-0242ac120002",
    ).map(java.util.UUID::fromString)
    private val WRITE_UUIDS = listOf(
        "fc9d0002-4899-11ee-be56-0242ac120002",
        "fc9d9ff0-4899-11ee-be56-0242ac120002",
    ).map(java.util.UUID::fromString)
    private val NOTIFY_UUIDS = listOf(
        "fc9d0003-4899-11ee-be56-0242ac120002",
        "fc9d9ff1-4899-11ee-be56-0242ac120002",
    ).map(java.util.UUID::fromString)
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
                if (notifyCharacteristic == null || it.uuid == notifyCharacteristic?.uuid) {
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
        if (gatt == null) {
            callback?.onError("GATT unavailable")
            return
        }
        bluetoothGatt = gatt

        var write: BluetoothGattCharacteristic? = null
        var notify: BluetoothGattCharacteristic? = null

        // Preferred UUID pairs first: documented Moto Buds layout, then the legacy set.
        serviceLoop@ for (serviceUuid in SERVICE_UUIDS) {
            val service = gatt.getService(serviceUuid) ?: continue@serviceLoop
            if (write == null) write = WRITE_UUIDS.firstNotNullOfOrNull { service.getCharacteristic(it) }
            if (notify == null) notify = NOTIFY_UUIDS.firstNotNullOfOrNull { service.getCharacteristic(it) }
        }

        // Fallback: any fc9d characteristic exposing write / notify properties.
        if (write == null || notify == null) {
            characteristicLoop@ for (service in gatt.services.orEmpty()) {
                for (characteristic in service.characteristics.orEmpty()) {
                    val uuid = characteristic.uuid.toString()
                    if (!uuid.startsWith("fc9d")) continue@characteristicLoop
                    val properties = characteristic.properties
                    val writable = properties and (
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                        ) != 0
                    val notifiable = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    if (write == null && writable && (uuid.endsWith("0002") || uuid.endsWith("ff0"))) {
                        write = characteristic
                    } else if (notify == null && notifiable && (uuid.endsWith("0003") || uuid.endsWith("ff1"))) {
                        notify = characteristic
                    }
                }
            }
        }

        if (write == null || notify == null) {
            Log.e(TAG, "Required characteristics not found")
            callback?.onError("Characteristics not found")
            return
        }

        writeCharacteristic = write
        notifyCharacteristic = notify
        Log.d(TAG, "Using write=" + write.uuid + " notify=" + notify.uuid)

        // Enable notifications
        gatt.setCharacteristicNotification(notify, true)
        notify.getDescriptor(CCCD_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

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
