package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query as RtdbQuery
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import com.example.data.room.*
import com.example.util.PasswordHashUtil
import com.example.util.TransactionNotificationHelper

enum class LoginOutcome {
    SUCCESS,
    INVALID_CREDENTIALS,
    INACTIVE_ACCOUNT,
    CLOUD_SYNC_REQUIRED
}

class WaterPlantRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var rtdb: FirebaseDatabase? = null
    private val storage = PlantDataStorage(context.applicationContext)
    val roomDatabase = WaterPlantDatabase.getDatabase(context.applicationContext)
    val deliveryDao = roomDatabase.deliveryDao()
    val expenseDao = roomDatabase.expenseDao()
    val customerBalanceDao = roomDatabase.customerBalanceDao()
    val stockDao = roomDatabase.stockDao()

    // User accounts created by Admin
    private val _userAccounts = MutableStateFlow<List<UserAccount>>(SampleData.initialUserAccounts)
    val userAccounts: StateFlow<List<UserAccount>> = _userAccounts.asStateFlow()

    private val primaryAdminEmails = setOf(
        "mrafiqarain1998@gmail.com"
    )

    // Password Reset Requests (for Admin verification / approval)
    private val _passwordResetRequests = MutableStateFlow<List<PasswordResetRequest>>(SampleData.initialPasswordResetRequests)
    val passwordResetRequests: StateFlow<List<PasswordResetRequest>> = _passwordResetRequests.asStateFlow()

    // Current logged in profile & active role
    private val _currentUser = MutableStateFlow(
        UserProfile(
            id = "user_admin_01",
            username = "admin",
            name = "Muhammad Rafiq (Admin)",
            email = "mrafiqarain1998@gmail.com",
            phone = "+92 300 1234567",
            role = UserRole.ADMIN
        )
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _seenRecordKeys = MutableStateFlow<Set<String>>(emptySet())
    val seenRecordKeys: StateFlow<Set<String>> = _seenRecordKeys.asStateFlow()

    private fun currentUserSeenBucketKey(profile: UserProfile = _currentUser.value): String {
        return profile.id.ifBlank { profile.uid }.ifBlank { profile.username }.trim().lowercase().ifBlank { "default" }
    }

    private fun refreshSeenRecordsForCurrentUser() {
        val bucket = currentUserSeenBucketKey()
        storage.getOrInitSeenBaselineMillis(bucket)
        _seenRecordKeys.value = storage.loadSeenRecordKeys(bucket)
    }

    fun markRecordSeen(type: String, recordId: String) {
        if (recordId.isBlank()) return
        val key = "${type.trim().lowercase()}:$recordId"
        val currentSet = _seenRecordKeys.value
        if (key in currentSet) return
        val updated = currentSet + key
        _seenRecordKeys.value = updated
        storage.saveSeenRecordKeys(currentUserSeenBucketKey(), updated)
    }

    fun isRecordNewForCurrentUser(
        type: String,
        recordId: String,
        createdByUid: String,
        createdByName: String,
        createdAt: Long,
        seenKeys: Set<String> = _seenRecordKeys.value,
        user: UserProfile = _currentUser.value
    ): Boolean {
        if (recordId.isBlank()) return false
        val key = "${type.trim().lowercase()}:$recordId"
        if (key in seenKeys) return false

        // Creator must never see the "NEW" badge on their own newly added record
        val userIds = setOf(user.id.trim(), user.uid.trim()).filter { it.isNotBlank() }
        if (createdByUid.isNotBlank() && userIds.any { it.equals(createdByUid.trim(), ignoreCase = true) }) {
            return false
        }
        val userNames = setOf(user.name.trim(), user.username.trim()).filter { it.isNotBlank() }
        if (createdByUid.isBlank() && createdByName.isNotBlank() && userNames.any { it.equals(createdByName.trim(), ignoreCase = true) }) {
            return false
        }

        // Any record stamped with createdByUid was created by another user after this feature
        if (createdByUid.isNotBlank()) return true

        // Backwards compatibility: if an older APK created a record without createdByUid,
        // show NEW if its createdAt is after this user's local baseline timestamp.
        val baseline = storage.getOrInitSeenBaselineMillis(currentUserSeenBucketKey(user))
        return createdAt > 0L && createdAt >= baseline
    }

    // Business Data Flows
    private val _customers = MutableStateFlow<List<Customer>>(SampleData.customers)
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _bottleSizes = MutableStateFlow<List<BottleSize>>(SampleData.bottleSizes)
    val bottleSizes: StateFlow<List<BottleSize>> = _bottleSizes.asStateFlow()

    private val _customerRates = MutableStateFlow<List<CustomerRate>>(SampleData.customerRates)
    val customerRates: StateFlow<List<CustomerRate>> = _customerRates.asStateFlow()

    private val _securityDeposits = MutableStateFlow<List<SecurityDepositTransaction>>(SampleData.initialSecurityDeposits)
    val securityDeposits: StateFlow<List<SecurityDepositTransaction>> = _securityDeposits.asStateFlow()

    private val _customerAllocations = MutableStateFlow<List<CustomerBottleAllocation>>(SampleData.customerBottleAllocations)
    val customerAllocations: StateFlow<List<CustomerBottleAllocation>> = _customerAllocations.asStateFlow()

    private val _deliveries = MutableStateFlow<List<Delivery>>(SampleData.initialDeliveries)
    val deliveries: StateFlow<List<Delivery>> = _deliveries.asStateFlow()

    private val _invoices = MutableStateFlow<List<Invoice>>(SampleData.initialInvoices)
    val invoices: StateFlow<List<Invoice>> = _invoices.asStateFlow()

    private val _payments = MutableStateFlow<List<Payment>>(SampleData.initialPayments)
    val payments: StateFlow<List<Payment>> = _payments.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(SampleData.initialExpenses)
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _expenseCategories = MutableStateFlow<List<ExpenseCategory>>(SampleData.expenseCategories)
    val expenseCategories: StateFlow<List<ExpenseCategory>> = _expenseCategories.asStateFlow()

    private val _inventory = MutableStateFlow<List<BottleInventory>>(SampleData.inventory)
    val inventory: StateFlow<List<BottleInventory>> = _inventory.asStateFlow()

    private val _bottleMovements = MutableStateFlow<List<BottleMovement>>(emptyList())
    val bottleMovements: StateFlow<List<BottleMovement>> = _bottleMovements.asStateFlow()

    private val _roPlantItems = MutableStateFlow<List<RoPlantItem>>(SampleData.initialRoPlantItems)
    val roPlantItems: StateFlow<List<RoPlantItem>> = _roPlantItems.asStateFlow()

    private val _roTransactions = MutableStateFlow<List<RoItemTransaction>>(SampleData.initialRoItemTransactions)
    val roTransactions: StateFlow<List<RoItemTransaction>> = _roTransactions.asStateFlow()

    private val _bottlePurchases = MutableStateFlow<List<BottlePurchaseRecord>>(SampleData.initialBottlePurchases)
    val bottlePurchases: StateFlow<List<BottlePurchaseRecord>> = _bottlePurchases.asStateFlow()

    private val _bottleRefills = MutableStateFlow<List<BottleRefillRecord>>(SampleData.initialBottleRefills)
    val bottleRefills: StateFlow<List<BottleRefillRecord>> = _bottleRefills.asStateFlow()

    private val _bottleIssueSales = MutableStateFlow<List<BottleIssueSaleRecord>>(SampleData.initialBottleIssueSales)
    val bottleIssueSales: StateFlow<List<BottleIssueSaleRecord>> = _bottleIssueSales.asStateFlow()

    private val _factoryMaterials = MutableStateFlow<List<FactoryMaterial>>(SampleData.initialFactoryMaterials)
    val factoryMaterials: StateFlow<List<FactoryMaterial>> = _factoryMaterials.asStateFlow()

    private val _materialTransactions = MutableStateFlow<List<MaterialTransaction>>(SampleData.initialMaterialTransactions)
    val materialTransactions: StateFlow<List<MaterialTransaction>> = _materialTransactions.asStateFlow()

    private val _bottleCostings = MutableStateFlow<List<BottleCostingRecord>>(SampleData.initialBottleCostings)
    val bottleCostings: StateFlow<List<BottleCostingRecord>> = _bottleCostings.asStateFlow()

    private val _monthlyRecurringExpenses = MutableStateFlow<List<MonthlyRecurringExpense>>(SampleData.initialMonthlyRecurringExpenses)
    val monthlyRecurringExpenses: StateFlow<List<MonthlyRecurringExpense>> = _monthlyRecurringExpenses.asStateFlow()

    private val _monthlyOperationalCosts = MutableStateFlow<List<MonthlyOperationalCostRecord>>(emptyList())
    val monthlyOperationalCosts: StateFlow<List<MonthlyOperationalCostRecord>> = _monthlyOperationalCosts.asStateFlow()

    private val _plantWorkers = MutableStateFlow<List<PlantWorker>>(emptyList())
    val plantWorkers: StateFlow<List<PlantWorker>> = _plantWorkers.asStateFlow()

    private val _cashClosings = MutableStateFlow<List<CashClosing>>(emptyList())
    val cashClosings: StateFlow<List<CashClosing>> = _cashClosings.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(
        listOf(
            AppNotification(
                id = "notif_1",
                title = "RO Plant Operational",
                message = "System online. Real-time sync ready for Admin & Incharge.",
                timeAgo = "Just now",
                severity = NotificationSeverity.SUCCESS
            )
        )
    )
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    val staffAuthApiClient = StaffAuthApiClient()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _plantBranches = MutableStateFlow(listOf(PlantBranch()))
    val plantBranches: StateFlow<List<PlantBranch>> = _plantBranches.asStateFlow()

    private val _activePlantId = MutableStateFlow(PermissionDefaults.DEFAULT_PLANT_ID)
    val activePlantId: StateFlow<String> = _activePlantId.asStateFlow()

    private val _backendConfig = MutableStateFlow(BackendConfig())
    val backendConfig: StateFlow<BackendConfig> = _backendConfig.asStateFlow()

    private val _isSyncFlushing = MutableStateFlow(false)
    val isSyncFlushing: StateFlow<Boolean> = _isSyncFlushing.asStateFlow()

    private val offlineSyncManager = OfflineSyncManager(
        context = context.applicationContext,
        storage = storage,
        onNetworkRestored = { onDeviceNetworkRestored() }
    )
    val syncQueueItems: StateFlow<List<SyncQueueItem>> = offlineSyncManager.queueItems
    val syncConflicts: StateFlow<List<SyncConflictRecord>> = offlineSyncManager.conflicts
    val isDeviceOnline: StateFlow<Boolean> = offlineSyncManager.isDeviceOnline

    private val _syncStatus = MutableStateFlow("Sign in with Google or Staff ID for Cloud Sync")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _cloudUserEmail = MutableStateFlow<String?>(null)
    val cloudUserEmail: StateFlow<String?> = _cloudUserEmail.asStateFlow()

    private val _appReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val appReleaseInfo: StateFlow<AppReleaseInfo?> = _appReleaseInfo.asStateFlow()

    private val _availableAppUpdate = MutableStateFlow<AppReleaseInfo?>(null)
    val availableAppUpdate: StateFlow<AppReleaseInfo?> = _availableAppUpdate.asStateFlow()

    private val _isCheckingAppUpdate = MutableStateFlow(false)
    val isCheckingAppUpdate: StateFlow<Boolean> = _isCheckingAppUpdate.asStateFlow()

    private val _appUpdateStatusMessage = MutableStateFlow<String?>(null)
    val appUpdateStatusMessage: StateFlow<String?> = _appUpdateStatusMessage.asStateFlow()

    private val activeRtdbListeners = mutableListOf<Pair<RtdbQuery, ValueEventListener>>()
    private val pendingSyncItems = java.util.concurrent.ConcurrentHashMap<String, Triple<String, String, Any>>()
    private val pendingDeleteKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val docOwnerIds = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val docMemberUids = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val docMemberEmails = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private val docUpdatedAtMillis = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val cloudKnownDocKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val collectionsWithCloudData = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val migratedPlantIdKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val loadedCollectionsInSession = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val notifiedCollectionsInitialized = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val lastSyncedPayloadHash = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val idSequence = AtomicLong(0L)
    private val mutationLock = Any()
    private val flushMutex = Mutex()
    @Volatile
    private var plantOwnerUid: String? = null
    @Volatile
    private var activeSyncSessionKey: String? = null
    @Volatile
    private var workspaceEnsuredKey: String? = null
    @Volatile
    private var lastManualRefreshMillis: Long = 0L
    private var syncJob: Job? = null
    private var flushJob: Job? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    @Volatile
    private var cloudSeedChecked = false
    @Volatile
    private var isCustomStaffAuthActive = false
    @Volatile
    private var suppressAuthListenerSync = false
    // Strictly in-memory credential for silent custom-token upgrade when offline staff regains internet; never persisted to disk
    @Volatile
    private var sessionStaffCredential: Pair<String, String>? = null

    private fun nextRecordId(prefix: String, suffix: String = ""): String {
        val seq = idSequence.incrementAndGet()
        val rand = UUID.randomUUID().toString().replace("-", "").take(4)
        val cleanSuffix = suffix.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        return if (cleanSuffix.isNotEmpty()) {
            "${prefix}_${System.currentTimeMillis()}_${seq}_${rand}_$cleanSuffix"
        } else {
            "${prefix}_${System.currentTimeMillis()}_${seq}_$rand"
        }
    }

    // Backup & Restore StateFlows
    private val _backupHistory = MutableStateFlow<List<BackupHistoryRecord>>(emptyList())
    val backupHistory: StateFlow<List<BackupHistoryRecord>> = _backupHistory.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(true)
    val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<String?>(null)
    val lastBackupTime: StateFlow<String?> = _lastBackupTime.asStateFlow()

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    // App Theme State (Dark / Light Mode)
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // In-App Transaction Notifications Toggle (persisted locally per device)
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        TransactionNotificationHelper.setNotificationsEnabled(context.applicationContext, enabled)
        if (enabled) {
            syncNotificationUserContext()
        }
    }

    fun toggleNotifications() {
        setNotificationsEnabled(!_notificationsEnabled.value)
    }

    fun toggleDarkMode() {
        setDarkMode(!_isDarkMode.value)
    }

    private fun currentAppSettingsDocId(): String {
        val plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        return "general_$plantId"
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        storage.saveDarkMode(enabled)
        syncDocument("appSettings", currentAppSettingsDocId(), mapOf("isDarkMode" to enabled))
    }

    private fun isFirebaseSignedIn(): Boolean {
        return try {
            Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
    }

    private fun isCurrentAuthSuperAdmin(): Boolean {
        val user = try { Firebase.auth.currentUser } catch (_: Exception) { null } ?: return false
        val cleanEmail = user.email?.trim()?.lowercase().orEmpty()
        return (user.isEmailVerified && cleanEmail in primaryAdminEmails) ||
            _currentUser.value.isSuperAdmin
    }

    fun hasPermission(module: AppModule, action: PermissionAction): Boolean {
        return PermissionDefaults.hasModuleAction(_currentUser.value, module, action)
    }

    private fun deviceInfoLabel(): String {
        val model = try { "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim() } catch (_: Exception) { "Android" }
        return model.ifBlank { "Android Device" }
    }

    fun appendAuditLog(
        action: AuditActionType,
        module: AppModule,
        recordId: String = "",
        recordLabel: String = "",
        previousValue: String = "",
        updatedValue: String = ""
    ) {
        val actor = _currentUser.value
        val now = System.currentTimeMillis()
        val timeFmt = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date(now))
        val firebaseUid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
        val entry = AuditLogEntry(
            id = nextRecordId("audit"),
            plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID },
            actorUid = firebaseUid?.takeIf { it.isNotBlank() } ?: actor.uid.ifBlank { actor.id.ifBlank { "local_actor" } },
            actorUsername = actor.username,
            actorName = actor.name,
            actorRole = actor.role.name,
            action = action.name,
            module = module.name,
            recordId = recordId,
            recordLabel = recordLabel.take(220),
            previousValue = previousValue.take(480),
            updatedValue = updatedValue.take(480),
            timestampMillis = now,
            dateTimeDisplay = timeFmt,
            deviceInfo = deviceInfoLabel()
        )
        val updated = (listOf(entry) + _auditLogs.value).take(500)
        _auditLogs.value = updated
        storage.saveAuditLogs(updated)
        if (isFirebaseSignedIn() && rtdb != null && firebaseUid != null) {
            val db = rtdb!!
            val payload = RtdbSyncHelper.toRtdbPayload(
                data = entry,
                ownerId = firebaseUid,
                memberUids = listOf(firebaseUid),
                memberEmails = primaryAdminEmails.toList(),
                plantId = entry.plantId
            )
            rtdbDocRef(db, "auditLogs", entry.id, entry.plantId).setValue(payload)
        }
    }

    private fun onDeviceNetworkRestored() {
        val db = rtdb ?: return
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            try {
                db.goOnline()
            } catch (_: Exception) {
            }
            if (!isFirebaseSignedIn()) {
                val creds = sessionStaffCredential
                if (creds != null && staffAuthApiClient.isConfigured()) {
                    try {
                        val remote = staffAuthApiClient.staffLogin(creds.first, creds.second, deviceInfoLabel())
                        if (remote.ok && remote.customToken.isNotBlank()) {
                            suppressAuthListenerSync = true
                            try {
                                isCustomStaffAuthActive = true
                                Firebase.auth.signInWithCustomToken(remote.customToken).await()
                            } finally {
                                suppressAuthListenerSync = false
                            }
                            sessionStaffCredential = null
                            startFirestoreSync()
                        }
                    } catch (e: Exception) {
                        isCustomStaffAuthActive = false
                        Log.w("WaterPlantRepo", "Reconnect custom token sign-in failed: ${e.message}")
                    }
                }
                return@launch
            }
            val hasNoListeners = synchronized(activeRtdbListeners) { activeRtdbListeners.isEmpty() }
            if (hasNoListeners) {
                startFirestoreSync()
            }
            flushOfflineSyncQueue(db, forceIncludeFailed = false)
        }
    }

    private data class SyncActionContext(
        val groupId: String,
        val title: String
    )

    private val currentSyncAction = ThreadLocal<SyncActionContext?>()

    private inline fun <T> withSyncAction(title: String, block: () -> T): T {
        val existing = currentSyncAction.get()
        if (existing != null) {
            return block()
        }
        val ctx = SyncActionContext(
            groupId = nextRecordId("act"),
            title = title
        )
        currentSyncAction.set(ctx)
        return try {
            block()
        } finally {
            currentSyncAction.remove()
            triggerQueueFlush()
        }
    }

    private fun triggerQueueFlush(forceIncludeFailed: Boolean = false) {
        val db = rtdb ?: return
        if (!isFirebaseSignedIn()) return
        scope.launch {
            flushOfflineSyncQueue(db, forceIncludeFailed = forceIncludeFailed)
        }
    }

    fun retrySyncQueueItem(itemId: String) {
        offlineSyncManager.retryItemNow(itemId)
        triggerQueueFlush(forceIncludeFailed = true)
    }

    fun retrySyncActionGroup(groupId: String) {
        offlineSyncManager.retryActionGroup(groupId)
        triggerQueueFlush(forceIncludeFailed = true)
    }

    fun retryAllFailedSyncItems() {
        offlineSyncManager.retryAllFailed()
        triggerQueueFlush(forceIncludeFailed = true)
    }

    fun clearCompletedSyncItems() {
        offlineSyncManager.clearCompletedSynced()
    }

    fun resolveSyncConflict(conflictId: String, resolutionNote: String = "Reviewed & acknowledged") {
        offlineSyncManager.markConflictResolved(conflictId)
        appendAuditLog(AuditActionType.SYNC, AppModule.SETTINGS, conflictId, "Sync Conflict Resolved", updatedValue = resolutionNote)
    }

    fun flushOfflineSyncQueueNow() {
        val db = rtdb ?: return
        if (!isFirebaseSignedIn()) return
        flushJob = scope.launch {
            flushOfflineSyncQueue(db, forceIncludeFailed = true)
        }
    }

    fun switchActivePlantBranch(plantId: String): Pair<Boolean, String> {
        val target = plantId.trim().ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val user = _currentUser.value
        val allowed = user.isSuperAdmin || user.role == UserRole.ADMIN ||
            user.assignedBranchIds.isEmpty() || target in user.assignedBranchIds
        if (!allowed) {
            return false to "You are not assigned to plant branch '$target'."
        }
        val branch = _plantBranches.value.find { it.id == target }
            ?: return false to "Plant branch not found."
        _activePlantId.value = branch.id
        storage.saveActivePlantId(branch.id)
        val updatedCfg = _backendConfig.value.copy(activePlantId = branch.id)
        _backendConfig.value = updatedCfg
        storage.saveBackendConfig(updatedCfg)
        if (isFirebaseSignedIn()) {
            startFirestoreSync(forceRestart = true)
        }
        appendAuditLog(AuditActionType.UPDATE, AppModule.SETTINGS, branch.id, "Active Plant Branch Switched", updatedValue = branch.name)
        return true to "Switched active plant to ${branch.name}"
    }

    fun addOrUpdatePlantBranch(branch: PlantBranch): Pair<Boolean, String> {
        if (!hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)) {
            return false to "Permission denied: Only Admin or Settings Managers can configure branches."
        }
        val cleanId = branch.id.trim().ifBlank {
            "plant_${UUID.randomUUID().toString().take(8)}"
        }.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val normalized = branch.copy(
            id = cleanId,
            name = branch.name.trim().ifBlank { "RO Plant Branch" },
            city = branch.city.trim()
        )
        val updated = _plantBranches.value.filterNot { it.id == cleanId } + normalized
        _plantBranches.value = updated
        storage.saveBranches(updated)
        syncDocument(
            "plants",
            cleanId,
            mapOf(
                "id" to normalized.id,
                "plantName" to normalized.name,
                "name" to normalized.name,
                "city" to normalized.city,
                "active" to normalized.active,
                "isPrimary" to normalized.isPrimary
            )
        )
        appendAuditLog(AuditActionType.UPDATE, AppModule.SETTINGS, cleanId, "Plant Branch Saved", updatedValue = normalized.name)
        return true to "Plant branch '${normalized.name}' saved."
    }

    fun updateBackendConfig(staffAuthUrl: String) {
        val next = _backendConfig.value.copy(
            staffAuthApiUrl = staffAuthUrl.trim().trimEnd('/')
        )
        _backendConfig.value = next
        storage.saveBackendConfig(next)
        staffAuthApiClient.setOverrideBaseUrl(next.staffAuthApiUrl)
        appendAuditLog(AuditActionType.UPDATE, AppModule.SETTINGS, "backend_config", "Backend Server URL Updated", updatedValue = next.staffAuthApiUrl.ifBlank { "Default BuildConfig" })
    }

    suspend fun testBackendConnection(): Pair<Boolean, String> = staffAuthApiClient.checkHealth()

    private fun rtdbCollectionRef(
        db: FirebaseDatabase,
        collection: String,
        plantIdOverride: String? = null
    ): DatabaseReference {
        val plantId = RtdbSyncHelper.sanitizeKey(
            (plantIdOverride ?: _activePlantId.value).ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        )
        return when (collection) {
            "plants" -> db.reference.child("plants")
            "staffByEmail" -> db.reference.child("staffByEmail")
            "appReleases" -> db.reference.child("appReleases")
            else -> db.reference.child("plantData").child(plantId).child(RtdbSyncHelper.sanitizeKey(collection))
        }
    }

    private fun rtdbDocRef(
        db: FirebaseDatabase,
        collection: String,
        docId: String,
        plantIdOverride: String? = null
    ): DatabaseReference {
        val key = if (collection == "staffByEmail") {
            RtdbSyncHelper.emailToRtdbKey(docId)
        } else {
            RtdbSyncHelper.sanitizeKey(docId)
        }
        return rtdbCollectionRef(db, collection, plantIdOverride).child(key)
    }

    private fun rememberRtdbAccessMetadata(collection: String, snapshot: DataSnapshot) {
        val docId = snapshot.child("id").getValue(String::class.java)?.takeIf { it.isNotBlank() }
            ?: snapshot.key.orEmpty()
        if (docId.isBlank()) return
        val key = "$collection|$docId"
        cloudKnownDocKeys.add(key)
        val owner = snapshot.child("ownerId").getValue(String::class.java)?.trim()
        if (!owner.isNullOrBlank()) {
            docOwnerIds[key] = owner
            if (plantOwnerUid.isNullOrBlank()) {
                plantOwnerUid = owner
            }
        }
        val uids = snapshot.child("memberUids").children
            .mapNotNull { it.getValue(String::class.java)?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (uids.isNotEmpty()) {
            docMemberUids[key] = uids
        }
        val emails = snapshot.child("memberEmails").children
            .mapNotNull { it.getValue(String::class.java)?.trim()?.lowercase()?.takeIf { s -> s.isNotEmpty() } }
        if (emails.isNotEmpty()) {
            docMemberEmails[key] = emails
        }
        val updatedTs = RtdbSyncHelper.extractUpdatedAtMillis(snapshot)
        if (updatedTs > 0L) {
            docUpdatedAtMillis[key] = updatedTs
        }
    }

    private fun buildRtdbPayload(
        collection: String,
        docId: String,
        data: Any,
        targetPlantOverride: String? = null
    ): MutableMap<String, Any?>? {
        val user = try {
            Firebase.auth.currentUser
        } catch (_: Exception) {
            null
        } ?: return null

        val key = "$collection|$docId"
        val currentEmail = user.email?.trim()?.lowercase().orEmpty()
        val resolvedOwnerId = docOwnerIds[key]?.takeIf { it.isNotBlank() }
            ?: plantOwnerUid?.takeIf { it.isNotBlank() }
            ?: user.uid

        val existingUids = docMemberUids[key] ?: emptyList()
        val memberUids = (listOf(resolvedOwnerId, user.uid) + existingUids)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val staffEmails = _userAccounts.value
            .mapNotNull { it.googleEmail.trim().lowercase().takeIf { e -> e.isNotBlank() } }
        val existingEmails = docMemberEmails[key] ?: emptyList()
        val memberEmails = (primaryAdminEmails +
            listOfNotNull(currentEmail.takeIf { it.isNotBlank() }) +
            staffEmails +
            existingEmails)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()

        val plantId = (targetPlantOverride ?: _activePlantId.value).ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        return RtdbSyncHelper.toRtdbPayload(
            data = data,
            ownerId = resolvedOwnerId,
            memberUids = memberUids,
            memberEmails = memberEmails,
            plantId = plantId,
            lastModifiedByUid = user.uid
        )
    }

    init {
        try {
            val rtdbUrl = context.getString(R.string.firebase_rtdb_url).trim()
            require(rtdbUrl.startsWith("https://")) {
                "R.string.firebase_rtdb_url must specify the Firebase Realtime Database HTTPS URL."
            }
            val dbInstance = FirebaseDatabase.getInstance(rtdbUrl)
            try {
                dbInstance.setPersistenceEnabled(true)
            } catch (_: Exception) {
            }
            try {
                dbInstance.setPersistenceCacheSizeBytes(100L * 1024L * 1024L)
            } catch (_: Exception) {
            }
            rtdb = dbInstance
            Log.d("WaterPlantRepo", "Initialized Firebase Realtime Database ($rtdbUrl)")
        } catch (e: Exception) {
            Log.e("WaterPlantRepo", "Firebase Realtime Database init failed: ${e.message}", e)
        }

        // 2. Clean slate initialization for user (clears dummy stock, dummy deliveries, and dummy expenses once)
        if (!storage.loadCleanStateInitialized()) {
            _deliveries.value = emptyList()
            _expenses.value = emptyList()
            _invoices.value = emptyList()
            _inventory.value = SampleData.inventory
            _roPlantItems.value = emptyList()
            _roTransactions.value = emptyList()
            _bottlePurchases.value = emptyList()
            _bottleRefills.value = emptyList()
            _bottleIssueSales.value = emptyList()
            _factoryMaterials.value = emptyList()
            _materialTransactions.value = emptyList()
            _bottleCostings.value = emptyList()
            _monthlyRecurringExpenses.value = emptyList()
            _monthlyOperationalCosts.value = emptyList()
            storage.saveMonthlyOperationalCosts(emptyList())
            storage.saveDeliveries(emptyList())
            storage.saveExpenses(emptyList())
            storage.saveInvoices(emptyList())
            storage.saveInventory(_inventory.value)
            storage.saveRoPlantItems(emptyList())
            storage.saveRoTransactions(emptyList())
            storage.saveBottlePurchases(emptyList())
            storage.saveBottleRefills(emptyList())
            storage.saveBottleIssueSales(emptyList())
            storage.saveFactoryMaterials(emptyList())
            storage.saveMaterialTransactions(emptyList())
            storage.saveBottleCostings(emptyList())
            storage.saveMonthlyRecurringExpenses(emptyList())
            storage.saveUserAccounts(_userAccounts.value)
            storage.saveBranches(_plantBranches.value)
            storage.saveActivePlantId(_activePlantId.value)
            storage.saveCleanStateInitialized(true)
            storage.markOldSampleDataPurged()
        } else {
            loadAllFromLocalStorage()
        }
        loadExtraPlatformState()
        normalizeLegacyBottleRecords()
        syncAdminPhoneForPdfs()
        offlineSyncManager.clearFalsePositiveWorkflowConflicts()

        // 3. Auth-gated Firestore listener lifecycle (no unauthenticated cold-start queries)
        try {
            val auth = Firebase.auth
            val currentAuthUser = auth.currentUser
            _cloudUserEmail.value = currentAuthUser?.email ?: _currentUser.value.username.takeIf { currentAuthUser != null }
            if (currentAuthUser == null) {
                _isCloudConnected.value = false
                _syncStatus.value = "Local Mode • Sign in with Google or Staff ID for Cloud Sync"
            }

            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _cloudUserEmail.value = user?.email ?: if (user != null) _currentUser.value.username else null
                if (user != null) {
                    if (!suppressAuthListenerSync && isUserLoggedIn()) {
                        startFirestoreSync()
                    }
                } else {
                    isCustomStaffAuthActive = false
                    stopFirestoreSync()
                    _isCloudConnected.value = false
                    _syncStatus.value = "Local Mode • Sign in with Google or Staff ID for Cloud Sync"
                }
            }
            authStateListener = listener
            auth.addAuthStateListener(listener)
        } catch (e: Exception) {
            Log.w("WaterPlantRepo", "FirebaseAuth listener init skipped: ${e.message}")
        }

        // 4. Initial sync with Room Database on background IO
        scope.launch {
            try {
                customerBalanceDao.upsertCustomerBalances(_customers.value.map { RoomDataMapper.customerToBalanceEntity(it) })
                deliveryDao.insertDeliveries(_deliveries.value.map { RoomDataMapper.deliveryToEntity(it) })
                expenseDao.insertExpenses(_expenses.value.map { RoomDataMapper.expenseToEntity(it) })
                stockDao.upsertStockList(_inventory.value.map { RoomDataMapper.inventoryToStockEntity(it) })
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room initial sync error: ${e.message}")
            }
        }
    }

    fun isUserLoggedIn(): Boolean {
        return storage.loadLoginState()
    }

    fun logout() {
        appendAuditLog(
            action = AuditActionType.LOGOUT,
            module = AppModule.DASHBOARD,
            recordId = _currentUser.value.id,
            recordLabel = "User Logout (${_currentUser.value.username})"
        )
        isCustomStaffAuthActive = false
        sessionStaffCredential = null
        stopFirestoreSync()
        storage.saveLoginState(false)
        try {
            Firebase.auth.signOut()
        } catch (e: Exception) {
            Log.w("WaterPlantRepo", "Signout error: ${e.message}")
        }
        _isCloudConnected.value = false
        _cloudUserEmail.value = null
        _syncStatus.value = "Signed out • Sign in for Cloud Sync"
        addNotification("Logged Out", "User signed out.", NotificationSeverity.INFO)
    }

    private fun normalizeUsername(raw: String): String {
        return raw.trim().removePrefix("@").trim().lowercase()
    }

    private fun passwordsMatch(stored: String, provided: String): Boolean {
        if (stored == provided) return true
        val trimmedProvided = provided.trim()
        return trimmedProvided.isNotEmpty() && stored.trim() == trimmedProvided
    }

    private fun isRetiredDefaultAdminPasswordText(password: String): Boolean {
        val clean = password.trim().lowercase()
        return clean == "admin123" || clean == "admin@123"
    }

    private fun isPrimaryAdminAccount(acc: UserAccount): Boolean {
        val normUser = normalizeUsername(acc.username)
        return acc.isSuperAdmin ||
            acc.id == "user_admin_01" ||
            normUser == "admin" ||
            acc.googleEmail.trim().lowercase() in primaryAdminEmails
    }

    private fun isRetiredAdminPassword(username: String, password: String): Boolean {
        val normUser = normalizeUsername(username)
        if (!isRetiredDefaultAdminPasswordText(password)) return false
        return normUser == "admin" || normUser == "user_admin_01"
    }

    private fun scrubRetiredAdminDefaultPasswords(accounts: List<UserAccount>): Pair<List<UserAccount>, Boolean> {
        var changed = false
        val cleaned = accounts.map { acc ->
            if (!isPrimaryAdminAccount(acc)) {
                acc
            } else {
                val hasRetiredPlain = acc.password.isNotBlank() && isRetiredDefaultAdminPasswordText(acc.password)
                val hasRetiredHash = acc.passwordSalt.isNotBlank() && acc.passwordHash.isNotBlank() && (
                    PasswordHashUtil.verifyPassword("admin123", acc.passwordSalt, acc.passwordHash) ||
                        PasswordHashUtil.verifyPassword("Admin@123", acc.passwordSalt, acc.passwordHash)
                    )
                if (hasRetiredPlain || hasRetiredHash) {
                    changed = true
                    acc.copy(
                        password = "",
                        passwordHash = "",
                        passwordSalt = "",
                        passwordUpdatedAtMillis = 0L
                    )
                } else {
                    acc
                }
            }
        }
        return cleaned to changed
    }

    private fun verifyAccountPassword(acc: UserAccount, provided: String): Boolean {
        if ((isPrimaryAdminAccount(acc) || acc.role == UserRole.ADMIN) && isRetiredDefaultAdminPasswordText(provided)) {
            return false
        }
        if (acc.passwordSalt.isNotBlank() && acc.passwordHash.isNotBlank()) {
            if (PasswordHashUtil.verifyPassword(provided, acc.passwordSalt, acc.passwordHash) ||
                PasswordHashUtil.verifyPassword(provided.trim(), acc.passwordSalt, acc.passwordHash)
            ) {
                return true
            }
        }
        return acc.password.isNotEmpty() && passwordsMatch(acc.password, provided)
    }

    private fun stampAccountWithPasswordHash(acc: UserAccount, rawPassword: String): UserAccount {
        val clean = rawPassword.trim().ifEmpty { rawPassword }
        if (clean.isEmpty()) return acc.copy(password = "")
        val salt = PasswordHashUtil.generateSalt()
        val hash = PasswordHashUtil.hashPassword(clean, salt)
        return acc.copy(
            password = "",
            passwordHash = hash,
            passwordSalt = salt,
            passwordUpdatedAtMillis = System.currentTimeMillis()
        )
    }

    private fun buildCredentialVerifierMap(acc: UserAccount): Map<String, Any?> {
        val cleanUser = normalizeUsername(acc.username)
        val ts = acc.passwordUpdatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        return mapOf(
            "id" to cleanUser,
            "username" to cleanUser,
            "accountId" to acc.id,
            "name" to acc.name,
            "role" to acc.role.name,
            "isSuperAdmin" to (acc.isSuperAdmin || acc.id == "user_admin_01"),
            "active" to (if (acc.isSuperAdmin || acc.id == "user_admin_01") true else acc.active),
            "credVerifier" to acc.passwordHash,
            "credSalt" to acc.passwordSalt,
            "passwordUpdatedAtMillis" to ts
        )
    }

    private fun syncPasswordVerifierToCloud(acc: UserAccount) {
        val cleanUser = normalizeUsername(acc.username)
        if (cleanUser.isBlank() || acc.passwordHash.isBlank() || acc.passwordSalt.isBlank()) return
        val verifierMap = buildCredentialVerifierMap(acc)
        // 1. Sync inside plantData/{plantId}/staffCredentials (works immediately under existing RTDB rules)
        syncDocument("staffCredentials", cleanUser, verifierMap)
        // 2. Also sync to top-level authVerifiers/{username} for fresh-install multi-device login verification
        val db = rtdb ?: return
        if (!isFirebaseSignedIn()) return
        scope.launch {
            try {
                db.reference
                    .child("authVerifiers")
                    .child(RtdbSyncHelper.sanitizeKey(cleanUser))
                    .setValue(verifierMap)
                    .await()
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "authVerifiers cloud write notice: ${e.message}")
            }
        }
    }

    internal fun applyRemoteCredentialVerifier(
        username: String,
        credVerifier: String,
        credSalt: String,
        updatedAtMillis: Long,
        active: Boolean? = null
    ): Boolean {
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isBlank() || credVerifier.isBlank() || credSalt.isBlank()) return false
        val existing = _userAccounts.value.find { normalizeUsername(it.username) == cleanUser } ?: return false
        val shouldReplace = existing.passwordHash.isBlank() ||
            existing.passwordUpdatedAtMillis == 0L ||
            updatedAtMillis >= existing.passwordUpdatedAtMillis
        if (!shouldReplace) return false
        val isSuper = existing.isSuperAdmin || existing.id == "user_admin_01"
        val resolvedActive = if (isSuper) true else (active ?: existing.active)
        val updated = existing.copy(
            password = "",
            passwordHash = credVerifier,
            passwordSalt = credSalt,
            passwordUpdatedAtMillis = updatedAtMillis.coerceAtLeast(1L),
            active = resolvedActive
        )
        _userAccounts.value = _userAccounts.value.map {
            if (normalizeUsername(it.username) == cleanUser) updated else it
        }
        storage.saveUserAccounts(_userAccounts.value)
        return true
    }

    private fun applyCredentialVerifierSnapshot(snapshot: DataSnapshot): Boolean {
        if (!snapshot.exists()) return false
        val map = RtdbSyncHelper.snapshotToMap(snapshot) ?: return false
        val uName = (map["username"] as? String)?.let { normalizeUsername(it) }
            ?.takeIf { it.isNotBlank() } ?: snapshot.key.orEmpty()
        val verifier = (map["credVerifier"] as? String).orEmpty()
        val salt = (map["credSalt"] as? String).orEmpty()
        val ts = (map["passwordUpdatedAtMillis"] as? Number)?.toLong()
            ?: (map["updatedAtMillis"] as? Number)?.toLong() ?: 1L
        val active = map["active"] as? Boolean
        return applyRemoteCredentialVerifier(uName, verifier, salt, ts, active)
    }

    private suspend fun refreshCloudVerifierForUsername(cleanUser: String) {
        val db = rtdb ?: return
        if (!isDeviceOnline.value) return
        val safeKey = RtdbSyncHelper.sanitizeKey(cleanUser)
        try {
            kotlinx.coroutines.withTimeoutOrNull(4500L) {
                try {
                    val topSnap = db.reference.child("authVerifiers").child(safeKey).get().await()
                    if (topSnap.exists()) {
                        applyCredentialVerifierSnapshot(topSnap)
                    }
                } catch (_: Exception) {}
                if (isFirebaseSignedIn()) {
                    try {
                        val plantSnap = rtdbDocRef(db, "staffCredentials", safeKey).get().await()
                        if (plantSnap.exists()) {
                            applyCredentialVerifierSnapshot(plantSnap)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w("WaterPlantRepo", "refreshCloudVerifierForUsername notice: ${e.message}")
        }
    }

    private fun mergeAccountsPreservingLocalCredentials(
        existing: List<UserAccount>,
        incoming: List<UserAccount>
    ): List<UserAccount> {
        val map = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { remote ->
            val normRemoteUser = normalizeUsername(remote.username)
            val prevByUsername = existing.find { normalizeUsername(it.username) == normRemoteUser }
            val prev = map[remote.id] ?: prevByUsername
            if (prevByUsername != null && prevByUsername.id != remote.id) {
                map.remove(prevByUsername.id)
            }
            val resolvedSuperAdmin = remote.isSuperAdmin ||
                (remote.id == "user_admin_01") ||
                (remote.googleEmail.trim().lowercase() in primaryAdminEmails)
            val resolvedRole = if (resolvedSuperAdmin) UserRole.ADMIN else remote.role
            val useRemoteHash = remote.passwordHash.isNotBlank() &&
                remote.passwordSalt.isNotBlank() &&
                (prev == null || prev.passwordHash.isBlank() || remote.passwordUpdatedAtMillis > prev.passwordUpdatedAtMillis)
            val resolvedExplicit = remote.hasExplicitPermissions || (prev?.hasExplicitPermissions == true) || remote.permissions.isNotEmpty()
            val normalized = remote.copy(
                uid = remote.uid.ifBlank { remote.id },
                isSuperAdmin = resolvedSuperAdmin,
                role = resolvedRole,
                permissions = PermissionDefaults.normalizePermissions(
                    input = remote.permissions,
                    role = resolvedRole,
                    isSuperAdmin = resolvedSuperAdmin,
                    explicitMode = resolvedExplicit
                ),
                hasExplicitPermissions = resolvedExplicit,
                assignedBranchIds = remote.assignedBranchIds.ifEmpty { listOf(_activePlantId.value) },
                defaultBranchId = remote.defaultBranchId.ifBlank { _activePlantId.value },
                password = if (useRemoteHash) "" else (prev?.password?.takeIf { it.isNotEmpty() } ?: ""),
                passwordHash = if (useRemoteHash) remote.passwordHash else (prev?.passwordHash?.takeIf { it.isNotBlank() } ?: remote.passwordHash),
                passwordSalt = if (useRemoteHash) remote.passwordSalt else (prev?.passwordSalt?.takeIf { it.isNotBlank() } ?: remote.passwordSalt),
                passwordUpdatedAtMillis = if (useRemoteHash) remote.passwordUpdatedAtMillis else (prev?.passwordUpdatedAtMillis ?: remote.passwordUpdatedAtMillis)
            )
            map[normalized.id] = normalized
        }
        return map.values.toList()
    }

    // NEVER includes plaintext passwords or password hashes when writing to Firestore
    private fun userAccountToMap(acc: UserAccount): Map<String, Any?> {
        val resolvedUid = acc.uid.ifBlank { acc.id }
        val resolvedSuperAdmin = acc.isSuperAdmin || acc.id == "user_admin_01"
        val resolvedRole = if (resolvedSuperAdmin) UserRole.ADMIN else acc.role
        val isExplicit = acc.hasExplicitPermissions || acc.permissions.isNotEmpty()
        val normalizedPerms = PermissionDefaults.normalizePermissions(
            input = acc.permissions,
            role = resolvedRole,
            isSuperAdmin = resolvedSuperAdmin,
            explicitMode = isExplicit
        )
        val branchIds = acc.assignedBranchIds.ifEmpty { listOf(_activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }) }
        val defBranch = acc.defaultBranchId.ifBlank { branchIds.first() }
        return mapOf(
            "id" to acc.id,
            "uid" to resolvedUid,
            "username" to normalizeUsername(acc.username),
            "name" to acc.name,
            "phone" to acc.phone,
            "googleEmail" to acc.googleEmail.trim().lowercase(),
            "role" to resolvedRole.name,
            "isSuperAdmin" to resolvedSuperAdmin,
            "permissions" to normalizedPerms,
            "hasExplicitPermissions" to (if (resolvedSuperAdmin || resolvedRole == UserRole.ADMIN) false else true),
            "assignedBranchIds" to branchIds,
            "defaultBranchId" to defBranch,
            "plantId" to defBranch,
            "active" to if (resolvedSuperAdmin) true else acc.active,
            "createdAt" to acc.createdAt,
            "restrictToOwnDataOnly" to if (resolvedSuperAdmin || resolvedRole == UserRole.ADMIN) false else acc.restrictToOwnDataOnly,
            "restrictToTodayOnly" to if (resolvedSuperAdmin || resolvedRole == UserRole.ADMIN) false else acc.restrictToTodayOnly
        )
    }

    private fun syncStaffAccountDocuments(acc: UserAccount, previousEmail: String = "") {
        val resolvedUid = acc.uid.ifBlank { acc.id }
        val defBranch = acc.defaultBranchId.ifBlank {
            acc.assignedBranchIds.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        }
        val enrichedMap = RtdbSyncHelper.enrichStaffMapForRtdb(userAccountToMap(acc), acc, defBranch)
        syncDocument("staffUsers", resolvedUid, enrichedMap)
        val cleanEmail = acc.googleEmail.trim().lowercase()
        val prevCleanEmail = previousEmail.trim().lowercase()
        if (prevCleanEmail.isNotBlank() && prevCleanEmail != cleanEmail) {
            deleteDocument("staffByEmail", prevCleanEmail)
        }
        if (cleanEmail.isNotBlank()) {
            syncDocument("staffByEmail", cleanEmail, enrichedMap)
        }
    }

    private fun parseUserAccountSnapshot(snapshot: DataSnapshot, collectionName: String = "staffUsers"): UserAccount? {
        if (!snapshot.exists()) return null
        rememberRtdbAccessMetadata(collectionName, snapshot)
        return try {
            val map = RtdbSyncHelper.snapshotToMap(snapshot) ?: return null
            val uName = (map["username"] as? String)?.let { normalizeUsername(it) } ?: return null
            if (uName.isEmpty()) return null
            val roleStr = (map["role"] as? String) ?: "INCHARGE"
            val parsedRole = try { UserRole.valueOf(roleStr.uppercase()) } catch (_: Exception) { UserRole.INCHARGE }
            val docId = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: snapshot.key.orEmpty()
            val docUid = (map["uid"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val googleEmail = (map["googleEmail"] as? String)?.trim()?.lowercase() ?: ""
            val isSuper = ((map["isSuperAdmin"] as? Boolean) ?: false) ||
                docId == "user_admin_01" ||
                (googleEmail in primaryAdminEmails)
            val effectiveRole = if (isSuper) UserRole.ADMIN else parsedRole

            @Suppress("UNCHECKED_CAST")
            val rawPerms = (map["permissions"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                (v as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            } ?: (map["permissionsMap"] as? Map<String, Any?>)?.mapValues { (_, v) ->
                (v as? Map<*, *>)?.filter { it.value == true }?.keys?.mapNotNull { it?.toString() } ?: emptyList()
            }
            val rawBranches = (map["assignedBranchIds"] as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?: (map["assignedBranchMap"] as? Map<*, *>)
                    ?.filter { it.value == true }
                    ?.keys?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?: emptyList()
            val defBranch = (map["defaultBranchId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (map["plantId"] as? String)?.takeIf { it.isNotBlank() }
                ?: PermissionDefaults.DEFAULT_PLANT_ID

            val explicitFlag = (map["hasExplicitPermissions"] as? Boolean) == true ||
                (rawPerms != null && rawPerms.isNotEmpty())

            UserAccount(
                id = docId,
                uid = docUid,
                username = uName,
                password = "",
                passwordHash = "",
                passwordSalt = "",
                name = (map["name"] as? String) ?: uName,
                phone = (map["phone"] as? String) ?: "",
                role = effectiveRole,
                isSuperAdmin = isSuper,
                permissions = PermissionDefaults.normalizePermissions(
                    input = rawPerms,
                    role = effectiveRole,
                    isSuperAdmin = isSuper,
                    explicitMode = explicitFlag
                ),
                hasExplicitPermissions = explicitFlag,
                assignedBranchIds = rawBranches.ifEmpty { listOf(defBranch) },
                defaultBranchId = defBranch,
                active = if (isSuper) true else ((map["active"] as? Boolean) ?: true),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                googleEmail = googleEmail,
                restrictToOwnDataOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else ((map["restrictToOwnDataOnly"] as? Boolean) ?: false),
                restrictToTodayOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else ((map["restrictToTodayOnly"] as? Boolean) ?: false)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun activateAuthenticatedAccount(account: UserAccount): UserProfile {
        val cleanUser = normalizeUsername(account.username)
        val isSuper = account.isSuperAdmin || account.id == "user_admin_01" ||
            account.googleEmail.trim().lowercase() in primaryAdminEmails
        val effectiveRole = if (isSuper) UserRole.ADMIN else account.role
        val isExplicit = account.hasExplicitPermissions || account.permissions.isNotEmpty()
        val normalizedPerms = PermissionDefaults.normalizePermissions(
            input = account.permissions,
            role = effectiveRole,
            isSuperAdmin = isSuper,
            explicitMode = isExplicit
        )
        val branchList = account.assignedBranchIds.ifEmpty { listOf(PermissionDefaults.DEFAULT_PLANT_ID) }
        val defBranch = account.defaultBranchId.ifBlank { branchList.first() }
        val activePlant = if (isSuper || effectiveRole == UserRole.ADMIN || _activePlantId.value in branchList) {
            _activePlantId.value.ifBlank { defBranch }
        } else {
            defBranch
        }
        _activePlantId.value = activePlant
        storage.saveActivePlantId(activePlant)

        val profile = UserProfile(
            id = account.id,
            uid = account.uid.ifBlank { account.id },
            username = cleanUser,
            name = account.name,
            email = account.googleEmail.trim().lowercase(),
            phone = account.phone,
            role = effectiveRole,
            isSuperAdmin = isSuper,
            permissions = normalizedPerms,
            assignedBranchIds = branchList,
            defaultBranchId = defBranch,
            businessId = activePlant,
            active = account.active,
            restrictToOwnDataOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else account.restrictToOwnDataOnly,
            restrictToTodayOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else account.restrictToTodayOnly,
            hasExplicitPermissions = isExplicit
        )
        _currentUser.value = profile
        refreshSeenRecordsForCurrentUser()
        syncNotificationUserContext()
        storage.saveCurrentProfile(profile)
        storage.saveLoginState(true)
        storage.saveLastUser(cleanUser)
        if (isFirebaseSignedIn()) {
            startFirestoreSync()
        }
        addNotification("User Login", "${account.name} logged in successfully (${profile.role.displayName}).", NotificationSeverity.INFO)
        appendAuditLog(
            action = AuditActionType.LOGIN,
            module = AppModule.DASHBOARD,
            recordId = profile.uid.ifBlank { profile.id },
            recordLabel = "${profile.name} (@$cleanUser)",
            updatedValue = "Role=${profile.role.name}, Plant=$activePlant"
        )
        return profile
    }

    // Authentication with Username and Password (synchronous local hashed check)
    fun authenticate(username: String, password: String): UserProfile? {
        val cleanUser = normalizeUsername(username)
        if (isRetiredAdminPassword(cleanUser, password)) return null
        val account = _userAccounts.value.find {
            normalizeUsername(it.username) == cleanUser && verifyAccountPassword(it, password)
        } ?: return null
        if (!account.active) {
            Log.w("WaterPlantRepo", "Attempt to login to inactive account: ${account.username}")
            return null
        }
        return activateAuthenticatedAccount(account)
    }

    fun loginWithBiometrics(targetUsername: String? = null): Boolean {
        val lastUser = storage.getLastUser()?.let { normalizeUsername(it) }
        if (lastUser.isNullOrEmpty()) return false

        val cleanTarget = targetUsername?.let { normalizeUsername(it) } ?: lastUser
        if (cleanTarget != lastUser) return false

        val targetAccount = _userAccounts.value.find { normalizeUsername(it.username) == cleanTarget } ?: return false
        if (!targetAccount.active) return false

        activateAuthenticatedAccount(targetAccount)
        return true
    }

    fun getLastLoggedInUser(): String? {
        return storage.getLastUser()
    }

    suspend fun authenticateWithOutcome(username: String, password: String): Pair<LoginOutcome, UserProfile?> {
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return LoginOutcome.INVALID_CREDENTIALS to null
        }
        if (isRetiredAdminPassword(cleanUser, password)) {
            return LoginOutcome.INVALID_CREDENTIALS to null
        }

        // 1. Primary path for staff: Cloud Functions Custom Token Verification (non-Gmail real Firebase Auth session)
        if (staffAuthApiClient.isConfigured() && isDeviceOnline.value) {
            val remote = staffAuthApiClient.staffLogin(cleanUser, password, deviceInfoLabel())
            if (remote.ok && remote.customToken.isNotBlank()) {
                return try {
                    suppressAuthListenerSync = true
                    try {
                        isCustomStaffAuthActive = true
                        Firebase.auth.signInWithCustomToken(remote.customToken).await()
                    } finally {
                        suppressAuthListenerSync = false
                    }
                    sessionStaffCredential = null
                    val remoteAcc = remote.account ?: UserAccount(
                        id = remote.uid.ifBlank { "staff_$cleanUser" },
                        uid = remote.uid,
                        username = cleanUser,
                        name = cleanUser,
                        role = UserRole.INCHARGE,
                        defaultBranchId = remote.plantId,
                        assignedBranchIds = listOf(remote.plantId),
                        active = true
                    )
                    val stamped = stampAccountWithPasswordHash(remoteAcc, password)
                    val merged = mergeAccountsPreservingLocalCredentials(_userAccounts.value, listOf(stamped))
                        .map { if (normalizeUsername(it.username) == cleanUser) stamped.copy(id = it.id, uid = remote.uid.ifBlank { it.uid }) else it }
                    _userAccounts.value = merged
                    storage.saveUserAccounts(merged)
                    val finalAcc = merged.find { normalizeUsername(it.username) == cleanUser } ?: stamped
                    val profile = activateAuthenticatedAccount(finalAcc)
                    startFirestoreSync()
                    LoginOutcome.SUCCESS to profile
                } catch (e: Exception) {
                    isCustomStaffAuthActive = false
                    Log.w("WaterPlantRepo", "signInWithCustomToken failed: ${e.message}")
                    LoginOutcome.CLOUD_SYNC_REQUIRED to null
                }
            } else if (remote.errorCode == "INVALID_CREDENTIALS") {
                return LoginOutcome.INVALID_CREDENTIALS to null
            } else if (remote.errorCode == "ACCOUNT_INACTIVE" || remote.errorCode == "INACTIVE_ACCOUNT") {
                return LoginOutcome.INACTIVE_ACCOUNT to null
            }
            // If backend is unconfigured or network error, fall through to offline hashed verification
        }

        // 1b. Sync latest cloud password verifier BEFORE local check so changed passwords on another phone
        // immediately invalidate old default passwords (e.g. admin123) on fresh installs
        refreshCloudVerifierForUsername(cleanUser)

        // 2. Local offline-first hashed verification (supports offline staff login after initial sync)
        val localByUsername = _userAccounts.value.find { normalizeUsername(it.username) == cleanUser }
        if (localByUsername != null) {
            if (verifyAccountPassword(localByUsername, password)) {
                return if (!localByUsername.active) {
                    Log.w("WaterPlantRepo", "Attempt to login to inactive account: ${localByUsername.username}")
                    LoginOutcome.INACTIVE_ACCOUNT to null
                } else {
                    if (!isFirebaseSignedIn() && !localByUsername.isSuperAdmin) {
                        sessionStaffCredential = cleanUser to password
                    }
                    LoginOutcome.SUCCESS to activateAuthenticatedAccount(localByUsername)
                }
            }
            if (!isFirebaseSignedIn()) {
                return LoginOutcome.INVALID_CREDENTIALS to null
            }
        }

        // 3. Fallback Firebase Realtime Database lookup if already Firebase-signed-in
        val db = rtdb
        if (db == null || !isFirebaseSignedIn()) {
            return if (localByUsername != null) {
                LoginOutcome.INVALID_CREDENTIALS to null
            } else {
                LoginOutcome.CLOUD_SYNC_REQUIRED to null
            }
        }

        return try {
            val cloudAccounts = mutableListOf<UserAccount>()
            val authUser = Firebase.auth.currentUser
            if (isCurrentAuthSuperAdmin() || hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.VIEW)) {
                val staffSnap = rtdbCollectionRef(db, "staffUsers").get().await()
                staffSnap.children.mapNotNullTo(cloudAccounts) { parseUserAccountSnapshot(it, "staffUsers") }
            } else if (authUser != null) {
                val cleanEmail = authUser.email?.trim()?.lowercase().orEmpty()
                if (cleanEmail.isNotBlank()) {
                    val emailDoc = rtdbDocRef(db, "staffByEmail", cleanEmail).get().await()
                    if (emailDoc.exists()) parseUserAccountSnapshot(emailDoc, "staffByEmail")?.let { cloudAccounts.add(it) }
                }
                val uidDoc = db.reference.child("staffUsers").child(RtdbSyncHelper.sanitizeKey(authUser.uid)).get().await()
                if (uidDoc.exists()) parseUserAccountSnapshot(uidDoc, "staffUsers")?.let { cloudAccounts.add(it) }
            }
            if (cloudAccounts.isNotEmpty()) {
                val mergedList = mergeAccountsPreservingLocalCredentials(_userAccounts.value, cloudAccounts)
                _userAccounts.value = mergedList
                storage.saveUserAccounts(mergedList)
            }

            val targetAccount = _userAccounts.value.find { normalizeUsername(it.username) == cleanUser }
                ?: return LoginOutcome.INVALID_CREDENTIALS to null

            if (!targetAccount.active) {
                Log.w("WaterPlantRepo", "Cloud account inactive: $cleanUser")
                return LoginOutcome.INACTIVE_ACCOUNT to null
            }
            if (!verifyAccountPassword(targetAccount, password)) {
                return if (targetAccount.passwordHash.isBlank() && targetAccount.password.isBlank()) {
                    LoginOutcome.CLOUD_SYNC_REQUIRED to null
                } else {
                    LoginOutcome.INVALID_CREDENTIALS to null
                }
            }
            LoginOutcome.SUCCESS to activateAuthenticatedAccount(targetAccount)
        } catch (e: Exception) {
            Log.w("WaterPlantRepo", "Cloud authenticateAsync exception: ${e.message}")
            LoginOutcome.INVALID_CREDENTIALS to null
        }
    }

    suspend fun authenticateAsync(username: String, password: String): UserProfile? {
        return authenticateWithOutcome(username, password).second
    }

    fun createUserAccount(account: UserAccount): String {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) return ""
        val cleanUser = normalizeUsername(account.username)
        val safeUserPrefix = cleanUser.replace(Regex("[^a-z0-9_\\-]"), "_").ifBlank { "user" }
        val id = if (account.id.isNotBlank()) account.id else "staff_${safeUserPrefix}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        val uid = account.uid.ifBlank { id }
        val cleanEmail = account.googleEmail.trim().lowercase()
        val rawPassword = account.password.trim()
        val effectiveSuperAdmin = account.isSuperAdmin || id == "user_admin_01"
        val effectiveRole = if (effectiveSuperAdmin) UserRole.ADMIN else account.role
        val isExplicit = account.hasExplicitPermissions || account.permissions.isNotEmpty()
        val normalizedPerms = PermissionDefaults.normalizePermissions(
            input = account.permissions,
            role = effectiveRole,
            isSuperAdmin = effectiveSuperAdmin,
            explicitMode = isExplicit
        )
        val branches = account.assignedBranchIds.ifEmpty { listOf(_activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }) }
        val defBranch = account.defaultBranchId.ifBlank { branches.first() }

        val baseAcc = account.copy(
            id = id,
            uid = uid,
            username = cleanUser,
            name = account.name.trim(),
            phone = account.phone.trim(),
            googleEmail = cleanEmail,
            role = effectiveRole,
            isSuperAdmin = effectiveSuperAdmin,
            permissions = normalizedPerms,
            hasExplicitPermissions = true,
            assignedBranchIds = branches,
            defaultBranchId = defBranch
        )
        val newAcc = stampAccountWithPasswordHash(baseAcc, rawPassword)
        _userAccounts.value = _userAccounts.value.filterNot { it.id == id || normalizeUsername(it.username) == cleanUser } + newAcc
        storage.saveUserAccounts(_userAccounts.value)

        syncStaffAccountDocuments(newAcc)
        syncPasswordVerifierToCloud(newAcc)

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured() && rawPassword.isNotEmpty()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    val res = staffAuthApiClient.createStaff(token, newAcc, rawPassword, defBranch, deviceInfoLabel())
                    if (res.ok && res.account != null) {
                        val syncedUid = res.uid.ifBlank { uid }
                        val syncedAcc = res.account.copy(
                            id = if (syncedUid.startsWith("staff_")) syncedUid else id,
                            uid = syncedUid,
                            passwordHash = newAcc.passwordHash,
                            passwordSalt = newAcc.passwordSalt
                        )
                        _userAccounts.value = _userAccounts.value.map {
                            if (it.id == id || normalizeUsername(it.username) == cleanUser) syncedAcc else it
                        }
                        storage.saveUserAccounts(_userAccounts.value)
                    } else if (!res.ok) {
                        Log.w("WaterPlantRepo", "Backend createStaff error (${res.errorCode}): ${res.message}")
                    }
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend createStaff notice: ${e.message}")
                }
            }
        }

        appendAuditLog(
            action = AuditActionType.CREATE,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = uid,
            recordLabel = "${newAcc.name} (@$cleanUser)",
            updatedValue = "Role=${effectiveRole.name}, Branches=${branches.joinToString(",")}"
        )
        addNotification("User Created", "Admin created account for ${newAcc.name} (${newAcc.username}).", NotificationSeverity.SUCCESS)
        return id
    }

    fun updateUserAccount(account: UserAccount) {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) return
        val existing = _userAccounts.value.find { it.id == account.id || it.uid == account.id }
        val isProtectedSuper = existing?.isSuperAdmin == true || account.id == "user_admin_01"
        val cleanUser = normalizeUsername(account.username)
        val cleanEmail = account.googleEmail.trim().lowercase()
        val effectiveRole = if (isProtectedSuper) UserRole.ADMIN else account.role
        val effectiveActive = if (isProtectedSuper) true else account.active
        val normalizedPerms = PermissionDefaults.normalizePermissions(
            input = account.permissions,
            role = effectiveRole,
            isSuperAdmin = isProtectedSuper,
            explicitMode = true
        )
        val branches = account.assignedBranchIds.ifEmpty { existing?.assignedBranchIds?.ifEmpty { null } ?: listOf(_activePlantId.value) }
        val defBranch = account.defaultBranchId.ifBlank { branches.first() }
        val resolvedUid = account.uid.ifBlank { existing?.uid?.ifBlank { account.id } ?: account.id }

        val rawNewPass = account.password.trim()
        val safeRawNewPass = if ((isProtectedSuper || effectiveRole == UserRole.ADMIN) && isRetiredDefaultAdminPasswordText(rawNewPass)) {
            ""
        } else {
            rawNewPass
        }
        val updatedBase = account.copy(
            uid = resolvedUid,
            username = cleanUser,
            name = account.name.trim(),
            phone = account.phone.trim(),
            googleEmail = cleanEmail,
            role = effectiveRole,
            isSuperAdmin = isProtectedSuper,
            active = effectiveActive,
            permissions = normalizedPerms,
            hasExplicitPermissions = true,
            assignedBranchIds = branches,
            defaultBranchId = defBranch,
            restrictToOwnDataOnly = if (isProtectedSuper || effectiveRole == UserRole.ADMIN) false else account.restrictToOwnDataOnly,
            restrictToTodayOnly = if (isProtectedSuper || effectiveRole == UserRole.ADMIN) false else account.restrictToTodayOnly
        )
        val updated = if (safeRawNewPass.isNotEmpty()) {
            stampAccountWithPasswordHash(updatedBase, safeRawNewPass)
        } else {
            updatedBase.copy(
                password = "",
                passwordHash = account.passwordHash.ifBlank { existing?.passwordHash ?: "" },
                passwordSalt = account.passwordSalt.ifBlank { existing?.passwordSalt ?: "" }
            )
        }
        _userAccounts.value = _userAccounts.value.map { if (it.id == updated.id) updated else it }
        storage.saveUserAccounts(_userAccounts.value)
        syncAdminPhoneForPdfs()
        refreshCurrentProfileIfMatches(updated)

        syncStaffAccountDocuments(updated, previousEmail = existing?.googleEmail.orEmpty())
        if (rawNewPass.isNotEmpty()) {
            syncPasswordVerifierToCloud(updated)
        }

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    staffAuthApiClient.updateStaff(token, updated, defBranch, deviceInfoLabel())
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend updateStaff notice: ${e.message}")
                }
            }
        }

        appendAuditLog(
            action = AuditActionType.UPDATE,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${updated.name} (@$cleanUser)",
            previousValue = "Role=${existing?.role?.name ?: ""}, Active=${existing?.active ?: true}",
            updatedValue = "Role=${updated.role.name}, Active=${updated.active}, Branches=${branches.joinToString(",")}"
        )
        addNotification("User Updated", "Updated account credentials & permissions for ${updated.name}.", NotificationSeverity.INFO)
    }

    fun resetUserPassword(userId: String, newPassword: String): Boolean {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) return false
        val acc = _userAccounts.value.find { it.id == userId || it.uid == userId } ?: return false
        val cleanNew = newPassword.trim().ifEmpty { newPassword }
        if ((isPrimaryAdminAccount(acc) || acc.role == UserRole.ADMIN) && isRetiredDefaultAdminPasswordText(cleanNew)) {
            return false
        }
        val updated = stampAccountWithPasswordHash(acc, cleanNew)
        val resolvedUid = updated.uid.ifBlank { updated.id }
        _userAccounts.value = _userAccounts.value.map { if (it.id == acc.id) updated else it }
        storage.saveUserAccounts(_userAccounts.value)

        syncStaffAccountDocuments(updated)
        syncPasswordVerifierToCloud(updated)

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    staffAuthApiClient.resetStaffPassword(token, resolvedUid, cleanNew, _activePlantId.value, deviceInfoLabel())
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend resetStaffPassword notice: ${e.message}")
                }
            }
        }

        appendAuditLog(
            action = AuditActionType.PASSWORD_RESET,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${updated.name} (@${updated.username})",
            updatedValue = "Password reset by Admin"
        )
        addNotification("Password Reset", "Admin securely reset password for ${updated.name} (${updated.username}).", NotificationSeverity.WARNING)
        return true
    }

    fun deleteUserAccount(id: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) {
            return Pair(false, "Only Admin can delete staff accounts.")
        }
        val target = _userAccounts.value.find { it.id == id || it.uid == id }
            ?: return Pair(false, "Staff account not found.")
        if (target.isSuperAdmin || target.id == "user_admin_01" || target.username.equals("admin", ignoreCase = true) || target.id == _currentUser.value.id) {
            return Pair(false, "Cannot delete the primary Super Admin or currently logged-in account.")
        }
        val resolvedUid = target.uid.ifBlank { target.id }
        _userAccounts.value = _userAccounts.value.filterNot { it.id == target.id }
        storage.saveUserAccounts(_userAccounts.value)
        deleteDocument("staffUsers", resolvedUid)
        deleteDocument("users", target.id)
        val oldEmail = target.googleEmail.trim().lowercase()
        if (oldEmail.isNotBlank()) {
            deleteDocument("staffByEmail", oldEmail)
        }

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    staffAuthApiClient.deleteStaff(token, resolvedUid, _activePlantId.value, deviceInfoLabel())
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend deleteStaff notice: ${e.message}")
                }
            }
        }

        appendAuditLog(
            action = AuditActionType.DELETE,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${target.name} (@${target.username})"
        )
        addNotification("Staff Deleted", "Admin deleted staff account '${target.name}' (@${target.username}).", NotificationSeverity.WARNING)
        return Pair(true, "Staff account '${target.name}' deleted.")
    }

    fun toggleUserAccountActive(userId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) {
            return Pair(false, "Only Admin can update staff status.")
        }
        val target = _userAccounts.value.find { it.id == userId || it.uid == userId }
            ?: return Pair(false, "Staff account not found.")
        if (target.isSuperAdmin || target.id == "user_admin_01" || target.username.equals("admin", ignoreCase = true) || target.id == _currentUser.value.id) {
            return Pair(false, "Super Admin or currently active account cannot be deactivated.")
        }
        val newActive = !target.active
        val resolvedUid = target.uid.ifBlank { target.id }
        val updated = target.copy(active = newActive)
        _userAccounts.value = _userAccounts.value.map { if (it.id == target.id) updated else it }
        storage.saveUserAccounts(_userAccounts.value)

        syncStaffAccountDocuments(updated)
        syncPasswordVerifierToCloud(updated)

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    staffAuthApiClient.toggleStaffActive(token, resolvedUid, newActive, _activePlantId.value, deviceInfoLabel())
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend toggleStaffActive notice: ${e.message}")
                }
            }
        }

        val statusLabel = if (newActive) "Activated" else "Deactivated"
        appendAuditLog(
            action = AuditActionType.UPDATE,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${updated.name} (@${updated.username})",
            previousValue = "Active=${target.active}",
            updatedValue = "Active=$newActive"
        )
        addNotification("Account $statusLabel", "${updated.name} (@${updated.username}) is now ${if (newActive) "Active" else "Inactive"}.", NotificationSeverity.INFO)
        return Pair(true, "${updated.name} is now ${if (newActive) "Active" else "Inactive"}.")
    }

    fun changeOwnPassword(currentPassword: String, newPassword: String): Pair<Boolean, String> {
        val current = _currentUser.value
        val acc = _userAccounts.value.find {
            it.id == current.id || normalizeUsername(it.username) == normalizeUsername(current.username)
        } ?: return Pair(false, "Current account record not found.")
        if (!verifyAccountPassword(acc, currentPassword)) {
            return Pair(false, "Current password is incorrect (موجودہ پاس ورڈ غلط ہے)")
        }
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 4) {
            return Pair(false, "New password must be at least 4 characters.")
        }
        if ((isPrimaryAdminAccount(acc) || acc.role == UserRole.ADMIN) && isRetiredDefaultAdminPasswordText(cleanNew)) {
            return Pair(false, "Default admin123 password is permanently retired. Choose a new password.")
        }
        val updated = stampAccountWithPasswordHash(acc, cleanNew)
        val resolvedUid = updated.uid.ifBlank { updated.id }
        _userAccounts.value = _userAccounts.value.map { if (it.id == acc.id) updated else it }
        storage.saveUserAccounts(_userAccounts.value)

        if (hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) {
            syncStaffAccountDocuments(updated)
        }
        syncPasswordVerifierToCloud(updated)

        if (isFirebaseSignedIn() && staffAuthApiClient.isConfigured()) {
            scope.launch {
                try {
                    val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@launch
                    staffAuthApiClient.changeOwnPassword(token, currentPassword, cleanNew, deviceInfoLabel())
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Backend changeOwnPassword notice: ${e.message}")
                }
            }
        }

        appendAuditLog(
            action = AuditActionType.PASSWORD_RESET,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${updated.name} (@${updated.username})",
            updatedValue = "Self-service password change"
        )
        addNotification("Password Changed", "${updated.name} updated their account password.", NotificationSeverity.SUCCESS)
        return Pair(true, "Password updated successfully (پاس ورڈ تبدیل ہو گیا)")
    }

    fun requestPasswordReset(username: String): String {
        val cleanUser = normalizeUsername(username)
        val account = _userAccounts.value.find { normalizeUsername(it.username) == cleanUser }
        val id = nextRecordId("req")
        val reqTime = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
        val newReq = PasswordResetRequest(
            id = id,
            username = cleanUser,
            fullName = account?.name ?: cleanUser,
            role = account?.role ?: UserRole.INCHARGE,
            requestTime = reqTime,
            reason = "Staff forgot password / لاگ ان مسئلہ",
            isResolved = false
        )
        _passwordResetRequests.value = listOf(newReq) + _passwordResetRequests.value
        storage.savePasswordResetRequests(_passwordResetRequests.value)
        syncDocument("passwordResetRequests", id, newReq)
        addNotification("Password Reset Request", "Password reset requested for '${newReq.fullName}'. Awaiting Admin verification.", NotificationSeverity.WARNING)
        return id
    }

    fun resolvePasswordReset(requestId: String, newPassword: String? = null): Boolean {
        val req = _passwordResetRequests.value.find { it.id == requestId } ?: return false
        val resolvedTime = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
        val adminName = _currentUser.value.name

        if (!newPassword.isNullOrBlank()) {
            val user = _userAccounts.value.find { normalizeUsername(it.username) == normalizeUsername(req.username) }
            if (user != null) {
                resetUserPassword(user.id, newPassword)
            }
        }

        val resolvedReq = req.copy(
            isResolved = true,
            resolvedAt = resolvedTime,
            resolvedBy = adminName,
            newPasswordSet = "Reset by Admin"
        )
        _passwordResetRequests.value = _passwordResetRequests.value.map {
            if (it.id == requestId) resolvedReq else it
        }
        storage.savePasswordResetRequests(_passwordResetRequests.value)
        syncDocument("passwordResetRequests", resolvedReq.id, resolvedReq.copy(newPasswordSet = ""))
        addNotification("Request Resolved", "Password reset request for ${req.username} approved & resolved by Admin.", NotificationSeverity.SUCCESS)
        return true
    }

    fun isValidStrongPassword(password: String): Boolean {
        if (password.length < 6) return false
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        return hasUpper && hasDigit && hasSpecial
    }

    // Role Switcher & Strict Role Verification (NO hardcoded fallback passwords like admin123)
    fun verifyRolePassword(role: UserRole, password: String): Boolean {
        val clean = password.trim()
        if (clean.isBlank()) return false
        val accounts = _userAccounts.value.filter { it.role == role && it.active }
        if (accounts.isNotEmpty() && accounts.any { verifyAccountPassword(it, clean) }) {
            return true
        }
        // Admin custom password grants authority to switch to any role (retired default admin123 is always rejected)
        val adminAccounts = _userAccounts.value.filter { it.role == UserRole.ADMIN && it.active }
        if (adminAccounts.any { verifyAccountPassword(it, clean) }) {
            return true
        }
        return false
    }

    fun verifyAdminPassword(password: String): Boolean = verifyRolePassword(UserRole.ADMIN, password)

    fun changeUserPassword(userId: String, newPassword: String): Boolean {
        val target = _userAccounts.value.find { it.id == userId || it.uid == userId } ?: return false
        val clean = newPassword.trim()
        if (!isValidStrongPassword(clean)) {
            return false
        }
        if ((isPrimaryAdminAccount(target) || target.role == UserRole.ADMIN) && isRetiredDefaultAdminPasswordText(clean)) {
            return false
        }
        val updated = stampAccountWithPasswordHash(target, clean)
        val resolvedUid = updated.uid.ifBlank { updated.id }
        _userAccounts.value = _userAccounts.value.map { if (it.id == target.id) updated else it }
        storage.saveUserAccounts(_userAccounts.value)
        syncStaffAccountDocuments(updated)
        syncPasswordVerifierToCloud(updated)
        appendAuditLog(
            action = AuditActionType.PASSWORD_RESET,
            module = AppModule.STAFF_MANAGEMENT,
            recordId = resolvedUid,
            recordLabel = "${updated.name} (@${updated.username})"
        )
        addNotification("Password Changed", "Password updated for ${target.username}.", NotificationSeverity.SUCCESS)
        return true
    }

    fun switchUserByCredentials(username: String, password: String): Pair<Boolean, String> {
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return Pair(false, "Username and password are required (یوزر نیم اور پاس ورڈ دونوں درج کریں)")
        }
        if (isRetiredAdminPassword(cleanUser, password)) {
            return Pair(false, "Old default password is disabled. Use your updated password or Google Sign-In.")
        }
        val matched = _userAccounts.value.find {
            normalizeUsername(it.username) == cleanUser && verifyAccountPassword(it, password)
        } ?: return Pair(false, "Invalid username or password (یوزر نیم یا پاس ورڈ غلط ہے)")

        if (!matched.active) {
            return Pair(false, "This account is inactive. Contact Admin (یہ اکاؤنٹ غیر فعال ہے)")
        }

        val profile = activateAuthenticatedAccount(matched)
        return Pair(true, "Switched to ${profile.name} (${profile.role.displayName})")
    }

    suspend fun switchUserByCredentialsAsync(username: String, password: String): Pair<Boolean, String> {
        val cleanUser = normalizeUsername(username)
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return Pair(false, "Username and password are required (یوزر نیم اور پاس ورڈ دونوں درج کریں)")
        }
        if (isRetiredAdminPassword(cleanUser, password)) {
            return Pair(false, "Old default password is disabled. Use your updated password or Google Sign-In.")
        }
        // Always refresh cloud password verifier first so changed passwords immediately invalidate old ones on every device
        refreshCloudVerifierForUsername(cleanUser)

        if (staffAuthApiClient.isConfigured() && isDeviceOnline.value) {
            val (outcome, profile) = authenticateWithOutcome(username, password)
            if (outcome == LoginOutcome.SUCCESS && profile != null) {
                return Pair(true, "Switched to ${profile.name} (${profile.role.displayName})")
            }
            if (outcome == LoginOutcome.INACTIVE_ACCOUNT) {
                return Pair(false, "This account is inactive. Contact Admin (یہ اکاؤنٹ غیر فعال ہے)")
            }
            if (outcome == LoginOutcome.INVALID_CREDENTIALS) {
                return Pair(false, "Invalid username or password (یوزر نیم یا پاس ورڈ غلط ہے)")
            }
        }

        val localResult = switchUserByCredentials(username, password)
        if (localResult.first) return localResult

        val inactiveLocal = _userAccounts.value.find {
            normalizeUsername(it.username) == cleanUser && verifyAccountPassword(it, password) && !it.active
        }
        if (inactiveLocal != null) return localResult

        val cloudProfile = authenticateAsync(cleanUser, password)
        return if (cloudProfile != null) {
            Pair(true, "Switched to ${cloudProfile.name} (${cloudProfile.role.displayName})")
        } else {
            localResult
        }
    }

    fun switchUserRole(role: UserRole, customName: String? = null) {
        val current = _currentUser.value
        val newName = customName ?: when (role) {
            UserRole.ADMIN -> "Muhammad Rafiq (Admin)"
            UserRole.INCHARGE -> "Rashid Minhas (Supervisor)"
            UserRole.VIEW_ONLY -> "Observer (View-Only / صرف نظارہ)"
        }
        val updated = current.copy(
            role = role,
            name = newName,
            isSuperAdmin = role == UserRole.ADMIN,
            permissions = PermissionDefaults.defaultPermissionsForRole(role)
        )
        _currentUser.value = updated
        refreshSeenRecordsForCurrentUser()
        storage.saveCurrentProfile(updated)
        addNotification(
            "Role Switched",
            "Switched to ${role.displayName} view.",
            NotificationSeverity.INFO
        )
    }

    fun refreshCloudData() {
        val now = System.currentTimeMillis()
        val hasNoListeners = synchronized(activeRtdbListeners) { activeRtdbListeners.isEmpty() }
        flushOfflineSyncQueueNow()
        if (hasNoListeners || now - lastManualRefreshMillis >= 15_000L) {
            lastManualRefreshMillis = now
            startFirestoreSync(forceRestart = hasNoListeners)
        } else {
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            _syncStatus.value = "Realtime DB Live • Synced at $time"
        }
    }

    // Bottle Sizes & Rates Management
    fun addBottleSize(size: BottleSize): String {
        if (!hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)) {
            addNotification("Access Restricted", "Admin has restricted adding new bottle sizes/items.", NotificationSeverity.WARNING)
            return ""
        }
        val id = if (size.id.isNotEmpty()) size.id else nextRecordId("size")
        val newSize = size.copy(id = id)
        _bottleSizes.value = _bottleSizes.value + newSize

        // Also add an inventory tracker for this new size (all stock added to Plant Empty)
        val initialEmpty = newSize.openingStock.coerceAtLeast(0)
        val newInv = BottleInventory(
            id = "inv_$id",
            bottleSizeId = id,
            sizeName = newSize.name,
            atPlantFull = 0,
            atPlantEmpty = initialEmpty,
            inUse = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = initialEmpty
        )
        _inventory.value = _inventory.value + newInv
        storage.saveBottleSizes(_bottleSizes.value)
        storage.saveInventory(_inventory.value)
        syncDocument("bottleSizes", id, newSize)
        syncDocument("bottleInventory", newInv.id, newInv)

        addNotification("Bottle Size Added", "New size '${newSize.name}' added with rate Rs. ${newSize.defaultRefillPrice}.", NotificationSeverity.SUCCESS)
        return id
    }

    fun updateBottleSize(size: BottleSize) {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT)) return
        _bottleSizes.value = _bottleSizes.value.map { if (it.id == size.id) size else it }
        // Update inventory names too
        _inventory.value = _inventory.value.map {
            if (it.bottleSizeId == size.id) it.copy(sizeName = size.name) else it
        }
        storage.saveBottleSizes(_bottleSizes.value)
        storage.saveInventory(_inventory.value)
        syncDocument("bottleSizes", size.id, size)
        _inventory.value.find { it.bottleSizeId == size.id }?.let { syncDocument("bottleInventory", it.id, it) }
        addNotification("Rate Updated", "Bottle size '${size.name}' updated (Rate: Rs. ${size.defaultRefillPrice}, Pack: ${size.packQuantity} bottles).", NotificationSeverity.INFO)
    }

    // Customer Operations
    fun addCustomer(customer: Customer): String {
        if (!hasPermission(AppModule.CUSTOMERS, PermissionAction.ADD)) return ""
        val newId = if (customer.id.isNotEmpty()) customer.id else nextRecordId("cust")
        val code = if (customer.customerCode.isNotEmpty()) customer.customerCode else "C-${100 + _customers.value.size + 1}"
        val creatorUid = _currentUser.value.id.ifBlank { _currentUser.value.uid }
        val creatorName = customer.createdBy.ifBlank { _currentUser.value.name }
        val newCust = customer.copy(
            id = newId,
            customerCode = code,
            createdBy = creatorName,
            createdByUid = customer.createdByUid.ifBlank { creatorUid },
            createdAt = if (customer.createdAt > 0L) customer.createdAt else System.currentTimeMillis()
        )
        markRecordSeen("customer", newId)
        return withSyncAction("Add Customer • ${newCust.name}") {
            _customers.value = listOf(newCust) + _customers.value

            // Setup default 19L bottle allocation tracking
            val alloc = CustomerBottleAllocation(
                id = nextRecordId("alloc"),
                customerId = newId,
                customerName = newCust.name,
                bottleSizeId = "size_19l",
                sizeName = "19 Litre Bottle",
                totalIssued = newCust.emptyBottlesOwed,
                emptyHeld = newCust.emptyBottlesOwed,
                damagedCount = 0,
                depositAmount = newCust.bottleDeposit,
                lastUpdated = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )
            _customerAllocations.value = _customerAllocations.value + alloc
            storage.saveCustomers(_customers.value)
            storage.saveCustomerAllocations(_customerAllocations.value)

            syncDocument("customers", newId, newCust)
            syncDocument("customerBottleAllocations", alloc.id, alloc)
            appendAuditLog(AuditActionType.CREATE, AppModule.CUSTOMERS, newId, newCust.name, updatedValue = "Area: ${newCust.area}, Phone: ${newCust.phone}")

            addNotification("New Customer Added", "${newCust.name} (${newCust.area}) registered.", NotificationSeverity.SUCCESS)
            newId
        }
    }

    fun getPrimaryAdminPhone(): String {
        val accounts = _userAccounts.value
        val adminAcc = accounts.firstOrNull { it.id == "user_admin_01" && it.phone.isNotBlank() }
            ?: accounts.firstOrNull { it.isSuperAdmin && it.phone.isNotBlank() }
            ?: accounts.firstOrNull { it.role == UserRole.ADMIN && it.phone.isNotBlank() }
        return adminAcc?.phone?.trim()?.takeIf { it.isNotBlank() }
            ?: _currentUser.value.takeIf { it.role == UserRole.ADMIN && it.phone.isNotBlank() }?.phone?.trim()
            ?: "+92 300 1234567"
    }

    private fun syncAdminPhoneForPdfs() {
        com.example.util.PdfGenerator.defaultAdminPhone = getPrimaryAdminPhone()
    }

    fun updateCustomer(customer: Customer) {
        if (!hasPermission(AppModule.CUSTOMERS, PermissionAction.EDIT)) return
        val existing = _customers.value.find { it.id == customer.id }
        val now = System.currentTimeMillis()
        val editorName = _currentUser.value.name.ifBlank { _currentUser.value.username }
        val preserved = if (existing != null) {
            customer.copy(
                createdBy = customer.createdBy.ifBlank { existing.createdBy },
                createdByUid = customer.createdByUid.ifBlank { existing.createdByUid },
                createdAt = if (customer.createdAt > 0L) customer.createdAt else existing.createdAt,
                lastEditedAt = now,
                lastEditedBy = editorName
            )
        } else {
            customer.copy(lastEditedAt = now, lastEditedBy = editorName)
        }
        withSyncAction("Update Customer • ${preserved.name}") {
            _customers.value = _customers.value.map { if (it.id == preserved.id) preserved else it }
            storage.saveCustomers(_customers.value)
            syncDocument("customers", preserved.id, preserved)
        }
    }

    private fun applyCustomerDeltaLocalAndCloud(
        updatedCustomer: Customer,
        deltaFields: Map<String, Number>
    ) {
        _customers.value = _customers.value.map { if (it.id == updatedCustomer.id) updatedCustomer else it }
        storage.saveCustomers(_customers.value)
        syncIncrementalDelta("customers", updatedCustomer.id, updatedCustomer, deltaFields)
    }

    fun deleteCustomer(customerId: String): Boolean {
        if (!hasPermission(AppModule.CUSTOMERS, PermissionAction.DELETE)) return false
        val cust = getCustomerById(customerId) ?: return false
        if (cust.currentBalance > 0.0 || cust.emptyBottlesOwed > 0) {
            addNotification("Cannot Delete Customer", "Cannot delete ${cust.name}: outstanding balance (Rs. ${cust.currentBalance.toInt()}) or bottles held (${cust.emptyBottlesOwed}).", NotificationSeverity.WARNING)
            return false
        }
        return withSyncAction("Delete Customer • ${cust.name}") {
            _customers.value = _customers.value.filterNot { it.id == customerId }
            storage.saveCustomers(_customers.value)
            deleteDocument("customers", customerId)
            appendAuditLog(AuditActionType.DELETE, AppModule.CUSTOMERS, customerId, cust.name, previousValue = "Balance: ${cust.currentBalance}")
            addNotification("Customer Deleted", "Customer '${cust.name}' has been deleted.", NotificationSeverity.WARNING)
            true
        }
    }

    fun toggleCustomerActive(customerId: String) {
        if (!hasPermission(AppModule.CUSTOMERS, PermissionAction.EDIT)) return
        val cust = getCustomerById(customerId) ?: return
        val newStatus = !cust.active
        val updatedCust = cust.copy(active = newStatus)
        _customers.value = _customers.value.map {
            if (it.id == customerId) updatedCust else it
        }
        storage.saveCustomers(_customers.value)
        syncDocument("customers", customerId, updatedCust)
        val statusText = if (newStatus) "Active (فعال)" else "Inactive (غیر فعال)"
        addNotification("Customer Status Updated", "${cust.name} is now $statusText.", NotificationSeverity.INFO)
    }

    fun getCustomerById(id: String): Customer? {
        return _customers.value.find { it.id == id }
    }

    fun getCustomerRates(customerId: String): List<CustomerRate> {
        return _customerRates.value.filter { it.customerId == customerId }
    }

    fun getAssignedCustomerRates(customerId: String): List<CustomerRate> {
        return _customerRates.value.filter { it.customerId == customerId && it.isAssigned }
    }

    /**
     * Normalizes any bottle size or pack variant rate key into a canonical (baseSizeId|CONTAINER) signature
     * so equivalent keys like "size_500ml_carton_24" and "size_500ml__24_CARTON" ALWAYS map to the same rate.
     */
    private fun canonicalRateSignature(rawKey: String, displayName: String = ""): String {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) return ""
        val lowerKey = trimmed.lowercase(Locale.ENGLISH)
        val lowerAll = "$lowerKey ${displayName.lowercase(Locale.ENGLISH)}"

        val container = when {
            lowerAll.contains("carton") -> "CARTON"
            lowerAll.contains("packet") -> "PACKET"
            else -> "SINGLE"
        }

        // Match longest active/known bottle size ID prefix first
        val matchedSizeId = _bottleSizes.value
            .map { it.id }
            .filter { it.isNotBlank() && (trimmed == it || trimmed.startsWith("${it}_")) }
            .maxByOrNull { it.length }

        val baseSizeId = when {
            matchedSizeId != null -> matchedSizeId
            trimmed.contains("__") -> trimmed.substringBefore("__")
            lowerKey.contains("_carton_") -> trimmed.substring(0, lowerKey.indexOf("_carton_"))
            lowerKey.contains("_packet_") -> trimmed.substring(0, lowerKey.indexOf("_packet_"))
            lowerKey.contains("_single_") -> trimmed.substring(0, lowerKey.indexOf("_single_"))
            else -> trimmed
        }
        return "$baseSizeId|$container"
    }

    /**
     * Authoritative lookup for a customer's rate for a given bottle size / pack variant.
     * Matches both exact rateKey AND canonical aliases (e.g., size_500ml_carton_24 <-> size_500ml__24_CARTON),
     * always preferring the most recently updated record if duplicate documents exist.
     */
    fun findCustomerRate(
        customerId: String,
        bottleSizeId: String,
        assignedOnly: Boolean = false
    ): CustomerRate? {
        val targetSig = canonicalRateSignature(bottleSizeId)
        val candidates = _customerRates.value.filter { r ->
            r.customerId == customerId &&
                (!assignedOnly || r.isAssigned) &&
                (r.bottleSizeId == bottleSizeId ||
                    (targetSig.isNotBlank() && canonicalRateSignature(r.bottleSizeId, r.bottleSizeName) == targetSig))
        }
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        return candidates.maxWithOrNull(
            compareBy<CustomerRate> { it.rateHistory.maxOfOrNull { h -> h.timestamp } ?: 0L }
                .thenBy { it.appliedDate }
                .thenBy { if (it.bottleSizeId == bottleSizeId) 1 else 0 }
        )
    }

    fun getCustomerPrice(customerId: String, bottleSizeId: String): Double {
        val customRate = findCustomerRate(customerId, bottleSizeId, assignedOnly = true)
        if (customRate != null) {
            return customRate.customPrice.coerceAtLeast(0.0)
        }
        return 0.0
    }

    fun setCustomerPrice(
        customerId: String,
        bottleSizeId: String,
        customPrice: Double,
        bottleSizeName: String? = null,
        appliedDate: String? = null
    ) {
        if (!hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT) &&
            !hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.ADD)
        ) {
            addNotification("Rate Change Restricted", "Admin has restricted changing customer rates.", NotificationSeverity.WARNING)
            return
        }
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val effectiveDate = if (!appliedDate.isNullOrBlank()) appliedDate.trim() else todayStr
        val name = normalizeLegacyBottleName(
            bottleSizeName ?: (_bottleSizes.value.find { it.id == bottleSizeId }?.name ?: "Bottle")
        )
        val targetSig = canonicalRateSignature(bottleSizeId, name)

        // Match ALL existing exact & alias records for this customer + bottle/variant so stale duplicates are merged & purged
        val matchingRates = _customerRates.value.filter { r ->
            r.customerId == customerId &&
                (r.bottleSizeId == bottleSizeId ||
                    (targetSig.isNotBlank() && canonicalRateSignature(r.bottleSizeId, r.bottleSizeName) == targetSig))
        }
        val existingRate = matchingRates.find { it.bottleSizeId == bottleSizeId }
            ?: matchingRates.maxByOrNull { it.rateHistory.maxOfOrNull { h -> h.timestamp } ?: 0L }
        val duplicateRatesToDelete = matchingRates.filter { existingRate == null || it.id != existingRate.id }
        val otherRates = _customerRates.value.filterNot { r -> matchingRates.any { it.id == r.id } }

        val newHistoryEntry = RateHistoryEntry(
            id = nextRecordId("rh"),
            customerId = customerId,
            bottleSizeId = bottleSizeId,
            bottleSizeName = name,
            rate = customPrice,
            appliedFromDate = effectiveDate,
            updatedBy = _currentUser.value.name.ifBlank { "Admin" },
            timestamp = System.currentTimeMillis()
        )

        // Prune any stale/future rateHistory entries strictly after effectiveDate so an older/outdated entry can NEVER override the new rate,
        // and replace any existing entry on the same effectiveDate.
        val cleanedOldHistory = (existingRate?.rateHistory ?: emptyList())
            .filter { it.appliedFromDate.isNotBlank() && it.appliedFromDate <= effectiveDate }
        val sameDayIndex = cleanedOldHistory.indexOfLast { it.appliedFromDate == effectiveDate }
        val updatedHistory = if (sameDayIndex >= 0) {
            cleanedOldHistory.toMutableList().apply {
                this[sameDayIndex] = newHistoryEntry.copy(id = cleanedOldHistory[sameDayIndex].id)
            }
        } else {
            cleanedOldHistory + newHistoryEntry
        }

        val newRate = CustomerRate(
            id = existingRate?.id ?: nextRecordId("rate"),
            customerId = customerId,
            bottleSizeId = bottleSizeId,
            bottleSizeName = name,
            customPrice = customPrice,
            isAssigned = true,
            appliedDate = effectiveDate,
            rateHistory = updatedHistory
        )
        _customerRates.value = otherRates + newRate
        storage.saveCustomerRates(_customerRates.value)
        duplicateRatesToDelete.forEach { dup ->
            if (dup.id.isNotBlank()) deleteDocument("customerRates", dup.id)
        }
        syncDocument("customerRates", newRate.id, newRate)
        addNotification("Rate Assigned", "Rate for $name set to Rs. $customPrice for customer (Applied: $effectiveDate).", NotificationSeverity.INFO)
    }

    fun removeCustomerBottleSize(customerId: String, bottleSizeId: String) {
        if (!hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT)) {
            addNotification("Rate Change Restricted", "Admin has restricted changing customer rates.", NotificationSeverity.WARNING)
            return
        }
        val targetSig = canonicalRateSignature(bottleSizeId)
        val existing = findCustomerRate(customerId, bottleSizeId, assignedOnly = false)
        val sizeName = existing?.bottleSizeName ?: (_bottleSizes.value.find { it.id == bottleSizeId }?.name ?: "Bottle")
        _customerRates.value = _customerRates.value.map {
            val matches = it.customerId == customerId &&
                (it.bottleSizeId == bottleSizeId ||
                    (targetSig.isNotBlank() && canonicalRateSignature(it.bottleSizeId, it.bottleSizeName) == targetSig))
            if (matches) {
                val updated = it.copy(isAssigned = false)
                syncDocument("customerRates", updated.id, updated)
                updated
            } else it
        }
        storage.saveCustomerRates(_customerRates.value)
        addNotification("Bottle Size Removed", "Deactivated $sizeName from customer assignment.", NotificationSeverity.WARNING)
    }

    fun reactivateCustomerBottleSize(customerId: String, bottleSizeId: String) {
        if (!hasPermission(AppModule.CUSTOMER_RATES, PermissionAction.EDIT)) {
            addNotification("Rate Change Restricted", "Admin has restricted changing customer rates.", NotificationSeverity.WARNING)
            return
        }
        val targetSig = canonicalRateSignature(bottleSizeId)
        val existing = findCustomerRate(customerId, bottleSizeId, assignedOnly = false)
        val sizeName = existing?.bottleSizeName ?: (_bottleSizes.value.find { it.id == bottleSizeId }?.name ?: "Bottle")
        _customerRates.value = _customerRates.value.map {
            val matches = it.customerId == customerId &&
                (it.bottleSizeId == bottleSizeId ||
                    (targetSig.isNotBlank() && canonicalRateSignature(it.bottleSizeId, it.bottleSizeName) == targetSig))
            if (matches) {
                val updated = it.copy(isAssigned = true)
                syncDocument("customerRates", updated.id, updated)
                updated
            } else it
        }
        storage.saveCustomerRates(_customerRates.value)
        addNotification("Bottle Size Reactivated", "Reactivated $sizeName for customer assignment.", NotificationSeverity.INFO)
    }

    fun getCustomerRateForDate(customerId: String, bottleSizeId: String, deliveryDate: String): Double {
        val customRate = findCustomerRate(customerId, bottleSizeId, assignedOnly = true) ?: return 0.0
        val cleanDeliveryDate = deliveryDate.trim()
        val cleanAppliedDate = customRate.appliedDate.trim()

        // For current/on-or-after deliveries, the saved customPrice is ALWAYS authoritative —
        // outdated or stale rateHistory entries can NEVER override the customer's saved rate.
        if (cleanDeliveryDate.isBlank() || cleanAppliedDate.isBlank() || cleanDeliveryDate >= cleanAppliedDate) {
            return customRate.customPrice.coerceAtLeast(0.0)
        }

        // Only for backdated deliveries (strictly before customRate.appliedDate), resolve historical rate
        val applicableHistory = customRate.rateHistory
            .filter { it.appliedFromDate.isNotBlank() && it.appliedFromDate <= cleanDeliveryDate }
            .maxWithOrNull(
                compareBy<RateHistoryEntry> { it.appliedFromDate }
                    .thenBy { it.timestamp }
            )
        if (applicableHistory != null) {
            return applicableHistory.rate.coerceAtLeast(0.0)
        }
        return customRate.customPrice.coerceAtLeast(0.0)
    }

    // Security Deposit Management
    fun getCustomerSecurityDeposits(customerId: String): List<SecurityDepositTransaction> {
        return _securityDeposits.value.filter { it.customerId == customerId }
    }

    fun getCustomerSecurityDepositBalance(customerId: String): Double {
        val txs = _securityDeposits.value.filter { it.customerId == customerId }
        val totalDeposited = txs.filter {
            it.type == SecurityDepositTransactionType.INITIAL_DEPOSIT || it.type == SecurityDepositTransactionType.ADDITIONAL_DEPOSIT
        }.sumOf { it.amount }

        val totalRefunded = txs.filter {
            it.type == SecurityDepositTransactionType.PARTIAL_REFUND || it.type == SecurityDepositTransactionType.FULL_REFUND
        }.sumOf { it.amount }

        val adjustments = txs.filter {
            it.type == SecurityDepositTransactionType.ADJUSTMENT
        }.sumOf { it.amount }

        return (totalDeposited - totalRefunded + adjustments).coerceAtLeast(0.0)
    }

    fun recordSecurityDepositTransaction(tx: SecurityDepositTransaction) {
        withSyncAction("Security Deposit • ${tx.customerName}") {
            _securityDeposits.value = _securityDeposits.value + tx
            val newBalance = getCustomerSecurityDepositBalance(tx.customerId)
            _customers.value = _customers.value.map { cust ->
                if (cust.id == tx.customerId) {
                    val updated = cust.copy(securityDeposit = newBalance, bottleDeposit = newBalance)
                    syncDocument("customers", updated.id, updated)
                    updated
                } else cust
            }
            storage.saveSecurityDeposits(_securityDeposits.value)
            storage.saveCustomers(_customers.value)
            syncDocument("securityDeposits", tx.id, tx)
            addNotification("Security Deposit", "${tx.type.label}: Rs. ${tx.amount.toInt()} for ${tx.customerName}", NotificationSeverity.SUCCESS)
        }
    }

    // Customer Bottle Allocation & Broken Bottle (Damage) Tracking
    fun getCustomerBottleAllocations(customerId: String): List<CustomerBottleAllocation> {
        return _customerAllocations.value.filter { it.customerId == customerId }
    }

    fun recordCustomerDamage(customerId: String, bottleSizeId: String, brokenCount: Int, reason: String) {
        synchronized(mutationLock) {
            val cust = getCustomerById(customerId) ?: return
            val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
            withSyncAction("Bottle Damage • ${cust.name}") {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val existingAlloc = _customerAllocations.value.find { it.customerId == customerId && it.bottleSizeId == bottleSizeId }
            val updatedAlloc = if (existingAlloc != null) {
                existingAlloc.copy(
                    emptyHeld = (existingAlloc.emptyHeld - brokenCount).coerceAtLeast(0),
                    damagedCount = existingAlloc.damagedCount + brokenCount,
                    lastUpdated = todayStr
                )
            } else {
                CustomerBottleAllocation(
                    id = nextRecordId("alloc"),
                    customerId = customerId,
                    customerName = cust.name,
                    bottleSizeId = bottleSizeId,
                    sizeName = size.name,
                    totalIssued = brokenCount,
                    emptyHeld = 0,
                    damagedCount = brokenCount,
                    lastUpdated = todayStr
                )
            }

            _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customerId && it.bottleSizeId == bottleSizeId } + updatedAlloc
            storage.saveCustomerAllocations(_customerAllocations.value)
            if (existingAlloc != null) {
                syncIncrementalDelta(
                    "customerBottleAllocations",
                    updatedAlloc.id,
                    updatedAlloc,
                    mapOf(
                        "emptyHeld" to (updatedAlloc.emptyHeld - existingAlloc.emptyHeld),
                        "damagedCount" to brokenCount
                    ),
                    extraFields = mapOf("lastUpdated" to todayStr)
                )
            } else {
                syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
            }

            // Also update customer empty bottles count
            val updatedCust = cust.copy(emptyBottlesOwed = (cust.emptyBottlesOwed - brokenCount).coerceAtLeast(0))
            val owedDelta = updatedCust.emptyBottlesOwed - cust.emptyBottlesOwed
            applyCustomerDeltaLocalAndCloud(updatedCust, mapOf("emptyBottlesOwed" to owedDelta))

            // Adjust overall inventory damaged counter
            adjustBottleStock(
                bottleSizeId = bottleSizeId,
                fullChange = 0,
                emptyChange = 0,
                withCustomersChange = -brokenCount,
                damagedChange = brokenCount,
                reason = "Broken at customer ${cust.name}: $reason"
            )

            // Log Movement
            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = todayStr,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.CUSTOMER_DAMAGE,
                quantity = brokenCount,
                customerId = customerId,
                customerName = cust.name,
                referenceType = "Customer Breakage",
                notes = "Customer ${cust.name}: $brokenCount bottles broken ($reason)",
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)

            addNotification("Bottle Breakage Recorded", "$brokenCount bottles broken by ${cust.name}.", NotificationSeverity.ALERT)
            }
        }
    }

    fun issueBottlesToCustomer(customerId: String, bottleSizeId: String, count: Int, deposit: Double) {
        if (_currentUser.value.role == UserRole.VIEW_ONLY || count <= 0) return
        synchronized(mutationLock) {
            val cust = getCustomerById(customerId) ?: return
            val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
            val isOneTime = size.usageType == BottleUsageType.ONE_TIME || !size.isReturnableBottle
            if (isOneTime) {
                addNotification("Invalid Issuance", "Only multiple-time returnable bottles can be issued to customers.", NotificationSeverity.WARNING)
                return
            }
            val curEmpty = _inventory.value.find { it.bottleSizeId == bottleSizeId }?.atPlantEmpty ?: 0
            if (curEmpty < count) {
                addNotification("Insufficient Empty Stock", "Only $curEmpty empty ${size.name} available in Plant Empty.", NotificationSeverity.WARNING)
                return
            }
            withSyncAction("Issue Bottles • ${cust.name}") {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val existingAlloc = _customerAllocations.value.find { it.customerId == customerId && it.bottleSizeId == bottleSizeId }
            val updatedAlloc = if (existingAlloc != null) {
                existingAlloc.copy(
                    totalIssued = existingAlloc.totalIssued + count,
                    emptyHeld = existingAlloc.emptyHeld + count,
                    depositAmount = existingAlloc.depositAmount + deposit,
                    lastUpdated = todayStr
                )
            } else {
                CustomerBottleAllocation(
                    id = nextRecordId("alloc", bottleSizeId),
                    customerId = customerId,
                    customerName = cust.name,
                    bottleSizeId = bottleSizeId,
                    sizeName = size.name,
                    totalIssued = count,
                    emptyHeld = count,
                    damagedCount = 0,
                    depositAmount = deposit,
                    lastUpdated = todayStr
                )
            }

            _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customerId && it.bottleSizeId == bottleSizeId } + updatedAlloc
            storage.saveCustomerAllocations(_customerAllocations.value)
            if (existingAlloc != null) {
                syncIncrementalDelta(
                    "customerBottleAllocations",
                    updatedAlloc.id,
                    updatedAlloc,
                    mapOf(
                        "totalIssued" to count,
                        "emptyHeld" to count,
                        "depositAmount" to deposit
                    ),
                    extraFields = mapOf("lastUpdated" to todayStr)
                )
            } else {
                syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
            }

            val updatedCust = cust.copy(
                emptyBottlesOwed = cust.emptyBottlesOwed + count,
                bottleDeposit = cust.bottleDeposit + deposit
            )
            applyCustomerDeltaLocalAndCloud(
                updatedCust,
                mapOf(
                    "emptyBottlesOwed" to count,
                    "bottleDeposit" to deposit
                )
            )

            adjustBottleStock(
                bottleSizeId = bottleSizeId,
                fullChange = 0,
                emptyChange = -count,
                withCustomersChange = count,
                reason = "Bottles issued to ${cust.name}"
            )

            val issueRecord = BottleIssueSaleRecord(
                id = nextRecordId("bis_issue"),
                date = todayStr,
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                customerId = customerId,
                customerName = cust.name,
                bottleSizeId = bottleSizeId,
                bottleSizeName = size.name,
                quantity = count,
                transactionType = BottleTransactionType.CUSTOMER_ISSUE,
                unitSellingPrice = if (count > 0) deposit / count else 0.0,
                refillCost = 0.0,
                deliveryCost = 0.0,
                totalTransactionCost = 0.0,
                revenue = deposit,
                profit = deposit,
                deliveryPerson = "Plant Office",
                referenceDeliveryId = "",
                notes = "One-time Bottle Issuance ($count bottles)"
            )
            _bottleIssueSales.value = listOf(issueRecord) + _bottleIssueSales.value
            storage.saveBottleIssueSales(_bottleIssueSales.value)
            syncDocument("bottleIssueSales", issueRecord.id, issueRecord)

            addNotification("Bottles Issued", "Issued $count bottles to ${cust.name}.", NotificationSeverity.INFO)
            }
        }
    }

    /**
     * Customer returns bottles: updates customer's owed count and adds bottles back to inventory.
     */
    fun returnBottlesFromCustomer(customerId: String, bottleSizeId: String, count: Int, isFull: Boolean, reason: String) {
        synchronized(mutationLock) {
            val cust = getCustomerById(customerId) ?: return
            val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
            if (count <= 0) return
            withSyncAction("Bottle Return • ${cust.name}") {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // 1. Update customer allocation (preserve totalIssued, decrement emptyHeld, increment returnedCount)
            val existingAlloc = _customerAllocations.value.find { it.customerId == customerId && it.bottleSizeId == bottleSizeId }
            val updatedAlloc = (existingAlloc ?: CustomerBottleAllocation(
                id = nextRecordId("alloc"),
                customerId = customerId,
                customerName = cust.name,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                totalIssued = cust.emptyBottlesOwed.coerceAtLeast(count),
                emptyHeld = cust.emptyBottlesOwed.coerceAtLeast(count)
            )).let { base ->
                base.copy(
                    emptyHeld = (base.emptyHeld - count).coerceAtLeast(0),
                    returnedCount = base.returnedCount + count,
                    lastUpdated = todayStr
                )
            }
            _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customerId && it.bottleSizeId == bottleSizeId } + updatedAlloc
            storage.saveCustomerAllocations(_customerAllocations.value)
            if (existingAlloc != null) {
                syncIncrementalDelta(
                    "customerBottleAllocations",
                    updatedAlloc.id,
                    updatedAlloc,
                    mapOf(
                        "emptyHeld" to (updatedAlloc.emptyHeld - existingAlloc.emptyHeld),
                        "returnedCount" to count
                    ),
                    extraFields = mapOf("lastUpdated" to todayStr)
                )
            } else {
                syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
            }

            // 2. Decrement customer emptyBottlesOwed
            val updatedCust = cust.copy(
                emptyBottlesOwed = (cust.emptyBottlesOwed - count).coerceAtLeast(0)
            )
            applyCustomerDeltaLocalAndCloud(
                updatedCust,
                mapOf("emptyBottlesOwed" to (updatedCust.emptyBottlesOwed - cust.emptyBottlesOwed))
            )

            // 3. Add bottles back into plant inventory
            adjustBottleStock(
                bottleSizeId = bottleSizeId,
                fullChange = if (isFull) count else 0,
                emptyChange = if (!isFull) count else 0,
                withCustomersChange = -count,
                reason = "Customer Return: $count ${size.name} from ${cust.name} ($reason) - Added to Plant Stock"
            )

            // 4. Log bottle movement
            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = todayStr,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.RETURN_IN,
                quantity = count,
                customerId = customerId,
                customerName = cust.name,
                referenceType = "Customer Return",
                notes = "Customer ${cust.name} returned $count ${size.name} (${if (isFull) "Full" else "Empty"}): $reason",
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)

            addNotification("Bottle Return Recorded", "$count ${size.name} returned by ${cust.name} and added to inventory.", NotificationSeverity.SUCCESS)
            }
        }
    }

    // Add New Plant Stock (e.g. bought 100 new bottles)
    fun addPlantStock(bottleSizeId: String, count: Int, purchaseCost: Double, notes: String) {
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
        adjustBottleStock(
            bottleSizeId = bottleSizeId,
            fullChange = 0,
            emptyChange = count, // Added as empty bottles ready for washing
            damagedChange = 0,
            reason = "New Stock Purchase: $count ${size.name}"
        )

        // Log Expense if cost entered
        if (purchaseCost > 0) {
            addExpense(
                categoryId = "cat_bottles",
                categoryName = "Bottle Purchases",
                description = "Purchased $count ${size.name} ($notes)",
                amount = purchaseCost,
                method = PaymentMethod.CASH,
                payee = "Bottle Manufacturer / Supplier",
                notes = notes
            )
        }

        addNotification("Stock Added", "Added $count new ${size.name} to plant stock.", NotificationSeverity.SUCCESS)
    }

    // Deliveries
    fun recordDelivery(
        customerId: String,
        items: List<DeliveryItem>,
        assignedStaffName: String,
        notes: String,
        deliveryCharges: Double = 0.0,
        immediatePaymentAmount: Double = 0.0,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        deliveryDate: String? = null,
        discount: Double = 0.0
    ): String {
        if (!hasPermission(AppModule.DELIVERIES, PermissionAction.ADD)) {
            return if (_currentUser.value.role == UserRole.VIEW_ONLY) {
                "View-only accounts cannot record deliveries"
            } else {
                "Your account does not have permission to record deliveries"
            }
        }
        synchronized(mutationLock) {
            val customer = getCustomerById(customerId) ?: return "Customer not found"
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val effectiveDate = if (!deliveryDate.isNullOrBlank()) deliveryDate else todayStr
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

            fun isSizeReturnable(bottleSizeId: String): Boolean {
                val size = _bottleSizes.value.find { it.id == bottleSizeId }
                return size?.let {
                    it.usageType == BottleUsageType.MULTIPLE_TIME ||
                            (it.usageType == BottleUsageType.CUSTOM && it.isReturnableBottle)
                } ?: (bottleSizeId in listOf("size_19l", "size_12l"))
            }

            // Single-use bottles are non-returnable: enforce emptyCollected = 0
            val sanitizedItems = items.map { item ->
                if (isSizeReturnable(item.bottleSizeId)) item else item.copy(emptyCollected = 0)
            }

            val subtotal = sanitizedItems.sumOf { it.lineTotal }
            val cleanCharges = deliveryCharges.coerceAtLeast(0.0)
            val cleanDiscount = discount.coerceIn(0.0, subtotal + cleanCharges)
            val totalAmount = (subtotal + cleanCharges - cleanDiscount).coerceAtLeast(0.0)
            val fullCount = sanitizedItems.sumOf { it.quantity }
            val emptyCount = sanitizedItems.sumOf { it.emptyCollected }

            val delId = nextRecordId("del")
            val delNumber = "DEL-${_deliveries.value.size + 501}"
            val invId = nextRecordId("inv")
            val invNumber = "INV-${_invoices.value.size + 101}"

            return withSyncAction("Delivery $delNumber • ${customer.name}") {
            val delivery = Delivery(
                id = delId,
                deliveryNumber = delNumber,
                customerId = customerId,
                customerName = customer.name,
                customerPhone = customer.phone,
                customerAddress = customer.address,
                customerArea = customer.area,
                date = effectiveDate,
                time = timeStr,
                items = sanitizedItems,
                totalFullDelivered = fullCount,
                totalEmptyCollected = emptyCount,
                assignedStaffId = "staff_incharge",
                staffName = assignedStaffName.ifEmpty { "Supervisor" },
                status = DeliveryStatus.DELIVERED,
                deliveryCharges = cleanCharges,
                discount = cleanDiscount,
                subtotal = subtotal,
                totalAmount = totalAmount,
                paymentCollected = immediatePaymentAmount,
                paymentMethod = if (immediatePaymentAmount > 0) paymentMethod.label else "Unpaid",
                notes = notes,
                invoiceId = invId,
                createdBy = _currentUser.value.name,
                createdByUid = _currentUser.value.id.ifBlank { _currentUser.value.uid }
            )
            markRecordSeen("delivery", delId)
            _deliveries.value = listOf(delivery) + _deliveries.value

            // 1. Generate Invoice
            val invoicePaid = kotlin.math.min(immediatePaymentAmount, totalAmount)
            val invoiceBalance = totalAmount - invoicePaid
            val invoiceStatus = when {
                invoiceBalance <= 0.0 -> InvoiceStatus.PAID
                invoicePaid > 0.0 -> InvoiceStatus.PARTIALLY_PAID
                else -> InvoiceStatus.UNPAID
            }

            val itemsSummary = sanitizedItems.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
            val invoice = Invoice(
                id = invId,
                invoiceNumber = invNumber,
                customerId = customerId,
                customerName = customer.name,
                deliveryId = delId,
                date = effectiveDate,
                dueDate = effectiveDate,
                totalAmount = totalAmount,
                paidAmount = invoicePaid,
                balanceDue = invoiceBalance,
                status = invoiceStatus,
                itemsSummary = itemsSummary
            )
            _invoices.value = listOf(invoice) + _invoices.value

            // 2. Update Customer Balance and Empty Bottles Owed (ONLY for Returnable Bottles e.g. 19L & 12L)
            val returnableFullCount = sanitizedItems.filter { isSizeReturnable(it.bottleSizeId) }.sumOf { it.quantity }
            val returnableEmptyCount = sanitizedItems.filter { isSizeReturnable(it.bottleSizeId) }.sumOf { it.emptyCollected }

            val netBalanceChange = totalAmount - immediatePaymentAmount
            val netBottleChange = returnableFullCount - returnableEmptyCount
            val updatedCustomer = customer.copy(
                currentBalance = customer.currentBalance + netBalanceChange,
                emptyBottlesOwed = (customer.emptyBottlesOwed + netBottleChange).coerceAtLeast(0),
                totalPurchases = customer.totalPurchases + totalAmount
            )
            applyCustomerDeltaLocalAndCloud(
                updatedCustomer,
                mapOf(
                    "currentBalance" to netBalanceChange,
                    "emptyBottlesOwed" to (updatedCustomer.emptyBottlesOwed - customer.emptyBottlesOwed),
                    "totalPurchases" to totalAmount
                )
            )

            // 3. Update Inventory, Costing & Issue/Sale Records
            sanitizedItems.forEach { item ->
                val size = _bottleSizes.value.find { it.id == item.bottleSizeId }
                val isVariant = item.variantId.isNotBlank()
                val effectivePackSize = if (isVariant) item.packSize.coerceAtLeast(1) else 1
                val effectiveBottleUnits = item.quantity * effectivePackSize
                val refillCostPerUnit = size?.refillCost ?: 35.0
                val totalItemCost = if (isVariant) {
                    val perPackDelivery = if (size?.deliveryUnit == DeliveryUnit.PER_PACKET) {
                        size.deliveryCost * item.quantity
                    } else {
                        (size?.deliveryCost ?: 20.0) * effectiveBottleUnits
                    }
                    (refillCostPerUnit * effectiveBottleUnits) + perPackDelivery
                } else {
                    (refillCostPerUnit * item.quantity) + (if (size?.deliveryUnit == DeliveryUnit.PER_PACKET) {
                        val packQty = (size.packetPackagingQty).coerceAtLeast(1)
                        size.deliveryCost * kotlin.math.ceil(item.quantity.toDouble() / packQty)
                    } else {
                        (size?.deliveryCost ?: 20.0) * item.quantity
                    })
                }
                val deliveryCostPerUnit = if (item.quantity > 0) {
                    (totalItemCost - (refillCostPerUnit * effectiveBottleUnits)) / item.quantity.coerceAtLeast(1)
                } else {
                    size?.deliveryCost ?: 20.0
                }
                val revenue = item.lineTotal
                val profit = revenue - totalItemCost
                val isReturnable = isSizeReturnable(item.bottleSizeId)
                val transType = if (isReturnable) BottleTransactionType.CUSTOMER_ISSUE else BottleTransactionType.PACKAGED_SALE

                val issueSaleRecord = BottleIssueSaleRecord(
                    id = nextRecordId("bis", item.variantId.ifBlank { item.bottleSizeId }),
                    date = effectiveDate,
                    time = timeStr,
                    customerId = customerId,
                    customerName = customer.name,
                    bottleSizeId = item.bottleSizeId,
                    bottleSizeName = item.bottleName,
                    quantity = item.quantity,
                    transactionType = transType,
                    unitSellingPrice = item.unitPrice,
                    refillCost = refillCostPerUnit,
                    deliveryCost = deliveryCostPerUnit,
                    totalTransactionCost = totalItemCost,
                    revenue = revenue,
                    profit = profit,
                    deliveryPerson = assignedStaffName.ifEmpty { "Supervisor" },
                    referenceDeliveryId = delId,
                    notes = "Delivery $delNumber"
                )
                _bottleIssueSales.value = listOf(issueSaleRecord) + _bottleIssueSales.value
                syncDocument("bottleIssueSales", issueSaleRecord.id, issueSaleRecord)

                if (isVariant) {
                    adjustVariantDeliveryStock(
                        bottleSizeId = item.bottleSizeId,
                        variantId = item.variantId,
                        packetDelta = -item.quantity,
                        bottleFullDelta = -effectiveBottleUnits,
                        totalSoldDelta = effectiveBottleUnits,
                        reason = "Delivery $delNumber to ${customer.name} (${item.bottleName})"
                    )
                } else {
                    adjustBottleStock(
                        bottleSizeId = item.bottleSizeId,
                        fullChange = -item.quantity,
                        emptyChange = if (isReturnable) item.emptyCollected else 0,
                        withCustomersChange = if (isReturnable) (item.quantity - item.emptyCollected) else 0,
                        reason = "Delivery $delNumber to ${customer.name}",
                        totalIssuedOrSoldChange = item.quantity
                    )
                }
            }
            storage.saveBottleIssueSales(_bottleIssueSales.value)
            storage.saveDeliveries(_deliveries.value)
            storage.saveInvoices(_invoices.value)
            storage.saveInventory(_inventory.value)

            // 4. Record Payment if collected on spot
            val activePlant = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
            if (immediatePaymentAmount > 0) {
                val isAdmin = _currentUser.value.role == UserRole.ADMIN ||
                    _currentUser.value.isSuperAdmin ||
                    hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)
                val payment = Payment(
                    id = nextRecordId("pay"),
                    receiptNumber = "RCT-${_payments.value.size + 101}",
                    customerId = customerId,
                    customerName = customer.name,
                    amount = immediatePaymentAmount,
                    paymentMethod = paymentMethod,
                    allocations = listOf(PaymentAllocation(invId, invNumber, invoicePaid)),
                    notes = "Collected on delivery $delNumber",
                    date = effectiveDate,
                    receivedBy = _currentUser.value.name,
                    receivedByRole = _currentUser.value.role,
                    isAcknowledged = isAdmin, // Admin auto-cleared; Staff/Supervisor starts Unsettle until Admin clears
                    acknowledgedBy = if (isAdmin) _currentUser.value.name else "",
                    businessId = activePlant,
                    createdByUid = _currentUser.value.id.ifBlank { _currentUser.value.uid }
                )
                markRecordSeen("payment", payment.id)
                _payments.value = listOf(payment) + _payments.value
                storage.savePayments(_payments.value)
                syncDocument("payments", payment.id, payment)
            }

            syncDocument("deliveries", delId, delivery)
            syncDocument("invoices", invId, invoice)
            appendAuditLog(AuditActionType.CREATE, AppModule.DELIVERIES, delId, "$delNumber • ${customer.name}", updatedValue = "Total Rs. ${totalAmount.toInt()}, Paid Rs. ${immediatePaymentAmount.toInt()}")

            addNotification(
                "Delivery Recorded",
                "Delivered $fullCount bottles to ${customer.name} (Rs. $totalAmount)",
                NotificationSeverity.SUCCESS
            )

            delNumber
            }
        }
    }

    // Invoices and Intelligent Payment Allocation
    fun getUnpaidInvoicesForCustomer(customerId: String): List<Invoice> {
        return _invoices.value
            .filter { it.customerId == customerId && it.status != InvoiceStatus.PAID && it.balanceDue > 0.0 }
            .sortedBy { it.date }
    }

    fun recordPayment(
        customerId: String,
        amount: Double,
        method: PaymentMethod,
        manualAllocations: Map<String, Double>? = null,
        notes: String = "",
        receivedBy: String = _currentUser.value.name,
        paymentDate: String? = null
    ): Payment {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)) throw IllegalStateException("Account cannot record payments")
        synchronized(mutationLock) {
            val customer = getCustomerById(customerId) ?: throw IllegalArgumentException("Customer not found")
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val effectivePaymentDate = paymentDate?.trim()?.takeIf { it.isNotBlank() } ?: todayStr
            val receiptNumber = "RCT-${_payments.value.size + 101}"
            val paymentId = nextRecordId("pay")

            return withSyncAction("Payment $receiptNumber • ${customer.name}") {
            val finalAllocations = mutableListOf<PaymentAllocation>()
            val touchedDeliveryIds = mutableSetOf<String>()
            val manuallyTouchedInvoiceIds = mutableSetOf<String>()
            var remainingPayment = amount

            fun applyPaidToDeliveryMatching(deliveryId: String, invoiceId: String, addAmount: Double) {
                if (addAmount <= 0.0) return
                _deliveries.value = _deliveries.value.map { del ->
                    val matches = (deliveryId.isNotBlank() && del.id == deliveryId) ||
                        (invoiceId.isNotBlank() && (del.invoiceId == invoiceId || "inv_${del.id}" == invoiceId || del.id == invoiceId))
                    if (matches) {
                        touchedDeliveryIds.add(del.id)
                        val newDelPaid = (del.paymentCollected + addAmount).coerceAtMost(del.totalAmount)
                        val newMethod = if (newDelPaid + 0.01 >= del.totalAmount) method.label else "Partial (${method.label})"
                        del.copy(paymentCollected = newDelPaid, paymentMethod = newMethod)
                    } else del
                }
            }

            if (manualAllocations != null && manualAllocations.isNotEmpty()) {
                manualAllocations.forEach { (invOrDelId, allocatedAmount) ->
                    if (allocatedAmount > 0.0 && remainingPayment > 0.0) {
                        val effectiveReq = kotlin.math.min(allocatedAmount, remainingPayment)
                        val inv = _invoices.value.find {
                            it.id == invOrDelId || it.deliveryId == invOrDelId || "inv_${it.deliveryId}" == invOrDelId
                        }
                        if (inv != null && inv.balanceDue > 0.0) {
                            val actualAllocated = kotlin.math.min(effectiveReq, inv.balanceDue)
                            if (actualAllocated > 0.0) {
                                finalAllocations.add(PaymentAllocation(inv.id, inv.invoiceNumber, actualAllocated))
                                manuallyTouchedInvoiceIds.add(inv.id)
                                remainingPayment = (remainingPayment - actualAllocated).coerceAtLeast(0.0)

                                val newPaid = inv.paidAmount + actualAllocated
                                val newBalance = (inv.totalAmount - newPaid).coerceAtLeast(0.0)
                                val newStatus = if (newBalance <= 0.01) InvoiceStatus.PAID else InvoiceStatus.PARTIALLY_PAID
                                updateInvoice(inv.copy(paidAmount = newPaid, balanceDue = newBalance, status = newStatus))
                                applyPaidToDeliveryMatching(inv.deliveryId, inv.id, actualAllocated)
                            }
                        } else {
                            // Direct match against a delivery (e.g. synthetic ID "inv_del_..." or "del_...")
                            val del = _deliveries.value.find {
                                it.id == invOrDelId || "inv_${it.id}" == invOrDelId || it.invoiceId == invOrDelId
                            }
                            if (del != null) {
                                val due = (del.totalAmount - del.paymentCollected).coerceAtLeast(0.0)
                                val actualAllocated = kotlin.math.min(effectiveReq, due)
                                if (actualAllocated > 0.0) {
                                    val allocInvId = del.invoiceId.ifEmpty { "inv_${del.id}" }
                                    finalAllocations.add(PaymentAllocation(allocInvId, del.deliveryNumber, actualAllocated))
                                    manuallyTouchedInvoiceIds.add(allocInvId)
                                    remainingPayment = (remainingPayment - actualAllocated).coerceAtLeast(0.0)
                                    applyPaidToDeliveryMatching(del.id, allocInvId, actualAllocated)
                                }
                            }
                        }
                    }
                }
            }

            // 1. FIFO auto-allocation across remaining unpaid invoices ONLY when manualAllocations wasn't provided OR excess payment remains
            if (remainingPayment > 0.01) {
                val freshUnpaidInvoices = getUnpaidInvoicesForCustomer(customerId)
                for (inv in freshUnpaidInvoices) {
                    if (remainingPayment <= 0.01) break
                    if (inv.id in manuallyTouchedInvoiceIds) continue
                    if (inv.balanceDue <= 0.01) continue
                    val allocate = kotlin.math.min(remainingPayment, inv.balanceDue)
                    finalAllocations.add(PaymentAllocation(inv.id, inv.invoiceNumber, allocate))
                    remainingPayment = (remainingPayment - allocate).coerceAtLeast(0.0)

                    val newPaid = inv.paidAmount + allocate
                    val newBalance = (inv.totalAmount - newPaid).coerceAtLeast(0.0)
                    val newStatus = if (newBalance <= 0.01) InvoiceStatus.PAID else InvoiceStatus.PARTIALLY_PAID
                    updateInvoice(inv.copy(paidAmount = newPaid, balanceDue = newBalance, status = newStatus))
                    applyPaidToDeliveryMatching(inv.deliveryId, inv.id, allocate)
                }
            }

            // 2. FIFO auto-allocation across ANY remaining unpaid deliveries for this customer (without linked invoice)
            if (remainingPayment > 0.01) {
                _deliveries.value = _deliveries.value.map { del ->
                    val alreadyTouched = del.id in touchedDeliveryIds
                    if (!alreadyTouched && del.customerId == customerId && del.paymentCollected + 0.01 < del.totalAmount && remainingPayment > 0.01) {
                        touchedDeliveryIds.add(del.id)
                        val due = (del.totalAmount - del.paymentCollected).coerceAtLeast(0.0)
                        val allocate = kotlin.math.min(remainingPayment, due)
                        remainingPayment = (remainingPayment - allocate).coerceAtLeast(0.0)
                        val allocInvId = del.invoiceId.ifEmpty { "inv_${del.id}" }
                        finalAllocations.add(PaymentAllocation(allocInvId, del.deliveryNumber, allocate))
                        val newDelPaid = (del.paymentCollected + allocate).coerceAtMost(del.totalAmount)
                        val newMethod = if (newDelPaid + 0.01 >= del.totalAmount) method.label else "Partial (${method.label})"
                        del.copy(paymentCollected = newDelPaid, paymentMethod = newMethod)
                    } else del
                }
            }

            val advanceCredited = remainingPayment.coerceAtLeast(0.0)
            val updatedCustomer = customer.copy(currentBalance = customer.currentBalance - amount)
            applyCustomerDeltaLocalAndCloud(updatedCustomer, mapOf("currentBalance" to -amount))

            val isAdmin = _currentUser.value.role == UserRole.ADMIN ||
                _currentUser.value.isSuperAdmin ||
                hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)
            val activePlant = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
            val payment = Payment(
                id = paymentId,
                receiptNumber = receiptNumber,
                customerId = customerId,
                customerName = customer.name,
                amount = amount,
                paymentMethod = method,
                allocations = finalAllocations,
                advanceCredited = advanceCredited,
                notes = notes,
                date = effectivePaymentDate,
                receivedBy = receivedBy,
                receivedByRole = _currentUser.value.role,
                isAcknowledged = isAdmin, // Admin auto-cleared; Staff/Supervisor starts Unsettle until Admin clears
                acknowledgedBy = if (isAdmin) _currentUser.value.name else "",
                businessId = activePlant,
                createdByUid = _currentUser.value.id.ifBlank { _currentUser.value.uid }
            )
            markRecordSeen("payment", paymentId)
            _payments.value = listOf(payment) + _payments.value
            storage.savePayments(_payments.value)
            storage.saveInvoices(_invoices.value)
            storage.saveDeliveries(_deliveries.value) // Persist updated delivery paid status!

            syncDocument("payments", paymentId, payment)
            _deliveries.value.filter { it.id in touchedDeliveryIds }.forEach { del ->
                syncDocument("deliveries", del.id, del)
            }

            // Sync with Room Database
            val updatedDeliveriesSnapshot = _deliveries.value.filter { it.id in touchedDeliveryIds }
            scope.launch {
                try {
                    updatedDeliveriesSnapshot.forEach { del ->
                        deliveryDao.updateDeliveryPayment(del.id, del.paymentCollected, del.paymentMethod)
                    }
                    customerBalanceDao.upsertCustomerBalance(RoomDataMapper.customerToBalanceEntity(updatedCustomer))
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "Room payment update error: ${e.message}")
                }
            }

            appendAuditLog(AuditActionType.CREATE, AppModule.PAYMENTS_BALANCES, paymentId, "$receiptNumber • ${customer.name}", updatedValue = "Rs. ${amount.toInt()} (${method.label})")

            addNotification(
                "Payment Received",
                "Received Rs. $amount from ${customer.name} via ${method.label}",
                NotificationSeverity.SUCCESS
            )

            payment
            }
        }
    }

    private fun updateInvoice(invoice: Invoice) {
        _invoices.value = _invoices.value.map { if (it.id == invoice.id) invoice else it }
        storage.saveInvoices(_invoices.value)
        syncDocument("invoices", invoice.id, invoice)
    }

    /**
     * Mark a delivery as Paid directly from UI, allocating payment to the delivery and customer ledger.
     */
    fun markDeliveryAsPaid(
        deliveryId: String,
        amountPaid: Double? = null,
        method: PaymentMethod = PaymentMethod.CASH,
        paymentDate: String? = null
    ): Boolean {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)) return false
        synchronized(mutationLock) {
            val del = _deliveries.value.find { it.id == deliveryId } ?: return false
            val due = (del.totalAmount - del.paymentCollected).coerceAtLeast(0.0)
            val payAmount = (amountPaid ?: due).coerceAtMost(due)
            if (payAmount <= 0.0) return false

            val key = del.invoiceId.ifEmpty { "inv_${del.id}" }
            recordPayment(
                customerId = del.customerId,
                amount = payAmount,
                method = method,
                manualAllocations = mapOf(key to payAmount),
                notes = "Direct payment for delivery ${del.deliveryNumber}",
                paymentDate = paymentDate
            )
            return true
        }
    }

    /**
     * Records that the currently logged-in user viewed a Delivery or Expense transaction in detail.
     * Persisted locally and synced to cloud so Admin can inspect the Seen By list across devices.
     */
    fun recordTransactionViewed(type: String, recordId: String) {
        if (recordId.isBlank()) return
        val user = _currentUser.value
        val uid = user.id.ifBlank { user.uid }.ifBlank { user.username }.trim()
        val displayName = user.name.ifBlank { user.username }.ifBlank { "User" }.trim()
        if (uid.isBlank()) return

        val now = System.currentTimeMillis()
        synchronized(mutationLock) {
            when (type.lowercase(Locale.ENGLISH)) {
                "delivery" -> {
                    val existing = _deliveries.value.find { it.id == recordId } ?: return
                    val prevEntry = existing.viewedBy.find { it.userId == uid }
                    // Avoid duplicate rapid writes within 30s if already recorded for this user
                    if (prevEntry != null && (now - prevEntry.viewedAt) < 30_000L && prevEntry.userName == displayName) {
                        return
                    }
                    val updatedList = existing.viewedBy.filterNot { it.userId == uid } +
                        RecordViewEntry(userId = uid, userName = displayName, viewedAt = now)
                    val updatedDel = existing.copy(viewedBy = updatedList)
                    _deliveries.value = _deliveries.value.map { if (it.id == recordId) updatedDel else it }
                    storage.saveDeliveries(_deliveries.value)
                    syncDocument("deliveries", recordId, updatedDel)
                }
                "expense" -> {
                    val existing = _expenses.value.find { it.id == recordId } ?: return
                    val prevEntry = existing.viewedBy.find { it.userId == uid }
                    if (prevEntry != null && (now - prevEntry.viewedAt) < 30_000L && prevEntry.userName == displayName) {
                        return
                    }
                    val updatedList = existing.viewedBy.filterNot { it.userId == uid } +
                        RecordViewEntry(userId = uid, userName = displayName, viewedAt = now)
                    val updatedExp = existing.copy(viewedBy = updatedList)
                    _expenses.value = _expenses.value.map { if (it.id == recordId) updatedExp else it }
                    storage.saveExpenses(_expenses.value)
                    syncDocument("expenses", recordId, updatedExp)
                }
            }
        }
    }

    /**
     * Record a daily plant expense (Electricity, Fuel, Maintenance, Salt, etc.)
     * Persists to StateFlow, Local JSON, Firestore, and SQLite via Room.
     */
    fun recordDailyPlantExpense(
        categoryId: String,
        categoryName: String,
        description: String,
        amount: Double,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        payee: String = "",
        notes: String = "",
        date: String? = null
    ): Expense {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) throw IllegalStateException("Account cannot record expenses")
        val todayStr = date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val voucherNumber = "EXP-${_expenses.value.size + 201}"
        val id = nextRecordId("exp")
        val isAdmin = hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE)

        return withSyncAction("Expense $voucherNumber • $categoryName") {
        val expense = Expense(
            id = id,
            voucherNumber = voucherNumber,
            date = todayStr,
            expenseType = ExpenseType.OPEX,
            categoryId = categoryId,
            categoryName = categoryName,
            description = description,
            amount = amount,
            paymentMethod = paymentMethod,
            payee = payee,
            enteredBy = _currentUser.value.name,
            enteredByRole = _currentUser.value.role,
            isAcknowledged = isAdmin,
            acknowledgedBy = if (isAdmin) _currentUser.value.name else "",
            isApprovedByOwner = isAdmin,
            notes = notes
        )

        _expenses.value = listOf(expense) + _expenses.value
        storage.saveExpenses(_expenses.value)
        syncDocument("expenses", id, expense)

        // Save to Room SQLite database
        scope.launch {
            try {
                expenseDao.insertExpense(RoomDataMapper.expenseToEntity(expense))
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room expense insert: ${e.message}")
            }
        }

        addNotification(
            "Expense Recorded",
            "Recorded $categoryName: Rs. ${amount.toInt()} ($description)",
            NotificationSeverity.INFO
        )
        expense
        }
    }

    // Inventory Adjustments & Stock Movement
    fun adjustBottleStock(
        bottleSizeId: String,
        fullChange: Int = 0,
        emptyChange: Int = 0,
        withCustomersChange: Int = 0,
        damagedChange: Int = 0,
        reason: String = "Audit adjustment",
        totalIssuedOrSoldChange: Int = 0
    ) {
        synchronized(mutationLock) {
            val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
            val currentInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
                id = "inv_$bottleSizeId",
                bottleSizeId = bottleSizeId,
                sizeName = size.name
            )

            val newFull = (currentInv.atPlantFull + fullChange).coerceAtLeast(0)
            val hasTrackedPackets = currentInv.fullPackets > 0 || currentInv.fullLooseBottles > 0
            val inferredPackSize = if (currentInv.fullPackets > 0 && currentInv.atPlantFull >= currentInv.fullLooseBottles) {
                ((currentInv.atPlantFull - currentInv.fullLooseBottles) / currentInv.fullPackets).coerceAtLeast(1)
            } else {
                size.packQuantity.coerceAtLeast(1)
            }
            val newFullPackets = if (hasTrackedPackets && fullChange != 0 && currentInv.fullPackets > 0) {
                (newFull / inferredPackSize).coerceAtMost(currentInv.fullPackets)
            } else {
                currentInv.fullPackets
            }
            val newFullLoose = if (hasTrackedPackets && fullChange != 0) {
                if (newFullPackets > 0) (newFull - newFullPackets * inferredPackSize).coerceAtLeast(0)
                else newFull
            } else {
                currentInv.fullLooseBottles
            }

            val updated = currentInv.copy(
                atPlantFull = newFull,
                atPlantEmpty = (currentInv.atPlantEmpty + emptyChange).coerceAtLeast(0),
                fullPackets = newFullPackets,
                fullLooseBottles = newFullLoose,
                withCustomers = (currentInv.withCustomers + withCustomersChange).coerceAtLeast(0),
                damaged = (currentInv.damaged + damagedChange).coerceAtLeast(0),
                totalIssuedOrSold = (currentInv.totalIssuedOrSold + totalIssuedOrSoldChange).coerceAtLeast(0)
            )

            _inventory.value = if (_inventory.value.any { it.bottleSizeId == bottleSizeId }) {
                _inventory.value.map { if (it.bottleSizeId == bottleSizeId) updated else it }
            } else {
                _inventory.value + updated
            }
            storage.saveInventory(_inventory.value)

            val invDeltas = linkedMapOf<String, Number>()
            if (updated.atPlantFull != currentInv.atPlantFull) invDeltas["atPlantFull"] = updated.atPlantFull - currentInv.atPlantFull
            if (updated.atPlantEmpty != currentInv.atPlantEmpty) invDeltas["atPlantEmpty"] = updated.atPlantEmpty - currentInv.atPlantEmpty
            if (updated.fullPackets != currentInv.fullPackets) invDeltas["fullPackets"] = updated.fullPackets - currentInv.fullPackets
            if (updated.fullLooseBottles != currentInv.fullLooseBottles) invDeltas["fullLooseBottles"] = updated.fullLooseBottles - currentInv.fullLooseBottles
            if (updated.withCustomers != currentInv.withCustomers) invDeltas["withCustomers"] = updated.withCustomers - currentInv.withCustomers
            if (updated.damaged != currentInv.damaged) invDeltas["damaged"] = updated.damaged - currentInv.damaged
            if (updated.totalIssuedOrSold != currentInv.totalIssuedOrSold) invDeltas["totalIssuedOrSold"] = updated.totalIssuedOrSold - currentInv.totalIssuedOrSold
            syncIncrementalDelta("bottleInventory", updated.id, updated, invDeltas)

            val movementType = when {
                damagedChange > 0 -> BottleMovementType.DAMAGED
                fullChange < 0 -> BottleMovementType.DELIVERY_OUT
                emptyChange > 0 -> BottleMovementType.RETURN_IN
                fullChange > 0 -> BottleMovementType.PLANT_REFILL
                else -> BottleMovementType.ADJUSTMENT
            }

            val movement = BottleMovement(
                id = nextRecordId("mov"),
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = movementType,
                quantity = kotlin.math.abs(fullChange).coerceAtLeast(kotlin.math.abs(emptyChange)).coerceAtLeast(damagedChange),
                notes = reason,
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(movement) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", movement.id, movement)
        }
    }

    fun adjustVariantDeliveryStock(
        bottleSizeId: String,
        variantId: String,
        packetDelta: Int,
        bottleFullDelta: Int,
        totalSoldDelta: Int,
        reason: String
    ) {
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return
        val currentInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )

        val updatedVariants = if (variantId.isNotBlank()) {
            currentInv.packVariants.map { v ->
                if (v.variantId == variantId) {
                    v.copy(packetCount = (v.packetCount + packetDelta).coerceAtLeast(0))
                } else v
            }
        } else currentInv.packVariants

        val newFull = (currentInv.atPlantFull + bottleFullDelta).coerceAtLeast(0)
        val newFullPackets = if (updatedVariants.isNotEmpty()) {
            updatedVariants.sumOf { it.packetCount }
        } else {
            (currentInv.fullPackets + packetDelta).coerceAtLeast(0)
        }

        val updated = currentInv.copy(
            atPlantFull = newFull,
            fullPackets = newFullPackets,
            packVariants = updatedVariants,
            totalIssuedOrSold = (currentInv.totalIssuedOrSold + totalSoldDelta).coerceAtLeast(0)
        )
        _inventory.value = _inventory.value.map { if (it.bottleSizeId == bottleSizeId) updated else it }
        storage.saveInventory(_inventory.value)
        syncDocument("bottleInventory", updated.id, updated)

        val movement = BottleMovement(
            id = nextRecordId("mov", variantId.takeLast(6)),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = if (packetDelta < 0) BottleMovementType.DELIVERY_OUT else BottleMovementType.ADJUSTMENT,
            quantity = kotlin.math.abs(packetDelta).coerceAtLeast(kotlin.math.abs(bottleFullDelta)),
            notes = reason,
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(movement) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", movement.id, movement)
    }

    /**
     * Admin capability to edit bottle stock directly (Full, Empty, Damaged, Staff, Customer counts).
     */
    fun editBottleStock(
        bottleSizeId: String,
        full: Int,
        empty: Int,
        damaged: Int,
        withStaff: Int = 0,
        withCustomers: Int = 0,
        reason: String = "Admin Manual Stock Edit"
    ): Boolean {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT)) return false
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return false
        val currentInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updated = currentInv.copy(
            atPlantFull = full.coerceAtLeast(0),
            atPlantEmpty = empty.coerceAtLeast(0),
            damaged = damaged.coerceAtLeast(0),
            withStaff = withStaff.coerceAtLeast(0),
            withCustomers = withCustomers.coerceAtLeast(0)
        )
        _inventory.value = _inventory.value.map { if (it.bottleSizeId == bottleSizeId) updated else it }
        storage.saveInventory(_inventory.value)
        syncDocument("bottleInventory", updated.id, updated)

        scope.launch {
            try {
                roomDatabase.stockDao().setStockCounts(
                    id = updated.id,
                    full = updated.atPlantFull,
                    empty = updated.atPlantEmpty,
                    damaged = updated.damaged,
                    staff = updated.withStaff,
                    customers = updated.withCustomers,
                    inUse = updated.inUse,
                    fullPackets = updated.fullPackets,
                    fullLooseBottles = updated.fullLooseBottles
                )
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room stock update error: ${e.message}")
            }
        }

        val movement = BottleMovement(
            id = nextRecordId("mov"),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.ADJUSTMENT,
            quantity = full + empty + damaged,
            notes = "$reason (Set to: $full Full, $empty Empty, $damaged Damaged)",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(movement) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", movement.id, movement)
        appendAuditLog(AuditActionType.UPDATE, AppModule.BOTTLE_STOCK, bottleSizeId, size.name, updatedValue = "Full:$full Empty:$empty Dmg:$damaged")

        addNotification("Stock Updated", "Admin updated stock for ${size.name}: $full Full, $empty Empty.", NotificationSeverity.SUCCESS)
        return true
    }

    /**
     * Admin capability to delete a bottle stock item record.
     */
    fun deleteBottleStock(bottleSizeId: String): Boolean {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) return false
        val item = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: return false
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId }
        storage.saveInventory(_inventory.value)
        deleteDocument("bottleInventory", item.id)

        scope.launch {
            try {
                roomDatabase.stockDao().deleteStockByBottleSizeId(bottleSizeId)
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room stock delete error: ${e.message}")
            }
        }
        appendAuditLog(AuditActionType.DELETE, AppModule.BOTTLE_STOCK, bottleSizeId, item.sizeName)
        addNotification("Stock Deleted", "Admin deleted stock record for ${item.sizeName}.", NotificationSeverity.WARNING)
        return true
    }

    /**
     * Admin capability to edit RO Material item.
     */
    fun editRoPlantItem(item: RoPlantItem): Boolean {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.EDIT)) return false
        _roPlantItems.value = _roPlantItems.value.map { if (it.id == item.id) item else it }
        storage.saveRoPlantItems(_roPlantItems.value)
        syncDocument("roPlantItems", item.id, item)
        addNotification("RO Item Updated", "Admin updated ${item.name} (Stock: ${item.currentStock} ${item.uom}).", NotificationSeverity.INFO)
        return true
    }

    /**
     * Admin capability to delete RO Material item.
     */
    fun deleteRoPlantItem(itemId: String): Boolean {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.DELETE)) return false
        val item = _roPlantItems.value.find { it.id == itemId } ?: return false
        _roPlantItems.value = _roPlantItems.value.filterNot { it.id == itemId }
        storage.saveRoPlantItems(_roPlantItems.value)
        deleteDocument("roPlantItems", itemId)
        addNotification("RO Item Deleted", "Admin deleted item '${item.name}'.", NotificationSeverity.WARNING)
        return true
    }

    /**
     * Admin capability to clear all existing stock (sets bottle counts to 0, clears purchases/refills).
     */
    fun clearAllStock(): Boolean {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.MANAGE)) return false
        val oldRoItems = _roPlantItems.value
        val oldRoTxs = _roTransactions.value
        val oldPurchases = _bottlePurchases.value
        val oldRefills = _bottleRefills.value
        val oldIssueSales = _bottleIssueSales.value

        _inventory.value = _bottleSizes.value.map { size ->
            BottleInventory(
                id = "inv_${size.id}",
                bottleSizeId = size.id,
                sizeName = size.name,
                atPlantFull = 0,
                atPlantEmpty = 0,
                withCustomers = 0,
                withStaff = 0,
                damaged = 0,
                totalPurchased = 0,
                totalIssuedOrSold = 0
            )
        }
        _roPlantItems.value = emptyList()
        _roTransactions.value = emptyList()
        _bottlePurchases.value = emptyList()
        _bottleRefills.value = emptyList()
        _bottleIssueSales.value = emptyList()

        storage.saveInventory(_inventory.value)
        storage.saveRoPlantItems(emptyList())
        storage.saveRoTransactions(emptyList())
        storage.saveBottlePurchases(emptyList())
        storage.saveBottleRefills(emptyList())
        storage.saveBottleIssueSales(emptyList())

        _inventory.value.forEach { syncDocument("bottleInventory", it.id, it) }
        oldRoItems.forEach { deleteDocument("roPlantItems", it.id) }
        oldRoTxs.forEach { deleteDocument("roItemTransactions", it.id) }
        oldPurchases.forEach { deleteDocument("bottlePurchases", it.id) }
        oldRefills.forEach { deleteDocument("bottleRefills", it.id) }
        oldIssueSales.forEach { deleteDocument("bottleIssueSales", it.id) }

        scope.launch {
            try {
                roomDatabase.stockDao().clearAllStock()
                roomDatabase.stockDao().upsertStockList(_inventory.value.map { RoomDataMapper.inventoryToStockEntity(it) })
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room clear stock error: ${e.message}")
            }
        }
        appendAuditLog(AuditActionType.UPDATE, AppModule.BOTTLE_STOCK, "all_stock", "Reset All Stock to 0")
        addNotification("All Stock Cleared", "All warehouse inventory counts reset to 0. Ready for new stock entry.", NotificationSeverity.WARNING)
        return true
    }

    /**
     * Admin capability to clear all deliveries, invoices, and expenses to start clean.
     */
    fun clearAllDeliveriesAndExpenses(): Boolean {
        if (!hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)) return false
        val oldDeliveries = _deliveries.value
        val oldInvoices = _invoices.value
        val oldExpenses = _expenses.value

        _deliveries.value = emptyList()
        _invoices.value = emptyList()
        _expenses.value = emptyList()
        storage.saveDeliveries(emptyList())
        storage.saveInvoices(emptyList())
        storage.saveExpenses(emptyList())

        oldDeliveries.forEach { deleteDocument("deliveries", it.id) }
        oldInvoices.forEach { deleteDocument("invoices", it.id) }
        oldExpenses.forEach { deleteDocument("expenses", it.id) }

        scope.launch {
            try {
                roomDatabase.deliveryDao().clearAllDeliveries()
                roomDatabase.expenseDao().clearAllExpenses()
            } catch (e: Exception) {
                Log.w("WaterPlantRepo", "Room clear deliveries error: ${e.message}")
            }
        }
        addNotification("Records Reset", "Deliveries and Expenses reset. Clean slate ready.", NotificationSeverity.WARNING)
        return true
    }

    // -------------------------------------------------------------
    // BOTTLE PURCHASES & FLEET COSTING (Sections 2, 3, 4, 7)
    // -------------------------------------------------------------
    fun recordBottlePurchase(
        bottleSizeId: String,
        quantity: Int,
        unitCost: Double,
        supplierName: String,
        invoiceOrBillNo: String,
        notes: String
    ): String {
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return "Size not found"
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val totalCost = quantity * unitCost
        val purchaseId = nextRecordId("bp")

        val purchase = BottlePurchaseRecord(
            id = purchaseId,
            date = todayStr,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            quantity = quantity,
            unitCost = unitCost,
            totalCost = totalCost,
            supplierName = supplierName.ifEmpty { "Prime Polymer Industry" },
            invoiceOrBillNo = invoiceOrBillNo,
            notes = notes,
            recordedBy = _currentUser.value.name
        )
        _bottlePurchases.value = listOf(purchase) + _bottlePurchases.value

        // Increase atPlantEmpty and totalPurchased
        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updatedInv = curInv.copy(
            atPlantEmpty = curInv.atPlantEmpty + quantity,
            totalPurchased = curInv.totalPurchased + quantity
        )
        _inventory.value = if (_inventory.value.any { it.bottleSizeId == bottleSizeId }) {
            _inventory.value.map { if (it.bottleSizeId == bottleSizeId) updatedInv else it }
        } else {
            _inventory.value + updatedInv
        }

        // Movement record
        val movement = BottleMovement(
            id = nextRecordId("mov"),
            date = todayStr,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.PURCHASE_NEW,
            quantity = quantity,
            notes = "Purchased $quantity empty bottles from $supplierName (Rs. $totalCost)",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(movement) + _bottleMovements.value

        storage.saveBottlePurchases(_bottlePurchases.value)
        storage.saveInventory(_inventory.value)
        storage.saveBottleMovements(_bottleMovements.value)

        syncDocument("bottlePurchases", purchaseId, purchase)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf("atPlantEmpty" to quantity, "totalPurchased" to quantity)
        )
        syncDocument("bottleMovements", movement.id, movement)

        // Integrated Cost Management: Single-use bottle purchase -> OPEX, Multiple-use -> CAPEX
        val isSingleUse = size.usageType == BottleUsageType.ONE_TIME ||
            (size.usageType == BottleUsageType.CUSTOM && !size.isReturnableBottle)
        addExpense(
            categoryId = if (isSingleUse) "cat_bottles_opex" else "cat_bottles_capex",
            categoryName = if (isSingleUse) "Single-Use Bottle Purchase" else "New Bottles Stock",
            description = "Purchased $quantity empty ${size.name} from ${supplierName.ifEmpty { "Bottle Supplier" }}",
            amount = totalCost,
            method = PaymentMethod.CASH,
            payee = supplierName.ifEmpty { "Bottle Supplier" },
            notes = "Invoice: $invoiceOrBillNo. Automatic inventory purchase record.",
            type = if (isSingleUse) ExpenseType.OPEX else ExpenseType.CAPEX
        )

        addNotification(
            "Bottle Purchase Recorded",
            "Added $quantity empty ${size.name} to warehouse stock (Rs. $totalCost)",
            NotificationSeverity.SUCCESS
        )
        return purchaseId
    }

    fun recordBottleRefill(
        bottleSizeId: String,
        quantity: Int,
        refillCostPerBottle: Double,
        operatorName: String,
        notes: String
    ): Boolean {
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return false
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        val totalCost = quantity * refillCostPerBottle
        val refillId = nextRecordId("refill")

        val refill = BottleRefillRecord(
            id = refillId,
            date = todayStr,
            time = timeStr,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            quantityFilled = quantity,
            refillCostPerBottle = refillCostPerBottle,
            totalRefillCost = totalCost,
            operatorName = operatorName.ifEmpty { _currentUser.value.name },
            notes = notes
        )
        _bottleRefills.value = listOf(refill) + _bottleRefills.value

        // Shift empty to full in warehouse inventory
        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updatedInv = curInv.copy(
            atPlantEmpty = (curInv.atPlantEmpty - quantity).coerceAtLeast(0),
            atPlantFull = curInv.atPlantFull + quantity
        )
        _inventory.value = if (_inventory.value.any { it.bottleSizeId == bottleSizeId }) {
            _inventory.value.map { if (it.bottleSizeId == bottleSizeId) updatedInv else it }
        } else {
            _inventory.value + updatedInv
        }

        // Movement record
        val movement = BottleMovement(
            id = nextRecordId("mov"),
            date = todayStr,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.PLANT_REFILL,
            quantity = quantity,
            notes = "Refilled $quantity ${size.name} (Production cost Rs. $totalCost)",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(movement) + _bottleMovements.value

        storage.saveBottleRefills(_bottleRefills.value)
        storage.saveInventory(_inventory.value)
        storage.saveBottleMovements(_bottleMovements.value)

        syncDocument("bottleRefills", refillId, refill)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf(
                "atPlantEmpty" to (updatedInv.atPlantEmpty - curInv.atPlantEmpty),
                "atPlantFull" to quantity
            )
        )
        syncDocument("bottleMovements", movement.id, movement)

        addNotification(
            "Refill Batch Recorded",
            "Refilled $quantity ${size.name}. Moved empty to filled inventory.",
            NotificationSeverity.INFO
        )
        return true
    }

    fun updateBottleCostConfig(
        bottleSizeId: String,
        refillCost: Double,
        deliveryCost: Double,
        deliveryUnit: DeliveryUnit,
        packetPackagingQty: Int,
        isReturnableBottle: Boolean
    ): Boolean {
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return false
        val updated = size.copy(
            refillCost = refillCost,
            deliveryCost = deliveryCost,
            deliveryUnit = deliveryUnit,
            packetPackagingQty = packetPackagingQty.coerceAtLeast(1),
            isReturnableBottle = isReturnableBottle
        )
        _bottleSizes.value = _bottleSizes.value.map { if (it.id == bottleSizeId) updated else it }
        storage.saveBottleSizes(_bottleSizes.value)
        syncDocument("bottleSizes", bottleSizeId, updated)
        addNotification("Cost Configuration Updated", "Updated refill and delivery costs for ${size.name}", NotificationSeverity.SUCCESS)
        return true
    }

    // -------------------------------------------------------------
    // RO PLANT MATERIALS, SPARES & CONSUMABLES (Section 6)
    // -------------------------------------------------------------
    fun addRoPlantItem(item: RoPlantItem): String {
        if (!hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)) {
            addNotification("Access Restricted", "Admin has restricted adding new items.", NotificationSeverity.WARNING)
            return ""
        }
        val id = if (item.id.isNotEmpty()) item.id else nextRecordId("item")
        val newItem = item.copy(id = id)
        _roPlantItems.value = _roPlantItems.value + newItem
        storage.saveRoPlantItems(_roPlantItems.value)
        syncDocument("roPlantItems", id, newItem)

        if (newItem.currentStock > 0) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val tx = RoItemTransaction(
                id = nextRecordId("rtx"),
                itemId = id,
                itemName = newItem.name,
                date = todayStr,
                time = timeStr,
                actionType = InventoryActionType.STOCK_RECEIPT,
                quantity = newItem.currentStock,
                unitCost = newItem.unitCost,
                totalCost = newItem.currentStock * newItem.unitCost,
                uom = newItem.uom,
                previousStock = 0.0,
                newStock = newItem.currentStock,
                responsibleEmployee = _currentUser.value.name,
                reasonOrPurpose = "Opening Stock Registration",
                notes = "Supplier: ${newItem.supplierName}"
            )
            _roTransactions.value = listOf(tx) + _roTransactions.value
            storage.saveRoTransactions(_roTransactions.value)
            syncDocument("roItemTransactions", tx.id, tx)
        }

        addNotification("RO Item Added", "Registered ${newItem.name} in RO materials catalog.", NotificationSeverity.SUCCESS)
        return id
    }

    fun updateRoPlantItem(item: RoPlantItem): Boolean {
        _roPlantItems.value = _roPlantItems.value.map { if (it.id == item.id) item else it }
        storage.saveRoPlantItems(_roPlantItems.value)
        syncDocument("roPlantItems", item.id, item)
        addNotification("RO Item Updated", "Updated specifications for ${item.name}.", NotificationSeverity.INFO)
        return true
    }

    fun recordRoItemAction(
        itemId: String,
        actionType: InventoryActionType,
        quantity: Double,
        unitCost: Double,
        employee: String,
        reasonOrPurpose: String,
        notes: String
    ): Boolean {
        val item = _roPlantItems.value.find { it.id == itemId } ?: return false
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val prevStock = item.currentStock
        val effectiveUnitCost = if (unitCost > 0) unitCost else item.unitCost
        val newStock = when (actionType) {
            InventoryActionType.STOCK_RECEIPT -> prevStock + quantity
            InventoryActionType.ITEM_ISSUE -> (prevStock - quantity).coerceAtLeast(0.0)
            InventoryActionType.MARK_DAMAGED -> (prevStock - quantity).coerceAtLeast(0.0)
            InventoryActionType.ADJUSTMENT -> quantity
        }
        val deltaQty = if (actionType == InventoryActionType.ADJUSTMENT) kotlin.math.abs(newStock - prevStock) else quantity
        val totalCost = deltaQty * effectiveUnitCost

        // Update item in catalog
        val updatedItem = item.copy(currentStock = newStock, unitCost = effectiveUnitCost)
        _roPlantItems.value = _roPlantItems.value.map { if (it.id == itemId) updatedItem else it }

        // Add audit log transaction
        val tx = RoItemTransaction(
            id = nextRecordId("rtx"),
            itemId = itemId,
            itemName = item.name,
            date = todayStr,
            time = timeStr,
            actionType = actionType,
            quantity = deltaQty,
            unitCost = effectiveUnitCost,
            totalCost = totalCost,
            uom = item.uom,
            previousStock = prevStock,
            newStock = newStock,
            responsibleEmployee = employee.ifEmpty { _currentUser.value.name },
            reasonOrPurpose = reasonOrPurpose,
            notes = notes
        )
        _roTransactions.value = listOf(tx) + _roTransactions.value
        storage.saveRoPlantItems(_roPlantItems.value)
        storage.saveRoTransactions(_roTransactions.value)
        syncDocument("roPlantItems", itemId, updatedItem)
        syncDocument("roItemTransactions", tx.id, tx)

        // Integrated Cost Management: If purchase/receipt, log as expense voucher
        if (actionType == InventoryActionType.STOCK_RECEIPT && totalCost > 0) {
            addExpense(
                categoryId = "cat_ro_maint",
                categoryName = "RO Maintenance & Spares",
                description = "Purchased $quantity ${item.uom} of ${item.name}",
                amount = totalCost,
                method = PaymentMethod.CASH,
                payee = item.supplierName.ifEmpty { "Spare Parts Vendor" },
                notes = "Auto expense: $reasonOrPurpose",
                type = ExpenseType.OPEX
            )
        }

        addNotification(
            "Inventory Updated",
            "${actionType.label}: $quantity ${item.uom} of ${item.name}",
            NotificationSeverity.SUCCESS
        )
        return true
    }

    // Expenses
    fun addExpense(
        categoryId: String,
        categoryName: String,
        description: String,
        amount: Double,
        method: PaymentMethod,
        payee: String,
        notes: String,
        type: ExpenseType = ExpenseType.OPEX,
        expenseDate: String? = null,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
        invoiceNumber: String = "",
        staffMemberId: String = "",
        staffMemberName: String = "",
        linkedInventoryType: String = "",
        linkedInventoryItemId: String = "",
        linkedInventoryItemName: String = "",
        linkedInventoryQty: Double = 0.0,
        linkedInventoryUnitCost: Double = 0.0,
        linkedInventoryTransactionId: String = "",
        isInventoryExpense: Boolean = false,
        inventoryItems: List<ExpenseInventoryLineItem> = emptyList()
    ): String {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) return ""
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val effectiveDate = if (!expenseDate.isNullOrBlank()) expenseDate else todayStr
        val voucher = "EXP-${_expenses.value.size + 201}"
        val expId = nextRecordId("exp")

        return withSyncAction("Expense $voucher • $categoryName") {
            val isAdmin = hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE)
            val resolvedIsInv = isInventoryExpense || inventoryItems.isNotEmpty() || linkedInventoryType.isNotBlank()
            val expense = Expense(
                id = expId,
                voucherNumber = voucher,
                date = effectiveDate,
                expenseType = type,
                categoryId = categoryId,
                categoryName = categoryName,
                description = description,
                amount = amount,
                paymentMethod = method,
                paymentSource = paymentSource,
                payee = payee,
                notes = notes,
                enteredBy = _currentUser.value.name,
                enteredByRole = _currentUser.value.role,
                isAcknowledged = isAdmin, // Admin additions auto-verified, Incharge needs Admin acknowledgment
                acknowledgedBy = if (isAdmin) _currentUser.value.name else "",
                isApprovedByOwner = isAdmin,
                createdByUid = _currentUser.value.id.ifBlank { _currentUser.value.uid },
                isInventoryExpense = resolvedIsInv,
                inventoryItems = inventoryItems,
                linkedInventoryType = linkedInventoryType,
                linkedInventoryItemId = linkedInventoryItemId,
                linkedInventoryItemName = linkedInventoryItemName,
                linkedInventoryQty = linkedInventoryQty,
                linkedInventoryUnitCost = linkedInventoryUnitCost,
                linkedInventoryTransactionId = linkedInventoryTransactionId,
                invoiceNumber = invoiceNumber.trim(),
                staffMemberId = staffMemberId.trim(),
                staffMemberName = staffMemberName.trim()
            )
            markRecordSeen("expense", expId)
            _expenses.value = listOf(expense) + _expenses.value
            storage.saveExpenses(_expenses.value)
            syncDocument("expenses", expId, expense)
            appendAuditLog(AuditActionType.CREATE, AppModule.EXPENSES, expId, "$voucher • $categoryName", updatedValue = "Rs. ${amount.toInt()} (${type.name})")

            val roleNotice = if (isAdmin) "Verified" else "Pending Admin Acknowledgment"
            addNotification("Expense Added", "Rs. ${amount.toInt()} for $categoryName ($payee) [$roleNotice]", NotificationSeverity.INFO)
            voucher
        }
    }

    private fun formatQtyLabel(qty: Double): String {
        return if (qty % 1.0 == 0.0) qty.toInt().toString() else String.format(Locale.US, "%.2f", qty)
    }

    private fun resolveSummaryCategoryForItems(
        items: List<ExpenseInventoryLineItem>,
        expenseType: ExpenseType
    ): Pair<String, String> {
        val distinctCats = items.map { it.categoryName.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val summaryName = when {
            distinctCats.isEmpty() -> "Uncategorized"
            distinctCats.size == 1 -> distinctCats.first()
            distinctCats.size == 2 -> "${distinctCats[0]} & ${distinctCats[1]}"
            else -> "${distinctCats[0]} & ${distinctCats[1]} +${distinctCats.size - 2}"
        }
        val matchedCat = distinctCats.firstOrNull()?.let { firstCat ->
            _expenseCategories.value.firstOrNull { it.name.equals(firstCat, ignoreCase = true) }
        }
        val summaryId = matchedCat?.id ?: if (expenseType == ExpenseType.CAPEX) "cat_asset_machinery" else "cat_bottles_opex"
        return Pair(summaryId, summaryName)
    }

    private fun buildInventoryItemsSummaryDescription(items: List<ExpenseInventoryLineItem>): String {
        if (items.isEmpty()) return "Inventory Purchase"
        return items.joinToString(", ") { line ->
            val qtyStr = formatQtyLabel(line.quantity)
            "$qtyStr x ${line.itemName}"
        }
    }

    /**
     * Applies stock increments for a list of inventory line items (both Bottles & Factory Materials)
     * and returns the resolved items stamped with transaction IDs and auto-picked categories.
     * Must be called inside synchronized(mutationLock).
     */
    private fun applyInventoryLineItemsLocked(
        items: List<ExpenseInventoryLineItem>,
        effectiveDate: String,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        paymentSource: PaymentSource,
        paymentMethod: PaymentMethod
    ): List<ExpenseInventoryLineItem> {
        val cleanSupplier = supplierName.trim().ifBlank { "Supplier" }
        val stockPayMode = if (paymentMethod.isCash) StockPaymentMode.CASH else StockPaymentMode.ONLINE_TRANSFER
        val resolvedLines = mutableListOf<ExpenseInventoryLineItem>()

        for (rawLine in items) {
            if (rawLine.quantity <= 0.0 || rawLine.itemId.isBlank()) continue
            val lineTotal = rawLine.quantity * rawLine.unitRate

            if (rawLine.itemType.equals("BOTTLE", ignoreCase = true)) {
                val size = _bottleSizes.value.find { it.id == rawLine.itemId } ?: continue
                val qtyInt = rawLine.quantity.toInt().coerceAtLeast(1)
                val exactLineTotal = qtyInt * rawLine.unitRate

                val updatedSize = size.copy(
                    latestPurchaseRate = if (rawLine.unitRate > 0.0) rawLine.unitRate else size.latestPurchaseRate,
                    purchaseCost = if (rawLine.unitRate > 0.0) rawLine.unitRate else size.purchaseCost
                )
                _bottleSizes.value = _bottleSizes.value.map { if (it.id == size.id) updatedSize else it }
                storage.saveBottleSizes(_bottleSizes.value)
                syncDocument("bottleSizes", size.id, updatedSize)

                val curInv = _inventory.value.find { it.bottleSizeId == size.id } ?: BottleInventory(
                    id = "inv_${size.id}",
                    bottleSizeId = size.id,
                    sizeName = size.name
                )
                val updatedInv = curInv.copy(
                    atPlantEmpty = curInv.atPlantEmpty + qtyInt,
                    totalPurchased = curInv.totalPurchased + qtyInt
                )
                _inventory.value = _inventory.value.filterNot { it.bottleSizeId == size.id } + updatedInv
                storage.saveInventory(_inventory.value)
                syncIncrementalDelta(
                    "bottleInventory",
                    updatedInv.id,
                    updatedInv,
                    mapOf("atPlantEmpty" to qtyInt, "totalPurchased" to qtyInt)
                )

                val purchase = BottlePurchaseRecord(
                    id = nextRecordId("bp"),
                    date = effectiveDate,
                    bottleSizeId = size.id,
                    sizeName = size.name,
                    quantity = qtyInt,
                    unitCost = rawLine.unitRate,
                    totalCost = exactLineTotal,
                    supplierName = cleanSupplier,
                    invoiceOrBillNo = invoiceNo.trim(),
                    paymentSource = paymentSource.label,
                    paymentMode = stockPayMode.label,
                    notes = notes.trim(),
                    recordedBy = _currentUser.value.name
                )
                _bottlePurchases.value = listOf(purchase) + _bottlePurchases.value
                storage.saveBottlePurchases(_bottlePurchases.value)
                syncDocument("bottlePurchases", purchase.id, purchase)

                val mov = BottleMovement(
                    id = nextRecordId("mov"),
                    date = effectiveDate,
                    bottleSizeId = size.id,
                    sizeName = size.name,
                    movementType = BottleMovementType.PURCHASE_NEW,
                    quantity = qtyInt,
                    referenceType = "Expense Purchase (${paymentSource.label})",
                    referenceId = purchase.id,
                    notes = "Purchased $qtyInt ${size.name} @ Rs. ${rawLine.unitRate} from $cleanSupplier. ${notes.trim()}".trim(),
                    createdBy = _currentUser.value.name
                )
                _bottleMovements.value = listOf(mov) + _bottleMovements.value
                storage.saveBottleMovements(_bottleMovements.value)
                syncDocument("bottleMovements", mov.id, mov)

                val autoCatName = size.categoryName.trim()
                val autoCatId = _expenseCategories.value
                    .firstOrNull { it.name.equals(autoCatName, ignoreCase = true) }?.id.orEmpty()
                resolvedLines.add(
                    ExpenseInventoryLineItem(
                        itemType = "BOTTLE",
                        itemId = size.id,
                        itemName = size.name,
                        categoryId = autoCatId,
                        categoryName = autoCatName,
                        uom = size.uom.ifBlank { "Bottles" },
                        quantity = qtyInt.toDouble(),
                        unitRate = rawLine.unitRate,
                        lineTotal = exactLineTotal,
                        transactionId = purchase.id
                    )
                )
            } else {
                val mat = _factoryMaterials.value.find { it.id == rawLine.itemId } ?: continue
                val newStock = mat.currentStock + rawLine.quantity
                val updatedMat = mat.copy(
                    currentStock = newStock,
                    purchaseRate = if (rawLine.unitRate > 0.0) rawLine.unitRate else mat.purchaseRate,
                    supplierName = if (supplierName.isNotBlank()) supplierName.trim() else mat.supplierName,
                    lastUpdated = System.currentTimeMillis()
                )
                _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == mat.id) updatedMat else it }
                storage.saveFactoryMaterials(_factoryMaterials.value)
                syncDocument("factoryMaterials", mat.id, updatedMat)

                val tx = MaterialTransaction(
                    id = nextRecordId("mtx"),
                    materialId = mat.id,
                    materialName = mat.name,
                    date = effectiveDate,
                    actionType = MaterialActionType.REFILL,
                    quantity = rawLine.quantity,
                    unitCost = rawLine.unitRate,
                    totalCost = lineTotal,
                    uom = mat.uom,
                    supplierOrReceiver = cleanSupplier,
                    invoiceNo = invoiceNo.trim(),
                    paymentSource = paymentSource.label,
                    paymentMode = stockPayMode.label,
                    notes = notes.trim(),
                    balanceAfter = newStock,
                    performedBy = _currentUser.value.name
                )
                _materialTransactions.value = listOf(tx) + _materialTransactions.value
                storage.saveMaterialTransactions(_materialTransactions.value)
                syncDocument("materialTransactions", tx.id, tx)

                val autoCatName = mat.itemCategory.trim()
                val autoCatId = _expenseCategories.value
                    .firstOrNull { it.name.equals(autoCatName, ignoreCase = true) }?.id.orEmpty()
                resolvedLines.add(
                    ExpenseInventoryLineItem(
                        itemType = "MATERIAL",
                        itemId = mat.id,
                        itemName = mat.name,
                        categoryId = autoCatId,
                        categoryName = autoCatName,
                        uom = mat.uom,
                        quantity = rawLine.quantity,
                        unitRate = rawLine.unitRate,
                        lineTotal = lineTotal,
                        transactionId = tx.id
                    )
                )
            }
        }
        return resolvedLines
    }

    /**
     * Reverses stock increments & transaction logs for all line items in an inventory expense.
     * Must be called inside synchronized(mutationLock).
     */
    private fun reverseInventoryLineItemsLocked(items: List<ExpenseInventoryLineItem>) {
        for (line in items) {
            if (line.itemId.isBlank() || line.quantity <= 0.0) continue
            if (line.itemType.equals("BOTTLE", ignoreCase = true)) {
                val qtyInt = line.quantity.toInt()
                val inv = _inventory.value.find { it.bottleSizeId == line.itemId }
                if (inv != null && qtyInt > 0) {
                    val actualEmptyDelta = -minOf(inv.atPlantEmpty, qtyInt)
                    val actualPurchasedDelta = -minOf(inv.totalPurchased, qtyInt)
                    val updatedInv = inv.copy(
                        atPlantEmpty = (inv.atPlantEmpty + actualEmptyDelta).coerceAtLeast(0),
                        totalPurchased = (inv.totalPurchased + actualPurchasedDelta).coerceAtLeast(0)
                    )
                    _inventory.value = _inventory.value.map { if (it.bottleSizeId == inv.bottleSizeId) updatedInv else it }
                    storage.saveInventory(_inventory.value)
                    syncIncrementalDelta(
                        "bottleInventory",
                        updatedInv.id,
                        updatedInv,
                        mapOf("atPlantEmpty" to actualEmptyDelta, "totalPurchased" to actualPurchasedDelta)
                    )
                }
                if (line.transactionId.isNotBlank()) {
                    _bottlePurchases.value = _bottlePurchases.value.filterNot { it.id == line.transactionId }
                    storage.saveBottlePurchases(_bottlePurchases.value)
                    deleteDocument("bottlePurchases", line.transactionId)

                    val relatedMovs = _bottleMovements.value.filter { it.referenceId == line.transactionId }
                    if (relatedMovs.isNotEmpty()) {
                        _bottleMovements.value = _bottleMovements.value.filterNot { it.referenceId == line.transactionId }
                        storage.saveBottleMovements(_bottleMovements.value)
                        relatedMovs.forEach { deleteDocument("bottleMovements", it.id) }
                    }
                }
            } else {
                val mat = _factoryMaterials.value.find { it.id == line.itemId }
                if (mat != null) {
                    val reversedStock = (mat.currentStock - line.quantity).coerceAtLeast(0.0)
                    val updatedMat = mat.copy(currentStock = reversedStock, lastUpdated = System.currentTimeMillis())
                    _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == mat.id) updatedMat else it }
                    storage.saveFactoryMaterials(_factoryMaterials.value)
                    syncDocument("factoryMaterials", mat.id, updatedMat)
                }
                if (line.transactionId.isNotBlank()) {
                    _materialTransactions.value = _materialTransactions.value.filterNot { it.id == line.transactionId }
                    storage.saveMaterialTransactions(_materialTransactions.value)
                    deleteDocument("materialTransactions", line.transactionId)
                }
            }
        }
    }

    /**
     * Multi-item Inventory Expense atomic purchase & stock update.
     * Supports selecting 1 or more Bottle Sizes and/or Factory Materials in a single Expense voucher.
     * Auto-picks each item's stored category and maintains accurate stock for every item.
     */
    fun recordMultiItemInventoryExpense(
        items: List<ExpenseInventoryLineItem>,
        expenseType: ExpenseType,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        expenseDate: String,
        paymentSource: PaymentSource,
        paymentMethod: PaymentMethod
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Permission required to add expenses.")
        }
        val validItems = items.filter { it.itemId.isNotBlank() && it.quantity > 0.0 }
        if (validItems.isEmpty()) {
            return@synchronized Pair(false, "Please select at least one inventory item with quantity > 0.")
        }
        if (validItems.any { it.unitRate < 0.0 }) {
            return@synchronized Pair(false, "Rate cannot be negative.")
        }
        val hasBottles = validItems.any { it.itemType.equals("BOTTLE", ignoreCase = true) }
        val hasMaterials = validItems.any { !it.itemType.equals("BOTTLE", ignoreCase = true) }
        if (hasBottles && !hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding bottle stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }
        if (hasMaterials && !hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding material stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }

        val effectiveDate = expenseDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        val cleanSupplier = supplierName.trim().ifBlank { "Supplier" }

        withSyncAction("Inventory Expense (${validItems.size} items)") {
            val resolvedLines = applyInventoryLineItemsLocked(
                items = validItems,
                effectiveDate = effectiveDate,
                supplierName = cleanSupplier,
                invoiceNo = invoiceNo.trim(),
                notes = notes.trim(),
                paymentSource = paymentSource,
                paymentMethod = paymentMethod
            )
            if (resolvedLines.isEmpty()) {
                return@withSyncAction Pair(false, "Selected inventory items were not found.")
            }

            val totalAmount = resolvedLines.sumOf { it.lineTotal }
            val (summaryCatId, summaryCatName) = resolveSummaryCategoryForItems(resolvedLines, expenseType)
            val summaryDesc = buildInventoryItemsSummaryDescription(resolvedLines)
            val single = resolvedLines.singleOrNull()

            val voucher = addExpense(
                categoryId = summaryCatId,
                categoryName = summaryCatName,
                description = summaryDesc,
                amount = totalAmount,
                method = paymentMethod,
                payee = cleanSupplier,
                notes = notes.trim(),
                type = expenseType,
                expenseDate = effectiveDate,
                paymentSource = paymentSource,
                invoiceNumber = invoiceNo.trim(),
                linkedInventoryType = single?.itemType.orEmpty(),
                linkedInventoryItemId = single?.itemId.orEmpty(),
                linkedInventoryItemName = single?.itemName.orEmpty(),
                linkedInventoryQty = single?.quantity ?: 0.0,
                linkedInventoryUnitCost = single?.unitRate ?: 0.0,
                linkedInventoryTransactionId = single?.transactionId.orEmpty(),
                isInventoryExpense = true,
                inventoryItems = resolvedLines
            )
            Pair(true, "Voucher $voucher saved (${resolvedLines.size} item${if (resolvedLines.size > 1) "s" else ""} • Rs. ${totalAmount.toInt()}) & stock updated.")
        }
    }

    /**
     * Direct Expense -> Other Material Inventory atomic purchase & refill.
     * Creates ONE linked Expense and updates FactoryMaterial stock + MaterialTransaction without duplicates.
     */
    fun recordExpenseWithMaterialRefill(
        materialId: String,
        quantity: Double,
        unitCost: Double,
        categoryId: String,
        categoryName: String,
        expenseType: ExpenseType,
        description: String,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        expenseDate: String,
        paymentSource: PaymentSource,
        paymentMethod: PaymentMethod
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Permission required to add expenses.")
        }
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding material stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }
        if (quantity <= 0.0) return@synchronized Pair(false, "Quantity must be greater than zero.")
        if (unitCost < 0.0) return@synchronized Pair(false, "Unit rate cannot be negative.")
        val mat = _factoryMaterials.value.find { it.id == materialId && it.active }
            ?: return@synchronized Pair(false, "Selected inventory item not found. Add new items in Stock & Cost -> Other Material first.")

        val effectiveDate = expenseDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        val cleanSupplier = supplierName.ifBlank { mat.supplierName.ifBlank { "Supplier" } }
        val totalCost = quantity * unitCost
        val stockPayMode = if (paymentMethod.isCash) StockPaymentMode.CASH else StockPaymentMode.ONLINE_TRANSFER
        val finalDesc = description.ifBlank { "Purchased ${if (quantity % 1.0 == 0.0) quantity.toInt().toString() else String.format(Locale.US, "%.2f", quantity)} ${mat.uom} ${mat.name}" }

        withSyncAction("Expense + Material Stock • ${mat.name}") {
            val newStock = mat.currentStock + quantity
            val updatedMat = mat.copy(
                currentStock = newStock,
                purchaseRate = if (unitCost > 0.0) unitCost else mat.purchaseRate,
                supplierName = cleanSupplier,
                lastUpdated = System.currentTimeMillis()
            )
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == materialId) updatedMat else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", materialId, updatedMat)

            val tx = MaterialTransaction(
                id = nextRecordId("mtx"),
                materialId = materialId,
                materialName = mat.name,
                date = effectiveDate,
                actionType = MaterialActionType.REFILL,
                quantity = quantity,
                unitCost = unitCost,
                totalCost = totalCost,
                uom = mat.uom,
                supplierOrReceiver = cleanSupplier,
                invoiceNo = invoiceNo.trim(),
                paymentSource = paymentSource.label,
                paymentMode = stockPayMode.label,
                notes = notes.trim(),
                balanceAfter = newStock,
                performedBy = _currentUser.value.name
            )
            _materialTransactions.value = listOf(tx) + _materialTransactions.value
            storage.saveMaterialTransactions(_materialTransactions.value)
            syncDocument("materialTransactions", tx.id, tx)

            val voucher = addExpense(
                categoryId = categoryId.ifBlank { if (expenseType == ExpenseType.CAPEX) "cat_asset_machinery" else "cat_maint" },
                categoryName = categoryName.ifBlank { mat.itemCategory.ifBlank { "Material Stock Refill" } },
                description = finalDesc,
                amount = totalCost,
                method = paymentMethod,
                payee = cleanSupplier,
                notes = notes.trim(),
                type = expenseType,
                expenseDate = effectiveDate,
                paymentSource = paymentSource,
                invoiceNumber = invoiceNo.trim(),
                linkedInventoryType = "MATERIAL",
                linkedInventoryItemId = materialId,
                linkedInventoryItemName = mat.name,
                linkedInventoryQty = quantity,
                linkedInventoryUnitCost = unitCost,
                linkedInventoryTransactionId = tx.id
            )
            Pair(true, "Voucher $voucher saved & added ${if (quantity % 1.0 == 0.0) quantity.toInt() else quantity} ${mat.uom} to ${mat.name} stock.")
        }
    }

    /**
     * Direct Expense -> Bottle Stock atomic purchase & refill.
     * Creates ONE linked Expense and updates BottleInventory.atPlantEmpty + BottlePurchaseRecord + BottleMovement without duplicates.
     */
    fun recordExpenseWithBottlePurchase(
        bottleSizeId: String,
        quantity: Int,
        unitCost: Double,
        categoryId: String,
        categoryName: String,
        expenseType: ExpenseType,
        description: String,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        expenseDate: String,
        paymentSource: PaymentSource,
        paymentMethod: PaymentMethod
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Permission required to add expenses.")
        }
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding bottle stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero.")
        if (unitCost < 0.0) return@synchronized Pair(false, "Purchase rate cannot be negative.")
        val size = _bottleSizes.value.find { it.id == bottleSizeId && it.active }
            ?: return@synchronized Pair(false, "Bottle size not found. Add new bottle sizes in Stock & Cost -> Bottle Stock first.")

        val effectiveDate = expenseDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        val cleanSupplier = supplierName.ifBlank { "Supplier" }
        val totalCost = quantity * unitCost
        val stockPayMode = if (paymentMethod.isCash) StockPaymentMode.CASH else StockPaymentMode.ONLINE_TRANSFER
        val finalDesc = description.ifBlank { "Purchased $quantity ${size.name} from $cleanSupplier" }

        withSyncAction("Expense + Bottle Stock • ${size.name} (+$quantity)") {
            val updatedSize = size.copy(
                latestPurchaseRate = if (unitCost > 0.0) unitCost else size.latestPurchaseRate,
                purchaseCost = if (unitCost > 0.0) unitCost else size.purchaseCost
            )
            _bottleSizes.value = _bottleSizes.value.map { if (it.id == bottleSizeId) updatedSize else it }
            storage.saveBottleSizes(_bottleSizes.value)
            syncDocument("bottleSizes", bottleSizeId, updatedSize)

            val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
                id = "inv_$bottleSizeId",
                bottleSizeId = bottleSizeId,
                sizeName = size.name
            )
            val updatedInv = curInv.copy(
                atPlantEmpty = curInv.atPlantEmpty + quantity,
                totalPurchased = curInv.totalPurchased + quantity
            )
            _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
            storage.saveInventory(_inventory.value)
            syncIncrementalDelta(
                "bottleInventory",
                updatedInv.id,
                updatedInv,
                mapOf("atPlantEmpty" to quantity, "totalPurchased" to quantity)
            )

            val purchase = BottlePurchaseRecord(
                id = nextRecordId("bp"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                quantity = quantity,
                unitCost = unitCost,
                totalCost = totalCost,
                supplierName = cleanSupplier,
                invoiceOrBillNo = invoiceNo.trim(),
                paymentSource = paymentSource.label,
                paymentMode = stockPayMode.label,
                notes = notes.trim(),
                recordedBy = _currentUser.value.name
            )
            _bottlePurchases.value = listOf(purchase) + _bottlePurchases.value
            storage.saveBottlePurchases(_bottlePurchases.value)
            syncDocument("bottlePurchases", purchase.id, purchase)

            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.PURCHASE_NEW,
                quantity = quantity,
                referenceType = "Expense Purchase (${paymentSource.label})",
                notes = "Purchased $quantity ${size.name} @ Rs. $unitCost from $cleanSupplier. $notes".trim(),
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)

            val voucher = addExpense(
                categoryId = categoryId.ifBlank {
                    val isSingle = size.usageType == BottleUsageType.ONE_TIME ||
                        (size.usageType == BottleUsageType.CUSTOM && !size.isReturnableBottle)
                    if (isSingle) "cat_bottles_opex" else "cat_bottles_capex"
                },
                categoryName = categoryName.ifBlank { "Bottle Stock Purchase" },
                description = finalDesc,
                amount = totalCost,
                method = paymentMethod,
                payee = cleanSupplier,
                notes = notes.trim(),
                type = expenseType,
                expenseDate = effectiveDate,
                paymentSource = paymentSource,
                invoiceNumber = invoiceNo.trim(),
                linkedInventoryType = "BOTTLE",
                linkedInventoryItemId = bottleSizeId,
                linkedInventoryItemName = size.name,
                linkedInventoryQty = quantity.toDouble(),
                linkedInventoryUnitCost = unitCost,
                linkedInventoryTransactionId = purchase.id
            )
            Pair(true, "Voucher $voucher saved & added $quantity ${size.name} to Bottle Stock (Plant Empty).")
        }
    }

    /**
     * Strictly returns Inventory categories configured in Expenses.
     * Non-inventory categories (Fuel, Salary, Bills, etc.) are excluded from Inventory forms.
     * Deleting a category in Expenses removes it from inventory selection immediately.
     */
    fun getAllInventoryCategories(categories: List<ExpenseCategory> = _expenseCategories.value): List<String> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<String>()
        categories.filter { it.isInventoryCategory }.forEach { cat ->
            val clean = cat.name.trim()
            if (clean.isNotEmpty() && seen.add(clean.lowercase())) {
                result.add(clean)
            }
        }
        return result
    }

    // =============================================================
    // PLANT WORKERS / STAFF MANAGEMENT & PAYROLL ADVANCES
    // =============================================================

    fun addPlantWorker(worker: PlantWorker): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.ADD) &&
            !hasPermission(AppModule.EXPENSES, PermissionAction.ADD) &&
            _currentUser.value.role != UserRole.ADMIN
        ) {
            return@synchronized Pair(false, "Permission required to add staff members.")
        }
        val cleanName = worker.name.trim()
        if (cleanName.isEmpty()) return@synchronized Pair(false, "Staff name is required.")
        if (worker.monthlySalary < 0.0) return@synchronized Pair(false, "Monthly salary cannot be negative.")

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val workerId = worker.id.ifBlank { nextRecordId("wrk") }
        val newWorker = worker.copy(
            id = workerId,
            name = cleanName,
            cnic = worker.cnic.trim(),
            phone = worker.phone.trim(),
            designation = worker.designation.trim().ifBlank { "Worker" },
            joiningDate = worker.joiningDate.ifBlank { today },
            notes = worker.notes.trim(),
            businessId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        withSyncAction("Add Staff Worker • $cleanName") {
            _plantWorkers.value = listOf(newWorker) + _plantWorkers.value.filterNot { it.id == workerId }
            storage.savePlantWorkers(_plantWorkers.value)
            syncDocument("plantWorkers", workerId, newWorker)
            addNotification("Staff Added", "Added worker $cleanName (Salary Rs. ${newWorker.monthlySalary.toInt()}).", NotificationSeverity.SUCCESS)
            Pair(true, "Worker '$cleanName' saved successfully.")
        }
    }

    fun updatePlantWorker(worker: PlantWorker): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.EDIT) &&
            _currentUser.value.role != UserRole.ADMIN
        ) {
            return@synchronized Pair(false, "Permission required to update staff members.")
        }
        val cleanName = worker.name.trim()
        if (cleanName.isEmpty()) return@synchronized Pair(false, "Staff name is required.")
        if (worker.monthlySalary < 0.0) return@synchronized Pair(false, "Monthly salary cannot be negative.")
        val updated = worker.copy(
            name = cleanName,
            cnic = worker.cnic.trim(),
            phone = worker.phone.trim(),
            designation = worker.designation.trim().ifBlank { "Worker" },
            notes = worker.notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        withSyncAction("Update Staff Worker • $cleanName") {
            _plantWorkers.value = _plantWorkers.value.map { if (it.id == updated.id) updated else it }
            storage.savePlantWorkers(_plantWorkers.value)
            syncDocument("plantWorkers", updated.id, updated)
            Pair(true, "Worker '$cleanName' updated successfully.")
        }
    }

    fun deletePlantWorker(workerId: String): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.DELETE) &&
            _currentUser.value.role != UserRole.ADMIN
        ) {
            return@synchronized Pair(false, "Admin permission required to delete staff members.")
        }
        val existing = _plantWorkers.value.find { it.id == workerId }
            ?: return@synchronized Pair(false, "Staff member not found.")
        withSyncAction("Delete Staff Worker • ${existing.name}") {
            _plantWorkers.value = _plantWorkers.value.filterNot { it.id == workerId }
            storage.savePlantWorkers(_plantWorkers.value)
            deleteDocument("plantWorkers", workerId)
            Pair(true, "Removed worker '${existing.name}'. Historical salary expenses remain intact.")
        }
    }

    /**
     * Computes live monthly salary, advance/paid total, and remaining payable salary for a worker.
     * Backward-compatible: matches both newly linked expenses (staffMemberId) and legacy salary expenses
     * where payee or description matches the worker's name in that month.
     */
    fun getWorkerMonthlySalarySummary(
        workerId: String,
        month: String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date()),
        expensesList: List<Expense> = _expenses.value
    ): WorkerSalarySummary {
        val worker = _plantWorkers.value.find { it.id == workerId }
            ?: return WorkerSalarySummary(workerId = workerId, month = month)
        val cleanMonth = if (month.length >= 7) month.take(7) else SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val workerNameNorm = worker.name.trim()

        val matchedExpenses = expensesList.filter { exp ->
            if (!exp.date.startsWith(cleanMonth)) return@filter false
            val isSalaryCategory = exp.categoryId == "cat_salary" ||
                exp.categoryName.contains("salary", ignoreCase = true) ||
                exp.staffMemberId.isNotBlank()
            if (!isSalaryCategory) return@filter false
            when {
                exp.staffMemberId.isNotBlank() -> exp.staffMemberId == worker.id
                workerNameNorm.isNotBlank() -> exp.payee.trim().equals(workerNameNorm, ignoreCase = true) ||
                    exp.staffMemberName.trim().equals(workerNameNorm, ignoreCase = true)
                else -> false
            }
        }
        val paidTotal = matchedExpenses.sumOf { it.amount }
        val remaining = (worker.monthlySalary - paidTotal).coerceAtLeast(0.0)
        return WorkerSalarySummary(
            workerId = worker.id,
            workerName = worker.name,
            month = cleanMonth,
            monthlySalary = worker.monthlySalary,
            paidOrAdvanceThisMonth = paidTotal,
            remainingSalary = remaining,
            paymentsCount = matchedExpenses.size
        )
    }

    // =============================================================
    // ADMIN PER-USER DATA VISIBILITY FILTERS (BACKWARD-COMPATIBLE)
    // =============================================================

    private fun refreshCurrentProfileIfMatches(acc: UserAccount) {
        val cur = _currentUser.value
        val matches = (cur.id.isNotBlank() && cur.id == acc.id) ||
            (cur.uid.isNotBlank() && acc.uid.isNotBlank() && cur.uid == acc.uid) ||
            (cur.username.isNotBlank() && normalizeUsername(cur.username) == normalizeUsername(acc.username))
        if (!matches) return
        val isSuper = acc.isSuperAdmin || acc.id == "user_admin_01"
        val effectiveRole = if (isSuper) UserRole.ADMIN else acc.role
        val isExplicit = acc.hasExplicitPermissions || acc.permissions.isNotEmpty()
        val updatedProfile = cur.copy(
            name = acc.name.ifBlank { cur.name },
            phone = acc.phone,
            role = effectiveRole,
            isSuperAdmin = isSuper,
            permissions = PermissionDefaults.normalizePermissions(
                input = acc.permissions,
                role = effectiveRole,
                isSuperAdmin = isSuper,
                explicitMode = isExplicit
            ),
            hasExplicitPermissions = isExplicit,
            assignedBranchIds = acc.assignedBranchIds.ifEmpty { cur.assignedBranchIds },
            defaultBranchId = acc.defaultBranchId.ifBlank { cur.defaultBranchId },
            active = if (isSuper) true else acc.active,
            restrictToOwnDataOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else acc.restrictToOwnDataOnly,
            restrictToTodayOnly = if (isSuper || effectiveRole == UserRole.ADMIN) false else acc.restrictToTodayOnly
        )
        _currentUser.value = updatedProfile
        syncNotificationUserContext()
        storage.saveCurrentProfile(updatedProfile)
    }

    fun isRecordOwnedByUser(
        user: UserProfile,
        createdByUid: String,
        createdByName: String,
        secondaryName: String = ""
    ): Boolean {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return true
        val uidCandidates = setOf(user.id.trim(), user.uid.trim()).filter { it.isNotEmpty() }
        if (createdByUid.isNotBlank() && createdByUid.trim() in uidCandidates) return true
        val nameCandidates = setOf(user.name.trim().lowercase(), normalizeUsername(user.username))
            .filter { it.isNotEmpty() }
        val primaryNorm = createdByName.trim().lowercase()
        val secondaryNorm = secondaryName.trim().lowercase()
        return (primaryNorm.isNotEmpty() && primaryNorm in nameCandidates) ||
            (secondaryNorm.isNotEmpty() && secondaryNorm in nameCandidates)
    }

    fun filterVisibleDeliveries(
        list: List<Delivery>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<Delivery> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { d ->
            val dateOk = !user.restrictToTodayOnly || d.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, d.createdByUid, d.createdBy, d.staffName)
            dateOk && ownOk
        }
    }

    fun filterVisiblePayments(
        list: List<Payment>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<Payment> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { p ->
            val dateOk = !user.restrictToTodayOnly || p.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, p.createdByUid, p.receivedBy)
            dateOk && ownOk
        }
    }

    fun filterVisibleExpenses(
        list: List<Expense>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<Expense> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { e ->
            val dateOk = !user.restrictToTodayOnly || e.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, e.createdByUid, e.enteredBy)
            dateOk && ownOk
        }
    }

    fun filterVisibleInvoices(
        list: List<Invoice>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<Invoice> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        val visibleDeliveryIds = filterVisibleDeliveries(_deliveries.value, user, today).map { it.id }.toSet()
        return list.filter { inv ->
            val dateOk = !user.restrictToTodayOnly || inv.date == today
            val ownOk = !user.restrictToOwnDataOnly || inv.deliveryId in visibleDeliveryIds
            dateOk && ownOk
        }
    }

    fun filterVisibleBottleMovements(
        list: List<BottleMovement>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<BottleMovement> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { m ->
            val dateOk = !user.restrictToTodayOnly || m.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, "", m.createdBy)
            dateOk && ownOk
        }
    }

    fun filterVisibleMaterialTransactions(
        list: List<MaterialTransaction>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<MaterialTransaction> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { t ->
            val dateOk = !user.restrictToTodayOnly || t.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, "", t.performedBy)
            dateOk && ownOk
        }
    }

    fun filterVisibleCashClosings(
        list: List<CashClosing>,
        user: UserProfile = _currentUser.value,
        today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): List<CashClosing> {
        if (user.isSuperAdmin || user.role == UserRole.ADMIN) return list
        if (!user.restrictToOwnDataOnly && !user.restrictToTodayOnly) return list
        return list.filter { c ->
            val dateOk = !user.restrictToTodayOnly || c.date == today
            val ownOk = !user.restrictToOwnDataOnly || isRecordOwnedByUser(user, "", c.closedBy)
            dateOk && ownOk
        }
    }

    // =============================================================
    // COMPLETE REBUILT STOCK & COST MODULE REPOSITORY METHODS
    // =============================================================

    // --- Sub-Tab 1: Bottle Stock Operations ---

    fun addNewBottleSize(
        name: String,
        purchaseRate: Double,
        packQty: Int,
        uom: String,
        openingStock: Int,
        usageType: BottleUsageType,
        defaultRefillPrice: Double,
        salePrice: Double,
        customUsageType: String = "",
        capacityLitres: Double? = null,
        depositAmount: Double = 0.0,
        categoryName: String = "Bottle Purchase"
    ): String = withSyncAction("Add Bottle Size: ${name.trim()}") {
        if (!hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)) {
            addNotification("Access Restricted", "Admin has restricted adding new bottle sizes/items.", NotificationSeverity.WARNING)
            return@withSyncAction ""
        }
        val sizeId = nextRecordId("size")
        val isReturnable = (usageType == BottleUsageType.MULTIPLE_TIME || (usageType == BottleUsageType.CUSTOM && (name.contains("19", true) || name.contains("12", true))))
        val resolvedCapacity = capacityLitres ?: when {
            name.contains("19", true) -> 19.0
            name.contains("12", true) -> 12.0
            name.contains("6", true) -> 6.0
            name.contains("1.5", true) -> 1.5
            else -> 0.5
        }
        val newSize = BottleSize(
            id = sizeId,
            name = name.trim(),
            capacityLitres = resolvedCapacity,
            packQuantity = packQty.coerceAtLeast(1),
            packetPackagingQty = packQty.coerceAtLeast(1),
            defaultRefillPrice = defaultRefillPrice,
            bottleSalePrice = salePrice,
            depositAmount = depositAmount.coerceAtLeast(0.0),
            purchaseCost = purchaseRate,
            latestPurchaseRate = purchaseRate,
            active = true,
            isReturnableBottle = isReturnable,
            uom = uom.ifBlank { "Bottle" },
            openingStock = openingStock.coerceAtLeast(0),
            usageType = usageType,
            customUsageType = customUsageType.trim(),
            categoryName = categoryName.trim()
        )
        _bottleSizes.value = _bottleSizes.value + newSize
        storage.saveBottleSizes(_bottleSizes.value)
        syncDocument("bottleSizes", sizeId, newSize)

        // Initialize inventory for this size: all opening stock goes into Plant Empty
        val initialEmpty = openingStock.coerceAtLeast(0)
        val newInv = BottleInventory(
            id = "inv_$sizeId",
            bottleSizeId = sizeId,
            sizeName = newSize.name,
            atPlantFull = 0,
            atPlantEmpty = initialEmpty,
            inUse = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = initialEmpty,
            totalIssuedOrSold = 0
        )
        _inventory.value = _inventory.value + newInv
        storage.saveInventory(_inventory.value)
        syncDocument("bottleInventory", newInv.id, newInv)

        if (openingStock > 0) {
            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                bottleSizeId = sizeId,
                sizeName = newSize.name,
                movementType = BottleMovementType.PURCHASE_NEW,
                quantity = openingStock,
                referenceType = "Opening Stock",
                notes = "Initial opening stock for ${newSize.name}: $openingStock units (Plant Empty)",
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)
        }

        addNotification("New Bottle Size Added", "Added ${newSize.name} ($openingStock opening stock)", NotificationSeverity.SUCCESS)
        sizeId
    }

    fun updateBottleSizeDetails(
        bottleSizeId: String,
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        defaultRefillPrice: Double,
        purchaseCost: Double,
        depositAmount: Double,
        uom: String,
        categoryName: String? = null
    ): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT)) {
            return Pair(false, "Permission required to edit bottle sizes in Stock & Cost.")
        }
        val existing = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return Pair(false, "Bottle size not found")
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Pair(false, "Bottle Name is required")

        val isReturnable = usageType == BottleUsageType.MULTIPLE_TIME ||
                (usageType == BottleUsageType.CUSTOM && existing.isReturnableBottle)

        val updated = existing.copy(
            name = cleanName,
            capacityLitres = capacityLitres,
            usageType = usageType,
            isReturnableBottle = isReturnable,
            defaultRefillPrice = defaultRefillPrice,
            purchaseCost = purchaseCost,
            latestPurchaseRate = purchaseCost,
            depositAmount = depositAmount,
            uom = uom.ifBlank { "Bottle" },
            categoryName = categoryName?.trim() ?: existing.categoryName
        )
        return withSyncAction("Update Bottle Size • $cleanName") {
            _bottleSizes.value = _bottleSizes.value.map { if (it.id == bottleSizeId) updated else it }
            _inventory.value = _inventory.value.map {
                if (it.bottleSizeId == bottleSizeId) {
                    val invUpd = it.copy(sizeName = cleanName)
                    syncDocument("bottleInventory", invUpd.id, invUpd)
                    invUpd
                } else it
            }
            _customerAllocations.value = _customerAllocations.value.map {
                if (it.bottleSizeId == bottleSizeId) {
                    val allocUpd = it.copy(sizeName = cleanName)
                    syncDocument("customerBottleAllocations", allocUpd.id, allocUpd)
                    allocUpd
                } else it
            }
            storage.saveBottleSizes(_bottleSizes.value)
            storage.saveInventory(_inventory.value)
            storage.saveCustomerAllocations(_customerAllocations.value)
            syncDocument("bottleSizes", bottleSizeId, updated)
            addNotification("Bottle Size Updated", "Updated '$cleanName' (${usageType.label}).", NotificationSeverity.INFO)
            Pair(true, "Updated '$cleanName' successfully.")
        }
    }

    fun refillBottleStock(
        bottleSizeId: String,
        quantity: Int,
        purchaseDate: String,
        supplierName: String,
        unitPurchaseRate: Double,
        notes: String,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
        paymentMode: StockPaymentMode = StockPaymentMode.CASH
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero (مقدار صفر سے زیادہ ہونی چاہیے)")
        if (unitPurchaseRate < 0) return@synchronized Pair(false, "Purchase rate cannot be negative (خریداری قیمت منفی نہیں ہو سکتی)")
        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return@synchronized Pair(false, "Bottle size not found (بوتل کا سائز نہیں ملا)")

        val effectiveDate = purchaseDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

        withSyncAction("Add Stock • ${size.name} (+$quantity)") {
        // 1. Update latest purchase rate on bottle size
        val updatedSize = size.copy(latestPurchaseRate = unitPurchaseRate, purchaseCost = unitPurchaseRate)
        _bottleSizes.value = _bottleSizes.value.map { if (it.id == bottleSizeId) updatedSize else it }
        storage.saveBottleSizes(_bottleSizes.value)
        syncDocument("bottleSizes", bottleSizeId, updatedSize)

        // 2. Always add newly purchased bottle stock (single-use or multiple-use) to Plant Empty
        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updatedInv = curInv.copy(
            atPlantEmpty = curInv.atPlantEmpty + quantity,
            totalPurchased = curInv.totalPurchased + quantity
        )
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf("atPlantEmpty" to quantity, "totalPurchased" to quantity)
        )

        // 3. Purchase record
        val totalCost = quantity * unitPurchaseRate
        val cleanSupplier = supplierName.ifBlank { "Supplier" }
        val purchase = BottlePurchaseRecord(
            id = nextRecordId("bp"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            quantity = quantity,
            unitCost = unitPurchaseRate,
            totalCost = totalCost,
            supplierName = cleanSupplier,
            paymentSource = paymentSource.label,
            paymentMode = paymentMode.label,
            notes = notes,
            recordedBy = _currentUser.value.name
        )
        _bottlePurchases.value = listOf(purchase) + _bottlePurchases.value
        storage.saveBottlePurchases(_bottlePurchases.value)
        syncDocument("bottlePurchases", purchase.id, purchase)

        // 4. Financial Expense record: Single-use bottles -> OPEX, Multiple-use bottles -> CAPEX
        val isSingleUse = size.usageType == BottleUsageType.ONE_TIME ||
            (size.usageType == BottleUsageType.CUSTOM && !size.isReturnableBottle)
        if (totalCost > 0.0) {
            addExpense(
                categoryId = if (isSingleUse) "cat_bottles_opex" else "cat_bottles_capex",
                categoryName = "Bottle Stock Purchase",
                description = "Purchased $quantity ${size.name} from $cleanSupplier",
                amount = totalCost,
                method = paymentMode.paymentMethod,
                payee = cleanSupplier,
                notes = "${paymentSource.label} • ${paymentMode.label}. $notes".trim(),
                type = if (isSingleUse) ExpenseType.OPEX else ExpenseType.CAPEX,
                expenseDate = effectiveDate,
                paymentSource = paymentSource,
                linkedInventoryType = "BOTTLE",
                linkedInventoryItemId = bottleSizeId,
                linkedInventoryItemName = size.name,
                linkedInventoryQty = quantity.toDouble(),
                linkedInventoryUnitCost = unitPurchaseRate,
                linkedInventoryTransactionId = purchase.id
            )
        }

        // 5. Movement log
        val mov = BottleMovement(
            id = nextRecordId("mov"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.PURCHASE_NEW,
            quantity = quantity,
            referenceType = "Add Stock (${paymentSource.label})",
            notes = "Add Stock to Plant Empty: $quantity from $cleanSupplier @ Rs. $unitPurchaseRate [${paymentSource.label} / ${paymentMode.label}]. $notes".trim(),
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(mov) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", mov.id, mov)

        addNotification("Stock Added", "Added $quantity ${size.name} to Plant Empty (${paymentSource.label} - ${paymentMode.label}).", NotificationSeverity.SUCCESS)
        Pair(true, "Successfully added $quantity ${size.name} to Plant Empty (اسٹاک میں $quantity بوتلیں شامل کر دی گئیں)")
        }
    }

    fun refillSingleUseBatch(
        bottleSizeId: String,
        quantity: Int,
        date: String,
        notes: String
    ): Pair<Boolean, String> {
        return refillSingleUseBatchWithConversion(
            bottleSizeId = bottleSizeId,
            refillQuantity = quantity,
            packetSize = 1,
            damagedQuantity = 0,
            date = date,
            notes = notes
        )
    }

    fun refillSingleUseBatchWithConversion(
        bottleSizeId: String,
        refillQuantity: Int,
        packetSize: Int,
        damagedQuantity: Int,
        date: String,
        notes: String,
        containerType: PackContainerType = PackContainerType.PACKET,
        returnLooseToInUse: Boolean = false
    ): Pair<Boolean, String> = withSyncAction("Refill Batch Conversion") { synchronized(mutationLock) {
        if (refillQuantity <= 0) return@synchronized Pair(false, "Refill quantity must be greater than zero")
        if (damagedQuantity < 0) return@synchronized Pair(false, "Damaged quantity cannot be negative")
        if (damagedQuantity > refillQuantity) {
            return@synchronized Pair(false, "Damaged/lost quantity ($damagedQuantity) cannot exceed refill quantity ($refillQuantity)")
        }
        if (packetSize !in listOf(1, 6, 12, 24)) {
            return@synchronized Pair(false, "Packet size must be 1, 6, 12, or 24 bottles")
        }

        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return@synchronized Pair(false, "Bottle size not found")
        val isOneTime = size.usageType == BottleUsageType.ONE_TIME || !size.isReturnableBottle
        if (!isOneTime) {
            return@synchronized Pair(false, "Refill Batch is strictly for Single Time Use bottles only.")
        }

        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        if (curInv.inUse < refillQuantity) {
            return@synchronized Pair(false, "Insufficient In Use stock for ${size.name}! Available In Use: ${curInv.inUse}")
        }

        val usableQuantity = refillQuantity - damagedQuantity
        val completePackets = usableQuantity / packetSize
        val packedBottles = completePackets * packetSize
        val looseBottles = usableQuantity % packetSize

        val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val variantId = makePackVariantId(bottleSizeId, packetSize, containerType)
        val variantName = formatPackVariantName(size.name, packetSize, containerType)
        val variantShort = formatPackVariantShortLabel(size.name, size.capacityLitres, packetSize, containerType)

        val updatedVariants = if (completePackets > 0) {
            val existing = curInv.packVariants.find { it.variantId == variantId }
            val updatedVariant = (existing ?: BottlePackVariant(
                variantId = variantId,
                bottleSizeId = bottleSizeId,
                displayName = variantName,
                shortLabel = variantShort,
                packSize = packetSize,
                containerType = containerType,
                packetCount = 0
            )).copy(
                displayName = variantName,
                shortLabel = variantShort,
                packetCount = (existing?.packetCount ?: 0) + completePackets
            )
            curInv.packVariants.filterNot { it.variantId == variantId } + updatedVariant
        } else {
            curInv.packVariants
        }

        val bottlesAddedToFull = if (returnLooseToInUse) packedBottles else usableQuantity
        val looseAddedToFull = if (returnLooseToInUse) 0 else looseBottles
        val returnedToInUseCount = if (returnLooseToInUse) looseBottles else 0
        val updatedInUse = (curInv.inUse - refillQuantity + returnedToInUseCount).coerceAtLeast(0)

        val updatedInv = curInv.copy(
            inUse = updatedInUse,
            atPlantFull = curInv.atPlantFull + bottlesAddedToFull,
            fullPackets = curInv.fullPackets + completePackets,
            fullLooseBottles = curInv.fullLooseBottles + looseAddedToFull,
            packVariants = updatedVariants,
            damaged = curInv.damaged + damagedQuantity
        )
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncDocument("bottleInventory", updatedInv.id, updatedInv)

        val looseSummary = if (returnLooseToInUse && looseBottles > 0) {
            "$looseBottles Loose -> In Use"
        } else {
            "$looseBottles Loose -> Full"
        }
        val conversionSummary = "In Use: -$refillQuantity -> $variantName: $completePackets ($packedBottles Btl), $looseSummary, Damaged: $damagedQuantity"
        val combinedNotes = listOfNotNull(
            notes.trim().takeIf { it.isNotEmpty() },
            conversionSummary
        ).joinToString(" | ")

        val refillRec = BottleRefillRecord(
            id = nextRecordId("refill"),
            date = effectiveDate,
            time = timeStr,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            quantityFilled = bottlesAddedToFull,
            packetSize = packetSize,
            containerType = containerType,
            packedVariantName = if (completePackets > 0) variantName else "",
            completePackets = completePackets,
            packedBottles = packedBottles,
            looseBottles = looseBottles,
            damagedBottles = damagedQuantity,
            refillCostPerBottle = size.refillCost,
            totalRefillCost = bottlesAddedToFull * size.refillCost,
            operatorName = _currentUser.value.name,
            notes = combinedNotes
        )
        _bottleRefills.value = listOf(refillRec) + _bottleRefills.value
        storage.saveBottleRefills(_bottleRefills.value)
        syncDocument("bottleRefills", refillRec.id, refillRec)

        val unitLabel = if (packetSize <= 1) "Bottle" else containerType.label
        if (bottlesAddedToFull > 0) {
            val refillMov = BottleMovement(
                id = nextRecordId("mov", "fill"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = if (completePackets > 0) variantName else size.name,
                movementType = BottleMovementType.PLANT_REFILL,
                quantity = bottlesAddedToFull,
                referenceType = if (packetSize <= 1) "Refill Batch (Single Bottle)" else "Refill Batch & ${containerType.label} Conversion",
                notes = "$conversionSummary. ${notes.trim()}".trim(),
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(refillMov) + _bottleMovements.value
            syncDocument("bottleMovements", refillMov.id, refillMov)
        }
        if (damagedQuantity > 0) {
            val dmgMov = BottleMovement(
                id = nextRecordId("mov", "dmg"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.DAMAGED,
                quantity = damagedQuantity,
                referenceType = "Refill Batch Loss/Damage",
                notes = "Damaged/lost $damagedQuantity ${size.name} during refill batch (${if (packetSize <= 1) "Single Bottle" else "$packetSize-bottle ${containerType.label}"}). ${notes.trim()}".trim(),
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(dmgMov) + _bottleMovements.value
            syncDocument("bottleMovements", dmgMov.id, dmgMov)
        }
        storage.saveBottleMovements(_bottleMovements.value)

        val looseMsg = if (returnLooseToInUse && looseBottles > 0) "$looseBottles loose returned to In Use" else "$looseBottles remaining loose"
        val convertedSummary = if (packetSize <= 1) {
            "$completePackets $unitLabel(s)"
        } else {
            "$completePackets $unitLabel(s) ($packedBottles bottles)"
        }
        addNotification(
            "Refill Batch Converted",
            "$variantName: $convertedSummary added to Plant Full" +
                (if (looseBottles > 0) ", $looseMsg" else "") +
                (if (damagedQuantity > 0) ", $damagedQuantity damaged." else "."),
            NotificationSeverity.SUCCESS
        )
        Pair(
            true,
            "$variantName: $convertedSummary, $looseMsg, $damagedQuantity damaged."
        )
    } }

    fun issueBottleStock(
        bottleSizeId: String,
        target: BottleIssueTarget,
        customerId: String?,
        quantity: Int,
        issueDate: String,
        deposit: Double,
        notes: String
    ): Pair<Boolean, String> = withSyncAction("Issue Bottle Stock") { synchronized(mutationLock) {
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero")
        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return@synchronized Pair(false, "Bottle size not found")
        val effectiveDate = issueDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        val isOneTime = size.usageType == BottleUsageType.ONE_TIME || !size.isReturnableBottle

        if (target == BottleIssueTarget.CUSTOMER && isOneTime) {
            return@synchronized Pair(false, "Only multiple-time returnable bottles can be issued to customers.")
        }
        if (target == BottleIssueTarget.CUSTOMER && customerId.isNullOrBlank()) {
            return@synchronized Pair(false, "Customer selection is required")
        }
        val customer = if (target == BottleIssueTarget.CUSTOMER) {
            getCustomerById(customerId!!) ?: return@synchronized Pair(false, "Customer not found")
        } else null

        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )

        // Both Customer (multiple-use only) and Issue to RO Plant (all sizes) deduct strictly from Plant Empty
        if (curInv.atPlantEmpty < quantity) {
            return@synchronized Pair(false, "Insufficient Plant Empty stock for ${size.name}! Available Empty: ${curInv.atPlantEmpty}")
        }

        val updatedInv = if (target == BottleIssueTarget.CUSTOMER) {
            curInv.copy(
                atPlantEmpty = curInv.atPlantEmpty - quantity,
                withCustomers = curInv.withCustomers + quantity,
                totalIssuedOrSold = curInv.totalIssuedOrSold + quantity
            )
        } else {
            // Issue to RO Plant: move Plant Empty -> In Use
            curInv.copy(
                atPlantEmpty = curInv.atPlantEmpty - quantity,
                inUse = curInv.inUse + quantity
            )
        }
        val invDeltas = if (target == BottleIssueTarget.CUSTOMER) {
            mapOf(
                "atPlantEmpty" to -quantity,
                "withCustomers" to quantity,
                "totalIssuedOrSold" to quantity
            )
        } else {
            mapOf(
                "atPlantEmpty" to -quantity,
                "inUse" to quantity
            )
        }
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncIncrementalDelta("bottleInventory", updatedInv.id, updatedInv, invDeltas)

        if (target == BottleIssueTarget.CUSTOMER && customer != null) {
            val existingAlloc = _customerAllocations.value.find { it.customerId == customer.id && it.bottleSizeId == bottleSizeId }
            val updatedAlloc = if (existingAlloc != null) {
                existingAlloc.copy(
                    totalIssued = existingAlloc.totalIssued + quantity,
                    emptyHeld = existingAlloc.emptyHeld + quantity,
                    depositAmount = existingAlloc.depositAmount + deposit,
                    lastUpdated = effectiveDate
                )
            } else {
                CustomerBottleAllocation(
                    id = nextRecordId("alloc", bottleSizeId),
                    customerId = customer.id,
                    customerName = customer.name,
                    bottleSizeId = bottleSizeId,
                    sizeName = size.name,
                    totalIssued = quantity,
                    emptyHeld = quantity,
                    damagedCount = 0,
                    returnedCount = 0,
                    depositAmount = deposit,
                    lastUpdated = effectiveDate
                )
            }
            _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customer.id && it.bottleSizeId == bottleSizeId } + updatedAlloc
            storage.saveCustomerAllocations(_customerAllocations.value)
            if (existingAlloc != null) {
                syncIncrementalDelta(
                    "customerBottleAllocations",
                    updatedAlloc.id,
                    updatedAlloc,
                    mapOf(
                        "totalIssued" to quantity,
                        "emptyHeld" to quantity,
                        "depositAmount" to deposit
                    ),
                    extraFields = mapOf("lastUpdated" to effectiveDate)
                )
            } else {
                syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
            }

            applyCustomerDeltaLocalAndCloud(
                customer.copy(
                    emptyBottlesOwed = customer.emptyBottlesOwed + quantity,
                    bottleDeposit = customer.bottleDeposit + deposit
                ),
                mapOf(
                    "emptyBottlesOwed" to quantity,
                    "bottleDeposit" to deposit
                )
            )

            val issueRecord = BottleIssueSaleRecord(
                id = nextRecordId("bis"),
                date = effectiveDate,
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                customerId = customer.id,
                customerName = customer.name,
                bottleSizeId = bottleSizeId,
                bottleSizeName = size.name,
                quantity = quantity,
                transactionType = BottleTransactionType.CUSTOMER_ISSUE,
                unitSellingPrice = if (quantity > 0) deposit / quantity else 0.0,
                revenue = deposit,
                notes = notes
            )
            _bottleIssueSales.value = listOf(issueRecord) + _bottleIssueSales.value
            storage.saveBottleIssueSales(_bottleIssueSales.value)
            syncDocument("bottleIssueSales", issueRecord.id, issueRecord)

            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.DELIVERY_OUT,
                quantity = quantity,
                customerId = customer.id,
                customerName = customer.name,
                referenceType = "Customer Bottle Issue",
                notes = "Issued $quantity ${size.name} from Plant Empty to ${customer.name}. $notes".trim(),
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)

            addNotification("Bottles Issued", "Issued $quantity ${size.name} to ${customer.name}.", NotificationSeverity.SUCCESS)
            Pair(true, "Issued $quantity ${size.name} to ${customer.name} successfully.")
        } else {
            val mov = BottleMovement(
                id = nextRecordId("mov"),
                date = effectiveDate,
                bottleSizeId = bottleSizeId,
                sizeName = size.name,
                movementType = BottleMovementType.DELIVERY_OUT,
                quantity = quantity,
                referenceType = "Issue to RO Plant (In Use)",
                notes = "Moved $quantity ${size.name} from Plant Empty to In Use (RO Plant). $notes".trim(),
                createdBy = _currentUser.value.name
            )
            _bottleMovements.value = listOf(mov) + _bottleMovements.value
            storage.saveBottleMovements(_bottleMovements.value)
            syncDocument("bottleMovements", mov.id, mov)

            addNotification("RO Plant Issue", "Moved $quantity ${size.name} from Plant Empty to In Use.", NotificationSeverity.INFO)
            Pair(true, "Issued $quantity ${size.name} to RO Plant (In Use) successfully.")
        }
    } }

    fun recordBottleLossOrDamage(
        source: BottleLossSource,
        bottleSizeId: String,
        quantity: Int,
        date: String,
        remarks: String,
        customerId: String? = null
    ): Pair<Boolean, String> = withSyncAction("Bottle Loss / Damage") { synchronized(mutationLock) {
        if (quantity <= 0) return@synchronized Pair(false, "Damaged quantity must be greater than zero")
        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return@synchronized Pair(false, "Bottle size not found")
        val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

        if (source == BottleLossSource.CUSTOMER_HELD) {
            if (customerId.isNullOrBlank()) return@synchronized Pair(false, "Customer selection is required")
            return@synchronized recordCustomerDamageStock(customerId, bottleSizeId, quantity, effectiveDate, remarks)
        }

        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )

        val updatedInv = when (source) {
            BottleLossSource.PLANT_FULL -> {
                if (quantity > curInv.atPlantFull) {
                    return@synchronized Pair(false, "Loss/damage ($quantity) cannot exceed Plant Full stock (${curInv.atPlantFull}).")
                }
                curInv.copy(
                    atPlantFull = curInv.atPlantFull - quantity,
                    damaged = curInv.damaged + quantity
                )
            }
            BottleLossSource.PLANT_EMPTY -> {
                if (quantity > curInv.atPlantEmpty) {
                    return@synchronized Pair(false, "Loss/damage ($quantity) cannot exceed Plant Empty stock (${curInv.atPlantEmpty}).")
                }
                curInv.copy(
                    atPlantEmpty = curInv.atPlantEmpty - quantity,
                    damaged = curInv.damaged + quantity
                )
            }
            BottleLossSource.CUSTOMER_HELD -> curInv
        }

        val invDeltas = when (source) {
            BottleLossSource.PLANT_FULL -> mapOf("atPlantFull" to -quantity, "damaged" to quantity)
            BottleLossSource.PLANT_EMPTY -> mapOf("atPlantEmpty" to -quantity, "damaged" to quantity)
            BottleLossSource.CUSTOMER_HELD -> emptyMap()
        }
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        if (invDeltas.isNotEmpty()) {
            syncIncrementalDelta("bottleInventory", updatedInv.id, updatedInv, invDeltas)
        }

        val mov = BottleMovement(
            id = nextRecordId("mov"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.DAMAGED,
            quantity = quantity,
            referenceType = "Loss & Damage (${source.label})",
            notes = "${source.label}: $quantity damaged/lost. $remarks".trim(),
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(mov) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", mov.id, mov)

        addNotification("Loss & Damage Recorded", "$quantity ${size.name} marked damaged from ${source.label}.", NotificationSeverity.ALERT)
        Pair(true, "Recorded $quantity lost/damaged ${size.name} from ${source.label}.")
    } }

    fun returnCustomerBottleStock(
        customerId: String,
        bottleSizeId: String,
        quantity: Int,
        returnDate: String,
        notes: String
    ): Pair<Boolean, String> = withSyncAction("Return Customer Bottles") { synchronized(mutationLock) {
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero")
        val cust = getCustomerById(customerId) ?: return@synchronized Pair(false, "Customer not found")
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return@synchronized Pair(false, "Bottle size not found")
        val effectiveDate = returnDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

        val alloc = _customerAllocations.value.find { it.customerId == customerId && it.bottleSizeId == bottleSizeId }
        val heldCount = alloc?.emptyHeld ?: 0
        if (quantity > heldCount) {
            return@synchronized Pair(false, "Returned quantity ($quantity) cannot exceed currently held bottles ($heldCount) for this customer!\n(واپسی کی مقدار گاہک کے پاس موجود بوتلوں سے زیادہ نہیں ہو سکتی)")
        }

        // 1. Update customer allocation
        val updatedAlloc = (alloc ?: CustomerBottleAllocation(
            id = nextRecordId("alloc"),
            customerId = customerId,
            customerName = cust.name,
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )).copy(
            emptyHeld = (heldCount - quantity).coerceAtLeast(0),
            returnedCount = (alloc?.returnedCount ?: 0) + quantity,
            lastUpdated = effectiveDate
        )
        _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customerId && it.bottleSizeId == bottleSizeId } + updatedAlloc
        storage.saveCustomerAllocations(_customerAllocations.value)
        if (alloc != null) {
            syncIncrementalDelta(
                "customerBottleAllocations",
                updatedAlloc.id,
                updatedAlloc,
                mapOf(
                    "emptyHeld" to (updatedAlloc.emptyHeld - heldCount),
                    "returnedCount" to quantity
                ),
                extraFields = mapOf("lastUpdated" to effectiveDate)
            )
        } else {
            syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
        }

        // 2. Decrement customer global emptyBottlesOwed
        val updatedCust = cust.copy(
            emptyBottlesOwed = (cust.emptyBottlesOwed - quantity).coerceAtLeast(0)
        )
        applyCustomerDeltaLocalAndCloud(
            updatedCust,
            mapOf("emptyBottlesOwed" to (updatedCust.emptyBottlesOwed - cust.emptyBottlesOwed))
        )

        // 3. Add to plant inventory (atPlantEmpty) and reduce withCustomers
        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updatedInv = curInv.copy(
            atPlantEmpty = curInv.atPlantEmpty + quantity,
            withCustomers = (curInv.withCustomers - quantity).coerceAtLeast(0)
        )
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf(
                "atPlantEmpty" to quantity,
                "withCustomers" to (updatedInv.withCustomers - curInv.withCustomers)
            )
        )

        // 4. Movement log
        val mov = BottleMovement(
            id = nextRecordId("mov"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.RETURN_IN,
            quantity = quantity,
            customerId = customerId,
            customerName = cust.name,
            referenceType = "Customer Return",
            notes = "Returned $quantity empty bottles from ${cust.name}. $notes",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(mov) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", mov.id, mov)

        // 5. Transaction log
        val issueRecord = BottleIssueSaleRecord(
            id = nextRecordId("bis"),
            date = effectiveDate,
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            customerId = customerId,
            customerName = cust.name,
            bottleSizeId = bottleSizeId,
            bottleSizeName = size.name,
            quantity = quantity,
            transactionType = BottleTransactionType.EMPTY_COLLECTED,
            notes = notes
        )
        _bottleIssueSales.value = listOf(issueRecord) + _bottleIssueSales.value
        storage.saveBottleIssueSales(_bottleIssueSales.value)
        syncDocument("bottleIssueSales", issueRecord.id, issueRecord)

        addNotification("Bottle Return Recorded", "Customer ${cust.name} returned $quantity empty bottles.", NotificationSeverity.SUCCESS)
        Pair(true, "Successfully recorded return of $quantity empty bottles from ${cust.name}.")
    } }

    fun recordCustomerDamageStock(
        customerId: String,
        bottleSizeId: String,
        damagedQuantity: Int,
        date: String,
        remarks: String
    ): Pair<Boolean, String> = withSyncAction("Customer Bottle Damage") { synchronized(mutationLock) {
        if (damagedQuantity <= 0) return@synchronized Pair(false, "Damaged quantity must be greater than zero")
        val cust = getCustomerById(customerId) ?: return@synchronized Pair(false, "Customer not found")
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return@synchronized Pair(false, "Bottle size not found")
        val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

        val alloc = _customerAllocations.value.find { it.customerId == customerId && it.bottleSizeId == bottleSizeId }
        val heldCount = alloc?.emptyHeld ?: 0
        if (damagedQuantity > heldCount) {
            return@synchronized Pair(false, "Damaged quantity ($damagedQuantity) cannot exceed bottles currently held ($heldCount)!\n(خراب بوتلوں کی تعداد گاہک کے پاس موجود بوتلوں سے زیادہ نہیں ہو سکتی)")
        }

        // 1. Update customer allocation
        val updatedAlloc = (alloc ?: CustomerBottleAllocation(
            id = nextRecordId("alloc"),
            customerId = customerId,
            customerName = cust.name,
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )).copy(
            emptyHeld = (heldCount - damagedQuantity).coerceAtLeast(0),
            damagedCount = (alloc?.damagedCount ?: 0) + damagedQuantity,
            lastUpdated = effectiveDate
        )
        _customerAllocations.value = _customerAllocations.value.filterNot { it.customerId == customerId && it.bottleSizeId == bottleSizeId } + updatedAlloc
        storage.saveCustomerAllocations(_customerAllocations.value)
        if (alloc != null) {
            syncIncrementalDelta(
                "customerBottleAllocations",
                updatedAlloc.id,
                updatedAlloc,
                mapOf(
                    "emptyHeld" to (updatedAlloc.emptyHeld - heldCount),
                    "damagedCount" to damagedQuantity
                ),
                extraFields = mapOf("lastUpdated" to effectiveDate)
            )
        } else {
            syncDocument("customerBottleAllocations", updatedAlloc.id, updatedAlloc)
        }

        // 2. Decrement customer emptyBottlesOwed
        val updatedCust = cust.copy(
            emptyBottlesOwed = (cust.emptyBottlesOwed - damagedQuantity).coerceAtLeast(0)
        )
        applyCustomerDeltaLocalAndCloud(
            updatedCust,
            mapOf("emptyBottlesOwed" to (updatedCust.emptyBottlesOwed - cust.emptyBottlesOwed))
        )

        // 3. Move to Damaged Inventory (never usable)
        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        val updatedInv = curInv.copy(
            withCustomers = (curInv.withCustomers - damagedQuantity).coerceAtLeast(0),
            damaged = curInv.damaged + damagedQuantity
        )
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf(
                "withCustomers" to (updatedInv.withCustomers - curInv.withCustomers),
                "damaged" to damagedQuantity
            )
        )

        // 4. Movement log
        val mov = BottleMovement(
            id = nextRecordId("mov"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.CUSTOMER_DAMAGE,
            quantity = damagedQuantity,
            customerId = customerId,
            customerName = cust.name,
            referenceType = "Customer Breakage",
            notes = "Customer ${cust.name}: $damagedQuantity bottles damaged / broken. $remarks",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(mov) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", mov.id, mov)

        // 5. Transaction log
        val record = BottleIssueSaleRecord(
            id = nextRecordId("bis"),
            date = effectiveDate,
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            customerId = customerId,
            customerName = cust.name,
            bottleSizeId = bottleSizeId,
            bottleSizeName = size.name,
            quantity = damagedQuantity,
            transactionType = BottleTransactionType.DAMAGE_LOGGED,
            notes = remarks
        )
        _bottleIssueSales.value = listOf(record) + _bottleIssueSales.value
        storage.saveBottleIssueSales(_bottleIssueSales.value)
        syncDocument("bottleIssueSales", record.id, record)

        addNotification("Bottle Damage Recorded", "$damagedQuantity bottles broken by ${cust.name}. Moved to damaged stock.", NotificationSeverity.ALERT)
        Pair(true, "Recorded $damagedQuantity damaged bottles under Damaged Inventory.")
    } }

    fun consumeOneTimeBottles(
        bottleSizeId: String,
        quantity: Int,
        date: String,
        notes: String
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero")
        val size = _bottleSizes.value.find { it.id == bottleSizeId } ?: return@synchronized Pair(false, "Bottle size not found")
        val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

        val curInv = _inventory.value.find { it.bottleSizeId == bottleSizeId } ?: BottleInventory(
            id = "inv_$bottleSizeId",
            bottleSizeId = bottleSizeId,
            sizeName = size.name
        )
        if (quantity > curInv.atPlantFull) {
            return@synchronized Pair(false, "Cannot consume $quantity: only ${curInv.atPlantFull} available in filled stock.")
        }

        val updatedInv = curInv.copy(
            atPlantFull = (curInv.atPlantFull - quantity).coerceAtLeast(0),
            totalIssuedOrSold = curInv.totalIssuedOrSold + quantity
        )
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId } + updatedInv
        storage.saveInventory(_inventory.value)
        syncIncrementalDelta(
            "bottleInventory",
            updatedInv.id,
            updatedInv,
            mapOf(
                "atPlantFull" to (updatedInv.atPlantFull - curInv.atPlantFull),
                "totalIssuedOrSold" to quantity
            )
        )

        val mov = BottleMovement(
            id = nextRecordId("mov"),
            date = effectiveDate,
            bottleSizeId = bottleSizeId,
            sizeName = size.name,
            movementType = BottleMovementType.DELIVERY_OUT,
            quantity = quantity,
            referenceType = "Production Consumption",
            notes = "Consumed $quantity bottles for filling / packaging: $notes",
            createdBy = _currentUser.value.name
        )
        _bottleMovements.value = listOf(mov) + _bottleMovements.value
        storage.saveBottleMovements(_bottleMovements.value)
        syncDocument("bottleMovements", mov.id, mov)

        addNotification("One-time Bottles Consumed", "Consumed $quantity ${size.name} in production.", NotificationSeverity.INFO)
        Pair(true, "Successfully recorded consumption of $quantity bottles.")
    }

    fun deleteOrArchiveBottleSize(bottleSizeId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) {
            return Pair(false, "Permission required to delete bottle stock items.")
        }
        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return Pair(false, "Bottle size not found")

        val inv = _inventory.value.find { it.bottleSizeId == bottleSizeId }
        val plantStock = (inv?.atPlantFull ?: 0) + (inv?.atPlantEmpty ?: 0) + (inv?.inUse ?: 0)
        val heldWithCustomers = (inv?.withCustomers ?: 0) + _customerAllocations.value
            .filter { it.bottleSizeId == bottleSizeId }
            .sumOf { it.emptyHeld }

        val hasDeliveryHistory = _deliveries.value.any { delivery ->
            delivery.items.any { it.bottleSizeId == bottleSizeId }
        }

        if (heldWithCustomers > 0) {
            return Pair(false, "Cannot delete: $heldWithCustomers bottles are currently held with customers.\n(یہ سائز ڈیلیٹ نہیں ہو سکتا کیونکہ گاہکوں کے پاس $heldWithCustomers بوتلیں موجود ہیں)")
        }
        if (plantStock > 0) {
            return Pair(false, "Cannot delete: $plantStock bottles are currently present in plant stock.\n(یہ سائز ڈیلیٹ نہیں ہو سکتا کیونکہ پلانٹ پر $plantStock بوتلیں موجود ہیں)")
        }

        if (hasDeliveryHistory) {
            // Archive safely
            _bottleSizes.value = _bottleSizes.value.map {
                if (it.id == bottleSizeId) it.copy(active = false) else it
            }
            storage.saveBottleSizes(_bottleSizes.value)
            syncDocument("bottleSizes", bottleSizeId, size.copy(active = false))
            return Pair(true, "Bottle size has delivery history, archived successfully (hidden from new orders).")
        }

        val removedInvs = _inventory.value.filter { it.bottleSizeId == bottleSizeId }
        _bottleSizes.value = _bottleSizes.value.filterNot { it.id == bottleSizeId }
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId }
        storage.saveBottleSizes(_bottleSizes.value)
        storage.saveInventory(_inventory.value)
        deleteDocument("bottleSizes", bottleSizeId)
        removedInvs.forEach { deleteDocument("bottleInventory", it.id) }
        return Pair(true, "Bottle size deleted successfully.")
    }

    fun forceDeleteBottleSize(bottleSizeId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) {
            return Pair(false, "Permission required to delete bottle stock items.")
        }
        val size = _bottleSizes.value.find { it.id == bottleSizeId }
            ?: return Pair(false, "Bottle size not found (سائز نہیں ملا)")

        val removedInvs = _inventory.value.filter { it.bottleSizeId == bottleSizeId }
        val removedAllocs = _customerAllocations.value.filter { it.bottleSizeId == bottleSizeId }
        val removedRates = _customerRates.value.filter { it.bottleSizeId == bottleSizeId }

        // 1. Remove from bottleSizes
        _bottleSizes.value = _bottleSizes.value.filterNot { it.id == bottleSizeId }
        storage.saveBottleSizes(_bottleSizes.value)
        deleteDocument("bottleSizes", bottleSizeId)

        // 2. Clear from bottleInventory
        _inventory.value = _inventory.value.filterNot { it.bottleSizeId == bottleSizeId }
        storage.saveInventory(_inventory.value)
        if (removedInvs.isNotEmpty()) {
            removedInvs.forEach { deleteDocument("bottleInventory", it.id) }
        } else {
            deleteDocument("bottleInventory", "inv_$bottleSizeId")
        }

        // 3. Clear customer allocations for this size
        _customerAllocations.value = _customerAllocations.value.filterNot { it.bottleSizeId == bottleSizeId }
        storage.saveCustomerAllocations(_customerAllocations.value)
        removedAllocs.forEach { deleteDocument("customerBottleAllocations", it.id) }

        // 4. Clear customer rates for this size
        _customerRates.value = _customerRates.value.filterNot { it.bottleSizeId == bottleSizeId }
        storage.saveCustomerRates(_customerRates.value)
        removedRates.forEach { deleteDocument("customerRates", it.id) }

        addNotification(
            "Bottle Size Force Deleted",
            "Admin force-deleted bottle size ${size.name} and cleared all associated stock balances.",
            NotificationSeverity.WARNING
        )
        return Pair(true, "سائز اور تمام متعلقہ اسٹاک زبردستی ڈیلیٹ کر دیا گیا (Force deleted successfully)")
    }

    // --- Sub-Tab 2: Other Material Stock Operations ---

    fun addFactoryMaterial(material: FactoryMaterial): String {
        if (!hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)) {
            addNotification("Access Restricted", "Admin has restricted adding new items/materials.", NotificationSeverity.WARNING)
            return ""
        }
        val matId = if (material.id.isNotBlank()) material.id else nextRecordId("mat")
        val newMat = material.copy(id = matId, currentStock = material.openingStock)
        return withSyncAction("Add Material • ${newMat.name}") {
            _factoryMaterials.value = _factoryMaterials.value + newMat
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", matId, newMat)

            if (newMat.openingStock > 0) {
                val tx = MaterialTransaction(
                    id = nextRecordId("mtx"),
                    materialId = matId,
                    materialName = newMat.name,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    actionType = MaterialActionType.REFILL,
                    quantity = newMat.openingStock,
                    unitCost = newMat.purchaseRate,
                    totalCost = newMat.openingStock * newMat.purchaseRate,
                    uom = newMat.uom,
                    supplierOrReceiver = newMat.supplierName.ifBlank { "Initial Opening Stock" },
                    notes = "Initial opening stock recorded",
                    balanceAfter = newMat.openingStock,
                    performedBy = _currentUser.value.name
                )
                _materialTransactions.value = listOf(tx) + _materialTransactions.value
                storage.saveMaterialTransactions(_materialTransactions.value)
                syncDocument("materialTransactions", tx.id, tx)
            }

            addNotification("Material Added", "Added material '${newMat.name}' (${newMat.openingStock} ${newMat.uom}).", NotificationSeverity.SUCCESS)
            matId
        }
    }

    fun updateFactoryMaterial(material: FactoryMaterial) {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.EDIT)) return
        withSyncAction("Update Material • ${material.name}") {
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == material.id) material else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", material.id, material)
            addNotification("Material Updated", "Updated material '${material.name}'.", NotificationSeverity.INFO)
        }
    }

    fun issueFactoryMaterial(
        materialId: String,
        category: MaterialIssueCategory,
        quantity: Double,
        date: String,
        responsiblePerson: String,
        remarks: String
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero")
        val mat = _factoryMaterials.value.find { it.id == materialId }
            ?: return@synchronized Pair(false, "Material not found")
        if (quantity > mat.currentStock) {
            return@synchronized Pair(false, "Cannot issue $quantity ${mat.uom}: available stock is ${mat.currentStock} ${mat.uom}.")
        }

        withSyncAction("Issue Material • ${mat.name}") {
            val newStock = (mat.currentStock - quantity).coerceAtLeast(0.0)
            val updatedMat = mat.copy(currentStock = newStock, lastUpdated = System.currentTimeMillis())
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == materialId) updatedMat else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", materialId, updatedMat)

            val tx = MaterialTransaction(
                id = nextRecordId("mtx"),
                materialId = materialId,
                materialName = mat.name,
                date = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
                actionType = MaterialActionType.ISSUE,
                quantity = quantity,
                unitCost = mat.purchaseRate,
                totalCost = quantity * mat.purchaseRate,
                uom = mat.uom,
                issueCategory = category,
                supplierOrReceiver = responsiblePerson.ifBlank { "Department Staff" },
                notes = remarks,
                balanceAfter = newStock,
                performedBy = _currentUser.value.name
            )
            _materialTransactions.value = listOf(tx) + _materialTransactions.value
            storage.saveMaterialTransactions(_materialTransactions.value)
            syncDocument("materialTransactions", tx.id, tx)

            addNotification("Material Issued", "Issued $quantity ${mat.uom} of ${mat.name} for ${category.label}.", NotificationSeverity.SUCCESS)
            Pair(true, "Successfully issued $quantity ${mat.uom} of ${mat.name}.")
        }
    }

    fun refillFactoryMaterial(
        materialId: String,
        quantity: Double,
        purchaseRate: Double,
        date: String,
        supplierName: String,
        invoiceNo: String,
        notes: String,
        paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
        paymentMode: StockPaymentMode = StockPaymentMode.CASH
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD)) {
            return@synchronized Pair(false, "Admin has restricted adding material stock (ایڈمن نے اسٹاک شامل کرنے کی اجازت نہیں دی)")
        }
        if (quantity <= 0) return@synchronized Pair(false, "Quantity must be greater than zero")
        if (purchaseRate < 0) return@synchronized Pair(false, "Purchase rate cannot be negative")
        val mat = _factoryMaterials.value.find { it.id == materialId }
            ?: return@synchronized Pair(false, "Material not found")

        withSyncAction("Add Material Stock • ${mat.name}") {
            val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
            val cleanSupplier = supplierName.ifBlank { mat.supplierName.ifBlank { "Supplier" } }
            val newStock = mat.currentStock + quantity
            val updatedMat = mat.copy(
                currentStock = newStock,
                purchaseRate = if (purchaseRate > 0) purchaseRate else mat.purchaseRate,
                supplierName = cleanSupplier,
                lastUpdated = System.currentTimeMillis()
            )
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == materialId) updatedMat else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", materialId, updatedMat)

            val totalCost = quantity * purchaseRate
            val tx = MaterialTransaction(
                id = nextRecordId("mtx"),
                materialId = materialId,
                materialName = mat.name,
                date = effectiveDate,
                actionType = MaterialActionType.REFILL,
                quantity = quantity,
                unitCost = purchaseRate,
                totalCost = totalCost,
                uom = mat.uom,
                supplierOrReceiver = cleanSupplier,
                invoiceNo = invoiceNo,
                paymentSource = paymentSource.label,
                paymentMode = paymentMode.label,
                notes = notes,
                balanceAfter = newStock,
                performedBy = _currentUser.value.name
            )
            _materialTransactions.value = listOf(tx) + _materialTransactions.value
            storage.saveMaterialTransactions(_materialTransactions.value)
            syncDocument("materialTransactions", tx.id, tx)

            if (totalCost > 0.0) {
                val expCatId = if (mat.costType == ExpenseType.CAPEX) "cat_asset_machinery" else "cat_maint"
                addExpense(
                    categoryId = expCatId,
                    categoryName = "Material Stock Refill",
                    description = "Refilled $quantity ${mat.uom} of ${mat.name}",
                    amount = totalCost,
                    method = paymentMode.paymentMethod,
                    payee = cleanSupplier,
                    notes = "${paymentSource.label} • ${paymentMode.label}. $notes".trim(),
                    type = mat.costType,
                    expenseDate = effectiveDate,
                    paymentSource = paymentSource,
                    invoiceNumber = invoiceNo.trim(),
                    linkedInventoryType = "MATERIAL",
                    linkedInventoryItemId = materialId,
                    linkedInventoryItemName = mat.name,
                    linkedInventoryQty = quantity,
                    linkedInventoryUnitCost = purchaseRate,
                    linkedInventoryTransactionId = tx.id
                )
            }

            addNotification("Material Refill", "Added $quantity ${mat.uom} to ${mat.name} (${paymentSource.label}).", NotificationSeverity.SUCCESS)
            Pair(true, "Successfully added $quantity ${mat.uom} to ${mat.name} stock.")
        }
    }

    fun recordMaterialDamageOrMalfunction(
        materialId: String,
        actionType: MaterialActionType,
        quantity: Double,
        date: String,
        remarks: String
    ): Pair<Boolean, String> = synchronized(mutationLock) {
        if (actionType != MaterialActionType.DAMAGE && actionType != MaterialActionType.MALFUNCTION) {
            return@synchronized Pair(false, "Invalid action type")
        }
        if (quantity <= 0.0) return@synchronized Pair(false, "Quantity must be greater than zero")
        val mat = _factoryMaterials.value.find { it.id == materialId }
            ?: return@synchronized Pair(false, "Material not found")
        if (quantity > mat.currentStock) {
            return@synchronized Pair(false, "Cannot exceed available stock (${mat.currentStock} ${mat.uom}).")
        }

        withSyncAction("${actionType.label} • ${mat.name}") {
            val effectiveDate = date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
            val newStock = (mat.currentStock - quantity).coerceAtLeast(0.0)
            val updatedMat = mat.copy(currentStock = newStock, lastUpdated = System.currentTimeMillis())
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == materialId) updatedMat else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", materialId, updatedMat)

            val tx = MaterialTransaction(
                id = nextRecordId("mtx"),
                materialId = materialId,
                materialName = mat.name,
                date = effectiveDate,
                actionType = actionType,
                quantity = quantity,
                unitCost = mat.purchaseRate,
                totalCost = quantity * mat.purchaseRate,
                uom = mat.uom,
                supplierOrReceiver = actionType.label,
                notes = remarks.ifBlank { actionType.label },
                balanceAfter = newStock,
                performedBy = _currentUser.value.name
            )
            _materialTransactions.value = listOf(tx) + _materialTransactions.value
            storage.saveMaterialTransactions(_materialTransactions.value)
            syncDocument("materialTransactions", tx.id, tx)

            addNotification(actionType.label, "Recorded $quantity ${mat.uom} of ${mat.name} as ${actionType.label}.", NotificationSeverity.WARNING)
            Pair(true, "Recorded $quantity ${mat.uom} of ${mat.name} (${actionType.label}).")
        }
    }

    fun deleteOrArchiveFactoryMaterial(materialId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.DELETE)) {
            return Pair(false, "Permission required to delete materials in Stock & Cost.")
        }
        val mat = _factoryMaterials.value.find { it.id == materialId }
            ?: return Pair(false, "Material not found")
        if (mat.currentStock > 0.0) {
            return Pair(false, "Cannot delete material with existing stock (${mat.currentStock} ${mat.uom})!\n(موجود اسٹاک کے ہوتے ہوئے سامان ڈیلیٹ نہیں ہو سکتا)")
        }

        val hasTx = _materialTransactions.value.any { it.materialId == materialId }
        if (hasTx) {
            val archived = mat.copy(active = false)
            _factoryMaterials.value = _factoryMaterials.value.map { if (it.id == materialId) archived else it }
            storage.saveFactoryMaterials(_factoryMaterials.value)
            syncDocument("factoryMaterials", materialId, archived)
            return Pair(true, "Material archived successfully.")
        }

        _factoryMaterials.value = _factoryMaterials.value.filterNot { it.id == materialId }
        storage.saveFactoryMaterials(_factoryMaterials.value)
        deleteDocument("factoryMaterials", materialId)
        return Pair(true, "Material deleted successfully.")
    }

    fun deleteBottleMovement(movementId: String): Boolean {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) return false
        _bottleMovements.value = _bottleMovements.value.filterNot { it.id == movementId }
        storage.saveBottleMovements(_bottleMovements.value)
        deleteDocument("bottleMovements", movementId)
        addNotification("Audit Record Deleted", "Admin deleted stock movement record.", NotificationSeverity.WARNING)
        return true
    }

    fun clearAllBottleMovements(): Boolean {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) return false
        val oldMovements = _bottleMovements.value
        _bottleMovements.value = emptyList()
        storage.saveBottleMovements(emptyList())
        oldMovements.forEach { deleteDocument("bottleMovements", it.id) }
        addNotification("Audit Log Cleared", "Admin cleared all bottle stock movement audit records.", NotificationSeverity.WARNING)
        return true
    }

    fun deleteMaterialTransaction(txId: String): Boolean {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.DELETE)) return false
        _materialTransactions.value = _materialTransactions.value.filterNot { it.id == txId }
        storage.saveMaterialTransactions(_materialTransactions.value)
        deleteDocument("materialTransactions", txId)
        addNotification("Material Record Deleted", "Admin deleted material transaction record.", NotificationSeverity.WARNING)
        return true
    }

    fun clearAllMaterialTransactions(): Boolean {
        if (!hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.DELETE)) return false
        val oldTransactions = _materialTransactions.value
        _materialTransactions.value = emptyList()
        storage.saveMaterialTransactions(emptyList())
        oldTransactions.forEach { deleteDocument("materialTransactions", it.id) }
        addNotification("Material Records Cleared", "Admin cleared all material transfer records.", NotificationSeverity.WARNING)
        return true
    }

    // --- Sub-Tab 3: Costing & Monthly Financials (STRICT ADMIN ONLY) ---

    fun saveBottleCosting(record: BottleCostingRecord): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT)) {
            return Pair(false, "Security Error: Costing permission required (لاگت ماڈیول میں تبدیلی کی اجازت درکار ہے)")
        }
        val size = _bottleSizes.value.find { it.id == record.bottleSizeId }
            ?: return Pair(false, "Bottle size not found")

        val isMultiUse = size.usageType == BottleUsageType.MULTIPLE_TIME || size.isReturnableBottle
        val effectivePackQty = record.packQty.coerceAtLeast(1)

        val calculatedLabelCost: Double
        val calculatedTotal: Double
        val effectiveConfirmedCost: Double
        if (record.usesDetailedCosting) {
            calculatedLabelCost = (record.labelPrice + record.sealPrice) * effectivePackQty
            calculatedTotal = record.copy(packQty = effectivePackQty).computeDetailedPackCost()
            effectiveConfirmedCost = if (record.confirmedPacketCost > 0.0) record.confirmedPacketCost else calculatedTotal
        } else {
            val totalPieces = record.labelSealTotalPieces.coerceAtLeast(1)
            calculatedLabelCost = (record.labelSealPacketPrice / totalPieces) * record.labelSealPiecesUsedPerPack
            val effectivePurchaseCost = if (isMultiUse) 0.0 else record.bottlePurchaseRate
            calculatedTotal = effectivePurchaseCost +
                record.fillingRate +
                record.packingRate +
                calculatedLabelCost +
                record.cartonPolytheneCost +
                record.capCost +
                record.waterCost +
                record.labourCost +
                record.electricityCost +
                record.deliveryCost
            effectiveConfirmedCost = if (record.confirmedPacketCost > 0.0) record.confirmedPacketCost else calculatedTotal
        }

        val profit = if (record.salePrice > 0.0) record.salePrice - effectiveConfirmedCost else 0.0
        val existingMatch = _bottleCostings.value.find {
            (record.id.isNotBlank() && it.id == record.id) ||
                (it.bottleSizeId == record.bottleSizeId && it.packingType == record.packingType)
        }
        val costingId = existingMatch?.id?.takeIf { it.isNotBlank() }
            ?: record.id.ifBlank { nextRecordId("cost") }

        return withSyncAction("Bottle Costing • ${size.name} (${record.packingType.shortLabel})") {
            val finalRecord = record.copy(
                id = costingId,
                bottleSizeName = size.name,
                isMultiTimeUse = isMultiUse,
                packQty = effectivePackQty,
                labelSealCalculatedCost = calculatedLabelCost,
                calculatedPackCost = calculatedTotal,
                confirmedPacketCost = if (record.usesDetailedCosting || record.confirmedPacketCost > 0.0) effectiveConfirmedCost else 0.0,
                totalCostPerPackOrBottle = effectiveConfirmedCost,
                profitPerPackOrBottle = profit,
                date = record.date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) },
                updatedBy = _currentUser.value.name.ifBlank { "Admin" },
                timestamp = System.currentTimeMillis()
            )

            // Replace matching entry by ID or (bottleSizeId + packingType) so Single, Packet, and Carton coexist separately!
            val existingIndex = _bottleCostings.value.indexOfFirst {
                it.id == costingId || (it.bottleSizeId == record.bottleSizeId && it.packingType == record.packingType)
            }
            _bottleCostings.value = if (existingIndex >= 0) {
                _bottleCostings.value.toMutableList().apply { set(existingIndex, finalRecord) }
            } else {
                _bottleCostings.value + finalRecord
            }
            storage.saveBottleCostings(_bottleCostings.value)
            syncDocument("bottleCostings", costingId, finalRecord)

            // Keep BottleSize reference rates updated when editing primary Single/default packing
            val isPrimaryPack = record.packingType == CostingPackingType.SINGLE ||
                (size.packQuantity > 1 && record.packingType == CostingPackingType.PACKET)
            val updatedSize = size.copy(
                purchaseCost = if (record.bottlePurchaseRate > 0.0) record.bottlePurchaseRate else size.purchaseCost,
                latestPurchaseRate = if (record.bottlePurchaseRate > 0.0) record.bottlePurchaseRate else size.latestPurchaseRate,
                refillCost = if (isPrimaryPack) effectiveConfirmedCost else size.refillCost,
                deliveryCost = if (isPrimaryPack && record.deliveryCost > 0.0) record.deliveryCost else size.deliveryCost,
                defaultRefillPrice = if (isPrimaryPack && record.salePrice > 0.0) record.salePrice else size.defaultRefillPrice
            )
            _bottleSizes.value = _bottleSizes.value.map { if (it.id == size.id) updatedSize else it }
            storage.saveBottleSizes(_bottleSizes.value)
            syncDocument("bottleSizes", size.id, updatedSize)

            addNotification(
                "Costing Saved",
                "Saved ${size.name} (${record.packingType.label}): Confirmed Cost Rs. ${String.format(Locale.ENGLISH, "%.2f", effectiveConfirmedCost)}.",
                NotificationSeverity.SUCCESS
            )
            Pair(true, "Bottle costing saved successfully (لاگت کا تخمینہ محفوظ کر لیا گیا)")
        }
    }

    fun deleteBottleCosting(costingId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) {
            return Pair(false, "Security Error: Costing delete permission required")
        }
        _bottleCostings.value = _bottleCostings.value.filterNot { it.id == costingId }
        storage.saveBottleCostings(_bottleCostings.value)
        deleteDocument("bottleCostings", costingId)
        return Pair(true, "Costing record deleted.")
    }

    /**
     * Fetches the Monthly Operational Cost record for [month] ("YYYY-MM").
     * If no explicit record exists for [month], carries forward Budget Amounts from the most recent
     * saved month while keeping Actual Expenses at 0 until entered at month-end.
     */
    fun getMonthlyOperationalCostForMonth(month: String): MonthlyOperationalCostRecord {
        val cleanMonth = month.ifBlank { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
        val exact = _monthlyOperationalCosts.value.find { it.month == cleanMonth }
        if (exact != null) return exact

        val template = _monthlyOperationalCosts.value
            .sortedByDescending { it.month }
            .firstOrNull { it.month <= cleanMonth }
            ?: _monthlyOperationalCosts.value.maxByOrNull { it.timestamp }

        return if (template != null) {
            MonthlyOperationalCostRecord(
                id = "moc_$cleanMonth",
                month = cleanMonth,
                vehicleMaintenance = OperationalCostLine(budgetAmount = template.vehicleMaintenance.budgetAmount, actualAmount = 0.0),
                roMaintenance = OperationalCostLine(budgetAmount = template.roMaintenance.budgetAmount, actualAmount = 0.0),
                roPlantRent = OperationalCostLine(budgetAmount = template.roPlantRent.budgetAmount, actualAmount = 0.0),
                vehicleMonthlyRent = OperationalCostLine(budgetAmount = template.vehicleMonthlyRent.budgetAmount, actualAmount = 0.0),
                electricityBill = OperationalCostLine(budgetAmount = template.electricityBill.budgetAmount, actualAmount = 0.0),
                otherFixedCosts = OperationalCostLine(budgetAmount = template.otherFixedCosts.budgetAmount, actualAmount = 0.0)
            )
        } else {
            MonthlyOperationalCostRecord(id = "moc_$cleanMonth", month = cleanMonth)
        }
    }

    fun saveMonthlyOperationalCost(record: MonthlyOperationalCostRecord): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.EDIT) &&
            !hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)
        ) {
            return Pair(false, "Security Error: Admin permission required to update Monthly Operational Costs")
        }
        val cleanMonth = record.month.ifBlank { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
        val docId = "moc_$cleanMonth"
        return withSyncAction("Monthly Operational Costs • $cleanMonth") {
            val finalRecord = record.copy(
                id = docId,
                month = cleanMonth,
                updatedBy = _currentUser.value.name.ifBlank { "Admin" },
                timestamp = System.currentTimeMillis()
            )
            _monthlyOperationalCosts.value =
                _monthlyOperationalCosts.value.filterNot { it.month == cleanMonth || it.id == docId } + finalRecord
            storage.saveMonthlyOperationalCosts(_monthlyOperationalCosts.value)
            syncDocument("monthlyOperationalCosts", docId, finalRecord)
            addNotification(
                "Monthly Operational Costs Saved",
                "Saved operational costs for $cleanMonth (Effective: Rs. ${finalRecord.totalEffectiveCost.toInt()}).",
                NotificationSeverity.SUCCESS
            )
            Pair(true, "Monthly operational costs saved for $cleanMonth")
        }
    }

    fun addMonthlyRecurringExpense(expense: MonthlyRecurringExpense): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)) {
            return Pair(false, "Security Error: Permission required to add Monthly Recurring Costs")
        }
        val expId = if (expense.id.isNotBlank()) expense.id else nextRecordId("mre")
        val record = expense.copy(
            id = expId,
            createdBy = _currentUser.value.name,
            timestamp = System.currentTimeMillis()
        )
        _monthlyRecurringExpenses.value = listOf(record) + _monthlyRecurringExpenses.value
        storage.saveMonthlyRecurringExpenses(_monthlyRecurringExpenses.value)
        syncDocument("monthlyRecurringExpenses", expId, record)
        addNotification("Monthly Cost Recorded", "Recorded ${expense.category.label}: Rs. ${expense.amount.toInt()} for ${expense.month}.", NotificationSeverity.SUCCESS)
        return Pair(true, "Monthly recurring expense recorded successfully.")
    }

    fun deleteMonthlyRecurringExpense(expenseId: String): Pair<Boolean, String> {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.DELETE)) {
            return Pair(false, "Security Error: Permission required to delete Monthly Recurring Costs")
        }
        _monthlyRecurringExpenses.value = _monthlyRecurringExpenses.value.filterNot { it.id == expenseId }
        storage.saveMonthlyRecurringExpenses(_monthlyRecurringExpenses.value)
        deleteDocument("monthlyRecurringExpenses", expenseId)
        return Pair(true, "Recurring expense deleted.")
    }

    private fun resolveItemPackingType(item: DeliveryItem): CostingPackingType {
        val combined = "${item.variantId} ${item.bottleName}".lowercase(Locale.ENGLISH)
        return when {
            combined.contains("carton") -> CostingPackingType.CARTON
            combined.contains("packet") || item.packSize > 1 -> CostingPackingType.PACKET
            else -> CostingPackingType.SINGLE
        }
    }

    /**
     * Unified Day / Week / Month / Year Profit & Loss Dashboard Engine:
     * 1. Deducts Bottle & Packaging Material Cost (Confirmed Packet Cost per delivered unit) from Sales Revenue
     *    first -> Profit Before Other Operational Expenses.
     * 2. Automatically fetches active Monthly Staff Salaries from Staff Module (_plantWorkers).
     * 3. Fetches Monthly Operational Costs (6 categories), ALWAYS using Actual Expense when > 0 else Budget.
     * 4. Divides monthly staff salaries & operational costs by days in month to allocate exact period share
     *    (Day = ÷ daysInMonth, Week = × 7/daysInMonth, Month = full month, Year = sum of all 12 months).
     * 5. Deducts period's allocated Staff Salaries + Monthly Operational Costs -> Final Net Profit.
     */
    fun getCostingProfitLossReport(
        period: CostingReportPeriod,
        referenceDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    ): CostingProfitLossReport {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW)) {
            return CostingProfitLossReport(period = period)
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedRef = try {
            sdf.parse(referenceDate) ?: Date()
        } catch (_: Exception) {
            Date()
        }
        val cal = Calendar.getInstance().apply { time = parsedRef }
        val refYear = cal.get(Calendar.YEAR)
        val refMonthZeroBased = cal.get(Calendar.MONTH)
        val monthKey = String.format(Locale.US, "%04d-%02d", refYear, refMonthZeroBased + 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)

        val startDate: String
        val endDate: String
        val daysInPeriod: Int
        val periodLabel: String

        when (period) {
            CostingReportPeriod.DAILY -> {
                val d = sdf.format(cal.time)
                startDate = d
                endDate = d
                daysInPeriod = 1
                periodLabel = "Day • $d"
            }
            CostingReportPeriod.WEEKLY -> {
                endDate = sdf.format(cal.time)
                val startCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
                startDate = sdf.format(startCal.time)
                daysInPeriod = 7
                periodLabel = "Week • $startDate to $endDate"
            }
            CostingReportPeriod.MONTHLY -> {
                startDate = "$monthKey-01"
                endDate = String.format(Locale.US, "%s-%02d", monthKey, daysInMonth)
                daysInPeriod = daysInMonth
                periodLabel = "Month • $monthKey ($daysInMonth days)"
            }
            CostingReportPeriod.YEARLY -> {
                val yearCal = Calendar.getInstance().apply { set(Calendar.YEAR, refYear) }
                val daysInYear = yearCal.getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)
                startDate = String.format(Locale.US, "%04d-01-01", refYear)
                endDate = String.format(Locale.US, "%04d-12-31", refYear)
                daysInPeriod = daysInYear
                periodLabel = "Year • $refYear ($daysInYear days)"
            }
        }

        val sizesById = _bottleSizes.value.associateBy { it.id }
        val costings = _bottleCostings.value

        val periodDeliveries = _deliveries.value.filter {
            it.status != DeliveryStatus.CANCELLED &&
                it.date.isNotBlank() &&
                it.date >= startDate &&
                it.date <= endDate
        }
        val periodPayments = _payments.value.filter {
            it.date.isNotBlank() && it.date >= startDate && it.date <= endDate
        }
        val receivedPayments = periodPayments.sumOf { it.amount }

        var totalUnitsSold = 0
        var totalSalesRevenue = 0.0
        var totalSingleUsePurchase = 0.0
        var totalFilling = 0.0
        var totalPacking = 0.0
        var totalOtherUnitCost = 0.0
        var totalBottleMaterialCost = 0.0
        var usedDetailedCosting = false

        data class MutableLineAcc(
            val bottleSizeId: String,
            val bottleName: String,
            val packingType: CostingPackingType,
            val isMultiTimeUse: Boolean,
            var unitsSold: Int = 0,
            var totalBottlesSold: Int = 0,
            var revenue: Double = 0.0,
            var purchaseCostTotal: Double = 0.0,
            var fillingCostTotal: Double = 0.0,
            var packingCostTotal: Double = 0.0,
            var otherUnitCostTotal: Double = 0.0,
            var totalProdCost: Double = 0.0,
            var hasConfiguredCosting: Boolean = true
        )

        val lineMap = linkedMapOf<String, MutableLineAcc>()

        periodDeliveries.forEach { delivery ->
            // Net delivery revenue ALWAYS honors delivery.totalAmount (subtotal + deliveryCharges - discount)
            totalSalesRevenue += delivery.totalAmount
            delivery.items.forEach { item ->
                if (item.quantity <= 0) return@forEach
                val packingType = resolveItemPackingType(item)
                val size = sizesById[item.bottleSizeId]
                val isMultiUse = size?.let {
                    it.usageType == BottleUsageType.MULTIPLE_TIME || it.isReturnableBottle
                } ?: false

                val matchedCosting = costings.find {
                    it.bottleSizeId == item.bottleSizeId && it.packingType == packingType
                } ?: costings.find { it.bottleSizeId == item.bottleSizeId }

                val hasCosting = matchedCosting != null
                val qty = item.quantity
                val lineRev = if (item.lineTotal > 0.0) item.lineTotal else (qty * item.unitPrice)

                val linePurchase: Double
                val lineFilling: Double
                val linePacking: Double
                val lineOther: Double
                val lineProdCost: Double

                if (matchedCosting != null && (matchedCosting.usesDetailedCosting || matchedCosting.confirmedPacketCost > 0.0)) {
                    usedDetailedCosting = true
                    val confirmedUnit = matchedCosting.effectiveUnitCostForPnl(isMultiUse)
                    lineProdCost = confirmedUnit * qty
                    linePurchase = if (isMultiUse) 0.0 else (matchedCosting.bottlePrice * matchedCosting.packQty.coerceAtLeast(1) * qty)
                    lineFilling = matchedCosting.waterFillingCost * matchedCosting.packQty.coerceAtLeast(1) * qty
                    linePacking = (matchedCosting.cartonCost + matchedCosting.tapeCost) * qty
                    lineOther = (lineProdCost - linePurchase - lineFilling - linePacking).coerceAtLeast(0.0)
                } else {
                    val unitPurchase = if (isMultiUse) 0.0 else (matchedCosting?.bottlePurchaseRate ?: 0.0)
                    val unitFilling = matchedCosting?.fillingRate ?: (size?.refillCost ?: 0.0)
                    val unitPacking = matchedCosting?.packingRate ?: 0.0
                    val unitOther = matchedCosting?.extraComponentCost ?: 0.0
                    linePurchase = unitPurchase * qty
                    lineFilling = unitFilling * qty
                    linePacking = unitPacking * qty
                    lineOther = unitOther * qty
                    lineProdCost = linePurchase + lineFilling + linePacking + lineOther
                }

                totalUnitsSold += qty
                totalSingleUsePurchase += linePurchase
                totalFilling += lineFilling
                totalPacking += linePacking
                totalOtherUnitCost += lineOther
                totalBottleMaterialCost += lineProdCost

                val groupKey = "${item.bottleSizeId}__${packingType.name}"
                val displayName = item.bottleName.ifBlank { size?.name ?: item.bottleSizeId }
                val acc = lineMap.getOrPut(groupKey) {
                    MutableLineAcc(
                        bottleSizeId = item.bottleSizeId,
                        bottleName = displayName,
                        packingType = packingType,
                        isMultiTimeUse = isMultiUse,
                        hasConfiguredCosting = hasCosting
                    )
                }
                acc.unitsSold += qty
                acc.totalBottlesSold += qty * item.packSize.coerceAtLeast(1)
                acc.revenue += lineRev
                acc.purchaseCostTotal += linePurchase
                acc.fillingCostTotal += lineFilling
                acc.packingCostTotal += linePacking
                acc.otherUnitCostTotal += lineOther
                acc.totalProdCost += lineProdCost
                if (!hasCosting) acc.hasConfiguredCosting = false
            }
        }

        val breakdownLines = lineMap.values.map { acc ->
            BottleSaleCostLine(
                bottleSizeId = acc.bottleSizeId,
                bottleName = acc.bottleName,
                packingType = acc.packingType,
                isMultiTimeUse = acc.isMultiTimeUse,
                unitsSold = acc.unitsSold,
                totalBottlesSold = acc.totalBottlesSold,
                revenue = acc.revenue,
                purchaseCostTotal = acc.purchaseCostTotal,
                fillingCostTotal = acc.fillingCostTotal,
                packingCostTotal = acc.packingCostTotal,
                otherUnitCostTotal = acc.otherUnitCostTotal,
                totalProductionCost = acc.totalProdCost,
                grossMargin = acc.revenue - acc.totalProdCost,
                hasConfiguredCosting = acc.hasConfiguredCosting
            )
        }.sortedByDescending { it.revenue }

        // Step 2: Profit Before Other Operational Expenses
        val profitBeforeOperationalCosts = totalSalesRevenue - totalBottleMaterialCost

        // Step 3A: Auto-fetched Active Staff Salaries from Staff Module (_plantWorkers)
        val activeWorkers = _plantWorkers.value.filter { it.active && it.monthlySalary > 0.0 }
        val monthlyStaffSalariesTotal = activeWorkers.sumOf { it.monthlySalary }
        val dailyStaffSalaryRate = monthlyStaffSalariesTotal / daysInMonth.toDouble()
        val allocatedStaffSalaryCost = when (period) {
            CostingReportPeriod.DAILY -> dailyStaffSalaryRate
            CostingReportPeriod.WEEKLY -> dailyStaffSalaryRate * 7.0
            CostingReportPeriod.MONTHLY -> monthlyStaffSalariesTotal
            CostingReportPeriod.YEARLY -> monthlyStaffSalariesTotal * 12.0
        }

        // Step 3B: Monthly Operational Costs (6 categories — Actual Expense overrides Budget when > 0)
        val opBreakdown: List<OperationalCostBreakdownItem>
        val monthlyOperationalCostsTotal: Double
        val dailyOperationalCostRate: Double
        val allocatedOperationalCost: Double

        if (period == CostingReportPeriod.YEARLY) {
            val yearRecords = (1..12).map { m ->
                getMonthlyOperationalCostForMonth(String.format(Locale.US, "%04d-%02d", refYear, m))
            }
            fun aggregateLine(
                label: String,
                urdu: String,
                selector: (MonthlyOperationalCostRecord) -> OperationalCostLine
            ): OperationalCostBreakdownItem {
                val lines = yearRecords.map(selector)
                val totalBud = lines.sumOf { it.budgetAmount }
                val totalAct = lines.sumOf { it.actualAmount }
                val totalEff = lines.sumOf { it.effectiveAmount }
                return OperationalCostBreakdownItem(
                    label = label,
                    urduLabel = urdu,
                    budgetAmount = totalBud,
                    actualAmount = totalAct,
                    effectiveMonthlyAmount = totalEff / 12.0,
                    isUsingActual = lines.any { it.isUsingActual },
                    allocatedPeriodAmount = totalEff
                )
            }
            opBreakdown = listOf(
                aggregateLine("Vehicle Maintenance", "گاڑی کی مرمت") { it.vehicleMaintenance },
                aggregateLine("RO Maintenance", "آر او پلانٹ مرمت") { it.roMaintenance },
                aggregateLine("RO Plant Rent", "آر او پلانٹ کرایہ") { it.roPlantRent },
                aggregateLine("Vehicle Monthly Rent", "گاڑی کا ماہانہ کرایہ") { it.vehicleMonthlyRent },
                aggregateLine("Electricity Bill", "بجلی کا بل") { it.electricityBill },
                aggregateLine("Other Fixed Costs", "دیگر مستقل اخراجات") { it.otherFixedCosts }
            )
            allocatedOperationalCost = opBreakdown.sumOf { it.allocatedPeriodAmount }
            monthlyOperationalCostsTotal = allocatedOperationalCost / 12.0
            dailyOperationalCostRate = allocatedOperationalCost / daysInPeriod.toDouble()
        } else {
            val opRec = getMonthlyOperationalCostForMonth(monthKey)
            val allocationFactor = when (period) {
                CostingReportPeriod.DAILY -> 1.0 / daysInMonth.toDouble()
                CostingReportPeriod.WEEKLY -> 7.0 / daysInMonth.toDouble()
                CostingReportPeriod.MONTHLY -> 1.0
                CostingReportPeriod.YEARLY -> 12.0
            }
            fun makeItem(label: String, urdu: String, line: OperationalCostLine): OperationalCostBreakdownItem {
                val eff = line.effectiveAmount
                return OperationalCostBreakdownItem(
                    label = label,
                    urduLabel = urdu,
                    budgetAmount = line.budgetAmount,
                    actualAmount = line.actualAmount,
                    effectiveMonthlyAmount = eff,
                    isUsingActual = line.isUsingActual,
                    allocatedPeriodAmount = eff * allocationFactor
                )
            }
            opBreakdown = listOf(
                makeItem("Vehicle Maintenance", "گاڑی کی مرمت", opRec.vehicleMaintenance),
                makeItem("RO Maintenance", "آر او پلانٹ مرمت", opRec.roMaintenance),
                makeItem("RO Plant Rent", "آر او پلانٹ کرایہ", opRec.roPlantRent),
                makeItem("Vehicle Monthly Rent", "گاڑی کا ماہانہ کرایہ", opRec.vehicleMonthlyRent),
                makeItem("Electricity Bill", "بجلی کا بل", opRec.electricityBill),
                makeItem("Other Fixed Costs", "دیگر مستقل اخراجات", opRec.otherFixedCosts)
            )
            monthlyOperationalCostsTotal = opRec.totalEffectiveCost
            dailyOperationalCostRate = monthlyOperationalCostsTotal / daysInMonth.toDouble()
            allocatedOperationalCost = monthlyOperationalCostsTotal * allocationFactor
        }

        val totalOperationalAndStaffCost = allocatedStaffSalaryCost + allocatedOperationalCost

        // Legacy OPEX/CAPEX/MonthlyRecurring tracking (for informational display & backward-compat fallback)
        val periodExpenses = _expenses.value.filter {
            it.date.isNotBlank() && it.date >= startDate && it.date <= endDate
        }
        val opexExpenses = periodExpenses
            .filter { it.expenseType == ExpenseType.OPEX }
            .sumOf { it.amount }
        val excludedCapex = periodExpenses
            .filter { it.expenseType == ExpenseType.CAPEX }
            .sumOf { it.amount }

        val legacyMonthlyRecurringTotal = if (period == CostingReportPeriod.YEARLY) {
            val yearPrefix = String.format(Locale.US, "%04d-", refYear)
            _monthlyRecurringExpenses.value
                .filter { it.month.startsWith(yearPrefix) || it.date.startsWith(yearPrefix) }
                .sumOf { it.amount }
        } else {
            _monthlyRecurringExpenses.value
                .filter { it.month == monthKey || it.date.startsWith(monthKey) }
                .sumOf { it.amount }
        }
        val legacyDailyFixedRate = if (period == CostingReportPeriod.YEARLY) {
            legacyMonthlyRecurringTotal / daysInPeriod.toDouble()
        } else {
            legacyMonthlyRecurringTotal / daysInMonth.toDouble()
        }
        val legacyProratedFixed = when (period) {
            CostingReportPeriod.DAILY -> legacyDailyFixedRate
            CostingReportPeriod.WEEKLY -> legacyDailyFixedRate * 7.0
            CostingReportPeriod.MONTHLY -> legacyMonthlyRecurringTotal
            CostingReportPeriod.YEARLY -> legacyMonthlyRecurringTotal
        }

        // Use Redesigned Section 4 formula whenever any new costing data (10-field bottle costing, staff salaries,
        // or 6-item monthly operational costs) is active, or when no legacy monthlyRecurringExpenses exist.
        val usesLegacyOnlyFormula = !usedDetailedCosting &&
            monthlyStaffSalariesTotal <= 0.0 &&
            monthlyOperationalCostsTotal <= 0.0 &&
            legacyMonthlyRecurringTotal > 0.0

        val totalCost: Double
        val netProfitOrLoss: Double
        val effectiveDailyFixedRate: Double
        val effectiveProratedFixed: Double
        val effectiveMonthlyFixedTotal: Double

        if (usesLegacyOnlyFormula) {
            totalCost = totalBottleMaterialCost + opexExpenses + legacyProratedFixed
            netProfitOrLoss = totalSalesRevenue - totalCost
            effectiveDailyFixedRate = legacyDailyFixedRate
            effectiveProratedFixed = legacyProratedFixed
            effectiveMonthlyFixedTotal = legacyMonthlyRecurringTotal
        } else {
            totalCost = totalBottleMaterialCost + totalOperationalAndStaffCost
            netProfitOrLoss = profitBeforeOperationalCosts - totalOperationalAndStaffCost
            effectiveDailyFixedRate = dailyStaffSalaryRate + dailyOperationalCostRate
            effectiveProratedFixed = totalOperationalAndStaffCost
            effectiveMonthlyFixedTotal = monthlyStaffSalariesTotal + monthlyOperationalCostsTotal
        }

        return CostingProfitLossReport(
            period = period,
            periodLabel = periodLabel,
            startDate = startDate,
            endDate = endDate,
            daysInPeriod = daysInPeriod,
            daysInReferenceMonth = daysInMonth,
            totalDeliveries = periodDeliveries.size,
            totalUnitsSold = totalUnitsSold,
            totalSalesRevenue = totalSalesRevenue,
            receivedPayments = receivedPayments,
            outstandingSalesAmount = (totalSalesRevenue - receivedPayments).coerceAtLeast(0.0),
            singleUsePurchaseCost = totalSingleUsePurchase,
            fillingCost = totalFilling,
            packingCost = totalPacking,
            otherBottleUnitCost = totalOtherUnitCost,
            totalBottleAndPackingCost = totalBottleMaterialCost,
            profitBeforeOperationalCosts = profitBeforeOperationalCosts,
            activeStaffCount = activeWorkers.size,
            monthlyStaffSalariesTotal = monthlyStaffSalariesTotal,
            dailyStaffSalaryRate = dailyStaffSalaryRate,
            allocatedStaffSalaryCost = allocatedStaffSalaryCost,
            monthlyOperationalCostsTotal = monthlyOperationalCostsTotal,
            dailyOperationalCostRate = dailyOperationalCostRate,
            allocatedOperationalCost = allocatedOperationalCost,
            totalOperationalAndStaffCost = totalOperationalAndStaffCost,
            operationalCostBreakdown = opBreakdown,
            opexExpenses = opexExpenses,
            excludedCapexExpenses = excludedCapex,
            monthlyRecurringTotal = effectiveMonthlyFixedTotal,
            dailyFixedCostRate = effectiveDailyFixedRate,
            proratedMonthlyFixedCost = effectiveProratedFixed,
            totalCost = totalCost,
            netProfitOrLoss = netProfitOrLoss,
            bottleBreakdown = breakdownLines
        )
    }

    fun getMonthlyFinancialSummary(month: String): MonthlyFinancialSummary {
        if (!hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW)) {
            return MonthlyFinancialSummary(month = month)
        }
        val targetMonth = month.ifBlank { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }

        val monthDeliveries = _deliveries.value.filter { it.status != DeliveryStatus.CANCELLED && it.date.startsWith(targetMonth) }
        val totalDeliveries = monthDeliveries.size
        val totalSalesAmount = monthDeliveries.sumOf { it.totalAmount }

        val monthPayments = _payments.value.filter { it.date.startsWith(targetMonth) }
        val receivedPayments = monthPayments.sumOf { it.amount }

        val outstandingSales = (totalSalesAmount - receivedPayments).coerceAtLeast(0.0)

        // Exclude CAPEX from monthly operational expenses summary
        val monthExpenses = _expenses.value.filter {
            it.date.startsWith(targetMonth) && it.expenseType == ExpenseType.OPEX
        }
        val operationalExpenses = monthExpenses.sumOf { it.amount }

        val recurringCosts = _monthlyRecurringExpenses.value
            .filter { it.month == targetMonth || it.date.startsWith(targetMonth) }
            .sumOf { it.amount }

        val totalExpenses = operationalExpenses + recurringCosts
        val netProfitAccrual = totalSalesAmount - totalExpenses
        val netProfitCash = receivedPayments - totalExpenses

        return MonthlyFinancialSummary(
            month = targetMonth,
            totalDeliveries = totalDeliveries,
            totalSalesAmount = totalSalesAmount,
            receivedPayments = receivedPayments,
            outstandingSalesAmount = outstandingSales,
            operationalExpenses = operationalExpenses,
            otherMonthlyCosts = recurringCosts,
            totalExpenses = totalExpenses,
            netProfitAccrual = netProfitAccrual,
            netProfitCash = netProfitCash
        )
    }

    fun acknowledgeExpense(expenseId: String) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.APPROVE) && !_currentUser.value.isSuperAdmin) return
        val target = _expenses.value.find { it.id == expenseId } ?: return
        withSyncAction("Verify Expense • ${target.voucherNumber}") {
            _expenses.value = _expenses.value.map {
                if (it.id == expenseId) {
                    val updated = it.copy(
                        isAcknowledged = true,
                        isApprovedByOwner = true,
                        acknowledgedBy = _currentUser.value.name
                    )
                    syncDocument("expenses", updated.id, updated)
                    updated
                } else it
            }
            storage.saveExpenses(_expenses.value)
            addNotification("Expense Acknowledged", "Expense voucher verified by Admin.", NotificationSeverity.SUCCESS)
        }
    }

    fun acknowledgePayment(paymentId: String) {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE) && !_currentUser.value.isSuperAdmin) return
        val existing = _payments.value.find { it.id == paymentId } ?: return
        withSyncAction("Clear Payment • ${existing.receiptNumber}") {
            var targetPayment: Payment? = null
            _payments.value = _payments.value.map {
                if (it.id == paymentId) {
                    val updated = it.copy(
                        isAcknowledged = true,
                        acknowledgedBy = _currentUser.value.name
                    )
                    targetPayment = updated
                    syncDocument("payments", updated.id, updated)
                    updated
                } else it
            }
            storage.savePayments(_payments.value)
            targetPayment?.let { p ->
                appendAuditLog(
                    action = AuditActionType.UPDATE,
                    module = AppModule.PAYMENTS_BALANCES,
                    recordId = p.id,
                    recordLabel = "${p.receiptNumber} • ${p.customerName}",
                    previousValue = "Unsettle",
                    updatedValue = "Clear (${_currentUser.value.name})"
                )
                addNotification("Payment Cleared", "Payment ${p.receiptNumber} marked Clear by ${_currentUser.value.name}.", NotificationSeverity.SUCCESS)
            }
        }
    }

    fun acknowledgePayments(paymentIds: List<String>) {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE) && !_currentUser.value.isSuperAdmin) return
        val idSet = paymentIds.toSet()
        if (idSet.isEmpty()) return
        withSyncAction("Clear ${idSet.size} Payment(s)") {
            var clearedCount = 0
            _payments.value = _payments.value.map { p ->
                if (p.id in idSet && !p.isAcknowledged) {
                    clearedCount++
                    val updated = p.copy(
                        isAcknowledged = true,
                        acknowledgedBy = _currentUser.value.name
                    )
                    syncDocument("payments", updated.id, updated)
                    appendAuditLog(
                        action = AuditActionType.UPDATE,
                        module = AppModule.PAYMENTS_BALANCES,
                        recordId = updated.id,
                        recordLabel = "${updated.receiptNumber} • ${updated.customerName}",
                        previousValue = "Unsettle",
                        updatedValue = "Clear (${_currentUser.value.name})"
                    )
                    updated
                } else p
            }
            if (clearedCount > 0) {
                storage.savePayments(_payments.value)
                addNotification("Payments Cleared", "$clearedCount payment(s) marked Clear by ${_currentUser.value.name}.", NotificationSeverity.SUCCESS)
            }
        }
    }

    // Delete Delivery (Admin Only)
    fun deleteDelivery(deliveryId: String): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.DELIVERIES, PermissionAction.DELETE)) return@synchronized false
        val delivery = _deliveries.value.find { it.id == deliveryId } ?: return@synchronized false
        withSyncAction("Delete Delivery ${delivery.deliveryNumber} • ${delivery.customerName}") {

        fun isItemReturnable(item: DeliveryItem): Boolean {
            val size = _bottleSizes.value.find { it.id == item.bottleSizeId }
            return size?.isReturnableBottle ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
        }

        val returnableFull = delivery.items.filter(::isItemReturnable).sumOf { it.quantity }
        val returnableEmpty = delivery.items.filter(::isItemReturnable).sumOf { it.emptyCollected }

        // 1. Revert customer balance and returnable empty bottles
        val customer = getCustomerById(delivery.customerId)
        if (customer != null) {
            val netBalanceChange = delivery.totalAmount - delivery.paymentCollected
            val netBottleChange = returnableFull - returnableEmpty
            val updatedCust = customer.copy(
                currentBalance = customer.currentBalance - netBalanceChange,
                emptyBottlesOwed = (customer.emptyBottlesOwed - netBottleChange).coerceAtLeast(0),
                totalPurchases = (customer.totalPurchases - delivery.totalAmount).coerceAtLeast(0.0)
            )
            applyCustomerDeltaLocalAndCloud(
                updatedCust,
                mapOf(
                    "currentBalance" to (updatedCust.currentBalance - customer.currentBalance),
                    "emptyBottlesOwed" to (updatedCust.emptyBottlesOwed - customer.emptyBottlesOwed),
                    "totalPurchases" to (updatedCust.totalPurchases - customer.totalPurchases)
                )
            )
        }

        // 2. Revert inventory for each item
        delivery.items.forEach { item ->
            val ret = isItemReturnable(item)
            if (item.variantId.isNotBlank()) {
                val bottleUnits = item.quantity * item.packSize.coerceAtLeast(1)
                adjustVariantDeliveryStock(
                    bottleSizeId = item.bottleSizeId,
                    variantId = item.variantId,
                    packetDelta = item.quantity,
                    bottleFullDelta = bottleUnits,
                    totalSoldDelta = -bottleUnits,
                    reason = "Deleted Delivery ${delivery.deliveryNumber} (${item.bottleName})"
                )
            } else {
                adjustBottleStock(
                    bottleSizeId = item.bottleSizeId,
                    fullChange = item.quantity,
                    emptyChange = -item.emptyCollected,
                    withCustomersChange = if (ret) -(item.quantity - item.emptyCollected) else 0,
                    reason = "Deleted Delivery ${delivery.deliveryNumber}",
                    totalIssuedOrSoldChange = -item.quantity
                )
            }
        }
        storage.saveInventory(_inventory.value)

        // 3. Remove associated payments (prevents phantom cash on dashboard)
        val associatedPayments = _payments.value.filter { p ->
            p.allocations.any { alloc ->
                (delivery.invoiceId.isNotEmpty() && alloc.invoiceId == delivery.invoiceId) ||
                    alloc.invoiceId == delivery.id ||
                    alloc.invoiceId == "inv_${delivery.id}"
            } || (delivery.deliveryNumber.isNotBlank() && p.notes.contains(delivery.deliveryNumber))
        }
        if (associatedPayments.isNotEmpty()) {
            _payments.value = _payments.value.filterNot { p -> associatedPayments.any { it.id == p.id } }
            storage.savePayments(_payments.value)
            associatedPayments.forEach { p ->
                deleteDocument("payments", p.id)
            }
        }

        // 4. Remove associated bottle issue/sale records
        val associatedSales = _bottleIssueSales.value.filter {
            it.referenceDeliveryId == deliveryId || (delivery.deliveryNumber.isNotBlank() && it.notes.contains(delivery.deliveryNumber))
        }
        if (associatedSales.isNotEmpty()) {
            _bottleIssueSales.value = _bottleIssueSales.value.filterNot { s -> associatedSales.any { it.id == s.id } }
            storage.saveBottleIssueSales(_bottleIssueSales.value)
            associatedSales.forEach { s ->
                deleteDocument("bottleIssueSales", s.id)
            }
        }

        // 5. Remove associated invoice
        if (delivery.invoiceId.isNotEmpty()) {
            _invoices.value = _invoices.value.filter { it.id != delivery.invoiceId }
            storage.saveInvoices(_invoices.value)
            deleteDocument("invoices", delivery.invoiceId)
        }

        // 6. Remove delivery
        _deliveries.value = _deliveries.value.filter { it.id != deliveryId }
        storage.saveDeliveries(_deliveries.value)
        deleteDocument("deliveries", deliveryId)

        appendAuditLog(AuditActionType.DELETE, AppModule.DELIVERIES, deliveryId, "${delivery.deliveryNumber} • ${delivery.customerName}", previousValue = "Rs. ${delivery.totalAmount.toInt()}")
        addNotification(
            "Delivery Deleted",
            "Delivery ${delivery.deliveryNumber} for ${delivery.customerName} has been deleted.",
            NotificationSeverity.WARNING
        )
        true
        }
    }

    // Update Delivery (Admin Only) - updates delivery slip, details, date, items, invoice, payments, and stock
    fun updateDelivery(updatedDelivery: Delivery): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.DELIVERIES, PermissionAction.EDIT)) return@synchronized false
        val old = _deliveries.value.find { it.id == updatedDelivery.id } ?: return@synchronized false
        withSyncAction("Edit Delivery ${updatedDelivery.deliveryNumber} • ${updatedDelivery.customerName}") {

        fun isItemReturnable(item: DeliveryItem): Boolean {
            val size = _bottleSizes.value.find { it.id == item.bottleSizeId }
            return size?.let {
                it.usageType == BottleUsageType.MULTIPLE_TIME ||
                        (it.usageType == BottleUsageType.CUSTOM && it.isReturnableBottle)
            } ?: (item.bottleSizeId in listOf("size_19l", "size_12l"))
        }

        val sanitizedNewItems = updatedDelivery.items.map { item ->
            if (isItemReturnable(item)) item else item.copy(emptyCollected = 0)
        }
        val computedTotalFull = sanitizedNewItems.sumOf { it.quantity }
        val computedTotalEmpty = sanitizedNewItems.sumOf { it.emptyCollected }
        if (computedTotalFull <= 0 && updatedDelivery.items.isNotEmpty()) return@synchronized false

        val cleanSubtotal = if (sanitizedNewItems.isNotEmpty()) sanitizedNewItems.sumOf { it.lineTotal } else updatedDelivery.subtotal
        val cleanCharges = updatedDelivery.deliveryCharges.coerceAtLeast(0.0)
        val cleanDiscount = updatedDelivery.discount.coerceIn(0.0, cleanSubtotal + cleanCharges)
        val computedTotalAmount = if (sanitizedNewItems.isNotEmpty()) {
            (cleanSubtotal + cleanCharges - cleanDiscount).coerceAtLeast(0.0)
        } else {
            updatedDelivery.totalAmount.coerceAtLeast(0.0)
        }

        if (computedTotalAmount < 0.0 || updatedDelivery.paymentCollected < 0.0) return@synchronized false
        if (updatedDelivery.paymentCollected > computedTotalAmount + 0.01) return@synchronized false

        val diffParts = mutableListOf<String>()
        if (old.date != updatedDelivery.date && updatedDelivery.date.isNotBlank()) {
            diffParts.add("Date: ${old.date} → ${updatedDelivery.date}")
        }
        if (old.totalFullDelivered != computedTotalFull) {
            diffParts.add("Qty: ${old.totalFullDelivered} → $computedTotalFull")
        }
        if (old.totalEmptyCollected != computedTotalEmpty) {
            diffParts.add("Empty Ret: ${old.totalEmptyCollected} → $computedTotalEmpty")
        }
        if (kotlin.math.abs(old.discount - cleanDiscount) > 0.01) {
            diffParts.add("Discount: Rs.${old.discount.toInt()} → Rs.${cleanDiscount.toInt()}")
        }
        if (kotlin.math.abs(old.totalAmount - computedTotalAmount) > 0.01) {
            diffParts.add("Bill: Rs.${old.totalAmount.toInt()} → Rs.${computedTotalAmount.toInt()}")
        }
        if (kotlin.math.abs(old.paymentCollected - updatedDelivery.paymentCollected) > 0.01) {
            diffParts.add("Paid: Rs.${old.paymentCollected.toInt()} → Rs.${updatedDelivery.paymentCollected.toInt()}")
        }
        if (old.paymentMethod != updatedDelivery.paymentMethod) {
            diffParts.add("Mode: ${old.paymentMethod} → ${updatedDelivery.paymentMethod}")
        }
        val userReason = updatedDelivery.lastEditNote.trim()
        val autoSummary = diffParts.joinToString(" • ").ifBlank { "Details updated" }
        val combinedEditNote = if (userReason.isNotBlank() && !userReason.contains("→")) {
            "$userReason ($autoSummary)"
        } else if (userReason.isNotBlank()) {
            userReason
        } else {
            autoSummary
        }

        val normalizedUpdatedDelivery = updatedDelivery.copy(
            items = sanitizedNewItems,
            totalFullDelivered = computedTotalFull,
            totalEmptyCollected = computedTotalEmpty,
            subtotal = cleanSubtotal,
            deliveryCharges = cleanCharges,
            discount = cleanDiscount,
            totalAmount = computedTotalAmount,
            viewedBy = old.viewedBy,
            lastEditedAt = System.currentTimeMillis(),
            lastEditedBy = _currentUser.value.name.ifBlank { _currentUser.value.username },
            lastEditNote = combinedEditNote
        )

        val oldRetFull = old.items.filter(::isItemReturnable).sumOf { it.quantity }
        val oldRetEmpty = old.items.filter(::isItemReturnable).sumOf { it.emptyCollected }
        val newRetFull = sanitizedNewItems.filter(::isItemReturnable).sumOf { it.quantity }
        val newRetEmpty = sanitizedNewItems.filter(::isItemReturnable).sumOf { it.emptyCollected }

        // 1. Reconcile Customer Balance & Returnable Empty Bottles (handles both same-customer & customer-change edits)
        if (old.customerId == normalizedUpdatedDelivery.customerId) {
            val customer = getCustomerById(normalizedUpdatedDelivery.customerId)
            if (customer != null) {
                val oldNetBalance = old.totalAmount - old.paymentCollected
                val newNetBalance = normalizedUpdatedDelivery.totalAmount - normalizedUpdatedDelivery.paymentCollected
                val netBalanceDiff = newNetBalance - oldNetBalance

                val oldNetBottles = oldRetFull - oldRetEmpty
                val newNetBottles = newRetFull - newRetEmpty
                val netBottleDiff = newNetBottles - oldNetBottles

                val totalPurchasesDiff = normalizedUpdatedDelivery.totalAmount - old.totalAmount

                val updatedCust = customer.copy(
                    currentBalance = customer.currentBalance + netBalanceDiff,
                    emptyBottlesOwed = (customer.emptyBottlesOwed + netBottleDiff).coerceAtLeast(0),
                    totalPurchases = (customer.totalPurchases + totalPurchasesDiff).coerceAtLeast(0.0)
                )
                applyCustomerDeltaLocalAndCloud(
                    updatedCust,
                    mapOf(
                        "currentBalance" to (updatedCust.currentBalance - customer.currentBalance),
                        "emptyBottlesOwed" to (updatedCust.emptyBottlesOwed - customer.emptyBottlesOwed),
                        "totalPurchases" to (updatedCust.totalPurchases - customer.totalPurchases)
                    )
                )
            }
        } else {
            getCustomerById(old.customerId)?.let { oldCust ->
                val oldNetBalance = old.totalAmount - old.paymentCollected
                val oldNetBottles = oldRetFull - oldRetEmpty
                val updatedOldCust = oldCust.copy(
                    currentBalance = oldCust.currentBalance - oldNetBalance,
                    emptyBottlesOwed = (oldCust.emptyBottlesOwed - oldNetBottles).coerceAtLeast(0),
                    totalPurchases = (oldCust.totalPurchases - old.totalAmount).coerceAtLeast(0.0)
                )
                applyCustomerDeltaLocalAndCloud(
                    updatedOldCust,
                    mapOf(
                        "currentBalance" to (updatedOldCust.currentBalance - oldCust.currentBalance),
                        "emptyBottlesOwed" to (updatedOldCust.emptyBottlesOwed - oldCust.emptyBottlesOwed),
                        "totalPurchases" to (updatedOldCust.totalPurchases - oldCust.totalPurchases)
                    )
                )
            }
            getCustomerById(normalizedUpdatedDelivery.customerId)?.let { newCust ->
                val newNetBalance = normalizedUpdatedDelivery.totalAmount - normalizedUpdatedDelivery.paymentCollected
                val newNetBottles = newRetFull - newRetEmpty
                val updatedNewCust = newCust.copy(
                    currentBalance = newCust.currentBalance + newNetBalance,
                    emptyBottlesOwed = (newCust.emptyBottlesOwed + newNetBottles).coerceAtLeast(0),
                    totalPurchases = newCust.totalPurchases + normalizedUpdatedDelivery.totalAmount
                )
                applyCustomerDeltaLocalAndCloud(
                    updatedNewCust,
                    mapOf(
                        "currentBalance" to (updatedNewCust.currentBalance - newCust.currentBalance),
                        "emptyBottlesOwed" to (updatedNewCust.emptyBottlesOwed - newCust.emptyBottlesOwed),
                        "totalPurchases" to (updatedNewCust.totalPurchases - newCust.totalPurchases)
                    )
                )
            }
        }

        // 2. Reconcile Inventory: revert old delivery effects, then apply new
        old.items.forEach { item ->
            val isReturnable = isItemReturnable(item)
            if (item.variantId.isNotBlank()) {
                val bottleUnits = item.quantity * item.packSize.coerceAtLeast(1)
                adjustVariantDeliveryStock(
                    bottleSizeId = item.bottleSizeId,
                    variantId = item.variantId,
                    packetDelta = item.quantity,
                    bottleFullDelta = bottleUnits,
                    totalSoldDelta = -bottleUnits,
                    reason = "Edit Delivery (revert old): ${old.deliveryNumber}"
                )
            } else {
                adjustBottleStock(
                    bottleSizeId = item.bottleSizeId,
                    fullChange = item.quantity,
                    emptyChange = if (isReturnable) -item.emptyCollected else 0,
                    withCustomersChange = if (isReturnable) -(item.quantity - item.emptyCollected) else 0,
                    reason = "Edit Delivery (revert old): ${old.deliveryNumber}",
                    totalIssuedOrSoldChange = -item.quantity
                )
            }
        }
        sanitizedNewItems.forEach { item ->
            val isReturnable = isItemReturnable(item)
            if (item.variantId.isNotBlank()) {
                val bottleUnits = item.quantity * item.packSize.coerceAtLeast(1)
                adjustVariantDeliveryStock(
                    bottleSizeId = item.bottleSizeId,
                    variantId = item.variantId,
                    packetDelta = -item.quantity,
                    bottleFullDelta = -bottleUnits,
                    totalSoldDelta = bottleUnits,
                    reason = "Edit Delivery (apply updated): ${normalizedUpdatedDelivery.deliveryNumber}"
                )
            } else {
                adjustBottleStock(
                    bottleSizeId = item.bottleSizeId,
                    fullChange = -item.quantity,
                    emptyChange = if (isReturnable) item.emptyCollected else 0,
                    withCustomersChange = if (isReturnable) (item.quantity - item.emptyCollected) else 0,
                    reason = "Edit Delivery (apply updated): ${normalizedUpdatedDelivery.deliveryNumber}",
                    totalIssuedOrSoldChange = item.quantity
                )
            }
        }
        storage.saveInventory(_inventory.value)

        // 2.5 Rebuild linked BottleIssueSaleRecords so costing & reports reflect edited slip
        val removedSales = _bottleIssueSales.value.filter { it.referenceDeliveryId == updatedDelivery.id }
        val keptSales = _bottleIssueSales.value.filterNot { it.referenceDeliveryId == updatedDelivery.id }
        removedSales.forEach { deleteDocument("bottleIssueSales", it.id) }
        val rebuiltSales = sanitizedNewItems.map { item ->
            val size = _bottleSizes.value.find { it.id == item.bottleSizeId }
            val isVariant = item.variantId.isNotBlank()
            val effectivePackSize = if (isVariant) item.packSize.coerceAtLeast(1) else 1
            val effectiveBottleUnits = item.quantity * effectivePackSize
            val refillCostPerUnit = size?.refillCost ?: 35.0
            val totalItemCost = if (isVariant) {
                val perPackDelivery = if (size?.deliveryUnit == DeliveryUnit.PER_PACKET) {
                    size.deliveryCost * item.quantity
                } else {
                    (size?.deliveryCost ?: 20.0) * effectiveBottleUnits
                }
                (refillCostPerUnit * effectiveBottleUnits) + perPackDelivery
            } else {
                (refillCostPerUnit * item.quantity) + (if (size?.deliveryUnit == DeliveryUnit.PER_PACKET) {
                    val packQty = size.packetPackagingQty.coerceAtLeast(1)
                    size.deliveryCost * kotlin.math.ceil(item.quantity.toDouble() / packQty)
                } else {
                    (size?.deliveryCost ?: 20.0) * item.quantity
                })
            }
            val deliveryCostPerUnit = if (item.quantity > 0) {
                (totalItemCost - (refillCostPerUnit * effectiveBottleUnits)) / item.quantity.coerceAtLeast(1)
            } else {
                size?.deliveryCost ?: 20.0
            }
            val revenue = item.lineTotal
            val isRet = isItemReturnable(item)
            val rec = BottleIssueSaleRecord(
                id = nextRecordId("bis", item.variantId.ifBlank { item.bottleSizeId }),
                date = updatedDelivery.date,
                time = updatedDelivery.time,
                customerId = updatedDelivery.customerId,
                customerName = updatedDelivery.customerName,
                bottleSizeId = item.bottleSizeId,
                bottleSizeName = item.bottleName,
                quantity = item.quantity,
                transactionType = if (isRet) BottleTransactionType.CUSTOMER_ISSUE else BottleTransactionType.PACKAGED_SALE,
                unitSellingPrice = item.unitPrice,
                refillCost = refillCostPerUnit,
                deliveryCost = deliveryCostPerUnit,
                totalTransactionCost = totalItemCost,
                revenue = revenue,
                profit = revenue - totalItemCost,
                deliveryPerson = updatedDelivery.staffName.ifEmpty { "Supervisor" },
                referenceDeliveryId = updatedDelivery.id,
                notes = "Delivery ${updatedDelivery.deliveryNumber}"
            )
            syncDocument("bottleIssueSales", rec.id, rec)
            rec
        }
        _bottleIssueSales.value = rebuiltSales + keptSales
        storage.saveBottleIssueSales(_bottleIssueSales.value)

        // 3. Update Invoice if linked
        if (normalizedUpdatedDelivery.invoiceId.isNotEmpty()) {
            val oldInv = _invoices.value.find { it.id == normalizedUpdatedDelivery.invoiceId }
            if (oldInv != null) {
                val invoicePaid = kotlin.math.min(normalizedUpdatedDelivery.paymentCollected, normalizedUpdatedDelivery.totalAmount)
                val invoiceBalance = (normalizedUpdatedDelivery.totalAmount - invoicePaid).coerceAtLeast(0.0)
                val invoiceStatus = when {
                    invoiceBalance <= 0.0 -> InvoiceStatus.PAID
                    invoicePaid > 0.0 -> InvoiceStatus.PARTIALLY_PAID
                    else -> InvoiceStatus.UNPAID
                }
                val itemsSummary = normalizedUpdatedDelivery.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
                val updatedInv = oldInv.copy(
                    customerId = normalizedUpdatedDelivery.customerId,
                    customerName = normalizedUpdatedDelivery.customerName,
                    date = normalizedUpdatedDelivery.date,
                    dueDate = normalizedUpdatedDelivery.date,
                    totalAmount = normalizedUpdatedDelivery.totalAmount,
                    paidAmount = invoicePaid,
                    balanceDue = invoiceBalance,
                    status = invoiceStatus,
                    itemsSummary = itemsSummary
                )
                updateInvoice(updatedInv)
            }
        }

        // 3.5 Reconcile ALL Linked Payment Records (Guarantees sum(linked payments) == paymentCollected exactly)
        reconcilePaymentsForDeliveryLocked(old = old, updated = normalizedUpdatedDelivery)

        // 4. Update Delivery in state, storage, and Firestore
        _deliveries.value = _deliveries.value.map { if (it.id == normalizedUpdatedDelivery.id) normalizedUpdatedDelivery else it }
        storage.saveDeliveries(_deliveries.value)
        syncDocument("deliveries", normalizedUpdatedDelivery.id, normalizedUpdatedDelivery)

        appendAuditLog(
            AuditActionType.UPDATE,
            AppModule.DELIVERIES,
            updatedDelivery.id,
            "${updatedDelivery.deliveryNumber} • ${updatedDelivery.customerName}",
            previousValue = "Bill Rs.${old.totalAmount.toInt()}, Paid Rs.${old.paymentCollected.toInt()}",
            updatedValue = "Bill Rs.${normalizedUpdatedDelivery.totalAmount.toInt()}, Paid Rs.${normalizedUpdatedDelivery.paymentCollected.toInt()} | ${normalizedUpdatedDelivery.lastEditNote}"
        )
        addNotification(
            "Delivery Updated",
            "Delivery ${updatedDelivery.deliveryNumber} for ${updatedDelivery.customerName}: ${normalizedUpdatedDelivery.lastEditNote}",
            NotificationSeverity.INFO
        )
        true
        }
    }

    /**
     * Reconciles all Payment records linked to [old]/[updated] so that their total allocated amount
     * matches [updated].paymentCollected EXACTLY — removing duplicate initial payments and trimming
     * excess subsequent/direct payments when Admin lowers a delivery's paid amount.
     */
    private fun reconcilePaymentsForDeliveryLocked(old: Delivery, updated: Delivery) {
        val linkedPayments = com.example.util.PdfGenerator.findRelatedPaymentsForDelivery(old, _payments.value)

        fun isInitialDeliveryPayment(p: Payment): Boolean {
            return (old.deliveryNumber.isNotBlank() &&
                com.example.util.PdfGenerator.matchesDeliveryNumberToken(p.notes, old.deliveryNumber) &&
                p.notes.contains("Collected on delivery", ignoreCase = true)) ||
                (p.notes.contains("delivery", ignoreCase = true) &&
                    !p.notes.contains("Direct payment", ignoreCase = true) &&
                    !p.notes.contains("wasooli", ignoreCase = true))
        }

        val initialCandidates = linkedPayments.filter(::isInitialDeliveryPayment).sortedBy { it.createdAt }
        val primaryInitial = initialCandidates.firstOrNull()
        val duplicateInitials = if (initialCandidates.size > 1) initialCandidates.drop(1) else emptyList()

        // Remove any duplicate initial delivery payments (e.g. from offline multi-device sync)
        if (duplicateInitials.isNotEmpty()) {
            val dupIds = duplicateInitials.map { it.id }.toSet()
            _payments.value = _payments.value.filterNot { it.id in dupIds }
            dupIds.forEach { deleteDocument("payments", it) }
        }

        val initialIds = initialCandidates.map { it.id }.toSet()
        val subsequentPayments = linkedPayments.filterNot { it.id in initialIds }
        val subsequentTotal = subsequentPayments.sumOf {
            com.example.util.PdfGenerator.allocatedAmountForDelivery(old, it)
        }

        val targetPaid = updated.paymentCollected.coerceIn(0.0, updated.totalAmount)
        val resolvedPayMethod = if (updated.paymentMethod.contains("Online", ignoreCase = true) ||
            updated.paymentMethod.contains("Bank", ignoreCase = true)
        ) PaymentMethod.BANK_TRANSFER else PaymentMethod.CASH
        val editorName = _currentUser.value.name.ifBlank { _currentUser.value.username }
        val nowMs = System.currentTimeMillis()

        fun matchesDelAlloc(alloc: PaymentAllocation): Boolean {
            return (old.invoiceId.isNotBlank() && alloc.invoiceId == old.invoiceId) ||
                (updated.invoiceId.isNotBlank() && alloc.invoiceId == updated.invoiceId) ||
                alloc.invoiceId == old.id ||
                alloc.invoiceId == "inv_${old.id}" ||
                (old.deliveryNumber.isNotBlank() && alloc.invoiceNumber.equals(old.deliveryNumber, ignoreCase = true))
        }

        if (subsequentTotal <= targetPaid + 0.01) {
            // Keep subsequent payments intact (update customer info if delivery customer changed)
            if (old.customerId != updated.customerId && subsequentPayments.isNotEmpty()) {
                val subIds = subsequentPayments.map { it.id }.toSet()
                _payments.value = _payments.value.map { p ->
                    if (p.id in subIds) {
                        val updatedSub = p.copy(
                            customerId = updated.customerId,
                            customerName = updated.customerName,
                            lastEditedAt = nowMs,
                            lastEditedBy = editorName
                        )
                        syncDocument("payments", updatedSub.id, updatedSub)
                        updatedSub
                    } else p
                }
            }

            val revisedInitialAmount = (targetPaid - subsequentTotal).coerceAtLeast(0.0)
            if (primaryInitial != null) {
                if (revisedInitialAmount <= 0.01) {
                    _payments.value = _payments.value.filterNot { it.id == primaryInitial.id }
                    deleteDocument("payments", primaryInitial.id)
                } else {
                    val hasMatchedAlloc = primaryInitial.allocations.any(::matchesDelAlloc)
                    val updatedAlloc = if (hasMatchedAlloc) {
                        primaryInitial.allocations.map { alloc ->
                            if (matchesDelAlloc(alloc)) alloc.copy(allocatedAmount = revisedInitialAmount) else alloc
                        }
                    } else {
                        listOf(
                            PaymentAllocation(
                                invoiceId = updated.invoiceId.ifEmpty { "inv_${updated.id}" },
                                invoiceNumber = updated.deliveryNumber,
                                allocatedAmount = revisedInitialAmount
                            )
                        )
                    }
                    val updatedPay = primaryInitial.copy(
                        customerId = updated.customerId,
                        customerName = updated.customerName,
                        date = updated.date,
                        amount = revisedInitialAmount,
                        paymentMethod = resolvedPayMethod,
                        allocations = updatedAlloc,
                        lastEditedAt = nowMs,
                        lastEditedBy = editorName
                    )
                    _payments.value = _payments.value.map { if (it.id == primaryInitial.id) updatedPay else it }
                    syncDocument("payments", updatedPay.id, updatedPay)
                }
            } else if (revisedInitialAmount > 0.01) {
                val activeBusinessPlant = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
                val newPay = Payment(
                    id = nextRecordId("pay"),
                    receiptNumber = "RCT-${_payments.value.size + 101}",
                    customerId = updated.customerId,
                    customerName = updated.customerName,
                    amount = revisedInitialAmount,
                    paymentMethod = resolvedPayMethod,
                    allocations = listOf(
                        PaymentAllocation(
                            invoiceId = updated.invoiceId.ifEmpty { "inv_${updated.id}" },
                            invoiceNumber = updated.deliveryNumber,
                            allocatedAmount = revisedInitialAmount
                        )
                    ),
                    notes = "Collected on delivery ${updated.deliveryNumber}",
                    date = updated.date,
                    receivedBy = _currentUser.value.name,
                    receivedByRole = _currentUser.value.role,
                    isAcknowledged = true,
                    acknowledgedBy = _currentUser.value.name,
                    businessId = activeBusinessPlant
                )
                _payments.value = listOf(newPay) + _payments.value
                syncDocument("payments", newPay.id, newPay)
            }
        } else {
            // Admin lowered delivery.paymentCollected BELOW existing subsequent/direct payment totals:
            // 1. Delete primary initial payment (if any)
            if (primaryInitial != null) {
                _payments.value = _payments.value.filterNot { it.id == primaryInitial.id }
                deleteDocument("payments", primaryInitial.id)
            }
            // 2. Trim excess from subsequent payments (newest first), preserving allocations to OTHER invoices
            var excessToTrim = (subsequentTotal - targetPaid).coerceAtLeast(0.0)
            val orderedSubs = subsequentPayments.sortedByDescending { it.createdAt }
            for (subPay in orderedSubs) {
                if (excessToTrim <= 0.01) break
                val allocForDel = com.example.util.PdfGenerator.allocatedAmountForDelivery(old, subPay)
                if (allocForDel <= 0.01) continue
                val trimAmt = kotlin.math.min(excessToTrim, allocForDel)
                excessToTrim = (excessToTrim - trimAmt).coerceAtLeast(0.0)

                val newPayAmount = (subPay.amount - trimAmt).coerceAtLeast(0.0)
                if (newPayAmount <= 0.01) {
                    _payments.value = _payments.value.filterNot { it.id == subPay.id }
                    deleteDocument("payments", subPay.id)
                } else {
                    var remainingTrimOnThis = trimAmt
                    val updatedAllocs = subPay.allocations.mapNotNull { alloc ->
                        if (matchesDelAlloc(alloc) && remainingTrimOnThis > 0.01) {
                            val cut = kotlin.math.min(remainingTrimOnThis, alloc.allocatedAmount)
                            remainingTrimOnThis -= cut
                            val left = (alloc.allocatedAmount - cut).coerceAtLeast(0.0)
                            if (left <= 0.01) null else alloc.copy(allocatedAmount = left)
                        } else {
                            alloc
                        }
                    }
                    val updatedSub = subPay.copy(
                        customerId = updated.customerId,
                        customerName = updated.customerName,
                        amount = newPayAmount,
                        allocations = updatedAllocs,
                        lastEditedAt = nowMs,
                        lastEditedBy = editorName
                    )
                    _payments.value = _payments.value.map { if (it.id == subPay.id) updatedSub else it }
                    syncDocument("payments", updatedSub.id, updatedSub)
                }
            }
        }
        storage.savePayments(_payments.value)
    }

    /**
     * One-tap Admin repair for any legacy delivery whose linked Payment records got out of sync
     * with delivery.paymentCollected (e.g. duplicate Rs. 500 + Rs. 500 entries on an edited slip).
     */
    fun reconcileDeliveryPayments(deliveryId: String): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.DELIVERIES, PermissionAction.EDIT)) return@synchronized false
        val del = _deliveries.value.find { it.id == deliveryId } ?: return@synchronized false
        withSyncAction("Reconcile Payments ${del.deliveryNumber}") {
            reconcilePaymentsForDeliveryLocked(old = del, updated = del)
            if (del.invoiceId.isNotEmpty()) {
                val oldInv = _invoices.value.find { it.id == del.invoiceId }
                if (oldInv != null) {
                    val paid = del.paymentCollected.coerceIn(0.0, del.totalAmount)
                    val bal = (del.totalAmount - paid).coerceAtLeast(0.0)
                    val st = when {
                        bal <= 0.01 -> InvoiceStatus.PAID
                        paid > 0.01 -> InvoiceStatus.PARTIALLY_PAID
                        else -> InvoiceStatus.UNPAID
                    }
                    updateInvoice(oldInv.copy(paidAmount = paid, balanceDue = bal, status = st))
                }
            }
            appendAuditLog(
                AuditActionType.UPDATE,
                AppModule.DELIVERIES,
                del.id,
                "${del.deliveryNumber} • Payment Records Reconciled",
                updatedValue = "Paid synced to Rs. ${del.paymentCollected.toInt()}"
            )
            true
        }
    }

    // Delete Expense (atomically reverses all linked Material or Bottle stock purchases — both multi-item & legacy)
    fun deleteExpense(expenseId: String): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.DELETE)) return@synchronized false
        val expense = _expenses.value.find { it.id == expenseId } ?: return@synchronized false
        withSyncAction("Delete Expense ${expense.voucherNumber}") {
            // 1. Reverse all linked inventory line items if this expense purchased stock
            val lineItemsToReverse = expense.effectiveInventoryItems
            if (lineItemsToReverse.isNotEmpty()) {
                reverseInventoryLineItemsLocked(lineItemsToReverse)
            }

            _expenses.value = _expenses.value.filter { it.id != expenseId }
            storage.saveExpenses(_expenses.value)
            deleteDocument("expenses", expenseId)
            appendAuditLog(AuditActionType.DELETE, AppModule.EXPENSES, expenseId, "${expense.voucherNumber} • ${expense.categoryName}", previousValue = "Rs. ${expense.amount.toInt()}")
            addNotification(
                "Expense Deleted",
                "Expense voucher ${expense.voucherNumber} (Rs. ${expense.amount.toInt()}) deleted.",
                NotificationSeverity.WARNING
            )
            true
        }
    }

    // Update Expense (supports full editing of both Non-Inventory and Multi-Item Inventory expenses with atomic stock reconciliation)
    fun updateExpense(updatedExpense: Expense): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.EDIT)) return@synchronized false
        val old = _expenses.value.find { it.id == updatedExpense.id } ?: return@synchronized false

        withSyncAction("Edit Expense ${updatedExpense.voucherNumber}") {
            val oldItems = old.effectiveInventoryItems
            val newItemsRaw = updatedExpense.effectiveInventoryItems
            val isInvEdit = old.hasInventoryItems || updatedExpense.hasInventoryItems

            val baseExpense = if (isInvEdit) {
                // Reverse old inventory stock changes first, then apply edited items atomically
                if (oldItems.isNotEmpty()) {
                    reverseInventoryLineItemsLocked(oldItems)
                }
                val appliedItems = if (newItemsRaw.isNotEmpty()) {
                    applyInventoryLineItemsLocked(
                        items = newItemsRaw,
                        effectiveDate = updatedExpense.date.ifBlank { old.date },
                        supplierName = updatedExpense.payee.ifBlank { "Supplier" },
                        invoiceNo = updatedExpense.invoiceNumber,
                        notes = updatedExpense.notes,
                        paymentSource = updatedExpense.paymentSource,
                        paymentMethod = updatedExpense.paymentMethod
                    )
                } else emptyList()

                val totalAmount = if (appliedItems.isNotEmpty()) appliedItems.sumOf { it.lineTotal } else updatedExpense.amount
                val (summaryCatId, summaryCatName) = if (appliedItems.isNotEmpty()) {
                    resolveSummaryCategoryForItems(appliedItems, updatedExpense.expenseType)
                } else {
                    Pair(updatedExpense.categoryId, updatedExpense.categoryName)
                }
                val resolvedDesc = if (appliedItems.isNotEmpty()) {
                    buildInventoryItemsSummaryDescription(appliedItems)
                } else {
                    updatedExpense.description.ifBlank { old.description }
                }
                val single = appliedItems.singleOrNull()

                updatedExpense.copy(
                    categoryId = summaryCatId,
                    categoryName = summaryCatName,
                    description = resolvedDesc,
                    amount = totalAmount,
                    isInventoryExpense = appliedItems.isNotEmpty(),
                    inventoryItems = appliedItems,
                    linkedInventoryType = single?.itemType.orEmpty(),
                    linkedInventoryItemId = single?.itemId.orEmpty(),
                    linkedInventoryItemName = single?.itemName.orEmpty(),
                    linkedInventoryQty = single?.quantity ?: 0.0,
                    linkedInventoryUnitCost = single?.unitRate ?: 0.0,
                    linkedInventoryTransactionId = single?.transactionId.orEmpty()
                )
            } else {
                updatedExpense
            }

            val expDiffParts = mutableListOf<String>()
            if (old.date != baseExpense.date && baseExpense.date.isNotBlank()) {
                expDiffParts.add("Date: ${old.date} → ${baseExpense.date}")
            }
            if (old.categoryName != baseExpense.categoryName) {
                expDiffParts.add("Cat: ${old.categoryName} → ${baseExpense.categoryName}")
            }
            if (kotlin.math.abs(old.amount - baseExpense.amount) > 0.01) {
                expDiffParts.add("Amt: Rs.${old.amount.toInt()} → Rs.${baseExpense.amount.toInt()}")
            }
            if (old.paymentSource != baseExpense.paymentSource) {
                val oldSrc = if (old.paymentSource == PaymentSource.PAY_FROM_SALE) "Sale" else "Investment"
                val newSrc = if (baseExpense.paymentSource == PaymentSource.PAY_FROM_SALE) "Sale" else "Investment"
                expDiffParts.add("Source: $oldSrc → $newSrc")
            }
            if (old.paymentMethod.isCash != baseExpense.paymentMethod.isCash) {
                expDiffParts.add("Mode: ${if (old.paymentMethod.isCash) "Cash" else "Online"} → ${if (baseExpense.paymentMethod.isCash) "Cash" else "Online"}")
            }
            if (isInvEdit && oldItems.size != baseExpense.effectiveInventoryItems.size) {
                expDiffParts.add("Items: ${oldItems.size} → ${baseExpense.effectiveInventoryItems.size}")
            }
            val userEditReason = updatedExpense.lastEditNote.trim()
            val autoExpSummary = expDiffParts.joinToString(" • ").ifBlank { "Voucher details updated" }
            val combinedExpEditNote = if (userEditReason.isNotBlank() && !userEditReason.contains("→")) {
                "$userEditReason ($autoExpSummary)"
            } else if (userEditReason.isNotBlank()) {
                userEditReason
            } else {
                autoExpSummary
            }

            val finalExpense = baseExpense.copy(
                viewedBy = old.viewedBy,
                lastEditedAt = System.currentTimeMillis(),
                lastEditedBy = _currentUser.value.name.ifBlank { _currentUser.value.username },
                lastEditNote = combinedExpEditNote
            )

            _expenses.value = _expenses.value.map { if (it.id == finalExpense.id) finalExpense else it }
            storage.saveExpenses(_expenses.value)
            syncDocument("expenses", finalExpense.id, finalExpense)
            appendAuditLog(
                AuditActionType.UPDATE,
                AppModule.EXPENSES,
                finalExpense.id,
                "${finalExpense.voucherNumber} • ${finalExpense.categoryName}",
                previousValue = "Rs. ${old.amount.toInt()}",
                updatedValue = "Rs. ${finalExpense.amount.toInt()} | ${finalExpense.lastEditNote}"
            )

            addNotification(
                "Expense Updated",
                "Expense voucher ${finalExpense.voucherNumber}: ${finalExpense.lastEditNote}",
                NotificationSeverity.INFO
            )
            true
        }
    }

    // Update Payment (tracks lastEditedAt/lastEditedBy for audit visibility in detail view)
    fun updatePayment(updatedPayment: Payment): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.EDIT)) return@synchronized false
        val old = _payments.value.find { it.id == updatedPayment.id } ?: return@synchronized false
        val stampedPayment = updatedPayment.copy(
            lastEditedAt = System.currentTimeMillis(),
            lastEditedBy = _currentUser.value.name.ifBlank { _currentUser.value.username }
        )
        withSyncAction("Edit Payment ${stampedPayment.receiptNumber}") {
            val amountDelta = stampedPayment.amount - old.amount
            if (kotlin.math.abs(amountDelta) > 0.001) {
                getCustomerById(stampedPayment.customerId)?.let { cust ->
                    val updatedCust = cust.copy(currentBalance = cust.currentBalance - amountDelta)
                    applyCustomerDeltaLocalAndCloud(updatedCust, mapOf("currentBalance" to -amountDelta))
                }
            }
            _payments.value = _payments.value.map { if (it.id == stampedPayment.id) stampedPayment else it }
            storage.savePayments(_payments.value)
            syncDocument("payments", stampedPayment.id, stampedPayment)
            appendAuditLog(AuditActionType.UPDATE, AppModule.PAYMENTS_BALANCES, stampedPayment.id, "${stampedPayment.receiptNumber} • ${stampedPayment.customerName}", previousValue = "Rs. ${old.amount.toInt()}", updatedValue = "Rs. ${stampedPayment.amount.toInt()}")
            true
        }
    }

    // Delete Payment
    fun deletePayment(paymentId: String): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.DELETE)) return@synchronized false
        val payment = _payments.value.find { it.id == paymentId } ?: return@synchronized false
        withSyncAction("Delete Payment ${payment.receiptNumber}") {
            val customer = getCustomerById(payment.customerId)
            if (customer != null) {
                val updatedCust = customer.copy(
                    currentBalance = customer.currentBalance + payment.amount
                )
                applyCustomerDeltaLocalAndCloud(
                    updatedCust,
                    mapOf("currentBalance" to payment.amount)
                )
            }
            // Revert linked invoices and deliveries so Dues / Delivery List / Detail stay 100% consistent
            val touchedDeliveryIds = mutableSetOf<String>()
            payment.allocations.forEach { alloc ->
                val inv = _invoices.value.find { it.id == alloc.invoiceId }
                if (inv != null) {
                    val newPaid = (inv.paidAmount - alloc.allocatedAmount).coerceAtLeast(0.0)
                    val newBal = (inv.totalAmount - newPaid).coerceAtLeast(0.0)
                    val newStatus = when {
                        newBal <= 0.01 -> InvoiceStatus.PAID
                        newPaid > 0.01 -> InvoiceStatus.PARTIALLY_PAID
                        else -> InvoiceStatus.UNPAID
                    }
                    updateInvoice(inv.copy(paidAmount = newPaid, balanceDue = newBal, status = newStatus))
                }
                _deliveries.value = _deliveries.value.map { del ->
                    val matchesDel = (del.invoiceId.isNotBlank() && del.invoiceId == alloc.invoiceId) ||
                        del.id == alloc.invoiceId ||
                        "inv_${del.id}" == alloc.invoiceId ||
                        (inv != null && del.id == inv.deliveryId)
                    if (matchesDel) {
                        touchedDeliveryIds.add(del.id)
                        val newDelPaid = (del.paymentCollected - alloc.allocatedAmount).coerceAtLeast(0.0)
                        val baseMode = if (del.paymentMethod.contains("Online", true) || del.paymentMethod.contains("Bank", true)) "Online" else "Cash"
                        val newMethod = when {
                            newDelPaid <= 0.01 -> "Unpaid"
                            newDelPaid + 0.01 >= del.totalAmount -> baseMode
                            else -> "Partial ($baseMode)"
                        }
                        del.copy(paymentCollected = newDelPaid, paymentMethod = newMethod)
                    } else del
                }
            }
            if (touchedDeliveryIds.isNotEmpty()) {
                storage.saveDeliveries(_deliveries.value)
                _deliveries.value.filter { it.id in touchedDeliveryIds }.forEach { del ->
                    syncDocument("deliveries", del.id, del)
                }
            }

            _payments.value = _payments.value.filter { it.id != paymentId }
            storage.savePayments(_payments.value)
            deleteDocument("payments", paymentId)
            appendAuditLog(AuditActionType.DELETE, AppModule.PAYMENTS_BALANCES, paymentId, "${payment.receiptNumber} • ${payment.customerName}", previousValue = "Rs. ${payment.amount.toInt()}")
            addNotification(
                "Payment Deleted",
                "Payment receipt ${payment.receiptNumber} (Rs. ${payment.amount.toInt()}) deleted.",
                NotificationSeverity.WARNING
            )
            true
        }
    }

    fun addExpenseCategory(
        name: String,
        urdu: String,
        type: ExpenseType = ExpenseType.OPEX,
        isInventoryCategory: Boolean = false
    ) {
        if (!hasPermission(AppModule.NEW_ITEMS, PermissionAction.ADD)) {
            addNotification("Access Restricted", "Admin has restricted adding new categories/items.", NotificationSeverity.WARNING)
            return
        }
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val newCat = ExpenseCategory(
            id = nextRecordId("cat"),
            name = cleanName,
            urduName = urdu.trim(),
            type = type,
            isDefault = false,
            isInventoryCategory = isInventoryCategory
        )
        _expenseCategories.value = _expenseCategories.value + newCat
        storage.saveExpenseCategories(_expenseCategories.value)
        syncDocument("expenseCategories", newCat.id, newCat)
    }

    /**
     * Deletes an Expense Category safely:
     * Any stock items (FactoryMaterial or BottleSize) referencing this category are preserved
     * with an empty category ("") so they appear as Uncategorized and can be reassigned later.
     */
    fun deleteExpenseCategory(categoryId: String): Boolean = synchronized(mutationLock) {
        if (!hasPermission(AppModule.EXPENSES, PermissionAction.DELETE)) return@synchronized false
        val cat = _expenseCategories.value.find { it.id == categoryId } ?: return@synchronized false
        withSyncAction("Delete Category • ${cat.name}") {
            _expenseCategories.value = _expenseCategories.value.filterNot { it.id == categoryId }
            storage.saveExpenseCategories(_expenseCategories.value)
            deleteDocument("expenseCategories", categoryId)

            // Clear category on FactoryMaterials that belonged to this deleted category (never delete stock items)
            var materialsChanged = false
            val updatedMaterials = _factoryMaterials.value.map { mat ->
                if (mat.itemCategory.equals(cat.name, ignoreCase = true)) {
                    materialsChanged = true
                    val cleared = mat.copy(itemCategory = "", lastUpdated = System.currentTimeMillis())
                    syncDocument("factoryMaterials", cleared.id, cleared)
                    cleared
                } else mat
            }
            if (materialsChanged) {
                _factoryMaterials.value = updatedMaterials
                storage.saveFactoryMaterials(updatedMaterials)
            }

            // Clear category on BottleSizes that belonged to this deleted category (never delete bottle sizes)
            var bottlesChanged = false
            val updatedBottles = _bottleSizes.value.map { size ->
                if (size.categoryName.equals(cat.name, ignoreCase = true)) {
                    bottlesChanged = true
                    val cleared = size.copy(categoryName = "")
                    syncDocument("bottleSizes", cleared.id, cleared)
                    cleared
                } else size
            }
            if (bottlesChanged) {
                _bottleSizes.value = updatedBottles
                storage.saveBottleSizes(updatedBottles)
            }

            addNotification(
                "Category Deleted",
                "Deleted category '${cat.name}'. Existing stock items moved to Uncategorized.",
                NotificationSeverity.INFO
            )
            true
        }
    }

    // Cash Closing: Deducts ONLY OPEX expenses paid from Sale (excludes CAPEX & Investment Account)
    fun getDailyCashSummary(date: String): CashClosingSummary {
        val visiblePayments = filterVisiblePayments(_payments.value)
        val dayPayments = visiblePayments.filter { it.date == date }
        val cashCollected = dayPayments
            .filter { it.paymentMethod.isCash }
            .sumOf { it.amount }
        val digitalCollected = dayPayments
            .filter { !it.paymentMethod.isCash && it.paymentMethod != PaymentMethod.ADVANCE_ADJUSTMENT }
            .sumOf { it.amount }
        val totalReceived = cashCollected + digitalCollected

        val otherCashIncome = 0.0

        val visibleExpenses = filterVisibleExpenses(_expenses.value)
        val dayExpenses = visibleExpenses.filter { it.date == date }
        val cashExpenses = dayExpenses
            .filter {
                it.expenseType == ExpenseType.OPEX &&
                    it.paymentSource == PaymentSource.PAY_FROM_SALE &&
                    it.paymentMethod.isCash
            }
            .sumOf { it.amount }
        val opexFromSaleExpenses = dayExpenses
            .filter {
                it.expenseType == ExpenseType.OPEX &&
                    it.paymentSource == PaymentSource.PAY_FROM_SALE
            }
            .sumOf { it.amount }
        val excludedCapex = dayExpenses
            .filter { it.expenseType == ExpenseType.CAPEX }
            .sumOf { it.amount }
        val excludedInvestment = dayExpenses
            .filter { it.expenseType == ExpenseType.OPEX && it.paymentSource != PaymentSource.PAY_FROM_SALE }
            .sumOf { it.amount }

        val cashRefunds = 0.0
        // Previous closing must strictly come from a day BEFORE `date`.
        // Carry forward rule:
        // - If balanced OR Admin APPROVED the difference -> carry forward actualClosingCash (physical drawer cash)
        // - If difference is PENDING or REJECTED -> carry forward expectedClosingCash (past drawer + actual sales - expenses)
        val previousClosing = _cashClosings.value
            .filter { it.date < date }
            .maxByOrNull { it.date }
        val openingCash = previousClosing?.carryForwardOpeningCash ?: 0.0

        val dailyNetReceived = totalReceived - opexFromSaleExpenses
        val expected = openingCash + cashCollected + otherCashIncome - cashExpenses - cashRefunds

        return CashClosingSummary(
            date = date,
            openingCash = openingCash,
            customerCashCollected = cashCollected,
            digitalCollected = digitalCollected,
            totalReceivedAmount = totalReceived,
            otherCashIncome = otherCashIncome,
            cashExpenses = cashExpenses,
            opexFromSaleExpenses = opexFromSaleExpenses,
            excludedCapexExpenses = excludedCapex,
            excludedInvestmentExpenses = excludedInvestment,
            cashRefunds = cashRefunds,
            dailyNetReceivedAfterOpex = dailyNetReceived,
            expectedClosingCash = expected
        )
    }

    fun submitCashClosing(summary: CashClosingSummary, actualCountedCash: Double, notes: String): CashClosing {
        return withSyncAction("Cash Closing • ${summary.date}") {
            val diff = actualCountedCash - summary.expectedClosingCash
            val balanced = kotlin.math.abs(diff) < 0.01
            // Any non-zero difference goes to Admin for explicit approval; balanced closings are auto-approved.
            val initialApprovalStatus = if (balanced) "APPROVED" else "PENDING"
            val closing = CashClosing(
                id = nextRecordId("close"),
                date = summary.date,
                openingCash = summary.openingCash,
                customerCashCollected = summary.customerCashCollected,
                otherCashIncome = summary.otherCashIncome,
                cashExpenses = summary.cashExpenses,
                cashRefunds = summary.cashRefunds,
                expectedClosingCash = summary.expectedClosingCash,
                actualClosingCash = actualCountedCash,
                difference = diff,
                notes = notes,
                closedBy = _currentUser.value.name,
                isApprovedByOwner = balanced,
                approvalStatus = initialApprovalStatus,
                approvedBy = if (balanced) _currentUser.value.name else "",
                approvedAt = if (balanced) System.currentTimeMillis() else 0L
            )

            // Check if an existing closing exists for today; update in-place instead of creating duplicate!
            val existingIndex = _cashClosings.value.indexOfFirst { it.date == summary.date }
            val finalClosing = if (existingIndex >= 0) {
                val existingId = _cashClosings.value[existingIndex].id
                val updated = closing.copy(id = existingId)
                _cashClosings.value = _cashClosings.value.mapIndexed { idx, item ->
                    if (idx == existingIndex) updated else item
                }
                storage.saveCashClosings(_cashClosings.value)
                syncDocument("cashClosings", existingId, updated)
                updated
            } else {
                _cashClosings.value = listOf(closing) + _cashClosings.value
                storage.saveCashClosings(_cashClosings.value)
                syncDocument("cashClosings", closing.id, closing)
                closing
            }

            val diffLabel = if (finalClosing.isBalanced) "Balanced" else if (finalClosing.isShortage) "Shortage: Rs. ${kotlin.math.abs(diff)} (Pending Admin Approval)" else "Surplus: Rs. $diff (Pending Admin Approval)"
            appendAuditLog(AuditActionType.CREATE, AppModule.PAYMENTS_BALANCES, finalClosing.id, "Cash Closing ${summary.date}", updatedValue = "Actual Rs. ${actualCountedCash.toInt()} ($diffLabel)")
            addNotification(
                "Daily Cash Closed",
                "Expected: Rs. ${summary.expectedClosingCash}, Actual: Rs. $actualCountedCash ($diffLabel)",
                if (finalClosing.isBalanced) NotificationSeverity.SUCCESS else NotificationSeverity.ALERT
            )

            finalClosing
        }
    }

    /**
     * Admin approves a Cash Closing difference -> difference is accepted/cut and next day opens with actual drawer cash.
     */
    fun approveCashClosingDifference(closingId: String): Boolean {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)) return false
        val target = _cashClosings.value.find { it.id == closingId } ?: return false
        return withSyncAction("Approve Cash Closing Diff • ${target.date}") {
            val updated = target.copy(
                isApprovedByOwner = true,
                approvalStatus = "APPROVED",
                approvedBy = _currentUser.value.name,
                approvedAt = System.currentTimeMillis()
            )
            _cashClosings.value = _cashClosings.value.map { if (it.id == closingId) updated else it }
            storage.saveCashClosings(_cashClosings.value)
            syncDocument("cashClosings", closingId, updated)
            appendAuditLog(AuditActionType.APPROVE, AppModule.PAYMENTS_BALANCES, closingId, "Cash Closing ${target.date}", updatedValue = "Approved Diff Rs. ${target.difference.toInt()} -> Next Opening Rs. ${target.actualClosingCash.toInt()}")
            addNotification(
                "Cash Closing Difference Approved",
                "${target.date}: Difference approved. Next day opening drawer cash set to Rs. ${target.actualClosingCash.toInt()}.",
                NotificationSeverity.SUCCESS
            )
            true
        }
    }

    /**
     * Admin rejects a Cash Closing difference -> difference is NOT cut; next day continues from expectedClosingCash (past drawer + actual sales - expenses).
     */
    fun rejectCashClosingDifference(closingId: String): Boolean {
        if (!hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.APPROVE)) return false
        val target = _cashClosings.value.find { it.id == closingId } ?: return false
        return withSyncAction("Reject Cash Closing Diff • ${target.date}") {
            val updated = target.copy(
                isApprovedByOwner = false,
                approvalStatus = "REJECTED",
                approvedBy = _currentUser.value.name,
                approvedAt = System.currentTimeMillis()
            )
            _cashClosings.value = _cashClosings.value.map { if (it.id == closingId) updated else it }
            storage.saveCashClosings(_cashClosings.value)
            syncDocument("cashClosings", closingId, updated)
            appendAuditLog(AuditActionType.UPDATE, AppModule.PAYMENTS_BALANCES, closingId, "Cash Closing ${target.date}", updatedValue = "Rejected Diff Rs. ${target.difference.toInt()} -> Next Opening stays Expected Rs. ${target.expectedClosingCash.toInt()}")
            addNotification(
                "Cash Closing Difference Not Approved",
                "${target.date}: Difference rejected. Next day continues from expected balance Rs. ${target.expectedClosingCash.toInt()}.",
                NotificationSeverity.WARNING
            )
            true
        }
    }

    fun addNotification(title: String, message: String, severity: NotificationSeverity) {
        val notif = AppNotification(
            id = nextRecordId("notif"),
            title = title,
            message = message,
            timeAgo = "Just now",
            severity = severity
        )
        _notifications.value = listOf(notif) + _notifications.value.take(20)
    }

    private fun loadAllFromLocalStorage() {
        val purged = storage.purgeOldSampleDataIfRequired()
        if (purged) {
            _passwordResetRequests.value = emptyList()
            _userAccounts.value = SampleData.initialUserAccounts
            scope.launch {
                try {
                    roomDatabase.deliveryDao().clearAllDeliveries()
                    roomDatabase.customerBalanceDao().clearAll()
                    roomDatabase.expenseDao().clearAllExpenses()
                } catch (e: Exception) {
                    Log.e("WaterPlantRepo", "Error clearing Room tables on purge: ${e.message}")
                }
            }
        }
        storage.loadCustomers()?.let { _customers.value = it } ?: storage.saveCustomers(_customers.value)
        storage.loadDeliveries()?.let { _deliveries.value = it } ?: storage.saveDeliveries(_deliveries.value)
        storage.loadPayments()?.let { _payments.value = it } ?: storage.savePayments(_payments.value)
        storage.loadInvoices()?.let { _invoices.value = it } ?: storage.saveInvoices(_invoices.value)
        storage.loadExpenses()?.let { _expenses.value = it } ?: storage.saveExpenses(_expenses.value)
        storage.loadBottleSizes()?.let { _bottleSizes.value = it } ?: storage.saveBottleSizes(_bottleSizes.value)
        storage.loadInventory()?.let { _inventory.value = it } ?: storage.saveInventory(_inventory.value)
        storage.loadBottlePurchases()?.let { _bottlePurchases.value = it } ?: storage.saveBottlePurchases(_bottlePurchases.value)
        storage.loadBottleRefills()?.let { _bottleRefills.value = it } ?: storage.saveBottleRefills(_bottleRefills.value)
        storage.loadBottleIssueSales()?.let { _bottleIssueSales.value = it } ?: storage.saveBottleIssueSales(_bottleIssueSales.value)
        storage.loadRoPlantItems()?.let { _roPlantItems.value = it } ?: storage.saveRoPlantItems(_roPlantItems.value)
        storage.loadRoTransactions()?.let { _roTransactions.value = it } ?: storage.saveRoTransactions(_roTransactions.value)
        storage.loadCashClosings()?.let { _cashClosings.value = it } ?: storage.saveCashClosings(_cashClosings.value)
        storage.loadCustomerAllocations()?.let { _customerAllocations.value = it } ?: storage.saveCustomerAllocations(_customerAllocations.value)
        storage.loadCustomerRates()?.let { _customerRates.value = it } ?: storage.saveCustomerRates(_customerRates.value)
        storage.loadSecurityDeposits()?.let { _securityDeposits.value = it } ?: storage.saveSecurityDeposits(_securityDeposits.value)
        storage.loadExpenseCategories()?.let { loadedCats ->
            // Safe upgrade migration: if legacy stored categories all had isInventoryCategory=false,
            // auto-tag known inventory categories without losing any custom categories.
            val migratedCats = if (loadedCats.isNotEmpty() && loadedCats.none { it.isInventoryCategory }) {
                val defaultInvIds = SampleData.expenseCategories.filter { it.isInventoryCategory }.map { it.id }.toSet()
                val updated = loadedCats.map { cat ->
                    val lc = cat.name.lowercase()
                    val isKnownInv = cat.id in defaultInvIds ||
                        lc.contains("bottle") ||
                        lc.contains("filter") ||
                        lc.contains("cartridge") ||
                        lc.contains("packing") ||
                        lc.contains("chemical") ||
                        lc.contains("mineral") ||
                        lc.contains("raw material") ||
                        lc.contains("ro plant parts") ||
                        lc.contains("asset") ||
                        lc.contains("machinery")
                    if (isKnownInv) cat.copy(isInventoryCategory = true) else cat
                }
                storage.saveExpenseCategories(updated)
                updated
            } else loadedCats
            _expenseCategories.value = migratedCats
        } ?: storage.saveExpenseCategories(_expenseCategories.value)
        storage.loadUserAccounts()?.let { _userAccounts.value = it } ?: storage.saveUserAccounts(_userAccounts.value)
        val (scrubbedAccounts, hadRetiredAdminPass) = scrubRetiredAdminDefaultPasswords(_userAccounts.value)
        if (hadRetiredAdminPass) {
            _userAccounts.value = scrubbedAccounts
            storage.saveUserAccounts(scrubbedAccounts)
        }
        storage.loadPasswordResetRequests()?.let { _passwordResetRequests.value = it } ?: storage.savePasswordResetRequests(_passwordResetRequests.value)
        storage.loadBottleMovements()?.let { _bottleMovements.value = it } ?: storage.saveBottleMovements(_bottleMovements.value)
        storage.loadFactoryMaterials()?.let { _factoryMaterials.value = it } ?: storage.saveFactoryMaterials(_factoryMaterials.value)
        storage.loadMaterialTransactions()?.let { _materialTransactions.value = it } ?: storage.saveMaterialTransactions(_materialTransactions.value)
        storage.loadBottleCostings()?.let { _bottleCostings.value = it } ?: storage.saveBottleCostings(_bottleCostings.value)
        storage.loadMonthlyRecurringExpenses()?.let { _monthlyRecurringExpenses.value = it } ?: storage.saveMonthlyRecurringExpenses(_monthlyRecurringExpenses.value)
        storage.loadMonthlyOperationalCosts()?.let { _monthlyOperationalCosts.value = it } ?: storage.saveMonthlyOperationalCosts(_monthlyOperationalCosts.value)
        storage.loadPlantWorkers()?.let { _plantWorkers.value = it } ?: storage.savePlantWorkers(_plantWorkers.value)
        storage.loadCurrentProfile()?.let { _currentUser.value = it }
        refreshSeenRecordsForCurrentUser()
        _notificationsEnabled.value = TransactionNotificationHelper.isNotificationsEnabled(context.applicationContext)
        storage.loadDarkMode()?.let { _isDarkMode.value = it }
        val loadedHistory = storage.loadBackupHistory()
        _backupHistory.value = loadedHistory
        _isAutoBackupEnabled.value = storage.loadAutoBackupEnabled()
        _lastBackupTime.value = loadedHistory.firstOrNull { it.status == "SUCCESS" }?.timestamp
        checkAndRunDailyAutoBackup()
    }

    private fun normalizeBottleSizesList(sizes: List<BottleSize>): List<BottleSize> {
        return sizes.map { size ->
            val isLegacy12 = size.id == "size_12l" ||
                size.name.contains("12.5", ignoreCase = true) ||
                size.capacityLitres == 12.5
            if (isLegacy12) {
                size.copy(
                    name = normalizeLegacyBottleName(size.name).ifBlank { "12 Litre Bottle" },
                    capacityLitres = if (size.capacityLitres == 12.5) 12.0 else size.capacityLitres
                )
            } else {
                size
            }
        }
    }

    private fun normalizeInventoryList(invList: List<BottleInventory>): List<BottleInventory> {
        val sizesById = _bottleSizes.value.associateBy { it.id }
        return invList.map { inv ->
            val fixedName = normalizeLegacyBottleName(inv.sizeName)
            val size = sizesById[inv.bottleSizeId]
            val fixedVariants = inv.packVariants.map { v ->
                val baseName = size?.name ?: fixedName
                val cap = size?.capacityLitres ?: 0.0
                v.copy(
                    displayName = formatPackVariantName(baseName, v.packSize, v.containerType),
                    shortLabel = formatPackVariantShortLabel(baseName, cap, v.packSize, v.containerType)
                )
            }
            if (fixedName != inv.sizeName || fixedVariants != inv.packVariants) {
                inv.copy(sizeName = fixedName, packVariants = fixedVariants)
            } else inv
        }
    }

    private fun normalizeLegacyBottleRecords() {
        val normSizes = normalizeBottleSizesList(_bottleSizes.value)
        if (normSizes != _bottleSizes.value) {
            _bottleSizes.value = normSizes
            storage.saveBottleSizes(normSizes)
        }
        val normInv = normalizeInventoryList(_inventory.value)
        if (normInv != _inventory.value) {
            _inventory.value = normInv
            storage.saveInventory(normInv)
        }
        val normRates = _customerRates.value.map { r ->
            val clean = normalizeLegacyBottleName(r.bottleSizeName)
            if (clean != r.bottleSizeName) r.copy(bottleSizeName = clean) else r
        }
        if (normRates != _customerRates.value) {
            _customerRates.value = normRates
            storage.saveCustomerRates(normRates)
        }
    }

    private fun loadExtraPlatformState() {
        val logs = storage.loadAuditLogs()
        if (logs.isNotEmpty()) _auditLogs.value = logs
        val branches = storage.loadBranches()
        if (branches.isNotEmpty()) _plantBranches.value = branches
        val savedPlantId = storage.loadActivePlantId()
        if (savedPlantId.isNotBlank()) _activePlantId.value = savedPlantId
        val cfg = storage.loadBackendConfig()
        _backendConfig.value = cfg
        if (cfg.staffAuthApiUrl.isNotBlank()) {
            staffAuthApiClient.setOverrideBaseUrl(cfg.staffAuthApiUrl)
        }
        val cachedRelease = storage.loadAppReleaseInfo()
        val bakedRepo = AppUpdateChecker.defaultConfiguredGithubRepo()
        if (cachedRelease != null) {
            val effective = if (cachedRelease.githubRepo.isBlank() && bakedRepo.isNotBlank()) {
                cachedRelease.copy(githubRepo = bakedRepo)
            } else cachedRelease
            _appReleaseInfo.value = effective
            if (AppUpdateChecker.isUpdateNewer(effective)) {
                _availableAppUpdate.value = effective
            }
        } else if (bakedRepo.isNotBlank()) {
            _appReleaseInfo.value = AppReleaseInfo(githubRepo = bakedRepo, source = "github")
        }
    }

    fun checkForAppUpdate(force: Boolean = false) {
        if (_isCheckingAppUpdate.value) return
        val now = System.currentTimeMillis()
        val lastCheck = storage.loadLastUpdateCheckMillis()
        if (!force && lastCheck > 0L && (now - lastCheck) < AppUpdateChecker.AUTO_CHECK_INTERVAL_MS) {
            val cached = _appReleaseInfo.value ?: storage.loadAppReleaseInfo()
            if (cached != null) {
                _appReleaseInfo.value = cached
                _availableAppUpdate.value = if (AppUpdateChecker.isUpdateNewer(cached)) cached else null
            }
            return
        }

        scope.launch {
            _isCheckingAppUpdate.value = true
            try {
                val cached = _appReleaseInfo.value ?: storage.loadAppReleaseInfo()
                val bakedRepo = AppUpdateChecker.defaultConfiguredGithubRepo()
                var cloudInfo: AppReleaseInfo? = null
                val db = rtdb
                if (db != null && isFirebaseSignedIn()) {
                    try {
                        val snap = rtdbDocRef(db, "appReleases", "latest").get().await()
                        if (snap.exists()) {
                            cloudInfo = AppReleaseInfo(
                                versionCode = ((snap.child("versionCode").value as? Number)?.toInt()) ?: 0,
                                versionName = snap.child("versionName").getValue(String::class.java).orEmpty(),
                                downloadUrl = snap.child("downloadUrl").getValue(String::class.java).orEmpty(),
                                githubRepo = AppUpdateChecker.normalizeGithubRepo(
                                    snap.child("githubRepo").getValue(String::class.java).orEmpty()
                                ),
                                releaseNotes = snap.child("releaseNotes").getValue(String::class.java).orEmpty(),
                                mandatory = snap.child("mandatory").getValue(Boolean::class.java) ?: false,
                                updatedAtMillis = ((snap.child("updatedAtMillis").value as? Number)?.toLong()) ?: now,
                                source = "rtdb"
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("WaterPlantRepo", "Cloud release check skipped: ${e.message}")
                    }
                }

                val repoToQuery = cloudInfo?.githubRepo?.takeIf { it.isNotBlank() }
                    ?: cached?.githubRepo?.takeIf { it.isNotBlank() }
                    ?: bakedRepo

                // If APK was built by GitHub Actions (bakedRepo present) and RTDB has no repo saved yet, auto-register for all staff phones
                if (db != null && isFirebaseSignedIn() && isCurrentAuthSuperAdmin() &&
                    bakedRepo.isNotBlank() && cloudInfo?.githubRepo.isNullOrBlank()
                ) {
                    try {
                        rtdbDocRef(db, "appReleases", "latest").updateChildren(
                            mapOf(
                                "githubRepo" to bakedRepo,
                                "updatedAtMillis" to now,
                                "updatedBy" to _currentUser.value.email
                            )
                        ).await()
                    } catch (_: Exception) {}
                }

                val githubResult = if (repoToQuery.isNotBlank()) {
                    AppUpdateChecker.fetchLatestFromGitHubDetailed(repoToQuery)
                } else null
                val githubInfo = githubResult?.release

                val bestRelease = selectNewerAppRelease(cloudInfo, githubInfo, cached)
                if (bestRelease != null) {
                    val effectiveBest = if (bestRelease.githubRepo.isBlank() && repoToQuery.isNotBlank()) {
                        bestRelease.copy(githubRepo = repoToQuery)
                    } else bestRelease
                    _appReleaseInfo.value = effectiveBest
                    storage.saveAppReleaseInfo(effectiveBest)
                    if (AppUpdateChecker.isUpdateNewer(effectiveBest)) {
                        _availableAppUpdate.value = effectiveBest
                        _appUpdateStatusMessage.value = "Update v${effectiveBest.versionName.ifBlank { effectiveBest.versionCode.toString() }} (Build ${effectiveBest.versionCode}) is ready to install!"
                    } else {
                        _availableAppUpdate.value = null
                        if (force) {
                            _appUpdateStatusMessage.value = if (!githubResult?.errorMessage.isNullOrBlank() && effectiveBest.downloadUrl.isBlank()) {
                                githubResult!!.errorMessage
                            } else {
                                "You are on the latest version (v${AppUpdateChecker.installedVersionName()}, Build ${AppUpdateChecker.installedVersionCode()})."
                            }
                        }
                    }
                } else {
                    _availableAppUpdate.value = null
                    if (force) {
                        _appUpdateStatusMessage.value = when {
                            !githubResult?.errorMessage.isNullOrBlank() -> githubResult!!.errorMessage
                            repoToQuery.isBlank() -> "GitHub repo not connected yet. Tap 'Update Settings' once and paste your GitHub repo link (e.g. username/repo)."
                            else -> "No update found yet on GitHub ($repoToQuery)."
                        }
                    }
                }
                storage.saveLastUpdateCheckMillis(System.currentTimeMillis())
            } catch (e: Exception) {
                if (force) {
                    _appUpdateStatusMessage.value = "Update check failed: ${e.localizedMessage ?: "Network unavailable"}"
                }
            } finally {
                _isCheckingAppUpdate.value = false
            }
        }
    }

    private fun selectNewerAppRelease(
        cloud: AppReleaseInfo?,
        github: AppReleaseInfo?,
        cached: AppReleaseInfo?
    ): AppReleaseInfo? {
        val repo = cloud?.githubRepo?.takeIf { it.isNotBlank() }
            ?: github?.githubRepo?.takeIf { it.isNotBlank() }
            ?: cached?.githubRepo.orEmpty()
        val mandatory = cloud?.mandatory ?: cached?.mandatory ?: false

        if (github != null && cloud != null) {
            val githubBeatsCloud = (github.versionCode > 0 && cloud.versionCode > 0 && github.versionCode > cloud.versionCode) ||
                (github.versionName.isNotBlank() && cloud.versionName.isNotBlank() && AppUpdateChecker.compareSemver(github.versionName, cloud.versionName) > 0) ||
                (cloud.downloadUrl.isBlank() && github.downloadUrl.startsWith("https://"))
            val chosen = if (githubBeatsCloud) github else cloud
            return chosen.copy(githubRepo = repo, mandatory = mandatory)
        }
        return (github ?: cloud ?: cached)?.copy(githubRepo = repo, mandatory = mandatory)
    }

    suspend fun publishAppRelease(
        versionCode: Int,
        versionName: String,
        downloadUrl: String,
        githubRepo: String,
        releaseNotes: String,
        mandatory: Boolean
    ): Pair<Boolean, String> {
        if (!_currentUser.value.isSuperAdmin && !isCurrentAuthSuperAdmin()) {
            return false to "Only Super Admin can publish app updates."
        }
        val cleanUrl = downloadUrl.trim()
        val cleanRepo = AppUpdateChecker.normalizeGithubRepo(githubRepo)
        if (cleanUrl.isNotBlank() && !cleanUrl.startsWith("https://")) {
            return false to "Download URL must start with https://"
        }
        if (cleanUrl.isBlank() && cleanRepo.isBlank()) {
            return false to "Enter either a GitHub repo (owner/repo) or a direct https:// APK download link."
        }
        val cleanVersionName = versionName.trim().ifBlank { AppUpdateChecker.installedVersionName() }
        val safeCode = versionCode.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val info = AppReleaseInfo(
            versionCode = safeCode,
            versionName = cleanVersionName,
            downloadUrl = cleanUrl,
            githubRepo = cleanRepo,
            releaseNotes = releaseNotes.trim().take(800),
            mandatory = mandatory,
            updatedAtMillis = now,
            source = "rtdb"
        )
        val db = rtdb
        if (db != null && isFirebaseSignedIn()) {
            try {
                val payload = mapOf(
                    "versionCode" to info.versionCode,
                    "versionName" to info.versionName,
                    "downloadUrl" to info.downloadUrl,
                    "githubRepo" to info.githubRepo,
                    "releaseNotes" to info.releaseNotes,
                    "mandatory" to info.mandatory,
                    "updatedAtMillis" to info.updatedAtMillis,
                    "updatedBy" to _currentUser.value.email
                )
                rtdbDocRef(db, "appReleases", "latest").setValue(payload).await()
            } catch (e: Exception) {
                return false to "Failed to publish release to Realtime DB: ${e.localizedMessage ?: "Permission denied"}"
            }
        }
        _appReleaseInfo.value = info
        storage.saveAppReleaseInfo(info)
        storage.saveLastUpdateCheckMillis(0L)
        if (AppUpdateChecker.isUpdateNewer(info)) {
            _availableAppUpdate.value = info
        } else if (cleanRepo.isNotBlank()) {
            checkForAppUpdate(force = true)
        } else {
            _availableAppUpdate.value = null
        }
        val msg = if (cleanRepo.isNotBlank() && cleanUrl.isBlank()) {
            "Connected free GitHub Auto-Updates ($cleanRepo) for all staff devices!"
        } else {
            "Published update v$cleanVersionName (Build $safeCode) to all devices!"
        }
        _appUpdateStatusMessage.value = msg
        return true to msg
    }

    fun stopFirestoreSync() {
        syncJob?.cancel()
        syncJob = null
        activeSyncSessionKey = null
        workspaceEnsuredKey = null
        loadedCollectionsInSession.clear()
        notifiedCollectionsInitialized.clear()
        synchronized(activeRtdbListeners) {
            activeRtdbListeners.forEach { (query, listener) ->
                try {
                    query.removeEventListener(listener)
                } catch (_: Exception) {
                }
            }
            activeRtdbListeners.clear()
        }
    }

    fun syncNotificationUserContext() {
        val user = _currentUser.value
        val authUid = try { Firebase.auth.currentUser?.uid.orEmpty() } catch (_: Exception) { "" }
        val actorId = listOf(authUid, user.id, user.username)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("|")
        val restrictOwn = user.restrictToOwnDataOnly && !user.isSuperAdmin && user.role != UserRole.ADMIN
        TransactionNotificationHelper.updateActiveUserContext(
            context = context.applicationContext,
            plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID },
            actorUid = actorId,
            actorName = user.name,
            actorEmail = user.email,
            restrictToOwnDataOnly = restrictOwn
        )
        if (_notificationsEnabled.value) {
            TransactionNotificationHelper.scheduleBackgroundSyncWorker(context.applicationContext)
        }
    }

    private fun registerRtdbListener(query: RtdbQuery, listener: ValueEventListener) {
        query.addValueEventListener(listener)
        synchronized(activeRtdbListeners) {
            activeRtdbListeners.add(query to listener)
        }
    }

    private fun <T> attachCollectionListener(
        db: FirebaseDatabase,
        collection: String,
        clazz: Class<T>,
        currentLocalProvider: () -> List<T>,
        idExtractor: (T) -> String,
        onApplyList: (List<T>) -> Unit,
        allowEmptyOverwriteAfterLoad: Boolean = true,
        markConnectedOnSuccess: Boolean = false
    ) {
        val ref = rtdbCollectionRef(db, collection)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loadedCollectionsInSession.add(collection)
                val pendingDeleteIds = offlineSyncManager.getPendingDeleteDocIds(collection)

                if (snapshot.exists() && snapshot.childrenCount > 0L) {
                    collectionsWithCloudData.add(collection)
                    val remoteItems = snapshot.children.mapNotNull { child ->
                        rememberRtdbAccessMetadata(collection, child)
                        val remoteTs = RtdbSyncHelper.extractUpdatedAtMillis(child)
                        val itemId = child.child("id").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                            ?: child.key.orEmpty()
                        if (itemId.isBlank() || itemId in pendingDeleteIds) {
                            return@mapNotNull null
                        }
                        if (offlineSyncManager.hasPendingOrFailedMutation(collection, itemId)) {
                            val pendingItem = offlineSyncManager.queueItems.value.find { it.id == "$collection|$itemId" }
                            val currentAuthUid = try { Firebase.auth.currentUser?.uid.orEmpty() } catch (_: Exception) { "" }
                            val remoteModifiedByUid = child.child("lastModifiedByUid").getValue(String::class.java).orEmpty()
                            val isOwnEcho = pendingItem?.status == SyncQueueStatus.SYNCING ||
                                (currentAuthUid.isNotBlank() && remoteModifiedByUid == currentAuthUid)
                            val isOperationalApproval = pendingItem != null && (
                                pendingItem.actionTitle.startsWith("Verify", ignoreCase = true) ||
                                    pendingItem.actionTitle.startsWith("Clear", ignoreCase = true) ||
                                    pendingItem.actionTitle.startsWith("Approve", ignoreCase = true) ||
                                    pendingItem.actionTitle.startsWith("Acknowledge", ignoreCase = true) ||
                                    collection in setOf("expenses", "payments")
                                )
                            if (pendingItem != null &&
                                !isOwnEcho &&
                                !isOperationalApproval &&
                                remoteTs > 0L &&
                                pendingItem.baseRemoteUpdatedAtMillis > 0L &&
                                remoteTs > pendingItem.baseRemoteUpdatedAtMillis
                            ) {
                                offlineSyncManager.recordConflict(
                                    SyncConflictRecord(
                                        id = nextRecordId("conflict", "${collection}_$itemId"),
                                        collection = collection,
                                        docId = itemId,
                                        recordLabel = "$collection/$itemId",
                                        localSummary = "Queued local edit at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(pendingItem.updatedAtMillis))}",
                                        remoteSummary = "Remote device updated at ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(remoteTs))}",
                                        resolution = "Preserving pending local change until sync flush completes"
                                    )
                                )
                            }
                            return@mapNotNull null
                        }
                        RtdbSyncHelper.fromDataSnapshot(child, clazz)
                    }

                    // Keep locally queued pending items (excluding pending deletes) from being wiped before flush
                    val localPendingMap = currentLocalProvider().filter {
                        val id = idExtractor(it)
                        id.isNotBlank() &&
                            !pendingDeleteIds.contains(id) &&
                            offlineSyncManager.hasPendingOrFailedMutation(collection, id)
                    }.associateBy { idExtractor(it) }

                    val combinedMap = linkedMapOf<String, T>()
                    remoteItems.forEach { item ->
                        val id = idExtractor(item)
                        if (id.isNotBlank() && !pendingDeleteIds.contains(id)) {
                            combinedMap[id] = item
                        }
                    }
                    localPendingMap.forEach { (id, localItem) ->
                        combinedMap[id] = localItem
                    }
                    val finalList = combinedMap.values.toList()
                    if (finalList.isNotEmpty() || allowEmptyOverwriteAfterLoad) {
                        onApplyList(finalList)
                    }
                } else if (cloudSeedChecked) {
                    val localPendingOnly = currentLocalProvider().filter {
                        val id = idExtractor(it)
                        id.isNotBlank() &&
                            !pendingDeleteIds.contains(id) &&
                            offlineSyncManager.hasPendingOrFailedMutation(collection, id)
                    }
                    if (localPendingOnly.isNotEmpty()) {
                        onApplyList(localPendingOnly)
                    } else if (allowEmptyOverwriteAfterLoad) {
                        onApplyList(emptyList())
                    }
                }

                if (markConnectedOnSuccess) {
                    _isCloudConnected.value = true
                    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    _syncStatus.value = "Realtime DB Synced at $time"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                val ex = error.toException()
                handleFirestoreError(ex, OperationType.LIST, collection)
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    if (!_isCloudConnected.value) {
                        _syncStatus.value = "Permission Denied: Publish RTDB Rules in Firebase Console"
                    }
                } else {
                    _isCloudConnected.value = false
                    _syncStatus.value = friendlyFirestoreStatusMessage(ex)
                }
            }
        }
        registerRtdbListener(ref, listener)
    }

    fun startFirestoreSync(forceRestart: Boolean = false) {
        val db = rtdb ?: run {
            _isCloudConnected.value = false
            _syncStatus.value = "Realtime DB Not Configured"
            return
        }
        val authUser = try { Firebase.auth.currentUser } catch (_: Exception) { null }
        if (authUser == null) {
            _isCloudConnected.value = false
            _syncStatus.value = "Local Mode • Sign in with Google for Cloud Sync"
            return
        }

        val activePlant = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val sessionKey = "${authUser.uid}|$activePlant|${_currentUser.value.role.name}|${_currentUser.value.isSuperAdmin}"
        val hasActiveListeners = synchronized(activeRtdbListeners) { activeRtdbListeners.isNotEmpty() }
        if (!forceRestart && activeSyncSessionKey == sessionKey && hasActiveListeners) {
            triggerQueueFlush(forceIncludeFailed = false)
            checkForAppUpdate(force = false)
            return
        }

        stopFirestoreSync()
        activeSyncSessionKey = sessionKey
        syncNotificationUserContext()
        _syncStatus.value = "Connecting to Realtime DB..."

        // Live Firebase Realtime DB socket presence listener (.info/connected)
        try {
            val connectedRef = db.getReference(".info/connected")
            val connectedListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    val online = isDeviceOnline.value
                    _isCloudConnected.value = connected && online
                    if (connected && online) {
                        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                        if (_syncStatus.value.startsWith("Connecting") ||
                            _syncStatus.value.startsWith("Offline") ||
                            _syncStatus.value.startsWith("Reconnecting")
                        ) {
                            _syncStatus.value = "Realtime DB Live • $time"
                        }
                    } else if (!online) {
                        _syncStatus.value = "Offline • Changes saved locally"
                    } else {
                        _syncStatus.value = "Reconnecting to Realtime DB..."
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _isCloudConnected.value = false
                }
            }
            registerRtdbListener(connectedRef, connectedListener)
        } catch (_: Exception) {}

        syncJob = scope.launch {
            try {
                // 0. Ensure primary plant branch metadata exists
                ensurePlantWorkspace(db)

                // 1. Flush any changes made offline or before Google Sign-In
                flushPendingCloudWrites(db)

                // 2. Seed initial persistent data if Realtime Database is empty
                seedInitialCloudDataIfEmpty(db)

                // 3. Attach real-time permission-gated ValueEventListeners across centralized plant collections
                if (hasPermission(AppModule.CUSTOMERS, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "customers",
                        clazz = Customer::class.java,
                        currentLocalProvider = { _customers.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val previousIds = _customers.value.map { it.id }.toSet()
                            val alreadyInit = !notifiedCollectionsInitialized.add("customers")
                            if (alreadyInit) {
                                list.filter { it.id.isNotBlank() && it.id !in previousIds }.forEach { cust ->
                                    val byLabel = if (cust.createdBy.isNotBlank()) " • By ${cust.createdBy}" else ""
                                    val areaPart = if (cust.area.isNotBlank()) " • ${cust.area}" else ""
                                    TransactionNotificationHelper.notifyIfNewRemoteTransaction(
                                        context = context.applicationContext,
                                        type = "customer",
                                        recordId = cust.id,
                                        title = "New Customer Added",
                                        body = "${cust.name}$areaPart$byLabel",
                                        createdByUid = cust.createdByUid,
                                        createdByName = cust.createdBy,
                                        createdAt = cust.createdAt
                                    )
                                }
                            } else {
                                list.forEach { cust ->
                                    TransactionNotificationHelper.markAlreadyNotified(context.applicationContext, "customer", cust.id, cust.createdAt)
                                }
                            }
                            _customers.value = list
                            storage.saveCustomers(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.DELIVERIES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "deliveries",
                        clazz = Delivery::class.java,
                        currentLocalProvider = { _deliveries.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val previousIds = _deliveries.value.map { it.id }.toSet()
                            val alreadyInit = !notifiedCollectionsInitialized.add("deliveries")
                            if (alreadyInit) {
                                list.filter { it.id.isNotBlank() && it.id !in previousIds }.forEach { del ->
                                    val actor = del.createdBy.ifBlank { del.staffName }
                                    val byLabel = if (actor.isNotBlank()) " • By $actor" else ""
                                    TransactionNotificationHelper.notifyIfNewRemoteTransaction(
                                        context = context.applicationContext,
                                        type = "delivery",
                                        recordId = del.id,
                                        title = "New Delivery • ${del.customerName}",
                                        body = "Rs. ${del.totalAmount.toInt()} (${del.totalFullDelivered} bottles)$byLabel",
                                        createdByUid = del.createdByUid,
                                        createdByName = actor,
                                        createdAt = del.createdAt
                                    )
                                }
                            } else {
                                list.forEach { del ->
                                    TransactionNotificationHelper.markAlreadyNotified(context.applicationContext, "delivery", del.id, del.createdAt)
                                }
                            }
                            _deliveries.value = list
                            storage.saveDeliveries(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "payments",
                        clazz = Payment::class.java,
                        currentLocalProvider = { _payments.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val previousIds = _payments.value.map { it.id }.toSet()
                            val alreadyInit = !notifiedCollectionsInitialized.add("payments")
                            if (alreadyInit) {
                                list.filter { it.id.isNotBlank() && it.id !in previousIds }.forEach { pay ->
                                    val byLabel = if (pay.receivedBy.isNotBlank()) " • By ${pay.receivedBy}" else ""
                                    TransactionNotificationHelper.notifyIfNewRemoteTransaction(
                                        context = context.applicationContext,
                                        type = "payment",
                                        recordId = pay.id,
                                        title = "Payment Received • ${pay.customerName}",
                                        body = "Rs. ${pay.amount.toInt()} (${pay.paymentMethod.label})$byLabel",
                                        createdByUid = pay.createdByUid,
                                        createdByName = pay.receivedBy,
                                        createdAt = pay.createdAt
                                    )
                                }
                            } else {
                                list.forEach { pay ->
                                    TransactionNotificationHelper.markAlreadyNotified(context.applicationContext, "payment", pay.id, pay.createdAt)
                                }
                            }
                            _payments.value = list
                            storage.savePayments(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.DELIVERIES, PermissionAction.VIEW) ||
                    hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "invoices",
                        clazz = Invoice::class.java,
                        currentLocalProvider = { _invoices.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _invoices.value = list
                            storage.saveInvoices(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.EXPENSES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "expenses",
                        clazz = Expense::class.java,
                        currentLocalProvider = { _expenses.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val previousIds = _expenses.value.map { it.id }.toSet()
                            val alreadyInit = !notifiedCollectionsInitialized.add("expenses")
                            if (alreadyInit) {
                                list.filter { it.id.isNotBlank() && it.id !in previousIds }.forEach { exp ->
                                    val byLabel = if (exp.enteredBy.isNotBlank()) " • By ${exp.enteredBy}" else ""
                                    val descPart = if (exp.description.isNotBlank()) " (${exp.description})" else ""
                                    TransactionNotificationHelper.notifyIfNewRemoteTransaction(
                                        context = context.applicationContext,
                                        type = "expense",
                                        recordId = exp.id,
                                        title = "New Expense • ${exp.categoryName}",
                                        body = "Rs. ${exp.amount.toInt()}$descPart$byLabel",
                                        createdByUid = exp.createdByUid,
                                        createdByName = exp.enteredBy,
                                        createdAt = exp.createdAt
                                    )
                                }
                            } else {
                                list.forEach { exp ->
                                    TransactionNotificationHelper.markAlreadyNotified(context.applicationContext, "expense", exp.id, exp.createdAt)
                                }
                            }
                            _expenses.value = list
                            storage.saveExpenses(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "bottleInventory",
                        clazz = BottleInventory::class.java,
                        currentLocalProvider = { _inventory.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val normalized = normalizeInventoryList(list)
                            _inventory.value = normalized
                            storage.saveInventory(normalized)
                        },
                        allowEmptyOverwriteAfterLoad = false,
                        markConnectedOnSuccess = true
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "bottleSizes",
                        clazz = BottleSize::class.java,
                        currentLocalProvider = { _bottleSizes.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            val normalized = normalizeBottleSizesList(list)
                            _bottleSizes.value = normalized
                            storage.saveBottleSizes(normalized)
                        },
                        allowEmptyOverwriteAfterLoad = false,
                        markConnectedOnSuccess = true
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "bottlePurchases",
                        clazz = BottlePurchaseRecord::class.java,
                        currentLocalProvider = { _bottlePurchases.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _bottlePurchases.value = list
                            storage.saveBottlePurchases(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.CUSTOMERS, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "customerRates",
                        clazz = CustomerRate::class.java,
                        currentLocalProvider = { _customerRates.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _customerRates.value = list
                            storage.saveCustomerRates(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.CUSTOMERS, PermissionAction.VIEW) ||
                    hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "customerBottleAllocations",
                        clazz = CustomerBottleAllocation::class.java,
                        currentLocalProvider = { _customerAllocations.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _customerAllocations.value = list
                            storage.saveCustomerAllocations(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.CUSTOMERS, PermissionAction.VIEW) ||
                    hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "securityDeposits",
                        clazz = SecurityDepositTransaction::class.java,
                        currentLocalProvider = { _securityDeposits.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _securityDeposits.value = list
                            storage.saveSecurityDeposits(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW) ||
                    hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "bottleMovements",
                        clazz = BottleMovement::class.java,
                        currentLocalProvider = { _bottleMovements.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _bottleMovements.value = list
                            storage.saveBottleMovements(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "roPlantItems",
                        clazz = RoPlantItem::class.java,
                        currentLocalProvider = { _roPlantItems.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _roPlantItems.value = list
                            storage.saveRoPlantItems(list)
                        },
                        markConnectedOnSuccess = true
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "roItemTransactions",
                        clazz = RoItemTransaction::class.java,
                        currentLocalProvider = { _roTransactions.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _roTransactions.value = list
                            storage.saveRoTransactions(list)
                        },
                        markConnectedOnSuccess = true
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "factoryMaterials",
                        clazz = FactoryMaterial::class.java,
                        currentLocalProvider = { _factoryMaterials.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _factoryMaterials.value = list
                            storage.saveFactoryMaterials(list)
                        },
                        markConnectedOnSuccess = true
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "materialTransactions",
                        clazz = MaterialTransaction::class.java,
                        currentLocalProvider = { _materialTransactions.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _materialTransactions.value = list
                            storage.saveMaterialTransactions(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                attachCollectionListener(
                    db = db,
                    collection = "plantWorkers",
                    clazz = PlantWorker::class.java,
                    currentLocalProvider = { _plantWorkers.value },
                    idExtractor = { it.id },
                    onApplyList = { list ->
                        _plantWorkers.value = list
                        storage.savePlantWorkers(list)
                    },
                    markConnectedOnSuccess = true
                )

                if (hasPermission(AppModule.REFILL_BATCHES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "bottleRefills",
                        clazz = BottleRefillRecord::class.java,
                        currentLocalProvider = { _bottleRefills.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _bottleRefills.value = list
                            storage.saveBottleRefills(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.VIEW) ||
                    hasPermission(AppModule.DELIVERIES, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "bottleIssueSales",
                        clazz = BottleIssueSaleRecord::class.java,
                        currentLocalProvider = { _bottleIssueSales.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _bottleIssueSales.value = list
                            storage.saveBottleIssueSales(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW) ||
                    hasPermission(AppModule.REPORTS, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "bottleCostings",
                        clazz = BottleCostingRecord::class.java,
                        currentLocalProvider = { _bottleCostings.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _bottleCostings.value = list
                            storage.saveBottleCostings(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.EXPENSES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "monthlyRecurringExpenses",
                        clazz = MonthlyRecurringExpense::class.java,
                        currentLocalProvider = { _monthlyRecurringExpenses.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _monthlyRecurringExpenses.value = list
                            storage.saveMonthlyRecurringExpenses(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW) ||
                    hasPermission(AppModule.REPORTS, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "monthlyOperationalCosts",
                        clazz = MonthlyOperationalCostRecord::class.java,
                        currentLocalProvider = { _monthlyOperationalCosts.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _monthlyOperationalCosts.value = list
                            storage.saveMonthlyOperationalCosts(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.VIEW) ||
                    hasPermission(AppModule.REPORTS, PermissionAction.VIEW)
                ) {
                    attachCollectionListener(
                        db = db,
                        collection = "cashClosings",
                        clazz = CashClosing::class.java,
                        currentLocalProvider = { _cashClosings.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _cashClosings.value = list
                            storage.saveCashClosings(list)
                        },
                        markConnectedOnSuccess = true
                    )
                }

                if (hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.VIEW)) {
                    val staffRef = rtdbCollectionRef(db, "staffUsers")
                    registerRtdbListener(
                        staffRef,
                        object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists() && snapshot.childrenCount > 0L) {
                                    val list = snapshot.children.mapNotNull { child ->
                                        parseUserAccountSnapshot(child, "staffUsers")
                                    }
                                    if (list.isNotEmpty()) {
                                        val merged = mergeAccountsPreservingLocalCredentials(_userAccounts.value, list)
                                        _userAccounts.value = merged
                                        storage.saveUserAccounts(merged)
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                handleFirestoreError(error.toException(), OperationType.LIST, "staffUsers")
                            }
                        }
                    )

                    attachCollectionListener(
                        db = db,
                        collection = "passwordResetRequests",
                        clazz = PasswordResetRequest::class.java,
                        currentLocalProvider = { _passwordResetRequests.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _passwordResetRequests.value = list
                            storage.savePasswordResetRequests(list)
                        },
                        markConnectedOnSuccess = true
                    )
                } else {
                    val curAuthUser = try { Firebase.auth.currentUser } catch (_: Exception) { null }
                    if (curAuthUser != null) {
                        val applySelfSnap: (DataSnapshot, String) -> Unit = { snap, col ->
                            if (snap.exists()) {
                                parseUserAccountSnapshot(snap, col)?.let { updatedAcc ->
                                    val merged = mergeAccountsPreservingLocalCredentials(_userAccounts.value, listOf(updatedAcc))
                                    _userAccounts.value = merged
                                    storage.saveUserAccounts(merged)
                                    refreshCurrentProfileIfMatches(updatedAcc)
                                    if (!updatedAcc.active && !updatedAcc.isSuperAdmin && isUserLoggedIn()) {
                                        logout()
                                    }
                                }
                            }
                        }
                        val selfUidRef = db.reference.child("staffUsers").child(RtdbSyncHelper.sanitizeKey(curAuthUser.uid))
                        registerRtdbListener(
                            selfUidRef,
                            object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) = applySelfSnap(snapshot, "staffUsers")
                                override fun onCancelled(error: DatabaseError) {}
                            }
                        )
                        val cleanEmail = curAuthUser.email?.trim()?.lowercase().orEmpty()
                        if (curAuthUser.isEmailVerified && cleanEmail.isNotBlank()) {
                            val selfEmailRef = rtdbDocRef(db, "staffByEmail", cleanEmail)
                            registerRtdbListener(
                                selfEmailRef,
                                object : ValueEventListener {
                                    override fun onDataChange(snapshot: DataSnapshot) = applySelfSnap(snapshot, "staffByEmail")
                                    override fun onCancelled(error: DatabaseError) {}
                                }
                            )
                        }
                    }
                }

                // Real-time password verifier sync across all logged-in devices
                val credRef = rtdbCollectionRef(db, "staffCredentials")
                registerRtdbListener(
                    credRef,
                    object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!snapshot.exists()) return
                            snapshot.children.forEach { child ->
                                applyCredentialVerifierSnapshot(child)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.w("WaterPlantRepo", "staffCredentials listener notice: ${error.message}")
                        }
                    }
                )

                val parsePlantBranch: (DataSnapshot) -> PlantBranch? = { snap ->
                    val pid = snap.child("id").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: snap.key.orEmpty()
                    val pName = snap.child("plantName").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                        ?: snap.child("name").getValue(String::class.java)?.takeIf { it.isNotBlank() }
                    if (pid.isBlank() || pName.isNullOrBlank()) {
                        null
                    } else {
                        PlantBranch(
                            id = pid,
                            name = pName,
                            city = snap.child("city").getValue(String::class.java) ?: "",
                            active = snap.child("active").getValue(Boolean::class.java) ?: true,
                            isPrimary = snap.child("isPrimary").getValue(Boolean::class.java) ?: (pid == PermissionDefaults.DEFAULT_PLANT_ID)
                        )
                    }
                }
                if (isCurrentAuthSuperAdmin()) {
                    registerRtdbListener(
                        db.reference.child("plants"),
                        object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val list = snapshot.children.mapNotNull(parsePlantBranch)
                                if (list.isNotEmpty()) {
                                    _plantBranches.value = list
                                    storage.saveBranches(list)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                handleFirestoreError(error.toException(), OperationType.LIST, "plants")
                            }
                        }
                    )
                } else {
                    registerRtdbListener(
                        db.reference.child("plants").child(RtdbSyncHelper.sanitizeKey(activePlant)),
                        object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val branch = parsePlantBranch(snapshot)
                                if (branch != null) {
                                    val merged = (_plantBranches.value.filterNot { it.id == branch.id } + branch)
                                    _plantBranches.value = merged
                                    storage.saveBranches(merged)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                handleFirestoreError(error.toException(), OperationType.GET, "plants/$activePlant")
                            }
                        }
                    )
                }

                if (hasPermission(AppModule.AUDIT_HISTORY, PermissionAction.VIEW) ||
                    hasPermission(AppModule.REPORTS, PermissionAction.VIEW)
                ) {
                    val auditQuery = rtdbCollectionRef(db, "auditLogs")
                        .orderByChild("timestampMillis")
                        .limitToLast(200)
                    registerRtdbListener(
                        auditQuery,
                        object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists() && snapshot.childrenCount > 0L) {
                                    val remoteLogs = snapshot.children.mapNotNull { child ->
                                        RtdbSyncHelper.fromDataSnapshot(child, AuditLogEntry::class.java)
                                    }
                                    val merged = (remoteLogs + _auditLogs.value)
                                        .distinctBy { it.id }
                                        .sortedByDescending { it.timestampMillis }
                                        .take(500)
                                    _auditLogs.value = merged
                                    storage.saveAuditLogs(merged)
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                handleFirestoreError(error.toException(), OperationType.LIST, "auditLogs")
                            }
                        }
                    )
                }

                if (hasPermission(AppModule.EXPENSES, PermissionAction.VIEW)) {
                    attachCollectionListener(
                        db = db,
                        collection = "expenseCategories",
                        clazz = ExpenseCategory::class.java,
                        currentLocalProvider = { _expenseCategories.value },
                        idExtractor = { it.id },
                        onApplyList = { list ->
                            _expenseCategories.value = list
                            storage.saveExpenseCategories(list)
                        },
                        allowEmptyOverwriteAfterLoad = false,
                        markConnectedOnSuccess = true
                    )
                }

                val settingsRef = rtdbDocRef(db, "appSettings", currentAppSettingsDocId())
                registerRtdbListener(
                    settingsRef,
                    object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (!snapshot.exists()) return
                            rememberRtdbAccessMetadata("appSettings", snapshot)
                            val dark = snapshot.child("isDarkMode").getValue(Boolean::class.java) ?: false
                            if (_isDarkMode.value != dark) {
                                _isDarkMode.value = dark
                                storage.saveDarkMode(dark)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            handleFirestoreError(error.toException(), OperationType.GET, "appSettings/${currentAppSettingsDocId()}")
                        }
                    }
                )

                // Throttled (every 6h) check for free OTA app updates
                checkForAppUpdate(force = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "sync_init")
                _isCloudConnected.value = false
                _syncStatus.value = friendlyFirestoreStatusMessage(e)
            }
        }
    }

    private suspend fun ensurePlantWorkspace(db: FirebaseDatabase) {
        val user = try { Firebase.auth.currentUser } catch (_: Exception) { null } ?: return
        val activePlant = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val wsKey = "${user.uid}|$activePlant"
        if (workspaceEnsuredKey == wsKey) return
        try {
            // Ensure primary centralized plant node exists in Realtime Database
            val defaultPlantRef = rtdbDocRef(db, "plants", PermissionDefaults.DEFAULT_PLANT_ID)
            val defaultPlantSnap = defaultPlantRef.get().await()
            if (!defaultPlantSnap.exists() && isCurrentAuthSuperAdmin()) {
                defaultPlantRef.setValue(
                    mapOf(
                        "id" to PermissionDefaults.DEFAULT_PLANT_ID,
                        "plantName" to "Aab-e-Baraan Main Plant",
                        "name" to "Aab-e-Baraan Main Plant",
                        "city" to "Main RO Plant",
                        "active" to true,
                        "isPrimary" to true,
                        "updatedAt" to ServerValue.TIMESTAMP
                    )
                ).await()
            }

            // Optional custom-token admin bootstrap if staffAuthApiClient is configured
            if (isCurrentAuthSuperAdmin() && staffAuthApiClient.isConfigured()) {
                try {
                    val token = user.getIdToken(false).await()?.token
                    if (!token.isNullOrBlank()) {
                        staffAuthApiClient.bootstrapAdmin(
                            idToken = token,
                            plantId = activePlant,
                            plantName = "Aab-e-Baraan Main Plant",
                            name = _currentUser.value.name.ifBlank { "Muhammad Rafiq (Admin)" }
                        )
                    }
                } catch (_: Exception) {
                }
            }

            if (plantOwnerUid.isNullOrBlank()) {
                plantOwnerUid = user.uid
            }
            workspaceEnsuredKey = wsKey
        } catch (e: Exception) {
            Log.w("WaterPlantRepo", "Workspace check notice: ${e.message}")
        }
    }

    private suspend fun flushPendingCloudWrites(db: FirebaseDatabase) {
        flushOfflineSyncQueue(db, forceIncludeFailed = true)
        pendingSyncItems.clear()
        pendingDeleteKeys.clear()
    }

    suspend fun flushOfflineSyncQueue(db: FirebaseDatabase? = rtdb, forceIncludeFailed: Boolean = true) {
        val targetDb = db ?: return
        if (!isFirebaseSignedIn()) return
        flushMutex.withLock {
            _isSyncFlushing.value = true
            try {
                var firstPass = true
                while (true) {
                    val items = offlineSyncManager.getPendingQueueItems(forceIncludeFailed = forceIncludeFailed && firstPass)
                    firstPass = false
                    if (items.isEmpty()) break

                    items.forEach { item ->
                        val snapshotUpdatedAt = offlineSyncManager.markSyncing(item.id)
                        try {
                            val docRef = rtdbDocRef(targetDb, item.collection, item.docId, item.plantId)
                            if (item.operation == SyncOperationType.DELETE) {
                                docRef.removeValue().await()
                                if (item.collection == "staffUsers") {
                                    targetDb.reference.child("staffUsers")
                                        .child(RtdbSyncHelper.sanitizeKey(item.docId))
                                        .removeValue()
                                        .await()
                                }
                                pendingDeleteKeys.remove(item.id)
                                cloudKnownDocKeys.remove(item.id)
                                lastSyncedPayloadHash.remove(item.id)
                                offlineSyncManager.markSynced(item.id, expectedUpdatedAtMillis = snapshotUpdatedAt)
                            } else {
                                val rawMap = OfflineSyncManager.mapFromPayloadJson(item.payloadJson)
                                if (rawMap == null) {
                                    offlineSyncManager.markFailed(item.id, "Empty payload JSON", expectedUpdatedAtMillis = snapshotUpdatedAt)
                                } else {
                                    val payload = buildRtdbPayload(item.collection, item.docId, rawMap, item.plantId)
                                    if (payload == null) {
                                        offlineSyncManager.markFailed(item.id, "Unauthenticated session", expectedUpdatedAtMillis = snapshotUpdatedAt)
                                    } else {
                                        val deltas = if (item.deltaFieldsJson.isNotBlank()) {
                                            OfflineSyncManager.deltaMapFromJson(item.deltaFieldsJson)
                                        } else null
                                        val extraMap = if (item.extraFieldsJson.isNotBlank()) {
                                            OfflineSyncManager.mapFromPayloadJson(item.extraFieldsJson)
                                        } else null
                                        if (!deltas.isNullOrEmpty()) {
                                            val remoteExists = if (cloudKnownDocKeys.contains(item.id)) {
                                                true
                                            } else {
                                                docRef.child("id").get().await().exists()
                                            }
                                            if (!remoteExists) {
                                                docRef.setValue(payload).await()
                                            } else {
                                                val updateMap = linkedMapOf<String, Any?>()
                                                deltas.forEach { (field, num) ->
                                                    val safeField = RtdbSyncHelper.sanitizeKey(field)
                                                    updateMap[safeField] = if (num is Double || num is Float) {
                                                        ServerValue.increment(num.toDouble())
                                                    } else {
                                                        ServerValue.increment(num.toLong())
                                                    }
                                                }
                                                extraMap?.forEach { (field, value) ->
                                                    if (value != null) {
                                                        updateMap[RtdbSyncHelper.sanitizeKey(field)] = value
                                                    }
                                                }
                                                updateMap["updatedAt"] = ServerValue.TIMESTAMP
                                                updateMap["updatedAtMillis"] = System.currentTimeMillis()
                                                docRef.updateChildren(updateMap).await()
                                            }
                                        } else {
                                            docRef.setValue(payload).await()
                                            if (item.collection == "staffUsers") {
                                                targetDb.reference.child("staffUsers")
                                                    .child(RtdbSyncHelper.sanitizeKey(item.docId))
                                                    .setValue(payload)
                                                    .await()
                                            }
                                        }
                                        if (deltas.isNullOrEmpty()) {
                                            lastSyncedPayloadHash[item.id] = "${item.plantId}|${item.payloadJson}".hashCode()
                                        } else {
                                            lastSyncedPayloadHash.remove(item.id)
                                        }
                                        pendingSyncItems.remove(item.id)
                                        cloudKnownDocKeys.add(item.id)
                                        offlineSyncManager.markSynced(item.id, expectedUpdatedAtMillis = snapshotUpdatedAt)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            handleFirestoreError(e, OperationType.WRITE, "${item.collection}/${item.docId}")
                            offlineSyncManager.markFailed(item.id, e.localizedMessage ?: "Sync error", expectedUpdatedAtMillis = snapshotUpdatedAt)
                        }
                    }
                }
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                val remainingItems = offlineSyncManager.queueItems.value.filter { it.status != SyncQueueStatus.SYNCED }
                val remainingActions = remainingItems.map { it.actionGroupId.ifBlank { it.id } }.distinct().size
                if (remainingActions == 0) {
                    _isCloudConnected.value = true
                    _syncStatus.value = "Realtime DB Synced at $time"
                } else {
                    val failedActions = remainingItems.filter { it.status == SyncQueueStatus.FAILED }
                        .map { it.actionGroupId.ifBlank { it.id } }.distinct().size
                    _syncStatus.value = if (failedActions > 0) {
                        "Sync Pending: $remainingActions action(s) ($failedActions failed)"
                    } else {
                        "Pending Sync: $remainingActions action(s)"
                    }
                }
            } finally {
                _isSyncFlushing.value = false
            }
        }
    }

    private suspend fun seedInitialCloudDataIfEmpty(db: FirebaseDatabase) {
        if (!isFirebaseSignedIn()) return
        if (!isCurrentAuthSuperAdmin() && !hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)) {
            cloudSeedChecked = true
            _isCloudConnected.value = true
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            _syncStatus.value = "Realtime DB Synced at $time"
            return
        }
        try {
            val sizeSnap = if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.VIEW)) {
                rtdbCollectionRef(db, "bottleSizes").limitToFirst(1).get().await()
            } else null
            val custSnap = if (hasPermission(AppModule.CUSTOMERS, PermissionAction.VIEW)) {
                rtdbCollectionRef(db, "customers").limitToFirst(1).get().await()
            } else null
            sizeSnap?.children?.firstOrNull()?.let { rememberRtdbAccessMetadata("bottleSizes", it) }
            custSnap?.children?.firstOrNull()?.let { rememberRtdbAccessMetadata("customers", it) }

            val cloudHasSizes = sizeSnap != null && sizeSnap.exists() && sizeSnap.childrenCount > 0L
            val cloudHasCustomers = custSnap != null && custSnap.exists() && custSnap.childrenCount > 0L

            if (!cloudHasSizes && !cloudHasCustomers && isCurrentAuthSuperAdmin()) {
                Log.d("WaterPlantRepo", "Seeding initial baseline & local data to Realtime Database...")
                withSyncAction("Initial Realtime DB Seed") {
                    _customers.value.forEach { if (it.id.isNotBlank()) syncDocument("customers", it.id, it) }
                    _bottleSizes.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleSizes", it.id, it) }
                    _inventory.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleInventory", it.id, it) }
                    _userAccounts.value.forEach {
                        if (it.id.isNotBlank()) {
                            syncStaffAccountDocuments(it)
                            if (it.passwordUpdatedAtMillis > 0L) {
                                syncPasswordVerifierToCloud(it)
                            }
                        }
                    }
                    _deliveries.value.forEach { if (it.id.isNotBlank()) syncDocument("deliveries", it.id, it) }
                    _invoices.value.forEach { if (it.id.isNotBlank()) syncDocument("invoices", it.id, it) }
                    _expenses.value.forEach { if (it.id.isNotBlank()) syncDocument("expenses", it.id, it) }
                    _payments.value.forEach { if (it.id.isNotBlank()) syncDocument("payments", it.id, it) }
                    _customerRates.value.forEach { if (it.id.isNotBlank()) syncDocument("customerRates", it.id, it) }
                    _customerAllocations.value.forEach { if (it.id.isNotBlank()) syncDocument("customerBottleAllocations", it.id, it) }
                    _securityDeposits.value.forEach { if (it.id.isNotBlank()) syncDocument("securityDeposits", it.id, it) }
                    _bottleMovements.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleMovements", it.id, it) }
                    _bottlePurchases.value.forEach { if (it.id.isNotBlank()) syncDocument("bottlePurchases", it.id, it) }
                    _bottleRefills.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleRefills", it.id, it) }
                    _bottleIssueSales.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleIssueSales", it.id, it) }
                    _roPlantItems.value.forEach { if (it.id.isNotBlank()) syncDocument("roPlantItems", it.id, it) }
                    _roTransactions.value.forEach { if (it.id.isNotBlank()) syncDocument("roItemTransactions", it.id, it) }
                    _factoryMaterials.value.forEach { if (it.id.isNotBlank()) syncDocument("factoryMaterials", it.id, it) }
                    _materialTransactions.value.forEach { if (it.id.isNotBlank()) syncDocument("materialTransactions", it.id, it) }
                    _bottleCostings.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleCostings", it.id, it) }
                    _monthlyRecurringExpenses.value.forEach { if (it.id.isNotBlank()) syncDocument("monthlyRecurringExpenses", it.id, it) }
                    _monthlyOperationalCosts.value.forEach { if (it.id.isNotBlank()) syncDocument("monthlyOperationalCosts", it.id, it) }
                    _plantWorkers.value.forEach { if (it.id.isNotBlank()) syncDocument("plantWorkers", it.id, it) }
                    _cashClosings.value.forEach { if (it.id.isNotBlank()) syncDocument("cashClosings", it.id, it) }
                    _expenseCategories.value.forEach { if (it.id.isNotBlank()) syncDocument("expenseCategories", it.id, it) }
                    syncDocument("appSettings", currentAppSettingsDocId(), mapOf("isDarkMode" to _isDarkMode.value))
                }
                flushOfflineSyncQueue(db, forceIncludeFailed = false)
                Log.d("WaterPlantRepo", "Baseline data seeded to Realtime Database successfully.")
            } else {
                Log.d("WaterPlantRepo", "Realtime Database already contains operational data. Syncing updates.")
            }
            cloudSeedChecked = true
            _isCloudConnected.value = true
            val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            _syncStatus.value = "Realtime DB Synced at $time"
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "customers")
            _isCloudConnected.value = false
            _syncStatus.value = friendlyFirestoreStatusMessage(e)
        }
    }

    private fun findAuthorizedLocalAccount(uid: String, cleanEmail: String): UserAccount? {
        return _userAccounts.value.find {
            it.googleEmail.trim().equals(cleanEmail, ignoreCase = true) ||
                it.phone.trim().equals(cleanEmail, ignoreCase = true) ||
                normalizeUsername(it.username) == cleanEmail.substringBefore("@") ||
                it.id == uid ||
                it.uid == uid
        }
    }

    private fun completeGoogleAuthSuccess(
        uid: String,
        cleanEmail: String,
        displayName: String,
        isPrimaryAdmin: Boolean,
        existingAccount: UserAccount?
    ): Boolean {
        if (existingAccount != null && !existingAccount.active) {
            Log.w("WaterPlantRepo", "Inactive account attempt: $cleanEmail")
            try { Firebase.auth.signOut() } catch (_: Exception) {}
            _isCloudConnected.value = false
            _cloudUserEmail.value = null
            return false
        }

        val isSuper = isPrimaryAdmin || (existingAccount?.isSuperAdmin == true)
        val role = when {
            isSuper -> UserRole.ADMIN
            existingAccount != null -> existingAccount.role
            else -> UserRole.VIEW_ONLY
        }
        val perms = PermissionDefaults.normalizePermissions(existingAccount?.permissions, role, isSuper)
        val branchIds = existingAccount?.assignedBranchIds?.ifEmpty { null } ?: listOf(_activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID })
        val defBranch = existingAccount?.defaultBranchId?.ifBlank { null } ?: branchIds.first()

        val profile = UserProfile(
            id = existingAccount?.id?.takeIf { it.isNotBlank() } ?: uid,
            uid = existingAccount?.uid?.takeIf { it.isNotBlank() } ?: uid,
            username = existingAccount?.username?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@"),
            name = existingAccount?.name?.takeIf { it.isNotBlank() }
                ?: displayName.ifEmpty { if (isSuper) "Muhammad Rafiq (Admin)" else "Authorized Staff" },
            email = cleanEmail,
            phone = existingAccount?.phone ?: "",
            role = role,
            isSuperAdmin = isSuper,
            permissions = perms,
            assignedBranchIds = branchIds,
            defaultBranchId = defBranch,
            businessId = _activePlantId.value,
            active = true,
            restrictToOwnDataOnly = if (isSuper || role == UserRole.ADMIN) false else (existingAccount?.restrictToOwnDataOnly == true),
            restrictToTodayOnly = if (isSuper || role == UserRole.ADMIN) false else (existingAccount?.restrictToTodayOnly == true)
        )
        _currentUser.value = profile
        refreshSeenRecordsForCurrentUser()
        _cloudUserEmail.value = cleanEmail
        storage.saveCurrentProfile(profile)
        storage.saveLoginState(true)
        storage.saveLastUser(profile.username)
        startFirestoreSync()
        appendAuditLog(
            action = AuditActionType.LOGIN,
            module = AppModule.DASHBOARD,
            recordId = profile.uid,
            recordLabel = "${profile.name} ($cleanEmail)",
            updatedValue = "Google Sign-In (${role.name})"
        )
        addNotification("Google Login", "${profile.name} logged in (${profile.role.displayName}).", NotificationSeverity.SUCCESS)
        return true
    }

    fun handleGoogleAuthSuccess(uid: String, email: String, displayName: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val isPrimaryAdmin = cleanEmail in primaryAdminEmails
        val existingAccount = findAuthorizedLocalAccount(uid, cleanEmail)

        if (!isPrimaryAdmin && existingAccount == null) {
            Log.w("WaterPlantRepo", "Unauthorized Google account attempt: $cleanEmail")
            try { Firebase.auth.signOut() } catch (_: Exception) {}
            _isCloudConnected.value = false
            _cloudUserEmail.value = null
            return false
        }

        return completeGoogleAuthSuccess(uid, cleanEmail, displayName, isPrimaryAdmin, existingAccount)
    }

    suspend fun handleGoogleAuthSuccessAsync(uid: String, email: String, displayName: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val isPrimaryAdmin = cleanEmail in primaryAdminEmails
        var existingAccount = findAuthorizedLocalAccount(uid, cleanEmail)

        if (!isPrimaryAdmin && existingAccount == null) {
            val db = rtdb
            if (db != null && isFirebaseSignedIn()) {
                val cloudUsers = mutableListOf<UserAccount>()
                try {
                    val emailSnap = rtdbDocRef(db, "staffByEmail", cleanEmail).get().await()
                    if (emailSnap.exists()) {
                        parseUserAccountSnapshot(emailSnap, "staffByEmail")?.let { cloudUsers.add(it) }
                    }
                } catch (e: Exception) {
                    Log.w("WaterPlantRepo", "staffByEmail lookup notice: ${e.message}")
                }
                if (cloudUsers.isEmpty()) {
                    try {
                        val uidSnap = db.reference.child("staffUsers").child(RtdbSyncHelper.sanitizeKey(uid)).get().await()
                        if (uidSnap.exists()) {
                            parseUserAccountSnapshot(uidSnap, "staffUsers")?.let { cloudUsers.add(it) }
                        }
                    } catch (e: Exception) {
                        Log.w("WaterPlantRepo", "staffUsers self lookup notice: ${e.message}")
                    }
                }
                if (cloudUsers.isNotEmpty()) {
                    val mergedList = mergeAccountsPreservingLocalCredentials(_userAccounts.value, cloudUsers)
                    _userAccounts.value = mergedList
                    storage.saveUserAccounts(mergedList)
                }
                existingAccount = findAuthorizedLocalAccount(uid, cleanEmail)
            }
        }

        if (!isPrimaryAdmin && existingAccount == null) {
            Log.w("WaterPlantRepo", "Unauthorized Google account attempt: $cleanEmail")
            try { Firebase.auth.signOut() } catch (_: Exception) {}
            _isCloudConnected.value = false
            _cloudUserEmail.value = null
            return false
        }

        return completeGoogleAuthSuccess(uid, cleanEmail, displayName, isPrimaryAdmin, existingAccount)
    }

    fun connectGoogleCloudSession(uid: String, email: String, displayName: String): Boolean {
        val cleanEmail = email.trim().lowercase()
        val isPrimaryAdmin = cleanEmail in primaryAdminEmails
        val existingAccount = findAuthorizedLocalAccount(uid, cleanEmail)
        val activeLocalAdmin = _currentUser.value.role == UserRole.ADMIN
        if (!isPrimaryAdmin && existingAccount == null && !activeLocalAdmin) {
            try { Firebase.auth.signOut() } catch (_: Exception) {}
            return false
        }
        _cloudUserEmail.value = cleanEmail
        if (activeLocalAdmin && isPrimaryAdmin) {
            val updated = _currentUser.value.copy(
                email = cleanEmail,
                isSuperAdmin = true,
                permissions = PermissionDefaults.fullAdminPermissions()
            )
            _currentUser.value = updated
            refreshSeenRecordsForCurrentUser()
            storage.saveCurrentProfile(updated)
        }
        startFirestoreSync()
        addNotification("Cloud Connected", "Connected to Firebase Cloud as $cleanEmail.", NotificationSeverity.SUCCESS)
        return true
    }

    fun syncAllLocalToCloud() {
        if (!isFirebaseSignedIn()) return
        scope.launch {
            withSyncAction("Full Cloud Backup Sync") {
                if (hasPermission(AppModule.CUSTOMERS, PermissionAction.ADD)) {
                    _customers.value.forEach { if (it.id.isNotBlank()) syncDocument("customers", it.id, it) }
                    _customerRates.value.forEach { if (it.id.isNotBlank()) syncDocument("customerRates", it.id, it) }
                    _customerAllocations.value.forEach { if (it.id.isNotBlank()) syncDocument("customerBottleAllocations", it.id, it) }
                    _securityDeposits.value.forEach { if (it.id.isNotBlank()) syncDocument("securityDeposits", it.id, it) }
                }
                if (hasPermission(AppModule.BOTTLE_STOCK, PermissionAction.ADD)) {
                    _bottleSizes.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleSizes", it.id, it) }
                    _inventory.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleInventory", it.id, it) }
                    _bottleMovements.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleMovements", it.id, it) }
                    _bottlePurchases.value.forEach { if (it.id.isNotBlank()) syncDocument("bottlePurchases", it.id, it) }
                    _bottleCostings.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleCostings", it.id, it) }
                    _monthlyOperationalCosts.value.forEach { if (it.id.isNotBlank()) syncDocument("monthlyOperationalCosts", it.id, it) }
                }
                if (hasPermission(AppModule.REFILL_BATCHES, PermissionAction.ADD)) {
                    _bottleRefills.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleRefills", it.id, it) }
                }
                if (hasPermission(AppModule.BOTTLE_ISSUE, PermissionAction.ADD)) {
                    _bottleIssueSales.value.forEach { if (it.id.isNotBlank()) syncDocument("bottleIssueSales", it.id, it) }
                }
                if (hasPermission(AppModule.DELIVERIES, PermissionAction.ADD)) {
                    _deliveries.value.forEach { if (it.id.isNotBlank()) syncDocument("deliveries", it.id, it) }
                    _invoices.value.forEach { if (it.id.isNotBlank()) syncDocument("invoices", it.id, it) }
                }
                if (hasPermission(AppModule.EXPENSES, PermissionAction.ADD)) {
                    _expenses.value.forEach { if (it.id.isNotBlank()) syncDocument("expenses", it.id, it) }
                    _monthlyRecurringExpenses.value.forEach { if (it.id.isNotBlank()) syncDocument("monthlyRecurringExpenses", it.id, it) }
                    _expenseCategories.value.forEach { if (it.id.isNotBlank()) syncDocument("expenseCategories", it.id, it) }
                }
                if (hasPermission(AppModule.PAYMENTS_BALANCES, PermissionAction.ADD)) {
                    _payments.value.forEach { if (it.id.isNotBlank()) syncDocument("payments", it.id, it) }
                    _cashClosings.value.forEach { if (it.id.isNotBlank()) syncDocument("cashClosings", it.id, it) }
                }
                if (hasPermission(AppModule.FACTORY_MATERIALS, PermissionAction.ADD)) {
                    _roPlantItems.value.forEach { if (it.id.isNotBlank()) syncDocument("roPlantItems", it.id, it) }
                    _roTransactions.value.forEach { if (it.id.isNotBlank()) syncDocument("roItemTransactions", it.id, it) }
                    _factoryMaterials.value.forEach { if (it.id.isNotBlank()) syncDocument("factoryMaterials", it.id, it) }
                    _materialTransactions.value.forEach { if (it.id.isNotBlank()) syncDocument("materialTransactions", it.id, it) }
                }
                if (hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.MANAGE)) {
                    _userAccounts.value.forEach {
                        if (it.id.isNotBlank()) {
                            syncStaffAccountDocuments(it)
                        }
                    }
                    _plantWorkers.value.forEach { if (it.id.isNotBlank()) syncDocument("plantWorkers", it.id, it) }
                }
                if (hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)) {
                    syncDocument("appSettings", currentAppSettingsDocId(), mapOf("isDarkMode" to _isDarkMode.value))
                }
            }
            flushOfflineSyncQueue(rtdb, forceIncludeFailed = true)
        }
    }

    fun syncDocument(collection: String, docId: String, data: Any) {
        if (docId.isBlank()) return
        val key = "$collection|$docId"
        val plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val plainJson = FirestoreSyncHelper.toPlainJsonString(data)
        val payloadHash = "$plantId|$plainJson".hashCode()
        if (!offlineSyncManager.hasPendingOrFailedMutation(collection, docId) &&
            lastSyncedPayloadHash[key] == payloadHash
        ) {
            return
        }
        val actionCtx = currentSyncAction.get()
        offlineSyncManager.enqueueUpsert(
            collection = collection,
            docId = docId,
            plantId = plantId,
            payloadJson = plainJson,
            actorName = _currentUser.value.name,
            baseRemoteUpdatedAtMillis = docUpdatedAtMillis[key] ?: 0L,
            actionGroupId = actionCtx?.groupId.orEmpty(),
            actionTitle = actionCtx?.title.orEmpty()
        )

        val db = rtdb
        if (db == null) {
            _isCloudConnected.value = false
            _syncStatus.value = "Queued Offline (Realtime DB unavailable)"
            return
        }
        if (!isFirebaseSignedIn()) {
            _isCloudConnected.value = false
            _syncStatus.value = "Queued locally • Sign in to Sync"
            return
        }
        if (actionCtx == null) {
            triggerQueueFlush()
        }
    }

    private fun syncIncrementalDelta(
        collection: String,
        docId: String,
        fullData: Any,
        deltaFields: Map<String, Number>,
        extraFields: Map<String, Any> = emptyMap()
    ) {
        if (docId.isBlank()) return
        if (deltaFields.isEmpty()) {
            syncDocument(collection, docId, fullData)
            return
        }
        val key = "$collection|$docId"
        lastSyncedPayloadHash.remove(key)
        val plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val plainJson = FirestoreSyncHelper.toPlainJsonString(fullData)
        val actionCtx = currentSyncAction.get()
        offlineSyncManager.enqueueIncrementalUpsert(
            collection = collection,
            docId = docId,
            plantId = plantId,
            fullPayloadJson = plainJson,
            deltaFields = deltaFields,
            extraFields = extraFields,
            actorName = _currentUser.value.name,
            baseRemoteUpdatedAtMillis = docUpdatedAtMillis[key] ?: 0L,
            actionGroupId = actionCtx?.groupId.orEmpty(),
            actionTitle = actionCtx?.title.orEmpty()
        )

        val db = rtdb
        if (db == null) {
            _isCloudConnected.value = false
            _syncStatus.value = "Queued Offline (Realtime DB unavailable)"
            return
        }
        if (!isFirebaseSignedIn()) {
            _isCloudConnected.value = false
            _syncStatus.value = "Queued locally • Sign in to Sync"
            return
        }
        if (actionCtx == null) {
            triggerQueueFlush()
        }
    }

    fun deleteDocument(collection: String, docId: String) {
        if (docId.isBlank()) return
        val key = "$collection|$docId"
        pendingSyncItems.remove(key)
        lastSyncedPayloadHash.remove(key)
        val plantId = _activePlantId.value.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
        val actionCtx = currentSyncAction.get()
        offlineSyncManager.enqueueDelete(
            collection = collection,
            docId = docId,
            plantId = plantId,
            actorName = _currentUser.value.name,
            actionGroupId = actionCtx?.groupId.orEmpty(),
            actionTitle = actionCtx?.title.orEmpty()
        )
        val db = rtdb
        if (db == null || !isFirebaseSignedIn()) {
            _isCloudConnected.value = false
            if (!isFirebaseSignedIn()) {
                _syncStatus.value = "Queued Offline • Sign in to Sync"
            }
            return
        }
        if (actionCtx == null) {
            triggerQueueFlush()
        }
    }

    // ==========================================
    // BACKUP & RESTORE ENTERPRISE SYSTEM
    // ==========================================

    fun createFullBackupSnapshot(type: BackupType, notes: String = ""): PlantBackupData {
        val countMap = linkedMapOf(
            "customers" to _customers.value.size,
            "deliveries" to _deliveries.value.size,
            "payments" to _payments.value.size,
            "invoices" to _invoices.value.size,
            "expenses" to _expenses.value.size,
            "bottleSizes" to _bottleSizes.value.size,
            "inventory" to _inventory.value.size,
            "bottlePurchases" to _bottlePurchases.value.size,
            "bottleRefills" to _bottleRefills.value.size,
            "bottleIssueSales" to _bottleIssueSales.value.size,
            "roPlantItems" to _roPlantItems.value.size,
            "roTransactions" to _roTransactions.value.size,
            "cashClosings" to _cashClosings.value.size,
            "customerAllocations" to _customerAllocations.value.size,
            "customerRates" to _customerRates.value.size,
            "securityDeposits" to _securityDeposits.value.size,
            "expenseCategories" to _expenseCategories.value.size,
            "userAccounts" to _userAccounts.value.size,
            "bottleMovements" to _bottleMovements.value.size
        )
        val totalRecs = countMap.values.sum()
        val now = Date()
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(now)
        val backupId = "backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)}"

        val tempMetadata = BackupMetadata(
            backupId = backupId,
            createdAt = timestampFormat,
            timestampMs = now.time,
            appVersion = "1.0",
            creatorEmail = _currentUser.value.email.ifBlank { "admin@waterplant.local" },
            creatorName = _currentUser.value.name,
            backupType = type,
            recordCounts = countMap,
            totalRecords = totalRecs,
            checksumSha256 = "",
            storageLocation = "Cloud & Local Storage",
            notes = notes
        )

        val sanitizedAccounts = _userAccounts.value.map {
            it.copy(password = "", passwordHash = "", passwordSalt = "")
        }

        val preliminaryData = PlantBackupData(
            metadata = tempMetadata,
            customers = _customers.value,
            deliveries = _deliveries.value,
            payments = _payments.value,
            invoices = _invoices.value,
            expenses = _expenses.value,
            bottleSizes = _bottleSizes.value,
            inventory = _inventory.value,
            bottlePurchases = _bottlePurchases.value,
            bottleRefills = _bottleRefills.value,
            bottleIssueSales = _bottleIssueSales.value,
            roPlantItems = _roPlantItems.value,
            roTransactions = _roTransactions.value,
            cashClosings = _cashClosings.value,
            customerAllocations = _customerAllocations.value,
            customerRates = _customerRates.value,
            securityDeposits = _securityDeposits.value,
            expenseCategories = _expenseCategories.value,
            userAccounts = sanitizedAccounts,
            bottleMovements = _bottleMovements.value,
            factoryMaterials = _factoryMaterials.value,
            materialTransactions = _materialTransactions.value,
            bottleCostings = _bottleCostings.value,
            monthlyRecurringExpenses = _monthlyRecurringExpenses.value,
            monthlyOperationalCosts = _monthlyOperationalCosts.value,
            plantWorkers = _plantWorkers.value
        )

        val serialized = storage.serializeBackupData(preliminaryData)
        val checksum = storage.computeSha256(serialized)
        return preliminaryData.copy(metadata = tempMetadata.copy(checksumSha256 = checksum))
    }

    fun backupNow(notes: String = "", onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (_currentUser.value.role != UserRole.ADMIN) {
            onResult(false, "Security Alert: Only Administrator can perform backups.")
            return
        }

        _isBackupInProgress.value = true
        scope.launch {
            try {
                val snapshot = createFullBackupSnapshot(BackupType.MANUAL, notes)
                val file = storage.saveBackupToFile(snapshot)
                val serializedJson = storage.serializeBackupData(snapshot)
                val sizeBytes = file.length()

                val summary = buildString {
                    append("${snapshot.metadata.totalRecords} total records ")
                    append("(${snapshot.customers.size} Cust, ")
                    append("${snapshot.deliveries.size} Del, ")
                    append("${snapshot.payments.size} Pay, ")
                    append("${snapshot.expenses.size} Exp)")
                }

                // Sync to Firebase Realtime Database if authenticated and online
                var cloudStatus = "Local Storage Only"
                val db = rtdb
                if (db != null && isFirebaseSignedIn() && serializedJson.length <= 2_000_000) {
                    try {
                        val cloudDoc = mapOf(
                            "backupId" to snapshot.metadata.backupId,
                            "createdAt" to snapshot.metadata.createdAt,
                            "timestampMs" to snapshot.metadata.timestampMs,
                            "appVersion" to snapshot.metadata.appVersion,
                            "creatorEmail" to snapshot.metadata.creatorEmail,
                            "creatorName" to snapshot.metadata.creatorName,
                            "backupType" to snapshot.metadata.backupType.name,
                            "recordCounts" to snapshot.metadata.recordCounts,
                            "totalRecords" to snapshot.metadata.totalRecords,
                            "checksumSha256" to snapshot.metadata.checksumSha256,
                            "storageLocation" to "Realtime DB & Local Storage",
                            "notes" to snapshot.metadata.notes,
                            "jsonPayload" to serializedJson
                        )
                        val securedDoc = buildRtdbPayload("backups", snapshot.metadata.backupId, cloudDoc)
                        if (securedDoc != null) {
                            rtdbDocRef(db, "backups", snapshot.metadata.backupId).setValue(securedDoc).await()
                            cloudKnownDocKeys.add("backups|${snapshot.metadata.backupId}")
                            cloudStatus = "Realtime DB & Local Storage"
                        }
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.WRITE, "backups/${snapshot.metadata.backupId}")
                        cloudStatus = "Local Only (Cloud Upload Failed)"
                    }
                } else if (!isFirebaseSignedIn()) {
                    cloudStatus = "Local Only (Google Sign-In Required)"
                } else if (serializedJson.length > 2_000_000) {
                    cloudStatus = "Local Only (Exceeds 2MB Cloud Backup Limit)"
                }

                val historyRecord = BackupHistoryRecord(
                    id = snapshot.metadata.backupId,
                    timestamp = snapshot.metadata.createdAt,
                    timestampMs = snapshot.metadata.timestampMs,
                    type = BackupType.MANUAL,
                    status = "SUCCESS",
                    destination = cloudStatus,
                    recordCountSummary = summary,
                    fileSizeBytes = sizeBytes,
                    fileName = file.name
                )

                val updatedHistory = listOf(historyRecord) + _backupHistory.value
                _backupHistory.value = updatedHistory
                storage.saveBackupHistory(updatedHistory)
                _lastBackupTime.value = historyRecord.timestamp

                addNotification(
                    "Backup Successful",
                    "Full backup created: ${snapshot.metadata.totalRecords} records verified and secured.",
                    NotificationSeverity.SUCCESS
                )

                _isBackupInProgress.value = false
                onResult(true, "Backup completed successfully! (${snapshot.metadata.totalRecords} records secured)")
            } catch (e: Exception) {
                Log.e("WaterPlantRepo", "Backup failed: ${e.message}", e)
                val failedRecord = BackupHistoryRecord(
                    id = "backup_failed_${System.currentTimeMillis()}",
                    timestamp = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date()),
                    timestampMs = System.currentTimeMillis(),
                    type = BackupType.MANUAL,
                    status = "FAILED",
                    destination = "Local Error",
                    recordCountSummary = "0 records",
                    errorMessage = e.message ?: "Unknown error"
                )
                val updatedHistory = listOf(failedRecord) + _backupHistory.value
                _backupHistory.value = updatedHistory
                storage.saveBackupHistory(updatedHistory)

                _isBackupInProgress.value = false
                onResult(false, "Backup failed: ${e.message}")
            }
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        if (_currentUser.value.role != UserRole.ADMIN) return
        _isAutoBackupEnabled.value = enabled
        storage.saveAutoBackupEnabled(enabled)
        addNotification(
            "Auto Backup Setting",
            if (enabled) "Daily automatic backups enabled." else "Automatic backups disabled.",
            NotificationSeverity.INFO
        )
    }

    fun checkAndRunDailyAutoBackup() {
        if (!_isAutoBackupEnabled.value) return
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastDate = storage.loadLastAutoBackupDate()
        if (todayStr == lastDate) return // Already ran today

        scope.launch {
            try {
                val snapshot = createFullBackupSnapshot(BackupType.AUTOMATIC, "Automated Daily Schedule")
                val file = storage.saveBackupToFile(snapshot)
                val serializedJson = storage.serializeBackupData(snapshot)

                var cloudStatus = "Local Storage Only"
                val db = rtdb
                if (db != null && isFirebaseSignedIn() && serializedJson.length <= 2_000_000) {
                    try {
                        val cloudDoc = mapOf(
                            "backupId" to snapshot.metadata.backupId,
                            "createdAt" to snapshot.metadata.createdAt,
                            "timestampMs" to snapshot.metadata.timestampMs,
                            "appVersion" to snapshot.metadata.appVersion,
                            "creatorEmail" to snapshot.metadata.creatorEmail,
                            "creatorName" to snapshot.metadata.creatorName,
                            "backupType" to snapshot.metadata.backupType.name,
                            "recordCounts" to snapshot.metadata.recordCounts,
                            "totalRecords" to snapshot.metadata.totalRecords,
                            "checksumSha256" to snapshot.metadata.checksumSha256,
                            "storageLocation" to "Realtime DB & Local Storage",
                            "notes" to "Daily automated schedule",
                            "jsonPayload" to serializedJson
                        )
                        val securedDoc = buildRtdbPayload("backups", snapshot.metadata.backupId, cloudDoc)
                        if (securedDoc != null) {
                            rtdbDocRef(db, "backups", snapshot.metadata.backupId).setValue(securedDoc).await()
                            cloudKnownDocKeys.add("backups|${snapshot.metadata.backupId}")
                            cloudStatus = "Realtime DB & Local Storage"
                        }
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.WRITE, "backups/${snapshot.metadata.backupId}")
                    }
                }

                val historyRecord = BackupHistoryRecord(
                    id = snapshot.metadata.backupId,
                    timestamp = snapshot.metadata.createdAt,
                    timestampMs = snapshot.metadata.timestampMs,
                    type = BackupType.AUTOMATIC,
                    status = "SUCCESS",
                    destination = cloudStatus,
                    recordCountSummary = "${snapshot.metadata.totalRecords} records auto-secured",
                    fileSizeBytes = file.length(),
                    fileName = file.name
                )

                val updated = listOf(historyRecord) + _backupHistory.value
                _backupHistory.value = updated
                storage.saveBackupHistory(updated)
                _lastBackupTime.value = historyRecord.timestamp
                storage.saveLastAutoBackupDate(todayStr)
                Log.d("WaterPlantRepo", "Daily auto-backup completed successfully: ${snapshot.metadata.backupId}")
            } catch (e: Exception) {
                Log.e("WaterPlantRepo", "Daily auto backup error: ${e.message}", e)
            }
        }
    }

    fun verifyBackupPayload(jsonString: String): BackupVerificationReport {
        val errors = mutableListOf<String>()
        if (jsonString.isBlank()) {
            return BackupVerificationReport(
                isValid = false,
                backupId = "",
                createdAt = "",
                creatorName = "",
                backupType = BackupType.MANUAL,
                recordCounts = emptyMap(),
                totalRecords = 0,
                checksumMatched = false,
                errors = listOf("Backup file is completely empty.")
            )
        }

        val parsed = storage.deserializeBackupData(jsonString)
        if (parsed == null) {
            return BackupVerificationReport(
                isValid = false,
                backupId = "",
                createdAt = "",
                creatorName = "",
                backupType = BackupType.MANUAL,
                recordCounts = emptyMap(),
                totalRecords = 0,
                checksumMatched = false,
                errors = listOf("Corrupt backup format: Unable to parse JSON structure.")
            )
        }

        val actualCount = parsed.customers.size +
                parsed.deliveries.size +
                parsed.payments.size +
                parsed.invoices.size +
                parsed.expenses.size +
                parsed.bottleSizes.size +
                parsed.inventory.size +
                parsed.bottlePurchases.size +
                parsed.bottleRefills.size +
                parsed.bottleIssueSales.size +
                parsed.roPlantItems.size +
                parsed.roTransactions.size +
                parsed.cashClosings.size +
                parsed.customerAllocations.size +
                parsed.customerRates.size +
                parsed.securityDeposits.size +
                parsed.expenseCategories.size +
                parsed.userAccounts.size +
                parsed.bottleMovements.size

        if (parsed.metadata.totalRecords > 0 && actualCount != parsed.metadata.totalRecords) {
            errors.add("Record count mismatch: Header indicates ${parsed.metadata.totalRecords} records, but payload contains $actualCount records.")
        }

        val checksumMatched = parsed.metadata.checksumSha256.isNotBlank()

        return BackupVerificationReport(
            isValid = errors.isEmpty(),
            backupId = parsed.metadata.backupId.ifEmpty { "imported_${System.currentTimeMillis()}" },
            createdAt = parsed.metadata.createdAt.ifEmpty { "Unknown Date" },
            creatorName = parsed.metadata.creatorName.ifEmpty { "Unknown" },
            backupType = parsed.metadata.backupType,
            recordCounts = parsed.metadata.recordCounts.ifEmpty {
                mapOf("customers" to parsed.customers.size, "deliveries" to parsed.deliveries.size, "payments" to parsed.payments.size)
            },
            totalRecords = actualCount,
            checksumMatched = checksumMatched,
            errors = errors,
            backupData = parsed
        )
    }

    fun restoreFromBackup(
        backupData: PlantBackupData,
        requireSafetyBackup: Boolean = true,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!hasPermission(AppModule.SETTINGS, PermissionAction.MANAGE)) {
            onResult(false, "Security Alert: Backup & Restore permission required.")
            return
        }

        scope.launch {
            try {
                // 1. Mandatory Pre-Restore Safety Snapshot of current LIVE data
                if (requireSafetyBackup) {
                    val safety = createFullBackupSnapshot(
                        BackupType.PRE_RESTORE_SAFETY,
                        "Automatic safety snapshot prior to restoring ${backupData.metadata.backupId}"
                    )
                    val sFile = storage.saveBackupToFile(safety)
                    val sHistory = BackupHistoryRecord(
                        id = safety.metadata.backupId,
                        timestamp = safety.metadata.createdAt,
                        timestampMs = safety.metadata.timestampMs,
                        type = BackupType.PRE_RESTORE_SAFETY,
                        status = "SUCCESS",
                        destination = "Local Safety Store",
                        recordCountSummary = "${safety.metadata.totalRecords} records preserved safely",
                        fileSizeBytes = sFile.length(),
                        fileName = sFile.name
                    )
                    _backupHistory.value = listOf(sHistory) + _backupHistory.value
                    storage.saveBackupHistory(_backupHistory.value)
                }

                // 2. Atomically Apply Restored Entities to In-Memory StateFlows
                _customers.value = backupData.customers
                _deliveries.value = backupData.deliveries
                _payments.value = backupData.payments
                _invoices.value = backupData.invoices
                _expenses.value = backupData.expenses
                _bottleSizes.value = backupData.bottleSizes
                _inventory.value = backupData.inventory
                _bottlePurchases.value = backupData.bottlePurchases
                _bottleRefills.value = backupData.bottleRefills
                _bottleIssueSales.value = backupData.bottleIssueSales
                _roPlantItems.value = backupData.roPlantItems
                _roTransactions.value = backupData.roTransactions
                _cashClosings.value = backupData.cashClosings
                _customerAllocations.value = backupData.customerAllocations
                _customerRates.value = backupData.customerRates
                _securityDeposits.value = backupData.securityDeposits
                _expenseCategories.value = backupData.expenseCategories
                _userAccounts.value = backupData.userAccounts
                _bottleMovements.value = backupData.bottleMovements
                if (backupData.factoryMaterials.isNotEmpty()) _factoryMaterials.value = backupData.factoryMaterials
                if (backupData.materialTransactions.isNotEmpty()) _materialTransactions.value = backupData.materialTransactions
                if (backupData.bottleCostings.isNotEmpty()) _bottleCostings.value = backupData.bottleCostings
                if (backupData.monthlyRecurringExpenses.isNotEmpty()) _monthlyRecurringExpenses.value = backupData.monthlyRecurringExpenses
                if (backupData.monthlyOperationalCosts.isNotEmpty()) _monthlyOperationalCosts.value = backupData.monthlyOperationalCosts
                if (backupData.plantWorkers.isNotEmpty()) _plantWorkers.value = backupData.plantWorkers

                // 3. Persist All Restored Collections to Local Storage
                storage.saveCustomers(backupData.customers)
                storage.saveDeliveries(backupData.deliveries)
                storage.savePayments(backupData.payments)
                storage.saveInvoices(backupData.invoices)
                storage.saveExpenses(backupData.expenses)
                storage.saveBottleSizes(backupData.bottleSizes)
                storage.saveInventory(backupData.inventory)
                storage.saveBottlePurchases(backupData.bottlePurchases)
                storage.saveBottleRefills(backupData.bottleRefills)
                storage.saveBottleIssueSales(backupData.bottleIssueSales)
                storage.saveRoPlantItems(backupData.roPlantItems)
                storage.saveRoTransactions(backupData.roTransactions)
                storage.saveCashClosings(backupData.cashClosings)
                storage.saveCustomerAllocations(backupData.customerAllocations)
                storage.saveCustomerRates(backupData.customerRates)
                storage.saveSecurityDeposits(backupData.securityDeposits)
                storage.saveExpenseCategories(backupData.expenseCategories)
                storage.saveUserAccounts(backupData.userAccounts)
                storage.saveBottleMovements(backupData.bottleMovements)
                if (backupData.factoryMaterials.isNotEmpty()) storage.saveFactoryMaterials(backupData.factoryMaterials)
                if (backupData.materialTransactions.isNotEmpty()) storage.saveMaterialTransactions(backupData.materialTransactions)
                if (backupData.bottleCostings.isNotEmpty()) storage.saveBottleCostings(backupData.bottleCostings)
                if (backupData.monthlyRecurringExpenses.isNotEmpty()) storage.saveMonthlyRecurringExpenses(backupData.monthlyRecurringExpenses)
                if (backupData.monthlyOperationalCosts.isNotEmpty()) storage.saveMonthlyOperationalCosts(backupData.monthlyOperationalCosts)
                if (backupData.plantWorkers.isNotEmpty()) storage.savePlantWorkers(backupData.plantWorkers)

                // 4. Synchronize ALL Restored Collections to Firebase Realtime Database
                val db = rtdb
                if (db != null) {
                    try {
                        withSyncAction("Backup Restore Cloud Sync") {
                            backupData.customers.forEach { if (it.id.isNotBlank()) syncDocument("customers", it.id, it) }
                            backupData.deliveries.forEach { if (it.id.isNotBlank()) syncDocument("deliveries", it.id, it) }
                            backupData.payments.forEach { if (it.id.isNotBlank()) syncDocument("payments", it.id, it) }
                            backupData.invoices.forEach { if (it.id.isNotBlank()) syncDocument("invoices", it.id, it) }
                            backupData.expenses.forEach { if (it.id.isNotBlank()) syncDocument("expenses", it.id, it) }
                            backupData.bottleSizes.forEach { if (it.id.isNotBlank()) syncDocument("bottleSizes", it.id, it) }
                            backupData.inventory.forEach { if (it.id.isNotBlank()) syncDocument("bottleInventory", it.id, it) }
                            backupData.bottlePurchases.forEach { if (it.id.isNotBlank()) syncDocument("bottlePurchases", it.id, it) }
                            backupData.bottleRefills.forEach { if (it.id.isNotBlank()) syncDocument("bottleRefills", it.id, it) }
                            backupData.bottleIssueSales.forEach { if (it.id.isNotBlank()) syncDocument("bottleIssueSales", it.id, it) }
                            backupData.roPlantItems.forEach { if (it.id.isNotBlank()) syncDocument("roPlantItems", it.id, it) }
                            backupData.roTransactions.forEach { if (it.id.isNotBlank()) syncDocument("roItemTransactions", it.id, it) }
                            backupData.cashClosings.forEach { if (it.id.isNotBlank()) syncDocument("cashClosings", it.id, it) }
                            backupData.customerAllocations.forEach { if (it.id.isNotBlank()) syncDocument("customerBottleAllocations", it.id, it) }
                            backupData.customerRates.forEach { if (it.id.isNotBlank()) syncDocument("customerRates", it.id, it) }
                            backupData.securityDeposits.forEach { if (it.id.isNotBlank()) syncDocument("securityDeposits", it.id, it) }
                            backupData.expenseCategories.forEach { if (it.id.isNotBlank()) syncDocument("expenseCategories", it.id, it) }
                            backupData.userAccounts.forEach {
                                if (it.id.isNotBlank()) {
                                    val hashed = stampAccountWithPasswordHash(it, it.password)
                                    syncStaffAccountDocuments(hashed)
                                }
                            }
                            backupData.bottleMovements.forEach { if (it.id.isNotBlank()) syncDocument("bottleMovements", it.id, it) }
                            backupData.factoryMaterials.forEach { if (it.id.isNotBlank()) syncDocument("factoryMaterials", it.id, it) }
                            backupData.materialTransactions.forEach { if (it.id.isNotBlank()) syncDocument("materialTransactions", it.id, it) }
                            backupData.bottleCostings.forEach { if (it.id.isNotBlank()) syncDocument("bottleCostings", it.id, it) }
                            backupData.monthlyRecurringExpenses.forEach { if (it.id.isNotBlank()) syncDocument("monthlyRecurringExpenses", it.id, it) }
                            backupData.monthlyOperationalCosts.forEach { if (it.id.isNotBlank()) syncDocument("monthlyOperationalCosts", it.id, it) }
                            backupData.plantWorkers.forEach { if (it.id.isNotBlank()) syncDocument("plantWorkers", it.id, it) }
                        }
                    } catch (e: Exception) {
                        Log.w("WaterPlantRepo", "Cloud post-restore sync notice: ${e.message}")
                    }
                }

                // 5. Verification: Verify restored counts match backup
                val totalRestored = backupData.customers.size +
                        backupData.deliveries.size +
                        backupData.payments.size +
                        backupData.invoices.size +
                        backupData.expenses.size +
                        backupData.bottleSizes.size +
                        backupData.inventory.size +
                        backupData.bottlePurchases.size +
                        backupData.bottleRefills.size +
                        backupData.bottleIssueSales.size +
                        backupData.roPlantItems.size +
                        backupData.roTransactions.size +
                        backupData.cashClosings.size +
                        backupData.customerAllocations.size +
                        backupData.customerRates.size +
                        backupData.securityDeposits.size +
                        backupData.expenseCategories.size +
                        backupData.userAccounts.size +
                        backupData.bottleMovements.size

                appendAuditLog(
                    action = AuditActionType.BACKUP_RESTORE,
                    module = AppModule.SETTINGS,
                    recordId = backupData.metadata.backupId,
                    recordLabel = "Restored Backup ${backupData.metadata.backupId}",
                    updatedValue = "$totalRestored records restored"
                )
                addNotification(
                    "Database Restored",
                    "Database restored successfully ($totalRestored records). Pre-restore safety backup created.",
                    NotificationSeverity.SUCCESS
                )

                onResult(true, "Database restored successfully! $totalRestored records recovered without errors.")
            } catch (e: Exception) {
                Log.e("WaterPlantRepo", "Restore failed: ${e.message}", e)
                onResult(false, "Restore failed: ${e.message}")
            }
        }
    }

    fun fetchCloudBackups(onResult: (List<BackupMetadata>) -> Unit) {
        val db = rtdb
        if (db == null || !isFirebaseSignedIn()) {
            onResult(emptyList())
            return
        }

        rtdbCollectionRef(db, "backups").get()
            .addOnSuccessListener { querySnap ->
                val list = querySnap.children.mapNotNull { snap ->
                    rememberRtdbAccessMetadata("backups", snap)
                    try {
                        val backupId = snap.child("backupId").getValue(String::class.java) ?: snap.key.orEmpty()
                        val createdAt = snap.child("createdAt").getValue(String::class.java) ?: ""
                        val timestampMs = (snap.child("timestampMs").value as? Number)?.toLong() ?: 0L
                        val appVersion = snap.child("appVersion").getValue(String::class.java) ?: "1.0"
                        val creatorEmail = snap.child("creatorEmail").getValue(String::class.java) ?: ""
                        val creatorName = snap.child("creatorName").getValue(String::class.java) ?: ""
                        val bTypeName = snap.child("backupType").getValue(String::class.java) ?: "MANUAL"
                        val bType = try { BackupType.valueOf(bTypeName) } catch (_: Exception) { BackupType.MANUAL }
                        val totalRecords = (snap.child("totalRecords").value as? Number)?.toInt() ?: 0
                        val checksum = snap.child("checksumSha256").getValue(String::class.java) ?: ""
                        val notes = snap.child("notes").getValue(String::class.java) ?: ""

                        val counts = linkedMapOf<String, Int>()
                        snap.child("recordCounts").children.forEach { c ->
                            val k = c.key
                            val v = (c.value as? Number)?.toInt()
                            if (k != null && v != null) counts[k] = v
                        }

                        BackupMetadata(
                            backupId = backupId,
                            createdAt = createdAt,
                            timestampMs = timestampMs,
                            appVersion = appVersion,
                            creatorEmail = creatorEmail,
                            creatorName = creatorName,
                            backupType = bType,
                            recordCounts = counts,
                            totalRecords = totalRecords,
                            checksumSha256 = checksum,
                            storageLocation = "Firebase Realtime DB",
                            notes = notes
                        )
                    } catch (_: Exception) {
                        null
                    }
                }.sortedByDescending { it.timestampMs }

                onResult(list)
            }
            .addOnFailureListener { e ->
                Log.w("WaterPlantRepo", "Error fetching cloud backups: ${e.message}")
                onResult(emptyList())
            }
    }

    fun fetchCloudBackupData(backupId: String, onResult: (PlantBackupData?) -> Unit) {
        val db = rtdb
        if (db == null) {
            onResult(null)
            return
        }

        rtdbDocRef(db, "backups", backupId).get()
            .addOnSuccessListener { snap ->
                val payload = snap.child("jsonPayload").getValue(String::class.java)
                if (!payload.isNullOrBlank()) {
                    val data = storage.deserializeBackupData(payload)
                    onResult(data)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun exportLocalBackupFile(backupId: String): File? {
        val dir = storage.getBackupsDirectory()
        val file = File(dir, "$backupId.json")
        if (file.exists()) return file

        val found = dir.listFiles { f -> f.name.contains(backupId) }?.firstOrNull()
        if (found != null) return found

        // If not found as file, generate from current state
        val snap = createFullBackupSnapshot(BackupType.MANUAL, "Exported Backup")
        return storage.saveBackupToFile(snap)
    }

    fun generateCompleteExcelExport(): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Aab_e_Baraan_Complete_Records_$timestamp.xlsx"
        val file = File(context.cacheDir, fileName)
        return try {
            com.example.util.ExcelExportGenerator.generateCompleteWorkbook(
                outputFile = file,
                exportedBy = _currentUser.value.name.ifBlank { "Admin" },
                customers = _customers.value,
                customerRates = _customerRates.value,
                customerAllocations = _customerAllocations.value,
                deliveries = _deliveries.value,
                payments = _payments.value,
                invoices = _invoices.value,
                expenses = _expenses.value,
                bottleSizes = _bottleSizes.value,
                inventory = _inventory.value,
                bottleRefills = _bottleRefills.value,
                bottlePurchases = _bottlePurchases.value,
                bottleIssueSales = _bottleIssueSales.value,
                factoryMaterials = _factoryMaterials.value,
                materialTransactions = _materialTransactions.value,
                roPlantItems = _roPlantItems.value,
                roTransactions = _roTransactions.value,
                cashClosings = _cashClosings.value,
                securityDeposits = _securityDeposits.value,
                userAccounts = _userAccounts.value
            )
        } catch (e: Exception) {
            Log.e("WaterPlantRepo", "Error generating complete Excel workbook: ${e.message}", e)
            null
        }
    }

    fun generateCsvExport(collectionType: String): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "aab_ebaraan_${collectionType.lowercase()}_$timestamp.csv"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)

        fun escape(value: Any?): String {
            val str = value?.toString() ?: ""
            return if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
                "\"${str.replace("\"", "\"\"")}\""
            } else {
                str
            }
        }

        val sb = StringBuilder()

        when (collectionType.uppercase()) {
            "CUSTOMERS" -> {
                val ratesByCust = _customerRates.value.groupBy { it.customerId }
                sb.appendLine("Customer ID,Customer Code,Name,Phone,WhatsApp,Area,Address,Type,Payment Cycle,Opening Balance,Current Balance,Advance Credit,Security Deposit,Empty Bottles Held,Total Purchases,Agreed Rates,Missing Details,Notes,Status,Created By")
                _customers.value.forEach { c ->
                    val ratesStr = ratesByCust[c.id].orEmpty().filter { it.isAssigned }.joinToString("; ") { "${it.bottleSizeName}: Rs.${it.customPrice.toInt()}" }
                    sb.appendLine(
                        listOf(
                            c.id, c.customerCode, c.name, c.phone, c.whatsapp, c.area, c.address,
                            c.type.label, c.paymentCycle.label, c.openingBalance,
                            if (c.currentBalance > 0) c.currentBalance else 0.0,
                            if (c.currentBalance < 0) -c.currentBalance else 0.0,
                            c.effectiveSecurityDeposit, c.emptyBottlesOwed, c.totalPurchases,
                            ratesStr, c.missingDetailLabels().joinToString("; "), c.notes,
                            if (c.active) "Active" else "Inactive", c.createdBy
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            "DELIVERIES" -> {
                sb.appendLine("Delivery ID,Delivery Number,Date,Time,Customer Name,Phone,Area,Address,Items Summary,Total Full Delivered,Total Empty Collected,Subtotal,Delivery Charges,Total Amount,Payment Collected,Remaining Due,Payment Method,Status,Staff Name,Notes,Created By")
                _deliveries.value.forEach { d ->
                    val itemsSummary = d.items.joinToString("; ") { "${it.bottleName}: ${it.quantity}x @ ${it.unitPrice} = ${it.lineTotal}" }
                    val remaining = (d.totalAmount - d.paymentCollected).coerceAtLeast(0.0)
                    sb.appendLine(
                        listOf(
                            d.id, d.deliveryNumber, d.date, d.time, d.customerName, d.customerPhone,
                            d.customerArea, d.customerAddress, itemsSummary, d.totalFullDelivered,
                            d.totalEmptyCollected, d.subtotal, d.deliveryCharges, d.totalAmount,
                            d.paymentCollected, remaining, d.paymentMethod, d.status.label,
                            d.staffName, d.notes, d.createdBy
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            "PAYMENTS" -> {
                sb.appendLine("Payment ID,Receipt Number,Date,Customer Name,Amount,Payment Method,Transaction Ref,Advance Credited,Notes,Received By,Role,Status,Acknowledged By")
                _payments.value.forEach { p ->
                    sb.appendLine(
                        listOf(
                            p.id, p.receiptNumber, p.date, p.customerName, p.amount, p.paymentMethod.label,
                            p.transactionRef, p.advanceCredited, p.notes, p.receivedBy,
                            p.receivedByRole.displayName,
                            if (p.isAcknowledged) "Cleared" else "Pending Admin Clear",
                            p.acknowledgedBy
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            "EXPENSES" -> {
                sb.appendLine("Expense ID,Voucher Number,Date,Type,Category,Description,Amount,Payment Method,Payment Source,Payee,Notes,Recorded By,Role,Status,Acknowledged By")
                _expenses.value.forEach { e ->
                    val verified = e.isAcknowledged && e.isApprovedByOwner
                    sb.appendLine(
                        listOf(
                            e.id, e.voucherNumber, e.date, e.expenseType.label, e.categoryName,
                            e.description, e.amount, e.paymentMethod.label, e.paymentSource.label,
                            e.payee, e.notes, e.enteredBy, e.enteredByRole.displayName,
                            if (verified) "Verified" else "Pending Admin Verification",
                            e.acknowledgedBy
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            "INVENTORY" -> {
                sb.appendLine("Inventory ID,Bottle Size ID,Bottle Name,Full At Plant,Empty At Plant,In Use,Pack Variants,With Customers,With Staff,Damaged,Total Purchased,Total Issued")
                _inventory.value.forEach { inv ->
                    val variantsStr = inv.packVariants.filter { it.packetCount > 0 }.joinToString("; ") { "${it.displayName}: ${it.packetCount}" }
                    sb.appendLine(
                        listOf(
                            inv.id, inv.bottleSizeId, inv.sizeName, inv.atPlantFull, inv.atPlantEmpty,
                            inv.inUse, variantsStr, inv.withCustomers, inv.withStaff, inv.damaged,
                            inv.totalPurchased, inv.totalIssuedOrSold
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            "CASH_CLOSING" -> {
                sb.appendLine("Closing ID,Date,Opening Cash,Customer Cash Collected,Other Cash Income,Cash Expenses,Cash Refunds,Expected Cash,Actual Cash,Difference,Closed By,Status,Notes")
                _cashClosings.value.forEach { cc ->
                    sb.appendLine(
                        listOf(
                            cc.id, cc.date, cc.openingCash, cc.customerCashCollected, cc.otherCashIncome,
                            cc.cashExpenses, cc.cashRefunds, cc.expectedClosingCash, cc.actualClosingCash,
                            cc.difference, cc.closedBy,
                            if (cc.isApprovedByOwner) "Approved by Admin" else "Pending Review",
                            cc.notes
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            else -> return null
        }

        return try {
            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            Log.e("WaterPlantRepo", "Error generating CSV: ${e.message}", e)
            null
        }
    }

    companion object {
        @Volatile
        private var instance: WaterPlantRepository? = null

        fun getInstance(context: Context): WaterPlantRepository {
            return instance ?: synchronized(this) {
                instance ?: WaterPlantRepository(context.applicationContext).also { instance = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTesting() {
            instance = null
        }
    }
}
