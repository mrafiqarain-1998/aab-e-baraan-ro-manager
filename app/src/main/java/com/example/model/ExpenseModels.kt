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
    val businessId: String = "aab_e_baraan_plant_01"
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
    val createdAt: Long = System.currentTimeMillis()
)
