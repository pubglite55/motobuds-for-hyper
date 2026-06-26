package moe.chenxy.oppopods.utils

import android.bluetooth.BluetoothDevice
import android.content.Intent
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams

/**
 * Utility functions for Bluetooth device operations.
 */
object DeviceUtils {

    /**
     * Check if a Bluetooth device is a MotoBuds earphone.
     *
     * @param device The Bluetooth device to check
     * @return true if the device is a MotoBuds earphone (name contains "moto buds", "guitar", or "xt2443")
     */
    fun isOppoPod(device: BluetoothDevice?): Boolean {
        val name = device?.name?.lowercase() ?: return false
        // Only match MotoBuds earphones, exclude speakers and other devices
        return (name.contains("moto") && name.contains("buds")) ||
               name.contains("guitar") ||
               name.contains("xt2443")
    }

    /**
     * Extract battery status from Intent extras.
     *
     * @param intent The Intent containing battery data
     * @return BatteryParams if valid data exists, null otherwise
     */
    fun batteryStatusFromExtras(intent: Intent): BatteryParams? {
        val leftBattery = intent.getIntExtra("left_battery", -1)
        val rightBattery = intent.getIntExtra("right_battery", -1)
        val caseBattery = intent.getIntExtra("case_battery", -1)
        if (leftBattery == -1 && rightBattery == -1 && caseBattery == -1) return null
        return BatteryParams(
            left = PodParams(
                battery = leftBattery.coerceAtLeast(0),
                isCharging = intent.getBooleanExtra("left_charging", false),
                isConnected = intent.getBooleanExtra("left_connected", false)
            ),
            right = PodParams(
                battery = rightBattery.coerceAtLeast(0),
                isCharging = intent.getBooleanExtra("right_charging", false),
                isConnected = intent.getBooleanExtra("right_connected", false)
            ),
            case = PodParams(
                battery = caseBattery.coerceAtLeast(0),
                isCharging = intent.getBooleanExtra("case_charging", false),
                isConnected = intent.getBooleanExtra("case_connected", false)
            )
        )
    }

    /**
     * Floor modulo operation that always returns a non-negative result.
     *
     * @param divisor The divisor
     * @return Floor modulo result
     */
    fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
