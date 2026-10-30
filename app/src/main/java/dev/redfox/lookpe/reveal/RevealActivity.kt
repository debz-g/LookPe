package dev.redfox.lookpe.reveal

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.redfox.lookpe.data.refreshWidgets
import kotlinx.coroutines.launch

/**
 * Transparent activity behind the widget's eye button: widgets can't show a biometric prompt
 * themselves. Hiding goes through here too, rather than a Glance ActionCallback broadcast, because
 * the activity route is the one that reliably reaches the app on HyperOS.
 */
class RevealActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        RevealState.sync(applicationContext)
        val hide = intent.getStringExtra(EXTRA_ACTION) == ACTION_HIDE
        if (hide || RevealState.revealed.value || !RevealState.lockEnabled.value) {
            // Hide, or the widget was out of date (already shown / lock turned off): just bring it up to date.
            lifecycleScope.launch {
                if (hide) RevealState.hide(applicationContext) else refreshWidgets(applicationContext)
                finish()
            }
            return
        }
        askForBiometric(
            onSuccess = {
                lifecycleScope.launch {
                    RevealState.reveal(applicationContext)
                    finish()
                }
            },
            onFail = { finish() },
        )
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_HIDE = "hide"
        const val ACTION_SHOW = "show"
    }
}
