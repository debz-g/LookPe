package dev.redfox.lookpe.data

import android.content.Context
import androidx.room.Room
import dev.redfox.lookpe.parse.ParsedPayment
import dev.redfox.lookpe.parse.PaymentParser
import kotlinx.coroutines.flow.Flow
import dev.redfox.lookpe.widget.TodayWidget
import java.time.LocalDate
import java.time.ZoneId

/** Single entry point for storage. Kept as a plain singleton; the app is too small for DI. */
class Repository private constructor(context: Context) {

    private val db = Room.databaseBuilder(context, LookPeDatabase::class.java, "lookpe.db").build()
    private val dao = db.paymentDao()
    private val skipped = db.skippedDao()

    /** Returns true if this was a new payment. */
    suspend fun record(parsed: ParsedPayment, notificationKey: String, postTime: Long, raw: String): Boolean {
        if (parsed.txnId == null &&
            dao.countSimilar(parsed.amountPaise, parsed.payer, postTime, DUPLICATE_WINDOW_MS) > 0
        ) return false
        val row = Payment(
            dedupeKey = parsed.txnId ?: "$notificationKey|$postTime",
            amountPaise = parsed.amountPaise,
            payer = parsed.payer,
            txnId = parsed.txnId,
            receivedAt = postTime,
            raw = raw,
        )
        return dao.insert(row) != -1L
    }

    /** Every row including deleted ones: for backups. */
    suspend fun allPayments() = dao.all()

    /** What the user sees: everything not deleted. */
    suspend fun activePayments() = dao.active()

    /** Hand-corrects a payment. The first edit remembers what the parser logged. */
    suspend fun edit(id: Long, amountPaise: Long, payer: String?) {
        val p = dao.all().firstOrNull { it.id == id } ?: return
        val originalAmount = p.originalAmountPaise ?: p.amountPaise
        val originalPayer = if (p.edited) p.originalPayer else p.payer
        dao.setState(id, amountPaise, payer, true, originalAmount, originalPayer, p.deleted)
    }

    /** Puts back what the parser logged before the edit. */
    suspend fun restoreOriginal(id: Long) {
        val p = dao.all().firstOrNull { it.id == id } ?: return
        val amount = p.originalAmountPaise ?: return
        dao.setState(id, amount, p.originalPayer, false, null, null, p.deleted)
    }

    /** Soft delete: hidden from totals and lists, but the row stays so the same notification isn't logged again. */
    suspend fun delete(id: Long) {
        val p = dao.all().firstOrNull { it.id == id } ?: return
        dao.setState(id, p.amountPaise, p.payer, p.edited, p.originalAmountPaise, p.originalPayer, true)
    }

    /**
     * Adds payments from a backup; ones already here (same dedupe key) are skipped, except that an
     * edit or delete made on the other phone carries over to an untouched copy here. Returns how many were new.
     */
    suspend fun importPayments(payments: List<Payment>): Int =
        payments.count { incoming ->
            if (dao.insert(incoming.copy(id = 0)) != -1L) return@count true
            val here = dao.byKey(incoming.dedupeKey)
            if (here != null && !here.edited && !here.deleted && (incoming.edited || incoming.deleted)) {
                dao.setState(
                    here.id, incoming.amountPaise, incoming.payer, incoming.edited,
                    incoming.originalAmountPaise, incoming.originalPayer, incoming.deleted,
                )
            }
            false
        }

    /** Free text matches payer and txn id; a whole number also matches that rupee amount. */
    fun search(query: String): Flow<List<Payment>> {
        val q = query.trim()
        val paise = q.removePrefix("₹").replace(",", "").toLongOrNull()?.times(100) ?: -1
        return dao.search(q, paise)
    }

    /** Payments from [from] to [to] inclusive, for month views. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Payment>> {
        val zone = ZoneId.systemDefault()
        return dao.observeBetween(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    suspend fun recordSkipped(notificationKey: String, postedAt: Long, title: String?, text: String?) {
        skipped.insert(SkippedNotification(notificationKey = notificationKey, postedAt = postedAt, title = title, text = text))
        skipped.trim(KEEP_SKIPPED)
    }

    fun observeSkipped() = skipped.observeAll()

    suspend fun clearSkipped() = skipped.clear()

    /**
     * Runs skipped notifications through the parser again, e.g. after an update taught it a new
     * PhonePe wording. Ones that parse now become payments. Returns how many were recovered.
     */
    suspend fun reparseSkipped(): Int {
        var recovered = 0
        for (n in skipped.all()) {
            val parsed = PaymentParser.parse(n.title, n.text) ?: continue
            if (record(parsed, n.notificationKey, n.postedAt, raw = "${n.title}\n${n.text}")) recovered++
            skipped.delete(n.id)
        }
        return recovered
    }

    /**
     * Re-reads every saved payment's original notification with the current parser and corrects
     * amounts that come out different. Fixes rows logged by an older parser (e.g. keycap digits
     * read as ₹2 instead of ₹290). Rows the parser now rejects are left as they are. Returns how
     * many were corrected.
     */
    suspend fun repairAmounts(): Int {
        var fixed = 0
        for (p in dao.all()) {
            val (title, text) = p.raw.split('\n', limit = 2).let { it[0] to it.getOrNull(1) }
            if (p.edited) continue // fixed by hand: the parser must not undo it
            val parsed = PaymentParser.parse(title, text) ?: continue
            if (parsed.amountPaise != p.amountPaise) {
                dao.correct(p.id, parsed.amountPaise, parsed.payer ?: p.payer)
                fixed++
            }
        }
        return fixed
    }

    fun observeDay(day: LocalDate) = dayRange(day).let { (from, to) -> dao.observeBetween(from, to) }

    suspend fun paymentsOn(day: LocalDate) = dayRange(day).let { (from, to) -> dao.between(from, to) }

    suspend fun payment(id: Long) = dao.byId(id)

    private fun dayRange(day: LocalDate): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        // Same amount from the same payer within a minute is treated as one notification seen twice.
        private const val DUPLICATE_WINDOW_MS = 60_000L
        private const val KEEP_SKIPPED = 100

        @Volatile private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}

/** Refreshes every placed widget. */
suspend fun refreshWidgets(context: Context) = TodayWidget.refresh(context)
