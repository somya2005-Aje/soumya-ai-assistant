package ai.soumya.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ai.soumya.assistant.MainActivity
import ai.soumya.assistant.R
import java.util.concurrent.TimeUnit

/** Fires when an exact-alarm reminder triggers. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("reminder_text") ?: "Reminder"
        showReminderNotification(context, text)
    }
}

/** WorkManager fallback for reminders when exact alarms are unavailable. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val text = inputData.getString("reminder_text") ?: "Reminder"
        showReminderNotification(applicationContext, text)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, text: String, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("reminder_text" to text))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

private fun showReminderNotification(context: Context, text: String) {
    val channelId = "soumya_reminders"
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(channelId, "SOUMYA AI reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val openIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle("SOUMYA AI reminder")
        .setContentText(text)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .build()
    nm.notify(text.hashCode(), notification)
}
