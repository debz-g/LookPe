package dev.redfox.lookpe

import android.content.Intent
import dev.redfox.lookpe.share.ShareTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareTotalTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun message_forToday_and_otherDays() {
        assertEquals("Today's collection (Sun, 8 Nov): ₹12,450", ShareTotal.message(today, today, 1_245_000))
        assertEquals("Collection on Sat, 7 Nov: ₹9,800.50", ShareTotal.message(today.minusDays(1), today, 980_050))
        assertEquals("Today's collection (Sun, 8 Nov): ₹0", ShareTotal.message(today, today, 0))
    }

    @Test
    fun oneWhatsApp_goesStraightToIt() {
        val intent = ShareTotal.buildIntent("hello", listOf("com.whatsapp.w4b"))
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("com.whatsapp.w4b", intent.`package`)
        assertEquals("hello", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun noWhatsApp_fallsBackToTheShareSheet() {
        val intent = ShareTotal.buildIntent("hello", emptyList())
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        val inner = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertNull(inner.`package`)
        assertEquals("hello", inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun bothWhatsApps_offersBoth() {
        val intent = ShareTotal.buildIntent("hello", ShareTotal.WHATSAPP)
        assertEquals(Intent.ACTION_CHOOSER, intent.action)
        assertEquals("com.whatsapp", intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!.`package`)
        val alternates = intent.getParcelableArrayExtra(Intent.EXTRA_ALTERNATE_INTENTS, Intent::class.java)!!
        assertEquals(listOf("com.whatsapp.w4b"), alternates.map { it.`package` })
    }
}
