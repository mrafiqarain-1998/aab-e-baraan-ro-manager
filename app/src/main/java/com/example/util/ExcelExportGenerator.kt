package com.example.util

import com.example.model.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure-Kotlin OOXML (.xlsx) Multi-Sheet Excel Workbook generator.
 * Produces a genuine .xlsx workbook with separate tabs for every plant module,
 * styled bold headers, auto column widths, proper numeric cells, and full UTF-8 support
 * (opens cleanly in Microsoft Excel, Google Sheets, WPS Office, and LibreOffice).
 */
object ExcelExportGenerator {

    private data class SheetData(
        val name: String,
        val headers: List<String>,
        val rows: List<List<Any?>>
    )

    fun generateCompleteWorkbook(
        outputFile: File,
        exportedBy: String,
        customers: List<Customer>,
        customerRates: List<CustomerRate>,
        customerAllocations: List<CustomerBottleAllocation>,
        deliveries: List<Delivery>,
        payments: List<Payment>,
        invoices: List<Invoice>,
        expenses: List<Expense>,
        bottleSizes: List<BottleSize>,
        inventory: List<BottleInventory>,
        bottleRefills: List<BottleRefillRecord>,
        bottlePurchases: List<BottlePurchaseRecord>,
        bottleIssueSales: List<BottleIssueSaleRecord>,
        factoryMaterials: List<FactoryMaterial>,
        materialTransactions: List<MaterialTransaction>,
        roPlantItems: List<RoPlantItem>,
        roTransactions: List<RoItemTransaction>,
        cashClosings: List<CashClosing>,
        securityDeposits: List<SecurityDepositTransaction>,
        userAccounts: List<UserAccount>
    ): File {
        val nowFormatted = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())

        val totalSales = deliveries.sumOf { it.totalAmount }
        val totalDeliveryCash = deliveries.sumOf { it.paymentCollected }
        val totalPayments = payments.sumOf { it.amount }
        val totalOpex = expenses.filter { it.expenseType == ExpenseType.OPEX }.sumOf { it.amount }
        val totalCapex = expenses.filter { it.expenseType == ExpenseType.CAPEX }.sumOf { it.amount }
        val totalExpenses = totalOpex + totalCapex
        val totalReceivables = customers.filter { it.currentBalance > 0 }.sumOf { it.currentBalance }
        val totalAdvances = customers.filter { it.currentBalance < 0 }.sumOf { -it.currentBalance }
        val totalDepositsHeld = customers.sumOf { it.effectiveSecurityDeposit }
        val totalPlantFull = inventory.sumOf { it.atPlantFull }
        val totalPlantEmpty = inventory.sumOf { it.atPlantEmpty }
        val totalWithCustomers = inventory.sumOf { it.withCustomers }
        val totalDamaged = inventory.sumOf { it.damaged }

        val sheets = mutableListOf<SheetData>()

        // 1. Summary Sheet
        sheets += SheetData(
            name = "Summary",
            headers = listOf("Metric", "Value", "Details / Notes"),
            rows = listOf(
                listOf("Report Title", "Aab-e-Baraan RO Drinking Water Plant - Complete Records", "All Tabs Combined Export"),
                listOf("Exported At", nowFormatted, "Exported By: $exportedBy"),
                listOf("Total Customers", customers.size, "Active: ${customers.count { it.active }}, Inactive: ${customers.count { !it.active }}"),
                listOf("Customers Missing Details", customers.count { it.hasMissingDetails }, "Incomplete profile details"),
                listOf("Total Customer Receivable (Rs.)", totalReceivables, "Customers with positive balance"),
                listOf("Total Customer Advance (Rs.)", totalAdvances, "Customers with advance credit"),
                listOf("Total Security Deposits Held (Rs.)", totalDepositsHeld, "${securityDeposits.size} deposit transactions"),
                listOf("Total Deliveries", deliveries.size, "Full Bottles Delivered: ${deliveries.sumOf { it.totalFullDelivered }}, Empty Collected: ${deliveries.sumOf { it.totalEmptyCollected }}"),
                listOf("Total Delivery Sales (Rs.)", totalSales, "Spot Payment Collected on Delivery: Rs. ${totalDeliveryCash.toInt()}"),
                listOf("Total Payments Recorded (Rs.)", totalPayments, "${payments.size} receipts (Pending Clear: ${payments.count { !it.isAcknowledged }})"),
                listOf("Total Expenses (Rs.)", totalExpenses, "OPEX: Rs. ${totalOpex.toInt()} | CAPEX: Rs. ${totalCapex.toInt()} | Unverified: ${expenses.count { !it.isAcknowledged || !it.isApprovedByOwner }}"),
                listOf("Net Operating Profit (Sales - Expenses)", totalSales - totalExpenses, "Accrual basis across all recorded data"),
                listOf("Total Bottle Stock - Plant Full", totalPlantFull, "${bottleSizes.size} bottle sizes configured"),
                listOf("Total Bottle Stock - Plant Empty", totalPlantEmpty, "Ready for refilling"),
                listOf("Total Bottles With Customers", totalWithCustomers, "${customerAllocations.size} active allocation records"),
                listOf("Total Damaged / Lost Bottles", totalDamaged, "Cumulative damage count"),
                listOf("Factory & RO Material Items", factoryMaterials.size + roPlantItems.size, "${materialTransactions.size + roTransactions.size} material movements"),
                listOf("Cash Closings Recorded", cashClosings.size, "Approved: ${cashClosings.count { it.isApprovedByOwner }}"),
                listOf("Invoices Generated", invoices.size, "Unpaid/Partial: ${invoices.count { it.status != InvoiceStatus.PAID }}"),
                listOf("Staff Accounts", userAccounts.size, "Active: ${userAccounts.count { it.active }}")
            )
        )

