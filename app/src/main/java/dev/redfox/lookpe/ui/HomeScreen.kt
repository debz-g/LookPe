package dev.redfox.lookpe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.formatTime
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.service.PhonePeListenerService
import dev.redfox.lookpe.setup.readSetupStatus
import dev.redfox.obsidian.components.ActionPillButton
import dev.redfox.obsidian.components.LargeHeader
import dev.redfox.obsidian.components.LargeHeaderHeight
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.RollingText
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val dayTitle = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

@Composable
fun HomeScreen(
    day: LocalDate,
    onDayChange: (LocalDate) -> Unit,
    onReveal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { Repository.get(context) }

    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        RevealState.sync(context)
        PhonePeListenerService.rebind(context)
        onPauseOrDispose { }
    }

    val payments by remember(day) { repo.observeDay(day) }.collectAsState(emptyList())
    val previousDay by remember(day) { repo.observeDay(day.minusDays(1)) }.collectAsState(emptyList())
    var opened by remember { mutableStateOf<Payment?>(null) }
    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val visible = !lockEnabled || revealed
    val today = LocalDate.now()

    val list = rememberLazyListState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.glassSource().fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = top + LargeHeaderHeight + ObSpacing.S,
                bottom = bottom + ObSpacing.XXL,
            ),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.M),
        ) {
            if (!status.complete) {
                item { SetupBanner(onOpenSettings, Modifier.revealOnEnter(0)) }
            }
            item {
                DayBar(
                    day = day,
                    isToday = day == today,
                    onPrevious = { onDayChange(day.minusDays(1)) },
                    onNext = { if (day < today) onDayChange(day.plusDays(1)) },
                    onToday = { onDayChange(today) },
                    modifier = Modifier.revealOnEnter(1),
                )
            }
            item {
                TotalCard(
                    total = payments.sumOf { it.amountPaise },
                    count = payments.size,
                    previousTotal = previousDay.sumOf { it.amountPaise },
                    previousLabel = if (day == today) "yesterday" else "the day before",
                    visible = visible,
                    lockEnabled = lockEnabled,
                    onToggle = { if (revealed) scope.launch { RevealState.hide(context) } else onReveal() },
                    modifier = Modifier.revealOnEnter(2),
                )
            }
            if (payments.isEmpty()) {
                item {
                    EmptyState(
                        icon = R.drawable.ic_rupee,
                        title = "No payments yet",
                        subtitle = if (day == today) "Payments appear here as PhonePe Business notifies you."
                        else "Nothing was recorded on this day.",
                        modifier = Modifier.padding(vertical = ObSpacing.XL),
                    )
                }
            } else {
                item { SectionLabel("Payments", Modifier.padding(top = ObSpacing.S).revealOnEnter(3)) }
                item { PaymentsCard(payments, visible, onOpen = { opened = it }, modifier = Modifier.revealOnEnter(4)) }
            }
        }
        // Fixed frosted bar: the name on the left, actions on the right, content scrolling under it.
        LargeHeader(
            title = "LookPe",
            actions = {
                HeaderIconButton(icon = R.drawable.ic_search, contentDescription = "Search payments", onClick = onOpenSearch)
                HeaderIconButton(icon = R.drawable.ic_calendar, contentDescription = "History", onClick = onOpenHistory)
                HeaderIconButton(icon = R.drawable.ic_settings, contentDescription = "Settings", onClick = onOpenSettings)
            },
        )
    }
    opened?.let { PaymentDetailsSheet(it, visible, onDismiss = { opened = null }) }
}

/** Points to Settings while notification access or battery isn't set up; payments are missed until then. */
@Composable
private fun SetupBanner(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    ObCard(
        modifier.fillMaxWidth(),
        borderColor = colors.accentBorder,
        contentPadding = PaddingValues(ObSpacing.L),
        onClick = onOpenSettings,
    ) {
        ObListRow(
            title = "Finish setup",
            subtitle = "Payments are being missed",
            icon = R.drawable.ic_settings,
            iconTint = colors.accentLight,
            titleStyleLarge = true,
            trailing = {
                PillButton(
                    text = "Open",
                    onClick = onOpenSettings,
                    contentColor = colors.accentLight,
                    containerColor = colors.accentContainer,
                )
            },
        )
    }
}

@Composable
private fun DayBar(
    day: LocalDate,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ObsidianTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderIconButton(
            icon = R.drawable.ic_chevron_right,
            contentDescription = "Previous day",
            onClick = onPrevious,
            modifier = Modifier.rotate(180f),
        )
        Column(Modifier.weight(1f).padding(horizontal = ObSpacing.XS)) {
            Text(
                if (isToday) "Today" else dayTitle.format(day),
                style = ObsidianTheme.typography.title,
                color = colors.textPrimary,
            )
            if (isToday) {
                Text(dayTitle.format(day), style = ObsidianTheme.typography.body, color = colors.textSecondary)
            }
        }
        if (!isToday) {
            PillButton(text = "Today", onClick = onToday)
            Spacer(Modifier.width(ObSpacing.XS))
        }
        HeaderIconButton(
            icon = R.drawable.ic_chevron_right,
            contentDescription = "Next day",
            onClick = onNext,
            tint = if (isToday) colors.textDisabled else colors.textPrimary,
        )
    }
}

@Composable
private fun TotalCard(
    total: Long,
    count: Int,
    previousTotal: Long,
    previousLabel: String,
    visible: Boolean,
    lockEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.XL)) {
        Text("TOTAL COLLECTION", style = type.caption, color = colors.textTertiary)
        Spacer(Modifier.height(ObSpacing.S))
        if (visible) {
            RollingText(Money.format(total), style = type.numericLarge, color = colors.textPrimary)
        } else {
            Text(Money.MASK, style = type.numericLarge, color = colors.textSecondary)
        }
        Text(
            if (count == 1) "1 payment" else "$count payments",
            style = type.bodyLarge,
            color = colors.textSecondary,
        )
        if (visible && (total > 0 || previousTotal > 0)) {
            val diff = total - previousTotal
            Text(
                when {
                    diff > 0 -> "${Money.format(diff)} more than $previousLabel"
                    diff < 0 -> "${Money.format(-diff)} less than $previousLabel"
                    else -> "Same as $previousLabel"
                },
                style = type.body,
                color = if (diff >= 0) colors.accentLight else colors.textTertiary,
            )
        }
        if (lockEnabled) {
            Spacer(Modifier.height(ObSpacing.L))
            ActionPillButton(
                text = if (visible) "Hide amounts" else "Show amounts",
                onClick = onToggle,
                icon = painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                primary = !visible,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


