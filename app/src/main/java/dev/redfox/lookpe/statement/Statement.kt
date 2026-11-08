package dev.redfox.lookpe.statement

import dev.redfox.lookpe.data.Payment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How far back a statement goes. */
enum class StatementPeriod(val label: String) {
    ThisMonth("Month"), LastMonth("Last"), Last3Months("3 months"), AllTime("All");

    /** First and last day covered. [firstPayment] only matters for [AllTime]. */
    fun range(today: LocalDate, firstPayment: LocalDate?): Pair<LocalDate, LocalDate> = when (this) {
        ThisMonth -> today.withDayOfMonth(1) to today
        LastMonth -> today.minusMonths(1).let { it.withDayOfMonth(1) to it.withDayOfMonth(it.lengthOfMonth()) }
        Last3Months -> today.minusMonths(2).withDayOfMonth(1) to today
        AllTime -> (firstPayment ?: today) to today
    }
}

/** One line of the statement, oldest first like a bank's. */
data class StatementRow(
    val at: Long,
    val payer: String?,
    val txnId: String?,
    val amountPaise: Long,
    /** Sum of every payment up to and including this one in the period. */
    val runningPaise: Long,
)

/** One payer's payments in a period, for the Top customers list. */
data class Customer(val name: String, val count: Int, val totalPaise: Long, val lastAt: Long)

data class StatementDay(val date: LocalDate, val count: Int, val totalPaise: Long)

/** Everything the PDF prints, worked out up front so it can be tested without drawing anything. */
data class Statement(
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: Long,
    val zone: ZoneId,
    val rows: List<StatementRow>,
    val days: List<StatementDay>,
) {
    val count get() = rows.size
    val totalPaise get() = rows.sumOf { it.amountPaise }
    val averagePaise get() = if (rows.isEmpty()) 0L else totalPaise / rows.size
    val highest get() = rows.maxByOrNull { it.amountPaise }
    val bestDay get() = days.maxByOrNull { it.totalPaise }

    /**
     * Payers ranked by what they paid, biggest first. Names are matched ignoring case and spacing, so
     * "ASHA ROY" and "Asha Roy " are one customer. Payments with no payer name are left out; see [unnamedCount].
     */
    val customers: List<Customer> by lazy {
        fun tidy(name: String) = name.trim().replace(Regex("\\s+"), " ")
        rows.filter { !it.payer.isNullOrBlank() }
            .groupBy { tidy(it.payer!!).lowercase() }
            .map { (_, ps) ->
                val newest = ps.maxBy { it.at }
                Customer(tidy(newest.payer!!), ps.size, ps.sumOf { it.amountPaise }, newest.at)
            }
            .sortedWith(compareByDescending<Customer> { it.totalPaise }.thenByDescending { it.count }.thenBy { it.name })
    }

    val unnamedCount get() = rows.count { it.payer.isNullOrBlank() }

    companion object {
        fun build(payments: List<Payment>, from: LocalDate, to: LocalDate, generatedAt: Long, zone: ZoneId): Statement {
            fun dayOf(p: Payment) = Instant.ofEpochMilli(p.receivedAt).atZone(zone).toLocalDate()
            val inRange = payments.filter { dayOf(it) in from..to }.sortedBy { it.receivedAt }
            var running = 0L
            val rows = inRange.map {
                running += it.amountPaise
                StatementRow(it.receivedAt, it.payer, it.txnId, it.amountPaise, running)
            }
            val days = inRange.groupBy(::dayOf).map { (date, ps) -> StatementDay(date, ps.size, ps.sumOf { it.amountPaise }) }
                .sortedBy { it.date }
            return Statement(from, to, generatedAt, zone, rows, days)
        }
    }
}
