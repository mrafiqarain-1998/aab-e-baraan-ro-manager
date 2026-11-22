package com.example.model

enum class DeliveryUnit(val label: String, val urduLabel: String) {
    PER_BOTTLE("Per Bottle", "فی بوتل"),
    PER_PACKET("Per Packet", "فی پیکٹ")
}

data class BottleSize(
    val id: String = "",
    val name: String = "",
    val capacityLitres: Double = 19.0,
    val packQuantity: Int = 1, // Kis size k packet / crate / bundle me kitni bottle hoti hain (e.g. 19L = 1, 1.5L = 6, 500ml = 12)
    val defaultRefillPrice: Double = 120.0,
    val bottleSalePrice: Double = 1200.0,
    val depositAmount: Double = 1000.0,
    val replacementPrice: Double = 800.0,
    val active: Boolean = true,
    val refillCost: Double = 35.0, // Cost to refill a single bottle (water, power, labor, caps)
    val deliveryCost: Double = 20.0, // Delivery cost per bottle or per packet
    val deliveryUnit: DeliveryUnit = DeliveryUnit.PER_BOTTLE, // PER_BOTTLE or PER_PACKET
    val packetPackagingQty: Int = 1, // 1 for 19L/12.5L/6L; 6 or 12 for 1.5L; 12 or 24 for 500ml
    val isReturnableBottle: Boolean = true, // 19L & 12.5L returnable; 6L, 1.5L, 500ml sold as packaged
    val uom: String = "Bottles",
    val openingStock: Int = 0,
    val purchaseCost: Double = 800.0,
    val latestPurchaseRate: Double = 800.0,
    val usageType: BottleUsageType = if (isReturnableBottle) BottleUsageType.MULTIPLE_TIME else BottleUsageType.ONE_TIME,
    val customUsageType: String = "",
    val categoryName: String = "Bottle Purchase",
    val businessId: String = "aab_e_baraan_plant_01"
)

