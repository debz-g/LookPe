package dev.redfox.lookpe.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.redfox.lookpe.MainActivity
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.ui.LookPeColors
import androidx.compose.ui.graphics.toArgb

/** LookPe's own notifications: the "stopped reading" alert and the end-of-day summary. */
object Notifications {
    private const val CHANNEL_ALERTS = "alerts"
    private const val CHANNEL_SUMMARY = "summary"
    private const val ID_LISTENER = 1
    private const val ID_SUMMARY = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Problems", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When LookPe stops reading PhonePe payments"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Daily summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Today's total at the time you choose"
            }
        )
    }

    fun allowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showListenerProblem(context: Context, accessOff: Boolean) {
        post(
            context, ID_LISTENER,
            base(context, CHANNEL_ALERTS)
                .setContentTitle(if (accessOff) "LookPe can't see PhonePe notifications" else "LookPe stopped reading payments")
                .setContentText(
                    if (accessOff) "Notification access was turned off. Tap to turn it back on."
                    else "HyperOS stopped it in the background. Tap to start it again."
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    fun clearListenerProblem(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_LISTENER)
    }

    /** With hiding on, the lock screen shows only that the summary is ready, not the amount. */
    fun showDailySummary(context: Context, totalPaise: Long, count: Int, hideAmounts: Boolean) {
        val title = if (count == 0) "No PhonePe payments today" else "Today's collection: ${Money.format(totalPaise)}"
        val text = when (count) {
            0 -> "If that's wrong, open LookPe to check it's set up."
            1 -> "1 payment. Tap to see it."
            else -> "$count payments. Tap to see them."
        }
        val builder = base(context, CHANNEL_SUMMARY).setContentTitle(title).setContentText(text)
        if (hideAmounts) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(base(context, CHANNEL_SUMMARY).setContentTitle("Today's collection is ready").build())
        }
        post(context, ID_SUMMARY, builder.build())
    }

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_rupee)
        .setColor(LookPeColors.accent.toArgb())
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!allowed(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
