package dev.redfox.lookpe.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dev.redfox.lookpe.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The share button beside the total: sends one line ("Today's collection (Sun, 8 Nov): ₹12,450") to
 * WhatsApp, where the user then picks who gets it. Nothing but that line leaves the phone.
 */
object ShareTotal {
    /** WhatsApp and WhatsApp Business. Both are listed in the manifest's <queries> so we can see them. */
    internal val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

    fun message(day: LocalDate, today: LocalDate, totalPaise: Long): String {
        val amount = Money.format(totalPaise)
        val date = dayFormat.format(day)
        return if (day == today) "Today's collection ($date): $amount" else "Collection on $date: $amount"
    }

    /**
     * What to launch for [installed] WhatsApp packages: straight into the one that's there, a short
     * chooser of the two when both are, and the ordinary share sheet when neither is.
     */
    internal fun buildIntent(text: String, installed: List<String>): Intent {
        fun send(pkg: String? = null) = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            pkg?.let { setPackage(it) }
        }
        return when (installed.size) {
            0 -> Intent.createChooser(send(), null)
            1 -> send(installed.first())
            else -> Intent.createChooser(send(installed.first()), null).apply {
                putExtra(Intent.EXTRA_ALTERNATE_INTENTS, installed.drop(1).map { send(it) }.toTypedArray())
            }
        }
    }

    /** Opens WhatsApp (or the share sheet if it isn't installed) with [text] ready to send. */
    fun send(context: Context, text: String) {
        val installed = WHATSAPP.filter { context.packageManager.getLaunchIntentForPackage(it) != null }
        val intent = buildIntent(text, installed).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                buildIntent(text, emptyList()).apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }
}
