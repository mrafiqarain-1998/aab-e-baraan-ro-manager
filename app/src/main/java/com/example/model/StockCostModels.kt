package com.example.model

import com.squareup.moshi.JsonClass

/**
 * Usage types for bottles:
 * MULTIPLE_TIME: 19L and 12.5L bottles that are returned and refilled repeatedly.
 * ONE_TIME: 500ml, 1.5L, 6L bottles that are packaged, sold once, and not returnable.
 */
enum class BottleUsageType(val label: String, val shortLabel: String, val urduLabel: String) {
    MULTIPLE_TIME("Multiple Time", "Multiple Time", "بار بار قابل واپسی بوتل"),
    ONE_TIME("Single Time", "Single Time", "ایک بار فروخت"),
    CUSTOM("Custom Type", "Custom", "مخصوص قسم")
}

/**
 * Payment source for bottle stock purchase or material refill
 */
enum class PaymentSource(val label: String, val urduLabel: String) {
    PAY_FROM_SALE("Pay from Sale", "سیل سے ادائیگی"),
    PAY_FROM_INVESTMENT("Pay from Investment Account", "انویسٹمنٹ اکاؤنٹ سے ادائیگی")
}

/**
 * Payment mode for bottle stock purchase or material refill
 */
enum class StockPaymentMode(val label: String, val urduLabel: String, val paymentMethod: PaymentMethod) {
    CASH("Cash", "نقد (Cash)", PaymentMethod.CASH),
    ONLINE_TRANSFER("Online Transfer", "آن لائن ٹرانسفر", PaymentMethod.BANK_TRANSFER)
}

/**
 * Source bucket when recording Bottle Loss & Damage
 */
enum class BottleLossSource(val label: String, val urduLabel: String) {
    PLANT_FULL("Plant Full Stock", "پلانٹ تیار اسٹاک"),
    PLANT_EMPTY("Plant Empty Stock", "پلانٹ خالی اسٹاک"),
    CUSTOMER_HELD("Customer Held", "گاہک کے پاس موجود")
}

object StockMasterOptions {
    val MATERIAL_CATEGORIES = listOf(
        "Packing Material",
        "RO Plant Parts",
        "Vehicle Parts",
        "Bottle & Cap",
        "Tools",
        "Filter & Cartridge",
        "Plumbing Items",
        "Electrical Items",
        "General Items",
        "Others"
    )

    val MATERIAL_UOMS = listOf(
        "Piece",
        "Packet",
        "Carton",
        "Pair",
        "Liter",
        "KG",
        "Gram",
        "Nos."
    )
}

/**
 * Target destination when issuing bottles
 */
enum class BottleIssueTarget(val label: String, val urduLabel: String) {
    CUSTOMER("Customer", "گاہک کو جاری کریں"),
    INTERNAL_USE("Issue to RO Plant", "آر او پلانٹ کو جاری کریں")
}

/**
 * Factory material classification
 */
enum class MaterialUsageType(val label: String, val urduLabel: String) {
    PRODUCTION_CONSUMABLE("Production Consumable", "پیداواری سامان (کیپس، لیبل، سیل)"),
    MAINTENANCE_MATERIAL("Maintenance Material", "مرمتی سامان (فلٹرز، اسپیئر پارٹس)"),
    CUSTOM("Custom Usage Type", "مخصوص قسم"),
    OTHER("Other Factory Material", "دیگر سامان")
}

/**
 * Department or usage category when material is issued
 */
enum class MaterialIssueCategory(val label: String, val urduLabel: String) {
    PRODUCTION("Issue for Production", "پیداوار کے لیے جاری"),
    DRIVER("Issue to Driver", "ڈرائیور کو جاری"),
    REPAIRING("Issue for Repairing", "مرمت کے لیے جاری"),
    REPLACEMENT("Issue for Replacement", "تبدیلی کے لیے جاری"),
    MAINTENANCE("Plant Maintenance", "پلانٹ مینٹیننس"),
    RO_PLANT("RO System Operation", "آر او سسٹم آپریشن"),
    ADMIN("Admin & Office", "انتظامیہ و دفتر"),
    OTHER("Other Department", "دیگر مقاصد")
}

/**
 * Material stock transaction action type
 */
