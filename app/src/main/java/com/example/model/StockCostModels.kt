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
 * Supports the 10-field Bottle Costing model + Automatic Pack Calculation + Confirmed Packet Cost override
 * while remaining 100% backward compatible with previously stored costing records.
 */
@JsonClass(generateAdapter = true)
data class BottleCostingRecord(
    val id: String = "",
    val bottleSizeId: String = "",
    val bottleSizeName: String = "",
    val packingType: CostingPackingType = CostingPackingType.SINGLE,
    val isMultiTimeUse: Boolean = false,
    // Legacy fields (kept for full backward compatibility)
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
    val timestamp: Long = System.currentTimeMillis(),
    // Redesigned 10-field Bottle Costing + Confirmed Packet Cost fields
    val bottlePrice: Double = 0.0,       // 1. Bottle Price (per bottle)
    val labelPrice: Double = 0.0,        // 2. Label Price (per bottle)
    val sealPrice: Double = 0.0,         // 3. Seal Price (per bottle)
    val waterFillingCost: Double = 0.0,  // 4. Water Filling (per bottle)
    val cartonCost: Double = 0.0,        // 5. Carton (per pack/carton)
    val tapeCost: Double = 0.0,          // 6. Tape (per pack/carton)
    // 7. deliveryCost (per pack/carton), 8. labourCost (per pack/carton)
    val miscCost: Double = 0.0,          // 9. Miscellaneous Cost (per pack/carton)
    // 10. capCost (per bottle)
    val calculatedPackCost: Double = 0.0,
    val confirmedPacketCost: Double = 0.0,
    val usesDetailedCosting: Boolean = false
) {
    val extraComponentCost: Double
        get() = labelSealCalculatedCost + cartonPolytheneCost + capCost + waterCost + labourCost + electricityCost + deliveryCost

    val perBottleSubtotal: Double
        get() = bottlePrice + labelPrice + sealPrice + waterFillingCost + capCost

    val perPackFixedSubtotal: Double
        get() = cartonCost + tapeCost + deliveryCost + labourCost + miscCost

    fun computeDetailedPackCost(): Double {
        val qty = packQty.coerceAtLeast(1)
        return (perBottleSubtotal * qty) + perPackFixedSubtotal
    }

    fun effectivePurchaseCostForPnl(forceMultiUse: Boolean = isMultiTimeUse): Double =
        if (forceMultiUse) 0.0 else bottlePurchaseRate

    fun effectiveUnitCostForPnl(forceMultiUse: Boolean = isMultiTimeUse): Double {
        if (confirmedPacketCost > 0.0) return confirmedPacketCost
        if (usesDetailedCosting) {
            val calc = if (calculatedPackCost > 0.0) calculatedPackCost else computeDetailedPackCost()
            return calc.coerceAtLeast(0.0)
        }
        if (totalCostPerPackOrBottle > 0.0 && bottlePrice == 0.0 && labelPrice == 0.0 && sealPrice == 0.0) {
            return effectivePurchaseCostForPnl(forceMultiUse) + fillingRate + packingRate + extraComponentCost
        }
        return (effectivePurchaseCostForPnl(forceMultiUse) + fillingRate + packingRate + extraComponentCost)
            .coerceAtLeast(0.0)
    }
}

/**
 * Budget vs Actual line item for Monthly Operational Costs.
 * Whenever actualAmount > 0, ALL costing & profit calculations automatically prefer actualAmount.
 */
@JsonClass(generateAdapter = true)
data class OperationalCostLine(
    val budgetAmount: Double = 0.0,
    val actualAmount: Double = 0.0
) {
    val isUsingActual: Boolean
        get() = actualAmount > 0.0

    val effectiveAmount: Double
        get() = if (actualAmount > 0.0) actualAmount else budgetAmount.coerceAtLeast(0.0)
}

/**
 * Monthly Operational Cost Record (per YYYY-MM) with 6 fixed operational items.
 * Staff Salaries are NOT entered here; they are automatically fetched from the Staff module.
 */
