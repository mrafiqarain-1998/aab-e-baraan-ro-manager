package com.example.model

enum class CustomerType(val label: String, val urduLabel: String) {
    HOME("Home (Ghar)", "گھر"),
    SHOP("Shop (Dukan)", "دکان"),
    OFFICE("Office (Daftar)", "دفتر"),
    RESTAURANT("Restaurant / Hotel", "ہوٹل / ریسٹورنٹ"),
    OTHER("Other (Deegar)", "دیگر")
}

enum class PaymentCycle(val label: String, val urduLabel: String) {
    PAY_ON_DELIVERY("Pay On Delivery (COD)", "ہر ڈیلیوری پر نقد"),
    DAILY("Daily (Rozana)", "روزانہ"),
    WEEKLY("Weekly (Haftawar)", "ہفتہ وار"),
    BILL_TO_BILL("Bill to Bill", "بل ٹو بل"),
    MONTHLY("Monthly (Mahana)", "ماہانہ"),
    ADVANCE("Advance Payment", "پیشگی ادائیگی"),
    CUSTOM("Custom Days", "مخصوص مدت")
}

data class Customer(
    val id: String = "",
    val customerCode: String = "",
    val name: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val area: String = "",
    val type: CustomerType = CustomerType.HOME,
    val paymentCycle: PaymentCycle = PaymentCycle.PAY_ON_DELIVERY,
    val registrationDate: String = "",
    val paymentDueDate: String = "",
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0, // Positive means customer owes business (Receivable)
    val creditLimit: Double = 5000.0,
    val bottleDeposit: Double = 0.0, // Security Deposit
    val securityDeposit: Double = 0.0,
    val emptyBottlesOwed: Int = 0, // Number of empty bottles currently held by customer
    val totalPurchases: Double = 0.0,
    val notes: String = "",
    val active: Boolean = true,
    val businessId: String = "aab_e_baraan_plant_01",
    val createdBy: String = "",
    val createdByUid: String = "",
    val createdAt: Long = 0L,
    val lastEditedAt: Long = 0L,
    val lastEditedBy: String = ""
) {
    val effectiveSecurityDeposit: Double get() = if (securityDeposit > 0) securityDeposit else bottleDeposit

    /**
     * Returns the list of incomplete customer profile details.
     * Used to show a blinking orange indicator until all details are filled.
     */
    fun missingDetailLabels(): List<String> = buildList {
        if (name.isBlank()) add("Name")
        if (phone.isBlank()) add("Phone")
        if (whatsapp.isBlank()) add("WhatsApp")
        if (area.isBlank()) add("Area")
        if (address.isBlank()) add("Address")
    }

    val hasMissingDetails: Boolean get() = missingDetailLabels().isNotEmpty()
}
