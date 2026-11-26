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

data class RecordViewEntry(
    val userId: String = "",
    val userName: String = "",
    val viewedAt: Long = System.currentTimeMillis()
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
    val discount: Double = 0.0,
    val subtotal: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentCollected: Double = 0.0,
    val paymentMethod: String = "Cash", // Cash, JazzCash, Easypaisa, Unpaid
    val notes: String = "",
    val invoiceId: String = "",
    val businessId: String = "aab_e_baraan_plant_01",
    val createdBy: String = "",
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastEditedAt: Long = 0L,
    val lastEditedBy: String = "",
    val lastEditNote: String = "",
    val viewedBy: List<RecordViewEntry> = emptyList()
)

enum class DeliveryPackagingUnit(
    val singular: String,
    val plural: String,
    val urduSingular: String,
    val urduPlural: String
) {
    CARTON("Carton", "Cartons", "کارٹن", "کارٹن"),
    PACKET("Packet", "Packets", "پیکٹ", "پیکٹ"),
    BOTTLE("Bottle", "Bottles", "بوتل", "بوتلیں");

    fun labelFor(quantity: Int): String {
        val en = if (quantity == 1) singular else plural
        val ur = if (quantity == 1) urduSingular else urduPlural
        return "$en ($ur)"
    }
}

data class DeliveredBottleBreakdownRow(
    val key: String,
    val displayLabel: String,
    val unit: DeliveryPackagingUnit,
    val quantity: Int
)

fun resolveDeliveryItemPackagingUnit(
    item: DeliveryItem,
    bottleSize: BottleSize? = null
): DeliveryPackagingUnit {
    val variantLower = item.variantId.lowercase()
    val nameLower = item.bottleName.lowercase()

    if (variantLower.contains("carton") ||
        nameLower.contains("carton") ||
        Regex("\\bctn\\b").containsMatchIn(nameLower)
    ) {
        return DeliveryPackagingUnit.CARTON
    }

    if (variantLower.contains("packet") ||
        variantLower.contains("pack") ||
        nameLower.contains("packet") ||
        Regex("\\bpkt\\b").containsMatchIn(nameLower) ||
        Regex("\\bpack\\b").containsMatchIn(nameLower)
    ) {
        return DeliveryPackagingUnit.PACKET
    }

    if (item.packSize > 1) {
        val baseToken = shortBaseSizeToken(
            bottleSize?.name?.ifBlank { item.bottleName } ?: item.bottleName,
            bottleSize?.capacityLitres ?: 0.0
        )
        return if ((baseToken.equals("500ml", ignoreCase = true) && item.packSize >= 24) ||
            (baseToken.equals("1.5L", ignoreCase = true) && item.packSize >= 12)
        ) {
            DeliveryPackagingUnit.CARTON
        } else {
            DeliveryPackagingUnit.PACKET
        }
    }

    if (nameLower.contains("bottle") || bottleSize?.deliveryUnit == DeliveryUnit.PER_BOTTLE) {
        return DeliveryPackagingUnit.BOTTLE
    }

    if (bottleSize?.deliveryUnit == DeliveryUnit.PER_PACKET &&
        (bottleSize.packQuantity > 1 || bottleSize.packetPackagingQty > 1)
    ) {
        return DeliveryPackagingUnit.PACKET
    }

    return DeliveryPackagingUnit.BOTTLE
}

fun aggregateDeliveredBottlesBySize(
    deliveries: List<Delivery>,
    bottleSizes: List<BottleSize> = emptyList()
): List<DeliveredBottleBreakdownRow> {
    val sizeById = bottleSizes.associateBy { it.id }
    val totals = linkedMapOf<String, DeliveredBottleBreakdownRow>()

    fun addRow(baseLabel: String, unit: DeliveryPackagingUnit, qty: Int) {
        if (qty <= 0) return
        val cleanBase = baseLabel.trim().ifBlank { "19L" }
        val key = "${cleanBase.lowercase()}__${unit.name}"
        val displayLabel = "$cleanBase ${unit.singular}"
        val existing = totals[key]
        totals[key] = if (existing != null) {
            existing.copy(quantity = existing.quantity + qty)
        } else {
            DeliveredBottleBreakdownRow(
                key = key,
                displayLabel = displayLabel,
                unit = unit,
                quantity = qty
            )
        }
    }

    deliveries.forEach { del ->
        if (del.items.isEmpty()) {
            if (del.totalFullDelivered > 0) {
                addRow("19L", DeliveryPackagingUnit.BOTTLE, del.totalFullDelivered)
            }
        } else {
            del.items.forEach { item ->
                val matchedSize = sizeById[item.bottleSizeId]
                val unit = resolveDeliveryItemPackagingUnit(item, matchedSize)
                val rawName = matchedSize?.name?.ifBlank { item.bottleName }
                    ?: item.bottleName.ifBlank { "19L" }
                val stripped = cleanBaseBottleName(rawName)
                    .replace(Regex("\\s*(cartons?|packets?|packs?|bottles?|refills?|ctn|pkt)\\s*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                val baseToken = shortBaseSizeToken(
                    stripped.ifBlank { rawName },
                    matchedSize?.capacityLitres ?: 0.0
                ).replace(Regex("\\s*(cartons?|packets?|packs?|bottles?|refills?|ctn|pkt)\\s*$", RegexOption.IGNORE_CASE), "")
                    .trim()
                    .ifBlank { stripped.ifBlank { "19L" } }
                addRow(baseToken, unit, item.quantity)
            }
        }
    }

    return totals.values.toList()
}

