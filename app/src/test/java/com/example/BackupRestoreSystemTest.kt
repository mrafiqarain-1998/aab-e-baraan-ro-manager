package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlantDataStorage
import com.example.data.WaterPlantRepository
import com.example.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreSystemTest {

    private lateinit var context: Context
    private lateinit var repository: WaterPlantRepository
    private lateinit var storage: PlantDataStorage

    @Before
    fun setUp() {
        WaterPlantRepository.resetInstanceForTesting()
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = WaterPlantRepository.getInstance(context)
        storage = PlantDataStorage(context)
        repository.switchUserRole(UserRole.ADMIN)
    }

    @Test
    fun testCreateFullBackupSnapshotIntegrityAndChecksum() {
        val snapshot = repository.createFullBackupSnapshot(BackupType.MANUAL, "Test manual backup")
        assertNotNull(snapshot)
        assertTrue(snapshot.metadata.backupId.startsWith("backup_"))
        assertEquals(BackupType.MANUAL, snapshot.metadata.backupType)
        assertTrue("Total records should be > 0", snapshot.metadata.totalRecords > 0)
        assertTrue("Checksum SHA-256 must be generated", snapshot.metadata.checksumSha256.isNotBlank())
        assertEquals("SHA-256 should be 64 hex characters", 64, snapshot.metadata.checksumSha256.length)

        // Verify JSON serialization and deserialization
        val json = storage.serializeBackupData(snapshot)
        assertTrue(json.isNotBlank())
        val deserialized = storage.deserializeBackupData(json)
        assertNotNull(deserialized)
        assertEquals(snapshot.metadata.backupId, deserialized?.metadata?.backupId)
        assertEquals(snapshot.customers.size, deserialized?.customers?.size)
        assertEquals(snapshot.deliveries.size, deserialized?.deliveries?.size)
        assertEquals(snapshot.payments.size, deserialized?.payments?.size)
        assertEquals(snapshot.expenses.size, deserialized?.expenses?.size)
    }

    @Test
    fun testBackupVerificationReport() {
        val snapshot = repository.createFullBackupSnapshot(BackupType.MANUAL)
        val validJson = storage.serializeBackupData(snapshot)

        // Verify valid payload
        val validReport = repository.verifyBackupPayload(validJson)
        assertTrue(validReport.isValid)
        assertEquals(snapshot.metadata.totalRecords, validReport.totalRecords)
        assertTrue(validReport.checksumMatched)
        assertTrue(validReport.errors.isEmpty())

        // Verify empty payload
        val emptyReport = repository.verifyBackupPayload("")
        assertFalse(emptyReport.isValid)
        assertTrue(emptyReport.errors.isNotEmpty())

        // Verify corrupted JSON
        val corruptReport = repository.verifyBackupPayload("{ corrupt_json: true, ... }")
        assertFalse(corruptReport.isValid)
        assertTrue(corruptReport.errors.isNotEmpty())
    }

    @Test
    fun testSafeRestoreCreatesAutomaticSafetySnapshot() {
        val originalHistoryCount = repository.backupHistory.value.size

        // 1. Create a known test snapshot
        val testSnapshot = repository.createFullBackupSnapshot(BackupType.MANUAL, "Target Restore State")
        val testCustomer = Customer(
            id = "restore_test_cust_999",
            name = "Test Restore Customer",
            phone = "03009999999",
            customerCode = "C-999",
            active = true
        )
        val snapshotWithCustomer = testSnapshot.copy(
            customers = testSnapshot.customers + testCustomer,
            metadata = testSnapshot.metadata.copy(
                totalRecords = testSnapshot.metadata.totalRecords + 1,
                recordCounts = testSnapshot.metadata.recordCounts.toMutableMap().apply {
                    put("customers", testSnapshot.customers.size + 1)
                }
            )
        )

        // 2. Perform restore with requireSafetyBackup = true
        val latch = java.util.concurrent.CountDownLatch(1)
        var restoreSuccess = false
        var restoreMessage = ""
        repository.restoreFromBackup(snapshotWithCustomer, requireSafetyBackup = true) { success, msg ->
            restoreSuccess = success
            restoreMessage = msg
            latch.countDown()
        }

        assertTrue("Restore should complete within 5 seconds", latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue(restoreSuccess)
        assertTrue(restoreMessage.contains("restored successfully"))

        // Verify that the restored customer is present
        val loadedCustomers = repository.customers.value
        assertTrue(loadedCustomers.any { it.id == "restore_test_cust_999" })

        // Verify that an automatic PRE_RESTORE_SAFETY backup was added to history
        val newHistory = repository.backupHistory.value
        assertTrue(newHistory.size > originalHistoryCount)
        val safetySnapshotLog = newHistory.firstOrNull { it.type == BackupType.PRE_RESTORE_SAFETY }
        assertNotNull("Safety pre-restore snapshot must be recorded in history", safetySnapshotLog)
        assertEquals("SUCCESS", safetySnapshotLog?.status)
    }

    @Test
    fun testAutoBackupToggleAndDatePersistence() {
        // Test direct storage write and read first
        storage.saveAutoBackupEnabled(false)
        assertFalse("Direct storage should load false", storage.loadAutoBackupEnabled())

        storage.saveAutoBackupEnabled(true)
        assertTrue("Direct storage should load true", storage.loadAutoBackupEnabled())

        // Test toggle via repository as ADMIN
        repository.switchUserRole(UserRole.ADMIN)
        repository.toggleAutoBackup(false)
        assertFalse("Repository flow should be false", repository.isAutoBackupEnabled.value)
        assertFalse("Storage after repo toggle should be false", storage.loadAutoBackupEnabled())

        repository.toggleAutoBackup(true)
        assertTrue("Repository flow should be true", repository.isAutoBackupEnabled.value)
        assertTrue("Storage after repo toggle should be true", storage.loadAutoBackupEnabled())

        // Test daily date tracking
        val todayStr = "2026-10-26"
        storage.saveLastAutoBackupDate(todayStr)
        assertEquals(todayStr, storage.loadLastAutoBackupDate())
    }

    @Test
    fun testCsvExportsForBusinessRecords() {
        val customerCsv = repository.generateCsvExport("CUSTOMERS")
        assertNotNull(customerCsv)
        assertTrue(customerCsv!!.exists())
        val custText = customerCsv.readText()
        assertTrue(custText.contains("Customer ID,Customer Code,Name,Phone"))

        val deliveryCsv = repository.generateCsvExport("DELIVERIES")
        assertNotNull(deliveryCsv)
        assertTrue(deliveryCsv!!.exists())
        val delText = deliveryCsv.readText()
        assertTrue(delText.contains("Delivery ID,Delivery Number,Date"))

        val paymentCsv = repository.generateCsvExport("PAYMENTS")
        assertNotNull(paymentCsv)
        assertTrue(paymentCsv!!.exists())
        val payText = paymentCsv.readText()
        assertTrue(payText.contains("Payment ID,Receipt Number,Date"))

        val expenseCsv = repository.generateCsvExport("EXPENSES")
        assertNotNull(expenseCsv)
        assertTrue(expenseCsv!!.exists())
        val expText = expenseCsv.readText()
        assertTrue(expText.contains("Expense ID,Voucher Number,Date"))

        val inventoryCsv = repository.generateCsvExport("INVENTORY")
        assertNotNull(inventoryCsv)
        assertTrue(inventoryCsv!!.exists())
        val invText = inventoryCsv.readText()
        assertTrue(invText.contains("Inventory ID,Bottle Size ID,Bottle Name"))

        val cashCsv = repository.generateCsvExport("CASH_CLOSING")
        assertNotNull(cashCsv)
        assertTrue(cashCsv!!.exists())
        val cashText = cashCsv.readText()
        assertTrue(cashText.contains("Closing ID,Date,Opening Cash"))
    }

    @Test
    fun testBackupNowExecutesCleanlyAndRecordsHistory() {
        val initialHistory = repository.backupHistory.value.size
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        var message = ""

        repository.backupNow("End of day test") { success, msg ->
            ok = success
            message = msg
            latch.countDown()
        }

        assertTrue("backupNow should finish within 5s", latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("backupNow must succeed: $message", ok)
        assertTrue(message.contains("Backup completed successfully"))
        assertTrue(repository.backupHistory.value.size > initialHistory)
        assertEquals("SUCCESS", repository.backupHistory.value.first().status)
    }

    @Test
    fun testCompleteExcelWorkbookExportContainsAllDetailedSheets() {
        // Ensure sample customer & delivery exist
        repository.addCustomer(
            Customer(
                name = "Excel Test Customer",
                phone = "03001234567",
                whatsapp = "03001234567",
                area = "Johar Town",
                address = "House 9, Block G",
                active = true
            )
        )

        val xlsxFile = repository.generateCompleteExcelExport()
        assertNotNull("Complete Excel .xlsx file must be created", xlsxFile)
        assertTrue("Excel file must exist and be non-empty", xlsxFile!!.exists() && xlsxFile.length() > 1000L)
        assertTrue("File name must end with .xlsx", xlsxFile.name.endsWith(".xlsx"))

        java.util.zip.ZipFile(xlsxFile).use { zip ->
            assertNotNull("[Content_Types].xml missing", zip.getEntry("[Content_Types].xml"))
            assertNotNull("xl/workbook.xml missing", zip.getEntry("xl/workbook.xml"))
            assertNotNull("xl/styles.xml missing", zip.getEntry("xl/styles.xml"))

            val workbookXml = zip.getInputStream(zip.getEntry("xl/workbook.xml")).bufferedReader().use { it.readText() }
            val expectedSheets = listOf(
                "Summary", "Customers", "Deliveries", "Delivery_Items",
                "Payments", "Expenses", "Bottle_Stock", "Bottle_Refills",
                "Bottle_Purchases", "Bottle_Issues", "Material_Stock",
                "Material_History", "Cash_Closing", "Security_Deposits",
                "Invoices", "Staff_Accounts"
            )
            expectedSheets.forEach { sheetName ->
                assertTrue("Workbook must contain sheet '$sheetName'", workbookXml.contains("name=\"$sheetName\""))
            }

            // Verify Customers sheet contains our newly added customer's full details
            val customersSheetXml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml")).bufferedReader().use { it.readText() }
            assertTrue(customersSheetXml.contains("Excel Test Customer"))
            assertTrue(customersSheetXml.contains("Johar Town"))
            assertTrue(customersSheetXml.contains("House 9, Block G"))

            // Verify Staff_Accounts sheet NEVER exports plaintext passwords or hashes
            val staffSheetXml = zip.getInputStream(zip.getEntry("xl/worksheets/sheet16.xml")).bufferedReader().use { it.readText() }
            assertFalse("Staff Excel sheet must NEVER contain plaintext password", staffSheetXml.contains("admin123"))
        }
    }
}

