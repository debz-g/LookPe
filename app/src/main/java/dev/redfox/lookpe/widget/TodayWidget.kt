package dev.redfox.lookpe.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.redfox.lookpe.MainActivity
import dev.redfox.lookpe.Money
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.formatTime
import dev.redfox.lookpe.reveal.RevealActivity
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.service.PhonePeListenerService
import dev.redfox.lookpe.ui.PaymentDialogActivity
import dev.redfox.lookpe.ui.LookPeColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayWidget : GlanceAppWidget() {

    // Lay out for the exact size the user dragged it to.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = Repository.get(context)
        val today = LocalDate.now()
        val initial = repo.paymentsOn(today)
        RevealState.sync(context)
        val listening = PhonePeListenerService.isEnabled(context)

        provideContent {
            // remember: a new Flow on every recomposition would restart collection and loop.
            val paymentsFlow = remember(today) { repo.observeDay(today) }
            val payments by paymentsFlow.collectAsState(initial)
            val revealed by RevealState.revealed.collectAsState()
            val lockEnabled by RevealState.lockEnabled.collectAsState()
            Content(
                WidgetState(
                    day = today,
                    payments = payments,
                    lockEnabled = lockEnabled,
                    visible = !lockEnabled || revealed,
                    listening = listening,
                )
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = TodayWidget().updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightRefresh.schedule(context)
    }
}

internal data class WidgetState(
    val day: LocalDate,
    val payments: List<Payment>,
    /** Show the eye button at all. */
    val lockEnabled: Boolean,
    /** Amounts readable: lock off, or recently unlocked. */
    val visible: Boolean,
    val listening: Boolean,
) {
    val total get() = payments.sumOf { it.amountPaise }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/** Obsidian tokens for Glance, which can't read ObsidianTheme. */
private object Ob {
    private val c = LookPeColors
    val background = ColorProvider(c.background)
    val card = ColorProvider(c.card)
    val tile = ColorProvider(c.tile)
    val textPrimary = ColorProvider(c.textPrimary)
    val textSecondary = ColorProvider(c.textSecondary)
    val accentLight = ColorProvider(c.accentLight)
    val accentContainer = ColorProvider(c.accentContainer)
    val error = ColorProvider(c.error)
}

/**
 * Three layouts by height, so nothing gets cut off however it's resized:
 *  - compact (one row of cells): the total, plus the eye button when the lock is on
 *  - summary: date, total, payment count
 *  - full: summary plus the list of payments
 */
@Composable
internal fun Content(state: WidgetState) {
    val size = LocalSize.current
    val pad = if (size.height < 110.dp || size.width < 160.dp) 12.dp else 18.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Ob.background)
            .cornerRadius(24.dp)
            .padding(pad),
        contentAlignment = Alignment.CenterStart,
    ) {
        when {
            size.height < 110.dp -> Compact(state)
            size.height < 190.dp || !state.listening -> Summary(state)
            else -> Full(state)
        }
    }
}

@Composable
private fun Compact(state: WidgetState) {
    val size = LocalSize.current
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Total(
            state,
            fontSize = amountSize(size.width, compact = true),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        if (state.lockEnabled) {
            Spacer(GlanceModifier.width(8.dp))
            EyeButton(state.visible, size = if (size.height < 70.dp) 36.dp else 44.dp)
        }
    }
}

@Composable
private fun Summary(state: WidgetState) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(state)
        if (!state.listening) {
            Spacer(GlanceModifier.height(8.dp))
            NotListening()
        }
    }
}

@Composable
private fun Full(state: WidgetState) {
    Column(GlanceModifier.fillMaxSize()) {
        Header(state)
        Spacer(GlanceModifier.height(14.dp))
        if (state.payments.isEmpty()) {
            Text(
                "No payments yet today",
                style = TextStyle(color = Ob.textSecondary, fontSize = 14.sp),
                modifier = GlanceModifier.padding(horizontal = 4.dp),
            )
        } else {
            val showTime = LocalSize.current.width >= 230.dp
            LazyColumn(modifier = GlanceModifier.fillMaxSize().background(Ob.card).cornerRadius(16.dp)) {
                items(state.payments, itemId = { it.id }) { p ->
                    PaymentRow(p, state.visible, showTime)
                }
            }
        }
    }
}

@Composable
private fun Header(state: WidgetState) {
    val size = LocalSize.current
    val count = state.payments.size
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(
                if (size.width >= 200.dp) "TODAY · ${dayFormat.format(state.day).uppercase()}" else "TODAY",
                style = TextStyle(color = Ob.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Total(state, fontSize = amountSize(size.width, compact = false))
            Text(
                when (count) {
                    0 -> "No payments yet"
                    1 -> "1 payment"
                    else -> "$count payments"
                },
                style = TextStyle(color = Ob.textSecondary, fontSize = 13.sp),
                maxLines = 1,
            )
        }
        if (state.lockEnabled) {
            Spacer(GlanceModifier.width(8.dp))
            EyeButton(state.visible, size = 48.dp)
        }
    }
}

@Composable
private fun Total(state: WidgetState, fontSize: TextUnit, modifier: GlanceModifier = GlanceModifier) {
    Text(
        if (state.visible) Money.format(state.total) else Money.MASK,
        style = TextStyle(
            color = if (state.visible) Ob.textPrimary else Ob.textSecondary,
            fontSize = fontSize,
            fontFamily = FontFamily("sans-serif-light"),
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

/** Shows (after a biometric check) or hides. Both go through RevealActivity; see its note. */
@Composable
private fun EyeButton(visible: Boolean, size: Dp) {
    val action: Action = actionStartActivity<RevealActivity>(
        actionParametersOf(
            ActionParameters.Key<String>(RevealActivity.EXTRA_ACTION) to
                if (visible) RevealActivity.ACTION_HIDE else RevealActivity.ACTION_SHOW
        )
    )
    Image(
        provider = ImageProvider(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
        contentDescription = if (visible) "Hide amounts" else "Show amounts",
        colorFilter = ColorFilter.tint(if (visible) Ob.textPrimary else Ob.accentLight),
        modifier = GlanceModifier
            .size(size)
            .background(if (visible) Ob.tile else Ob.accentContainer)
            .cornerRadius(size / 2)
            .padding(size / 4)
            .clickable(action),
    )
}

@Composable
private fun NotListening() {
    Text(
        "Not reading PhonePe notifications. Tap to fix.",
        style = TextStyle(color = Ob.error, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
    )
}

@Composable
private fun PaymentRow(p: Payment, visible: Boolean, showTime: Boolean) {
    // Widgets can't show a dialog: this opens PaymentDialogActivity, which draws the details over the home screen.
    val open = actionStartActivity<PaymentDialogActivity>(
        actionParametersOf(ActionParameters.Key<Long>(PaymentDialogActivity.EXTRA_PAYMENT_ID) to p.id)
    )
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(open).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTime) {
            Text(
                formatTime(p.receivedAt),
                style = TextStyle(color = Ob.textSecondary, fontSize = 13.sp),
                modifier = GlanceModifier.width(70.dp),
            )
        }
        Text(
            p.payer ?: "—",
            style = TextStyle(color = Ob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            if (visible) Money.format(p.amountPaise) else "₹ •••",
            style = TextStyle(
                color = if (visible) Ob.textPrimary else Ob.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** Big enough to read at a glance, small enough that ₹1,23,456 fits the width. */
private fun amountSize(width: Dp, compact: Boolean): TextUnit = when {
    width < 140.dp -> 20.sp
    width < 190.dp -> 26.sp
    compact -> 30.sp
    else -> 36.sp
}
