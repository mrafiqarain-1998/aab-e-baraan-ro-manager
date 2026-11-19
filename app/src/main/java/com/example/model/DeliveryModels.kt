package com.example.model

enum class DeliveryStatus(val label: String, val urduLabel: String) {
    PENDING("Pending (Muntazir)", "منتظر"),
    ASSIGNED("Assigned to Staff (Hawale Shuda)", "حوالے شدہ"),
    DELIVERED("Delivered (Pohanch Gayi)", "پہنچ گئی"),
    PARTIALLY_DELIVERED("Partially Delivered (Juzwi)", "جزوی ڈیلیور"),
    CANCELLED("Cancelled (Mansookh)", "منسوخ")
}

data class DeliveryItem(
    val bottleSizeId: String = "",
    val variantId: String = "",
    val packSize: Int = 1,
    val bottleName: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 120.0,
    val emptyCollected: Int = 1,
    val lineTotal: Double = 120.0
)

data class Delivery(
    val id: String = "",
    val deliveryNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerArea: String = "",
    val date: String = "", // YYYY-MM-DD
    val time: String = "",
    val items: List<DeliveryItem> = emptyList(),
    val totalFullDelivered: Int = 0,
    val totalEmptyCollected: Int = 0,
    val assignedStaffId: String = "",
    val staffName: String = "",
    val status: DeliveryStatus = DeliveryStatus.DELIVERED,
    val deliveryCharges: Double = 0.0,
    val subtotal: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentCollected: Double = 0.0,
    val paymentMethod: String = "Cash", // Cash, JazzCash, Easypaisa, Unpaid
    val notes: String = "",
    val invoiceId: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val createdBy: String = "",
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
