package dev.redfox.lookpe

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Money {
    /** ₹1,21,050 or ₹1,210.50: Indian digit grouping, paise only when non-zero. Done by hand, not via locale data. */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val fraction = abs % 100
        return sign + "₹" + group(abs / 100) + if (fraction == 0L) "" else ".%02d".format(Locale.US, fraction)
    }

    /** 1,21,050.00: no ₹ sign and always two decimals, for the columns of a statement. */
    fun plain(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return sign + group(abs / 100) + ".%02d".format(Locale.US, abs % 100)
    }

    private fun group(rupees: Long): String {
        val digits = rupees.toString()
        if (digits.length <= 3) return digits
        return digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + digits.takeLast(3)
    }

    /** Short form for tight spaces like calendar cells: ₹850, ₹4.5k, ₹45k, ₹1.2L. */
    fun compact(paise: Long): String {
        val rupees = paise / 100.0
        fun trim(v: Double) =
            if (v >= 10) "%.0f".format(Locale.US, v) else "%.1f".format(Locale.US, v).removeSuffix(".0")
        return when {
            rupees < 1_000 -> "₹%.0f".format(Locale.US, rupees)
            rupees < 1_00_000 -> "₹${trim(rupees / 1_000)}k"
            else -> "₹${trim(rupees / 1_00_000)}L"
        }
    }

    const val MASK = "₹ ••••"
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun formatTime(epochMillis: Long): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
