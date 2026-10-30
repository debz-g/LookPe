package dev.redfox.lookpe.ui

import android.Manifest
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.notify.DailySummary
import dev.redfox.lookpe.setup.openAppNotificationSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.redfox.lookpe.R
import dev.redfox.lookpe.backup.BackupFormatException
import dev.redfox.lookpe.backup.BackupInterval
import dev.redfox.lookpe.backup.Backups
import dev.redfox.lookpe.formatTime
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.setup.SetupStatus
import dev.redfox.lookpe.setup.openAppInfo
import dev.redfox.lookpe.setup.openAutostart
import dev.redfox.lookpe.setup.openNotificationAccess
import dev.redfox.lookpe.setup.pinWidget
import dev.redfox.lookpe.setup.readSetupStatus
import dev.redfox.lookpe.setup.requestUnrestrictedBattery
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.IconTile
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.ObSnackbarHost
import dev.redfox.obsidian.components.ObSnackbarState
import dev.redfox.obsidian.components.ObSwitch
import dev.redfox.obsidian.components.PillButton
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.components.SegmentOption
import dev.redfox.obsidian.components.SegmentedPill
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Privacy, backup and phone setup; opened from the gear on the home screen. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onTurnOffLock: () -> Unit, onOpenSkipped: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(readSetupStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSetupStatus(context)
        RevealState.sync(context)
        Backups.sync(context)
        onPauseOrDispose { }
    }
    val lockEnabled by RevealState.lockEnabled.collectAsState()
    val snackbars = remember { ObSnackbarState() }
    val skippedCount by remember { Repository.get(context).observeSkipped() }.collectAsState(emptyList())
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Denied before: Android won't ask again, so send him to the app's notification settings.
        if (!granted) openAppNotificationSettings(context)
        status = readSetupStatus(context)
    }
    val allowNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }

    DetailPage(title = "Settings", onBack = onBack) { padding ->
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
                if (!status.complete || !status.notifications) {
                    item { FinishSetupCard(status, allowNotifications, Modifier.revealOnEnter(0)) }
                }
                item { SectionLabel("Privacy", Modifier.padding(top = ObSpacing.S).revealOnEnter(1)) }
                item {
                    PrivacyCard(
                        lockEnabled = lockEnabled,
                        onLockChange = { on ->
                            if (on) scope.launch { RevealState.setLockEnabled(context, true) } else onTurnOffLock()
                        },
                        modifier = Modifier.revealOnEnter(2),
                    )
                }
                item { SectionLabel("Daily summary", Modifier.padding(top = ObSpacing.S).revealOnEnter(3)) }
                item { DailySummaryCard(status.notifications, allowNotifications, Modifier.revealOnEnter(4)) }
                item { SectionLabel("Backup", Modifier.padding(top = ObSpacing.S).revealOnEnter(5)) }
                item { BackupCard(snackbars, Modifier.revealOnEnter(6)) }
                item { SectionLabel("Phone setup", Modifier.padding(top = ObSpacing.S).revealOnEnter(7)) }
                item { PhoneSetupCard(status, allowNotifications, Modifier.revealOnEnter(8)) }
                item { SectionLabel("Troubleshooting", Modifier.padding(top = ObSpacing.S).revealOnEnter(9)) }
                item { TroubleshootingCard(skippedCount.size, onOpenSkipped, Modifier.revealOnEnter(10)) }
            }
            ObSnackbarHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter),
                bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
        }
    }
}

