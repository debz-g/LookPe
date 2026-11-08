package dev.redfox.lookpe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.statement.Statement
import dev.redfox.lookpe.statement.StatementPeriod
import dev.redfox.lookpe.statement.Statements
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.SegmentOption
import dev.redfox.obsidian.components.SegmentedPill
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val lastFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Who paid the most in a period: payers ranked by total, with how many payments and when they last paid. */
@Composable
fun CustomersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    val period = StatementPeriod.entries[periodIndex]

    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val visible = !lockEnabled || revealed

    val statement: Statement? by produceState<Statement?>(null, period) {
        value = null
        value = Statements.build(context, period)
    }

    DetailPage(title = "Top customers", onBack = onBack) { padding ->
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
                SegmentedPill(
                    options = StatementPeriod.entries.map { SegmentOption(it.label) },
                    selectedIndex = periodIndex,
                    onSelect = { periodIndex = it },
                    modifier = Modifier.revealOnEnter(0),
                )
            }
            val st = statement
            if (st != null && st.customers.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_person,
                        title = "No customers yet",
                        subtitle = "Named payers in this period show up here.",
                        modifier = Modifier.padding(vertical = ObSpacing.XL),
                    )
                }
            }
            if (st != null && st.customers.isNotEmpty()) {
                item {
                    ObCard(Modifier.fillMaxWidth().revealOnEnter(1), contentPadding = PaddingValues(horizontal = ObSpacing.L)) {
                        st.customers.forEachIndexed { i, c ->
                            if (i > 0) ObDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = ObSpacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${i + 1}",
                                    style = type.labelStrong,
                                    color = if (i < 3) colors.accentLight else colors.textTertiary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = type.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${plural(c.count, "payment")} · last ${lastFmt.format(Instant.ofEpochMilli(c.lastAt).atZone(ZoneId.systemDefault()))}",
                                        style = type.body,
                                        color = colors.textSecondary,
                                    )
                                }
                                Spacer(Modifier.width(ObSpacing.S))
                                Text(
                                    if (visible) Money.format(c.totalPaise) else "₹ •••",
                                    style = type.numericSmall,
                                    color = if (visible) colors.textPrimary else colors.textSecondary,
                                )
                            }
                        }
                    }
                }
                if (st.unnamedCount > 0) {
                    item {
                        Text(
                            "${plural(st.unnamedCount, "payment")} had no payer name and ${if (st.unnamedCount == 1) "isn't" else "aren't"} listed.",
                            style = type.body,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}