        // 2. Customers Sheet (Complete Details)
        val ratesByCustomer = customerRates.groupBy { it.customerId }
        val allocByCustomer = customerAllocations.groupBy { it.customerId }
        sheets += SheetData(
            name = "Customers",
            headers = listOf(
                "Customer ID", "Customer Code", "Name", "Phone", "WhatsApp", "Area", "Complete Address",
                "Customer Type", "Payment Cycle", "Registration Date", "Payment Due Date",
                "Opening Balance", "Current Balance", "Advance Credit", "Credit Limit",
                "Security Deposit", "Empty Bottles Held", "Total Purchases",
                "Agreed Bottle Rates", "Bottles Issued Breakdown", "Missing Details",
                "Notes", "Status", "Created By"
            ),
            rows = customers.map { c ->
                val agreedRatesStr = ratesByCustomer[c.id].orEmpty()
                    .filter { it.isAssigned }
                    .joinToString("; ") { "${it.bottleSizeName}: Rs.${it.customPrice.toInt()}" }
                val allocStr = allocByCustomer[c.id].orEmpty()
                    .filter { it.emptyHeld > 0 || it.totalIssued > 0 }
                    .joinToString("; ") { "${it.sizeName}: Held ${it.emptyHeld} / Issued ${it.totalIssued}" }
                listOf(
                    c.id, c.customerCode, c.name, c.phone, c.whatsapp, c.area, c.address,
                    c.type.label, c.paymentCycle.label, c.registrationDate, c.paymentDueDate,
                    c.openingBalance,
                    if (c.currentBalance > 0) c.currentBalance else 0.0,
                    if (c.currentBalance < 0) -c.currentBalance else 0.0,
                    c.creditLimit,
                    c.effectiveSecurityDeposit,
                    c.emptyBottlesOwed,
                    c.totalPurchases,
                    agreedRatesStr,
                    allocStr,
                    c.missingDetailLabels().joinToString(", "),
                    c.notes,
                    if (c.active) "Active" else "Inactive",
                    c.createdBy
                )
            }
        )

        // 3. Deliveries Sheet (Complete Details)
        sheets += SheetData(
            name = "Deliveries",
            headers = listOf(
                "Delivery ID", "Delivery Number", "Date", "Time", "Customer ID", "Customer Name",
                "Customer Phone", "Customer Area", "Customer Address", "Items Breakdown",
                "Total Full Delivered", "Total Empty Collected", "Subtotal", "Delivery Charges",
                "Total Amount", "Payment Collected", "Balance Remaining", "Payment Method",
                "Status", "Assigned Staff", "Invoice ID", "Notes", "Created By"
            ),
            rows = deliveries.sortedByDescending { it.date }.map { d ->
                val itemsSummary = d.items.joinToString("; ") {
                    "${it.bottleName} x${it.quantity} @ Rs.${it.unitPrice.toInt()} = Rs.${it.lineTotal.toInt()} (Empty:${it.emptyCollected})"
                }
                val remaining = (d.totalAmount - d.paymentCollected).coerceAtLeast(0.0)
                listOf(
                    d.id, d.deliveryNumber, d.date, d.time, d.customerId, d.customerName,
                    d.customerPhone, d.customerArea, d.customerAddress, itemsSummary,
                    d.totalFullDelivered, d.totalEmptyCollected, d.subtotal, d.deliveryCharges,
                    d.totalAmount, d.paymentCollected, remaining, d.paymentMethod,
                    d.status.label, d.staffName, d.invoiceId, d.notes, d.createdBy
                )
            }
        )

