package dev.redfox.lookpe.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "payment", indices = [Index(value = ["dedupeKey"], unique = true), Index("receivedAt")])
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Txn id when PhonePe gives one, otherwise the notification key + post time. */
    val dedupeKey: String,
    val amountPaise: Long,
    val payer: String?,
    val txnId: String?,
    /** Notification post time, epoch millis. */
    val receivedAt: Long,
    /** Original title + text, kept so a changed format can be re-parsed later. */
    val raw: String,
    /** Amount or payer changed by hand (a failsafe for a notification the parser misread). */
    @ColumnInfo(defaultValue = "0") val edited: Boolean = false,
    /** What the parser logged before the first edit, so "Restore original" has something to go back to. */
    val originalAmountPaise: Long? = null,
    val originalPayer: String? = null,
    /** Hidden everywhere but kept, so a notification seen again isn't logged a second time. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(payment: Payment): Long

    @Query("SELECT * FROM payment WHERE deleted = 0 AND receivedAt >= :from AND receivedAt < :to ORDER BY receivedAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Payment>>

    @Query("SELECT * FROM payment WHERE deleted = 0 AND receivedAt >= :from AND receivedAt < :to ORDER BY receivedAt DESC")
    suspend fun between(from: Long, to: Long): List<Payment>

    /** Every row, deleted ones too: backups and repair need the lot. */
    @Query("SELECT * FROM payment ORDER BY receivedAt")
    suspend fun all(): List<Payment>

    @Query("SELECT * FROM payment WHERE deleted = 0 ORDER BY receivedAt")
    suspend fun active(): List<Payment>

    /** One payment, for the widget's detail dialog. */
    @Query("SELECT * FROM payment WHERE id = :id AND deleted = 0")
    suspend fun byId(id: Long): Payment?

    @Query("UPDATE payment SET amountPaise = :amountPaise, payer = :payer WHERE id = :id")
    suspend fun correct(id: Long, amountPaise: Long, payer: String?)

    @Query("SELECT * FROM payment WHERE dedupeKey = :key")
    suspend fun byKey(key: String): Payment?

    /** Saves a hand edit (or its restore) and the delete flag. */
    @Query(
        """UPDATE payment SET amountPaise = :amountPaise, payer = :payer, edited = :edited,
           originalAmountPaise = :originalAmountPaise, originalPayer = :originalPayer, deleted = :deleted WHERE id = :id"""
    )
    suspend fun setState(
        id: Long, amountPaise: Long, payer: String?, edited: Boolean,
        originalAmountPaise: Long?, originalPayer: String?, deleted: Boolean,
    )

    /** Payer name, last digits, txn id, or an exact rupee amount ("300"). Newest first. */
    @Query(
        """SELECT * FROM payment
           WHERE deleted = 0 AND (payer LIKE '%' || :text || '%' OR txnId LIKE '%' || :text || '%' OR amountPaise = :paise)
           ORDER BY receivedAt DESC LIMIT 300"""
    )
    fun search(text: String, paise: Long): Flow<List<Payment>>

    /** Catches a payment without a txn id that was also logged under a different notification key. */
    @Query("SELECT COUNT(*) FROM payment WHERE amountPaise = :amountPaise AND payer IS :payer AND ABS(receivedAt - :at) < :windowMs")
    suspend fun countSimilar(amountPaise: Long, payer: String?, at: Long, windowMs: Long): Int
}

/**
 * A PhonePe Business notification the parser didn't turn into a payment: promos, settlements, or a
 * payment in a wording LookPe doesn't know yet. Shown under Settings so a format change is noticed.
 */
@Entity(tableName = "skipped", indices = [Index(value = ["notificationKey", "postedAt"], unique = true)])
data class SkippedNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notificationKey: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
)

@Dao
interface SkippedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(n: SkippedNotification): Long

    @Query("SELECT * FROM skipped ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<SkippedNotification>>

    @Query("SELECT * FROM skipped")
    suspend fun all(): List<SkippedNotification>

    @Query("DELETE FROM skipped WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM skipped")
    suspend fun clear()

    /** Keeps the newest [keep]. */
    @Query("DELETE FROM skipped WHERE id NOT IN (SELECT id FROM skipped ORDER BY postedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Database(
    entities = [Payment::class, SkippedNotification::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class LookPeDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun skippedDao(): SkippedDao
}
