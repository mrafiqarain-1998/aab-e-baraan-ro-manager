package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class AddExpenseStep {
    CHOICE,
    NON_INVENTORY,
    INVENTORY
}

private data class UnifiedInventoryItemOption(
    val itemType: String, // "BOTTLE" or "MATERIAL"
    val itemId: String,
    val name: String,
    val categoryName: String,
    val uom: String,
    val defaultRate: Double,
    val defaultSupplier: String,
    val currentStockLabel: String
)

private data class InventoryLineDraft(
    val rowKey: Long,
    val itemType: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val categoryName: String = "",
    val uom: String = "",
    val qtyText: String = "",
    val rateText: String = ""
) {
    val qtyVal: Double get() = qtyText.toDoubleOrNull() ?: 0.0
    val rateVal: Double get() = rateText.toDoubleOrNull() ?: 0.0
    val lineTotal: Double get() = qtyVal * rateVal
}

private fun formatCleanNumber(v: Double): String {
    if (v <= 0.0) return ""
    return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", v)
}

internal fun isBottleInventoryCategory(categoryName: String): Boolean {
    val lc = categoryName.trim().lowercase()
    if (lc.isBlank()) return false
    // Categories specifically for bottles (e.g. "Bottle Purchase", "Bottles", "Water Bottle", "PET Bottle")
    // Exclude accessory categories like "Bottle Caps" or "Bottle Labels" unless strictly Bottle Purchase
    if (lc.contains("cap") || lc.contains("seal") || lc.contains("label") || lc.contains("packing")) {
        return false
    }
    return lc.contains("bottle")
}

private fun defaultDescriptionForCategory(categoryName: String, workerName: String? = null): String {
    val lc = categoryName.trim().lowercase()
    return when {
        lc.contains("salary") || lc.contains("staff") -> {
            if (!workerName.isNullOrBlank()) "Salary / Advance paid to Mr. $workerName"
            else "Salary / Advance paid to Mr. "
        }
        lc.contains("fuel") || lc.contains("rent") -> "Suzuki Fuel for Daily Water Delivery"
        lc.contains("vehicle") -> "Monthly Suzuki Maintenance"
        lc.contains("plant") && lc.contains("maint") -> "Monthly RO Plant Maintenance Working Done"
        lc.contains("maint") -> "Monthly RO Plant Maintenance Working Done"
        lc.contains("electric") || lc.contains("wapda") || lc.contains("bill") -> "Monthly RO Plant Electricity Bill Paid"
        lc.contains("entertain") || lc.contains("tea") || lc.contains("food") -> "Staff Tea & Refreshment Expense"
        lc.contains("general") -> "General Plant Operational Expense"
        lc.contains("other") -> "Miscellaneous Plant Expense"
        categoryName.isNotBlank() -> "$categoryName Expense"
        else -> ""
    }
}

private fun buildUnifiedInventoryOptions(
    bottleSizes: List<BottleSize>,
    factoryMaterials: List<FactoryMaterial>
): List<UnifiedInventoryItemOption> {
    val bottles = bottleSizes.filter { it.active }.map { size ->
        UnifiedInventoryItemOption(
            itemType = "BOTTLE",
            itemId = size.id,
            name = size.name,
            categoryName = size.categoryName.trim(),
            uom = size.uom.ifBlank { "Bottles" },
            defaultRate = if (size.latestPurchaseRate > 0.0) size.latestPurchaseRate else size.purchaseCost,
            defaultSupplier = "",
            currentStockLabel = "${size.capacityLitres}L"
        )
    }
    val materials = factoryMaterials.filter { it.active }.map { mat ->
        val stkStr = if (mat.currentStock % 1.0 == 0.0) mat.currentStock.toInt().toString()
        else String.format(Locale.ENGLISH, "%.1f", mat.currentStock)
        UnifiedInventoryItemOption(
            itemType = "MATERIAL",
            itemId = mat.id,
            name = mat.name,
            categoryName = mat.itemCategory.trim(),
            uom = mat.uom.ifBlank { "Piece" },
            defaultRate = mat.purchaseRate,
            defaultSupplier = mat.supplierName,
            currentStockLabel = "$stkStr ${mat.uom}"
        )
    }
    return bottles + materials
}

// =========================================================================
// 1. COMPACT SMART DIALOG SHELL & TIGHT FORM CONTROLS (Fits Front Screen)
// =========================================================================

