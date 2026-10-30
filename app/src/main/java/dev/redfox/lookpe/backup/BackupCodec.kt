package dev.redfox.lookpe.backup

import dev.redfox.lookpe.data.Payment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The `.lookpe` backup file:
 *
 * ```
 * "LOOKPE" | version (1 byte) | IV (12 bytes) | AES-256-GCM( gzip( payments ) )
 * ```
 *
 * The header is authenticated as GCM associated data, so any change to the file is rejected.
 * The key is built into the app ([BackupKey]) rather than the Android Keystore on purpose:
 * Keystore keys are deleted with the app, and the whole point is restoring after a reinstall.
 */
object BackupCodec {
    const val EXTENSION = "lookpe"
    private val MAGIC = "LOOKPE".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encode(payments: List<Payment>, createdAt: Long): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(BackupKey.bytes(), "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(header())
        }
        val sealed = cipher.doFinal(gzip(serialize(payments, createdAt)))
        return header() + iv + sealed
    }

    /** Throws [BackupFormatException] for anything that isn't an intact LookPe backup. */
    fun decode(file: ByteArray): Backup {
        val header = header()
        if (file.size < header.size + IV_BYTES + TAG_BITS / 8 ||
            !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) throw BackupFormatException("Not a LookPe backup file")
        if (file[MAGIC.size] != VERSION) {
            throw BackupFormatException("Made by a newer LookPe; update the app first")
        }
        val iv = file.copyOfRange(header.size, header.size + IV_BYTES)
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(BackupKey.bytes(), "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(header)
                doFinal(file, header.size + IV_BYTES, file.size - header.size - IV_BYTES)
            }
        } catch (e: GeneralSecurityException) {
            throw BackupFormatException("The backup file is damaged or was changed", e)
        }
        return try {
            deserialize(gunzip(plain))
        } catch (e: Exception) {
            throw BackupFormatException("The backup file is damaged", e)
        }
    }

    data class Backup(val createdAt: Long, val payments: List<Payment>)

    private fun header() = MAGIC + VERSION

    private fun serialize(payments: List<Payment>, createdAt: Long): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeLong(createdAt)
            out.writeInt(payments.size)
            for (p in payments) {
                out.writeUTF(p.dedupeKey)
                out.writeLong(p.amountPaise)
                out.writeNullableUTF(p.payer)
                out.writeNullableUTF(p.txnId)
                out.writeLong(p.receivedAt)
                out.writeUTF(p.raw)
            }
        }
        return bytes.toByteArray()
    }

    private fun deserialize(bytes: ByteArray): Backup =
        DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
            val createdAt = inp.readLong()
            val count = inp.readInt()
            require(count >= 0)
            val payments = List(count) {
                Payment(
                    dedupeKey = inp.readUTF(),
                    amountPaise = inp.readLong(),
                    payer = inp.readNullableUTF(),
                    txnId = inp.readNullableUTF(),
                    receivedAt = inp.readLong(),
                    raw = inp.readUTF(),
                )
            }
            Backup(createdAt, payments)
        }

    private fun DataOutputStream.writeNullableUTF(s: String?) {
        writeBoolean(s != null)
        if (s != null) writeUTF(s)
    }

    private fun DataInputStream.readNullableUTF(): String? = if (readBoolean()) readUTF() else null

    private fun gzip(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    private fun gunzip(b: ByteArray) = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
