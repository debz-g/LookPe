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
    fun financialYear_runsAprilToMarch() {
        val mid = LocalDate.of(2026, 11, 8)
        assertEquals(LocalDate.of(2026, 4, 1) to mid, StatementPeriod.ThisFY.range(mid, null))
        assertEquals(LocalDate.of(2025, 4, 1) to LocalDate.of(2026, 3, 31), StatementPeriod.LastFY.range(mid, null))
        // January to March still belongs to the year that began the previous April.
        val feb = LocalDate.of(2027, 2, 10)
        assertEquals(LocalDate.of(2026, 4, 1) to feb, StatementPeriod.ThisFY.range(feb, null))
        assertEquals(LocalDate.of(2025, 4, 1) to LocalDate.of(2026, 3, 31), StatementPeriod.LastFY.range(feb, null))
        // 1 April itself starts the new year.
        val first = LocalDate.of(2026, 4, 1)
        assertEquals(first to first, StatementPeriod.ThisFY.range(first, null))
    }

    @Test
    fun customRange_isUsed_swappedIfBackwards_andDefaultsToThisMonth() {
        val today = LocalDate.of(2026, 11, 8)
        val a = LocalDate.of(2026, 9, 10)
        val b = LocalDate.of(2026, 10, 20)
        assertEquals(a to b, StatementPeriod.Custom.range(today, null, a to b))
        assertEquals(a to b, StatementPeriod.Custom.range(today, null, b to a))
        assertEquals(LocalDate.of(2026, 11, 1) to today, StatementPeriod.Custom.range(today, null, null))
    }

    @Test
    fun customers_areRankedByTotal_matchingNamesLoosely_andSkippingUnnamed() {
        val ps = listOf(
            pay(1, at("2026-11-01", "10:00"), 10000, "ASHA ROY"),
            pay(2, at("2026-11-02", "10:00"), 25000, "asha  roy "),
            pay(3, at("2026-11-02", "11:00"), 30000, "Bob"),
            pay(4, at("2026-11-03", "11:00"), 500, null),
            pay(5, at("2026-11-03", "12:00"), 700, "  "),
            pay(6, at("2026-11-04", "12:00"), 30000, "Carl"),
        )
        val st = Statement.build(ps, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), 0, zone)
        // Bob and Carl tie on ₹300 and one payment each, so the name decides.
        assertEquals(listOf("asha roy", "Bob", "Carl"), st.customers.map { it.name })
        assertEquals(35000L, st.customers[0].totalPaise)
        assertEquals(2, st.customers[0].count)
        assertEquals(at("2026-11-02", "10:00"), st.customers[0].lastAt)
        assertEquals(2, st.unnamedCount)
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