@Composable
private fun BackupCard(snackbars: ObSnackbarState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val status by Backups.status.collectAsState()

    fun run(block: suspend () -> String) {
        scope.launch {
            val message = try {
                block()
            } catch (e: BackupFormatException) {
                e.message ?: "That isn't a LookPe backup"
            } catch (e: Exception) {
                "Something went wrong: ${e.message}"
            }
            snackbars.show(message)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backups.MIME)) { uri ->
        if (uri != null) run { "Exported ${plural(Backups.exportTo(context, uri), "payment")}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val result = Backups.importFrom(context, uri)
            when {
                result.added == 0 -> "Nothing new: all ${plural(result.inFile, "payment")} were already here"
                else -> "Imported ${plural(result.added, "payment")}"
            }
        }
    }

    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.L)) {
        Text("Automatic backup", style = type.bodyLarge, color = colors.textPrimary)
        Text(
            "Saved in ${Backups.FOLDER}, which stays on the phone even if LookPe is deleted.",
            style = type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(ObSpacing.M))
        SegmentedPill(
            options = BackupInterval.entries.map { SegmentOption(it.label) },
            selectedIndex = status.interval.ordinal,
            onSelect = { Backups.setInterval(context, BackupInterval.entries[it]) },
        )
        Spacer(Modifier.height(ObSpacing.M))
        Text(
            status.lastAt?.let { "Last backup: ${formatWhen(it)}" } ?: "No backup yet",
            style = type.body,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(ObSpacing.L))
        Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            PillButton(
                text = "Back up now",
                onClick = {
                    run {
                        Backups.backUpNow(context, onlyIfChanged = false)?.let { "Saved $it" }
                            ?: "Nothing to back up yet"
                    }
                },
                contentColor = colors.accentLight,
                containerColor = colors.accentContainer,
            )
            PillButton(text = "Export", onClick = { exportLauncher.launch(Backups.suggestedExportName()) })
            PillButton(text = "Import", onClick = { importLauncher.launch(arrayOf("*/*")) })
        }
        Spacer(Modifier.height(ObSpacing.M))
        Text(
            "After reinstalling, tap Import and pick the newest .lookpe file. Importing more than one is fine; " +
                "payments already here are skipped.",
            style = type.body,
            color = colors.textTertiary,
        )
    }
}


private val whenFormat = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

private fun formatWhen(epochMillis: Long): String {
    val at = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return if (at.toLocalDate() == LocalDate.now()) "today, ${formatTime(epochMillis)}" else whenFormat.format(at)
}

@Composable
private fun PrivacyCard(lockEnabled: Boolean, onLockChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.L)) {
        ObListRow(
            title = "Hide amounts",
            subtitle = if (lockEnabled) "Fingerprint or PIN to show them" else "Amounts always visible",
            icon = if (lockEnabled) R.drawable.ic_eye_off else R.drawable.ic_eye,
            titleStyleLarge = true,
            onClick = { onLockChange(!lockEnabled) },
            trailing = { ObSwitch(checked = lockEnabled, onCheckedChange = onLockChange) },
        )
    }
}

@Composable
private fun FinishSetupCard(status: SetupStatus, allowNotifications: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    ObCard(modifier.fillMaxWidth(), borderColor = colors.accentBorder, contentPadding = PaddingValues(ObSpacing.L)) {
        Text("Finish setup", style = ObsidianTheme.typography.title, color = colors.textPrimary)
        Text(
            "LookPe can't log payments until these are done.",
            style = ObsidianTheme.typography.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(ObSpacing.M))
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.L)) {
            SetupRow(
                icon = R.drawable.ic_bell,
                title = "Allow notification access",
                detail = "So LookPe can read PhonePe Business payment notifications. If the switch is greyed out, " +
                    "open App info → ⋮ → Allow restricted settings, then try again.",
                done = status.listening,
                actions = listOf("Open" to { openNotificationAccess(context) }, "App info" to { openAppInfo(context) }),
            )
            SetupRow(
                icon = R.drawable.ic_battery,
                title = "Don't restrict battery",
                detail = "Otherwise HyperOS stops LookPe in the background and payments are missed.",
                done = status.unrestrictedBattery,
                actions = listOf("Open" to { requestUnrestrictedBattery(context) }),
            )
            SetupRow(
                icon = R.drawable.ic_bell,
                title = "Allow LookPe notifications",
                detail = "For the daily summary, and a warning if LookPe ever stops reading payments.",
                done = status.notifications,
                actions = listOf("Allow" to allowNotifications),
            )
        }
    }
}

