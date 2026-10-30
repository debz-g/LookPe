package dev.redfox.lookpe.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** "Today's collection: ₹4,560" at a time he picks; re-armed every day and after a reboot. */
object DailySummary {
    /** Hours offered in Settings (24h clock); null is off. */
    val CHOICES: List<Int?> = listOf(null, 19, 20, 21, 22)
    private const val DEFAULT_HOUR = 21
    private const val PREFS = "summary"
    private const val KEY_HOUR = "hour"
    private const val OFF = -1

    private val _hour = MutableStateFlow<Int?>(DEFAULT_HOUR)
    val hour: StateFlow<Int?> = _hour.asStateFlow()

    fun sync(context: Context) {
        val stored = prefs(context).getInt(KEY_HOUR, DEFAULT_HOUR)
        _hour.value = stored.takeIf { it != OFF }
    }

    fun setHour(context: Context, hour: Int?) {
        prefs(context).edit { putInt(KEY_HOUR, hour ?: OFF) }
        sync(context)
        schedule(context)
    }

    fun schedule(context: Context) {
        sync(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pendingIntent(context)
        val hour = _hour.value ?: return alarms.cancel(intent)
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(hour, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        }
    }

    suspend fun post(context: Context) {
        val payments = Repository.get(context).paymentsOn(LocalDate.now())
        RevealState.sync(context)
        Notifications.showDailySummary(
            context,
            totalPaise = payments.sumOf { it.amountPaise },
            count = payments.size,
            hideAmounts = RevealState.lockEnabled.value,
        )
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, DailySummaryReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailySummary.post(app)
            } finally {
                DailySummary.schedule(app) // tomorrow
                pending.finish()
            }
        }
    }
}
