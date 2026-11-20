package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.data.TransactionNotificationWorker
import java.util.concurrent.TimeUnit

data class PendingTxAlert(
    val type: String,
    val recordId: String,
    val title: String,
    val body: String,
    val createdAt: Long
)

/**
 * 100% Free Android Local + Background (WorkManager) Transaction Notification Helper.
 *
 * - Works on Firebase Spark Free Tier (no Blaze plan / Cloud Functions required).
 * - Posts instant system notifications when the app is alive in background/foreground via RTDB listeners.
 * - Posts automatic background notifications via WorkManager (every 15 min) even when the app is fully closed.
 * - Deduplicates alerts and suppresses notifications for records created by the currently logged-in user.
 */
object TransactionNotificationHelper {

    private const val TAG = "TxNotificationHelper"
    const val CHANNEL_ID = "aab_plant_transactions"
    private const val CHANNEL_NAME = "Plant Transactions & Updates"
    private const val CHANNEL_DESC = "Alerts when staff or admin adds deliveries, payments, expenses, or customers"

    private const val PREFS_NAME = "aab_tx_notify_prefs"
    private const val KEY_ACTIVE_PLANT_ID = "active_plant_id"
    private const val KEY_ACTOR_UID = "actor_uid"
    private const val KEY_ACTOR_NAME = "actor_name"
    private const val KEY_ACTOR_EMAIL = "actor_email"
    private const val KEY_LAST_CHECK_TS = "last_check_ts"
    private const val KEY_NOTIFIED_KEYS = "notified_keys"
    private const val WORK_NAME = "aab_bg_tx_alerts"
    private const val MAX_STORED_KEYS = 250

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val existing = manager.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = CHANNEL_DESC
                        enableVibration(true)
                    }
                    manager.createNotificationChannel(channel)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to create notification channel: ${e.message}")
            }
        }
    }

    fun updateActiveUserContext(
        context: Context,
        plantId: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_PLANT_ID, plantId.trim())
            .putString(KEY_ACTOR_UID, actorUid.trim())
            .putString(KEY_ACTOR_NAME, actorName.trim())
            .putString(KEY_ACTOR_EMAIL, actorEmail.trim().lowercase())
        if (!prefs.contains(KEY_LAST_CHECK_TS)) {
            editor.putLong(KEY_LAST_CHECK_TS, System.currentTimeMillis())
        }
        editor.apply()
    }

    fun getActivePlantId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PLANT_ID, "").orEmpty().ifBlank { "aab_e_baraan_plant_01" }
    }

    fun getActorUid(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTOR_UID, "").orEmpty()

    fun getActorName(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTOR_NAME, "").orEmpty()

    fun getActorEmail(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACTOR_EMAIL, "").orEmpty()

    fun getLastCheckTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_LAST_CHECK_TS, 0L)
        if (ts <= 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_CHECK_TS, now).apply()
            return now
        }
        return ts
    }

    fun updateLastCheckTimestamp(context: Context, timestamp: Long) {
        if (timestamp <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong(KEY_LAST_CHECK_TS, 0L)
        if (timestamp > current) {
            prefs.edit().putLong(KEY_LAST_CHECK_TS, timestamp).apply()
        }
    }

    fun isOwnTransaction(
        createdByUid: String,
        createdByName: String,
        currentActorUid: String,
        currentActorName: String,
        currentActorEmail: String = ""
    ): Boolean {
        val cleanCreatedUid = createdByUid.trim()
        val cleanCurrentUid = currentActorUid.trim()
        if (cleanCreatedUid.isNotBlank() && cleanCurrentUid.isNotBlank()) {
            if (cleanCreatedUid.equals(cleanCurrentUid, ignoreCase = true)) return true
            // Match profile id token if embedded like "uid|profileId"
            val createdTokens = cleanCreatedUid.split("|", ":").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            val currentTokens = cleanCurrentUid.split("|", ":").map { it.trim().lowercase() }.filter { it.isNotBlank() }
            if (createdTokens.any { it in currentTokens }) return true
        }
        val cleanCreatedName = createdByName.trim()
        val cleanCurrentName = currentActorName.trim()
        if (cleanCreatedName.isNotBlank() && cleanCurrentName.isNotBlank() &&
            cleanCreatedName.equals(cleanCurrentName, ignoreCase = true)
        ) {
            return true
        }
        val cleanEmail = currentActorEmail.trim()
        if (cleanEmail.isNotBlank() && cleanCreatedUid.equals(cleanEmail, ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun recordKey(type: String, recordId: String): String = "${type.lowercase()}:${recordId.trim()}"

    fun isAlreadyNotified(context: Context, type: String, recordId: String): Boolean {
        if (recordId.isBlank()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_NOTIFIED_KEYS, emptySet()) ?: emptySet()
        return recordKey(type, recordId) in set
    }

    fun markAlreadyNotified(context: Context, type: String, recordId: String, timestamp: Long = System.currentTimeMillis()) {
        if (recordId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_NOTIFIED_KEYS, emptySet()) ?: emptySet()).toMutableList()
        val key = recordKey(type, recordId)
        if (key !in current) {
            current.add(key)
            val trimmed = if (current.size > MAX_STORED_KEYS) {
                current.takeLast(MAX_STORED_KEYS).toSet()
            } else {
                current.toSet()
            }
            prefs.edit().putStringSet(KEY_NOTIFIED_KEYS, trimmed).apply()
        }
        updateLastCheckTimestamp(context, timestamp)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyIfNewRemoteTransaction(
        context: Context,
        type: String,
        recordId: String,
        title: String,
        body: String,
        createdByUid: String,
        createdByName: String,
        createdAt: Long
    ): Boolean {
        if (recordId.isBlank()) return false
        val actorUid = getActorUid(context)
        val actorName = getActorName(context)
        val actorEmail = getActorEmail(context)
        if (isOwnTransaction(createdByUid, createdByName, actorUid, actorName, actorEmail)) {
            markAlreadyNotified(context, type, recordId, createdAt)
            return false
        }
        if (isAlreadyNotified(context, type, recordId)) return false

        markAlreadyNotified(context, type, recordId, createdAt)
        return postSingleNotification(context, type, recordId, title, body)
    }

    fun notifyGroupedTransactions(context: Context, alerts: List<PendingTxAlert>): Int {
        if (alerts.isEmpty()) return 0
        val fresh = alerts.filter { it.recordId.isNotBlank() && !isAlreadyNotified(context, it.type, it.recordId) }
        if (fresh.isEmpty()) return 0

        fresh.forEach { markAlreadyNotified(context, it.type, it.recordId, it.createdAt) }

        return if (fresh.size <= 3) {
            var posted = 0
            fresh.forEach { alert ->
                if (postSingleNotification(context, alert.type, alert.recordId, alert.title, alert.body)) {
                    posted++
                }
            }
            posted
        } else {
            val summaryTitle = "${fresh.size} New Plant Transactions"
            val summaryBody = fresh.take(5).joinToString("\n") { "• ${it.title}: ${it.body}" }
            val posted = postSingleNotification(
                context = context,
                type = "summary",
                recordId = "batch_${System.currentTimeMillis() / 1000L}",
                title = summaryTitle,
                body = summaryBody
            )
            if (posted) fresh.size else 0
        }
    }

    private fun postSingleNotification(
        context: Context,
        type: String,
        recordId: String,
        title: String,
        body: String
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        createNotificationChannel(context)
        return try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_tx_type", type)
                putExtra("notification_tx_id", recordId)
            }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(
                context,
                recordKey(type, recordId).hashCode(),
                launchIntent,
                pendingFlags
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(
                recordKey(type, recordId).hashCode(),
                notification
            )
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission missing: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show notification: ${e.message}")
            false
        }
    }

    fun scheduleBackgroundSyncWorker(context: Context) {
        try {
            createNotificationChannel(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<TransactionNotificationWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager scheduling skipped: ${e.message}")
        }
    }
}
