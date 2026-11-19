package com.example.model

data class CashClosing(
    val id: String = "",
    val date: String = "", // YYYY-MM-DD
    val openingCash: Double = 0.0,
    val customerCashCollected: Double = 0.0,
    val otherCashIncome: Double = 0.0,
    val cashExpenses: Double = 0.0,
    val cashRefunds: Double = 0.0,
    val expectedClosingCash: Double = 0.0,
    val actualClosingCash: Double = 0.0,
    val difference: Double = 0.0, // actual - expected
    val notes: String = "",
    val closedBy: String = "",
    val isApprovedByOwner: Boolean = false,
    val businessId: String = "aab_e_baraan_plant_01",
    val closedAt: Long = System.currentTimeMillis()
) {
    val isBalanced: Boolean get() = kotlin.math.abs(difference) < 0.01
    val isShortage: Boolean get() = difference < -0.01
    val isSurplus: Boolean get() = difference > 0.01
}

data class CashClosingSummary(
    val date: String = "",
    val openingCash: Double = 0.0,
    val customerCashCollected: Double = 0.0,
    val otherCashIncome: Double = 0.0,
    val cashExpenses: Double = 0.0,
    val cashRefunds: Double = 0.0,
    val expectedClosingCash: Double = 0.0
)
