package dev.redfox.lookpe.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.statement.Statement
import dev.redfox.lookpe.statement.StatementPeriod
import dev.redfox.lookpe.statement.Statements
import dev.redfox.obsidian.components.ActionPillButton
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObSnackbarHost
import dev.redfox.obsidian.components.ObSnackbarState
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.components.SegmentOption
import dev.redfox.obsidian.components.SegmentedPill
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * Pick a period and save a bank-style PDF statement to Downloads. [requireUnlock] runs its argument
 * straight away when amounts are visible, otherwise after the fingerprint / PIN check, because the
 * PDF lists every amount.
 */
@Composable
fun StatementScreen(onBack: () -> Unit, requireUnlock: (onUnlocked: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val snackbars = remember { ObSnackbarState() }

    var periodIndex by rememberSaveable { mutableStateOf(0) }
    val period = StatementPeriod.entries[periodIndex]
    var saving by remember { mutableStateOf(false) }
    var savedUri by remember(period) { mutableStateOf<Uri?>(null) }

    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val visible = !lockEnabled || revealed

    val statement: Statement? by produceState<Statement?>(null, period) {
        value = null
        value = Statements.build(context, period)
    }

    fun download() {
        val st = statement ?: return
        requireUnlock {
            scope.launch {
                saving = true
                val message = try {
                    val saved = Statements.save(context, st)
                    savedUri = saved.uri
                    "Saved ${saved.name} in Downloads"
                } catch (e: Exception) {
                    "Couldn't save the statement: ${e.message}"
                } finally {
                    saving = false
                }
                snackbars.show(message)
            }
        }
    }

    fun open(uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, Statements.MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbars.show("No PDF viewer found. Open the file from the Downloads folder.") }
        }
    }

    fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(Statements.MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "LookPe payment statement")
            .putExtra(Intent.EXTRA_TITLE, "LookPe payment statement")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("LookPe statement", uri) // lets the chooser and the target read the file
        context.startActivity(Intent.createChooser(send, "Share statement"))
    }

    DetailPage(title = "Statement", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
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
                    ObCard(Modifier.fillMaxWidth().revealOnEnter(0), contentPadding = PaddingValues(ObSpacing.L)) {
                        Text("Download a statement", style = type.bodyLarge, color = colors.textPrimary)
                        Text(
                            "A PDF like a bank statement: totals, day by day, and every payment with its running total. " +
                                "It is saved in your Downloads folder.",
                            style = type.body,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(ObSpacing.M))
                        SegmentedPill(
                            options = StatementPeriod.entries.map { SegmentOption(it.label) },
                            selectedIndex = periodIndex,
                            onSelect = { periodIndex = it },
                        )
                    }
                }
                item { SectionLabel("This statement", Modifier.padding(top = ObSpacing.S).revealOnEnter(1)) }
                item {
                    ObCard(Modifier.fillMaxWidth().revealOnEnter(2), contentPadding = PaddingValues(ObSpacing.L)) {
                        val st = statement
                        if (st == null) {
                            Text("Getting ready…", style = type.body, color = colors.textSecondary)
                        } else {
                            Text(
                                "${dateFmt.format(st.from)} to ${dateFmt.format(st.to)}",
                                style = type.bodyLarge,
                                color = colors.textPrimary,
                            )
                            Text(plural(st.count, "payment"), style = type.body, color = colors.textSecondary)
                            if (st.count > 0) {
                                Spacer(Modifier.height(ObSpacing.S))
                                Text(
                                    if (visible) Money.format(st.totalPaise) else Money.MASK,
                                    style = type.numericLarge,
                                    color = if (visible) colors.textPrimary else colors.textSecondary,
                                )
                            }
                            Spacer(Modifier.height(ObSpacing.L))
                            if (st.count == 0) {
                                Text("No payments in this period yet.", style = type.body, color = colors.textTertiary)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    ActionPillButton(
                                        text = if (saving) "Saving…" else "Download PDF",
                                        onClick = { download() },
                                        icon = painterResource(R.drawable.ic_download),
                                        primary = true,
                                        enabled = !saving,
                                    )
                                    savedUri?.let { uri ->
                                        PillButton(
                                            text = "Share",
                                            onClick = { share(uri) },
                                            contentColor = colors.accentLight,
                                            containerColor = colors.accentContainer,
                                        )
                                        PillButton(
                                            text = "Open",
                                            onClick = { open(uri) },
                                            contentColor = colors.accentLight,
                                            containerColor = colors.accentContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ObSnackbarHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter),
                bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
        }
    }
}
