package dev.redfox.lookpe.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.redfox.lookpe.notify.DailySummary
import dev.redfox.lookpe.service.PhonePeListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Clears the widget to the new day just after midnight; also re-armed on boot. */
object MidnightRefresh {
    fun schedule(context: Context) {
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 5_000
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, MidnightReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inexact is fine: a few minutes late at midnight doesn't matter, and needs no special permission.
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, pi)
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        MidnightRefresh.schedule(app)
        DailySummary.schedule(app) // alarms don't survive a reboot
        PhonePeListenerService.rebind(app)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                TodayWidget.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }
}
