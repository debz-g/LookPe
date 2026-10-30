package dev.redfox.lookpe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Month calendar with each day's total, the month total, and week by week. Tapping a day opens it. */
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    val thisMonth = YearMonth.now()
    var monthIndex by rememberSaveable { mutableLongStateOf(thisMonth.year * 12L + thisMonth.monthValue - 1) }
    val month = YearMonth.of((monthIndex / 12).toInt(), (monthIndex % 12).toInt() + 1)
    val payments by remember(month) { repo.observeRange(month.atDay(1), month.atEndOfMonth()) }.collectAsState(emptyList())
    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val visible = !lockEnabled || revealed

    val zone = ZoneId.systemDefault()
    val perDay: Map<LocalDate, List<Payment>> =
        payments.groupBy { Instant.ofEpochMilli(it.receivedAt).atZone(zone).toLocalDate() }
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography

    DetailPage(title = "History", onBack = onBack) { padding ->
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.revealOnEnter(0)) {
                    HeaderIconButton(
                        icon = R.drawable.ic_chevron_right,
                        contentDescription = "Previous month",
                        onClick = { monthIndex-- },
                        modifier = Modifier.rotate(180f),
                    )
                    Text(
                        monthTitle.format(month),
                        style = type.title,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderIconButton(
                        icon = R.drawable.ic_chevron_right,
                        contentDescription = "Next month",
                        onClick = { if (month < thisMonth) monthIndex++ },
                        tint = if (month < thisMonth) colors.textPrimary else colors.textDisabled,
                    )
                }
            }
            item {
                ObCard(Modifier.fillMaxWidth().revealOnEnter(1), contentPadding = PaddingValues(ObSpacing.XL)) {
                    Text("MONTH TOTAL", style = type.caption, color = colors.textTertiary)
                    Spacer(Modifier.height(ObSpacing.S))
                    Text(
                        if (visible) Money.format(payments.sumOf { it.amountPaise }) else Money.MASK,
                        style = type.numericLarge,
                        color = if (visible) colors.textPrimary else colors.textSecondary,
                    )
                    val days = perDay.size
                    Text(
                        "${plural(payments.size, "payment")} on ${plural(days, "day")}",
                        style = type.bodyLarge,
                        color = colors.textSecondary,
                    )
                    val best = perDay.maxByOrNull { (_, ps) -> ps.sumOf { it.amountPaise } }
                    if (visible && best != null) {
                        Text(
                            "Best day: ${shortDay.format(best.key)}, ${Money.format(best.value.sumOf { it.amountPaise })}",
                            style = type.body,
                            color = colors.accentLight,
                        )
                    }
                }
            }
            item {
                ObCard(Modifier.fillMaxWidth().revealOnEnter(2), contentPadding = PaddingValues(ObSpacing.M)) {
                    MonthGrid(month, perDay, visible, onOpenDay)
                }
            }
            item { SectionLabel("Week by week", Modifier.padding(top = ObSpacing.S).revealOnEnter(3)) }
            item {
                ObCard(Modifier.fillMaxWidth().revealOnEnter(4), contentPadding = PaddingValues(horizontal = ObSpacing.L)) {
                    val weeks = weeksOf(month)
                    weeks.forEachIndexed { i, (from, to) ->
                        if (i > 0) ObDivider()
                        val inWeek = perDay.filterKeys { it in from..to }.values.flatten()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = ObSpacing.M),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${shortDay.format(from)} – ${shortDay.format(to)}", style = type.bodyLarge, color = colors.textPrimary)
                                Text(plural(inWeek.size, "payment"), style = type.body, color = colors.textSecondary)
                            }
                            Text(
                                if (visible) Money.format(inWeek.sumOf { it.amountPaise }) else "₹ •••",
                                style = type.numericSmall,
                                color = if (visible) colors.textPrimary else colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, perDay: Map<LocalDate, List<Payment>>, visible: Boolean, onOpenDay: (LocalDate) -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val today = LocalDate.now()
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1 // Monday first
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = type.label, color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) { if (date != null) DayCell(date, perDay[date].orEmpty(), visible, today, onOpenDay) }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, payments: List<Payment>, visible: Boolean, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val future = date.isAfter(today)
    val has = payments.isNotEmpty()
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(shape)
            .background(if (has) colors.accentContainer else colors.cardInner)
            .then(if (date == today) Modifier.border(1.dp, colors.accentBorder, shape) else Modifier)
            .pressable(enabled = !future, pressedScale = 0.9f) { onOpenDay(date) }
            .padding(vertical = ObSpacing.XS),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = type.labelStrong,
            color = when {
                future -> colors.textDisabled
                has -> colors.accentLight
                else -> colors.textSecondary
            },
        )
        if (has) {
            Text(
                if (visible) Money.compact(payments.sumOf { it.amountPaise }) else "•••",
                style = type.caption,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** Monday–Sunday weeks overlapping [month], clipped to the month. */
private fun weeksOf(month: YearMonth): List<Pair<LocalDate, LocalDate>> {
    val weeks = mutableListOf<Pair<LocalDate, LocalDate>>()
    var start = month.atDay(1)
    while (!start.isAfter(month.atEndOfMonth())) {
        val end = minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), month.atEndOfMonth())
        weeks += start to end
        start = end.plusDays(1)
    }
    return weeks.reversed() // newest first
}

internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
