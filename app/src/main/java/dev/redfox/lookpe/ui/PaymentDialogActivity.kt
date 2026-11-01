package dev.redfox.lookpe.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * The widget's transaction dialog. A Glance widget can't show one, so tapping a payment row starts
 * this transparent activity and [PaymentDetailsDialog] draws the scrim and card itself.
 *
 * Read-only on purpose: amounts stay masked while the lock is on, exactly like the sheet in the app,
 * and closing it needs no biometric check.
 */
class PaymentDialogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_PAYMENT_ID, -1L)
        if (id < 0) {
            finish()
            return
        }
        RevealState.sync(applicationContext)
        setContent {
            ObsidianTheme(accent = LookPeAccent, neutrals = LookPeNeutrals) {
                PaymentDialogContent(id, onDismiss = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_PAYMENT_ID = "paymentId"
    }
}

@Composable
private fun PaymentDialogContent(id: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var payment by remember { mutableStateOf<Payment?>(null) }
    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()

    LaunchedEffect(id) {
        val found = Repository.get(context).payment(id)
        // Gone from the database (cleared history): nothing to show, so just close.
        if (found == null) onDismiss() else payment = found
    }

    payment?.let { PaymentDetailsDialog(it, visible = !lockEnabled || revealed, onDismiss = onDismiss) }
}
