package com.example.ui.screens.stockcost

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.CompactDropdownField
import com.example.ui.components.MobileFormDialog
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WORKER_DESIGNATIONS = listOf(
    "Plant Operator",
    "Delivery Driver",
    "Delivery Rider",
    "Helper / Loader",
    "Supervisor",
    "Cashier / Clerk",
    "Security Guard",
    "Worker"
)

@Composable
fun StaffManagementSubTab(
    viewModel: WaterPlantViewModel,
    isAdmin: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workers by viewModel.plantWorkers.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    val canManageWorkers = isAdmin ||
        viewModel.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.ADD) ||
        viewModel.hasPermission(AppModule.EXPENSES, PermissionAction.ADD)

    var monthOffset by remember { mutableStateOf(0) }
    val selectedMonthKey = remember(monthOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthOffset)
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }
    val selectedMonthLabel = remember(monthOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthOffset)
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    var showAddWorkerDialog by remember { mutableStateOf(false) }
    var workerToEdit by remember { mutableStateOf<PlantWorker?>(null) }
    var workerToDelete by remember { mutableStateOf<PlantWorker?>(null) }

    val summariesByWorkerId = remember(workers, expenses, selectedMonthKey) {
        workers.associate { worker ->
            worker.id to viewModel.getWorkerMonthlySalarySummary(worker.id, selectedMonthKey)
        }
    }

    val activeWorkers = remember(workers) { workers.filter { it.active } }
    val totalMonthlyPayroll = remember(activeWorkers) { activeWorkers.sumOf { it.monthlySalary } }
    val totalPaidThisMonth = remember(activeWorkers, summariesByWorkerId) {
        activeWorkers.sumOf { summariesByWorkerId[it.id]?.paidOrAdvanceThisMonth ?: 0.0 }
    }
    val totalRemainingThisMonth = (totalMonthlyPayroll - totalPaidThisMonth).coerceAtLeast(0.0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("staff_management_sub_tab"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Payroll Summary Banner + Month Switcher
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WaterNavyPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Staff Management & Payroll",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "عملے کی تفصیلات، شناختی کارڈ، تنخواہ اور ایڈوانس حساب",
                                color = WaterSkyLight,
                                fontSize = 9.5.sp
                            )
                        }
                        if (canManageWorkers) {
                            Button(
                                onClick = { showAddWorkerDialog = true },
                                colors = waterPrimaryButtonColors(),
                                shape = RoundedCornerShape(9.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("btn_add_plant_worker")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Staff", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    // Month selector strip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WaterNavyDark.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { monthOffset -= 1 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Month",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = selectedMonthLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = { if (monthOffset < 0) monthOffset += 1 },
                                enabled = monthOffset < 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Month",
                                    tint = if (monthOffset < 0) Color.White else Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 3 KPI boxes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StaffKpiTile(
                            label = "Monthly Salary",
                            value = "Rs. ${totalMonthlyPayroll.toInt()}",
                            sub = "${activeWorkers.size} Active",
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        StaffKpiTile(
                            label = "Paid / Advance",
                            value = "Rs. ${totalPaidThisMonth.toInt()}",
                            sub = selectedMonthKey,
                            valueColor = WaterSkyButton,
                            modifier = Modifier.weight(1f)
                        )
                        StaffKpiTile(
                            label = "Remaining",
                            value = "Rs. ${totalRemainingThisMonth.toInt()}",
                            sub = "Payable",
                            valueColor = Color(0xFFFDE68A),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Info Banner connecting Staff Management with Expenses
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WaterSkyLight,
                border = BorderStroke(1.dp, WaterSkyButton.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = WaterNavyPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tip: Expenses tab mein 'Staff Salary' category select kar k worker chunein — advance ya salary automatically remaining balance se minus ho jayegi.",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WaterNavyDark,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // 3. Worker List
        if (workers.isEmpty()) {
            item {
                EmptyStateCard(
                    message = "No staff workers added yet.",
                    urduMessage = "ورکرز کا نام، شناختی کارڈ اور ماہانہ تنخواہ شامل کرنے کے لیے Add Staff دبائیں"
                )
            }
        } else {
            items(workers, key = { it.id }) { worker ->
                val summary = summariesByWorkerId[worker.id] ?: WorkerSalarySummary(
                    workerId = worker.id,
                    workerName = worker.name,
                    month = selectedMonthKey,
                    monthlySalary = worker.monthlySalary,
                    paidOrAdvanceThisMonth = 0.0,
                    remainingSalary = worker.monthlySalary
                )
                PlantWorkerCard(
                    worker = worker,
                    summary = summary,
                    canEditOrDelete = isAdmin,
                    onEditClick = { workerToEdit = worker },
                    onDeleteClick = { workerToDelete = worker }
                )
            }
        }
    }

    // Add / Edit Worker Dialog
    if (showAddWorkerDialog || workerToEdit != null) {
        val editing = workerToEdit
        StaffWorkerFormDialog(
            existing = editing,
            onDismiss = {
                showAddWorkerDialog = false
                workerToEdit = null
            },
            onSave = { savedWorker ->
                if (editing == null) {
                    viewModel.addPlantWorker(savedWorker) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) showAddWorkerDialog = false
                    }
                } else {
                    viewModel.updatePlantWorker(savedWorker) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (ok) workerToEdit = null
                    }
                }
            }
        )
    }

    // Delete Worker Dialog
    workerToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { workerToDelete = null },
            containerColor = Color.White,
            titleContentColor = ErrorRed,
            textContentColor = TextPrimaryDark,
            title = {
                Text("Remove ${target.name}?", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = ErrorRed)
            },
            text = {
                Text(
                    "Delete '${target.name}' from Staff Management? Past salary vouchers in Expenses will remain safe.",
                    fontSize = 11.sp,
                    color = TextPrimaryDark
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlantWorker(target.id) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) workerToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Remove", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { workerToDelete = null }) {
                    Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
private fun StaffKpiTile(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WaterNavyDark.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = WaterSkyLight, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 8.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

@Composable
private fun PlantWorkerCard(
    worker: PlantWorker,
    summary: WorkerSalarySummary,
    canEditOrDelete: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isFullyPaid = worker.monthlySalary > 0.0 && summary.remainingSalary <= 0.0
    val hasAdvance = summary.paidOrAdvanceThisMonth > 0.0

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (worker.active) Color.White else Color(0xFFF8FAFC)),
        border = BorderStroke(0.6.dp, if (worker.active) BorderLight else ErrorRed.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("worker_card_${worker.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (worker.active) WaterSkyLight else ErrorRedLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = if (worker.active) WaterNavyPrimary else ErrorRed,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = worker.name,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WaterNavyDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = WaterSkyLight
                            ) {
                                Text(
                                    text = worker.designation.ifBlank { "Worker" },
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WaterNavyPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (!worker.active) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = ErrorRedLight
                                ) {
                                    Text(
                                        text = "Inactive",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ErrorRed,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val metaParts = buildList {
                            if (worker.cnic.isNotBlank()) add("NIC: ${worker.cnic}")
                            if (worker.phone.isNotBlank()) add("Ph: ${worker.phone}")
                        }
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" • "),
                                fontSize = 9.5.sp,
                                color = TextSecondaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (canEditOrDelete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp).testTag("edit_worker_${worker.id}")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Staff", tint = WaterBlueDeep, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp).testTag("delete_worker_${worker.id}")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Staff", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Monthly Salary / Advance / Remaining strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(0.5.dp, BorderLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("Monthly Salary", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryDark)
                        Text("Rs. ${worker.monthlySalary.toInt()}", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = WaterNavyDark)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasAdvance) WarningOrangeLight else Color(0xFFF8FAFC),
                    border = BorderStroke(0.5.dp, if (hasAdvance) WarningOrange.copy(alpha = 0.4f) else BorderLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("Advance / Paid", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = if (hasAdvance) WarningOrange else TextSecondaryDark)
                        Text(
                            text = "Rs. ${summary.paidOrAdvanceThisMonth.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hasAdvance) WarningOrange else TextSecondaryDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFullyPaid) SuccessGreenLight else WaterSkyLight,
                    border = BorderStroke(0.6.dp, if (isFullyPaid) SuccessGreen.copy(alpha = 0.4f) else WaterSkyButton.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = if (isFullyPaid) "Settled ✓" else "Remaining",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFullyPaid) SuccessGreen else WaterNavyPrimary
                        )
                        Text(
                            text = "Rs. ${summary.remainingSalary.toInt()}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isFullyPaid) SuccessGreen else WaterNavyPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffWorkerFormDialog(
    existing: PlantWorker?,
    onDismiss: () -> Unit,
    onSave: (PlantWorker) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cnic by remember { mutableStateOf(existing?.cnic ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var designation by remember { mutableStateOf(existing?.designation?.ifBlank { WORKER_DESIGNATIONS.first() } ?: WORKER_DESIGNATIONS.first()) }
    var monthlySalary by remember { mutableStateOf(existing?.monthlySalary?.takeIf { it > 0.0 }?.toInt()?.toString() ?: "") }
    var joiningDate by remember { mutableStateOf(existing?.joiningDate?.ifBlank { dateFormat.format(Date()) } ?: dateFormat.format(Date())) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val hasUnsaved = name != (existing?.name ?: "") ||
        cnic != (existing?.cnic ?: "") ||
        phone != (existing?.phone ?: "") ||
        monthlySalary != (existing?.monthlySalary?.takeIf { it > 0.0 }?.toInt()?.toString() ?: "")

    MobileFormDialog(
        title = if (existing == null) "Add Staff Member" else "Edit Staff Member",
        subtitle = "ورکر کا نام، شناختی کارڈ نمبر اور ماہانہ تنخواہ",
        hasUnsavedChanges = hasUnsaved,
        onDismiss = onDismiss,
        footerContent = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            }
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val sal = monthlySalary.toDoubleOrNull() ?: 0.0
                    when {
                        cleanName.isEmpty() -> errorMsg = "Staff Name is required."
                        sal <= 0.0 -> errorMsg = "Please enter valid Monthly Salary."
                        else -> onSave(
                            (existing ?: PlantWorker()).copy(
                                name = cleanName,
                                cnic = cnic.trim(),
                                phone = phone.trim(),
                                designation = designation,
                                monthlySalary = sal,
                                joiningDate = joiningDate.trim().ifBlank { dateFormat.format(Date()) },
                                active = active,
                                notes = notes.trim()
                            )
                        )
                    }
                },
                colors = waterPrimaryButtonColors(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_plant_worker")
            ) {
                Text(if (existing == null) "Save Staff" else "Update Staff", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; errorMsg = null },
            label = { Text("Staff Full Name (نام)*", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("worker_name_input")
        )

        OutlinedTextField(
            value = cnic,
            onValueChange = { cnic = it },
            label = { Text("CNIC / NIC Number (شناختی کارڈ)", fontSize = 10.5.sp) },
            placeholder = { Text("e.g. 42101-1234567-1", fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("worker_cnic_input")
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Mobile Phone", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("worker_phone_input")
        )

        CompactDropdownField(
            label = "Designation (عہدہ)",
            selectedText = designation,
            options = WORKER_DESIGNATIONS,
            optionLabel = { it },
            onOptionSelected = { designation = it }
        )

        OutlinedTextField(
            value = monthlySalary,
            onValueChange = { monthlySalary = it.filter { ch -> ch.isDigit() || ch == '.' }; errorMsg = null },
            label = { Text("Monthly Salary (Rs. ماہانہ تنخواہ)*", fontSize = 10.5.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("worker_salary_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = joiningDate,
                onValueChange = { joiningDate = it },
                label = { Text("Joining Date", fontSize = 10.5.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = WaterSkyLight,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        val cal = Calendar.getInstance()
                        try { dateFormat.parse(joiningDate)?.let { cal.time = it } } catch (_: Exception) {}
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply { set(y, m, d) }
                                joiningDate = dateFormat.format(picked.time)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Joining Date", tint = WaterNavyPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }

        if (existing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Active Staff Member", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WaterNavyDark)
                Switch(
                    checked = active,
                    onCheckedChange = { active = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SuccessGreen
                    )
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (Optional)", fontSize = 10.5.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMsg != null) {
            Text(errorMsg!!, color = ErrorRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}
