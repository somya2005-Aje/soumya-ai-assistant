package ai.soumya.assistant.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification assistant. Only active after the user grants Notification
 * Access in system settings. Reads accessible notifications so the assistant
 * can summarise them; never auto-replies or deletes without confirmation.
 */
class SoumyaNotificationListener : NotificationListenerService() {

    companion object {
        /** Latest notifications, newest first — kept in memory only. */
        val recent = ArrayDeque<NotificationSummary>(100)

        data class NotificationSummary(
            val packageName: String,
            val title: String?,
            val text: String?,
            val time: Long
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification: Notification = sbn?.notification ?: return
        val extras = notification.extras
        val summary = NotificationSummary(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            time = sbn.postTime
        )
        synchronized(recent) {
            recent.addFirst(summary)
            while (recent.size > 100) recent.removeLast()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Nothing to do — summaries are informational only.
    }
}
