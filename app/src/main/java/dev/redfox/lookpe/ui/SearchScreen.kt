package dev.redfox.lookpe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import dev.redfox.lookpe.R
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.obsidian.components.DateDivider
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.ObSearchField
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val groupDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

/** Find a customer's payment on any day: by name, last digits, txn id or amount. */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository.get(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val results by remember(query) {
        if (query.isBlank()) flowOf(emptyList()) else repo.search(query)
    }.collectAsState(emptyList())
    val revealed by RevealState.revealed.collectAsState()
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val visible = !lockEnabled || revealed
    var opened by remember { mutableStateOf<Payment?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DetailPage(title = "Search", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.glassSource().fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + ObSpacing.XXL,
            ),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.S),
        ) {
            item {
                ObSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Name, last 4 digits, txn ID or amount",
                    focusRequester = focus,
                )
            }
            when {
                query.isBlank() -> item {
                    Hint("Find a payment", "Try a name, the last 4 digits of the number (1234), a txn ID, or an amount like 300.")
                }
                results.isEmpty() -> item {
                    Hint("No payments match", "Check the spelling, or try fewer letters.")
                }
                else -> {
                    val zone = ZoneId.systemDefault()
                    val byDay = results.groupBy { Instant.ofEpochMilli(it.receivedAt).atZone(zone).toLocalDate() }
                    byDay.forEach { (date, payments) ->
                        item(key = "d$date") { DateDivider(dayLabel(date)) }
                        item(key = "p$date") {
                            PaymentsCard(payments, visible, onOpen = { opened = it })
                        }
                    }
                }
            }
        }
    }
    opened?.let { PaymentDetailsSheet(it, visible, onDismiss = { opened = null }) }
}

@Composable
private fun Hint(title: String, subtitle: String) {
    Box(Modifier.padding(vertical = ObSpacing.XXL)) {
        EmptyState(icon = R.drawable.ic_search, title = title, subtitle = subtitle)
    }
}

internal fun dayLabel(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> groupDate.format(date)
}
