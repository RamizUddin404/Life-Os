package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.Locale

class AlarmReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_TRIGGER_ALARM = "com.example.lifeos.ACTION_TRIGGER_ALARM"
        const val ACTION_TASK_REMINDER = "com.example.lifeos.ACTION_TASK_REMINDER"
        const val ACTION_DISMISS_ALARM = "com.example.lifeos.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.lifeos.ACTION_SNOOZE_ALARM"

        const val EXTRA_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_alarm_title"
        const val EXTRA_MESSAGE = "extra_alarm_message"
        const val EXTRA_IS_SOUND = "extra_is_sound"
        const val EXTRA_IS_VIBRATE = "extra_is_vibrate"
        const val EXTRA_PRIORITY = "extra_priority"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive action: $action")

        val id = intent.getLongExtra(EXTRA_ID, System.currentTimeMillis())
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder Alert"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "You have a scheduled activity."
        val isSound = intent.getBooleanExtra(EXTRA_IS_SOUND, true)
        val isVibrate = intent.getBooleanExtra(EXTRA_IS_VIBRATE, true)
        val priority = intent.getStringExtra(EXTRA_PRIORITY) ?: "MEDIUM"

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Device rebooted. Rescheduling active custom alarms...")
                AlarmReminderManager.initNotificationChannels(context)
                val alarms = AlarmReminderManager.getSavedCustomAlarms(context)
                for (a in alarms) {
                    if (a.isEnabled) {
                        AlarmReminderManager.scheduleCustomAlarm(context, a)
                    }
                }
            }

            ACTION_DISMISS_ALARM -> {
                Log.d(TAG, "Dismiss action clicked for alarm $id")
                AlarmReminderManager.stopAlarmSound()
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(id.toInt())
            }

            ACTION_SNOOZE_ALARM -> {
                Log.d(TAG, "Snooze action clicked for alarm $id")
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(id.toInt())
                AlarmReminderManager.snoozeAlarm(context, id, title, message, isSound, snoozeMinutes = 10)
            }

            ACTION_TRIGGER_ALARM -> {
                // Play alarm sound and vibrate
                if (isSound) {
                    AlarmReminderManager.playAlarmSound(context)
                }
                if (isVibrate) {
                    AlarmReminderManager.triggerVibration(context)
                }
                showAlarmNotification(context, id, title, message, isSound, isLoudAlarm = true)
            }

            ACTION_TASK_REMINDER -> {
                if (isSound) {
                    AlarmReminderManager.playAlarmSound(context)
                }
                if (isVibrate) {
                    AlarmReminderManager.triggerVibration(context)
                }
                showAlarmNotification(context, id, title, message, isSound, isLoudAlarm = isSound, priority = priority)
            }
        }
    }

    private fun showAlarmNotification(
        context: Context,
        id: Long,
        title: String,
        message: String,
        isSound: Boolean,
        isLoudAlarm: Boolean,
        priority: String = "HIGH"
    ) {
        try {
            AlarmReminderManager.initNotificationChannels(context)

            // Content intent to open app
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("NAVIGATE_TO", "tasks")
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                id.toInt(),
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            // Dismiss action
            val dismissIntent = Intent(context, AlarmReminderReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
                putExtra(EXTRA_ID, id)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                id.toInt() + 10000,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            // Snooze action
            val snoozeIntent = Intent(context, AlarmReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_IS_SOUND, isSound)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                id.toInt() + 20000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val channelId = if (isLoudAlarm) AlarmReminderManager.CHANNEL_ALARM_ID else AlarmReminderManager.CHANNEL_REMINDER_ID

            val accentColor = when (priority.uppercase(Locale.getDefault())) {
                "HIGH" -> 0xFFFF5252.toInt()
                "MEDIUM" -> 0xFFFFB142.toInt()
                else -> 0xFF6C5CE7.toInt()
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(if (isLoudAlarm) "⏰ ALARM: $title" else "📌 Reminder: $title")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nLifeOS Smart Alarm & Productivity Reminder"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(if (isLoudAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
                .setColor(accentColor)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setOngoing(isLoudAlarm)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
                .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 10m", snoozePendingIntent)

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(id.toInt().coerceAtLeast(1001), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException showing notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }
}
