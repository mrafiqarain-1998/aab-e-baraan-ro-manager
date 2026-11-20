package com.example.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.util.PendingTxAlert
import com.example.util.TransactionNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Play-policy-compliant background worker (WorkManager) that runs periodically (~every 15 min)
 * even when the app is completely closed, checking Firebase Realtime Database (Spark Free Tier)
 * for new Deliveries, Payments, Expenses, or Customers recorded by other users and posting
 * Android system notifications.
 */
class TransactionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TxNotifyWorker"
    }

    override suspend fun doWork(): Result {
        val authUser = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        } ?: return Result.success()

        val rtdbUrl = try {
            applicationContext.getString(R.string.firebase_rtdb_url).trim()
        } catch (_: Exception) {
            ""
        }
        if (rtdbUrl.isBlank()) return Result.success()

        val db = try {
            FirebaseDatabase.getInstance(rtdbUrl)
        } catch (e: Exception) {
            Log.w(TAG, "RTDB init failed in worker: ${e.message}")
            return Result.success()
        }

        val plantId = RtdbSyncHelper.sanitizeKey(TransactionNotificationHelper.getActivePlantId(applicationContext))
        val actorUid = TransactionNotificationHelper.getActorUid(applicationContext).ifBlank { authUser.uid }
        val actorName = TransactionNotificationHelper.getActorName(applicationContext)
        val actorEmail = TransactionNotificationHelper.getActorEmail(applicationContext)
            .ifBlank { authUser.email.orEmpty().lowercase() }
        val lastCheckTs = TransactionNotificationHelper.getLastCheckTimestamp(applicationContext)

        val collectedAlerts = mutableListOf<PendingTxAlert>()
        var maxSeenTs = lastCheckTs

        try {
            val plantRef = db.reference.child("plantData").child(plantId)

            // 1. Deliveries
            val deliveriesSnap = fetchSafe(plantRef.child("deliveries"))
            deliveriesSnap?.children?.forEach { child ->
                val ts = extractEventTs(child)
                if (ts > lastCheckTs) {
                    if (ts > maxSeenTs) maxSeenTs = ts
                    val id = child.child("id").getValue(String::class.java).orEmpty().ifBlank { child.key.orEmpty() }
                    val createdByUid = child.child("createdByUid").getValue(String::class.java).orEmpty()
                        .ifBlank { child.child("lastModifiedByUid").getValue(String::class.java).orEmpty() }
                    val staff = child.child("createdBy").getValue(String::class.java).orEmpty()
                        .ifBlank { child.child("staffName").getValue(String::class.java).orEmpty() }
                    if (!TransactionNotificationHelper.isOwnTransaction(createdByUid, staff, actorUid, actorName, actorEmail) &&
                        !TransactionNotificationHelper.isAlreadyNotified(applicationContext, "delivery", id)
                    ) {
                        val customer = child.child("customerName").getValue(String::class.java).orEmpty().ifBlank { "Customer" }
                        val amount = extractDouble(child, "totalAmount").toInt()
                        val bottles = extractLong(child, "totalFullDelivered")
                        val byLabel = if (staff.isNotBlank()) " • By $staff" else ""
                        collectedAlerts.add(
                            PendingTxAlert(
                                type = "delivery",
                                recordId = id,
                                title = "New Delivery • $customer",
                                body = "Rs. $amount ($bottles bottles)$byLabel",
                                createdAt = ts
                            )
                        )
                    }
                }
            }

            // 2. Payments
            val paymentsSnap = fetchSafe(plantRef.child("payments"))
            paymentsSnap?.children?.forEach { child ->
                val ts = extractEventTs(child)
                if (ts > lastCheckTs) {
                    if (ts > maxSeenTs) maxSeenTs = ts
                    val id = child.child("id").getValue(String::class.java).orEmpty().ifBlank { child.key.orEmpty() }
                    val createdByUid = child.child("createdByUid").getValue(String::class.java).orEmpty()
                        .ifBlank { child.child("lastModifiedByUid").getValue(String::class.java).orEmpty() }
                    val receivedBy = child.child("receivedBy").getValue(String::class.java).orEmpty()
                    if (!TransactionNotificationHelper.isOwnTransaction(createdByUid, receivedBy, actorUid, actorName, actorEmail) &&
                        !TransactionNotificationHelper.isAlreadyNotified(applicationContext, "payment", id)
                    ) {
                        val customer = child.child("customerName").getValue(String::class.java).orEmpty().ifBlank { "Customer" }
                        val amount = extractDouble(child, "amount").toInt()
                        val method = child.child("paymentMethod").getValue(String::class.java).orEmpty().ifBlank { "CASH" }
                        val byLabel = if (receivedBy.isNotBlank()) " • By $receivedBy" else ""
                        collectedAlerts.add(
                            PendingTxAlert(
                                type = "payment",
                                recordId = id,
                                title = "Payment Received • $customer",
                                body = "Rs. $amount ($method)$byLabel",
                                createdAt = ts
                            )
                        )
                    }
                }
            }

            // 3. Expenses
            val expensesSnap = fetchSafe(plantRef.child("expenses"))
            expensesSnap?.children?.forEach { child ->
                val ts = extractEventTs(child)
                if (ts > lastCheckTs) {
                    if (ts > maxSeenTs) maxSeenTs = ts
                    val id = child.child("id").getValue(String::class.java).orEmpty().ifBlank { child.key.orEmpty() }
                    val createdByUid = child.child("createdByUid").getValue(String::class.java).orEmpty()
                        .ifBlank { child.child("lastModifiedByUid").getValue(String::class.java).orEmpty() }
                    val enteredBy = child.child("enteredBy").getValue(String::class.java).orEmpty()
                    if (!TransactionNotificationHelper.isOwnTransaction(createdByUid, enteredBy, actorUid, actorName, actorEmail) &&
                        !TransactionNotificationHelper.isAlreadyNotified(applicationContext, "expense", id)
                    ) {
                        val cat = child.child("categoryName").getValue(String::class.java).orEmpty().ifBlank { "Expense" }
                        val desc = child.child("description").getValue(String::class.java).orEmpty()
                        val amount = extractDouble(child, "amount").toInt()
                        val byLabel = if (enteredBy.isNotBlank()) " • By $enteredBy" else ""
                        val descPart = if (desc.isNotBlank()) " ($desc)" else ""
                        collectedAlerts.add(
                            PendingTxAlert(
                                type = "expense",
                                recordId = id,
                                title = "New Expense • $cat",
                                body = "Rs. $amount$descPart$byLabel",
                                createdAt = ts
                            )
                        )
                    }
                }
            }

            // 4. Customers
            val customersSnap = fetchSafe(plantRef.child("customers"))
            customersSnap?.children?.forEach { child ->
                val ts = extractEventTs(child)
                if (ts > lastCheckTs) {
                    if (ts > maxSeenTs) maxSeenTs = ts
                    val id = child.child("id").getValue(String::class.java).orEmpty().ifBlank { child.key.orEmpty() }
                    val createdByUid = child.child("createdByUid").getValue(String::class.java).orEmpty()
                        .ifBlank { child.child("lastModifiedByUid").getValue(String::class.java).orEmpty() }
                    val createdBy = child.child("createdBy").getValue(String::class.java).orEmpty()
                    if (!TransactionNotificationHelper.isOwnTransaction(createdByUid, createdBy, actorUid, actorName, actorEmail) &&
                        !TransactionNotificationHelper.isAlreadyNotified(applicationContext, "customer", id)
                    ) {
                        val name = child.child("name").getValue(String::class.java).orEmpty().ifBlank { "New Customer" }
                        val area = child.child("area").getValue(String::class.java).orEmpty()
                        val areaPart = if (area.isNotBlank()) " • $area" else ""
                        val byLabel = if (createdBy.isNotBlank()) " • By $createdBy" else ""
                        collectedAlerts.add(
                            PendingTxAlert(
                                type = "customer",
                                recordId = id,
                                title = "New Customer Added",
                                body = "$name$areaPart$byLabel",
                                createdAt = ts
                            )
                        )
                    }
                }
            }

            if (collectedAlerts.isNotEmpty()) {
                val sorted = collectedAlerts.sortedByDescending { it.createdAt }
                TransactionNotificationHelper.notifyGroupedTransactions(applicationContext, sorted)
            }
            if (maxSeenTs > lastCheckTs) {
                TransactionNotificationHelper.updateLastCheckTimestamp(applicationContext, maxSeenTs)
            }
            return Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Background transaction poll failed: ${e.message}")
            return Result.retry()
        }
    }

    private suspend fun fetchSafe(ref: com.google.firebase.database.DatabaseReference): DataSnapshot? {
        return try {
            ref.get().await()
        } catch (e: Exception) {
            Log.w(TAG, "Skipped ${ref.key} in background worker: ${e.message}")
            null
        }
    }

    private fun extractEventTs(child: DataSnapshot): Long {
        val createdAt = extractLong(child, "createdAt")
        if (createdAt > 0L) return createdAt
        return RtdbSyncHelper.extractUpdatedAtMillis(child)
    }

    private fun extractLong(child: DataSnapshot, key: String): Long {
        val raw = child.child(key).value
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun extractDouble(child: DataSnapshot, key: String): Double {
        val raw = child.child(key).value
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