        // 4. Delivery Items Line-by-Line Sheet
        val deliveryItemRows = mutableListOf<List<Any?>>()
        deliveries.sortedByDescending { it.date }.forEach { d ->
            if (d.items.isEmpty()) {
                deliveryItemRows += listOf(
                    d.deliveryNumber, d.date, d.time, d.customerName, d.customerArea,
                    "-", 1, d.totalFullDelivered, 0.0, d.totalAmount, d.totalEmptyCollected,
                    d.paymentMethod, d.staffName
                )
            } else {
                d.items.forEach { item ->
                    deliveryItemRows += listOf(
                        d.deliveryNumber, d.date, d.time, d.customerName, d.customerArea,
                        item.bottleName, item.packSize, item.quantity, item.unitPrice,
                        item.lineTotal, item.emptyCollected, d.paymentMethod, d.staffName
                    )
                }
            }
        }
        sheets += SheetData(
            name = "Delivery_Items",
            headers = listOf(
                "Delivery Number", "Date", "Time", "Customer Name", "Area",
                "Bottle Size / Variant", "Pack Size", "Quantity Delivered", "Unit Rate",
                "Line Total", "Empty Collected", "Payment Method", "Staff Name"
            ),
            rows = deliveryItemRows
        )

        // 5. Payments Sheet (Complete Details)
        sheets += SheetData(
            name = "Payments",
            headers = listOf(
                "Payment ID", "Receipt Number", "Date", "Customer ID", "Customer Name",
                "Amount Received", "Payment Method", "Transaction Ref", "Advance Credited",
                "Invoice Allocations", "Notes", "Received By", "Role", "Admin Status", "Acknowledged By"
            ),
            rows = payments.sortedByDescending { it.date }.map { p ->
                val allocs = p.allocations.joinToString("; ") { "${it.invoiceNumber}: Rs.${it.allocatedAmount.toInt()}" }
                listOf(
                    p.id, p.receiptNumber, p.date, p.customerId, p.customerName,
                    p.amount, p.paymentMethod.label, p.transactionRef, p.advanceCredited,
                    allocs, p.notes, p.receivedBy, p.receivedByRole.displayName,
                    if (p.isAcknowledged) "Cleared" else "Pending Admin Clear",
                    p.acknowledgedBy
                )
            }
        )

        // 6. Expenses Sheet (Complete Details)
        sheets += SheetData(
            name = "Expenses",
            headers = listOf(
                "Expense ID", "Voucher Number", "Date", "Expense Type", "Category",
                "Description", "Amount", "Payment Method", "Payment Source", "Payee",
                "Notes", "Entered By", "Role", "Admin Status", "Acknowledged By"
            ),
            rows = expenses.sortedByDescending { it.date }.map { e ->
                val verified = e.isAcknowledged && e.isApprovedByOwner
                listOf(
                    e.id, e.voucherNumber, e.date, e.expenseType.label, e.categoryName,
                    e.description, e.amount, e.paymentMethod.label, e.paymentSource.label,
                    e.payee, e.notes, e.enteredBy, e.enteredByRole.displayName,
                    if (verified) "Verified" else "Pending Admin Verification",
                    e.acknowledgedBy
                )
            }
        )

        // 7. Bottle Stock & Sizes Sheet
        val sizeById = bottleSizes.associateBy { it.id }
        sheets += SheetData(
            name = "Bottle_Stock",
            headers = listOf(
                "Inventory ID", "Bottle Size ID", "Bottle Name", "Capacity (L)",
                "Usage Type", "Default Refill Rate", "Bottle Sale Price", "Deposit Amount",
                "Plant Full", "Plant Empty", "In Use", "Pack Variants Breakdown",
                "With Customers", "With Staff", "Damaged", "Total Purchased", "Total Issued/Sold"
            ),
            rows = inventory.map { inv ->
                val sz = sizeById[inv.bottleSizeId]
                val variantsStr = inv.packVariants
                    .filter { it.packetCount > 0 }
                    .joinToString("; ") { "${it.displayName}: ${it.packetCount}" }
                listOf(
                    inv.id, inv.bottleSizeId, inv.sizeName, sz?.capacityLitres ?: 0.0,
                    sz?.usageType?.label ?: "", sz?.defaultRefillPrice ?: 0.0,
                    sz?.bottleSalePrice ?: 0.0, sz?.depositAmount ?: 0.0,
                    inv.atPlantFull, inv.atPlantEmpty, inv.inUse, variantsStr,
                    inv.withCustomers, inv.withStaff, inv.damaged, inv.totalPurchased, inv.totalIssuedOrSold
                )
            }
        )

