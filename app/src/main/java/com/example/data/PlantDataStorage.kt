package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

class PlantDataStorage(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun <T> saveList(fileName: String, list: List<T>, elementClass: Class<T>) {
        try {
            val type = Types.newParameterizedType(List::class.java, elementClass)
            val adapter = moshi.adapter<List<T>>(type)
            val json = adapter.toJson(list)
            val file = File(context.filesDir, fileName)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $fileName: ${e.message}", e)
        }
    }

    private fun <T> loadList(fileName: String, elementClass: Class<T>): List<T>? {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return null
            val json = file.readText()
            if (json.isBlank()) return null
            val type = Types.newParameterizedType(List::class.java, elementClass)
            val adapter = moshi.adapter<List<T>>(type)
            return adapter.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $fileName: ${e.message}", e)
            return null
        }
    }

    // 1. Customers
    fun saveCustomers(list: List<Customer>) = saveList("db_customers.json", list, Customer::class.java)
    fun loadCustomers(): List<Customer>? = loadList("db_customers.json", Customer::class.java)

    // 2. Deliveries
    fun saveDeliveries(list: List<Delivery>) = saveList("db_deliveries.json", list, Delivery::class.java)
    fun loadDeliveries(): List<Delivery>? = loadList("db_deliveries.json", Delivery::class.java)

    // 3. Payments
    fun savePayments(list: List<Payment>) = saveList("db_payments.json", list, Payment::class.java)
    fun loadPayments(): List<Payment>? = loadList("db_payments.json", Payment::class.java)

    // 4. Invoices
    fun saveInvoices(list: List<Invoice>) = saveList("db_invoices.json", list, Invoice::class.java)
    fun loadInvoices(): List<Invoice>? = loadList("db_invoices.json", Invoice::class.java)

    // 5. Expenses
    fun saveExpenses(list: List<Expense>) = saveList("db_expenses.json", list, Expense::class.java)
    fun loadExpenses(): List<Expense>? = loadList("db_expenses.json", Expense::class.java)

    // 6. Bottle Sizes
    fun saveBottleSizes(list: List<BottleSize>) = saveList("db_bottle_sizes.json", list, BottleSize::class.java)
    fun loadBottleSizes(): List<BottleSize>? = loadList("db_bottle_sizes.json", BottleSize::class.java)

    // 7. Bottle Inventory
    fun saveInventory(list: List<BottleInventory>) = saveList("db_bottle_inventory.json", list, BottleInventory::class.java)
    fun loadInventory(): List<BottleInventory>? = loadList("db_bottle_inventory.json", BottleInventory::class.java)

    // 8. Bottle Purchases
    fun saveBottlePurchases(list: List<BottlePurchaseRecord>) = saveList("db_bottle_purchases.json", list, BottlePurchaseRecord::class.java)
    fun loadBottlePurchases(): List<BottlePurchaseRecord>? = loadList("db_bottle_purchases.json", BottlePurchaseRecord::class.java)

    // 9. Bottle Refills
    fun saveBottleRefills(list: List<BottleRefillRecord>) = saveList("db_bottle_refills.json", list, BottleRefillRecord::class.java)
    fun loadBottleRefills(): List<BottleRefillRecord>? = loadList("db_bottle_refills.json", BottleRefillRecord::class.java)

    // 10. Bottle Issue & Sales
    fun saveBottleIssueSales(list: List<BottleIssueSaleRecord>) = saveList("db_bottle_issue_sales.json", list, BottleIssueSaleRecord::class.java)
    fun loadBottleIssueSales(): List<BottleIssueSaleRecord>? = loadList("db_bottle_issue_sales.json", BottleIssueSaleRecord::class.java)

    // 11. RO Plant Items
    fun saveRoPlantItems(list: List<RoPlantItem>) = saveList("db_ro_plant_items.json", list, RoPlantItem::class.java)
    fun loadRoPlantItems(): List<RoPlantItem>? = loadList("db_ro_plant_items.json", RoPlantItem::class.java)

    // 12. RO Item Transactions
    fun saveRoTransactions(list: List<RoItemTransaction>) = saveList("db_ro_transactions.json", list, RoItemTransaction::class.java)
    fun loadRoTransactions(): List<RoItemTransaction>? = loadList("db_ro_transactions.json", RoItemTransaction::class.java)

    // 13. Cash Closings
    fun saveCashClosings(list: List<CashClosing>) = saveList("db_cash_closings.json", list, CashClosing::class.java)
    fun loadCashClosings(): List<CashClosing>? = loadList("db_cash_closings.json", CashClosing::class.java)

    // 14. Customer Allocations
    fun saveCustomerAllocations(list: List<CustomerBottleAllocation>) = saveList("db_customer_allocations.json", list, CustomerBottleAllocation::class.java)
    fun loadCustomerAllocations(): List<CustomerBottleAllocation>? = loadList("db_customer_allocations.json", CustomerBottleAllocation::class.java)

    // 15. Customer Rates
    fun saveCustomerRates(list: List<CustomerRate>) = saveList("db_customer_rates.json", list, CustomerRate::class.java)
    fun loadCustomerRates(): List<CustomerRate>? = loadList("db_customer_rates.json", CustomerRate::class.java)

    // 16. Security Deposits
    fun saveSecurityDeposits(list: List<SecurityDepositTransaction>) = saveList("db_security_deposits.json", list, SecurityDepositTransaction::class.java)
    fun loadSecurityDeposits(): List<SecurityDepositTransaction>? = loadList("db_security_deposits.json", SecurityDepositTransaction::class.java)

    // 17. Expense Categories
    fun saveExpenseCategories(list: List<ExpenseCategory>) = saveList("db_expense_categories.json", list, ExpenseCategory::class.java)
    fun loadExpenseCategories(): List<ExpenseCategory>? = loadList("db_expense_categories.json", ExpenseCategory::class.java)

    // 18. User Accounts (Zero-plaintext-on-disk: stores salted PBKDF2 hash & salt only)
    fun saveUserAccounts(list: List<UserAccount>) {
        val sanitized = list.map { acc ->
            val rawPass = acc.password.trim()
            val salt = if (acc.passwordSalt.isNotBlank()) acc.passwordSalt else if (rawPass.isNotEmpty()) com.example.util.PasswordHashUtil.generateSalt() else ""
            val hash = if (rawPass.isNotEmpty() && salt.isNotBlank()) {
                com.example.util.PasswordHashUtil.hashPassword(rawPass, salt)
            } else {
                acc.passwordHash
            }
            acc.copy(
                password = "",
                passwordSalt = salt,
                passwordHash = hash
            )
        }
        saveList("db_user_accounts.json", sanitized, UserAccount::class.java)
    }

    fun loadUserAccounts(): List<UserAccount>? = loadList("db_user_accounts.json", UserAccount::class.java)

    // 19. Bottle Movements
    fun saveBottleMovements(list: List<BottleMovement>) = saveList("db_bottle_movements.json", list, BottleMovement::class.java)
    fun loadBottleMovements(): List<BottleMovement>? = loadList("db_bottle_movements.json", BottleMovement::class.java)

    // 20. Factory Materials
    fun saveFactoryMaterials(list: List<FactoryMaterial>) = saveList("db_factory_materials.json", list, FactoryMaterial::class.java)
    fun loadFactoryMaterials(): List<FactoryMaterial>? = loadList("db_factory_materials.json", FactoryMaterial::class.java)

    // 21. Material Transactions
    fun saveMaterialTransactions(list: List<MaterialTransaction>) = saveList("db_material_transactions.json", list, MaterialTransaction::class.java)
    fun loadMaterialTransactions(): List<MaterialTransaction>? = loadList("db_material_transactions.json", MaterialTransaction::class.java)

    // 22. Bottle Costings (Admin Only)
    fun saveBottleCostings(list: List<BottleCostingRecord>) = saveList("db_bottle_costings.json", list, BottleCostingRecord::class.java)
    fun loadBottleCostings(): List<BottleCostingRecord>? = loadList("db_bottle_costings.json", BottleCostingRecord::class.java)

    // 23. Monthly Recurring Expenses (Admin Only)
    fun saveMonthlyRecurringExpenses(list: List<MonthlyRecurringExpense>) = saveList("db_monthly_recurring_expenses.json", list, MonthlyRecurringExpense::class.java)
    fun loadMonthlyRecurringExpenses(): List<MonthlyRecurringExpense>? = loadList("db_monthly_recurring_expenses.json", MonthlyRecurringExpense::class.java)

    // 24. Password Reset Requests
    fun savePasswordResetRequests(list: List<PasswordResetRequest>) = saveList("db_password_resets.json", list, PasswordResetRequest::class.java)
    fun loadPasswordResetRequests(): List<PasswordResetRequest>? = loadList("db_password_resets.json", PasswordResetRequest::class.java)

    // 25. Offline Sync Queue & Conflict History
    fun saveSyncQueue(list: List<SyncQueueItem>) = saveList("db_sync_queue.json", list, SyncQueueItem::class.java)
    fun loadSyncQueue(): List<SyncQueueItem> = loadList("db_sync_queue.json", SyncQueueItem::class.java) ?: emptyList()

    fun saveSyncConflicts(list: List<SyncConflictRecord>) = saveList("db_sync_conflicts.json", list, SyncConflictRecord::class.java)
    fun loadSyncConflicts(): List<SyncConflictRecord> = loadList("db_sync_conflicts.json", SyncConflictRecord::class.java) ?: emptyList()

    // 26. Immutable Audit Logs (Local Cache + Synced History)
    fun saveAuditLogs(list: List<AuditLogEntry>) = saveList("db_audit_logs.json", list, AuditLogEntry::class.java)
    fun loadAuditLogs(): List<AuditLogEntry> = loadList("db_audit_logs.json", AuditLogEntry::class.java) ?: emptyList()

    // 27. Multi-Branch Plant List & Active Plant ID
    fun saveBranches(list: List<PlantBranch>) = saveList("db_plant_branches.json", list, PlantBranch::class.java)
    fun loadBranches(): List<PlantBranch> = loadList("db_plant_branches.json", PlantBranch::class.java) ?: emptyList()

    fun saveActivePlantId(plantId: String) {
        try {
            File(context.filesDir, "db_active_plant_id.txt").writeText(plantId.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving active plant ID", e)
        }
    }

    fun loadActivePlantId(): String {
        return try {
            val f = File(context.filesDir, "db_active_plant_id.txt")
            if (f.exists()) f.readText().trim().ifEmpty { PermissionDefaults.DEFAULT_PLANT_ID }
            else PermissionDefaults.DEFAULT_PLANT_ID
        } catch (_: Exception) {
            PermissionDefaults.DEFAULT_PLANT_ID
        }
    }

    // 28. Backend Config
    fun saveBackendConfig(config: BackendConfig) {
        try {
            val json = moshi.adapter(BackendConfig::class.java).toJson(config)
            File(context.filesDir, "db_backend_config.json").writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving backend config", e)
        }
    }

    fun loadBackendConfig(): BackendConfig {
        return try {
            val f = File(context.filesDir, "db_backend_config.json")
            if (f.exists()) moshi.adapter(BackendConfig::class.java).fromJson(f.readText()) ?: BackendConfig()
            else BackendConfig()
        } catch (_: Exception) {
            BackendConfig()
        }
    }

    // 29. App Release Update Cache (6-hour throttle keeps Spark Firestore reads near zero)
    fun saveAppReleaseInfo(info: AppReleaseInfo) {
        try {
            val json = moshi.adapter(AppReleaseInfo::class.java).toJson(info)
            File(context.filesDir, "db_app_release_info.json").writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app release cache", e)
        }
    }

    fun loadAppReleaseInfo(): AppReleaseInfo? {
        return try {
            val f = File(context.filesDir, "db_app_release_info.json")
            if (f.exists()) moshi.adapter(AppReleaseInfo::class.java).fromJson(f.readText())
            else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveLastUpdateCheckMillis(ts: Long) {
        try {
            File(context.filesDir, "db_last_update_check.txt").writeText(ts.toString())
        } catch (_: Exception) {
        }
    }

    fun loadLastUpdateCheckMillis(): Long {
        return try {
            val f = File(context.filesDir, "db_last_update_check.txt")
            if (f.exists()) f.readText().trim().toLongOrNull() ?: 0L else 0L
        } catch (_: Exception) {
            0L
        }
    }

    // 20. App Settings (Dark Mode, Preferences, Session)
    fun saveDarkMode(isDarkMode: Boolean) {
        try {
            val file = File(context.filesDir, "db_setting_dark_mode.txt")
            file.writeText(isDarkMode.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving dark mode setting", e)
        }
    }

    fun loadDarkMode(): Boolean? {
        try {
            val file = File(context.filesDir, "db_setting_dark_mode.txt")
            if (file.exists()) {
                return file.readText().trim().toBoolean()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dark mode setting", e)
        }
        return null
    }

    fun saveLoginState(isLoggedIn: Boolean) {
        try {
            val file = File(context.filesDir, "db_session_login.txt")
            file.writeText(isLoggedIn.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving login state", e)
        }
    }

    fun loadLoginState(): Boolean {
        try {
            val file = File(context.filesDir, "db_session_login.txt")
            if (file.exists()) {
                return file.readText().trim().toBoolean()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading login state", e)
        }
        return false
    }

    fun saveCurrentProfile(profile: UserProfile) {
        try {
            val adapter = moshi.adapter(UserProfile::class.java)
            val json = adapter.toJson(profile)
            File(context.filesDir, "db_current_profile.json").writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
        }
    }

    fun loadCurrentProfile(): UserProfile? {
        try {
            val file = File(context.filesDir, "db_current_profile.json")
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotBlank()) {
                    return moshi.adapter(UserProfile::class.java).fromJson(json)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile", e)
        }
        return null
    }

    // 21. Backup & Restore System
    private val backupAdapter = moshi.adapter(PlantBackupData::class.java)
    private val backupHistoryType = Types.newParameterizedType(List::class.java, BackupHistoryRecord::class.java)
    private val backupHistoryAdapter = moshi.adapter<List<BackupHistoryRecord>>(backupHistoryType)

    fun getBackupsDirectory(): File {
        val dir = File(context.filesDir, "backups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun serializeBackupData(backupData: PlantBackupData): String {
        return backupAdapter.indent("  ").toJson(backupData)
    }

    fun deserializeBackupData(json: String): PlantBackupData? {
        return try {
            backupAdapter.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse backup JSON: ${e.message}", e)
            null
        }
    }

    fun saveBackupToFile(backupData: PlantBackupData): File {
        val backupsDir = getBackupsDirectory()
        val fileName = "${backupData.metadata.backupId}.json"
        val file = File(backupsDir, fileName)
        val json = serializeBackupData(backupData)
        file.writeText(json)
        return file
    }

    fun loadBackupFromFile(file: File): PlantBackupData? {
        return try {
            if (!file.exists()) return null
            val json = file.readText()
            deserializeBackupData(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading backup file ${file.name}: ${e.message}", e)
            null
        }
    }

    fun listLocalBackupFiles(): List<File> {
        val dir = getBackupsDirectory()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun saveBackupHistory(history: List<BackupHistoryRecord>) {
        try {
            val json = backupHistoryAdapter.toJson(history)
            File(context.filesDir, "db_backup_history.json").writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving backup history", e)
        }
    }

    fun loadBackupHistory(): List<BackupHistoryRecord> {
        try {
            val file = File(context.filesDir, "db_backup_history.json")
            if (file.exists()) {
                val json = file.readText()
                if (json.isNotBlank()) {
                    return backupHistoryAdapter.fromJson(json) ?: emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading backup history", e)
        }
        return emptyList()
    }

    fun saveAutoBackupEnabled(enabled: Boolean) {
        try {
            val file = File(context.filesDir, "db_setting_auto_backup.txt")
            file.parentFile?.mkdirs()
            file.writeText(enabled.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auto-backup setting", e)
        }
    }

    fun loadAutoBackupEnabled(): Boolean {
        try {
            val file = File(context.filesDir, "db_setting_auto_backup.txt")
            if (file.exists()) {
                val txt = file.readText().trim()
                if (txt.isNotEmpty()) {
                    return txt.toBoolean()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading auto-backup setting", e)
        }
        return true // Default enabled for data safety
    }

    fun saveLastAutoBackupDate(dateStr: String) {
        try {
            val file = File(context.filesDir, "db_setting_last_auto_backup_date.txt")
            file.parentFile?.mkdirs()
            file.writeText(dateStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last auto backup date", e)
        }
    }

    fun loadLastAutoBackupDate(): String? {
        try {
            val file = File(context.filesDir, "db_setting_last_auto_backup_date.txt")
            if (file.exists()) {
                return file.readText().trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading last auto backup date", e)
        }
        return null
    }

    fun saveCleanStateInitialized(initialized: Boolean) {
        try {
            val file = File(context.filesDir, "db_clean_slate_v2.txt")
            file.writeText(initialized.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving clean state flag", e)
        }
    }

    fun loadCleanStateInitialized(): Boolean {
        try {
            val file = File(context.filesDir, "db_clean_slate_v2.txt")
            if (file.exists()) {
                return file.readText().trim().toBoolean()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading clean state flag", e)
        }
        return false
    }

    fun markOldSampleDataPurged() {
        try {
            val file = File(context.filesDir, "db_clean_slate_v3_wiped.txt")
            if (!file.exists()) {
                file.writeText("WIPED_CLEAN")
            }
            val v4File = File(context.filesDir, "db_clean_slate_v4_materials_costings.txt")
            if (!v4File.exists()) {
                v4File.writeText("CLEANED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking sample data purged", e)
        }
    }

    fun purgeOldSampleDataIfRequired(): Boolean {
        var v3Purged = false
        try {
            val file = File(context.filesDir, "db_clean_slate_v3_wiped.txt")
            if (!file.exists()) {
                File(context.filesDir, "db_customers.json").delete()
                File(context.filesDir, "db_deliveries.json").delete()
                File(context.filesDir, "db_payments.json").delete()
                File(context.filesDir, "db_invoices.json").delete()
                File(context.filesDir, "db_expenses.json").delete()
                File(context.filesDir, "db_purchases.json").delete()
                File(context.filesDir, "db_refills.json").delete()
                File(context.filesDir, "db_issuesales.json").delete()
                File(context.filesDir, "db_ro_items.json").delete()
                File(context.filesDir, "db_ro_transactions.json").delete()
                File(context.filesDir, "db_customer_allocations.json").delete()
                File(context.filesDir, "db_customer_rates.json").delete()
                File(context.filesDir, "db_security_deposits.json").delete()
                File(context.filesDir, "db_expense_categories.json").delete()
                File(context.filesDir, "db_bottle_movements.json").delete()
                File(context.filesDir, "db_password_resets.json").delete()
                file.writeText("WIPED_CLEAN")
                v3Purged = true
            }

            val v4File = File(context.filesDir, "db_clean_slate_v4_materials_costings.txt")
            if (!v4File.exists()) {
                val legacyMaterialIds = setOf(
                    "mat_caps_19l", "mat_seals_19l", "mat_labels_19l",
                    "mat_cartons_500ml", "mat_polythene_rolls", "mat_spun_filter_20in", "mat_antiscalant"
                )
                val legacyCostingIds = setOf("cost_19l_default", "cost_500ml_default")
                val legacyMonthlyIds = setOf("mre_rent_1", "mre_maint_1")

                val usedMatIds = loadMaterialTransactions().orEmpty().map { it.materialId }.toSet()
                loadFactoryMaterials()?.let { existing ->
                    val cleaned = existing.filterNot { it.id in legacyMaterialIds && it.id !in usedMatIds }
                    if (cleaned.size != existing.size) saveFactoryMaterials(cleaned)
                }
                loadBottleCostings()?.let { existing ->
                    val cleaned = existing.filterNot { it.id in legacyCostingIds }
                    if (cleaned.size != existing.size) saveBottleCostings(cleaned)
                }
                loadMonthlyRecurringExpenses()?.let { existing ->
                    val cleaned = existing.filterNot { it.id in legacyMonthlyIds }
                    if (cleaned.size != existing.size) saveMonthlyRecurringExpenses(cleaned)
                }
                v4File.writeText("CLEANED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging old sample data", e)
        }
        return v3Purged
    }

    fun computeSha256(content: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun saveLastUser(username: String) {
        try {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_logged_in_user", username)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving last user", e)
        }
    }

    fun getLastUser(): String? {
        return try {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .getString("last_logged_in_user", null)
        } catch (e: Exception) {
            null
        }
    }

    fun loadSeenRecordKeys(userKey: String): Set<String> {
        return try {
            val safeKey = userKey.trim().lowercase().ifBlank { "default" }
            val prefs = context.getSharedPreferences("app_seen_records", Context.MODE_PRIVATE)
            prefs.getStringSet("seen_$safeKey", emptySet())?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading seen record keys", e)
            emptySet()
        }
    }

    fun saveSeenRecordKeys(userKey: String, keys: Set<String>) {
        try {
            val safeKey = userKey.trim().lowercase().ifBlank { "default" }
            val trimmed = if (keys.size > 4000) keys.toList().takeLast(4000).toSet() else keys.toSet()
            context.getSharedPreferences("app_seen_records", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("seen_$safeKey", trimmed)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving seen record keys", e)
        }
    }

    fun getOrInitSeenBaselineMillis(userKey: String): Long {
        return try {
            val safeKey = userKey.trim().lowercase().ifBlank { "default" }
            val prefs = context.getSharedPreferences("app_seen_records", Context.MODE_PRIVATE)
            val key = "baseline_$safeKey"
            val existing = prefs.getLong(key, 0L)
            if (existing > 0L) {
                existing
            } else {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(key, now).apply()
                now
            }
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    companion object {
        private const val TAG = "PlantDataStorage"
    }
}
