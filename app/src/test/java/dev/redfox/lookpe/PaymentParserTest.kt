package dev.redfox.lookpe

import dev.redfox.lookpe.parse.PaymentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    @Test
    fun moneyReceived_withMonospaceDigits_noTxn() {
        // "300" in MATHEMATICAL MONOSPACE DIGITs, as PhonePe sends it.
        val p = PaymentParser.parse("Money received", "You've received Rs.𝟹𝟶𝟶 from ASHA ROY")!!
        assertEquals(30000L, p.amountPaise)
        assertEquals("ASHA ROY", p.payer)
        assertNull(p.txnId)
    }

    @Test
    fun receivedRs_withBoldDigits_andTxn() {
        val p = PaymentParser.parse(
            "Received Rs 𝟏𝟔𝟎",
            "You've received Rs 𝟏𝟔𝟎 from ******1234 via PhonePe for txn T2601011030000000000001.",
        )!!
        assertEquals(16000L, p.amountPaise)
        assertEquals("******1234", p.payer)
        assertEquals("T2601011030000000000001", p.txnId)
    }

    @Test
    fun receivedRs_monospace750() {
        val p = PaymentParser.parse(
            "Received Rs 𝟽𝟻𝟶",
            "You've received Rs 𝟽𝟻𝟶 from ******5678 via PhonePe for txn T2601010940000000000002.",
        )!!
        assertEquals(75000L, p.amountPaise)
        assertEquals("T2601010940000000000002", p.txnId)
    }

    // 29 Oct 2026: PhonePe switched some notifications to keycap emoji digits; ₹290 was logged as ₹2.
    @Test
    fun keycapDigits_moneyReceived() {
        val p = PaymentParser.parse("Money received", "You've received Rs.3\uFE0F\u20E33\uFE0F\u20E30\uFE0F\u20E3 from TEST PAYER")!!
        assertEquals(33000L, p.amountPaise)
        assertEquals("TEST PAYER", p.payer)
    }

    @Test
    fun keycapDigits_receivedRs_withTxn() {
        val k = "2\uFE0F\u20E39\uFE0F\u20E30\uFE0F\u20E3"
        val p = PaymentParser.parse("Received Rs $k", "You've received Rs $k from Test Payer via PhonePe for txn T2601011810000000000003.")!!
        assertEquals(29000L, p.amountPaise)
        assertEquals("Test Payer", p.payer)
        assertEquals("T2601011810000000000003", p.txnId)
    }

    @Test
    fun keycapTen_andOtherMathStyles() {
        assertEquals(1000L, PaymentParser.parse("Money received", "You've received Rs.\uD83D\uDD1F from A")!!.amountPaise)
        // Double-struck (𝟚𝟟𝟘) and sans-serif bold (𝟯𝟰𝟬), both seen in real notifications.
        assertEquals(27000L, PaymentParser.parse("Money received", "You've received Rs.𝟚𝟟𝟘 from A")!!.amountPaise)
        assertEquals(34000L, PaymentParser.parse("Received Rs 𝟯𝟰𝟬", "You've received Rs 𝟯𝟰𝟬 from ******9012 via PhonePe for txn T1234567890.")!!.amountPaise)
    }

    @Test
    fun unknownDigitStyle_isSkippedNotCutShort() {
        // A digit followed by a character we don't understand must not become "₹2".
        assertNull(PaymentParser.parse("Money received", "You've received Rs.2\u26069\u26060 from A"))
    }

    @Test
    fun titleAndBodyDisagree_isSkipped() {
        assertNull(PaymentParser.parse("Received Rs 290", "You've received Rs 2 from A via PhonePe for txn T1234567890."))
    }

    @Test
    fun plainDigits_commasAndPaise() {
        val p = PaymentParser.parse("Money received", "You've received ₹1,250.5 from RAM")!!
        assertEquals(125050L, p.amountPaise)
    }

    @Test
    fun ignoresSettlementsAndPromos() {
        assertNull(PaymentParser.parse("Settlement", "Rs 5000 received amount settled to your bank account"))
        assertNull(PaymentParser.parse("Get a loan", "Pre-approved loan up to Rs 5,00,000"))
        assertNull(PaymentParser.parse("Refund", "Refund of Rs 100 received"))
    }
}

class MoneyTest {
    @Test
    fun format_usesIndianGrouping() {
        assertEquals("₹1,21,050", Money.format(1_21_050_00))
        assertEquals("₹1,210.50", Money.format(1_210_50))
        assertEquals("₹999", Money.format(999_00))
        assertEquals("₹1,00,00,000", Money.format(1_00_00_000_00))
        assertEquals("₹0.05", Money.format(5))
    }

    @Test
    fun compact_fitsCalendarCells() {
        assertEquals("₹850", Money.compact(850_00))
        assertEquals("₹4.5k", Money.compact(4_500_00))
        assertEquals("₹1k", Money.compact(1_000_00))
        assertEquals("₹45k", Money.compact(45_000_00))
        assertEquals("₹1.2L", Money.compact(1_20_000_00))
    }
}
