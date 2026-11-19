package com.example.model

enum class InvoiceStatus(val label: String, val urduLabel: String) {
    UNPAID("Unpaid (Ghair Ada Shuda)", "غیر ادا شدہ"),
    PARTIALLY_PAID("Partially Paid (Juzwi Ada)", "جزوی ادا شدہ"),
    PAID("Paid (Mukammal Ada)", "مکمل ادا شدہ")
}

data class Invoice(
    val id: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val deliveryId: String = "",
    val date: String = "",
    val dueDate: String = "",
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val status: InvoiceStatus = InvoiceStatus.UNPAID,
    val itemsSummary: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val createdAt: Long = System.currentTimeMillis()
)
