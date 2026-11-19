package com.example.model

enum class PaymentMethod(val label: String, val isCash: Boolean) {
    CASH("Cash (Naqd)", true),
    JAZZCASH("JazzCash", false),
    EASYPAISA("Easypaisa", false),
    BANK_TRANSFER("Bank Transfer", false),
    ADVANCE_ADJUSTMENT("Advance Balance (Peshgi)", false)
}

data class PaymentAllocation(
    val invoiceId: String = "",
    val invoiceNumber: String = "",
    val allocatedAmount: Double = 0.0
)

data class Payment(
    val id: String = "",
    val receiptNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val allocations: List<PaymentAllocation> = emptyList(),
    val advanceCredited: Double = 0.0, // Any payment exceeding total unpaid invoices becomes credit
    val transactionRef: String = "",
    val notes: String = "",
    val date: String = "",
    val receivedBy: String = "",
    val receivedByRole: UserRole = UserRole.ADMIN,
    val isAcknowledged: Boolean = true,
    val acknowledgedBy: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class LedgerEntry(
    val id: String = "",
    val customerId: String = "",
    val date: String = "",
    val type: String = "", // "INVOICE", "PAYMENT", "OPENING_BALANCE", "ADJUSTMENT"
    val referenceNumber: String = "",
    val description: String = "",
    val debitAmount: Double = 0.0,  // Increases customer debt (Invoice)
    val creditAmount: Double = 0.0, // Decreases customer debt (Payment)
    val runningBalance: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
