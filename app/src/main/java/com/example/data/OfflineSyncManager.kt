package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.model.SyncConflictRecord
import com.example.model.SyncOperationType
import com.example.model.SyncQueueItem
import com.example.model.SyncQueueStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.pow

class OfflineSyncManager(
    private val context: Context,
    private val storage: PlantDataStorage,
    private val onNetworkRestored: () -> Unit = {}
) {
    private val lock = Any()

    private val _queueItems = MutableStateFlow<List<SyncQueueItem>>(emptyList())
    val queueItems: StateFlow<List<SyncQueueItem>> = _queueItems.asStateFlow()

    private val _conflicts = MutableStateFlow<List<SyncConflictRecord>>(emptyList())
    val conflicts: StateFlow<List<SyncConflictRecord>> = _conflicts.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(true)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        val loadedQueue = storage.loadSyncQueue()
            // Synced items are no longer kept in the offline sync queue
            .filterNot { it.status == SyncQueueStatus.SYNCED }
            .map {
                // Any item left in SYNCING on sudden process kill resets to PENDING for safe retry
                if (it.status == SyncQueueStatus.SYNCING) it.copy(status = SyncQueueStatus.PENDING) else it
            }
        _queueItems.value = loadedQueue
        storage.saveSyncQueue(loadedQueue)
        _conflicts.value = storage.loadSyncConflicts()
        registerConnectivityObserver()
    }

    private fun registerConnectivityObserver() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val activeNet = cm.activeNetwork
            val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
            _isDeviceOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true ||
                activeNet != null

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isDeviceOnline.value = true
                    onNetworkRestored()
                }

                override fun onLost(network: Network) {
                    _isDeviceOnline.value = checkIsOnlineNow(cm)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val wasOffline = !_isDeviceOnline.value
                    _isDeviceOnline.value = online
                    if (online && wasOffline) {
                        onNetworkRestored()
                    }
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        } catch (e: Exception) {
            Log.w("OfflineSyncManager", "Connectivity observer unavailable: ${e.message}")
            _isDeviceOnline.value = true
        }
    }

    private fun checkIsOnlineNow(cm: ConnectivityManager): Boolean {
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    fun release() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    fun setNetworkStateForTesting(online: Boolean) {
        _isDeviceOnline.value = online
        if (online) {
            onNetworkRestored()
        }
    }

    private fun nextTimestamp(prevUpdatedAt: Long): Long {
        val now = System.currentTimeMillis()
        return if (now <= prevUpdatedAt) prevUpdatedAt + 1L else now
    }

    fun enqueueUpsert(
        collection: String,
        docId: String,
        plantId: String,
        payloadJson: String,
        actorName: String,
        baseRemoteUpdatedAtMillis: Long = 0L,
        actionGroupId: String = "",
        actionTitle: String = ""
    ): SyncQueueItem {
        val keyId = "${collection}|$docId"
        synchronized(lock) {
            val prev = _queueItems.value.find { it.id == keyId }
            val now = nextTimestamp(prev?.updatedAtMillis ?: 0L)
            val createdMs = if (prev != null && prev.status != SyncQueueStatus.SYNCED) prev.createdAtMillis else now
            val resolvedGroupId = actionGroupId.ifBlank { prev?.actionGroupId?.ifBlank { keyId } ?: keyId }
            val resolvedTitle = actionTitle.ifBlank { prev?.actionTitle?.ifBlank { defaultActionTitle(collection, SyncOperationType.UPSERT) } ?: defaultActionTitle(collection, SyncOperationType.UPSERT) }
            val item = SyncQueueItem(
                id = keyId,
                collection = collection,
                docId = docId,
                plantId = plantId,
                operation = SyncOperationType.UPSERT,
                payloadJson = payloadJson,
                status = SyncQueueStatus.PENDING,
                attemptCount = 0,
                lastError = "",
                createdAtMillis = createdMs,
                updatedAtMillis = now,
                nextRetryAtMillis = 0L,
                actorName = actorName,
                baseRemoteUpdatedAtMillis = baseRemoteUpdatedAtMillis,
                deltaFieldsJson = "",
                extraFieldsJson = "",
                actionGroupId = resolvedGroupId,
                actionTitle = resolvedTitle
            )
            val updated = _queueItems.value.filterNot { it.id == keyId } + item
            _queueItems.value = updated.sortedByDescending { it.updatedAtMillis }
            storage.saveSyncQueue(_queueItems.value)
            return item
        }
    }

    fun enqueueIncrementalUpsert(
        collection: String,
        docId: String,
        plantId: String,
        fullPayloadJson: String,
        deltaFields: Map<String, Number>,
        extraFields: Map<String, Any> = emptyMap(),
        actorName: String,
        baseRemoteUpdatedAtMillis: Long = 0L,
        actionGroupId: String = "",
        actionTitle: String = ""
    ): SyncQueueItem {
        val keyId = "${collection}|$docId"
        synchronized(lock) {
            val prev = _queueItems.value.find { it.id == keyId }
            val now = nextTimestamp(prev?.updatedAtMillis ?: 0L)
            val createdMs = if (prev != null && prev.status != SyncQueueStatus.SYNCED) prev.createdAtMillis else now
            val resolvedGroupId = actionGroupId.ifBlank { prev?.actionGroupId?.ifBlank { keyId } ?: keyId }
            val resolvedTitle = actionTitle.ifBlank { prev?.actionTitle?.ifBlank { defaultActionTitle(collection, SyncOperationType.UPSERT) } ?: defaultActionTitle(collection, SyncOperationType.UPSERT) }

            val prevIsUnsyncedFullUpsert = prev != null &&
                prev.operation == SyncOperationType.UPSERT &&
                prev.status in listOf(SyncQueueStatus.PENDING, SyncQueueStatus.FAILED) &&
                prev.deltaFieldsJson.isBlank()

            val mergedDeltas = linkedMapOf<String, Number>()
            if (prev != null &&
                prev.operation == SyncOperationType.UPSERT &&
                prev.status in listOf(SyncQueueStatus.PENDING, SyncQueueStatus.FAILED) &&
                prev.deltaFieldsJson.isNotBlank()
            ) {
                deltaMapFromJson(prev.deltaFieldsJson)?.forEach { (k, v) ->
                    mergedDeltas[k] = v
                }
            }
            deltaFields.forEach { (k, v) ->
                val prevVal = mergedDeltas[k]
                mergedDeltas[k] = if (prevVal == null) {
                    v
                } else if (prevVal is Double || prevVal is Float || v is Double || v is Float) {
                    prevVal.toDouble() + v.toDouble()
                } else {
                    prevVal.toLong() + v.toLong()
                }
            }
            val deltaJson = JSONObject().apply {
                mergedDeltas.forEach { (k, v) ->
                    if (v is Double || v is Float) put(k, v.toDouble()) else put(k, v.toLong())
                }
            }.toString()

            val mergedExtra = linkedMapOf<String, Any?>()
            if (prevIsUnsyncedFullUpsert) {
                val metadataSkip = setOf("plantId", "ownerId", "memberUids", "memberEmails", "updatedAt")
                mapFromPayloadJson(fullPayloadJson)?.forEach { (k, v) ->
                    if (v != null && k !in mergedDeltas && k !in metadataSkip) {
                        mergedExtra[k] = v
                    }
                }
            } else if (prev != null &&
                prev.operation == SyncOperationType.UPSERT &&
                prev.status in listOf(SyncQueueStatus.PENDING, SyncQueueStatus.FAILED)
            ) {
                mapFromPayloadJson(prev.extraFieldsJson)?.forEach { (k, v) ->
                    if (v != null) mergedExtra[k] = v
                }
            }
            extraFields.forEach { (k, v) -> mergedExtra[k] = v }
            val mergedExtraJson = if (mergedExtra.isEmpty()) "" else FirestoreSyncHelper.toPlainJsonString(mergedExtra)

            val item = SyncQueueItem(
                id = keyId,
                collection = collection,
                docId = docId,
                plantId = plantId,
                operation = SyncOperationType.UPSERT,
                payloadJson = fullPayloadJson,
                status = SyncQueueStatus.PENDING,
                attemptCount = 0,
                lastError = "",
                createdAtMillis = createdMs,
                updatedAtMillis = now,
                nextRetryAtMillis = 0L,
                actorName = actorName,
                baseRemoteUpdatedAtMillis = baseRemoteUpdatedAtMillis,
                deltaFieldsJson = deltaJson,
                extraFieldsJson = mergedExtraJson,
                actionGroupId = resolvedGroupId,
                actionTitle = resolvedTitle
            )
            val updated = _queueItems.value.filterNot { it.id == keyId } + item
            _queueItems.value = updated.sortedByDescending { it.updatedAtMillis }
            storage.saveSyncQueue(_queueItems.value)
            return item
        }
    }

    fun enqueueDelete(
        collection: String,
        docId: String,
        plantId: String,
        actorName: String,
        actionGroupId: String = "",
        actionTitle: String = ""
    ): SyncQueueItem {
        val keyId = "${collection}|$docId"
        synchronized(lock) {
            val prev = _queueItems.value.find { it.id == keyId }
            val now = nextTimestamp(prev?.updatedAtMillis ?: 0L)
            val resolvedGroupId = actionGroupId.ifBlank { keyId }
            val resolvedTitle = actionTitle.ifBlank { defaultActionTitle(collection, SyncOperationType.DELETE) }
            val item = SyncQueueItem(
                id = keyId,
                collection = collection,
                docId = docId,
                plantId = plantId,
                operation = SyncOperationType.DELETE,
                payloadJson = "",
                status = SyncQueueStatus.PENDING,
                attemptCount = 0,
                lastError = "",
                createdAtMillis = now,
                updatedAtMillis = now,
                nextRetryAtMillis = 0L,
                actorName = actorName,
                deltaFieldsJson = "",
                extraFieldsJson = "",
                actionGroupId = resolvedGroupId,
                actionTitle = resolvedTitle
            )
            val updated = _queueItems.value.filterNot { it.id == keyId } + item
            _queueItems.value = updated.sortedByDescending { it.updatedAtMillis }
            storage.saveSyncQueue(_queueItems.value)
            return item
        }
    }

    fun hasPendingOrFailedMutation(collection: String, docId: String): Boolean {
        val keyId = "$collection|$docId"
        return _queueItems.value.any {
            it.id == keyId && it.status in listOf(
                SyncQueueStatus.PENDING,
                SyncQueueStatus.SYNCING,
                SyncQueueStatus.FAILED
            )
        }
    }

    fun getPendingDeleteDocIds(collection: String): Set<String> {
        return _queueItems.value
            .filter {
                it.collection == collection &&
                    it.operation == SyncOperationType.DELETE &&
                    it.status in listOf(
                        SyncQueueStatus.PENDING,
                        SyncQueueStatus.SYNCING,
                        SyncQueueStatus.FAILED
                    )
            }
            .map { it.docId }
            .toSet()
    }

    fun getPendingQueueItems(forceIncludeFailed: Boolean = false): List<SyncQueueItem> {
        val now = System.currentTimeMillis()
        return _queueItems.value
            .filter {
                it.status == SyncQueueStatus.PENDING ||
                    (forceIncludeFailed && it.status == SyncQueueStatus.FAILED) ||
                    (it.status == SyncQueueStatus.FAILED && it.nextRetryAtMillis <= now)
            }
            .sortedBy { it.createdAtMillis }
    }

    fun markSyncing(itemId: String): Long {
        synchronized(lock) {
            var snapshotUpdatedAt = 0L
            _queueItems.value = _queueItems.value.map {
                if (it.id == itemId) {
                    snapshotUpdatedAt = it.updatedAtMillis
                    it.copy(status = SyncQueueStatus.SYNCING)
                } else it
            }
            storage.saveSyncQueue(_queueItems.value)
            return snapshotUpdatedAt
        }
    }

    /**
     * Removes a synced item from the offline sync queue once cloud write completes.
     * If a newer mutation updated the same document while this write was in flight,
     * keeps the newer item in PENDING state so it is immediately flushed next.
     */
    fun markSynced(itemId: String, expectedUpdatedAtMillis: Long = 0L) {
        synchronized(lock) {
            val current = _queueItems.value.find { it.id == itemId } ?: return
            if (expectedUpdatedAtMillis > 0L && current.updatedAtMillis != expectedUpdatedAtMillis) {
                _queueItems.value = _queueItems.value.map {
                    if (it.id == itemId && it.status == SyncQueueStatus.SYNCING) {
                        it.copy(status = SyncQueueStatus.PENDING)
                    } else it
                }
            } else {
                _queueItems.value = _queueItems.value.filterNot { it.id == itemId }
                val hadConflict = _conflicts.value.any {
                    !it.resolved && it.collection == current.collection && it.docId == current.docId
                }
                if (hadConflict) {
                    _conflicts.value = _conflicts.value.map { c ->
                        if (!c.resolved && c.collection == current.collection && c.docId == current.docId) {
                            c.copy(resolved = true, resolution = "Synced cleanly")
                        } else c
                    }
                    storage.saveSyncConflicts(_conflicts.value)
                }
            }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun markFailed(itemId: String, errorMessage: String, expectedUpdatedAtMillis: Long = 0L) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val current = _queueItems.value.find { it.id == itemId } ?: return
            if (expectedUpdatedAtMillis > 0L && current.updatedAtMillis != expectedUpdatedAtMillis) {
                // Newer mutation superseded this failed attempt; keep as PENDING for immediate retry
                _queueItems.value = _queueItems.value.map {
                    if (it.id == itemId && it.status == SyncQueueStatus.SYNCING) {
                        it.copy(status = SyncQueueStatus.PENDING, nextRetryAtMillis = 0L)
                    } else it
                }
            } else {
                _queueItems.value = _queueItems.value.map { prev ->
                    if (prev.id == itemId) {
                        val attempts = prev.attemptCount + 1
                        val backoffMs = min(300_000L, (2.0.pow(attempts.coerceAtMost(8)) * 1000.0).toLong())
                        prev.copy(
                            status = SyncQueueStatus.FAILED,
                            attemptCount = attempts,
                            lastError = errorMessage.take(240),
                            nextRetryAtMillis = now + backoffMs
                        )
                    } else prev
                }
            }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun retryItemNow(itemId: String) {
        synchronized(lock) {
            _queueItems.value = _queueItems.value.map {
                if (it.id == itemId) {
                    it.copy(status = SyncQueueStatus.PENDING, nextRetryAtMillis = 0L)
                } else it
            }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun retryActionGroup(groupId: String) {
        synchronized(lock) {
            _queueItems.value = _queueItems.value.map {
                if (it.actionGroupId.ifBlank { it.id } == groupId) {
                    it.copy(status = SyncQueueStatus.PENDING, nextRetryAtMillis = 0L)
                } else it
            }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun retryAllFailed() {
        synchronized(lock) {
            _queueItems.value = _queueItems.value.map {
                if (it.status == SyncQueueStatus.FAILED) {
                    it.copy(status = SyncQueueStatus.PENDING, nextRetryAtMillis = 0L)
                } else it
            }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun clearCompletedSynced() {
        synchronized(lock) {
            _queueItems.value = _queueItems.value.filterNot { it.status == SyncQueueStatus.SYNCED }
            storage.saveSyncQueue(_queueItems.value)
        }
    }

    fun recordConflict(conflict: SyncConflictRecord) {
        synchronized(lock) {
            val deduplicated = _conflicts.value.filterNot {
                it.collection == conflict.collection && it.docId == conflict.docId && !it.resolved
            }
            _conflicts.value = (listOf(conflict) + deduplicated).take(100)
            storage.saveSyncConflicts(_conflicts.value)
        }
    }

    fun clearFalsePositiveWorkflowConflicts() {
        synchronized(lock) {
            val pendingIds = _queueItems.value
                .filter { it.status != SyncQueueStatus.SYNCED }
                .map { it.id }
                .toSet()
            var changed = false
            val updated = _conflicts.value.map { c ->
                val docKey = "${c.collection}|${c.docId}"
                val isWorkflowDoc = c.collection in setOf("expenses", "payments", "deliveries") && docKey !in pendingIds
                if (!c.resolved && isWorkflowDoc) {
                    changed = true
                    c.copy(resolved = true, resolution = "Normal operational approval (auto-cleared)")
                } else c
            }
            if (changed) {
                _conflicts.value = updated
                storage.saveSyncConflicts(updated)
            }
        }
    }

    fun markConflictResolved(conflictId: String) {
        synchronized(lock) {
            _conflicts.value = _conflicts.value.map {
                if (it.id == conflictId) it.copy(resolved = true) else it
            }
            storage.saveSyncConflicts(_conflicts.value)
        }
    }

    companion object {
        fun defaultActionTitle(collection: String, operation: SyncOperationType): String {
            val verb = if (operation == SyncOperationType.DELETE) "Delete" else "Save"
            val label = when (collection) {
                "customers" -> "Customer"
                "deliveries" -> "Delivery"
                "payments" -> "Payment"
                "invoices" -> "Invoice"
                "expenses" -> "Expense"
                "expenseCategories" -> "Expense Category"
                "bottleSizes" -> "Bottle Size"
                "bottleInventory" -> "Bottle Stock"
                "bottlePurchases" -> "Bottle Purchase"
                "bottleRefills" -> "Bottle Refill"
                "bottleIssueSales" -> "Bottle Issue"
                "bottleMovements" -> "Bottle Movement"
                "customerRates" -> "Customer Rate"
                "customerBottleAllocations" -> "Bottle Allocation"
                "securityDeposits" -> "Security Deposit"
                "factoryMaterials" -> "Factory Material"
                "materialTransactions" -> "Material Transaction"
                "roPlantItems" -> "RO Plant Item"
                "roItemTransactions" -> "RO Item Transaction"
                "bottleCostings" -> "Bottle Costing"
                "monthlyRecurringExpenses" -> "Recurring Expense"
                "cashClosings" -> "Cash Closing"
                "staffUsers", "staffByEmail", "users" -> "Staff Account"
                "plants", "plantWorkspaces" -> "Plant Branch"
                "appSettings" -> "App Settings"
                else -> collection
            }
            return "$verb $label"
        }

        fun deltaMapFromJson(json: String): Map<String, Number>? {
            if (json.isBlank()) return null
            return try {
                val obj = JSONObject(json)
                val out = linkedMapOf<String, Number>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (v is Number) {
                        out[k] = v
                    }
                }
                out.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        fun mapFromPayloadJson(json: String): MutableMap<String, Any?>? {
            if (json.isBlank()) return null
            return try {
                val obj = JSONObject(json)
                jsonObjectToMap(obj)
            } catch (_: Exception) {
                null
            }
        }

        private fun jsonObjectToMap(obj: JSONObject): MutableMap<String, Any?> {
            val map = linkedMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)
                if (v != null && v != JSONObject.NULL) {
                    map[k] = jsonValueToKotlin(v)
                }
            }
            return map
        }

        private fun jsonValueToKotlin(v: Any): Any? {
            return when (v) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until v.length()) {
                        val elem = v.opt(i)
                        if (elem != null && elem != JSONObject.NULL) {
                            list.add(jsonValueToKotlin(elem))
                        }
                    }
                    list
                }
                else -> v
            }
        }
    }
}
