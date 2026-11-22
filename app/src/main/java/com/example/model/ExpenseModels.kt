package com.example.model

enum class ExpenseType(val label: String, val urduLabel: String) {
    OPEX("OPEX (Operating)", "آپریٹنگ اخراجات"),
    CAPEX("CAPEX (Capital / Assets)", "کیپٹل اثاثہ جات")
}

data class ExpenseCategory(
    val id: String = "",
    val name: String = "",
    val urduName: String = "",
    val type: ExpenseType = ExpenseType.OPEX,
    val iconName: String = "receipt",
    val isDefault: Boolean = true,
    val isInventoryCategory: Boolean = false,
    val businessId: String = "aab_e_baraan_plant_01"
)

data class ExpenseInventoryLineItem(
    val itemType: String = "MATERIAL", // "MATERIAL" or "BOTTLE"
    val itemId: String = "",
    val itemName: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val uom: String = "",
    val quantity: Double = 0.0,
    val unitRate: Double = 0.0,
    val lineTotal: Double = 0.0,
    val transactionId: String = ""
)

data class Expense(
    val id: String = "",
    val voucherNumber: String = "",
    val date: String = "",
    val expenseType: ExpenseType = ExpenseType.OPEX,
    val categoryId: String = "",
    val categoryName: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH, // Cash or Bank/Online
    val paymentSource: PaymentSource = PaymentSource.PAY_FROM_SALE,
    val payee: String = "",
    val notes: String = "",
    val enteredBy: String = "",
    val enteredByRole: UserRole = UserRole.ADMIN,
    val isAcknowledged: Boolean = true,
    val acknowledgedBy: String = "",
    val isApprovedByOwner: Boolean = true,
    val businessId: String = "aab_e_baraan_plant_01",
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Backward-compatible Expense <-> Inventory & Staff Salary linkage fields
    val isInventoryExpense: Boolean = false,
    val inventoryItems: List<ExpenseInventoryLineItem> = emptyList(),
    val linkedInventoryType: String = "",          // "", "MATERIAL", "BOTTLE"
    val linkedInventoryItemId: String = "",
    val linkedInventoryItemName: String = "",
    val linkedInventoryQty: Double = 0.0,
    val linkedInventoryUnitCost: Double = 0.0,
    val linkedInventoryTransactionId: String = "",
    val invoiceNumber: String = "",
    val staffMemberId: String = "",
    val staffMemberName: String = "",
    val lastEditedAt: Long = 0L,
    val lastEditedBy: String = ""
) {
    /**
     * Unified inventory line items across both new multi-item inventory expenses
     * and legacy single-item linked expenses.
     */
    val effectiveInventoryItems: List<ExpenseInventoryLineItem>
        get() = when {
            inventoryItems.isNotEmpty() -> inventoryItems
            linkedInventoryType.isNotBlank() && linkedInventoryItemId.isNotBlank() && linkedInventoryQty > 0.0 -> {
                listOf(
                    ExpenseInventoryLineItem(
                        itemType = linkedInventoryType,
                        itemId = linkedInventoryItemId,
                        itemName = linkedInventoryItemName.ifBlank { description },
                        categoryId = categoryId,
                        categoryName = categoryName,
                        uom = if (linkedInventoryType == "BOTTLE") "Bottles" else "",
                        quantity = linkedInventoryQty,
                        unitRate = linkedInventoryUnitCost,
                        lineTotal = if (linkedInventoryUnitCost > 0.0) linkedInventoryQty * linkedInventoryUnitCost else amount,
                        transactionId = linkedInventoryTransactionId
                    )
                )
            }
            else -> emptyList()
        }

    val hasInventoryItems: Boolean
        get() = isInventoryExpense || effectiveInventoryItems.isNotEmpty()
}