@Composable
private fun DailySummaryCard(notificationsAllowed: Boolean, allowNotifications: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val hour by DailySummary.hour.collectAsState()
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.L)) {
        Text("Today's total as a notification", style = type.bodyLarge, color = colors.textPrimary)
        Text(
            "Every evening at the time you pick. With Hide amounts on, the lock screen doesn't show the amount.",
            style = type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(ObSpacing.M))
        SegmentedPill(
            options = DailySummary.CHOICES.map { SegmentOption(it?.let(::hourLabel) ?: "Off") },
            selectedIndex = DailySummary.CHOICES.indexOf(hour).coerceAtLeast(0),
            onSelect = { DailySummary.setHour(context, DailySummary.CHOICES[it]) },
        )
        if (!notificationsAllowed && hour != null) {
            Spacer(Modifier.height(ObSpacing.M))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Notifications are off for LookPe, so the summary can't show.",
                    style = type.body,
                    color = colors.warning,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(ObSpacing.S))
                PillButton(
                    text = "Allow",
                    onClick = allowNotifications,
                    contentColor = colors.accentLight,
                    containerColor = colors.accentContainer,
                )
            }
        }
    }
}

private fun hourLabel(hour: Int) = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"

@Composable
private fun TroubleshootingCard(skipped: Int, onOpenSkipped: () -> Unit, modifier: Modifier = Modifier) {
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.L)) {
        ObListRow(
            title = "Skipped notifications",
            subtitle = when (skipped) {
                0 -> "None so far"
                1 -> "1 PhonePe notification wasn't logged"
                else -> "$skipped PhonePe notifications weren't logged"
            },
            icon = R.drawable.ic_info,
            titleStyleLarge = true,
            onClick = onOpenSkipped,
            trailing = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = ObsidianTheme.colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

@Composable
private fun PhoneSetupCard(status: SetupStatus, allowNotifications: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ObCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(ObSpacing.L)) {
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.L)) {
            SetupRow(R.drawable.ic_bell, "Notification access", null, status.listening,
                listOf("Open" to { openNotificationAccess(context) }))
            SetupRow(R.drawable.ic_battery, "Battery: No restrictions", null, status.unrestrictedBattery,
                listOf("Open" to { requestUnrestrictedBattery(context) }))
            SetupRow(R.drawable.ic_bell, "LookPe notifications", null, status.notifications,
                listOf("Allow" to allowNotifications))
            SetupRow(
                R.drawable.ic_power, "Autostart",
                "Turn on Autostart for LookPe, then lock it in Recents (long-press its card → lock).",
                null, listOf("Open" to { openAutostart(context) }),
            )
            SetupRow(R.drawable.ic_widgets, "Home screen widget", "Today's total, one glance away.", null,
                listOf("Add widget" to { pinWidget(context) }))
        }
        Spacer(Modifier.height(ObSpacing.L))
        Text(
            "Keep PhonePe Business notifications on (silent is fine). LookPe only sees payments that notify.",
            style = ObsidianTheme.typography.body,
            color = ObsidianTheme.colors.textTertiary,
        )
    }
}

/** Icon tile, title, a wrapping explanation and pill actions; a check replaces the actions once done. */
@Composable
private fun SetupRow(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    done: Boolean?,
    actions: List<Pair<String, () -> Unit>>,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    Row(verticalAlignment = Alignment.Top) {
        IconTile(
            icon = icon,
            background = if (done == true) colors.accentContainer else colors.tile,
            tint = if (done == true) colors.accentLight else colors.textSecondary,
        )
        Spacer(Modifier.width(ObSpacing.L))
        Column(Modifier.weight(1f).padding(top = ObSpacing.XXS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = type.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (done == true) {
                    Icon(
                        painterResource(R.drawable.ic_check_bold),
                        contentDescription = "Done",
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (detail != null) {
                Text(detail, style = type.body, color = colors.textSecondary)
            }
            if (done != true) {
                Spacer(Modifier.height(ObSpacing.S))
                Row(horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                    actions.forEachIndexed { i, (label, onClick) ->
                        PillButton(
                            text = label,
                            onClick = onClick,
                            contentColor = if (i == 0) colors.accentLight else colors.textPrimary,
                            containerColor = if (i == 0) colors.accentContainer else colors.tile,
                        )
                    }
                }
            }
        }
    }
}
