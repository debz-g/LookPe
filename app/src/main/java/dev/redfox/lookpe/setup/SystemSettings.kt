package dev.redfox.lookpe.setup

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dev.redfox.lookpe.notify.Notifications
import dev.redfox.lookpe.service.PhonePeListenerService
import dev.redfox.lookpe.widget.TodayWidgetReceiver

/** What the phone still needs before payments are logged reliably. */
data class SetupStatus(val listening: Boolean, val unrestrictedBattery: Boolean, val notifications: Boolean) {
    /** Payments are logged reliably. Notifications only matter for alerts and the summary. */
    val complete get() = listening && unrestrictedBattery
}

fun readSetupStatus(context: Context) = SetupStatus(
    listening = PhonePeListenerService.isEnabled(context),
    unrestrictedBattery = context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName),
    notifications = Notifications.allowed(context),
)

/** For when the permission prompt can no longer be shown (denied twice). */
fun openAppNotificationSettings(context: Context) {
    context.startFirst(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )
}

fun openNotificationAccess(context: Context) {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
        ComponentName(context, PhonePeListenerService::class.java).flattenToString(),
    )
    context.startFirst(detail, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

fun openAppInfo(context: Context) {
    context.startFirst(appDetails(context))
}

@SuppressLint("BatteryLife") // Sideloaded single-user app; reliable background reading is the whole point.
fun requestUnrestrictedBattery(context: Context) {
    context.startFirst(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
}

/** HyperOS's Autostart list; falls back to App info elsewhere. */
fun openAutostart(context: Context) {
    context.startFirst(
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        ),
        appDetails(context),
    )
}

fun pinWidget(context: Context) {
    val manager = context.getSystemService(AppWidgetManager::class.java)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, TodayWidgetReceiver::class.java), null, null)
    }
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

/** Starts the first intent the phone can handle; OEM settings screens move around between versions. */
private fun Context.startFirst(vararg intents: Intent) {
    for (intent in intents) {
        val ok = runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
}
