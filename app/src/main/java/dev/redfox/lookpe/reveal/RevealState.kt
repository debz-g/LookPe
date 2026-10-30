package dev.redfox.lookpe.reveal

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.redfox.lookpe.data.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Whether amounts are visible. Shared by the widget and the app. With the lock on (the default),
 * amounts show only after a fingerprint/face/PIN check and hide again on their own [REVEAL_SECONDS]
 * later. With the lock off they always show.
 */
object RevealState {
    const val REVEAL_SECONDS = 60L
    private const val PREFS = "reveal"
    private const val KEY_UNTIL = "revealedUntil"
    private const val KEY_LOCK = "lockEnabled"

    private val _revealed = MutableStateFlow(false)
    /** Unlocked by a recent biometric check. Says nothing about the lock being off; see [lockEnabled]. */
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    private val _lockEnabled = MutableStateFlow(true)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    /** Re-reads the stored state; call before showing anything. */
    fun sync(context: Context) {
        val prefs = prefs(context)
        _lockEnabled.value = prefs.getBoolean(KEY_LOCK, true)
        _revealed.value = System.currentTimeMillis() < prefs.getLong(KEY_UNTIL, 0)
    }

    /** Turning the lock off should be confirmed with a biometric check by the caller first. */
    suspend fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK, enabled).apply()
        _lockEnabled.value = enabled
        hide(context)
    }

    suspend fun reveal(context: Context) {
        val until = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(REVEAL_SECONDS)
        prefs(context).edit().putLong(KEY_UNTIL, until).apply()
        _revealed.value = true
        // An exact alarm, not WorkManager: HyperOS freezes the process and defers jobs by minutes.
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, hideIntent(context))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, hideIntent(context))
        }
        refreshWidgets(context)
    }

    suspend fun hide(context: Context) {
        prefs(context).edit().putLong(KEY_UNTIL, 0).apply()
        _revealed.value = false
        context.getSystemService(AlarmManager::class.java).cancel(hideIntent(context))
        refreshWidgets(context)
    }

    private fun hideIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, HideReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class HideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                RevealState.hide(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
