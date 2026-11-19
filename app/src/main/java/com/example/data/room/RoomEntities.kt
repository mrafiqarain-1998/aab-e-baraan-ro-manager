package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity for Customer Water Bottle Deliveries.
 * Tracks bottle counts (full delivered, empty collected), financials, and payment status.
 */
@Entity(tableName = "customer_deliveries")
data class CustomerDeliveryEntity(
    @PrimaryKey
    val id: String,
    val deliveryNumber: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerArea: String = "",
    val date: String, // YYYY-MM-DD
    val time: String = "",
    val totalFullDelivered: Int = 0,
    val totalEmptyCollected: Int = 0,
    val subtotal: Double = 0.0,
    val deliveryCharges: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentCollected: Double = 0.0,
    val paymentMethod: String = "Cash", // Cash, JazzCash, Easypaisa, Unpaid
    val status: String = "DELIVERED",
    val notes: String = "",
    val invoiceId: String = "",
    val itemsSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity for Customer Balance & Outstanding Ledger.
 * Tracks live outstanding amounts and empty bottles owed.
 */
@Entity(tableName = "customer_balances")
data class CustomerBalanceEntity(
    @PrimaryKey
    val customerId: String,
    val customerCode: String = "",
    val customerName: String,
    val customerPhone: String = "",
    val currentBalance: Double = 0.0,
    val emptyBottlesOwed: Int = 0,
    val totalPurchases: Double = 0.0,
    val active: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Room Entity for Daily Plant Expenses (Electricity, Vehicle Fuel, Salt, Maintenance, etc.).
 */
@Entity(tableName = "daily_plant_expenses")
data class DailyPlantExpenseEntity(
    @PrimaryKey
    val id: String,
    val voucherNumber: String,
    val date: String, // YYYY-MM-DD
    val categoryId: String,
    val categoryName: String, // e.g. "Electricity / بجلی", "Vehicle Fuel / پیٹرول"
    val description: String,
    val amount: Double,
    val paymentMethod: String = "Cash",
    val payee: String = "", // e.g. "PSO Pump", "WAPDA / LESCO"
    val enteredBy: String = "Admin",
    val isAcknowledged: Boolean = true,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity for Plant Bottle Stock Items.
 */
@Entity(tableName = "stock_items")
data class StockItemEntity(
    @PrimaryKey
    val id: String,
    val bottleSizeId: String,
    val sizeName: String,
    val atPlantFull: Int = 0,
    val atPlantEmpty: Int = 0,
    val inUse: Int = 0,
    val fullPackets: Int = 0,
    val fullLooseBottles: Int = 0,
    val damaged: Int = 0,
    val withStaff: Int = 0,
    val withCustomers: Int = 0,
    val unitCost: Double = 0.0,
    val minStockThreshold: Int = 10,
    val lastUpdated: Long = System.currentTimeMillis()
)
