package dev.redfox.lookpe

import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.statement.Statement
import dev.redfox.lookpe.statement.StatementPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatementTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: String, time: String) =
        java.time.LocalDateTime.parse("${day}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun pay(id: Int, whenMs: Long, paise: Long, payer: String? = "Test Payer") =
        Payment(0, "T$id", paise, payer, "T2601011030000000000$id", whenMs, "raw")

    private val payments = listOf(
        pay(3, at("2026-11-03", "18:00"), 30000),
        pay(1, at("2026-11-01", "10:30"), 16000, "******1234"),
        pay(2, at("2026-11-03", "09:15"), 5050),
        pay(4, at("2026-10-31", "23:59"), 99900), // before the period
        pay(5, at("2026-11-09", "00:00"), 12300), // after the period
    )

    private fun build() = Statement.build(
        payments, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), at("2026-11-08", "21:00"), zone,
    )

    @Test
    fun rows_areOldestFirst_withRunningTotal_andRespectRange() {
        val st = build()
        assertEquals(listOf(16000L, 5050L, 30000L), st.rows.map { it.amountPaise })
        assertEquals(listOf(16000L, 21050L, 51050L), st.rows.map { it.runningPaise })
        assertEquals(51050L, st.totalPaise)
        assertEquals(17016L, st.averagePaise)
        assertEquals(30000L, st.highest?.amountPaise)
    }

    @Test
    fun days_groupByLocalDate() {
        val st = build()
        assertEquals(listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3)), st.days.map { it.date })
        assertEquals(listOf(1, 2), st.days.map { it.count })
        assertEquals(LocalDate.of(2026, 11, 3), st.bestDay?.date)
        assertEquals(35050L, st.bestDay?.totalPaise)
    }

    @Test
    fun emptyPeriod_hasNoRows_andZeroStats() {
        val st = Statement.build(payments, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, zone)
        assertEquals(0, st.count)
        assertEquals(0L, st.averagePaise)
        assertEquals(null, st.bestDay)
    }

    @Test
    fun periods() {
        val today = LocalDate.of(2026, 3, 15)
        assertEquals(LocalDate.of(2026, 3, 1) to today, StatementPeriod.ThisMonth.range(today, null))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), StatementPeriod.LastMonth.range(today, null))
        assertEquals(LocalDate.of(2026, 1, 1) to today, StatementPeriod.Last3Months.range(today, null))
        assertEquals(LocalDate.of(2025, 12, 5) to today, StatementPeriod.AllTime.range(today, LocalDate.of(2025, 12, 5)))
        // January looks back into the previous year.
        assertEquals(
            LocalDate.of(2025, 12, 1) to LocalDate.of(2025, 12, 31),
            StatementPeriod.LastMonth.range(LocalDate.of(2026, 1, 10), null),
        )
    }

    @Test
    fun money_plain() {
        assertEquals("0.00", Money.plain(0))
        assertEquals("160.00", Money.plain(16000))
        assertEquals("1,21,050.50", Money.plain(12105050))
        assertEquals("₹1,21,050", Money.format(12105000))
    }
}