data class RateHistoryEntry(
    val id: String = "",
    val customerId: String = "",
    val bottleSizeId: String = "",
    val bottleSizeName: String = "",
    val rate: Double = 0.0,
    val appliedFromDate: String = "",
    val updatedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomerRate(
    val id: String = "",
    val customerId: String = "",
    val bottleSizeId: String = "",
    val bottleSizeName: String = "",
    val customPrice: Double = 0.0,
    val isAssigned: Boolean = true,
    val appliedDate: String = "",
    val rateHistory: List<RateHistoryEntry> = emptyList(),
    val businessId: String = "aab_e_baraan_plant_01"
)

enum class SecurityDepositTransactionType(val label: String, val urduLabel: String) {
    INITIAL_DEPOSIT("Initial Deposit Received", "ابتدائی سیکیورٹی ڈپازٹ"),
    ADDITIONAL_DEPOSIT("Additional Deposit Received", "اضافی سیکیورٹی ڈپازٹ"),
    PARTIAL_REFUND("Partial Refund", "جزوی واپسی"),
    FULL_REFUND("Full Refund", "مکمل واپسی"),
    ADJUSTMENT("Deposit Adjustment", "ڈپازٹ ایڈجسٹمنٹ")
}

data class SecurityDepositTransaction(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val type: SecurityDepositTransactionType = SecurityDepositTransactionType.INITIAL_DEPOSIT,
    val date: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reference: String = "",
    val notes: String = "",
    val createdBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis(),
    val businessId: String = "aab_e_baraan_plant_01"
)

// Tracks bottles issued, currently held, and broken/damaged per customer
data class CustomerBottleAllocation(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val bottleSizeId: String = "",
    val sizeName: String = "",
    val totalIssued: Int = 0,      // Total bottles issued to this customer
    val emptyHeld: Int = 0,        // Currently in their possession
    val returnedCount: Int = 0,    // Kitni wapis huin is customer ki
    val damagedCount: Int = 0,     // Kitni tooti / damage huin is customer ki
    val depositAmount: Double = 0.0,
    val lastUpdated: String = "",
    val businessId: String = "aab_e_baraan_plant_01"
)

enum class BottleMovementType(val label: String, val urduLabel: String) {
    DELIVERY_OUT("Delivered Full (بھری بوتل دی)", "بھری بوتل دی"),
    RETURN_IN("Empty Collected (خالی بوتل واپس)", "خالی بوتل واپس"),
    PLANT_REFILL("Plant Refilled (پلانٹ فلنگ)", "پلانٹ فلنگ"),
    PURCHASE_NEW("Stock Added (نئی بوتل خریدی)", "نئی بوتل خریدی"),
    DAMAGED("Damaged / Broken (خراب یا ٹوٹی بوتل)", "خراب / ٹوٹی بوتل"),
    CUSTOMER_DAMAGE("Customer Broken Bottle (گاہک کے پاس ٹوٹی)", "گاہک سے ٹوٹی"),
    ADJUSTMENT("Audit Adjustment (درستگی)", "درستگی")
}

enum class PackContainerType(val label: String, val shortLabel: String) {
    PACKET("Packet", "Pkt"),
    CARTON("Carton", "Ctn")
}

data class BottlePackVariant(
    val variantId: String = "",
    val bottleSizeId: String = "",
    val displayName: String = "",
    val shortLabel: String = "",
    val packSize: Int = 1,
    val containerType: PackContainerType = PackContainerType.PACKET,
    val packetCount: Int = 0
)

fun normalizeLegacyBottleName(rawName: String): String {
    return rawName
        .replace(Regex("12\\.5\\s*Litre", RegexOption.IGNORE_CASE), "12 Litre")
        .replace(Regex("12\\.5\\s*Liter", RegexOption.IGNORE_CASE), "12 Litre")
        .replace(Regex("12\\.5\\s*L\\b", RegexOption.IGNORE_CASE), "12L")
        .trim()
}

fun cleanBaseBottleName(rawName: String): String {
    return normalizeLegacyBottleName(rawName)
        .replace(Regex("\\s*Pack\\s*\\((Carton|Packet)\\)\\s*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*\\((Carton|Packet)\\)\\s*$", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifBlank { normalizeLegacyBottleName(rawName) }
}

fun shortBaseSizeToken(baseSizeName: String, capacityLitres: Double = 0.0): String {
    val cleanBase = cleanBaseBottleName(baseSizeName)
    return when {
        cleanBase.contains("19", ignoreCase = true) -> "19L"
        cleanBase.contains("12.5", ignoreCase = true) || cleanBase.contains("12", ignoreCase = true) -> "12L"
        cleanBase.startsWith("6", ignoreCase = true) || cleanBase.contains("6 Lit", ignoreCase = true) -> "6L"
        cleanBase.contains("1.5", ignoreCase = true) -> "1.5L"
        cleanBase.contains("500", ignoreCase = true) -> "500ml"
        cleanBase.contains("300", ignoreCase = true) -> "300ml"
        cleanBase.contains("ml", ignoreCase = true) -> {
            val match = Regex("(\\d+(?:\\.\\d+)?)\\s*ml", RegexOption.IGNORE_CASE).find(cleanBase)
            match?.let { "${it.groupValues[1]}ml" } ?: cleanBase.take(8)
        }
        capacityLitres > 0 -> {
            if (capacityLitres % 1.0 == 0.0) "${capacityLitres.toInt()}L" else "${capacityLitres}L"
        }
        else -> cleanBase.take(8)
    }
}

fun formatPackVariantName(
    baseSizeName: String,
    packSize: Int,
    containerType: PackContainerType
): String {
    val cleanBase = cleanBaseBottleName(baseSizeName)
    val shortBase = shortBaseSizeToken(cleanBase)
    return if (packSize <= 1) {
        cleanBase
    } else {
        "$shortBase ${containerType.label}"
    }
}

fun formatPackVariantShortLabel(
    baseSizeName: String,
    capacityLitres: Double,
    packSize: Int,
    containerType: PackContainerType
): String {
    val baseShort = shortBaseSizeToken(baseSizeName, capacityLitres)
    return if (packSize <= 1) {
        baseShort
    } else {
        "$baseShort ${containerType.label}"
    }
}

fun makePackVariantId(
    bottleSizeId: String,
    packSize: Int,
    containerType: PackContainerType
): String = "${bottleSizeId}__${packSize}_${containerType.name}"

data class BottleInventory(
    val id: String = "",
    val bottleSizeId: String = "",
    val sizeName: String = "",
    val atPlantFull: Int = 0,      // Refilled bottles available in stock ready for issue/sale
    val atPlantEmpty: Int = 0,     // Empty bottles in stock in warehouse (ready for refilling)
    val inUse: Int = 0,            // Bottles issued to RO Plant for refilling/production
    val fullPackets: Int = 0,      // Complete converted packets/cartons available in Plant Full
    val fullLooseBottles: Int = 0, // Remaining individual full bottles after packet conversion
    val packVariants: List<BottlePackVariant> = emptyList(), // Packet/Carton breakdown inside Plant Full
    val withCustomers: Int = 0,    // Bottles currently in customer homes/shops
    val withStaff: Int = 0,        // Bottles currently on rider/delivery vehicle
    val damaged: Int = 0,          // Broken/damaged bottles
    val totalPurchased: Int = 0,   // Total bottles purchased throughout history
    val totalIssuedOrSold: Int = 0,// Total bottles issued or sold throughout history
    val businessId: String = "aab_e_baraan_plant_01"
) {
    val totalPlantBottles: Int get() = atPlantFull + atPlantEmpty + inUse
    val totalCirculation: Int get() = atPlantFull + atPlantEmpty + inUse + withCustomers + withStaff + damaged
}

data class BottleMovement(
    val id: String = "",
    val date: String = "",
    val bottleSizeId: String = "",
    val sizeName: String = "",
    val movementType: BottleMovementType = BottleMovementType.DELIVERY_OUT,
    val quantity: Int = 0,
    val customerId: String? = null,
    val customerName: String? = null,
    val referenceType: String = "", // e.g. "Delivery", "Stock Addition", "Customer Breakage"
    val referenceId: String = "",
    val notes: String = "",
    val createdBy: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------
// PURCHASE RECORD FOR EMPTY BOTTLES (Section 3.A)
// -------------------------------------------------------------
data class BottlePurchaseRecord(
    val id: String = "",
    val date: String = "",
    val bottleSizeId: String = "",
    val sizeName: String = "",
    val quantity: Int = 0,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val supplierName: String = "",
    val invoiceOrBillNo: String = "",
    val paymentSource: String = "",
    val paymentMode: String = "",
    val notes: String = "",
    val recordedBy: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------
// REFILL PRODUCTION RECORD (Section 3.C & 4.A)
// -------------------------------------------------------------
data class BottleRefillRecord(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val bottleSizeId: String = "",
    val sizeName: String = "",
    val quantityFilled: Int = 0,
    val packetSize: Int = 1,
    val containerType: PackContainerType = PackContainerType.PACKET,
    val packedVariantName: String = "",
    val completePackets: Int = 0,
    val packedBottles: Int = 0,
    val looseBottles: Int = 0,
    val damagedBottles: Int = 0,
    val refillCostPerBottle: Double = 0.0,
    val totalRefillCost: Double = 0.0,
    val operatorName: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------
// BOTTLE ISSUE AND SALES TRANSACTION LOG (Section 3.D & 5)
// -------------------------------------------------------------
enum class BottleTransactionType(val label: String, val urduLabel: String) {
    CUSTOMER_ISSUE("Bottle Issued (گاہک کو دی)", "گاہک کو جاری"),
    PACKAGED_SALE("Packaged Sale (پیکٹ فروخت)", "پیکجڈ فروخت"),
    EMPTY_COLLECTED("Empty Collected (خالی وصول)", "خالی وصول"),
    DAMAGE_LOGGED("Bottle Damaged (ٹوٹی بوتل)", "ٹوٹی بوتل")
}

data class BottleIssueSaleRecord(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val bottleSizeId: String = "",
    val bottleSizeName: String = "",
    val quantity: Int = 0,
    val transactionType: BottleTransactionType = BottleTransactionType.CUSTOMER_ISSUE,
    val unitSellingPrice: Double = 0.0,
    val refillCost: Double = 0.0,
    val deliveryCost: Double = 0.0,
    val totalTransactionCost: Double = 0.0, // (refillCost + deliveryCost) * units / packets
    val revenue: Double = 0.0,
    val profit: Double = 0.0, // revenue - totalTransactionCost
    val deliveryPerson: String = "",
    val referenceDeliveryId: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------
// RO PLANT MATERIALS, SPARE PARTS & CONSUMABLES (Section 6)
// -------------------------------------------------------------
enum class MaterialCategory(val label: String, val urduLabel: String) {
    FILTRATION("Filtration & Membranes", "فلٹرز و میمبرین"),
    CHEMICALS("Water Treatment Chemicals", "کیمیکلز و نمکیات"),
    PACKAGING("Caps, Seals & Packaging", "کیپس و پیکجنگ"),
    SPARES("Pumps & Spare Parts", "پمپ و اسپیئر پارٹس"),
    LAB_TESTING("Testing & Lab Reagents", "لیب و ٹیسٹنگ"),
    GENERAL("General RO Plant Supplies", "متفرق سامان")
}

data class RoPlantItem(
    val id: String = "",
    val name: String = "",
    val sizeOrSpec: String = "",
    val description: String = "",
    val category: MaterialCategory = MaterialCategory.FILTRATION,
    val unitCost: Double = 0.0,
    val uom: String = "Pieces", // Pieces, Kg, Litres, Meters, Packets, Boxes
    val currentStock: Double = 0.0,
    val minimumAlertLevel: Double = 5.0,
    val supplierName: String = "",
    val purchaseDate: String = "",
    val location: String = "Main Plant Store",
    val businessId: String = "aab_e_baraan_plant_01"
)

enum class InventoryActionType(val label: String, val urduLabel: String) {
    STOCK_RECEIPT("Stock Added / Purchased", "اسٹاک خریداری"),
    ITEM_ISSUE("Issued for Operation / Maintenance", "پلانٹ استعمال"),
    MARK_DAMAGED("Marked Damaged / Discarded", "خراب / ضائع شدہ"),
    ADJUSTMENT("Audit Stock Adjustment", "اسٹاک درستگی")
}

data class RoItemTransaction(
    val id: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val date: String = "",
    val time: String = "",
    val actionType: InventoryActionType = InventoryActionType.STOCK_RECEIPT,
    val quantity: Double = 0.0,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val uom: String = "",
    val previousStock: Double = 0.0,
    val newStock: Double = 0.0,
    val responsibleEmployee: String = "",
    val reasonOrPurpose: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
