package dev.redfox.lookpe

import android.app.Application
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.parse.ParsedPayment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Hand edits and deletes: they stick, survive the launch repair, and travel with backups. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditPaymentTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val repo = Repository.get(app)
    private val noon = LocalDate.of(2026, 1, 5).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun row(key: String, paise: Long) = Payment(
        0, key, paise, "Test Payer", key, noon,
        "Received Rs 160\nYou've received Rs 160 from Test Payer via PhonePe for txn $key.",
    )

    private suspend fun byKey(key: String) = repo.allPayments().first { it.dedupeKey == key }

    @Test
    fun edit_isKept_andRepairDoesNotUndoIt() = runBlocking {
        repo.importPayments(listOf(row("TE1", 16000)))
        val id = byKey("TE1").id
        repo.edit(id, 29000, "Asha")
        repo.edit(id, 30000, "Asha R") // a second edit keeps the first original
        assertEquals(0, repo.repairAmounts())
        val p = byKey("TE1")
        assertEquals(30000L, p.amountPaise)
        assertEquals("Asha R", p.payer)
        assertTrue(p.edited)
        assertEquals(16000L, p.originalAmountPaise)
        assertEquals("Test Payer", p.originalPayer)
    }

    @Test
    fun restoreOriginal_putsBackTheParsedValues() = runBlocking {
        repo.importPayments(listOf(row("TE2", 16000)))
        val id = byKey("TE2").id
        repo.edit(id, 1, "x")
        repo.restoreOriginal(id)
        val p = byKey("TE2")
        assertEquals(16000L, p.amountPaise)
        assertEquals("Test Payer", p.payer)
        assertFalse(p.edited)
        assertNull(p.originalAmountPaise)
    }

    @Test
    fun deleted_isHidden_andNotRecordedAgain() = runBlocking {
        repo.importPayments(listOf(row("TD1", 16000)))
        val id = byKey("TD1").id
        repo.delete(id)
        assertNull(repo.payment(id))
        assertTrue(repo.activePayments().none { it.dedupeKey == "TD1" })
        assertTrue(repo.paymentsOn(LocalDate.of(2026, 1, 5)).none { it.dedupeKey == "TD1" })
        // The listener re-reads active notifications on connect: the same txn must not come back.
        assertFalse(repo.record(ParsedPayment(16000, "Test Payer", "TD1"), "k", noon, "raw"))
        assertTrue(byKey("TD1").deleted) // still in the full list, so backups carry it
    }

    @Test
    fun import_carriesAnEditOntoAnUntouchedCopy_butNeverOverwritesALocalEdit() = runBlocking {
        repo.importPayments(listOf(row("TI1", 16000), row("TI2", 16000)))
        repo.edit(byKey("TI2").id, 11100, "Local")
        val fromOtherPhone = listOf(
            row("TI1", 29000).copy(edited = true, originalAmountPaise = 16000, originalPayer = "Test Payer"),
            row("TI2", 22200).copy(edited = true, originalAmountPaise = 16000, originalPayer = "Test Payer"),
        )
        assertEquals(0, repo.importPayments(fromOtherPhone))
        assertEquals(29000L, byKey("TI1").amountPaise)
        assertEquals(11100L, byKey("TI2").amountPaise)
    }

    @Test
    fun parseAmount() {
        assertEquals(125000L, Money.parse("1250"))
        assertEquals(125050L, Money.parse("1,250.5"))
        assertEquals(30000L, Money.parse("₹300"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1e5"))
        assertNull(Money.parse("99999999999"))
        assertEquals("1250", Money.editable(125000))
        assertEquals("1250.50", Money.editable(125050))
    }
}