@Composable
internal fun CompactExpenseDialogShell(
    title: String,
    subtitle: String? = null,
    accentColor: Color = WaterNavyPrimary,
    headerTrailing: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    footerContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 460.dp)
                .heightIn(max = 740.dp)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Slim Header
                Surface(
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 13.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (headerTrailing != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            headerTrailing()
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("expense_dialog_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Compact Body (no scroll needed for standard forms; scrolls smoothly for multi-item)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    content = content
                )

                if (footerContent != null) {
                    HorizontalDivider(color = BorderLight)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = footerContent
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmartCompactInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, BorderLight),
        modifier = modifier.height(42.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    cursorBrush = SolidColor(WaterNavyPrimary),
                    textStyle = TextStyle(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun <T> SmartCompactDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    optionSubLabel: ((T) -> String?)? = null,
    onOptionSelected: (T) -> Unit,
    placeholder: String = "Select...",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            border = BorderStroke(1.dp, if (expanded) WaterBlueMedium else BorderLight),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = WaterNavyPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedText.ifBlank { placeholder },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedText.isBlank()) TextMuted else TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Open $label",
                    tint = WaterNavyPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
            modifier = Modifier
                .widthIn(min = 210.dp, max = 360.dp)
                .heightIn(max = 280.dp)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No items available", fontSize = 11.sp, color = TextMuted) },
                    onClick = { expanded = false }
                )
            } else {
                options.forEach { option ->
                    val sub = optionSubLabel?.invoke(option)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = optionLabel(option),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WaterNavyDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!sub.isNullOrBlank()) {
                                    Text(
                                        text = sub,
                                        fontSize = 9.sp,
                                        color = TextSecondaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onOptionSelected(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun <T> SmartSegmentedPill(
    label: String? = null,
    selected: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                maxLines = 1
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = WaterSkyLight.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, BorderLight),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { (value, text) ->
                    val isSel = selected == value
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) WaterSkyButton else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelect(value) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = text,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSel) WaterSkyButtonContent else WaterNavyDark,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactDateChipButton(
    date: String,
    onDatePicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = WaterSkyLight,
        border = BorderStroke(1.dp, WaterBlueMedium.copy(alpha = 0.45f)),
        modifier = modifier
            .height(32.dp)
            .clickable {
                val cal = Calendar.getInstance()
                try {
                    val parts = date.split("-")
                    if (parts.size == 3) cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                } catch (_: Exception) {}
                DatePickerDialog(
                    context,
                    { _, y, m, d ->
                        onDatePicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CalendarToday,
                contentDescription = "Pick Date",
                tint = WaterNavyPrimary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = date,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterNavyPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// =========================================================================
// 2. ADD EXPENSE DIALOG (Step 0: Choice -> Step 1A: Non-Inventory / Step 1B: Inventory)
// =========================================================================

@Composable
fun AddExpenseDialog(
    categories: List<ExpenseCategory>,
    isAdmin: Boolean,
    factoryMaterials: List<FactoryMaterial> = emptyList(),
    bottleSizes: List<BottleSize> = emptyList(),
    plantWorkers: List<PlantWorker> = emptyList(),
    onGetWorkerSalarySummary: ((workerId: String, month: String) -> WorkerSalarySummary)? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double, PaymentMethod, String, String, ExpenseType, String) -> Unit,
    onSaveWithSource: ((String, String, String, Double, PaymentMethod, String, String, ExpenseType, String, PaymentSource) -> Unit)? = null,
    onSaveWithSourceAndStaff: ((
        catId: String,
        catName: String,
        desc: String,
        amt: Double,
        method: PaymentMethod,
        payee: String,
        notes: String,
        type: ExpenseType,
        date: String,
        source: PaymentSource,
        invoiceNo: String,
        staffMemberId: String,
        staffMemberName: String
    ) -> Unit)? = null,
    onSaveWithMaterialRefill: ((
        materialId: String,
        qty: Double,
        unitCost: Double,
        catId: String,
        catName: String,
        expType: ExpenseType,
        desc: String,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod
    ) -> Unit)? = null,
    onSaveWithBottlePurchase: ((
        bottleSizeId: String,
        qty: Int,
        unitCost: Double,
        catId: String,
        catName: String,
        expType: ExpenseType,
        desc: String,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod
    ) -> Unit)? = null,
    onSaveMultiItemInventory: ((
        items: List<ExpenseInventoryLineItem>,
        expType: ExpenseType,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod
    ) -> Unit)? = null,
    onAddCategory: (String, String, ExpenseType) -> Unit = { _, _, _ -> },
    onAddCategoryWithType: ((name: String, urdu: String, type: ExpenseType, isInventory: Boolean) -> Unit)? = null,
    onAddNewMaterial: ((FactoryMaterial) -> String)? = null,
    onAddNewBottleSize: ((
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        refillPrice: Double,
        purchaseCost: Double,
        uom: String,
        categoryName: String
    ) -> String)? = null
) {
    var step by remember { mutableStateOf(AddExpenseStep.CHOICE) }

    when (step) {
        AddExpenseStep.CHOICE -> {
            ExpenseTypeChoiceDialog(
                onSelectInventory = { step = AddExpenseStep.INVENTORY },
                onSelectNonInventory = { step = AddExpenseStep.NON_INVENTORY },
                onDismiss = onDismiss
            )
        }

        AddExpenseStep.NON_INVENTORY -> {
            NonInventoryExpenseFormDialog(
                categories = categories,
                plantWorkers = plantWorkers,
                onGetWorkerSalarySummary = onGetWorkerSalarySummary,
                onBack = { step = AddExpenseStep.CHOICE },
                onDismiss = onDismiss,
                onSubmit = { catId, catName, desc, amt, method, vendor, notes, expType, date, source, staffId, staffName, _ ->
                    if (onSaveWithSourceAndStaff != null) {
                        onSaveWithSourceAndStaff(
                            catId, catName, desc, amt, method, vendor, notes,
                            expType, date, source, "", staffId, staffName
                        )
                    } else if (onSaveWithSource != null) {
                        onSaveWithSource(catId, catName, desc, amt, method, vendor, notes, expType, date, source)
                    } else {
                        onSave(catId, catName, desc, amt, method, vendor, notes, expType, date)
                    }
                    onDismiss()
                }
            )
        }

        AddExpenseStep.INVENTORY -> {
            InventoryExpenseFormDialog(
                title = "Inventory Expense",
                subtitle = "اسٹاک / بوتل و میٹریل خریداری",
                categories = categories,
                bottleSizes = bottleSizes,
                factoryMaterials = factoryMaterials,
                initialExpense = null,
                onBack = { step = AddExpenseStep.CHOICE },
                onDismiss = onDismiss,
                onAddNewMaterial = onAddNewMaterial,
                onAddNewBottleSize = onAddNewBottleSize,
                onSubmit = { items, expType, supplier, invoiceNo, notes, date, source, method, _ ->
                    if (onSaveMultiItemInventory != null) {
                        onSaveMultiItemInventory(items, expType, supplier, invoiceNo, notes, date, source, method)
                    } else if (items.size == 1) {
                        val single = items.first()
                        if (single.itemType == "BOTTLE" && onSaveWithBottlePurchase != null) {
                            onSaveWithBottlePurchase(
                                single.itemId, single.quantity.toInt(), single.unitRate,
                                single.categoryId, single.categoryName, expType,
                                "${single.quantity.toInt()} x ${single.itemName}",
                                supplier, invoiceNo, notes, date, source, method
                            )
                        } else if (onSaveWithMaterialRefill != null) {
                            onSaveWithMaterialRefill(
                                single.itemId, single.quantity, single.unitRate,
                                single.categoryId, single.categoryName, expType,
                                "${formatCleanNumber(single.quantity)} x ${single.itemName}",
                                supplier, invoiceNo, notes, date, source, method
                            )
                        }
                    }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ExpenseTypeChoiceDialog(
    onSelectInventory: () -> Unit,
    onSelectNonInventory: () -> Unit,
    onDismiss: () -> Unit
) {
    CompactExpenseDialogShell(
        title = "Add Expense (نیا خرچہ)",
        subtitle = "Select expense type (خرچے کی قسم منتخب کریں)",
        accentColor = WaterNavyPrimary,
        onDismiss = onDismiss
    ) {
        Text(
            text = "What type of expense would you like to add?\nآپ کون سا خرچہ شامل کرنا چاہتے ہیں؟",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryDark,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpenseChoiceCard(
                title = "Inventory\nExpense",
                urduTitle = "اسٹاک خریداری",
                description = "Bottles & Materials\nAuto updates stock",
                icon = Icons.Default.Inventory2,
                accentColor = SuccessGreen,
                bgColor = SuccessGreenLight,
                onClick = onSelectInventory,
                modifier = Modifier
                    .weight(1f)
                    .testTag("choice_inventory_expense_btn")
            )

            ExpenseChoiceCard(
                title = "Non-Inventory\nExpense",
                urduTitle = "عمومی اخراجات",
                description = "Bills, Fuel, Rent,\nSalary & General",
                icon = Icons.Default.ReceiptLong,
                accentColor = WaterBlueDeep,
                bgColor = WaterSkyLight,
                onClick = onSelectNonInventory,
                modifier = Modifier
                    .weight(1f)
                    .testTag("choice_non_inventory_expense_btn")
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun ExpenseChoiceCard(
    title: String,
    urduTitle: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    bgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
        shadowElevation = 2.dp,
        modifier = modifier.height(152.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(23.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WaterNavyDark,
                    lineHeight = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = urduTitle,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1
                )
            }

            Text(
                text = description,
                fontSize = 8.5.sp,
                color = TextSecondaryDark,
                lineHeight = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// =========================================================================
// 3. NON-INVENTORY EXPENSE FORM (Add & Edit — Fits Front Screen)
// =========================================================================

@Composable
private fun NonInventoryExpenseFormDialog(
    categories: List<ExpenseCategory>,
    plantWorkers: List<PlantWorker>,
    onGetWorkerSalarySummary: ((workerId: String, month: String) -> WorkerSalarySummary)?,
    initialExpense: Expense? = null,
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        catId: String,
        catName: String,
        desc: String,
        amt: Double,
        method: PaymentMethod,
        vendor: String,
        notes: String,
        type: ExpenseType,
        date: String,
        source: PaymentSource,
        staffId: String,
        staffName: String,
        editReason: String
    ) -> Unit
) {
    val isEdit = initialExpense != null
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val activeWorkers = remember(plantWorkers) { plantWorkers.filter { it.active } }

    // Strictly Non-Inventory categories only (preserve existing category if editing a legacy record)
    val nonInventoryCategories = remember(categories, initialExpense) {
        val base = categories.filter { !it.isInventoryCategory }.distinctBy { it.name.trim().lowercase() }
        if (initialExpense != null && base.none {
                it.id == initialExpense.categoryId || it.name.equals(initialExpense.categoryName, ignoreCase = true)
            }
        ) {
            val existing = categories.find {
                it.id == initialExpense.categoryId || it.name.equals(initialExpense.categoryName, ignoreCase = true)
            }
            if (existing != null) listOf(existing) + base else base
        } else {
            base
        }
    }

    var selectedType by remember { mutableStateOf(initialExpense?.expenseType ?: ExpenseType.OPEX) }
    var expenseDate by remember { mutableStateOf(initialExpense?.date?.ifBlank { today } ?: today) }
    val initialSelectedCat = remember(nonInventoryCategories, initialExpense) {
        if (initialExpense != null) {
            nonInventoryCategories.find {
                it.id == initialExpense.categoryId || it.name.equals(initialExpense.categoryName, ignoreCase = true)
            } ?: nonInventoryCategories.firstOrNull()
        } else {
            nonInventoryCategories.firstOrNull { it.type == ExpenseType.OPEX }
                ?: nonInventoryCategories.firstOrNull()
        }
    }
    var selectedCategory by remember(nonInventoryCategories) { mutableStateOf(initialSelectedCat) }

    val initialAutoDesc = remember(initialSelectedCat, isEdit) {
        if (!isEdit && initialSelectedCat != null) defaultDescriptionForCategory(initialSelectedCat.name) else ""
    }
    var lastAutoDescription by remember { mutableStateOf(initialAutoDesc) }
    var description by remember {
        mutableStateOf(
            if (isEdit) initialExpense?.description.orEmpty() else initialAutoDesc
        )
    }
    var amountText by remember {
        mutableStateOf(if (initialExpense != null && initialExpense.amount > 0.0) formatCleanNumber(initialExpense.amount) else "")
    }
    var vendorName by remember { mutableStateOf(initialExpense?.payee.orEmpty()) }
    var paymentSource by remember { mutableStateOf(initialExpense?.paymentSource ?: PaymentSource.PAY_FROM_SALE) }
    var paymentMethod by remember {
        mutableStateOf(
            if (initialExpense == null || initialExpense.paymentMethod.isCash) PaymentMethod.CASH
            else PaymentMethod.BANK_TRANSFER
        )
    }
    var notes by remember { mutableStateOf(initialExpense?.notes.orEmpty()) }
    var editReasonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Staff Salary support (preserved cleanly when Staff Salary category selected)
    val isSalaryCategory = selectedCategory?.let {
        it.id == "cat_salary" || it.name.contains("salary", ignoreCase = true)
    } == true
    var selectedWorker by remember(activeWorkers) {
        mutableStateOf(
            if (initialExpense != null && initialExpense.staffMemberId.isNotBlank()) {
                activeWorkers.find { it.id == initialExpense.staffMemberId }
            } else null
        )
    }
    val expenseMonth = remember(expenseDate) {
        if (expenseDate.length >= 7) expenseDate.substring(0, 7)
        else SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }
    val workerSummary = remember(selectedWorker, expenseMonth) {
        selectedWorker?.let { w -> onGetWorkerSalarySummary?.invoke(w.id, expenseMonth) }
    }

    val enteredAmt = amountText.trim().toDoubleOrNull() ?: 0.0
    val editWarnings = remember(isEdit, initialExpense, enteredAmt, expenseDate, paymentSource, isSalaryCategory, workerSummary, selectedWorker) {
        if (!isEdit || initialExpense == null) emptyList()
        else buildList {
            if (initialExpense.amount > 0.0 && enteredAmt > 0.0 &&
                kotlin.math.abs(enteredAmt - initialExpense.amount) >= maxOf(initialExpense.amount * 0.5, 5000.0)
            ) {
                add("Large amount change: Rs. ${initialExpense.amount.toInt()} → Rs. ${enteredAmt.toInt()}. Please verify.")
            }
            if (expenseDate != initialExpense.date) {
                add("Date changed (${initialExpense.date} → $expenseDate). Daily/Monthly reports & Cash Closing will adjust.")
            }
            if (paymentSource != initialExpense.paymentSource) {
                add("Payment source changed (affects daily drawer cash closing).")
            }
            if (isSalaryCategory && workerSummary != null && enteredAmt > workerSummary.monthlySalary + 0.01) {
                add("Amount (Rs. ${enteredAmt.toInt()}) exceeds ${selectedWorker?.name ?: "staff"}'s monthly salary (Rs. ${workerSummary.monthlySalary.toInt()}).")
            }
        }
    }

    // Confirmation dialog on Edit
    var showEditConfirm by remember { mutableStateOf(false) }

    fun validateAndProceed() {
        val cat = selectedCategory
        val amt = amountText.trim().toDoubleOrNull() ?: 0.0
        val finalDesc = description.trim().ifBlank {
            if (isSalaryCategory && selectedWorker != null) {
                "Salary / Advance paid to Mr. ${selectedWorker!!.name}"
            } else ""
        }
        when {
            cat == null -> errorMessage = "Please select a Category."
            finalDesc.isBlank() -> errorMessage = "Please enter Description."
            amt <= 0.0 -> errorMessage = "Please enter a valid Amount."
            else -> {
                if (isEdit) {
                    showEditConfirm = true
                } else {
                    val staffId = if (isSalaryCategory) selectedWorker?.id.orEmpty() else ""
                    val staffName = if (isSalaryCategory) selectedWorker?.name.orEmpty() else ""
                    val finalVendor = vendorName.trim().ifBlank { staffName }
                    onSubmit(
                        cat.id, cat.name, finalDesc, amt, paymentMethod, finalVendor,
                        notes.trim(), selectedType, expenseDate, paymentSource, staffId, staffName, ""
                    )
                }
            }
        }
    }

    if (showEditConfirm && selectedCategory != null) {
        val cat = selectedCategory!!
        val amt = amountText.trim().toDoubleOrNull() ?: 0.0
        val finalDesc = description.trim().ifBlank { initialExpense?.description.orEmpty() }
        val staffId = if (isSalaryCategory) selectedWorker?.id.orEmpty() else ""
        val staffName = if (isSalaryCategory) selectedWorker?.name.orEmpty() else ""
        val finalVendor = vendorName.trim().ifBlank { staffName }

        AlertDialog(
            onDismissRequest = { showEditConfirm = false },
            containerColor = Color.White,
            icon = {
                Icon(
                    imageVector = if (editWarnings.isNotEmpty()) Icons.Default.Warning else Icons.Default.EditNote,
                    contentDescription = null,
                    tint = if (editWarnings.isNotEmpty()) WarningOrange else WaterNavyPrimary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (editWarnings.isNotEmpty()) "Warning — Confirm Edit?" else "Confirm Edit Expense?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (editWarnings.isNotEmpty()) WarningOrange else WaterNavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Save changes to Voucher #${initialExpense?.voucherNumber} ($expenseDate • ${cat.name} • Rs. ${amt.toInt()})?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                    if (editWarnings.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarningOrangeLight,
                            border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                editWarnings.forEach { w ->
                                    Text("• $w", fontSize = 9.5.sp, color = Color(0xFF9A3412), lineHeight = 13.sp)
                                }
                            }
                        }
                    }
                    Text(
                        "کیا آپ واقعی اس خرچے میں تبدیلی محفوظ کرنا چاہتے ہیں؟",
                        fontSize = 10.5.sp,
                        color = TextSecondaryDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditConfirm = false
                        onSubmit(
                            cat.id, cat.name, finalDesc, amt, paymentMethod, finalVendor,
                            notes.trim(), selectedType, expenseDate, paymentSource, staffId, staffName, editReasonText.trim()
                        )
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_edit_expense_dialog_btn")
                ) {
                    Text("Yes, Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditConfirm = false }) {
                    Text("Cancel", color = TextSecondaryDark, fontSize = 11.sp)
                }
            }
        )
    }

    CompactExpenseDialogShell(
        title = if (isEdit) "Edit Non-Inventory #${initialExpense!!.voucherNumber}" else "Non-Inventory Expense",
        subtitle = "عمومی اخراجات (Bills, Fuel, Salary, Rent)",
        accentColor = if (isEdit) WaterNavyPrimary else ErrorRed,
        onDismiss = onDismiss,
        footerContent = {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondaryDark, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Back", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                    Text("Cancel", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { validateAndProceed() },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(if (isEdit) "save_edit_expense_btn" else "save_expense_voucher_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isEdit) "Save Changes" else "Submit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    ) {
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ErrorRedLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 1. OPEX / CAPEX + Date (Compact single row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            SmartSegmentedPill(
                label = "Expense Type*",
                selected = selectedType,
                options = listOf(ExpenseType.OPEX to "OPEX", ExpenseType.CAPEX to "CAPEX"),
                onSelect = { newType ->
                    selectedType = newType
                    errorMessage = null
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("expense_type_toggle")
            )
            CompactDateChipButton(
                date = expenseDate,
                onDatePicked = { expenseDate = it },
                modifier = Modifier.testTag("expense_date_btn")
            )
        }

        // 2. Category Dropdown (Non-Inventory categories only — managed from Expenses tab)
        SmartCompactDropdown(
            label = "Category (کیٹیگری)*",
            selectedText = selectedCategory?.name.orEmpty(),
            options = nonInventoryCategories,
            optionLabel = { it.name },
            optionSubLabel = {
                "${it.type.name}${if (it.urduName.isNotBlank()) " • ${it.urduName}" else ""}"
            },
            onOptionSelected = { cat ->
                selectedCategory = cat
                val nextDesc = defaultDescriptionForCategory(
                    categoryName = cat.name,
                    workerName = if (cat.id == "cat_salary" || cat.name.contains("salary", ignoreCase = true)) {
                        selectedWorker?.name
                    } else null
                )
                if (!isEdit && (description.isBlank() || description == lastAutoDescription || description.startsWith("Salary / Advance paid to Mr."))) {
                    description = nextDesc
                    lastAutoDescription = nextDesc
                }
                errorMessage = null
            },
            placeholder = "Select Category...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_category_dropdown")
        )

        // Optional Staff Salary Selector when Staff Salary category chosen
        if (isSalaryCategory && activeWorkers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, WaterAqua.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SmartCompactDropdown(
                        label = "Staff Member (عملہ)*",
                        selectedText = selectedWorker?.name.orEmpty(),
                        options = activeWorkers,
                        optionLabel = { w ->
                            val sum = onGetWorkerSalarySummary?.invoke(w.id, expenseMonth)
                            if (sum != null && sum.paidOrAdvanceThisMonth > 0.0) {
                                "${w.name} • Rem Rs. ${sum.remainingSalary.toInt()}"
                            } else {
                                "${w.name} • Rs. ${w.monthlySalary.toInt()}"
                            }
                        },
                        onOptionSelected = { worker ->
                            selectedWorker = worker
                            vendorName = worker.name
                            val sum = onGetWorkerSalarySummary?.invoke(worker.id, expenseMonth)
                            val remAmount = sum?.remainingSalary ?: worker.monthlySalary
                            if (remAmount > 0.0) amountText = remAmount.toInt().toString()
                            val workerDesc = "Salary / Advance paid to Mr. ${worker.name}"
                            if (!isEdit && (description.isBlank() || description == lastAutoDescription || description.startsWith("Salary"))) {
                                description = workerDesc
                                lastAutoDescription = workerDesc
                            }
                            errorMessage = null
                        },
                        placeholder = "Choose Staff Member...",
                        modifier = Modifier.fillMaxWidth()
                    )
                    workerSummary?.let { s ->
                        Text(
                            text = "Salary: Rs.${s.monthlySalary.toInt()}  •  Adv: Rs.${s.paidOrAdvanceThisMonth.toInt()}  •  Rem: Rs.${s.remainingSalary.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 3. Description
        SmartCompactInput(
            label = "Description (تفصیل)*",
            value = description,
            onValueChange = { description = it; errorMessage = null },
            placeholder = "Enter expense description...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_description_input")
        )

        // 4. Amount + 5. Vendor Name (Smart 2-column row so form fits cleanly without scrolling)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmartCompactInput(
                label = "Amount (Rs.)*",
                value = amountText,
                onValueChange = {
                    amountText = it.filter { ch -> ch.isDigit() || ch == '.' }
                    errorMessage = null
                },
                placeholder = "0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(0.45f)
                    .testTag("expense_amount_input")
            )

            SmartCompactInput(
                label = "Vendor Name",
                value = vendorName,
                onValueChange = { vendorName = it },
                placeholder = "Vendor / Payee",
                modifier = Modifier
                    .weight(0.55f)
                    .testTag("expense_vendor_input")
            )
        }

        // 6. Payment Source + 7. Payment Mode (Smart 2-column row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmartSegmentedPill(
                label = "Payment Source",
                selected = paymentSource,
                options = listOf(
                    PaymentSource.PAY_FROM_SALE to "Sale",
                    PaymentSource.PAY_FROM_INVESTMENT to "Invest"
                ),
                onSelect = { paymentSource = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("expense_source_toggle")
            )

            SmartSegmentedPill(
                label = "Payment Mode",
                selected = paymentMethod,
                options = listOf(
                    PaymentMethod.CASH to "Cash",
                    PaymentMethod.BANK_TRANSFER to "Online"
                ),
                onSelect = { paymentMethod = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("expense_mode_toggle")
            )
        }

        // Optional Note (compact single line)
        SmartCompactInput(
            label = "Note (Optional)",
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Optional note...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_note_input")
        )

        if (isEdit) {
            SmartCompactInput(
                label = "Edit Reason / Note (تبدیلی کی وجہ)",
                value = editReasonText,
                onValueChange = { editReasonText = it },
                placeholder = "Optional reason for edit...",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_expense_reason_input")
            )
            if (editWarnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WarningOrangeLight,
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        editWarnings.forEach { warn ->
                            Text("⚠ $warn", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412), lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. INVENTORY EXPENSE FORM (Multi-Item Add & Edit — Fits Front Screen)
// =========================================================================

@Composable
internal fun InventoryExpenseFormDialog(
    title: String,
    subtitle: String,
    categories: List<ExpenseCategory>,
    bottleSizes: List<BottleSize>,
    factoryMaterials: List<FactoryMaterial>,
    initialExpense: Expense?,
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onAddNewMaterial: ((FactoryMaterial) -> String)? = null,
    onAddNewBottleSize: ((
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        refillPrice: Double,
        purchaseCost: Double,
        uom: String,
        categoryName: String
    ) -> String)? = null,
    onSubmit: (
        items: List<ExpenseInventoryLineItem>,
        expType: ExpenseType,
        supplier: String,
        invoiceNo: String,
        notes: String,
        date: String,
        source: PaymentSource,
        method: PaymentMethod,
        editReason: String
    ) -> Unit
) {
    val isEdit = initialExpense != null
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val allInventoryOptions = remember(bottleSizes, factoryMaterials) {
        buildUnifiedInventoryOptions(bottleSizes, factoryMaterials)
    }

    var selectedType by remember { mutableStateOf(initialExpense?.expenseType ?: ExpenseType.OPEX) }
    var expenseDate by remember { mutableStateOf(initialExpense?.date?.ifBlank { today } ?: today) }
    var invoiceNumber by remember { mutableStateOf(initialExpense?.invoiceNumber.orEmpty()) }
    var vendorName by remember { mutableStateOf(initialExpense?.payee.orEmpty()) }
    var paymentSource by remember { mutableStateOf(initialExpense?.paymentSource ?: PaymentSource.PAY_FROM_SALE) }
    var paymentMethod by remember {
        mutableStateOf(
            if (initialExpense == null || initialExpense.paymentMethod.isCash) PaymentMethod.CASH
            else PaymentMethod.BANK_TRANSFER
        )
    }
    var notes by remember { mutableStateOf(initialExpense?.notes.orEmpty()) }
    var editReasonText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var nextRowKey by remember { mutableStateOf(100L) }
    val initialDrafts = remember(initialExpense) {
        val existingLines = initialExpense?.effectiveInventoryItems.orEmpty()
        if (existingLines.isNotEmpty()) {
            existingLines.mapIndexed { idx, line ->
                InventoryLineDraft(
                    rowKey = idx + 1L,
                    itemType = line.itemType,
                    itemId = line.itemId,
                    itemName = line.itemName,
                    categoryName = line.categoryName,
                    uom = line.uom,
                    qtyText = formatCleanNumber(line.quantity),
                    rateText = formatCleanNumber(line.unitRate)
                )
            }
        } else {
            listOf(InventoryLineDraft(rowKey = 1L))
        }
    }
    var lineItems by remember { mutableStateOf(initialDrafts) }

    // Inline "+ New Inventory Item" dialog state
    var addNewItemForRowKey by remember { mutableStateOf<Long?>(null) }
    var pendingAutoSelectTarget by remember { mutableStateOf<Pair<Long, Pair<String, String>>?>(null) }

    // Auto-select newly created item as soon as StateFlow updates bottleSizes / factoryMaterials
    LaunchedEffect(allInventoryOptions, pendingAutoSelectTarget) {
        val (targetRowKey, typeAndId) = pendingAutoSelectTarget ?: return@LaunchedEffect
        val (targetType, targetId) = typeAndId
        val created = allInventoryOptions.find { it.itemType == targetType && it.itemId == targetId }
        if (created != null) {
            lineItems = lineItems.map { row ->
                if (row.rowKey == targetRowKey) {
                    row.copy(
                        itemType = created.itemType,
                        itemId = created.itemId,
                        itemName = created.name,
                        categoryName = created.categoryName,
                        uom = created.uom,
                        rateText = formatCleanNumber(created.defaultRate)
                    )
                } else row
            }
            if (vendorName.isBlank() && created.defaultSupplier.isNotBlank()) {
                vendorName = created.defaultSupplier
            }
            pendingAutoSelectTarget = null
        }
    }

    val grandTotal = remember(lineItems) { lineItems.sumOf { it.lineTotal } }
    var showEditConfirm by remember { mutableStateOf(false) }

    // Abnormal stock-underflow check & edit warnings when editing an existing Inventory Expense
    val stockUnderflowErrors = remember(isEdit, initialExpense, lineItems, factoryMaterials) {
        if (!isEdit || initialExpense == null) emptyList()
        else {
            val oldByMat = initialExpense.effectiveInventoryItems
                .filter { it.itemType == "MATERIAL" && it.itemId.isNotBlank() }
                .groupBy { it.itemId }
                .mapValues { (_, list) -> list.sumOf { it.quantity } }
            val newByMat = lineItems
                .filter { it.itemType == "MATERIAL" && it.itemId.isNotBlank() }
                .groupBy { it.itemId }
                .mapValues { (_, list) -> list.sumOf { it.qtyVal } }
            val affectedMatIds = (oldByMat.keys + newByMat.keys)
            buildList {
                affectedMatIds.forEach { matId ->
                    val oldQty = oldByMat[matId] ?: 0.0
                    val newQty = newByMat[matId] ?: 0.0
                    if (newQty < oldQty - 0.001) {
                        val mat = factoryMaterials.find { it.id == matId }
                        if (mat != null) {
                            val projected = mat.currentStock - oldQty + newQty
                            if (projected < -0.001) {
                                add("Abnormal Stock Reduction: ${mat.name} current stock is ${formatCleanNumber(mat.currentStock)} ${mat.uom}. Reducing qty (${formatCleanNumber(oldQty)} → ${formatCleanNumber(newQty)}) would push stock negative!")
                            }
                        }
                    }
                }
            }
        }
    }

    val invEditWarnings = remember(isEdit, initialExpense, grandTotal, expenseDate, paymentSource) {
        if (!isEdit || initialExpense == null) emptyList()
        else buildList {
            if (initialExpense.amount > 0.0 && grandTotal > 0.0 &&
                kotlin.math.abs(grandTotal - initialExpense.amount) >= maxOf(initialExpense.amount * 0.5, 5000.0)
            ) {
                add("Large total change: Rs. ${initialExpense.amount.toInt()} → Rs. ${grandTotal.toInt()}. Verify items & rates.")
            }
            if (expenseDate != initialExpense.date) {
                add("Date changed (${initialExpense.date} → $expenseDate). Daily/Monthly reports will adjust.")
            }
            if (paymentSource != initialExpense.paymentSource) {
                add("Payment source changed (affects daily drawer cash closing).")
            }
        }
    }

    fun buildValidLineItems(): List<ExpenseInventoryLineItem>? {
        if (stockUnderflowErrors.isNotEmpty()) {
            errorMessage = stockUnderflowErrors.first()
            return null
        }
        if (lineItems.isEmpty()) {
            errorMessage = "Add at least one inventory item."
            return null
        }
        val built = mutableListOf<ExpenseInventoryLineItem>()
        for ((index, row) in lineItems.withIndex()) {
            if (row.itemId.isBlank()) {
                errorMessage = "Select an item for Row #${index + 1}."
                return null
            }
            val qty = row.qtyVal
            val rate = row.rateVal
            if (qty <= 0.0) {
                errorMessage = "Enter Qty > 0 for ${row.itemName}."
                return null
            }
            if (rate < 0.0) {
                errorMessage = "Enter valid Rate for ${row.itemName}."
                return null
            }
            val matchedCatId = categories
                .firstOrNull { it.name.equals(row.categoryName, ignoreCase = true) }?.id.orEmpty()
            built.add(
                ExpenseInventoryLineItem(
                    itemType = row.itemType,
                    itemId = row.itemId,
                    itemName = row.itemName,
                    categoryId = matchedCatId,
                    categoryName = row.categoryName,
                    uom = row.uom,
                    quantity = if (row.itemType == "BOTTLE") qty.toInt().coerceAtLeast(1).toDouble() else qty,
                    unitRate = rate,
                    lineTotal = (if (row.itemType == "BOTTLE") qty.toInt().coerceAtLeast(1).toDouble() else qty) * rate
                )
            )
        }
        return built
    }

    if (showEditConfirm) {
        val builtItems = buildValidLineItems()
        if (builtItems != null) {
            AlertDialog(
                onDismissRequest = { showEditConfirm = false },
                containerColor = Color.White,
                icon = {
                    Icon(
                        imageVector = if (invEditWarnings.isNotEmpty()) Icons.Default.Warning else Icons.Default.EditNote,
                        contentDescription = null,
                        tint = if (invEditWarnings.isNotEmpty()) WarningOrange else WaterNavyPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = if (invEditWarnings.isNotEmpty()) "Warning — Confirm Edit?" else "Confirm Edit Inventory Expense?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (invEditWarnings.isNotEmpty()) WarningOrange else WaterNavyPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Update Voucher #${initialExpense?.voucherNumber} (${builtItems.size} item${if (builtItems.size > 1) "s" else ""} • Total Rs. ${builtItems.sumOf { it.lineTotal }.toInt()})? Stock quantities will automatically adjust.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark
                        )
                        if (invEditWarnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = WarningOrangeLight,
                                border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    invEditWarnings.forEach { w ->
                                        Text("• $w", fontSize = 9.5.sp, color = Color(0xFF9A3412), lineHeight = 13.sp)
                                    }
                                }
                            }
                        }
                        Text(
                            "کیا آپ واقعی تبدیلیاں محفوظ کرنا چاہتے ہیں؟ اسٹاک خودکار اپڈیٹ ہو جائے گا۔",
                            fontSize = 10.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showEditConfirm = false
                            onSubmit(
                                builtItems, selectedType, vendorName.trim(), invoiceNumber.trim(),
                                notes.trim(), expenseDate, paymentSource, paymentMethod, editReasonText.trim()
                            )
                        },
                        colors = waterPrimaryButtonColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_edit_expense_dialog_btn")
                    ) {
                        Text("Yes, Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditConfirm = false }) {
                        Text("Cancel", color = TextSecondaryDark, fontSize = 11.sp)
                    }
                }
            )
        } else {
            showEditConfirm = false
        }
    }

    if (addNewItemForRowKey != null) {
        val targetRow = addNewItemForRowKey!!
        UnifiedAddInventoryItemDialog(
            categories = categories,
            saveButtonLabel = "Save & Select",
            onDismiss = { addNewItemForRowKey = null },
            onAddMaterial = { mat ->
                val newId = onAddNewMaterial?.invoke(mat).orEmpty()
                if (newId.isNotBlank()) {
                    pendingAutoSelectTarget = targetRow to ("MATERIAL" to newId)
                }
                addNewItemForRowKey = null
            },
            onAddBottleSize = { name, cap, usage, refill, cost, _, uom, catName ->
                val newId = onAddNewBottleSize?.invoke(name, cap, usage, refill, cost, uom, catName).orEmpty()
                if (newId.isNotBlank()) {
                    pendingAutoSelectTarget = targetRow to ("BOTTLE" to newId)
                }
                addNewItemForRowKey = null
            }
        )
    }

    CompactExpenseDialogShell(
        title = title,
        subtitle = subtitle,
        accentColor = if (isEdit) WaterNavyPrimary else SuccessGreen,
        headerTrailing = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "Total: Rs. ${grandTotal.toInt()}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        },
        onDismiss = onDismiss,
        footerContent = {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondaryDark, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Back", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                    Text("Cancel", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    val valid = buildValidLineItems() ?: return@Button
                    if (isEdit) {
                        showEditConfirm = true
                    } else {
                        onSubmit(
                            valid, selectedType, vendorName.trim(), invoiceNumber.trim(),
                            notes.trim(), expenseDate, paymentSource, paymentMethod, ""
                        )
                    }
                },
                enabled = stockUnderflowErrors.isEmpty(),
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(if (isEdit) "save_edit_expense_btn" else "save_inventory_expense_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isEdit) "Save (Rs. ${grandTotal.toInt()})" else "Submit (Rs. ${grandTotal.toInt()})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    ) {
        if (errorMessage != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ErrorRedLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage!!,
                    color = ErrorRed,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 1. OPEX / CAPEX + Date (Compact single row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            SmartSegmentedPill(
                label = "Expense Type*",
                selected = selectedType,
                options = listOf(ExpenseType.OPEX to "OPEX", ExpenseType.CAPEX to "CAPEX"),
                onSelect = { selectedType = it; errorMessage = null },
                modifier = Modifier.weight(1f)
            )
            CompactDateChipButton(
                date = expenseDate,
                onDatePicked = { expenseDate = it }
            )
        }

        // 2. Multi-Item Inventory Rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lineItems.forEachIndexed { index, row ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, WaterAqua.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inv_expense_row_$index")
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Item Dropdown + small "+ New" button at end of field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartCompactDropdown(
                                label = "Item #${index + 1}*",
                                selectedText = row.itemName,
                                options = allInventoryOptions,
                                optionLabel = { opt -> opt.name },
                                optionSubLabel = { opt ->
                                    val catDisp = opt.categoryName.ifBlank { "Uncategorized" }
                                    "$catDisp • Rs. ${opt.defaultRate.toInt()}"
                                },
                                onOptionSelected = { opt ->
                                    lineItems = lineItems.map { item ->
                                        if (item.rowKey == row.rowKey) {
                                            item.copy(
                                                itemType = opt.itemType,
                                                itemId = opt.itemId,
                                                itemName = opt.name,
                                                categoryName = opt.categoryName,
                                                uom = opt.uom,
                                                rateText = if (item.rateText.isBlank() || item.itemId != opt.itemId) {
                                                    formatCleanNumber(opt.defaultRate)
                                                } else item.rateText
                                            )
                                        } else item
                                    }
                                    if (vendorName.isBlank() && opt.defaultSupplier.isNotBlank()) {
                                        vendorName = opt.defaultSupplier
                                    }
                                    errorMessage = null
                                },
                                placeholder = "Select Bottle or Material...",
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inv_item_dropdown_$index")
                            )

                            if (onAddNewMaterial != null || onAddNewBottleSize != null) {
                                Surface(
                                    onClick = { addNewItemForRowKey = row.rowKey },
                                    shape = RoundedCornerShape(8.dp),
                                    color = WaterSkyButton,
                                    border = BorderStroke(1.dp, WaterBlueMedium.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("btn_add_new_inv_item_$index")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add New Item", tint = WaterSkyButtonContent, modifier = Modifier.size(14.dp))
                                        Text("New", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterSkyButtonContent, maxLines = 1, softWrap = false)
                                    }
                                }
                            }

                            if (lineItems.size > 1) {
                                IconButton(
                                    onClick = { lineItems = lineItems.filterNot { it.rowKey == row.rowKey } },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("btn_remove_inv_row_$index")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Item", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Qty + Rate + Subtotal Row (All 3 equal width & height)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartCompactInput(
                                label = "Qty*",
                                value = row.qtyText,
                                onValueChange = { raw ->
                                    val filtered = if (row.itemType == "BOTTLE") raw.filter { it.isDigit() }
                                    else raw.filter { it.isDigit() || it == '.' }
                                    lineItems = lineItems.map { if (it.rowKey == row.rowKey) it.copy(qtyText = filtered) else it }
                                    errorMessage = null
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (row.itemType == "BOTTLE") KeyboardType.Number else KeyboardType.Decimal
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inv_qty_input_$index")
                            )

                            SmartCompactInput(
                                label = "Rate (Rs.)*",
                                value = row.rateText,
                                onValueChange = { raw ->
                                    val filtered = raw.filter { it.isDigit() || it == '.' }
                                    lineItems = lineItems.map { if (it.rowKey == row.rowKey) it.copy(rateText = filtered) else it }
                                    errorMessage = null
                                },
                                placeholder = "0",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inv_rate_input_$index")
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (row.lineTotal > 0.0) SuccessGreenLight else Color.White,
                                border = BorderStroke(1.dp, if (row.lineTotal > 0.0) SuccessGreen.copy(alpha = 0.35f) else BorderLight),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "Subtotal",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WaterNavyPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Rs. ${row.lineTotal.toInt()}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (row.lineTotal > 0.0) SuccessGreen else TextMuted,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Auto-picked Category Strip
                        if (row.itemId.isNotBlank()) {
                            val catLabel = row.categoryName.ifBlank { "Uncategorized (بغیر کیٹیگری)" }
                            Text(
                                text = "Category: $catLabel",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (row.categoryName.isNotBlank()) WaterBlueDeep else WarningOrange,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Small "+" Button right below item rows to add another item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    onClick = {
                        nextRowKey += 1L
                        lineItems = lineItems + InventoryLineDraft(rowKey = nextRowKey)
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = WaterSkyLight,
                    border = BorderStroke(1.dp, WaterBlueMedium.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("btn_add_another_inv_item")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Another Item", tint = WaterNavyPrimary, modifier = Modifier.size(15.dp))
                        Text(
                            text = "Add Another Item (+ مزید آئٹم)",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 3. Invoice / Bill Number + 4. Vendor (Compact 2-column row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmartCompactInput(
                label = "Invoice / Bill #",
                value = invoiceNumber,
                onValueChange = { invoiceNumber = it },
                placeholder = "e.g. INV-104",
                modifier = Modifier
                    .weight(0.45f)
                    .testTag("inv_invoice_input")
            )
            SmartCompactInput(
                label = "Vendor / Supplier",
                value = vendorName,
                onValueChange = { vendorName = it },
                placeholder = "Supplier name",
                modifier = Modifier
                    .weight(0.55f)
                    .testTag("inv_vendor_input")
            )
        }

        // 5. Payment Source + 6. Payment Mode (Compact 2-column row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            SmartSegmentedPill(
                label = "Payment Source",
                selected = paymentSource,
                options = listOf(
                    PaymentSource.PAY_FROM_SALE to "Sale",
                    PaymentSource.PAY_FROM_INVESTMENT to "Invest"
                ),
                onSelect = { paymentSource = it },
                modifier = Modifier.weight(1f)
            )
            SmartSegmentedPill(
                label = "Payment Mode",
                selected = paymentMethod,
                options = listOf(
                    PaymentMethod.CASH to "Cash",
                    PaymentMethod.BANK_TRANSFER to "Online"
                ),
                onSelect = { paymentMethod = it },
                modifier = Modifier.weight(1f)
            )
        }

        // 7. Note (Compact single-line)
        SmartCompactInput(
            label = "Note",
            value = notes,
            onValueChange = { notes = it },
            placeholder = "Optional purchase note...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inv_note_input")
        )

        if (isEdit) {
            SmartCompactInput(
                label = "Edit Reason / Note (تبدیلی کی وجہ)",
                value = editReasonText,
                onValueChange = { editReasonText = it },
                placeholder = "Optional reason for edit...",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_inv_expense_reason_input")
            )
            if (stockUnderflowErrors.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ErrorRedLight,
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        stockUnderflowErrors.forEach { err ->
                            Text("⛔ $err", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ErrorRed, lineHeight = 12.sp)
                        }
                    }
                }
            }
            if (invEditWarnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WarningOrangeLight,
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        invEditWarnings.forEach { warn ->
                            Text("⚠ $warn", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412), lineHeight = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. UNIFIED COMPACT "ADD INVENTORY ITEM" DIALOG (Matches Expense Form UI)
//    Shared by Stock & Cost main tab AND Inventory Expense "+ New" button.
//    - Strictly shows ONLY Inventory categories from Expenses.
//    - Selecting a Bottle category auto-routes to Bottle Size (shown in Bottle tab & Dashboard).
//    - Fits completely on front screen without scrolling.
// =========================================================================

@Composable
internal fun UnifiedAddInventoryItemDialog(
    categories: List<ExpenseCategory>,
    saveButtonLabel: String = "Save Item",
    onDismiss: () -> Unit,
    onAddMaterial: (FactoryMaterial) -> Unit,
    onAddBottleSize: (
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        refillPrice: Double,
        purchaseCost: Double,
        securityDeposit: Double,
        uom: String,
        categoryName: String
    ) -> Unit
) {
    // Strictly Inventory categories only
    val inventoryCategories = remember(categories) {
        categories
            .filter { it.isInventoryCategory }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    var selectedCategory by remember(inventoryCategories) {
        mutableStateOf(inventoryCategories.firstOrNull().orEmpty())
    }
    var isBottle by remember(inventoryCategories) {
        mutableStateOf(isBottleInventoryCategory(inventoryCategories.firstOrNull().orEmpty()))
    }
    var itemName by remember { mutableStateOf("") }
    var uom by remember(isBottle) { mutableStateOf(if (isBottle) "Bottles" else "Piece") }
    var purchaseRateText by remember { mutableStateOf("") }
    var costType by remember { mutableStateOf(ExpenseType.OPEX) }
    var supplierText by remember { mutableStateOf("") }
    var openingStockText by remember { mutableStateOf("0") }
    var minAlertText by remember { mutableStateOf("5") }

    // Bottle-specific compact fields
    var capacityText by remember { mutableStateOf("") }
    var refillPriceText by remember { mutableStateOf("") }
    var depositText by remember { mutableStateOf("") }
    var usageType by remember { mutableStateOf(BottleUsageType.MULTIPLE_TIME) }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    CompactExpenseDialogShell(
        title = if (isBottle) "Add Bottle Size" else "Add Inventory Item",
        subtitle = if (isBottle) "نیا بوتل سائز — Bottle Stock & Dashboard پر شو ہوگا" else "نیا اسٹاک آئٹم شامل کریں",
        accentColor = WaterNavyPrimary,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                Text("Cancel", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    val cleanName = itemName.trim()
                    val rateVal = purchaseRateText.toDoubleOrNull() ?: 0.0
                    if (cleanName.isEmpty()) {
                        errorMsg = "Item Name is required."
                        return@Button
                    }
                    if (isBottle) {
                        val cap = capacityText.toDoubleOrNull() ?: 0.0
                        if (cap <= 0.0) {
                            errorMsg = "Enter valid Capacity (Litres)."
                            return@Button
                        }
                        val refill = refillPriceText.toDoubleOrNull() ?: 0.0
                        val dep = depositText.toDoubleOrNull() ?: 0.0
                        onAddBottleSize(
                            cleanName,
                            cap,
                            usageType,
                            refill,
                            rateVal,
                            dep,
                            uom.trim().ifBlank { "Bottles" },
                            selectedCategory.trim()
                        )
                    } else {
                        val opening = openingStockText.toDoubleOrNull() ?: 0.0
                        val minAlert = minAlertText.toDoubleOrNull() ?: 5.0
                        val usage = if (selectedCategory.contains("packing", true) || selectedCategory.contains("bottle", true)) {
                            MaterialUsageType.PRODUCTION_CONSUMABLE
                        } else {
                            MaterialUsageType.MAINTENANCE_MATERIAL
                        }
                        onAddMaterial(
                            FactoryMaterial(
                                name = cleanName,
                                itemCategory = selectedCategory.trim(),
                                costType = costType,
                                uom = uom.trim().ifBlank { "Piece" },
                                openingStock = opening,
                                currentStock = opening,
                                minStockLevel = minAlert,
                                purchaseRate = rateVal,
                                supplierName = supplierText.trim(),
                                usageType = usage,
                                defaultIssueCategory = MaterialIssueCategory.PRODUCTION
                            )
                        )
                    }
                },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("btn_save_quick_inv_item")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(saveButtonLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    ) {
        if (errorMsg != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ErrorRedLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMsg!!,
                    color = ErrorRed,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Row 1: Category (Inventory Categories Only) + Smart Auto-Synced Item Type
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            SmartCompactDropdown(
                label = "Category (Inventory)*",
                selectedText = selectedCategory,
                options = inventoryCategories,
                optionLabel = { it },
                onOptionSelected = { cat ->
                    selectedCategory = cat
                    val bottleCat = isBottleInventoryCategory(cat)
                    isBottle = bottleCat
                    uom = if (bottleCat) "Bottles" else "Piece"
                    errorMsg = null
                },
                placeholder = "Select Category...",
                modifier = Modifier
                    .weight(1.25f)
                    .testTag("add_item_category_dropdown")
            )

            SmartSegmentedPill(
                label = "Item Type",
                selected = isBottle,
                options = listOf(true to "Bottle", false to "Material"),
                onSelect = { bottle ->
                    isBottle = bottle
                    uom = if (bottle) "Bottles" else "Piece"
                    if (bottle) {
                        inventoryCategories.firstOrNull { isBottleInventoryCategory(it) }?.let {
                            selectedCategory = it
                        }
                    } else if (isBottleInventoryCategory(selectedCategory)) {
                        inventoryCategories.firstOrNull { !isBottleInventoryCategory(it) }?.let {
                            selectedCategory = it
                        }
                    }
                    errorMsg = null
                },
                modifier = Modifier
                    .weight(0.95f)
                    .testTag("add_item_type_toggle")
            )
        }

        if (isBottle) {
            // Bottle Size Smart Compact Grid (Fits Front Screen)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SmartCompactInput(
                    label = "Bottle Name*",
                    value = itemName,
                    onValueChange = { itemName = it; errorMsg = null },
                    placeholder = "e.g. 19L / 1.5L",
                    modifier = Modifier
                        .weight(1.35f)
                        .testTag("input_unified_item_name")
                )
                SmartCompactInput(
                    label = "Capacity (L)*",
                    value = capacityText,
                    onValueChange = { capacityText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
                    placeholder = "19",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(0.65f)
                        .testTag("input_unified_bottle_capacity")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                SmartSegmentedPill(
                    label = "Usage Type*",
                    selected = usageType,
                    options = listOf(BottleUsageType.MULTIPLE_TIME to "Multi-Use", BottleUsageType.ONE_TIME to "Single-Use"),
                    onSelect = { usageType = it },
                    modifier = Modifier.weight(1.2f)
                )
                SmartCompactInput(
                    label = "UOM*",
                    value = uom,
                    onValueChange = { uom = it },
                    placeholder = "Bottles",
                    modifier = Modifier.weight(0.8f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmartCompactInput(
                    label = "Refill Price",
                    value = refillPriceText,
                    onValueChange = { refillPriceText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                SmartCompactInput(
                    label = "Purchase Rate",
                    value = purchaseRateText,
                    onValueChange = { purchaseRateText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                SmartCompactInput(
                    label = "Deposit",
                    value = depositText,
                    onValueChange = { depositText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Material Smart Compact Grid (Fits Front Screen)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                SmartCompactInput(
                    label = "Item Name*",
                    value = itemName,
                    onValueChange = { itemName = it; errorMsg = null },
                    placeholder = "e.g. Bottle Caps 28mm",
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("input_unified_item_name")
                )
                SmartSegmentedPill(
                    label = "Cost Type",
                    selected = costType,
                    options = listOf(ExpenseType.OPEX to "OPEX", ExpenseType.CAPEX to "CAPEX"),
                    onSelect = { costType = it },
                    modifier = Modifier.weight(0.85f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmartCompactDropdown(
                    label = "UOM*",
                    selectedText = uom,
                    options = StockMasterOptions.MATERIAL_UOMS,
                    optionLabel = { it },
                    onOptionSelected = { uom = it },
                    modifier = Modifier.weight(1f)
                )
                SmartCompactInput(
                    label = "Rate (Rs.)",
                    value = purchaseRateText,
                    onValueChange = { purchaseRateText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                SmartCompactInput(
                    label = "Min Alert",
                    value = minAlertText,
                    onValueChange = { minAlertText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "5",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SmartCompactInput(
                    label = "Opening Stock",
                    value = openingStockText,
                    onValueChange = { openingStockText = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.42f)
                )
                SmartCompactInput(
                    label = "Default Supplier",
                    value = supplierText,
                    onValueChange = { supplierText = it },
                    placeholder = "Optional supplier",
                    modifier = Modifier.weight(0.58f)
                )
            }
        }
    }
}

// =========================================================================
// 7. FULL EDIT EXPENSE DIALOG (Supports Editing ALL Fields of BOTH Types)
// =========================================================================

@Composable
fun EditExpenseDialog(
    expense: Expense,
    categories: List<ExpenseCategory>,
    isAdmin: Boolean = true,
    factoryMaterials: List<FactoryMaterial> = emptyList(),
    bottleSizes: List<BottleSize> = emptyList(),
    plantWorkers: List<PlantWorker> = emptyList(),
    onGetWorkerSalarySummary: ((workerId: String, month: String) -> WorkerSalarySummary)? = null,
    onAddCategoryWithType: ((name: String, urdu: String, type: ExpenseType, isInventory: Boolean) -> Unit)? = null,
    onAddNewMaterial: ((FactoryMaterial) -> String)? = null,
    onAddNewBottleSize: ((
        name: String,
        capacityLitres: Double,
        usageType: BottleUsageType,
        refillPrice: Double,
        purchaseCost: Double,
        uom: String,
        categoryName: String
    ) -> String)? = null,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    if (expense.hasInventoryItems) {
        InventoryExpenseFormDialog(
            title = "Edit Inventory #${expense.voucherNumber}",
            subtitle = "تمام فیلڈز اور آئٹمز ایڈٹ کریں",
            categories = categories,
            bottleSizes = bottleSizes,
            factoryMaterials = factoryMaterials,
            initialExpense = expense,
            onBack = null,
            onDismiss = onDismiss,
            onAddNewMaterial = onAddNewMaterial,
            onAddNewBottleSize = onAddNewBottleSize,
            onSubmit = { items, expType, supplier, invoiceNo, notes, date, source, method, editReason ->
                val updated = expense.copy(
                    date = date.ifBlank { expense.date },
                    expenseType = expType,
                    payee = supplier,
                    invoiceNumber = invoiceNo,
                    notes = notes,
                    paymentSource = source,
                    paymentMethod = method,
                    isInventoryExpense = true,
                    inventoryItems = items,
                    amount = items.sumOf { it.lineTotal },
                    lastEditNote = editReason
                )
                onSave(updated)
            }
        )
    } else {
        NonInventoryExpenseFormDialog(
            categories = categories,
            plantWorkers = plantWorkers,
            onGetWorkerSalarySummary = onGetWorkerSalarySummary,
            initialExpense = expense,
            onBack = null,
            onDismiss = onDismiss,
            onSubmit = { catId, catName, desc, amt, method, vendor, notes, expType, date, source, staffId, staffName, editReason ->
                val updated = expense.copy(
                    date = date.ifBlank { expense.date },
                    categoryId = catId,
                    categoryName = catName,
                    description = desc,
                    amount = amt,
                    paymentMethod = method,
                    paymentSource = source,
                    payee = vendor,
                    notes = notes,
                    expenseType = expType,
                    staffMemberId = staffId,
                    staffMemberName = staffName,
                    lastEditNote = editReason
                )
                onSave(updated)
            }
        )
    }
}

// =========================================================================
// 8. MANAGE EXPENSE CATEGORIES DIALOG (Inventory & Non-Inventory + Safe Delete)
// =========================================================================

@Composable
fun ManageExpenseCategoriesDialog(
    categories: List<ExpenseCategory>,
    onAddCategory: (name: String, urdu: String, type: ExpenseType, isInventory: Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCatName by remember { mutableStateOf("") }
    var newCatUrdu by remember { mutableStateOf("") }
    var newCatType by remember { mutableStateOf(ExpenseType.OPEX) }
    var newCatIsInventory by remember { mutableStateOf(false) }
    var categoryPendingDelete by remember { mutableStateOf<ExpenseCategory?>(null) }

    if (categoryPendingDelete != null) {
        val target = categoryPendingDelete!!
        AlertDialog(
            onDismissRequest = { categoryPendingDelete = null },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(28.dp)) },
            title = { Text("Delete Category '${target.name}'?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorRed) },
            text = {
                Text(
                    "Any stock items currently in '${target.name}' will NOT be deleted — they will show as Uncategorized so you can reassign a new category later.\n\nاس کیٹیگری کے اسٹاک آئٹمز محفوظ رہیں گے اور بغیر کیٹیگری کے نظر آئیں گے۔",
                    fontSize = 10.5.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(target.id)
                        categoryPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete Category", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryPendingDelete = null }) {
                    Text("Cancel", fontSize = 10.5.sp, color = TextSecondaryDark)
                }
            }
        )
    }

    CompactExpenseDialogShell(
        title = "Expense & Inventory Categories",
        subtitle = "کیٹیگریز کا انتظام (Inventory & Non-Inventory)",
        accentColor = WaterNavyPrimary,
        onDismiss = onDismiss,
        footerContent = {
            Button(
                onClick = onDismiss,
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Done", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, BorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Add New Category:", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyDark)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartSegmentedPill(
                        selected = newCatIsInventory,
                        options = listOf(false to "Non-Inv", true to "Inventory"),
                        onSelect = { newCatIsInventory = it },
                        modifier = Modifier.weight(1.1f)
                    )
                    SmartSegmentedPill(
                        selected = newCatType,
                        options = listOf(ExpenseType.OPEX to "OPEX", ExpenseType.CAPEX to "CAPEX"),
                        onSelect = { newCatType = it },
                        modifier = Modifier.weight(0.9f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartCompactInput(
                        label = "Category Name*",
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        placeholder = "e.g. Shrink Film",
                        modifier = Modifier.weight(1f)
                    )
                    SmartCompactInput(
                        label = "Urdu (Optional)",
                        value = newCatUrdu,
                        onValueChange = { newCatUrdu = it },
                        placeholder = "اردو",
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            onAddCategory(newCatName.trim(), newCatUrdu.trim(), newCatType, newCatIsInventory)
                            newCatName = ""
                            newCatUrdu = ""
                        }
                    },
                    colors = waterPrimaryButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("btn_save_manage_category")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Category", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("All Categories (${categories.size}):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)

        categories.forEach { cat ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = cat.name,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (cat.isInventoryCategory) SuccessGreenLight else WaterSkyLight
                            ) {
                                Text(
                                    text = if (cat.isInventoryCategory) "Inventory" else "Non-Inv",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (cat.isInventoryCategory) SuccessGreen else WaterBlueDeep,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (cat.type == ExpenseType.CAPEX) WarningOrangeLight else Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = cat.type.name,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cat.type == ExpenseType.CAPEX) WarningOrange else TextSecondaryDark,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (cat.urduName.isNotBlank()) {
                            Text(cat.urduName, fontSize = 8.5.sp, color = TextSecondaryDark)
                        }
                    }

                    IconButton(
                        onClick = { categoryPendingDelete = cat },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
