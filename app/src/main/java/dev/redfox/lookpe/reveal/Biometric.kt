package dev.redfox.lookpe.reveal

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint / face, falling back to the phone's PIN or pattern so a wet finger never locks him out.
 * BIOMETRIC_WEAK (which includes strong) lets HyperOS's camera face unlock count as well.
 */
fun FragmentActivity.askForBiometric(
    onSuccess: () -> Unit,
    onFail: () -> Unit,
    title: String = "Show today's collection",
    subtitle: String = "Amounts hide again after ${RevealState.REVEAL_SECONDS} seconds",
) {
    // No fingerprint, face or screen lock set up at all: nothing to check against, so just show.
    if (BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
    )
}

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
