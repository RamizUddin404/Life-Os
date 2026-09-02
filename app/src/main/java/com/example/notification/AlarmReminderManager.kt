package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CustomAlarmItem(
    val id: Long = System.currentTimeMillis(),
    val label: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val isSound: Boolean = true,
    val isVibrate: Boolean = true,
    val days: List<Int> = emptyList() // Calendar.MONDAY .. Calendar.SUNDAY or empty for once
) {
    val formattedTime: String
        get() {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
        }
}

object AlarmReminderManager {
    private const val TAG = "AlarmReminderMgr"

    const val CHANNEL_ALARM_ID = "lifeos_alarm_channel_high"
    const val CHANNEL_REMINDER_ID = "lifeos_task_reminders"

    private const val PREFS_NAME = "lifeos_alarms_prefs"
    private const val KEY_CUSTOM_ALARMS = "custom_alarms_json"

    private var activeRingtone: Ringtone? = null

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Loud Alarm Channel
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_ID,
                "LifeOS Active Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority audible ringing alarms for tasks, study sessions and wake-up alerts"
                enableLights(true)
                enableVibration(true)
                setSound(alarmSoundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            }
            notificationManager.createNotificationChannel(alarmChannel)

            // 2. Task & Productivity Reminder Channel
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER_ID,
                "Task & Productivity Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Intelligent notifications for upcoming tasks, deadlines, and daily reviews"
                enableLights(true)
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    /**
     * Schedule an exact Task Reminder with AlarmManager
     */
    fun scheduleTaskReminder(
        context: Context,
        taskId: Long,
        title: String,
        message: String,
        reminderTimeMillis: Long,
        isSoundAlarm: Boolean = false,
        priority: String = "MEDIUM"
    ) {
        if (reminderTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Reminder time is in past, skipping alarm schedule for task $taskId")
            return
        }

        initNotificationChannels(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TASK_REMINDER
            putExtra(AlarmReminderReceiver.EXTRA_ID, taskId)
            putExtra(AlarmReminderReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(AlarmReminderReceiver.EXTRA_IS_SOUND, isSoundAlarm)
            putExtra(AlarmReminderReceiver.EXTRA_PRIORITY, priority)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val showIntent = Intent(context, MainActivity::class.java)
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    taskId.toInt() + 50000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(reminderTimeMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            }
            Log.d(TAG, "Successfully scheduled task alarm for task $taskId at $reminderTimeMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing exact alarm permission: ${e.message}, falling back to setWindow")
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling task reminder: ${e.message}", e)
        }
    }

    /**
     * Cancel an active task reminder alarm
     */
    fun cancelTaskReminder(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TASK_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task $taskId")
        }
    }

