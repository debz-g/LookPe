package dev.redfox.lookpe.parse

import java.text.Normalizer

/** A payment pulled out of a PhonePe Business "money received" notification. */
data class ParsedPayment(
    val amountPaise: Long,
    val payer: String?,
    val txnId: String?,
)

/**
 * Parses PhonePe Business notifications. Seen formats (Oct 2026):
 *
 *  - title "Money received",  text "You've received Rs.300 from ASHA ROY"
 *  - title "Received Rs 160", text "You've received Rs 160 from ******1234 via PhonePe for txn T2601011030000000000001."
 *
 * PhonePe styles the digits, and the style changes between notifications: mathematical digits
 * (𝟏𝟔𝟎, 𝟽𝟻𝟶, 𝟚𝟟𝟘, 𝟯𝟰𝟬) and, since 29 Oct 2026, keycap emoji (2️⃣9️⃣0️⃣ = "2" + U+FE0F + U+20E3).
 * Text is NFKC-normalised (mathematical → ASCII) and keycap decorations are stripped before the
 * amount is read. An amount followed by anything unexpected is rejected rather than cut short, so
 * a future digit style lands in Skipped instead of being logged as a wrong amount.
 */
object PaymentParser {

    /** The amount must end at a space, the end, or punctuation followed by a space or the end. */
    private val AMOUNT = Regex(
        """(?:Rs\.?|₹|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?=$|\s|[.,;:!)/-](?:\s|$))""",
        RegexOption.IGNORE_CASE,
    )

    /** Emoji presentation, keycap and zero-width characters that sit between styled digits. */
    private val DECORATIONS = Regex("[\uFE00-\uFE0F\u20DD-\u20E4\u200B-\u200D\u2060\uFEFF]")
    private const val KEYCAP_TEN = "\uD83D\uDD1F" // 🔟
    private val RECEIVED = Regex("""\breceived\b""", RegexOption.IGNORE_CASE)
    private val PAYER = Regex("""\bfrom\s+(.+?)(?:\s+via\b|\s+for\s+txn\b|\s+on\b|[.]?\s*$)""", RegexOption.IGNORE_CASE)
    private val TXN = Regex("""\btxn\s*(?:id)?[:\s]*([A-Z0-9]{8,})""", RegexOption.IGNORE_CASE)

    // Notifications that mention money but are not incoming customer payments.
    private val EXCLUDE = Regex("""\b(refund|refunded|settle|settled|settlement|cashback|failed|reversed|sent|paid to|debited)\b""", RegexOption.IGNORE_CASE)

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKC)
            .replace(' ', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** [normalize] plus keycap/emoji decorations removed, for reading numbers (not names). */
    fun forNumbers(s: String): String = normalize(s.replace(KEYCAP_TEN, "10").replace(DECORATIONS, ""))

    fun parse(title: String?, text: String?): ParsedPayment? {
        val t = normalize(title.orEmpty())
        val body = normalize(text.orEmpty())
        val all = "$t $body"
        if (!RECEIVED.containsMatchIn(all) || EXCLUDE.containsMatchIn(all)) return null

        // Prefer the body: the title amount is sometimes absent ("Money received").
        val bodyAmount = amountIn(forNumbers(text.orEmpty()))
        val titleAmount = amountIn(forNumbers(title.orEmpty()))
        // When both carry an amount they must agree; if not, something unreadable is going on.
        if (bodyAmount != null && titleAmount != null && bodyAmount != titleAmount) return null
        val amountPaise = bodyAmount ?: titleAmount ?: return null
        if (amountPaise <= 0) return null

        val payer = PAYER.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val txnId = TXN.find(body)?.groupValues?.get(1)?.uppercase()
        return ParsedPayment(amountPaise, payer, txnId)
    }

    private fun amountIn(s: String): Long? = AMOUNT.find(s)?.groupValues?.get(1)?.let(::toPaise)

    private fun toPaise(s: String): Long? {
        val clean = s.replace(",", "")
        val parts = clean.split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return rupees * 100 + paise
    }
}
