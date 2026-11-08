package dev.redfox.lookpe.statement

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dev.redfox.lookpe.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Saves statements as PDFs in the phone's Downloads folder (no permission needed via MediaStore). */
object Statements {
    const val MIME = "application/pdf"

    data class Saved(val uri: Uri, val name: String, val payments: Int)

    /** The statement for [period] as of now, built from every saved payment. */
    suspend fun build(context: Context, period: StatementPeriod): Statement = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val payments = Repository.get(context).allPayments()
        val first = payments.minOfOrNull { it.receivedAt }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val (from, to) = period.range(LocalDate.now(zone), first)
        Statement.build(payments, from, to, System.currentTimeMillis(), zone)
    }

    /** Writes the PDF to Downloads; the file is removed again if drawing fails part way. */
    suspend fun save(context: Context, statement: Statement): Saved = withContext(Dispatchers.IO) {
        val name = "LookPe-Statement-${nameFmt.format(statement.from)}-to-${nameFmt.format(statement.to)}" +
            "-${stampFmt.format(Instant.ofEpochMilli(statement.generatedAt).atZone(statement.zone))}.pdf"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the statement file")
        try {
            resolver.openOutputStream(uri, "w")!!.use { StatementPdf.write(statement, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        Saved(uri, name, statement.count)
    }

    private val nameFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val stampFmt = DateTimeFormatter.ofPattern("HHmm")
}
