package dev.redfox.lookpe.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.redfox.lookpe.BuildConfig
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.data.refreshWidgets
import dev.redfox.lookpe.parse.PaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Receives every posted notification once the user grants Notification access; keeps PhonePe Business payments. */
class PhonePeListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        ListenerHealth.markConnected(applicationContext, true)
        // Pick up anything still in the shade, e.g. payments that arrived while HyperOS had us stopped.
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        scope.launch {
            var added = false
            for (sbn in active) added = handle(sbn) || added
            if (added) refreshWidgets(applicationContext)
        }
    }

    override fun onListenerDisconnected() {
        ListenerHealth.markConnected(applicationContext, false)
        // Ask the system to bind us again instead of staying dead until the next reboot.
        requestRebind(ComponentName(this, PhonePeListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isSource(sbn.packageName)) return
        scope.launch {
            if (handle(sbn)) refreshWidgets(applicationContext)
        }
    }

    private suspend fun handle(sbn: StatusBarNotification): Boolean {
        if (!isSource(sbn.packageName)) return false
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        val parsed = PaymentParser.parse(title, text)
        val repo = Repository.get(applicationContext)
        if (parsed == null) {
            Log.d(TAG, "Ignored PhonePe notification: $title / $text")
            // Only real PhonePe Business notifications go in the Skipped list, never test or other apps' posts.
            if (sbn.packageName == PHONEPE_BUSINESS) repo.recordSkipped(sbn.key, sbn.postTime, title, text)
            return false
        }
        return repo.record(parsed, sbn.key, sbn.postTime, raw = "$title\n$text")
    }

    // Debug builds also accept `adb shell cmd notification post`, so the flow can be tested without PhonePe.
    private fun isSource(pkg: String) =
        pkg == PHONEPE_BUSINESS || (BuildConfig.DEBUG && pkg == "com.android.shell")

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PHONEPE_BUSINESS = "com.phonepe.app.business"
        private const val TAG = "LookPe"

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, PhonePeListenerService::class.java)
            return context.getSystemService(android.app.NotificationManager::class.java)
                .isNotificationListenerAccessGranted(me)
        }

        /** Nudges a listener that HyperOS unbound without telling us. */
        fun rebind(context: Context) {
            if (isEnabled(context)) {
                requestRebind(ComponentName(context, PhonePeListenerService::class.java))
            }
        }
    }
}
