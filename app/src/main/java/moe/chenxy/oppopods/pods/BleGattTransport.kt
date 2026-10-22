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

        // BLE notifications carry the compact frame (no HEAD/length/CRC/TAIL).
        val bleFrame = MotoBudsPackets.toBleFrame(data)

        // BLE GATT has MTU limit (typically 512 bytes); fragment if needed.
        val mtu = 512
        if (bleFrame.size <= mtu) {
            BleGattController.sendPacket(bleFrame)
        } else {
            var offset = 0
            while (offset < bleFrame.size) {
                val end = minOf(offset + mtu, bleFrame.size)
                BleGattController.sendPacket(bleFrame.copyOfRange(offset, end))
                offset = end
                if (offset < bleFrame.size) {
                    Thread.sleep(50)
                }
            }
        }
    }

    private fun processIncomingData(data: ByteArray) {
        // BLE GATT may split a frame across several notifications. Two layouts are accepted:
        //  - full SPP frame starting with "HEAD" (some ROMs forward the raw SPP bytes)
        //  - compact GATT frame: 8-byte core header + payload (documented Moto Buds BLE layout)
        packetAssembler.write(data)
        val assembled = packetAssembler.toByteArray()

        var consumed = 0
        while (consumed < assembled.size) {
            val available = assembled.size - consumed
            if (available < 8) break

            val isFullFrame = assembled[consumed] == 0x48.toByte() && assembled[consumed + 1] == 0x45.toByte()
            if (isFullFrame) {
                if (available < 6) break
                val totalLen = (assembled[consumed + 4].toInt() and 0xFF) or
                    ((assembled[consumed + 5].toInt() and 0xFF) shl 8)
                if (totalLen < 8) break
                val expectedSize = 6 + totalLen + 8 // HEAD + len + PDU + CRC + TAIL
                if (available < expectedSize) break
                dataCallback?.invoke(assembled.copyOfRange(consumed, consumed + expectedSize))
                consumed += expectedSize
            } else {
                val payloadLen = (assembled[consumed + 4].toInt() and 0xFF) or
                    ((assembled[consumed + 5].toInt() and 0xFF) shl 8)
                val expectedSize = 8 + payloadLen
                if (available < expectedSize) break
                val core = assembled.copyOfRange(consumed, consumed + expectedSize)
                MotoBudsPackets.fromBleFrame(core)?.let { dataCallback?.invoke(it) }
                consumed += expectedSize
            }
        }

        packetAssembler.reset()
        if (consumed < assembled.size) {
            packetAssembler.write(assembled, consumed, assembled.size - consumed)
        }
    }

    fun isReady(): Boolean = isConnected
}