    /**
     * Schedule a Custom Alarm
     */
    fun scheduleCustomAlarm(context: Context, alarm: CustomAlarmItem) {
        if (!alarm.isEnabled) return
        initNotificationChannels(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If time is before now, schedule for tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val triggerTime = cal.timeInMillis

        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReminderReceiver.EXTRA_ID, alarm.id)
            putExtra(AlarmReminderReceiver.EXTRA_TITLE, alarm.label)
            putExtra(AlarmReminderReceiver.EXTRA_MESSAGE, "Time to wake up & focus: ${alarm.formattedTime}")
            putExtra(AlarmReminderReceiver.EXTRA_IS_SOUND, alarm.isSound)
            putExtra(AlarmReminderReceiver.EXTRA_IS_VIBRATE, alarm.isVibrate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            val showIntent = Intent(context, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                alarm.id.toInt() + 100000,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled custom alarm ${alarm.label} at $triggerTime")
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Cancel a custom alarm
     */
    fun cancelCustomAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Snooze an alarm by X minutes (default 10)
     */
    fun snoozeAlarm(context: Context, id: Long, title: String, message: String, isSound: Boolean, snoozeMinutes: Int = 10) {
        stopAlarmSound()
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReminderReceiver.EXTRA_ID, id)
            putExtra(AlarmReminderReceiver.EXTRA_TITLE, "Snoozed: $title")
            putExtra(AlarmReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(AlarmReminderReceiver.EXTRA_IS_SOUND, isSound)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    /**
     * Trigger immediate test alarm to verify audio and notifications
     */
    fun testAlarmNow(context: Context, isSound: Boolean = true) {
        initNotificationChannels(context)
        val intent = Intent(context, AlarmReminderReceiver::class.java).apply {
            action = AlarmReminderReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReminderReceiver.EXTRA_ID, 999999L)
            putExtra(AlarmReminderReceiver.EXTRA_TITLE, "🔔 Test Alarm & Notification")
            putExtra(AlarmReminderReceiver.EXTRA_MESSAGE, "Alarm audio, vibration & notifications are working perfectly!")
            putExtra(AlarmReminderReceiver.EXTRA_IS_SOUND, isSound)
            putExtra(AlarmReminderReceiver.EXTRA_IS_VIBRATE, true)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Play alarm sound
     */
    fun playAlarmSound(context: Context) {
        try {
            stopAlarmSound()
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            activeRingtone = ringtone
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing alarm ringtone: ${e.message}")
        }
    }

    /**
     * Stop active alarm sound
     */
    fun stopAlarmSound() {
        try {
            activeRingtone?.stop()
            activeRingtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone: ${e.message}")
        }
    }

    /**
     * Trigger vibration
     */
    fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 800), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 300, 800), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 600, 300, 600, 300, 800), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    // Persistence of Custom Alarms
    fun getSavedCustomAlarms(context: Context): List<CustomAlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_ALARMS, null) ?: return defaultAlarms()
        return try {
            val list = mutableListOf<CustomAlarmItem>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomAlarmItem(
                        id = obj.getLong("id"),
                        label = obj.getString("label"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        isEnabled = obj.getBoolean("isEnabled"),
                        isSound = obj.optBoolean("isSound", true),
                        isVibrate = obj.optBoolean("isVibrate", true)
                    )
                )
            }
            list
        } catch (e: Exception) {
            defaultAlarms()
        }
    }

    fun saveCustomAlarms(context: Context, alarms: List<CustomAlarmItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (a in alarms) {
            val obj = JSONObject().apply {
                put("id", a.id)
                put("label", a.label)
                put("hour", a.hour)
                put("minute", a.minute)
                put("isEnabled", a.isEnabled)
                put("isSound", a.isSound)
                put("isVibrate", a.isVibrate)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_ALARMS, array.toString()).apply()
    }

    fun addCustomAlarm(context: Context, alarm: CustomAlarmItem) {
        val current = getSavedCustomAlarms(context).toMutableList()
        current.removeAll { it.id == alarm.id }
        current.add(alarm)
        saveCustomAlarms(context, current)
        if (alarm.isEnabled) {
            scheduleCustomAlarm(context, alarm)
        }
    }

    fun toggleCustomAlarm(context: Context, alarmId: Long, isEnabled: Boolean) {
        val current = getSavedCustomAlarms(context).map {
            if (it.id == alarmId) it.copy(isEnabled = isEnabled) else it
        }
        saveCustomAlarms(context, current)
        val target = current.find { it.id == alarmId }
        if (target != null) {
            if (isEnabled) {
                scheduleCustomAlarm(context, target)
            } else {
                cancelCustomAlarm(context, alarmId)
            }
        }
    }

    fun deleteCustomAlarm(context: Context, alarmId: Long) {
        cancelCustomAlarm(context, alarmId)
        val current = getSavedCustomAlarms(context).filterNot { it.id == alarmId }
        saveCustomAlarms(context, current)
    }

    private fun defaultAlarms(): List<CustomAlarmItem> {
        return listOf(
            CustomAlarmItem(id = 101, label = "Morning Focus Routine", hour = 7, minute = 0, isEnabled = true),
            CustomAlarmItem(id = 102, label = "Deep Study & Work Block", hour = 14, minute = 0, isEnabled = false),
            CustomAlarmItem(id = 103, label = "Night Review & Journal", hour = 21, minute = 30, isEnabled = true)
        )
    }
}
