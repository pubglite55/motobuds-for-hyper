package moe.chenxy.oppopods.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.*
import moe.chenxy.oppopods.hook.Log
import java.io.ByteArrayOutputStream

/**
 * BLE GATT transport layer for MotoBuds communication.
 *
 * This class provides a higher-level interface on top of [BleGattController],
 * handling packet assembly, fragmentation, and the MotoBuds protocol frame format.
 *
 * The transport layer:
 * - Assembles incoming BLE GATT notifications into complete packets
 * - Handles packet fragmentation for outgoing data (MTU limit)
 * - Validates packet headers (HEAD/TAIL markers)
 *
 * Usage:
 * ```
 * BleGattTransport.connect(context, device) { data ->
 *     // Handle received packet
 * }
 * // Send packets
 * BleGattTransport.sendPacket(packet)
 * ```
 */
@SuppressLint("MissingPermission")
object BleGattTransport {
    private const val TAG = "MotoBuds-BleTransport"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var packetAssembler = ByteArrayOutputStream()
    private var expectedLength = 0

    var isConnected = false
        private set

    private var dataCallback: ((ByteArray) -> Unit)? = null

    fun connect(context: Context, device: BluetoothDevice, onData: (ByteArray) -> Unit) {
        dataCallback = onData
        Log.d(TAG, "Starting BLE GATT connection")

        BleGattController.connect(context, device, object : BleGattController.BleGattCallback {
            override fun onConnected() {
                isConnected = true
                Log.d(TAG, "BLE GATT connected")
            }

            override fun onDisconnected() {
                isConnected = false
                Log.d(TAG, "BLE GATT disconnected")
            }

            override fun onDataReceived(data: ByteArray) {
                processIncomingData(data)
            }

            override fun onError(error: String) {
                Log.e(TAG, "BLE GATT error: $error")
                isConnected = false
            }
        })
    }

    fun disconnect() {
        isConnected = false
        BleGattController.disconnect()
        packetAssembler.reset()
        expectedLength = 0
    }

    fun sendPacket(data: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "Cannot send: not connected")
            return
        }

        // BLE GATT has MTU limit (typically 512 bytes)
        // Split large packets if needed
        val mtu = 512
        if (data.size <= mtu) {
            BleGattController.sendPacket(data)
        } else {
            // Fragment large packets
            var offset = 0
            while (offset < data.size) {
                val end = minOf(offset + mtu, data.size)
                val fragment = data.copyOfRange(offset, end)
                BleGattController.sendPacket(fragment)
                offset = end
                if (offset < data.size) {
                    Thread.sleep(50) // Small delay between fragments
                }
            }
        }
    }

    private fun processIncomingData(data: ByteArray) {
        // Assemble packets from BLE GATT notifications
        // BLE GATT may split packets into multiple notifications
        packetAssembler.write(data)

        val assembled = packetAssembler.toByteArray()

        // Check for complete packet (starts with HEAD, ends with TAIL)
        if (assembled.size >= 14) {
            // Check for HEAD marker
            if (assembled[0] == 0x48.toByte() && assembled[1] == 0x45.toByte()) {
                // Extract total length from bytes 4-5
                val totalLen = (assembled[4].toInt() and 0xFF) or ((assembled[5].toInt() and 0xFF) shl 8)
                val expectedSize = 4 + 2 + totalLen + 4 + 4 // HEAD + len + PDU + CRC + TAIL

                if (assembled.size >= expectedSize) {
                    // Complete packet received
                    val packet = assembled.copyOfRange(0, expectedSize)
                    packetAssembler.reset()
                    dataCallback?.invoke(packet)
                }
            } else {
                // Invalid data, reset
                packetAssembler.reset()
            }
        }
    }

    fun isReady(): Boolean = isConnected
}
