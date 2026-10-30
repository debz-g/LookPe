package dev.redfox.lookpe

import dev.redfox.lookpe.backup.BackupCodec
import dev.redfox.lookpe.backup.BackupFormatException
import dev.redfox.lookpe.data.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {

    private val payments = listOf(
        Payment(0, "T2601011030000000000001", 16000, "******1234", "T2601011030000000000001", 1_761_627_715_000, "Received Rs 𝟏𝟔𝟎\n…"),
        Payment(0, "0|com.phonepe.app.business|2020|t2|2000|1793171409176", 30000, "ASHA ROY", null, 1_761_630_000_000, "Money received\n…"),
    )

    @Test
    fun roundTrip_keepsEveryField() {
        val file = BackupCodec.encode(payments, createdAt = 42)
        val backup = BackupCodec.decode(file)
        assertEquals(42L, backup.createdAt)
        assertEquals(payments, backup.payments)
    }

    @Test
    fun emptyBackup_roundTrips() {
        assertEquals(emptyList<Payment>(), BackupCodec.decode(BackupCodec.encode(emptyList(), 1)).payments)
    }

    @Test
    fun contentIsNotReadableInTheFile() {
        val text = String(BackupCodec.encode(payments, 1), Charsets.ISO_8859_1)
        assertFalse(text.contains("ASHA"))
        assertFalse(text.contains("T2601011030000000000001"))
    }

    @Test
    fun tamperedFile_isRejected() {
        val file = BackupCodec.encode(payments, 1)
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(file) }
    }

    @Test
    fun tamperedHeader_isRejected() {
        val file = BackupCodec.encode(payments, 1)
        file[6] = 2 // version byte is authenticated
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(file) }
    }

    @Test
    fun otherFiles_areRejected() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("hello".toByteArray()) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(ByteArray(0)) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(ByteArray(200)) }
    }
}