        // 8. Bottle Refills Sheet
        sheets += SheetData(
            name = "Bottle_Refills",
            headers = listOf(
                "Refill ID", "Date", "Time", "Bottle Size", "Quantity Filled",
                "Pack Variant", "Pack Size", "Complete Packs", "Loose Bottles", "Damaged",
                "Refill Cost / Unit", "Total Refill Cost", "Operator", "Notes"
            ),
            rows = bottleRefills.sortedByDescending { it.date }.map { r ->
                listOf(
                    r.id, r.date, r.time, r.sizeName, r.quantityFilled,
                    r.packedVariantName, r.packetSize, r.completePackets, r.looseBottles,
                    r.damagedBottles, r.refillCostPerBottle, r.totalRefillCost, r.operatorName, r.notes
                )
            }
        )

        // 9. Bottle Purchases Sheet
        sheets += SheetData(
            name = "Bottle_Purchases",
            headers = listOf(
                "Purchase ID", "Date", "Bottle Size", "Quantity", "Unit Cost", "Total Cost",
                "Supplier", "Invoice / Bill No", "Payment Source", "Payment Mode", "Notes", "Recorded By"
            ),
            rows = bottlePurchases.sortedByDescending { it.date }.map { bp ->
                listOf(
                    bp.id, bp.date, bp.sizeName, bp.quantity, bp.unitCost, bp.totalCost,
                    bp.supplierName, bp.invoiceOrBillNo, bp.paymentSource, bp.paymentMode,
                    bp.notes, bp.recordedBy
                )
            }
        )

        // 10. Bottle Issue & Sales Sheet
        sheets += SheetData(
            name = "Bottle_Issues",
            headers = listOf(
                "Record ID", "Date", "Time", "Customer Name", "Bottle Size", "Transaction Type",
                "Quantity", "Unit Price", "Revenue", "Total Cost", "Profit", "Delivery Staff", "Notes"
            ),
            rows = bottleIssueSales.sortedByDescending { it.date }.map { bi ->
                listOf(
                    bi.id, bi.date, bi.time, bi.customerName, bi.bottleSizeName, bi.transactionType.label,
                    bi.quantity, bi.unitSellingPrice, bi.revenue, bi.totalTransactionCost,
                    bi.profit, bi.deliveryPerson, bi.notes
                )
            }
        )

        // 11. Material Stock Sheet (Factory Materials + RO Plant Items)
        val materialStockRows = mutableListOf<List<Any?>>()
        factoryMaterials.forEach { m ->
            materialStockRows += listOf(
                "Factory Material", m.id, m.name, m.itemCategory, m.uom,
                m.currentStock, m.minStockLevel, m.purchaseRate, m.currentStock * m.purchaseRate,
                m.supplierName, if (m.active) "Active" else "Inactive"
            )
        }
        roPlantItems.forEach { item ->
            materialStockRows += listOf(
                "RO Plant Store", item.id, item.name, item.category.label, item.uom,
                item.currentStock, item.minimumAlertLevel, item.unitCost, item.currentStock * item.unitCost,
                item.supplierName, item.location
            )
        }
        sheets += SheetData(
            name = "Material_Stock",
            headers = listOf(
                "Store / Source", "Item ID", "Item Name", "Category", "Unit (UOM)",
                "Current Stock", "Min Alert Level", "Unit Cost", "Stock Value", "Supplier", "Status / Location"
            ),
            rows = materialStockRows
        )

