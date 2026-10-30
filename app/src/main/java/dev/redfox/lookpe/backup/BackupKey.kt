package dev.redfox.lookpe.backup

import dev.redfox.lookpe.BuildConfig

/**
 * The AES-256 key for `.lookpe` backups. It comes from `backup.key` in the project root at build
 * time (see app/build.gradle.kts) and is never in source control, so only builds made with that
 * file can open its backups. It's compiled in as two masked halves; R8 also renames this class.
 * Someone determined could still dig it out of an APK, but no other app or viewer can read a backup.
 *
 * Never change backup.key on the machine that builds the APK you hand out: every existing backup
 * would become unreadable.
 */
internal object BackupKey {
    fun bytes(): ByteArray {
        val a = BuildConfig.BACKUP_KEY_A.hexToBytes()
        val b = BuildConfig.BACKUP_KEY_B.hexToBytes()
        return ByteArray(32) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    private fun String.hexToBytes() = ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
