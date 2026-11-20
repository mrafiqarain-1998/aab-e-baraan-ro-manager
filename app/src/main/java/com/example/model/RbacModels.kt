package com.example.model

import com.squareup.moshi.JsonClass

enum class AppModule(
    val title: String,
    val urduTitle: String,
    val description: String
) {
    DASHBOARD("Dashboard", "ڈیش بورڈ", "Plant overview, KPIs, and summary"),
    DELIVERIES("Deliveries", "ترسیلات", "Record and track customer water deliveries"),
    CUSTOMERS("Customers", "گاہک", "Customer directory, deposits, and allocations"),
    CUSTOMER_RATES("Customer Rates", "گاہک کے ریٹ", "Allow or block changing customer bottle rates"),
    PAYMENTS_BALANCES("Payments & Balances", "وصولیاں اور کھاتہ", "Receive payments, invoices, and cash closing"),
    BOTTLE_STOCK("Bottle Stock & Cost", "بوتل اسٹاک", "Add stock, manage sizes, and view costing"),
    NEW_ITEMS("Add New Items & Sizes", "نئے آئٹمز شامل کریں", "Allow or block adding new bottle sizes, materials, and categories"),
    BOTTLE_ISSUE("Bottle Issue", "بوتل اجراء", "Issue bottles to RO plant or customers"),
    REFILL_BATCHES("Refill Batches", "ریفل بیچ", "Bottle refilling and packet/carton conversion"),
    EXPENSES("Expenses", "اخراجات", "Record OPEX/CAPEX and recurring expenses"),
    FACTORY_MATERIALS("Factory Materials", "فیکٹری میٹریل", "RO plant spares, chemicals, and materials"),
    STAFF_MANAGEMENT("Staff Management", "عملہ کنٹرول", "Create/edit staff logins and permissions"),
    REPORTS("Reports", "رپورٹس", "Financial, customer, and bottle movement reports"),
    SETTINGS("Settings & Backup", "ترتیبات و بیک اپ", "Plant branches, backup & restore"),
    AUDIT_HISTORY("Audit History", "آڈٹ ہسٹری", "Immutable activity log and change tracking")
}

enum class PermissionAction(val label: String, val urduLabel: String) {
    VIEW("View", "دیکھیں"),
    ADD("Add", "شامل"),
    EDIT("Edit", "ترمیم"),
    DELETE("Delete", "حذف"),
    MANAGE("Manage", "انتظام"),
    APPROVE("Approve", "منظور")
}

enum class AuditActionType(val label: String) {
    LOGIN("Login"),
    LOGOUT("Logout"),
    CREATE("Created"),
    UPDATE("Updated"),
    DELETE("Deleted"),
    APPROVE("Approved"),
    PASSWORD_RESET("Password Reset"),
    SYNC("Cloud Sync"),
    BACKUP_RESTORE("Backup / Restore")
}

@JsonClass(generateAdapter = true)
data class AuditLogEntry(
    val id: String = "",
    val plantId: String = "aab_e_baraan_plant_01",
    val actorUid: String = "",
    val actorUsername: String = "",
    val actorName: String = "",
    val actorRole: String = "ADMIN",
    val action: String = AuditActionType.UPDATE.name,
    val module: String = AppModule.DASHBOARD.name,
    val recordId: String = "",
    val recordLabel: String = "",
    val previousValue: String = "",
    val updatedValue: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val dateTimeDisplay: String = "",
    val deviceInfo: String = ""
)

enum class SyncQueueStatus(val label: String, val urduLabel: String) {
    PENDING("Pending", "زیر التواء"),
    SYNCING("Syncing", "سنک جاری ہے"),
    SYNCED("Synced", "کلاؤڈ پر محفوظ"),
    FAILED("Failed", "ناکام (دوبارہ کوشش)")
}

enum class SyncOperationType {
    UPSERT,
    DELETE
}

@JsonClass(generateAdapter = true)
data class SyncQueueItem(
    val id: String = "",
    val collection: String = "",
    val docId: String = "",
    val plantId: String = "aab_e_baraan_plant_01",
    val operation: SyncOperationType = SyncOperationType.UPSERT,
    val payloadJson: String = "",
    val status: SyncQueueStatus = SyncQueueStatus.PENDING,
    val attemptCount: Int = 0,
    val lastError: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val nextRetryAtMillis: Long = 0L,
    val actorName: String = "",
    val baseRemoteUpdatedAtMillis: Long = 0L,
    val deltaFieldsJson: String = "",
    val extraFieldsJson: String = "",
    val actionGroupId: String = "",
    val actionTitle: String = ""
)

data class SyncActionGroup(
    val groupId: String,
    val title: String,
    val status: SyncQueueStatus,
    val itemCount: Int,
    val failedCount: Int,
    val attemptCount: Int,
    val lastError: String,
    val updatedAtMillis: Long,
    val itemIds: List<String>
)

