package dev.redfox.lookpe

import android.app.Application
import dev.redfox.lookpe.data.Payment
import dev.redfox.lookpe.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Rows an older parser misread are corrected from their saved notification text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepairAmountsTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun keycapRows_areCorrected_othersUntouched() = runBlocking {
        val k = "2️⃣9️⃣0️⃣"
        val repo = Repository.get(app)
        repo.importPayments(
            listOf(
                // What 1.2.1 saved for a ₹290 keycap notification.
                Payment(0, "T290", 200, "Test Payer", "T290", 1_000,
                    "Received Rs $k\nYou've received Rs $k from Test Payer via PhonePe for txn T2601011810000000000003."),
                Payment(0, "T160", 16000, "******1234", "T160", 2_000,
                    "Received Rs 𝟏𝟔𝟎\nYou've received Rs 𝟏𝟔𝟎 from ******1234 via PhonePe for txn T2601011030000000000001."),
            )
        )
        assertEquals(1, repo.repairAmounts())
        val byKey = repo.allPayments().associateBy { it.dedupeKey }
        assertEquals(29000L, byKey.getValue("T290").amountPaise)
        assertEquals(16000L, byKey.getValue("T160").amountPaise)
        assertEquals(0, repo.repairAmounts()) // nothing left to fix
    }
}
