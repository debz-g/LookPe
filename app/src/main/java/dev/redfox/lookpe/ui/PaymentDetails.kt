package dev.redfox.lookpe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.fragment.app.FragmentActivity
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.data.refreshWidgets
import dev.redfox.lookpe.reveal.askForBiometric
import dev.redfox.obsidian.components.ActionPillButton
import dev.redfox.obsidian.components.ObTextField
import dev.redfox.obsidian.components.PillButton
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.popIn
import dev.redfox.obsidian.platform.copyToClipboard
import dev.redfox.obsidian.theme.ObShapes
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

private enum class SheetMode { View, Edit, ConfirmDelete }

/**
 * Everything about one payment, with the txn id ready to copy for a customer who says "I paid".
 * Also where a payment is corrected or deleted, in case PhonePe sends something the parser misreads.
 * Saving, restoring and deleting all need the same fingerprint / PIN check as showing amounts.
 */
@Composable
fun PaymentDetailsSheet(p: Payment, visible: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ObsidianTheme.colors
    var mode by remember { mutableStateOf(SheetMode.View) }

    /** Runs [action] after the biometric check, then closes the sheet. */
    fun confirmed(title: String, subtitle: String, action: suspend (Repository) -> Unit) {
        val activity = context as? FragmentActivity ?: return
        activity.askForBiometric(
            onSuccess = {
                scope.launch {
                    action(Repository.get(context))
                    refreshWidgets(context)
                    onDismiss()
                }
            },
            onFail = {},
            title = title,
            subtitle = subtitle,
        )
    }

    ObSheet(onDismiss = onDismiss, title = if (mode == SheetMode.Edit) "Edit payment" else "Payment") {
        val pad = Modifier.padding(horizontal = ObSpacing.Gutter).padding(bottom = ObSpacing.XL)
        when (mode) {
            SheetMode.View -> Column(pad) {
                PaymentDetailsBody(p = p, visible = visible)
                if (visible) {
                    Spacer(Modifier.height(ObSpacing.L))
                    Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                        PillButton(
                            text = "Edit",
                            onClick = { mode = SheetMode.Edit },
                            contentColor = colors.accentLight,
                            containerColor = colors.accentContainer,
                        )
                        if (p.edited && p.originalAmountPaise != null) {
                            PillButton(
                                text = "Restore original",
                                onClick = {
                                    confirmed("Restore original", "Back to what PhonePe sent") { it.restoreOriginal(p.id) }
                                },
                            )
                        }
                        PillButton(text = "Delete", onClick = { mode = SheetMode.ConfirmDelete }, contentColor = colors.error)
                    }
                }
            }
            SheetMode.Edit -> EditForm(
                p = p,
                modifier = pad,
                onCancel = { mode = SheetMode.View },
                onSave = { paise, payer ->
                    confirmed("Save changes", "Confirm it's you to change this payment") { it.edit(p.id, paise, payer) }
                },
            )
            SheetMode.ConfirmDelete -> Column(pad) {
                Text("Delete this payment?", style = ObsidianTheme.typography.title, color = colors.textPrimary)
                Text(
                    "${Money.format(p.amountPaise)} from ${p.payer ?: "unknown payer"} will no longer count in totals, " +
                        "history or statements.",
                    style = ObsidianTheme.typography.body,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(ObSpacing.L))
                Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                    PillButton(text = "Keep it", onClick = { mode = SheetMode.View })
                    PillButton(
                        text = "Delete",
                        onClick = { confirmed("Delete payment", "Confirm it's you to delete this payment") { it.delete(p.id) } },
                        contentColor = colors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditForm(p: Payment, onCancel: () -> Unit, onSave: (paise: Long, payer: String?) -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    var amount by remember { mutableStateOf(Money.editable(p.amountPaise)) }
    var payer by remember { mutableStateOf(p.payer.orEmpty()) }
    val paise = Money.parse(amount)
    val newPayer = payer.trim().ifEmpty { null }
    val changed = paise != p.amountPaise || newPayer != p.payer

    Column(modifier) {
        Text(
            "Use this only if LookPe logged a payment wrongly. The original notification is kept below.",
            style = type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(ObSpacing.L))
        ObTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount (₹)",
            isError = paise == null,
            supportingText = if (paise == null) "Enter an amount like 250 or 250.50" else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(ObSpacing.M))
        ObTextField(
            value = payer,
            onValueChange = { payer = it },
            label = "From",
            placeholder = "Not in the notification",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(ObSpacing.M))
        Text("Original notification", style = type.label, color = colors.textSecondary)
        Text(p.raw.replace('\n', ' '), style = type.body, color = colors.textTertiary)
        Spacer(Modifier.height(ObSpacing.L))
        Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), verticalAlignment = Alignment.CenterVertically) {
            ActionPillButton(
                text = "Save",
                onClick = { if (paise != null) onSave(paise, newPayer) },
                primary = true,
                enabled = paise != null && changed,
            )
            PillButton(text = "Cancel", onClick = onCancel)
        }
        Spacer(Modifier.height(ObSpacing.S))
        Text("Saving asks for your fingerprint or PIN.", style = type.body, color = colors.textTertiary)
    }
}

/**
 * The same details as a centred dialog over the home screen, for the widget. A Glance widget can't
 * host a dialog, so the tap starts [PaymentDialogActivity], whose transparent window draws this.
 * Tapping the scrim or pressing back closes it.
 */
@Composable
fun PaymentDetailsDialog(p: Payment, visible: Boolean, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pressable(pressedScale = 1f, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        ObCard(
            modifier = Modifier
                .padding(ObSpacing.Gutter)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Swallow taps on the card itself, so they don't fall through to the scrim and dismiss.
                .pointerInput(Unit) { detectTapGestures { } }
                .popIn(),
            shape = ObShapes.CardLarge,
        ) {
            PaymentDetailsBody(p, visible)
        }
    }
}

/** The shared body: everything about one payment, without the sheet's page chrome. */
@Composable
fun PaymentDetailsBody(p: Payment, visible: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val at = Instant.ofEpochMilli(p.receivedAt).atZone(ZoneId.systemDefault())

    Column(modifier) {
        Text(
            if (visible) Money.format(p.amountPaise) else Money.MASK,
            style = type.numericLarge,
            color = if (visible) colors.textPrimary else colors.textSecondary,
        )
        if (visible && p.edited) {
            Text(
                "Edited" + (p.originalAmountPaise?.let { " · was ${Money.format(it)}" } ?: ""),
                style = type.body,
                color = colors.warning,
            )
        }
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

@Composable
private fun Detail(label: String, value: String) {
    Text(label, style = ObsidianTheme.typography.label, color = ObsidianTheme.colors.textSecondary)
    Text(value, style = ObsidianTheme.typography.bodyLarge, color = ObsidianTheme.colors.textPrimary)
}
