package dev.redfox.lookpe.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.redfox.lookpe.notify.Notifications
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Notices when LookPe has stopped reading PhonePe notifications and tells him, instead of payments
 * silently going missing. The connected flag lives in memory on purpose: the listener runs in the
 * app's process, so if HyperOS kills the process, a fresh process starts with it false until the
 * system binds the listener again.
 */
object ListenerHealth {
    private const val WORK = "listener-health"

    @Volatile
    var connected = false
        private set

    fun markConnected(context: Context, value: Boolean) {
        connected = value
        if (value) Notifications.clearListenerProblem(context)
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ListenerHealthWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    /** Tries to recover first; alerts only if the listener still isn't running. */
    suspend fun check(context: Context) {
        if (!PhonePeListenerService.isEnabled(context)) {
            Notifications.showListenerProblem(context, accessOff = true)
            return
        }
        if (!connected) {
            PhonePeListenerService.rebind(context)
            delay(20_000)
        }
        if (connected) Notifications.clearListenerProblem(context)
        else Notifications.showListenerProblem(context, accessOff = false)
    }
}

class ListenerHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ListenerHealth.check(applicationContext)
        return Result.success()
    }
}
