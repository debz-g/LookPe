package dev.redfox.lookpe.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.data.refreshWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class BackupInterval(val hours: Long?, val label: String) {
    Off(null, "Off"), Every5h(5, "5h"), Every12h(12, "12h"), Every24h(24, "24h");
}

data class BackupStatus(val interval: BackupInterval, val lastAt: Long?, val lastFile: String?)

/**
 * Backups that survive uninstalling the app. Automatic and "Back up now" copies go to the shared
 * Download/LookPe folder, which Android keeps when the app is removed (an app's own storage is
 * wiped). After a reinstall, Import reads one back through the system file picker.
 */
object Backups {
    const val FOLDER = "Download/LookPe"
    const val MIME = "application/octet-stream"
    /** Fresh installs back up every 12 hours until changed. */
    private val DEFAULT_INTERVAL = BackupInterval.Every12h
    /** Automatic copies LookPe keeps; older ones it made are deleted. */
    private const val KEEP = 5

    private const val PREFS = "backup"
    private const val KEY_INTERVAL = "interval"
    private const val KEY_LAST_AT = "lastAt"
    private const val KEY_LAST_FILE = "lastFile"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_OWNED = "owned"
    private const val WORK = "auto-backup"

    private val _status = MutableStateFlow(BackupStatus(DEFAULT_INTERVAL, null, null))
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun sync(context: Context) {
        val p = prefs(context)
        _status.value = BackupStatus(
            interval = p.getString(KEY_INTERVAL, null)
                ?.let { name -> BackupInterval.entries.firstOrNull { it.name == name } } ?: DEFAULT_INTERVAL,
            lastAt = p.getLong(KEY_LAST_AT, 0).takeIf { it > 0 },
            lastFile = p.getString(KEY_LAST_FILE, null),
        )
    }

    /** Call on app start: makes sure the automatic backup is scheduled (keeps an existing schedule). */
    fun ensureScheduled(context: Context) {
        sync(context)
        schedule(context, _status.value.interval, ExistingPeriodicWorkPolicy.KEEP)
    }

    fun setInterval(context: Context, interval: BackupInterval) {
        prefs(context).edit { putString(KEY_INTERVAL, interval.name) }
        sync(context)
        schedule(context, interval, ExistingPeriodicWorkPolicy.UPDATE)
    }

    private fun schedule(context: Context, interval: BackupInterval, policy: ExistingPeriodicWorkPolicy) {
        val work = WorkManager.getInstance(context)
        val hours = interval.hours ?: return run { work.cancelUniqueWork(WORK) }
        work.enqueueUniquePeriodicWork(
            WORK, policy, PeriodicWorkRequestBuilder<BackupWorker>(hours, TimeUnit.HOURS).build(),
        )
    }

    /**
     * Writes a new backup to [FOLDER]. Skipped (returns null) when there are no payments, so a fresh
     * reinstall never buries the real backup under an empty newer one; with [onlyIfChanged]
     * (automatic runs) also when nothing changed since the last backup. Returns the file name.
     */
    suspend fun backUpNow(context: Context, onlyIfChanged: Boolean): String? = withContext(Dispatchers.IO) {
        val payments = Repository.get(context).allPayments()
        val fingerprint = "${payments.size}|${payments.maxOfOrNull { it.receivedAt } ?: 0}"
        val p = prefs(context)
        if (payments.isEmpty() || (onlyIfChanged && p.getString(KEY_FINGERPRINT, null) == fingerprint)) {
            return@withContext null
        }

        val now = System.currentTimeMillis()
        val name = "LookPe-${fileStamp.format(LocalDateTime.now())}.${BackupCodec.EXTENSION}"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LookPe")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the backup file")
        try {
            resolver.openOutputStream(uri, "w")!!.use { it.write(BackupCodec.encode(payments, now)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        val owned = (p.getString(KEY_OWNED, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()) + uri.toString()
        owned.dropLast(KEEP).forEach { runCatching { resolver.delete(Uri.parse(it), null, null) } }
        p.edit {
            putLong(KEY_LAST_AT, now)
            putString(KEY_LAST_FILE, name)
            putString(KEY_FINGERPRINT, fingerprint)
            putString(KEY_OWNED, owned.takeLast(KEEP).joinToString("\n"))
        }
        sync(context)
        name
    }

    /** Writes a backup to a place the user picked (Export). */
    suspend fun exportTo(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val payments = Repository.get(context).allPayments()
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            it.write(BackupCodec.encode(payments, System.currentTimeMillis()))
        }
        payments.size
    }

    data class ImportResult(val inFile: Int, val added: Int)

    /** Merges a backup into the app's data. Throws [BackupFormatException] for a wrong or damaged file. */
    suspend fun importFrom(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupFormatException("Couldn't open the file")
        val backup = BackupCodec.decode(bytes)
        val repo = Repository.get(context)
        val added = repo.importPayments(backup.payments)
        repo.repairAmounts() // a backup from an older version may carry amounts that parser misread
        refreshWidgets(context)
        ImportResult(backup.payments.size, added)
    }

    fun suggestedExportName() = "LookPe-${fileStamp.format(LocalDateTime.now())}.${BackupCodec.EXTENSION}"

    private val fileStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Backups.backUpNow(applicationContext, onlyIfChanged = true)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
