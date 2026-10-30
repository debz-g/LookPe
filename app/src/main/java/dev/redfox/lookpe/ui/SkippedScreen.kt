package dev.redfox.lookpe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.data.SkippedNotification
import dev.redfox.lookpe.formatTime
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.ObAlertDialog
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * PhonePe Business notifications LookPe didn't count. Mostly offers and settlements; a real
 * payment here means PhonePe changed its wording and the parser needs updating.
 */
@Composable
fun SkippedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repository.get(context) }
    val skipped by remember { repo.observeSkipped() }.collectAsState(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography

    DetailPage(
        title = "Skipped notifications",
        onBack = onBack,
        actions = {
            if (skipped.isNotEmpty()) PillButton(text = "Clear", onClick = { confirmClear = true })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.glassSource().fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + ObSpacing.XXL,
            ),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.M),
        ) {
            item {
                Text(
                    "PhonePe Business notifications LookPe didn't count as a payment, like offers and settlements. " +
                        "If a real payment shows up here, PhonePe changed its wording: send a screenshot so LookPe " +
                        "can be updated. After updating, these are checked again automatically.",
                    style = type.body,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = ObSpacing.XXS),
                )
            }
            if (skipped.isEmpty()) {
                item {
                    Box(Modifier.padding(vertical = ObSpacing.XXL)) {
                        EmptyState(R.drawable.ic_check_bold, "Nothing skipped", "Every PhonePe notification so far was understood.")
                    }
                }
            }
            itemsIndexed(skipped, key = { _, n -> n.id }) { i, n -> SkippedCard(n, Modifier.revealOnEnter(i + 1)) }
        }
    }

    if (confirmClear) {
        ObAlertDialog(
            title = "Clear the list?",
            text = "This only clears this list. It doesn't change any payments.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClear = false
                scope.launch { repo.clearSkipped() }
            },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun SkippedCard(n: SkippedNotification, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val suspicious = looksLikePayment(n)
    ObCard(
        modifier.fillMaxWidth(),
        borderColor = if (suspicious) colors.warning.copy(alpha = 0.5f) else null,
        contentPadding = PaddingValues(ObSpacing.L),
    ) {
        Text(
            "${dayLabel(Instant.ofEpochMilli(n.postedAt).atZone(ZoneId.systemDefault()).toLocalDate())} · ${formatTime(n.postedAt)}" +
                if (suspicious) " · Looks like a payment" else "",
            style = type.label,
            color = if (suspicious) colors.warning else colors.textTertiary,
        )
        Spacer(Modifier.height(ObSpacing.XS))
        if (!n.title.isNullOrBlank()) Text(n.title, style = type.bodyLarge, color = colors.textPrimary)
        if (!n.text.isNullOrBlank()) Text(n.text, style = type.body, color = colors.textSecondary)
    }
}

private val paymentWords = Regex("""\b(received|credited|paid you)\b""", RegexOption.IGNORE_CASE)

/** Flags entries worth a second look: they mention receiving money but didn't parse. */
private fun looksLikePayment(n: SkippedNotification): Boolean {
    val text = "${n.title} ${n.text}"
    return paymentWords.containsMatchIn(text) && (text.contains("Rs", ignoreCase = true) || text.contains('₹'))
}