enum class MaterialActionType(val label: String, val urduLabel: String) {
    REFILL("Stock Refill (Purchase)", "اسٹاک خریداری / اضافہ"),
    ISSUE("Material Issued", "سامان جاری کیا"),
    ADJUSTMENT("Stock Adjustment", "اسٹاک درستگی"),
    DAMAGE("Mark Damage", "ٹوٹ پھوٹ / نقصان"),
    MALFUNCTION("Malfunction", "خرابی / ناکارہ")
}

/**
 * Represents consumables, packaging, or spare parts in factory inventory
 */
@JsonClass(generateAdapter = true)
data class FactoryMaterial(
    val id: String = "",
    val name: String = "",
    val purchaseRate: Double = 0.0,
    val perPackQty: Int = 1,
    val uom: String = "Piece",
    val currentStock: Double = 0.0,
    val openingStock: Double = 0.0,
    val usageType: MaterialUsageType = MaterialUsageType.PRODUCTION_CONSUMABLE,
    val customUsageType: String = "",
    val defaultIssueCategory: MaterialIssueCategory = MaterialIssueCategory.PRODUCTION,
    val itemCategory: String = "General Items",
    val costType: ExpenseType = ExpenseType.OPEX,
    val active: Boolean = true,
    val supplierName: String = "",
    val minStockLevel: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Transaction log for factory material movements
 */
@JsonClass(generateAdapter = true)
data class MaterialTransaction(
    val id: String = "",
    val materialId: String = "",
    val materialName: String = "",
    val date: String = "",
    val actionType: MaterialActionType = MaterialActionType.REFILL,
    val quantity: Double = 0.0,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val uom: String = "Piece",
    val issueCategory: MaterialIssueCategory? = null,
    val supplierOrReceiver: String = "",
    val invoiceNo: String = "",
    val paymentSource: String = "",
    val paymentMode: String = "",
    val notes: String = "",
    val balanceAfter: Double = 0.0,
    val performedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Packing mode for bottle costing: Single Bottle, Packet, or Carton
 */
enum class CostingPackingType(val label: String, val shortLabel: String, val urduLabel: String) {
    SINGLE("Single Bottle", "Single", "سنگل بوتل"),
    PACKET("Packet", "Packet", "پیکٹ"),
    CARTON("Carton", "Carton", "کارٹن")
}

/**
 * Costing record per bottle size & packing type (Admin Only)
 */
@JsonClass(generateAdapter = true)
data class BottleCostingRecord(
    val id: String = "",
    val bottleSizeId: String = "",
    val bottleSizeName: String = "",
    val packingType: CostingPackingType = CostingPackingType.SINGLE,
    val isMultiTimeUse: Boolean = false,
    val bottlePurchaseRate: Double = 0.0,
    val fillingRate: Double = 0.0,
    val packingRate: Double = 0.0,
    val packQty: Int = 1,
    val labelSealPacketPrice: Double = 0.0,
    val labelSealTotalPieces: Int = 1,
    val labelSealPiecesUsedPerPack: Int = 1,
    val labelSealCalculatedCost: Double = 0.0,
    val cartonPolytheneCost: Double = 0.0,
    val capCost: Double = 0.0,
    val waterCost: Double = 0.0,
    val labourCost: Double = 0.0,
    val electricityCost: Double = 0.0,
    val deliveryCost: Double = 0.0,
    val totalCostPerPackOrBottle: Double = 0.0,
    val salePrice: Double = 0.0,
    val profitPerPackOrBottle: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    val updatedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
) {
    val extraComponentCost: Double
        get() = labelSealCalculatedCost + cartonPolytheneCost + capCost + waterCost + labourCost + electricityCost + deliveryCost

    fun effectivePurchaseCostForPnl(forceMultiUse: Boolean = isMultiTimeUse): Double =
        if (forceMultiUse) 0.0 else bottlePurchaseRate

    fun effectiveUnitCostForPnl(forceMultiUse: Boolean = isMultiTimeUse): Double =
        effectivePurchaseCostForPnl(forceMultiUse) + fillingRate + packingRate + extraComponentCost
}

/**
 * Period filter for Costing Profit & Loss Dashboard
 */
enum class CostingReportPeriod(val label: String, val urduLabel: String) {
    DAILY("Daily", "روزانہ"),
    WEEKLY("Weekly", "ہفتہ وار"),
    MONTHLY("Monthly", "ماہانہ")
}

/**
 * Per-bottle sold cost and revenue breakdown inside Costing P&L report
 */
data class BottleSaleCostLine(
    val bottleSizeId: String = "",
    val bottleName: String = "",
    val packingType: CostingPackingType = CostingPackingType.SINGLE,
    val isMultiTimeUse: Boolean = false,
    val unitsSold: Int = 0,
    val totalBottlesSold: Int = 0,
    val revenue: Double = 0.0,
    val purchaseCostTotal: Double = 0.0,
    val fillingCostTotal: Double = 0.0,
    val packingCostTotal: Double = 0.0,
    val otherUnitCostTotal: Double = 0.0,
    val totalProductionCost: Double = 0.0,
    val grossMargin: Double = 0.0,
    val hasConfiguredCosting: Boolean = true
)

/**
 * Complete Daily / Weekly / Monthly Profit & Loss Dashboard Report
 * - Excludes bottle purchasing cost for multi-time-use (returnable) bottles
 * - Excludes CAPEX expenses
 * - Includes OPEX expenses (salary, maintenance, fuel, etc.)
 * - Divides monthly fixed/flexible recurring expenses per day and prorates to selected period
 */
data class CostingProfitLossReport(
    val period: CostingReportPeriod = CostingReportPeriod.DAILY,
    val periodLabel: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val daysInPeriod: Int = 1,
    val daysInReferenceMonth: Int = 30,
    val totalDeliveries: Int = 0,
    val totalUnitsSold: Int = 0,
    val totalSalesRevenue: Double = 0.0,
    val singleUsePurchaseCost: Double = 0.0,
    val fillingCost: Double = 0.0,
    val packingCost: Double = 0.0,
    val otherBottleUnitCost: Double = 0.0,
    val totalBottleAndPackingCost: Double = 0.0,
    val opexExpenses: Double = 0.0,
    val excludedCapexExpenses: Double = 0.0,
    val monthlyRecurringTotal: Double = 0.0,
    val dailyFixedCostRate: Double = 0.0,
    val proratedMonthlyFixedCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val netProfitOrLoss: Double = 0.0,
    val bottleBreakdown: List<BottleSaleCostLine> = emptyList()
)

/**
 * Recurring monthly factory and operational cost categories
 */
enum class MonthlyCostCategory(val label: String, val urduLabel: String) {
    STAFF_SALARIES("Staff Salaries", "سٹاف کی تنخواہیں"),
    RO_MAINTENANCE("RO Maintenance", "آر او پلانٹ مرمت"),
    VEHICLE_MAINTENANCE("Vehicle Maintenance", "گاڑی / رکشہ مرمت"),
    RO_RENT("RO Rent / Building", "پلانٹ و بلڈنگ کرایہ"),
    OTHER_EXPENSES("Other Recurring Expenses", "دیگر ماہانہ اخراجات")
}

/**
 * Recurring monthly expense item (Admin Only)
 */
@JsonClass(generateAdapter = true)
data class MonthlyRecurringExpense(
    val id: String = "",
    val category: MonthlyCostCategory = MonthlyCostCategory.OTHER_EXPENSES,
    val description: String = "",
    val amount: Double = 0.0,
    val month: String = "", // Format: "yyyy-MM" e.g. "2026-11"
    val date: String = "",  // Format: "yyyy-MM-dd"
    val notes: String = "",
    val createdBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Real-time Monthly Financial Summary
 */
data class MonthlyFinancialSummary(
    val month: String = "",
    val totalDeliveries: Int = 0,
    val totalSalesAmount: Double = 0.0,
    val receivedPayments: Double = 0.0,
    val outstandingSalesAmount: Double = 0.0,
    val operationalExpenses: Double = 0.0,
    val otherMonthlyCosts: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfitAccrual: Double = 0.0, // totalSalesAmount - totalExpenses
    val netProfitCash: Double = 0.0     // receivedPayments - totalExpenses
)

/**
 * Plant Worker / Employee profile managed under Stock & Cost -> Staff
 */
@JsonClass(generateAdapter = true)
data class PlantWorker(
    val id: String = "",
    val name: String = "",
    val cnic: String = "",
    val phone: String = "",
    val designation: String = "Worker",
    val monthlySalary: Double = 0.0,
    val joiningDate: String = "",
    val active: Boolean = true,
    val notes: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Monthly salary & advance summary computed live for a PlantWorker from Expenses
 */
data class WorkerSalarySummary(
    val workerId: String = "",
    val workerName: String = "",
    val month: String = "",
    val monthlySalary: Double = 0.0,
    val paidOrAdvanceThisMonth: Double = 0.0,
    val remainingSalary: Double = 0.0,
    val paymentsCount: Int = 0
)
