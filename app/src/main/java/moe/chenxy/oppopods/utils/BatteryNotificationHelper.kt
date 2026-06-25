package moe.chenxy.oppopods.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.xiuxiu391.motobuds.R

object BatteryNotificationHelper {
    private const val CHANNEL_ID = "low_battery"
    private const val CHANNEL_NAME = "Low Battery Alert"
    private const val NOTIFICATION_ID = 10001
    private const val LOW_BATTERY_THRESHOLD = 20

    private var lastLeftNotified = -1
    private var lastRightNotified = -1
    private var lastCaseNotified = -1

    fun checkAndNotify(context: Context, leftBattery: Int, rightBattery: Int, caseBattery: Int) {
        val prefs = context.getSharedPreferences("motobuds_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("low_battery_reminder_enabled", true)) return

        createNotificationChannel(context)

        val needsNotification = (leftBattery in 1 until LOW_BATTERY_THRESHOLD && leftBattery != lastLeftNotified) ||
                (rightBattery in 1 until LOW_BATTERY_THRESHOLD && rightBattery != lastRightNotified) ||
                (caseBattery in 1 until LOW_BATTERY_THRESHOLD && caseBattery != lastCaseNotified)

        if (!needsNotification) return

        val parts = mutableListOf<String>()
        if (leftBattery in 1 until LOW_BATTERY_THRESHOLD) {
            parts.add("${context.getString(R.string.batt_left_pod)} ${leftBattery}%")
            lastLeftNotified = leftBattery
        }
        if (rightBattery in 1 until LOW_BATTERY_THRESHOLD) {
            parts.add("${context.getString(R.string.batt_right_pod)} ${rightBattery}%")
            lastRightNotified = rightBattery
        }
        if (caseBattery in 1 until LOW_BATTERY_THRESHOLD) {
            parts.add("${context.getString(R.string.pod_case)} ${caseBattery}%")
            lastCaseNotified = caseBattery
        }

        if (parts.isEmpty()) return

        val title = context.getString(R.string.low_battery_title)
        val content = parts.joinToString(" | ")

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun resetNotificationState() {
        lastLeftNotified = -1
        lastRightNotified = -1
        lastCaseNotified = -1
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alert when earphone battery is low"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