        // 12. Material History Sheet
        val materialHistRows = mutableListOf<List<Any?>>()
        materialTransactions.sortedByDescending { it.date }.forEach { mt ->
            materialHistRows += listOf(
                mt.date, "Factory Material", mt.materialName, mt.actionType.label,
                mt.quantity, mt.uom, mt.unitCost, mt.totalCost, mt.supplierOrReceiver,
                mt.balanceAfter, mt.notes, mt.performedBy
            )
        }
        roTransactions.sortedByDescending { it.date }.forEach { rt ->
            materialHistRows += listOf(
                rt.date, "RO Plant Store", rt.itemName, rt.actionType.label,
                rt.quantity, rt.uom, rt.unitCost, rt.totalCost, rt.reasonOrPurpose,
                rt.newStock, rt.notes, rt.responsibleEmployee
            )
        }
        sheets += SheetData(
            name = "Material_History",
            headers = listOf(
                "Date", "Store / Source", "Item Name", "Action Type", "Quantity",
                "Unit", "Unit Cost", "Total Cost", "Supplier / Purpose", "Balance After", "Notes", "Performed By"
            ),
            rows = materialHistRows
        )

        // 13. Cash Closing Sheet
        sheets += SheetData(
            name = "Cash_Closing",
            headers = listOf(
                "Closing ID", "Date", "Opening Cash", "Customer Cash Collected", "Other Cash Income",
                "Cash Expenses", "Cash Refunds", "Expected Closing Cash", "Actual Closing Cash",
                "Difference", "Balance Status", "Closed By", "Admin Status", "Notes"
            ),
            rows = cashClosings.sortedByDescending { it.date }.map { cc ->
                val balStatus = when {
                    cc.isBalanced -> "Balanced"
                    cc.isShortage -> "Shortage"
                    else -> "Surplus"
                }
                listOf(
                    cc.id, cc.date, cc.openingCash, cc.customerCashCollected, cc.otherCashIncome,
                    cc.cashExpenses, cc.cashRefunds, cc.expectedClosingCash, cc.actualClosingCash,
                    cc.difference, balStatus, cc.closedBy,
                    if (cc.isApprovedByOwner) "Approved by Admin" else "Pending Review",
                    cc.notes
                )
            }
        )

        // 14. Security Deposits Sheet
        sheets += SheetData(
            name = "Security_Deposits",
            headers = listOf(
                "Transaction ID", "Date", "Customer ID", "Customer Name", "Transaction Type",
                "Amount", "Payment Method", "Reference", "Notes", "Recorded By"
            ),
            rows = securityDeposits.sortedByDescending { it.date }.map { sd ->
                listOf(
                    sd.id, sd.date, sd.customerId, sd.customerName, sd.type.label,
                    sd.amount, sd.paymentMethod.label, sd.reference, sd.notes, sd.createdBy
                )
            }
        )

        // 15. Invoices Sheet
        sheets += SheetData(
            name = "Invoices",
            headers = listOf(
                "Invoice ID", "Invoice Number", "Date", "Due Date", "Customer ID", "Customer Name",
                "Delivery ID", "Items Summary", "Total Amount", "Paid Amount", "Balance Due", "Status"
            ),
            rows = invoices.sortedByDescending { it.date }.map { inv ->
                listOf(
                    inv.id, inv.invoiceNumber, inv.date, inv.dueDate, inv.customerId, inv.customerName,
                    inv.deliveryId, inv.itemsSummary, inv.totalAmount, inv.paidAmount, inv.balanceDue, inv.status.label
                )
            }
        )

        // 16. Staff Accounts Sheet (strictly secret-free — no passwords/hashes)
        sheets += SheetData(
            name = "Staff_Accounts",
            headers = listOf(
                "Account ID", "Username", "Full Name", "Phone", "Role",
                "Super Admin", "Google Email", "Status", "Assigned Branches"
            ),
            rows = userAccounts.map { acc ->
                listOf(
                    acc.id, acc.username, acc.name, acc.phone, acc.role.displayName,
                    if (acc.isSuperAdmin) "Yes" else "No", acc.googleEmail,
                    if (acc.active) "Active" else "Inactive",
                    acc.assignedBranchIds.joinToString(", ")
                )
            }
        )

