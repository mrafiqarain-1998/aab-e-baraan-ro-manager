package com.example.data.room

import com.example.model.*

object RoomDataMapper {

    fun deliveryToEntity(del: Delivery): CustomerDeliveryEntity {
        val summary = del.items.joinToString(", ") { "${it.quantity}x ${it.bottleName}" }
        return CustomerDeliveryEntity(
            id = del.id,
            deliveryNumber = del.deliveryNumber,
            customerId = del.customerId,
            customerName = del.customerName,
            customerPhone = del.customerPhone,
            customerAddress = del.customerAddress,
            customerArea = del.customerArea,
            date = del.date,
            time = del.time,
            totalFullDelivered = del.totalFullDelivered,
            totalEmptyCollected = del.totalEmptyCollected,
            subtotal = del.subtotal,
            deliveryCharges = del.deliveryCharges,
            totalAmount = del.totalAmount,
            paymentCollected = del.paymentCollected,
            paymentMethod = del.paymentMethod,
            status = del.status.name,
            notes = del.notes,
            invoiceId = del.invoiceId,
            itemsSummary = summary,
            timestamp = del.createdAt
        )
    }

    fun entityToDelivery(entity: CustomerDeliveryEntity): Delivery {
        val status = try {
            DeliveryStatus.valueOf(entity.status)
        } catch (e: Exception) {
            DeliveryStatus.DELIVERED
        }
        return Delivery(
            id = entity.id,
            deliveryNumber = entity.deliveryNumber,
            customerId = entity.customerId,
            customerName = entity.customerName,
            customerPhone = entity.customerPhone,
            customerAddress = entity.customerAddress,
            customerArea = entity.customerArea,
            date = entity.date,
            time = entity.time,
            items = emptyList(), // Items summary preserved in notes/summary
            totalFullDelivered = entity.totalFullDelivered,
            totalEmptyCollected = entity.totalEmptyCollected,
            status = status,
            deliveryCharges = entity.deliveryCharges,
            subtotal = entity.subtotal,
            totalAmount = entity.totalAmount,
            paymentCollected = entity.paymentCollected,
            paymentMethod = entity.paymentMethod,
            notes = entity.notes,
            invoiceId = entity.invoiceId,
            createdAt = entity.timestamp
        )
    }

    fun customerToBalanceEntity(cust: Customer): CustomerBalanceEntity {
        return CustomerBalanceEntity(
            customerId = cust.id,
            customerCode = cust.customerCode,
            customerName = cust.name,
            customerPhone = cust.phone,
            currentBalance = cust.currentBalance,
            emptyBottlesOwed = cust.emptyBottlesOwed,
            totalPurchases = cust.totalPurchases,
            active = cust.active,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun expenseToEntity(exp: Expense): DailyPlantExpenseEntity {
        return DailyPlantExpenseEntity(
            id = exp.id,
            voucherNumber = exp.voucherNumber,
            date = exp.date,
            categoryId = exp.categoryId,
            categoryName = exp.categoryName,
            description = exp.description,
            amount = exp.amount,
            paymentMethod = exp.paymentMethod.label,
            payee = exp.payee,
            enteredBy = exp.enteredBy,
            isAcknowledged = exp.isAcknowledged,
            notes = exp.notes,
            timestamp = System.currentTimeMillis()
        )
    }

    fun entityToExpense(entity: DailyPlantExpenseEntity): Expense {
        val method = when (entity.paymentMethod.lowercase()) {
            "cash" -> PaymentMethod.CASH
            "bank", "bank_transfer", "bank transfer" -> PaymentMethod.BANK_TRANSFER
            "jazzcash" -> PaymentMethod.JAZZCASH
            "easypaisa" -> PaymentMethod.EASYPAISA
            "cheque", "advance" -> PaymentMethod.ADVANCE_ADJUSTMENT
            else -> PaymentMethod.CASH
        }
        return Expense(
            id = entity.id,
            voucherNumber = entity.voucherNumber,
            date = entity.date,
            expenseType = ExpenseType.OPEX,
            categoryId = entity.categoryId,
            categoryName = entity.categoryName,
            description = entity.description,
            amount = entity.amount,
            paymentMethod = method,
            payee = entity.payee,
            enteredBy = entity.enteredBy,
            isAcknowledged = entity.isAcknowledged,
            notes = entity.notes
        )
    }

    fun inventoryToStockEntity(inv: BottleInventory): StockItemEntity {
        return StockItemEntity(
            id = inv.id,
            bottleSizeId = inv.bottleSizeId,
            sizeName = inv.sizeName,
            atPlantFull = inv.atPlantFull,
            atPlantEmpty = inv.atPlantEmpty,
            inUse = inv.inUse,
            fullPackets = inv.fullPackets,
            fullLooseBottles = inv.fullLooseBottles,
            damaged = inv.damaged,
            withStaff = inv.withStaff,
            withCustomers = inv.withCustomers,
            unitCost = 0.0,
            minStockThreshold = 10,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
