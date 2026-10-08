package com.savemymoney.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.savemymoney.app.parse.ParsedPayment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class Payment(
    val id: Long,
    val amountCents: Long,
    val currency: String,
    val merchant: String,
    val timeMillis: Long,
    val source: String,
)

data class LoggedNotification(
    val timeMillis: Long,
    val packageName: String,
    val title: String?,
    val body: String?,
    val result: String,
)

/** Everything the app records, kept in a small SQLite database on the phone. */
class PaymentStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "payments.db", null, 1) {

    private val changeCount = MutableStateFlow(0L)

    /** Changes whenever something is written, so screens and the banner know to reload. */
    val changes: StateFlow<Long> = changeCount

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                merchant TEXT NOT NULL,
                time_millis INTEGER NOT NULL,
                source TEXT NOT NULL,
                package TEXT,
                notification_key TEXT,
                hidden INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX payments_time ON payments(time_millis)")
        db.execSQL(
            """
            CREATE TABLE notification_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time_millis INTEGER NOT NULL,
                package TEXT NOT NULL,
                notification_key TEXT NOT NULL,
                title TEXT,
                body TEXT,
                result TEXT NOT NULL,
                UNIQUE (notification_key, time_millis)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun paymentsBetween(fromMillis: Long, untilMillis: Long): List<Payment> =
        readableDatabase.rawQuery(
            "SELECT id, amount_cents, currency, merchant, time_millis, source FROM payments " +
                "WHERE hidden = 0 AND time_millis >= ? AND time_millis < ? ORDER BY time_millis DESC",
            arrayOf(fromMillis.toString(), untilMillis.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Payment(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5)))
                }
            }
        }

    /** The currency most payments were made in, if there are any. */
    fun mostUsedCurrency(): String? =
        readableDatabase.rawQuery(
            "SELECT currency FROM payments WHERE hidden = 0 GROUP BY currency ORDER BY COUNT(*) DESC LIMIT 1",
            null,
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    /**
     * Records a payment seen in a notification. Returns false if it was already
     * recorded: Wallet updates its notifications, Play services can post the same
     * payment again, and the listener rescans open notifications when it restarts.
     * Rows the user deleted are only hidden, so they're matched here too and stay deleted.
     */
    @Synchronized
    fun addWalletPayment(
        payment: ParsedPayment,
        timeMillis: Long,
        packageName: String,
        notificationKey: String,
    ): Boolean {
        val db = writableDatabase
        val merchant = payment.merchant ?: DEFAULT_MERCHANT
        val existing = db.rawQuery(
            "SELECT id, merchant FROM payments WHERE source = ? AND amount_cents = ? AND currency = ? " +
                "AND time_millis BETWEEN ? AND ? AND (notification_key = ? OR package <> ?) LIMIT 1",
            arrayOf(
                SOURCE_WALLET,
                payment.amountCents.toString(),
                payment.currency,
                (timeMillis - SAME_PAYMENT_WINDOW_MS).toString(),
                (timeMillis + SAME_PAYMENT_WINDOW_MS).toString(),
                notificationKey,
                packageName,
            ),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }

        if (existing != null) {
            val (id, oldMerchant) = existing
            if (oldMerchant == DEFAULT_MERCHANT && merchant != DEFAULT_MERCHANT) {
                db.update("payments", ContentValues().apply { put("merchant", merchant) }, "id = ?", arrayOf(id.toString()))
                changed()
            }
            return false
        }

        db.insert(
            "payments",
            null,
            ContentValues().apply {
                put("amount_cents", payment.amountCents)
                put("currency", payment.currency)
                put("merchant", merchant)
                put("time_millis", timeMillis)
                put("source", SOURCE_WALLET)
                put("package", packageName)
                put("notification_key", notificationKey)
            },
        )
        changed()
        return true
    }

    fun addManual(amountCents: Long, currency: String, merchant: String, timeMillis: Long) {
        writableDatabase.insert(
            "payments",
            null,
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("currency", currency)
                put("merchant", merchant)
                put("time_millis", timeMillis)
                put("source", SOURCE_MANUAL)
            },
        )
        changed()
    }

    fun update(id: Long, amountCents: Long, currency: String, merchant: String, timeMillis: Long) {
        writableDatabase.update(
            "payments",
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("currency", currency)
                put("merchant", merchant)
                put("time_millis", timeMillis)
            },
            "id = ?",
            arrayOf(id.toString()),
        )
        changed()
    }

    fun delete(id: Long) {
        writableDatabase.update(
            "payments",
            ContentValues().apply { put("hidden", 1) },
            "id = ?",
            arrayOf(id.toString()),
        )
        changed()
    }

    fun logNotification(
        timeMillis: Long,
        packageName: String,
        notificationKey: String,
        title: String?,
        body: String?,
        result: String,
    ) {
        val db = writableDatabase
        // The same notification seen again on a rescan keeps its original entry.
        db.insertWithOnConflict(
            "notification_log",
            null,
            ContentValues().apply {
                put("time_millis", timeMillis)
                put("package", packageName)
                put("notification_key", notificationKey)
                put("title", title)
                put("body", body)
                put("result", result)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        db.execSQL(
            "DELETE FROM notification_log WHERE id NOT IN " +
                "(SELECT id FROM notification_log ORDER BY id DESC LIMIT $LOG_SIZE)",
        )
        changed()
    }

    fun recentNotifications(): List<LoggedNotification> =
        readableDatabase.rawQuery(
            "SELECT time_millis, package, title, body, result FROM notification_log ORDER BY id DESC",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(LoggedNotification(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
                }
            }
        }

    fun clearLog() {
        writableDatabase.delete("notification_log", null, null)
        changed()
    }

    private fun changed() = changeCount.update { it + 1 }

    companion object {
        const val SOURCE_WALLET = "wallet"
        const val SOURCE_MANUAL = "manual"
        const val DEFAULT_MERCHANT = "Google Wallet payment"
        private const val SAME_PAYMENT_WINDOW_MS = 10 * 60 * 1000L
        private const val LOG_SIZE = 100

        @Volatile
        private var instance: PaymentStore? = null

        fun get(context: Context): PaymentStore =
            instance ?: synchronized(this) {
                instance ?: PaymentStore(context.applicationContext).also { instance = it }
            }
    }
}
