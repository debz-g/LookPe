package dev.redfox.lookpe

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import dev.redfox.lookpe.notify.DailySummary
import dev.redfox.lookpe.notify.Notifications
import dev.redfox.lookpe.parse.ParsedPayment
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.reveal.RevealState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The 9 PM summary shows today's total, and hides it on the lock screen when amounts are hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailySummaryTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
        runBlocking {
            val repo = Repository.get(app)
            val now = System.currentTimeMillis()
            repo.record(ParsedPayment(16000, "******1234", "T1"), "k1", now, "raw")
            repo.record(ParsedPayment(30000, "ASHA", "T2"), "k2", now, "raw")
        }
    }

    private fun posted(): Notification = shadowOf(manager).allNotifications.single()

    @Test
    fun summary_showsTotalAndCount() = runBlocking {
        RevealState.setLockEnabled(app, false)
        DailySummary.post(app)
        val n = posted()
        assertEquals("Today's collection: ₹460", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("2 payments. Tap to see them.", n.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun hiddenAmounts_lockScreenShowsNoAmount() = runBlocking {
        RevealState.setLockEnabled(app, true)
        DailySummary.post(app)
        val n = posted()
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        val public = n.publicVersion
        assertNotNull(public)
        assertEquals("Today's collection is ready", public.extras.getString(Notification.EXTRA_TITLE))
    }
}
