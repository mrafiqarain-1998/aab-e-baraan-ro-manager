package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExpenseCategory
import com.example.model.ExpenseType
import com.example.model.PaymentMethod
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlantExpenseCard(
    categories: List<ExpenseCategory> = emptyList(),
    onAddCategory: ((name: String, urdu: String) -> Unit)? = null,
    onDeleteCategory: ((String) -> Unit)? = null,
    onRecordExpense: (
        categoryId: String,
        categoryName: String,
        description: String,
        amount: Double,
        method: PaymentMethod,
        payee: String,
        notes: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryId by remember(categories) {
        mutableStateOf(categories.firstOrNull()?.id ?: "")
    }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }

    val activeCategory = categories.find { it.id == selectedCategoryId }
        ?: categories.firstOrNull()

    var amountText by remember { mutableStateOf("") }
    var payeeText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notesText by remember { mutableStateOf("") }
    var showSuccessBanner by remember { mutableStateOf(false) }

    val quickAmounts = listOf(500, 1000, 2000, 5000)
    val todayFormatted = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }

    fun getCategoryColor(catId: String): Color {
        val colors = listOf(
            Color(0xFF0284C7),
            Color(0xFFD97706),
            Color(0xFF10B981),
            Color(0xFF8B5CF6),
            Color(0xFFE11D48),
            Color(0xFF0D9488),
            Color(0xFF64748B)
        )
        val idx = catId.hashCode().let { kotlin.math.abs(it) } % colors.size
        return colors[idx]
    }

    val themeColor = activeCategory?.let { getCategoryColor(it.id) } ?: WaterNavyPrimary

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(themeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Daily Plant Expense (روزمرہ خرچ)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = WaterNavyDark
                        )
                        Text(
                            text = todayFormatted,
                            fontSize = 9.5.sp,
                            color = TextSecondaryDark
                        )
                    }
                }

                // Quick Manage Categories Button
                FilledTonalButton(
                    onClick = { showManageCategoriesDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WaterSkyLight,
                        contentColor = WaterNavyPrimary
                    )
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("شعبہ جات", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = WaterSkyLight, thickness = 0.8.dp)

            // Category Chips Row (Compact / تھورا چھوٹا ٹیب)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "خرچ کا شعبہ (Category):",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WaterNavyPrimary
                )

                TextButton(
                    onClick = { showAddCategoryDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp), tint = WaterNavyPrimary)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("نیا زمرہ (+)", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyPrimary)
                }
            }

            if (categories.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAddCategoryDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "کوئی زمرہ موجود نہیں — نیا زمرہ شامل کرنے کیلئے یہاں ٹیپ کریں (+)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = WaterNavyPrimary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = activeCategory?.id == cat.id
                        val color = getCategoryColor(cat.id)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) color.copy(alpha = 0.15f) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) color else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.clickable {
                                selectedCategoryId = cat.id
                                if (descText.isBlank()) descText = cat.name
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(color, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Column {
                                    Text(
                                        text = cat.name,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) color else WaterNavyDark
                                    )
                                    if (cat.urduName.isNotBlank()) {
                                        Text(
                                            text = cat.urduName,
                                            fontSize = 9.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Button Chip at the end
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterSkyLight.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WaterSkyLight),
                        modifier = Modifier.clickable { showAddCategoryDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = WaterNavyPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("نیا زمرہ", fontSize = 9.5.sp, color = WaterNavyPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Amount Input with Quick Preset Chips
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Expense Amount (رقم درج کریں)*", fontSize = 10.5.sp) },
                prefix = { Text("Rs. ", fontWeight = FontWeight.Bold, color = WaterNavyPrimary, fontSize = 11.5.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("daily_expense_amount_input"),
                shape = RoundedCornerShape(10.dp)
            )

            // Quick Amount Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickAmounts.forEach { amt ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (amountText == amt.toString()) themeColor else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { amountText = amt.toString() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                            Text(
                                text = "Rs.$amt",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (amountText == amt.toString()) Color.White else WaterNavyDark
                            )
                        }
                    }
                }
            }

            // Payee & Description (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = payeeText,
                    onValueChange = { payeeText = it },
                    label = { Text("بنام (Payee)", fontSize = 10.sp) },
                    placeholder = { Text("مثلاً: پٹرول پمپ، واپڈا", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("تفصیل (Notes)", fontSize = 10.sp) },
                    placeholder = { Text("تفصیل درج کریں", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Payment Method Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(PaymentMethod.CASH, PaymentMethod.JAZZCASH, PaymentMethod.EASYPAISA, PaymentMethod.BANK_TRANSFER).forEach { method ->
                    val isSelected = selectedMethod == method
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) WaterNavyPrimary else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMethod = method }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                            Text(
                                text = method.label,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else WaterNavyDark
                            )
                        }
                    }
                }
            }

            // Submit Button
            val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
            Button(
                onClick = {
                    if (parsedAmount > 0) {
                        val cId = activeCategory?.id ?: "cat_general"
                        val cName = activeCategory?.name ?: "General Expense"
                        onRecordExpense(
                            cId,
                            cName,
                            descText.ifEmpty { cName },
                            parsedAmount,
                            selectedMethod,
                            payeeText.ifEmpty { "Cash Payee" },
                            notesText
                        )
                        amountText = ""
                        payeeText = ""
                        descText = ""
                        showSuccessBanner = true
                    }
                },
                shape = RoundedCornerShape(10.dp),
                enabled = parsedAmount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("record_daily_expense_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Record Expense (Rs. ${if (parsedAmount > 0) parsedAmount.toInt() else 0}) • خرچ درج کریں",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = Color.White
                )
            }

            if (showSuccessBanner) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreenLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Expense recorded successfully and saved to ledger!",
                            color = SuccessGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Add New Category
    if (showAddCategoryDialog) {
        var catName by remember { mutableStateOf("") }
        var catUrdu by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextPrimaryDark,
            title = {
                Text(
                    text = "نیا خرچ کا شعبہ بنائیں (Add Category)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = WaterNavyPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name (English)*") },
                        placeholder = { Text("e.g. Fuel, WAPDA Bill, Generator Diesel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = catUrdu,
                        onValueChange = { catUrdu = it },
                        label = { Text("شعبہ کا نام (اردو میں)") },
                        placeholder = { Text("مثلاً: گاڑی کا پیٹرول، بجلی کا بل") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catName.isNotBlank()) {
                            onAddCategory?.invoke(catName.trim(), catUrdu.trim())
                            showAddCategoryDialog = false
                        }
                    },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text("شامل کریں (Save)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("منسوخ", color = TextSecondaryDark) }
            }
        )
    }

    // Dialog 2: Manage Categories (List & Delete)
    if (showManageCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showManageCategoriesDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyPrimary,
            textContentColor = TextPrimaryDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = WaterNavyPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "خرچ کے شعبہ جات (Manage Categories)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = WaterNavyPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "آپ کسی بھی شعبے کو ڈیلیٹ کر سکتے ہیں یا نیا شامل کر سکتے ہیں:",
                        fontSize = 10.sp,
                        color = TextSecondaryDark
                    )

                    Button(
                        onClick = {
                            showManageCategoriesDialog = false
                            showAddCategoryDialog = true
                        },
                        colors = waterPrimaryButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نیا شعبہ شامل کریں (Add New Category)", fontSize = 10.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (categories.isEmpty()) {
                        Text(
                            text = "ابھی کوئی شعبہ موجود نہیں۔ اوپر سے نیا شعبہ شامل کریں۔",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            categories.forEach { cat ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cat.name, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                                            if (cat.urduName.isNotBlank()) {
                                                Text(cat.urduName, fontSize = 9.5.sp, color = TextSecondaryDark)
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDeleteCategory?.invoke(cat.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showManageCategoriesDialog = false },
                    colors = waterPrimaryButtonColors()
                ) {
                    Text("بند کریں (Done)")
                }
            }
        )
    }
}
