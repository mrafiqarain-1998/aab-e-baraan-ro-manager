package com.example.data

import com.example.model.*
import java.text.SimpleDateFormat
import java.util.*

object SampleData {
    val today: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000L))
    val olderDay: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 3 * 86400000L))

    val initialPasswordResetRequests: List<PasswordResetRequest> = emptyList()

    val bottleSizes = listOf(
        BottleSize(
            id = "size_19l",
            name = "19 Litre Bottle",
            capacityLitres = 19.0,
            packQuantity = 1, // Single bottle
            defaultRefillPrice = 120.0,
            bottleSalePrice = 1200.0,
            depositAmount = 1000.0,
            replacementPrice = 800.0,
            active = true,
            refillCost = 35.0,
            deliveryCost = 20.0,
            deliveryUnit = DeliveryUnit.PER_BOTTLE,
            packetPackagingQty = 1,
            isReturnableBottle = true
        ),
        BottleSize(
            id = "size_12l",
            name = "12.5 Litre Bottle",
            capacityLitres = 12.5,
            packQuantity = 1, // Single bottle
            defaultRefillPrice = 90.0,
            bottleSalePrice = 900.0,
            depositAmount = 800.0,
            replacementPrice = 650.0,
            active = true,
            refillCost = 25.0,
            deliveryCost = 15.0,
            deliveryUnit = DeliveryUnit.PER_BOTTLE,
            packetPackagingQty = 1,
            isReturnableBottle = true
        ),
        BottleSize(
            id = "size_6l",
            name = "6 Litre Bottle",
            capacityLitres = 6.0,
            packQuantity = 1,
            defaultRefillPrice = 50.0,
            bottleSalePrice = 500.0,
            depositAmount = 400.0,
            replacementPrice = 350.0,
            active = true,
            refillCost = 15.0,
            deliveryCost = 12.0,
            deliveryUnit = DeliveryUnit.PER_BOTTLE,
            packetPackagingQty = 1,
            isReturnableBottle = false
        ),
        BottleSize(
            id = "size_1_5l",
            name = "1.5L Pack (Carton)",
            capacityLitres = 9.0,
            packQuantity = 6, // 6 bottles per pack
            defaultRefillPrice = 360.0,
            bottleSalePrice = 360.0,
            depositAmount = 0.0,
            replacementPrice = 0.0,
            active = true,
            refillCost = 120.0, // Rs. 20 per bottle = 120 per pack of 6
            deliveryCost = 40.0,
            deliveryUnit = DeliveryUnit.PER_PACKET,
            packetPackagingQty = 6,
            isReturnableBottle = false
        ),
        BottleSize(
            id = "size_500ml",
            name = "500ml Pack (Carton)",
            capacityLitres = 6.0,
            packQuantity = 12, // 12 bottles per pack
            defaultRefillPrice = 300.0,
            bottleSalePrice = 300.0,
            depositAmount = 0.0,
            replacementPrice = 0.0,
            active = true,
            refillCost = 144.0, // Rs. 12 per bottle = 144 per pack of 12
            deliveryCost = 50.0,
            deliveryUnit = DeliveryUnit.PER_PACKET,
            packetPackagingQty = 12,
            isReturnableBottle = false
        )
    )

    val customers: List<Customer> = emptyList()
    val customerBottleAllocations: List<CustomerBottleAllocation> = emptyList()
    val initialSecurityDeposits: List<SecurityDepositTransaction> = emptyList()
    val customerRates: List<CustomerRate> = emptyList()

    val inventory = listOf(
        BottleInventory(
            id = "inv_19l",
            bottleSizeId = "size_19l",
            sizeName = "19 Litre Bottle",
            atPlantFull = 0,
            atPlantEmpty = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = 0,
            totalIssuedOrSold = 0
        ),
        BottleInventory(
            id = "inv_12l",
            bottleSizeId = "size_12l",
            sizeName = "12 Litre Bottle",
            atPlantFull = 0,
            atPlantEmpty = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = 0,
            totalIssuedOrSold = 0
        ),
        BottleInventory(
            id = "inv_6l",
            bottleSizeId = "size_6l",
            sizeName = "6 Litre Bottle",
            atPlantFull = 0,
            atPlantEmpty = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = 0,
            totalIssuedOrSold = 0
        ),
        BottleInventory(
            id = "inv_1_5l",
            bottleSizeId = "size_1_5l",
            sizeName = "1.5L Pack (Carton)",
            atPlantFull = 0,
            atPlantEmpty = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = 0,
            totalIssuedOrSold = 0
        ),
        BottleInventory(
            id = "inv_500ml",
            bottleSizeId = "size_500ml",
            sizeName = "500ml Pack (Carton)",
            atPlantFull = 0,
            atPlantEmpty = 0,
            withCustomers = 0,
            withStaff = 0,
            damaged = 0,
            totalPurchased = 0,
            totalIssuedOrSold = 0
        )
    )

    val initialBottlePurchases: List<BottlePurchaseRecord> = emptyList()
    val initialBottleRefills: List<BottleRefillRecord> = emptyList()
    val initialBottleIssueSales: List<BottleIssueSaleRecord> = emptyList()
    val initialRoPlantItems: List<RoPlantItem> = emptyList()
    val initialRoItemTransactions: List<RoItemTransaction> = emptyList()

    val initialFactoryMaterials: List<FactoryMaterial> = emptyList()

    val initialMaterialTransactions: List<MaterialTransaction> = emptyList()

    val currentMonth: String = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    val initialBottleCostings: List<BottleCostingRecord> = emptyList()

    val initialMonthlyRecurringExpenses: List<MonthlyRecurringExpense> = emptyList()

    val expenseCategories: List<ExpenseCategory> = listOf(
        // OPEX (Operating Expenses)
        ExpenseCategory("cat_entertainment", "Entertainment", "مہمان نوازی", ExpenseType.OPEX),
        ExpenseCategory("cat_fuel", "Fuel / Rent", "ایندھن / کرایہ", ExpenseType.OPEX),
        ExpenseCategory("cat_electricity", "Electricity Bill", "بجلی کا بل", ExpenseType.OPEX),
        ExpenseCategory("cat_maint", "Plant Maintenance", "پلانٹ مینٹیننس", ExpenseType.OPEX),
        ExpenseCategory("cat_vehicle_maint", "Vehicle Maintenance", "گاڑی کی مرمت", ExpenseType.OPEX),
        ExpenseCategory("cat_filters", "Filter and Cartridge", "فلٹر اور کارٹریج", ExpenseType.OPEX),
        ExpenseCategory("cat_packing", "Packing Material", "پیکنگ مٹیریل", ExpenseType.OPEX),
        ExpenseCategory("cat_minerals", "Chemical and Mineral", "کیمیکلز اور منرلز", ExpenseType.OPEX),
        ExpenseCategory("cat_misc", "General", "عمومی اخراجات", ExpenseType.OPEX),
        ExpenseCategory("cat_salary", "Staff Salary", "عملے کی تنخواہ", ExpenseType.OPEX),
        ExpenseCategory("cat_raw_material", "Raw Material", "خام مال", ExpenseType.OPEX),
        ExpenseCategory("cat_others", "Others", "دیگر اخراجات", ExpenseType.OPEX),
        // CAPEX (Capital Expenses)
        ExpenseCategory("cat_asset_machinery", "Asset / Machinery", "اثاثہ جات / مشینری", ExpenseType.CAPEX)
    )

    val initialInvoices: List<Invoice> = emptyList()
    val initialPayments: List<Payment> = emptyList()
    val initialDeliveries: List<Delivery> = emptyList()
    val initialExpenses: List<Expense> = emptyList()

    // User Accounts - Only primary Admin preserved; user can add new supervisors/riders
    val initialUserAccounts = listOf(
        UserAccount(
            id = "user_admin_01",
            username = "admin",
            password = "admin123",
            name = "Muhammad Rafiq (Admin)",
            phone = "0300-1234567",
            role = UserRole.ADMIN
        )
    )
}
