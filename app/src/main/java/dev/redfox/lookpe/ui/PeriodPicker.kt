package dev.redfox.lookpe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.redfox.lookpe.R
import dev.redfox.lookpe.statement.StatementPeriod
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObSheet
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pickedFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** Which period is chosen, and the From / To dates for Custom. Survives rotation and process death. */
class PeriodState(period: StatementPeriod, from: LocalDate, to: LocalDate) {
    var period by mutableStateOf(period)
    var from by mutableStateOf(from)
    var to by mutableStateOf(to)

    val custom get() = from to to

    companion object {
        val Saver = listSaver<PeriodState, Long>(
            save = { listOf(it.period.ordinal.toLong(), it.from.toEpochDay(), it.to.toEpochDay()) },
            restore = {
                PeriodState(StatementPeriod.entries[it[0].toInt()], LocalDate.ofEpochDay(it[1]), LocalDate.ofEpochDay(it[2]))
            },
        )
    }
}

@Composable
fun rememberPeriodState(): PeriodState = rememberSaveable(saver = PeriodState.Saver) {
    val today = LocalDate.now()
    PeriodState(StatementPeriod.ThisMonth, today.withDayOfMonth(1), today)
}

/**
 * A scrolling row of period chips; choosing Custom adds From and To buttons that open a date picker.
 * Moving From past To (or To before From) drags the other date along, so the range is never backwards.
 */
@Composable
fun PeriodPicker(state: PeriodState, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    var picking by remember { mutableStateOf<String?>(null) } // "from" or "to"

    Column(modifier) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            StatementPeriod.entries.forEach { p ->
                val selected = p == state.period
                PillButton(
                    text = p.label,
                    onClick = { state.period = p },
                    contentColor = if (selected) colors.accentLight else colors.textPrimary,
                    containerColor = if (selected) colors.accentContainer else colors.tile,
                )
            }
        }
        if (state.period == StatementPeriod.Custom) {
            Spacer(Modifier.height(ObSpacing.M))
            Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), verticalAlignment = Alignment.CenterVertically) {
                Text("From", style = ObsidianTheme.typography.label, color = colors.textSecondary)
                PillButton(text = pickedFmt.format(state.from), onClick = { picking = "from" })
                Text("To", style = ObsidianTheme.typography.label, color = colors.textSecondary)
                PillButton(text = pickedFmt.format(state.to), onClick = { picking = "to" })
            }
        }
    }

    when (picking) {
        "from" -> DatePickerSheet(
            title = "From which day?",
            initial = state.from,
            onPick = { d ->
                state.from = d
                if (state.to < d) state.to = d
                picking = null
            },
            onDismiss = { picking = null },
        )
        "to" -> DatePickerSheet(
            title = "Until which day?",
            initial = state.to,
            onPick = { d ->
                state.to = d
                if (state.from > d) state.from = d
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** A month calendar in a sheet: arrows for month and year, tap a day to choose it. Days after today are greyed out. */
@Composable
fun DatePickerSheet(title: String, initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val thisMonth = YearMonth.from(today)
    val shape = RoundedCornerShape(10.dp)

    ObSheet(onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = ObSpacing.Gutter).padding(bottom = ObSpacing.XL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(
                    icon = R.drawable.ic_chevron_right,
                    contentDescription = "Previous month",
                    onClick = { month = month.minusMonths(1) },
                    modifier = Modifier.rotate(180f),
                )
                Text(
                    monthFmt.format(month),
                    style = type.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                HeaderIconButton(
                    icon = R.drawable.ic_chevron_right,
                    contentDescription = "Next month",
                    onClick = { if (month < thisMonth) month = month.plusMonths(1) },
                    tint = if (month < thisMonth) colors.textPrimary else colors.textDisabled,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S), modifier = Modifier.padding(vertical = ObSpacing.S)) {
                PillButton(text = "− 1 year", onClick = { month = month.minusYears(1) })
                if (month.plusYears(1) <= thisMonth) PillButton(text = "+ 1 year", onClick = { month = month.plusYears(1) })
            }
            Row {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = type.label, color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val cells = List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val future = date.isAfter(today)
                                    val chosen = date == initial
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(shape)
                                            .background(if (chosen) colors.accent else colors.cardInner)
                                            .pressable(enabled = !future, pressedScale = 0.9f) { onPick(date) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${date.dayOfMonth}",
                                            style = type.labelStrong,
                                            color = when {
                                                future -> colors.textDisabled
                                                chosen -> colors.onAccent
                                                else -> colors.textPrimary
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
