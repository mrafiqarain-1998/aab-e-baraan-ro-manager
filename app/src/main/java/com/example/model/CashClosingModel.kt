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
    val approvalStatus: String = "", // "", "PENDING", "APPROVED", "REJECTED"
    val approvedBy: String = "",
    val approvedAt: Long = 0L,
    val businessId: String = "aab_e_baraan_plant_01",
    val closedAt: Long = System.currentTimeMillis()
) {
    val isBalanced: Boolean get() = kotlin.math.abs(difference) < 0.01
    val isShortage: Boolean get() = difference < -0.01
    val isSurplus: Boolean get() = difference > 0.01

    /**
     * Backward-compatible effective approval state:
     * - Balanced closings are always APPROVED.
     * - Legacy closings with blank approvalStatus follow isApprovedByOwner.
     */
    val effectiveApprovalStatus: String
        get() = when {
            isBalanced -> "APPROVED"
            approvalStatus.equals("APPROVED", ignoreCase = true) -> "APPROVED"
            approvalStatus.equals("REJECTED", ignoreCase = true) -> "REJECTED"
            approvalStatus.equals("PENDING", ignoreCase = true) -> "PENDING"
            isApprovedByOwner -> "APPROVED"
            else -> "PENDING"
        }

    val isDifferencePendingApproval: Boolean
        get() = !isBalanced && effectiveApprovalStatus == "PENDING"

    /**
     * Next day's opening drawer cash:
     * - If balanced OR Admin explicitly APPROVED the difference -> actualClosingCash (physical drawer cash)
     * - If difference is PENDING or REJECTED -> expectedClosingCash (past drawer + actual sales - expenses)
     */
    val carryForwardOpeningCash: Double
        get() = if (isBalanced || effectiveApprovalStatus == "APPROVED") actualClosingCash else expectedClosingCash
}

data class CashClosingSummary(
    val date: String = "",
    val openingCash: Double = 0.0,
    val customerCashCollected: Double = 0.0,
    val digitalCollected: Double = 0.0,
    val totalReceivedAmount: Double = customerCashCollected + digitalCollected,
    val otherCashIncome: Double = 0.0,
    val cashExpenses: Double = 0.0, // Cash OPEX expenses paid from Sale only
    val opexFromSaleExpenses: Double = cashExpenses, // All OPEX expenses paid from Sale (Cash + Online)
    val excludedCapexExpenses: Double = 0.0,
    val excludedInvestmentExpenses: Double = 0.0,
    val cashRefunds: Double = 0.0,
    val dailyNetReceivedAfterOpex: Double = totalReceivedAmount - opexFromSaleExpenses,
    val expectedClosingCash: Double = 0.0
)