        writeXlsxFile(outputFile, sheets)
        return outputFile
    }

    private fun writeXlsxFile(outputFile: File, sheets: List<SheetData>) {
        outputFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            fun putEntry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // [Content_Types].xml
            val sheetOverrides = sheets.indices.joinToString("\n") { i ->
                """  <Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
            }
            putEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$sheetOverrides
</Types>"""
            )

            // _rels/.rels
            putEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            // xl/workbook.xml
            val sheetEntries = sheets.mapIndexed { i, sheet ->
                val safeName = sanitizeSheetName(sheet.name, i + 1)
                """    <sheet name="${xmlEscape(safeName)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("\n")
            putEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
$sheetEntries
  </sheets>
</workbook>"""
            )

            // xl/_rels/workbook.xml.rels
            val sheetRels = sheets.indices.joinToString("\n") { i ->
                """  <Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>"""
            }
            val stylesRelId = "rId${sheets.size + 1}"
            putEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$sheetRels
  <Relationship Id="$stylesRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            // xl/styles.xml — Style 0: default, Style 1: bold navy header with white text, Style 2: numeric 2-decimal, Style 3: integer
            putEntry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0A2E52"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD0D7DE"/></left>
      <right style="thin"><color rgb="FFD0D7DE"/></right>
      <top style="thin"><color rgb="FFD0D7DE"/></top>
      <bottom style="thin"><color rgb="FFD0D7DE"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="4" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
    <xf numFmtId="3" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
            )

            // Worksheets
            sheets.forEachIndexed { idx, sheet ->
                putEntry("xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(sheet))
            }
        }
    }

    private fun buildWorksheetXml(sheet: SheetData): String {
        val sb = StringBuilder(4096)
        val colCount = sheet.headers.size
        val colWidths = IntArray(colCount) { i -> sheet.headers[i].length.coerceIn(12, 36) }

        sheet.rows.forEach { row ->
            row.forEachIndexed { colIdx, value ->
                if (colIdx < colCount) {
                    val len = (value?.toString()?.length ?: 0)
                    if (len > colWidths[colIdx]) {
                        colWidths[colIdx] = len.coerceAtMost(44)
                    }
                }
            }
        }

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("\n")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("\n  <sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>\n")

        if (colCount > 0) {
            sb.append("  <cols>\n")
            for (i in 0 until colCount) {
                val width = (colWidths[i] + 4).coerceIn(12, 48)
                sb.append("""    <col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""")
                sb.append("\n")
            }
            sb.append("  </cols>\n")
        }

        sb.append("  <sheetData>\n")

        // Header Row (Row 1, style 1 = bold navy fill with white text)
        sb.append("""    <row r="1" ht="22" customHeight="1">""")
        sheet.headers.forEachIndexed { colIdx, header ->
            val cellRef = "${colLetter(colIdx)}1"
            sb.append("""<c r="$cellRef" t="inlineStr" s="1"><is><t>${xmlEscape(header)}</t></is></c>""")
        }
        sb.append("</row>\n")

        // Data Rows
        sheet.rows.forEachIndexed { rowIdx, rowValues ->
            val excelRow = rowIdx + 2
            sb.append("""    <row r="$excelRow">""")
            rowValues.forEachIndexed { colIdx, raw ->
                val cellRef = "${colLetter(colIdx)}$excelRow"
                when (raw) {
                    null -> sb.append("""<c r="$cellRef" t="inlineStr" s="0"><is><t></t></is></c>""")
                    is Int, is Long -> {
                        sb.append("""<c r="$cellRef" s="3"><v>$raw</v></c>""")
                    }
                    is Double -> {
                        if (raw.isNaN() || raw.isInfinite()) {
                            sb.append("""<c r="$cellRef" s="2"><v>0</v></c>""")
                        } else {
                            sb.append("""<c r="$cellRef" s="2"><v>$raw</v></c>""")
                        }
                    }
                    is Float -> {
                        if (raw.isNaN() || raw.isInfinite()) {
                            sb.append("""<c r="$cellRef" s="2"><v>0</v></c>""")
                        } else {
                            sb.append("""<c r="$cellRef" s="2"><v>$raw</v></c>""")
                        }
                    }
                    else -> {
                        val safeText = stripInvalidXmlChars(raw.toString())
                        sb.append("""<c r="$cellRef" t="inlineStr" s="0"><is><t xml:space="preserve">${xmlEscape(safeText)}</t></is></c>""")
                    }
                }
            }
            sb.append("</row>\n")
        }

        sb.append("  </sheetData>\n")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun colLetter(zeroBasedIndex: Int): String {
        var n = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun sanitizeSheetName(raw: String, fallbackIndex: Int): String {
        val cleaned = raw.replace(Regex("""[:\\/?*\[\]]"""), "_").trim().take(31)
        return cleaned.ifBlank { "Sheet$fallbackIndex" }
    }

    private fun stripInvalidXmlChars(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val code = ch.code
            if (code == 0x9 || code == 0xA || code == 0xD || (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun xmlEscape(value: String): String {
        val sb = StringBuilder(value.length + 16)
        for (ch in value) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
