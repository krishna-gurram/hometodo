package dev.krishnagurram.hometodo.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.krishnagurram.hometodo.HomeTodoApp
import dev.krishnagurram.hometodo.MainActivity
import dev.krishnagurram.hometodo.R
import dev.krishnagurram.hometodo.domain.ChoreSchedule
import dev.krishnagurram.hometodo.domain.ReminderTime
import dev.krishnagurram.hometodo.domain.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One daily notification listing what's due. No server: Android's AlarmManager
 * wakes our ReminderReceiver at the time ReminderTime picks, the receiver reads
 * the local database, posts a notification if anything is due, then books
 * tomorrow's alarm. A chain of one-shot alarms, each scheduling the next.
 */
object Reminders {

    const val CHANNEL_ID = "daily"
    private const val NOTIFICATION_ID = 1

    /** Android 8+ requires a "channel"; it's what appears in the app's notification settings. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "What's due today, once a day"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Inexact alarm: Android may shift it by a few minutes to save battery. That's fine
     * for "trash tonight", and inexact alarms need no special permission.
     * Calling this again replaces the previous alarm (same PendingIntent), never stacks them.
     */
    fun scheduleNext(context: Context) {
        val next = ReminderTime.next(LocalDateTime.now())
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canPost() just above the notify call
    suspend fun postIfAnythingDue(context: Context) {
        if (!canPost(context)) return
        val db = (context.applicationContext as HomeTodoApp).db
        val today = LocalDate.now()
        val due = db.choreDao().openNow()
            .map { ChoreSchedule.classify(it, today) }
            .filter { it.section == Section.TODAY }
        if (due.isEmpty()) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val titles = due.map { it.chore.title }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (titles.size == 1) titles.first() else "${titles.size} things today")
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

/** Woken by the alarm. goAsync() buys a few seconds to read the database off the main thread. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.postIfAnythingDue(context)
            } finally {
                Reminders.scheduleNext(context)
                pending.finish()
            }
        }
    }
}

/** Alarms are wiped when the phone reboots or the app updates; this books the next one again. */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.scheduleNext(context)
        }
    }
}