@JsonClass(generateAdapter = true)
data class SyncConflictRecord(
    val id: String = "",
    val collection: String = "",
    val docId: String = "",
    val recordLabel: String = "",
    val localSummary: String = "",
    val remoteSummary: String = "",
    val resolution: String = "Kept newer cloud state",
    val timestampMillis: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

@JsonClass(generateAdapter = true)
data class PlantBranch(
    val id: String = "aab_e_baraan_plant_01",
    val name: String = "Aab-e-Baraan Main Plant",
    val city: String = "Main RO Plant",
    val active: Boolean = true,
    val isPrimary: Boolean = true
)

@JsonClass(generateAdapter = true)
data class BackendConfig(
    val staffAuthApiUrl: String = "",
    val activePlantId: String = "aab_e_baraan_plant_01"
)

@JsonClass(generateAdapter = true)
data class AppReleaseInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val downloadUrl: String = "",
    val githubRepo: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val updatedAtMillis: Long = 0L,
    val source: String = ""
)

object PermissionDefaults {
    const val DEFAULT_PLANT_ID = "aab_e_baraan_plant_01"

    fun fullAdminPermissions(): Map<String, List<String>> {
        val all = PermissionAction.entries.map { it.name }
        return AppModule.entries.associate { it.name to all }
    }

    fun defaultPermissionsForRole(role: UserRole): Map<String, List<String>> {
        return when (role) {
            UserRole.ADMIN -> fullAdminPermissions()
            UserRole.VIEW_ONLY -> mapOf(
                AppModule.DASHBOARD.name to listOf(PermissionAction.VIEW.name),
                AppModule.DELIVERIES.name to listOf(PermissionAction.VIEW.name),
                AppModule.CUSTOMERS.name to listOf(PermissionAction.VIEW.name),
                AppModule.CUSTOMER_RATES.name to listOf(PermissionAction.VIEW.name),
                AppModule.PAYMENTS_BALANCES.name to listOf(PermissionAction.VIEW.name),
                AppModule.BOTTLE_STOCK.name to listOf(PermissionAction.VIEW.name),
                AppModule.NEW_ITEMS.name to emptyList(),
                AppModule.BOTTLE_ISSUE.name to listOf(PermissionAction.VIEW.name),
                AppModule.REFILL_BATCHES.name to listOf(PermissionAction.VIEW.name),
                AppModule.EXPENSES.name to listOf(PermissionAction.VIEW.name),
                AppModule.FACTORY_MATERIALS.name to listOf(PermissionAction.VIEW.name),
                AppModule.STAFF_MANAGEMENT.name to emptyList(),
                AppModule.REPORTS.name to listOf(PermissionAction.VIEW.name),
                AppModule.SETTINGS.name to emptyList(),
                AppModule.AUDIT_HISTORY.name to emptyList()
            )
            UserRole.INCHARGE -> mapOf(
                AppModule.DASHBOARD.name to listOf(PermissionAction.VIEW.name),
                AppModule.DELIVERIES.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.CUSTOMERS.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name, PermissionAction.EDIT.name),
                AppModule.CUSTOMER_RATES.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name, PermissionAction.EDIT.name),
                AppModule.PAYMENTS_BALANCES.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.BOTTLE_STOCK.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.NEW_ITEMS.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.BOTTLE_ISSUE.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.REFILL_BATCHES.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.EXPENSES.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.FACTORY_MATERIALS.name to listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name),
                AppModule.STAFF_MANAGEMENT.name to emptyList(),
                AppModule.REPORTS.name to listOf(PermissionAction.VIEW.name),
                AppModule.SETTINGS.name to emptyList(),
                AppModule.AUDIT_HISTORY.name to emptyList()
            )
        }
    }

    fun normalizePermissions(
        input: Map<String, List<String>>?,
        role: UserRole,
        isSuperAdmin: Boolean = false,
        explicitMode: Boolean = false
    ): Map<String, List<String>> {
        if (isSuperAdmin || role == UserRole.ADMIN) {
            return fullAdminPermissions()
        }
        val fallback = defaultPermissionsForRole(role)
        val validActionNames = PermissionAction.entries.map { it.name }.toSet()
        val out = linkedMapOf<String, List<String>>()
        val treatMissingAsDenied = explicitMode || (input != null && input.isNotEmpty())

        AppModule.entries.forEach { mod ->
            val raw = when {
                input != null && mod.name in input -> input[mod.name].orEmpty()
                treatMissingAsDenied -> emptyList()
                else -> fallback[mod.name].orEmpty()
            }
            val cleaned = raw.map { it.trim().uppercase() }
                .filter { it in validActionNames }
                .distinct()
                .toMutableList()
            if (cleaned.isNotEmpty() && PermissionAction.VIEW.name !in cleaned) {
                cleaned.add(0, PermissionAction.VIEW.name)
            }
            out[mod.name] = cleaned
        }
        return out
    }

    fun hasModuleAction(
        profile: UserProfile,
        module: AppModule,
        action: PermissionAction
    ): Boolean {
        if (!profile.active) return false
        if (profile.isSuperAdmin || profile.role == UserRole.ADMIN) return true
        val effectiveMap = if (!profile.hasExplicitPermissions && profile.permissions.isEmpty()) {
            defaultPermissionsForRole(profile.role)
        } else {
            profile.permissions
        }
        val actions = effectiveMap[module.name].orEmpty()
        return action.name in actions
    }
}
