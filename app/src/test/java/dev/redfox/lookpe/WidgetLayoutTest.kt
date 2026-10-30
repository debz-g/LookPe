package dev.redfox.lookpe

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.widget.Content
import dev.redfox.lookpe.widget.WidgetState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Each widget size shows what fits, and the eye button follows the lock setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLayoutTest {

    private val payments = listOf(
        Payment(1, "a", 16000, "******1234", "T1", 1_000, ""),
        Payment(2, "b", 30000, "ASHA ROY", null, 2_000, ""),
    )

    private fun state(lockEnabled: Boolean = true, visible: Boolean = false) = WidgetState(
        day = LocalDate.of(2026, 10, 28),
        payments = payments,
        lockEnabled = lockEnabled,
        visible = visible,
        listening = true,
    )

    @Test
    fun smallest_lockOn_showsMaskedTotalAndEyeOnly() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(130.dp, 60.dp))
        provideComposable { Content(state()) }
        onNode(hasText(Money.MASK)).assertExists()
        onNode(hasContentDescription("Show amounts")).assertExists()
        onNode(hasText("2 payments")).assertDoesNotExist()
        onNode(hasText("ASHA ROY")).assertDoesNotExist()
    }

    @Test
    fun smallest_lockOff_showsOnlyTheAmount() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(130.dp, 60.dp))
        provideComposable { Content(state(lockEnabled = false, visible = true)) }
        onNode(hasText("₹460")).assertExists()
        onNode(hasContentDescription("Show amounts")).assertDoesNotExist()
        onNode(hasContentDescription("Hide amounts")).assertDoesNotExist()
        onNode(hasText("2 payments")).assertDoesNotExist()
    }

    @Test
    fun shown_offersHide() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 80.dp))
        provideComposable { Content(state(visible = true)) }
        onNode(hasText("₹460")).assertExists()
        onNode(hasContentDescription("Hide amounts")).assertExists()
    }

    @Test
    fun medium_showsSummaryWithoutList() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 160.dp))
        provideComposable { Content(state(visible = true)) }
        onNode(hasText("₹460")).assertExists()
        onNode(hasText("2 payments")).assertExists()
        onNode(hasText("ASHA ROY")).assertDoesNotExist()
    }

    @Test
    fun large_showsList() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 260.dp))
        provideComposable { Content(state(visible = true)) }
        onNode(hasText("2 payments")).assertExists()
        onNode(hasText("ASHA ROY")).assertExists()
        onNode(hasText("₹300")).assertExists()
    }

    @Test
    fun large_hidden_masksRowAmounts() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 260.dp))
        provideComposable { Content(state()) }
        onNode(hasText("ASHA ROY")).assertExists()
        onNode(hasText("₹300")).assertDoesNotExist()
    }
}
