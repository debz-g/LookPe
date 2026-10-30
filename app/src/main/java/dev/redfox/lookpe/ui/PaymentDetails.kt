package dev.redfox.lookpe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.formatTime
import dev.redfox.obsidian.components.CopyPill
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.ObSheet
import dev.redfox.obsidian.platform.copyToClipboard
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/** A payment row: payer, time, amount. Tapping opens [PaymentDetailsSheet]. */
@Composable
fun PaymentRow(p: Payment, visible: Boolean, onClick: () -> Unit, subtitle: String = formatTime(p.receivedAt)) {
    val colors = ObsidianTheme.colors
    ObListRow(
        title = p.payer ?: "Unknown payer",
        subtitle = subtitle,
        icon = R.drawable.ic_rupee,
        iconTint = colors.accentLight,
        titleStyleLarge = true,
        onClick = onClick,
        modifier = Modifier.padding(vertical = ObSpacing.M),
        trailing = {
            Text(
                if (visible) Money.format(p.amountPaise) else "₹ •••",
                style = ObsidianTheme.typography.numericSmall,
                color = if (visible) colors.textPrimary else colors.textSecondary,
            )
        },
    )
}

/** A card of [PaymentRow]s with dividers. */
@Composable
fun PaymentsCard(
    payments: List<Payment>,
    visible: Boolean,
    onOpen: (Payment) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (Payment) -> String = { formatTime(it.receivedAt) },
) {
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = ObSpacing.L)) {
        payments.forEachIndexed { i, p ->
            if (i > 0) ObDivider()
            PaymentRow(p, visible, onClick = { onOpen(p) }, subtitle = subtitle(p))
        }
    }
}

/** Everything about one payment, with the txn id ready to copy for a customer who says "I paid". */
@Composable
fun PaymentDetailsSheet(p: Payment, visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val at = Instant.ofEpochMilli(p.receivedAt).atZone(ZoneId.systemDefault())

    ObSheet(onDismiss = onDismiss, title = "Payment") {
        Column(Modifier.padding(horizontal = ObSpacing.Gutter).padding(bottom = ObSpacing.XL)) {
            Text(
                if (visible) Money.format(p.amountPaise) else Money.MASK,
                style = type.numericLarge,
                color = if (visible) colors.textPrimary else colors.textSecondary,
            )
            Spacer(Modifier.height(ObSpacing.L))
            ObCard(Modifier.fillMaxWidth(), color = colors.cardInner) {
                Detail("From", p.payer ?: "Not in the notification")
                ObDivider(Modifier.padding(vertical = ObSpacing.M))
                Detail("When", "${detailDate.format(at)} · ${formatTime(p.receivedAt)}")
                ObDivider(Modifier.padding(vertical = ObSpacing.M))
                Text("Transaction ID", style = type.label, color = colors.textSecondary)
                if (p.txnId != null) {
                    // The pill shows the id itself ("Copy T2610…"), then "Copied".
                    Spacer(Modifier.height(ObSpacing.XS))
                    CopyPill(value = p.txnId, onCopy = { context.copyToClipboard("Transaction ID", it) })
                } else {
                    Text("PhonePe didn't include one", style = type.bodyLarge, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(ObSpacing.L))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp),
                )
                Text(
                    // The original notification, so a doubtful entry can be checked against it.
                    if (visible) p.raw.replace('\n', ' ') else "Original notification hidden",
                    style = type.body,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = ObSpacing.S),
                )
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Text(label, style = ObsidianTheme.typography.label, color = ObsidianTheme.colors.textSecondary)
    Text(value, style = ObsidianTheme.typography.bodyLarge, color = ObsidianTheme.colors.textPrimary)
}
