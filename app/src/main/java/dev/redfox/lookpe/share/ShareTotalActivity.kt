package dev.redfox.lookpe.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Transparent activity behind the widget's share button: a widget can't pick a share target itself.
 * It reads today's total at the moment of the tap (not when the widget was last drawn), and does
 * nothing while amounts are hidden, so the lock can't be bypassed from the home screen.
 */
class ShareTotalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        RevealState.sync(applicationContext)
        if (RevealState.lockEnabled.value && !RevealState.revealed.value) {
            finish()
            return
        }
        lifecycleScope.launch {
            val today = LocalDate.now()
            val total = Repository.get(applicationContext).paymentsOn(today).sumOf { it.amountPaise }
            ShareTotal.send(this@ShareTotalActivity, ShareTotal.message(today, today, total))
            finish()
        }
    }
}