@JsonClass(generateAdapter = true)
data class MonthlyOperationalCostRecord(
    val id: String = "",
    val month: String = "", // "YYYY-MM"
    val vehicleMaintenance: OperationalCostLine = OperationalCostLine(),
    val roMaintenance: OperationalCostLine = OperationalCostLine(),
    val roPlantRent: OperationalCostLine = OperationalCostLine(),
    val vehicleMonthlyRent: OperationalCostLine = OperationalCostLine(),
    val electricityBill: OperationalCostLine = OperationalCostLine(),
    val otherFixedCosts: OperationalCostLine = OperationalCostLine(),
    val updatedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalBudget: Double
        get() = vehicleMaintenance.budgetAmount +
            roMaintenance.budgetAmount +
            roPlantRent.budgetAmount +
            vehicleMonthlyRent.budgetAmount +
            electricityBill.budgetAmount +
            otherFixedCosts.budgetAmount

    val totalActual: Double
        get() = vehicleMaintenance.actualAmount +
            roMaintenance.actualAmount +
            roPlantRent.actualAmount +
            vehicleMonthlyRent.actualAmount +
            electricityBill.actualAmount +
            otherFixedCosts.actualAmount

    val totalEffectiveCost: Double
        get() = vehicleMaintenance.effectiveAmount +
            roMaintenance.effectiveAmount +
            roPlantRent.effectiveAmount +
            vehicleMonthlyRent.effectiveAmount +
            electricityBill.effectiveAmount +
            otherFixedCosts.effectiveAmount
}

data class OperationalCostBreakdownItem(
    val label: String,
    val urduLabel: String,
    val budgetAmount: Double,
    val actualAmount: Double,
    val effectiveMonthlyAmount: Double,
    val isUsingActual: Boolean,
    val allocatedPeriodAmount: Double
)

/**
 * Period filter for Unified Profit & Loss Dashboard (Day / Week / Month / Year)
 */
enum class CostingReportPeriod(val label: String, val urduLabel: String) {
    DAILY("Day", "روزانہ"),
    WEEKLY("Week", "ہفتہ وار"),
    MONTHLY("Month", "ماہانہ"),
    YEARLY("Year", "سالانہ")
}

/**
 * Per-bottle sold cost and revenue breakdown (kept for data/test compatibility)
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
 * Unified Day / Week / Month / Year Profit & Loss Dashboard Report:
 * 1. Sales Revenue (from deliveries, net of discounts)
 * 2. Deduct Bottle & Packaging Material Cost (using Confirmed Packet Cost per delivered unit)
 *    -> Profit Before Operational Expenses
 * 3. Divide Monthly Staff Salaries (auto-fetched from Staff module) & Monthly Operational Costs
 *    (Actual if > 0 else Budget) by days in month, then allocate to selected period
 * 4. Deduct Allocated Staff Salaries + Allocated Monthly Operational Costs -> Final Net Profit
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
    val receivedPayments: Double = 0.0,
    val outstandingSalesAmount: Double = 0.0,
    val singleUsePurchaseCost: Double = 0.0,
    val fillingCost: Double = 0.0,
    val packingCost: Double = 0.0,
    val otherBottleUnitCost: Double = 0.0,
    val totalBottleAndPackingCost: Double = 0.0,
    val profitBeforeOperationalCosts: Double = 0.0,
    val activeStaffCount: Int = 0,
    val monthlyStaffSalariesTotal: Double = 0.0,
    val dailyStaffSalaryRate: Double = 0.0,
    val allocatedStaffSalaryCost: Double = 0.0,
    val monthlyOperationalCostsTotal: Double = 0.0,
    val dailyOperationalCostRate: Double = 0.0,
    val allocatedOperationalCost: Double = 0.0,
    val totalOperationalAndStaffCost: Double = 0.0,
    val operationalCostBreakdown: List<OperationalCostBreakdownItem> = emptyList(),
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
