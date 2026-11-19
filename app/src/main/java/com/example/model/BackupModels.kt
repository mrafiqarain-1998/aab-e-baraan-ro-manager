package com.example.model

import com.squareup.moshi.JsonClass

enum class BackupType(val displayName: String, val urduLabel: String) {
    MANUAL("Manual Backup", "دستی بیک اپ"),
    AUTOMATIC("Daily Auto Backup", "روزانہ خودکار بیک اپ"),
    PRE_RESTORE_SAFETY("Safety Pre-Restore Snapshot", "بحالی سے قبل حفاظتی اسنیپ شاٹ")
}

@JsonClass(generateAdapter = true)
data class BackupMetadata(
    val backupId: String = "",
    val createdAt: String = "",
    val timestampMs: Long = 0L,
    val appVersion: String = "1.0",
    val creatorEmail: String = "",
    val creatorName: String = "",
    val backupType: BackupType = BackupType.MANUAL,
    val recordCounts: Map<String, Int> = emptyMap(),
    val totalRecords: Int = 0,
    val checksumSha256: String = "",
    val storageLocation: String = "Cloud & Local Storage",
    val notes: String = ""
)

@JsonClass(generateAdapter = true)
data class PlantBackupData(
    val metadata: BackupMetadata = BackupMetadata(),
    val customers: List<Customer> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val bottleSizes: List<BottleSize> = emptyList(),
    val inventory: List<BottleInventory> = emptyList(),
    val bottlePurchases: List<BottlePurchaseRecord> = emptyList(),
    val bottleRefills: List<BottleRefillRecord> = emptyList(),
    val bottleIssueSales: List<BottleIssueSaleRecord> = emptyList(),
    val roPlantItems: List<RoPlantItem> = emptyList(),
    val roTransactions: List<RoItemTransaction> = emptyList(),
    val cashClosings: List<CashClosing> = emptyList(),
    val customerAllocations: List<CustomerBottleAllocation> = emptyList(),
    val customerRates: List<CustomerRate> = emptyList(),
    val securityDeposits: List<SecurityDepositTransaction> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    val userAccounts: List<UserAccount> = emptyList(),
    val bottleMovements: List<BottleMovement> = emptyList(),
    val factoryMaterials: List<FactoryMaterial> = emptyList(),
    val materialTransactions: List<MaterialTransaction> = emptyList(),
    val bottleCostings: List<BottleCostingRecord> = emptyList(),
    val monthlyRecurringExpenses: List<MonthlyRecurringExpense> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupHistoryRecord(
    val id: String = "",
    val timestamp: String = "",
    val timestampMs: Long = 0L,
    val type: BackupType = BackupType.MANUAL,
    val status: String = "SUCCESS", // "SUCCESS" or "FAILED"
    val destination: String = "Cloud & Local Storage",
    val recordCountSummary: String = "",
    val fileSizeBytes: Long = 0L,
    val fileName: String = "",
    val errorMessage: String? = null
)

data class BackupVerificationReport(
    val isValid: Boolean,
    val backupId: String,
    val createdAt: String,
    val creatorName: String,
    val backupType: BackupType,
    val recordCounts: Map<String, Int>,
    val totalRecords: Int,
    val checksumMatched: Boolean,
    val errors: List<String> = emptyList(),
    val backupData: PlantBackupData? = null
)
