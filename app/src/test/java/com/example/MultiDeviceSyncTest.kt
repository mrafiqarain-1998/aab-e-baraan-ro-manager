package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LoginOutcome
import com.example.data.OfflineSyncManager
import com.example.data.PlantDataStorage
import com.example.data.WaterPlantRepository
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiDeviceSyncTest {

    private lateinit var context: Context
    private lateinit var storage: PlantDataStorage
    private lateinit var repository: WaterPlantRepository

    @Before
    fun setUp() {
        WaterPlantRepository.resetInstanceForTesting()
        context = ApplicationProvider.getApplicationContext()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        storage = PlantDataStorage(context)
        repository = WaterPlantRepository.getInstance(context)
        repository.switchUserRole(UserRole.ADMIN)
    }

    @Test
    fun `offline sync queue preserves chronological order across restart`() {
        repository.syncDocument("customers", "cust_sync_1", Customer(id = "cust_sync_1", name = "First Customer"))
        Thread.sleep(5)
        repository.syncDocument("deliveries", "del_sync_2", mapOf("id" to "del_sync_2", "customerId" to "cust_sync_1"))
        Thread.sleep(5)
        repository.syncDocument("payments", "pay_sync_3", mapOf("id" to "pay_sync_3", "customerId" to "cust_sync_1", "amount" to 500.0))

        val managerBeforeReset = OfflineSyncManager(context, storage)
        val orderedBefore = managerBeforeReset.getPendingQueueItems(forceIncludeFailed = true)
        assertEquals(listOf("customers|cust_sync_1", "deliveries|del_sync_2", "payments|pay_sync_3"), orderedBefore.map { it.id })
        assertTrue(orderedBefore[0].createdAtMillis <= orderedBefore[1].createdAtMillis)
        assertTrue(orderedBefore[1].createdAtMillis <= orderedBefore[2].createdAtMillis)

        // Simulate app restart
        WaterPlantRepository.resetInstanceForTesting()
        val reloadedRepo = WaterPlantRepository.getInstance(context)
        val reloadedIds = reloadedRepo.syncQueueItems.value.map { it.id }.toSet()
        assertTrue(reloadedIds.containsAll(listOf("customers|cust_sync_1", "deliveries|del_sync_2", "payments|pay_sync_3")))
    }

    @Test
    fun `offline delete is queued even when cloudKnownDocKeys is empty`() {
        // Fresh repository has an empty cloudKnownDocKeys set
        repository.deleteDocument("expenses", "exp_remote_unknown_1")

        val manager = OfflineSyncManager(context, storage)
        val pendingDeletes = manager.getPendingDeleteDocIds("expenses")
        assertTrue(pendingDeletes.contains("exp_remote_unknown_1"))
        assertTrue(manager.hasPendingOrFailedMutation("expenses", "exp_remote_unknown_1"))

        val deleteItem = repository.syncQueueItems.value.find { it.id == "expenses|exp_remote_unknown_1" }
        assertNotNull(deleteItem)
        assertEquals(SyncOperationType.DELETE, deleteItem!!.operation)
        assertEquals(SyncQueueStatus.PENDING, deleteItem.status)
    }

    @Test
    fun `offline incremental deltas coalesce accurately across multiple mutations`() {
        val manager = OfflineSyncManager(context, storage)
        manager.enqueueIncrementalUpsert(
            collection = "customers",
            docId = "cust_delta_1",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            fullPayloadJson = """{"id":"cust_delta_1","name":"Delta Shop","currentBalance":500.0}""",
            deltaFields = mapOf("currentBalance" to 500.0, "emptyBottlesOwed" to 5),
            actorName = "Driver A"
        )
        manager.enqueueIncrementalUpsert(
            collection = "customers",
            docId = "cust_delta_1",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            fullPayloadJson = """{"id":"cust_delta_1","name":"Delta Shop","currentBalance":300.0}""",
            deltaFields = mapOf("currentBalance" to -200.0, "emptyBottlesOwed" to -2),
            actorName = "Driver A"
        )

        val item = manager.queueItems.value.find { it.id == "customers|cust_delta_1" }
        assertNotNull(item)
        val decoded = OfflineSyncManager.deltaMapFromJson(item!!.deltaFieldsJson)
        assertNotNull(decoded)
        assertEquals(300.0, decoded!!["currentBalance"]!!.toDouble(), 0.001)
        assertEquals(3L, decoded["emptyBottlesOwed"]!!.toLong())

        // Also test real repository offline delivery + payment flow produces delta queue items
        val custId = repository.addCustomer(Customer(name = "Live Delta Customer", phone = "03001112233"))
        repository.adjustBottleStock("size_19l", fullChange = 20, reason = "Test fill")
        repository.setCustomerPrice(custId, "size_19l", 150.0, "19 Litre Bottle", "2026-11-01")

        val deliveryMsg = repository.recordDelivery(
            customerId = custId,
            items = listOf(DeliveryItem(bottleSizeId = "size_19l", bottleName = "19 Litre Bottle", quantity = 4, emptyCollected = 1, unitPrice = 150.0, lineTotal = 600.0)),
            assignedStaffName = "Staff A",
            notes = "Credit delivery",
            immediatePaymentAmount = 0.0
        )
        assertFalse("Delivery should succeed, got: $deliveryMsg", deliveryMsg.contains("Insufficient", ignoreCase = true))

        repository.recordPayment(custId, 250.0, PaymentMethod.CASH, notes = "Partial recovery")

        val custQueue = repository.syncQueueItems.value.find { it.id == "customers|$custId" }
        assertNotNull(custQueue)
        val custDeltas = OfflineSyncManager.deltaMapFromJson(custQueue!!.deltaFieldsJson)
        assertNotNull(custDeltas)
        assertEquals(350.0, custDeltas!!["currentBalance"]!!.toDouble(), 0.001)
        assertEquals(3L, custDeltas["emptyBottlesOwed"]!!.toLong())

        val invId19l = repository.inventory.value.find { it.bottleSizeId == "size_19l" }?.id
        assertNotNull(invId19l)
        val invQueue = repository.syncQueueItems.value.find { it.id == "bottleInventory|$invId19l" }
        assertNotNull(invQueue)
        val invDeltas = OfflineSyncManager.deltaMapFromJson(invQueue!!.deltaFieldsJson)
        assertNotNull(invDeltas)
        assertEquals(16L, invDeltas!!["atPlantFull"]!!.toLong())
    }

    @Test
    fun `rapid same-millisecond deliveries produce unique firestore-valid IDs`() {
        val custId = repository.addCustomer(Customer(name = "Rapid Order Cafe", phone = "03002223344"))
        repository.adjustBottleStock("size_19l", fullChange = 100, reason = "Bulk prep")
        repository.adjustBottleStock("size_12l", fullChange = 100, reason = "Bulk prep")
        repository.setCustomerPrice(custId, "size_19l", 120.0, "19 Litre Bottle", "2026-11-01")
        repository.setCustomerPrice(custId, "size_12l", 80.0, "12.5 Litre Bottle", "2026-11-01")

        repeat(5) {
            repository.recordDelivery(
                customerId = custId,
                items = listOf(
                    DeliveryItem(bottleSizeId = "size_19l", bottleName = "19 Litre Bottle", quantity = 2, emptyCollected = 1, unitPrice = 120.0, lineTotal = 240.0),
                    DeliveryItem(bottleSizeId = "size_12l", bottleName = "12.5 Litre Bottle", quantity = 3, emptyCollected = 0, unitPrice = 80.0, lineTotal = 240.0)
                ),
                assignedStaffName = "Fast Driver",
                notes = "Rapid #$it",
                immediatePaymentAmount = 100.0
            )
        }

        val idRegex = Regex("^[a-zA-Z0-9_-]+$")
        val delIds = repository.deliveries.value.map { it.id }
        val invIds = repository.invoices.value.map { it.id }
        val payIds = repository.payments.value.map { it.id }
        val bisIds = repository.bottleIssueSales.value.map { it.id }

        assertEquals(5, delIds.distinct().size)
        assertEquals(5, invIds.distinct().size)
        assertEquals(5, payIds.distinct().size)
        assertEquals(10, bisIds.distinct().size)
        (delIds + invIds + payIds + bisIds).forEach { id ->
            assertTrue("Invalid Firestore ID format: $id", idRegex.matches(id))
        }
    }

    @Test
    fun `staff account UID alignment, secret-free reset persistence, and username login work`() = runBlocking {
        val createdUid = repository.createUserAccount(
            UserAccount(
                username = "kashif_staff",
                password = "Pass#9876",
                name = "Kashif Plant",
                role = UserRole.INCHARGE
            )
        )
        assertTrue("Expected staff_ prefix, got: $createdUid", createdUid.startsWith("staff_"))
        val acct = repository.userAccounts.value.find { it.id == createdUid }
        assertNotNull(acct)
        assertEquals(createdUid, acct!!.uid)

        // Password reset request persists across resetInstanceForTesting without plaintext leakage
        val reqId = repository.requestPasswordReset("kashif_staff")
        assertTrue(reqId.isNotBlank())
        assertTrue(repository.resolvePasswordReset(reqId, "NewPass@2026"))

        val resetRaw = File(context.filesDir, "db_password_resets.json").readText()
        assertFalse(resetRaw.contains("NewPass@2026"))

        WaterPlantRepository.resetInstanceForTesting()
        val freshRepo = WaterPlantRepository.getInstance(context)
        assertTrue(freshRepo.passwordResetRequests.value.any { it.id == reqId && it.isResolved })

        val (outcome, profile) = freshRepo.authenticateWithOutcome("kashif_staff", "NewPass@2026")
        assertEquals(LoginOutcome.SUCCESS, outcome)
        assertNotNull(profile)
        assertEquals(createdUid, profile!!.uid)
    }

    @Test
    fun `forceDeleteBottleSize queues delete on bottleInventory collection`() {
        val sizeId = repository.addBottleSize(
            BottleSize(
                name = "Temp 5L Gallon",
                capacityLitres = 5.0,
                usageType = BottleUsageType.MULTIPLE_TIME,
                isReturnableBottle = true,
                openingStock = 5
            )
        )
        assertTrue(sizeId.isNotBlank())
        val (deleted, _) = repository.forceDeleteBottleSize(sizeId)
        assertTrue(deleted)

        val invDeletes = repository.syncQueueItems.value
            .filter { it.collection == "bottleInventory" && it.operation == SyncOperationType.DELETE }
            .map { it.docId }
        val wrongInvDeletes = repository.syncQueueItems.value
            .filter { it.collection == "inventory" && it.operation == SyncOperationType.DELETE }
        val sizeDeletes = repository.syncQueueItems.value
            .filter { it.collection == "bottleSizes" && it.operation == SyncOperationType.DELETE }
            .map { it.docId }

        assertTrue(invDeletes.contains("inv_$sizeId"))
        assertTrue(wrongInvDeletes.isEmpty())
        assertTrue(sizeDeletes.contains(sizeId))
    }

    @Test
    fun `cash closing math, balances, and RBAC delete guards hold`() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val custId = repository.addCustomer(Customer(name = "Cash Test Customer", currentBalance = 2000.0))
        repository.recordPayment(custId, 1200.0, PaymentMethod.CASH, notes = "Cash collected")
        repository.addExpense(
            categoryId = "cat_maint",
            categoryName = "Maintenance",
            description = "Filter wash",
            amount = 350.0,
            method = PaymentMethod.CASH,
            payee = "Technician",
            notes = "",
            type = ExpenseType.OPEX,
            expenseDate = today,
            paymentSource = PaymentSource.PAY_FROM_SALE
        )

        val updatedCust = repository.getCustomerById(custId)!!
        assertEquals(800.0, updatedCust.currentBalance, 0.001)

        val summary = repository.getDailyCashSummary(today)
        assertEquals(1200.0, summary.customerCashCollected, 0.001)
        assertEquals(350.0, summary.cashExpenses, 0.001)
        assertEquals(summary.openingCash + 1200.0 - 350.0, summary.expectedClosingCash, 0.001)

        // View-only role cannot delete customers or bottle sizes
        repository.switchUserRole(UserRole.VIEW_ONLY)
        assertFalse(repository.deleteCustomer(custId))
        assertFalse(repository.forceDeleteBottleSize("size_19l").first)
    }

    @Test
    fun `firestore named database ID is strictly bound and only mrafiqarain1998@gmail is Super Admin`() {
        val dbId = context.getString(R.string.firestore_database_id).trim()
        assertEquals("ai-studio-android-e454ad39-56bf-4477-a4f8-70f0a5b3b75f", dbId)
        assertNotEquals("(default)", dbId)

        assertTrue(repository.handleGoogleAuthSuccess("uid_super_new", "mrafiqarain1998@gmail.com", "Muhammad Rafiq"))
        assertTrue(repository.currentUser.value.isSuperAdmin)
        assertEquals(UserRole.ADMIN, repository.currentUser.value.role)

        assertFalse(repository.handleGoogleAuthSuccess("uid_old_removed", "muhammadrafiq.utopia@gmail.com", "Old Admin"))
        assertFalse(repository.handleGoogleAuthSuccess("uid_fake_admin", "fake.admin@gmail.com", "Fake Admin"))
    }

    @Test
    fun `complete delivery, expense, and customer actions each group into a single sync action entry`() {
        val custId = repository.addCustomer(Customer(name = "Action Group Mart", phone = "03005556677"))
        val customerGroups = repository.syncQueueItems.value
            .filter { it.status != SyncQueueStatus.SYNCED }
            .map { it.actionGroupId }
            .distinct()
        assertEquals("Adding a customer should produce 1 sync action group", 1, customerGroups.size)

        repository.adjustBottleStock("size_19l", fullChange = 30, reason = "Stock for grouped test")
        repository.setCustomerPrice(custId, "size_19l", 200.0, "19 Litre Bottle", "2026-11-01")
        // Clear prior queued items so we inspect recordDelivery cleanly in isolation
        val priorIds = repository.syncQueueItems.value.map { it.id }
        val mgr = OfflineSyncManager(context, storage)
        priorIds.forEach { id ->
            val ts = mgr.markSyncing(id)
            mgr.markSynced(id, ts)
        }
        WaterPlantRepository.resetInstanceForTesting()
        repository = WaterPlantRepository.getInstance(context)
        repository.switchUserRole(UserRole.ADMIN)

        val delResult = repository.recordDelivery(
            customerId = custId,
            items = listOf(
                DeliveryItem(
                    bottleSizeId = "size_19l",
                    bottleName = "19 Litre Bottle",
                    quantity = 5,
                    emptyCollected = 2,
                    unitPrice = 200.0,
                    lineTotal = 1000.0
                )
            ),
            assignedStaffName = "Driver Ali",
            notes = "Grouped delivery check",
            immediatePaymentAmount = 400.0
        )
        assertFalse("Delivery should succeed: $delResult", delResult.contains("Insufficient", ignoreCase = true))

        val deliveryItems = repository.syncQueueItems.value.filter { it.status != SyncQueueStatus.SYNCED }
        assertTrue("Delivery must queue multiple underlying docs (deliveries, invoices, customers, inventory, payments, etc.)", deliveryItems.size >= 5)
        val deliveryGroups = deliveryItems.map { it.actionGroupId.ifBlank { it.id } }.distinct()
        assertEquals("Complete delivery must appear as ONE action group in sync queue", 1, deliveryGroups.size)
        assertTrue(
            "Action title should describe the delivery",
            deliveryItems.any { it.actionTitle.contains("Delivery", ignoreCase = true) }
        )

        // Now add an expense and verify it forms exactly 1 additional action group
        val expId = repository.addExpense(
            categoryId = "cat_elec",
            categoryName = "Electricity",
            description = "WAPDA bill",
            amount = 1500.0,
            method = PaymentMethod.CASH,
            payee = "LESCO",
            notes = "Monthly bill",
            type = ExpenseType.OPEX,
            expenseDate = "2026-11-18",
            paymentSource = PaymentSource.PAY_FROM_SALE
        )
        assertTrue(expId.isNotBlank())
        val allGroupsAfterExpense = repository.syncQueueItems.value
            .filter { it.status != SyncQueueStatus.SYNCED }
            .map { it.actionGroupId.ifBlank { it.id } }
            .distinct()
        assertEquals("Delivery + Expense must appear as 2 total action groups", 2, allGroupsAfterExpense.size)
    }

    @Test
    fun `offline sync manager removes items once synced and protects concurrent mutations from stale completions`() {
        val manager = OfflineSyncManager(context, storage)
        val item1 = manager.enqueueUpsert(
            collection = "expenses",
            docId = "exp_clean_1",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            payloadJson = """{"id":"exp_clean_1","amount":500.0}""",
            actorName = "Staff A",
            actionGroupId = "grp_exp_1",
            actionTitle = "Expense • Fuel"
        )
        val ts1 = manager.markSyncing(item1.id)
        manager.markSynced(item1.id, expectedUpdatedAtMillis = ts1)
        assertTrue(
            "Synced item must be removed from queue so Offline Sync Queue stays hidden when synced",
            manager.queueItems.value.none { it.id == item1.id }
        )

        // Concurrent edit protection: enqueue v1, markSyncing, then enqueue v2 before v1 finishes
        val v1 = manager.enqueueUpsert(
            collection = "customers",
            docId = "cust_race_1",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            payloadJson = """{"id":"cust_race_1","currentBalance":100.0}""",
            actorName = "Staff A",
            actionGroupId = "grp_c1",
            actionTitle = "Update Customer"
        )
        val v1Ts = manager.markSyncing(v1.id)
        val v2 = manager.enqueueIncrementalUpsert(
            collection = "customers",
            docId = "cust_race_1",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            fullPayloadJson = """{"id":"cust_race_1","currentBalance":350.0}""",
            deltaFields = mapOf("currentBalance" to 250.0),
            actorName = "Staff A",
            actionGroupId = "grp_c2",
            actionTitle = "Delivery • Race Customer"
        )
        assertTrue(v2.updatedAtMillis > v1Ts)

        // Stale v1 completion must NOT remove or fail v2
        manager.markSynced(v1.id, expectedUpdatedAtMillis = v1Ts)
        val afterStaleSync = manager.queueItems.value.find { it.id == v1.id }
        assertNotNull("Newer v2 mutation must remain queued", afterStaleSync)
        assertEquals(SyncQueueStatus.PENDING, afterStaleSync!!.status)

        manager.markFailed(v1.id, "Stale timeout", expectedUpdatedAtMillis = v1Ts)
        val afterStaleFail = manager.queueItems.value.find { it.id == v1.id }
        assertEquals(SyncQueueStatus.PENDING, afterStaleFail!!.status)

        // Real failure + retryActionGroup
        val v2Ts = manager.markSyncing(v2.id)
        manager.markFailed(v2.id, "Network lost", expectedUpdatedAtMillis = v2Ts)
        assertEquals(SyncQueueStatus.FAILED, manager.queueItems.value.find { it.id == v2.id }!!.status)

        manager.retryActionGroup("grp_c2")
        assertEquals(SyncQueueStatus.PENDING, manager.queueItems.value.find { it.id == v2.id }!!.status)
    }

    @Test
    fun `firestore sync helper round-trips core delivery, customer, payment, and expense models cleanly`() {
        val cust = Customer(
            id = "cust_rt_1",
            name = "RoundTrip Shop",
            phone = "03009998877",
            currentBalance = 1450.5,
            emptyBottlesOwed = 7
        )
        val custMap = com.example.data.FirestoreSyncHelper.toFirestorePayload(
            cust, "uid_1", listOf("uid_1"), listOf("staff@example.com"), PermissionDefaults.DEFAULT_PLANT_ID
        )
        val custBack = com.example.data.FirestoreSyncHelper.fromFirestoreMap(custMap, cust.id, Customer::class.java)
        assertNotNull(custBack)
        assertEquals(1450.5, custBack!!.currentBalance, 0.001)
        assertEquals(7, custBack.emptyBottlesOwed)

        val delivery = Delivery(
            id = "del_rt_1",
            customerId = "cust_rt_1",
            customerName = "RoundTrip Shop",
            items = listOf(
                DeliveryItem(
                    bottleSizeId = "size_19l",
                    bottleName = "19 Litre Bottle",
                    quantity = 3,
                    emptyCollected = 1,
                    unitPrice = 180.0,
                    lineTotal = 540.0
                )
            ),
            totalAmount = 540.0,
            paymentCollected = 200.0,
            status = DeliveryStatus.DELIVERED
        )
        val delMap = com.example.data.FirestoreSyncHelper.toFirestorePayload(
            delivery, "uid_1", listOf("uid_1"), listOf("staff@example.com"), PermissionDefaults.DEFAULT_PLANT_ID
        )
        val delBack = com.example.data.FirestoreSyncHelper.fromFirestoreMap(delMap, delivery.id, Delivery::class.java)
        assertNotNull(delBack)
        assertEquals(540.0, delBack!!.totalAmount, 0.001)
        assertEquals(200.0, delBack.paymentCollected, 0.001)
        assertEquals(1, delBack.items.size)
        assertEquals(3, delBack.items.first().quantity)

        val expense = Expense(
            id = "exp_rt_1",
            categoryId = "cat_fuel",
            categoryName = "Fuel",
            description = "Delivery Van Diesel",
            amount = 2200.0,
            paymentMethod = PaymentMethod.CASH,
            expenseType = ExpenseType.OPEX,
            paymentSource = PaymentSource.PAY_FROM_SALE
        )
        val expMap = com.example.data.FirestoreSyncHelper.toFirestorePayload(
            expense, "uid_1", listOf("uid_1"), listOf("staff@example.com"), PermissionDefaults.DEFAULT_PLANT_ID
        )
        val expBack = com.example.data.FirestoreSyncHelper.fromFirestoreMap(expMap, expense.id, Expense::class.java)
        assertNotNull(expBack)
        assertEquals(2200.0, expBack!!.amount, 0.001)
        assertEquals(PaymentSource.PAY_FROM_SALE, expBack.paymentSource)
    }

    @Test
    fun `app update checker parses github releases, compares versions, and enforces https`() {
        assertEquals(
            "mrafiq/aab-e-baraan-app",
            com.example.data.AppUpdateChecker.normalizeGithubRepo("https://github.com/mrafiq/aab-e-baraan-app.git")
        )
        assertEquals(
            "mrafiq/aab-e-baraan-app",
            com.example.data.AppUpdateChecker.normalizeGithubRepo("mrafiq/aab-e-baraan-app/")
        )
        assertEquals("", com.example.data.AppUpdateChecker.normalizeGithubRepo("invalid"))

        assertTrue(com.example.data.AppUpdateChecker.compareSemver("1.0.2", "1.0.1") > 0)
        assertTrue(com.example.data.AppUpdateChecker.compareSemver("v2.0.0", "1.9.9") > 0)
        assertEquals(0, com.example.data.AppUpdateChecker.compareSemver("1.0.1", "v1.0.1"))

        val sampleGithubJson = """
            {
              "tag_name": "v1.0.5-105",
              "name": "Aab-e-Baraan v1.0.5",
              "body": "Faster cloud sync and bug fixes",
              "html_url": "https://github.com/mrafiq/aab-e-baraan-app/releases/tag/v1.0.5-105",
              "assets": [
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/mrafiq/aab-e-baraan-app/releases/download/v1.0.5-105/checksums.txt"
                },
                {
                  "name": "Aab-e-Baraan-v1.0.5.apk",
                  "browser_download_url": "https://github.com/mrafiq/aab-e-baraan-app/releases/download/v1.0.5-105/Aab-e-Baraan-v1.0.5.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.data.AppUpdateChecker.parseGithubReleaseJson(sampleGithubJson, "mrafiq/aab-e-baraan-app")
        assertNotNull(parsed)
        assertEquals(105, parsed!!.versionCode)
        assertEquals("1.0.5", parsed.versionName)
        assertTrue(parsed.downloadUrl.endsWith(".apk"))
        assertTrue(com.example.data.AppUpdateChecker.isUpdateNewer(parsed, currentCode = 100, currentName = "1.0.1"))
        assertFalse(com.example.data.AppUpdateChecker.isUpdateNewer(parsed, currentCode = 105, currentName = "1.0.5"))

        val insecureHttp = parsed.copy(downloadUrl = "http://example.com/app.apk")
        assertFalse(com.example.data.AppUpdateChecker.isUpdateNewer(insecureHttp, currentCode = 1, currentName = "0.0.1"))
    }

    @Test
    fun `super admin can publish cached ota release and non-admin is blocked`() = runBlocking {
        // Non-Super-Admin cannot publish
        repository.switchUserRole(UserRole.INCHARGE)
        val (deniedOk, _) = repository.publishAppRelease(
            versionCode = 999,
            versionName = "9.9.9",
            downloadUrl = "https://example.com/app.apk",
            githubRepo = "",
            releaseNotes = "Unauthorized",
            mandatory = false
        )
        assertFalse(deniedOk)

        // Super Admin can publish and HTTP URLs are rejected
        assertTrue(repository.handleGoogleAuthSuccess("uid_super_ota", "mrafiqarain1998@gmail.com", "Muhammad Rafiq"))
        val (httpOk, _) = repository.publishAppRelease(
            versionCode = 105,
            versionName = "1.0.5",
            downloadUrl = "http://insecure.example.com/app.apk",
            githubRepo = "",
            releaseNotes = "Bad URL",
            mandatory = false
        )
        assertFalse(httpOk)

        val (ok, msg) = repository.publishAppRelease(
            versionCode = com.example.BuildConfig.VERSION_CODE + 10,
            versionName = "2.0.0",
            downloadUrl = "https://github.com/mrafiq/aab-e-baraan-app/releases/latest/download/app.apk",
            githubRepo = "mrafiq/aab-e-baraan-app",
            releaseNotes = "Free 1-click update",
            mandatory = false
        )
        assertTrue("Expected publish success, got: $msg", ok)
        assertNotNull(repository.availableAppUpdate.value)
        assertEquals("2.0.0", repository.availableAppUpdate.value!!.versionName)

        // Persists across restart without needing Firestore
        WaterPlantRepository.resetInstanceForTesting()
        val freshRepo = WaterPlantRepository.getInstance(context)
        assertNotNull(freshRepo.availableAppUpdate.value)
        assertEquals("mrafiq/aab-e-baraan-app", freshRepo.appReleaseInfo.value?.githubRepo)
    }

    @Test
    fun `rtdb url config and helper key encoding, rbac maps, and secret stripping are valid`() {
        val rtdbUrl = context.getString(R.string.firebase_rtdb_url).trim()
        assertEquals("https://aab-e-baraan-ro-manager-default-rtdb.firebaseio.com", rtdbUrl)

        // Email key must match database.rules.json: auth.token.email.toLowerCase().replace('.', ',')
        assertEquals(
            "mrafiqarain1998@gmail,com",
            com.example.data.RtdbSyncHelper.emailToRtdbKey("  MRafiqArain1998@Gmail.COM ")
        )
        assertEquals("doc_a_b_c_d_e", com.example.data.RtdbSyncHelper.sanitizeKey("doc.a#b\$c[d]e"))

        val staff = UserAccount(
            id = "staff_rtdb_1",
            uid = "staff_rtdb_1",
            username = "driver_one",
            password = "SecretPassword123",
            passwordHash = "hash_abc",
            passwordSalt = "salt_xyz",
            name = "Driver One",
            role = UserRole.INCHARGE,
            permissions = mapOf(
                "DELIVERIES" to listOf("VIEW", "ADD"),
                "EXPENSES" to listOf("VIEW")
            ),
            assignedBranchIds = listOf("plant_default", "plant_north"),
            defaultBranchId = "plant_default",
            googleEmail = "driver.one@gmail.com"
        )

        val permMap = com.example.data.RtdbSyncHelper.permissionsToRtdbMap(staff.permissions)
        assertEquals(true, permMap["DELIVERIES"]?.get("VIEW"))
        assertEquals(true, permMap["DELIVERIES"]?.get("ADD"))
        assertEquals(true, permMap["EXPENSES"]?.get("VIEW"))

        val branchMap = com.example.data.RtdbSyncHelper.assignedBranchesToRtdbMap(staff.assignedBranchIds, "plant_default")
        assertEquals(true, branchMap["plant_default"])
        assertEquals(true, branchMap["plant_north"])

        val enriched = com.example.data.RtdbSyncHelper.enrichStaffMapForRtdb(
            baseMap = mapOf(
                "id" to staff.id,
                "username" to staff.username,
                "password" to "SecretPassword123",
                "passwordHash" to "hash_abc",
                "passwordSalt" to "salt_xyz"
            ),
            account = staff,
            plantId = "plant_default"
        )
        assertFalse(enriched.containsKey("password"))
        assertFalse(enriched.containsKey("passwordHash"))
        assertFalse(enriched.containsKey("passwordSalt"))
        assertEquals("plant_default", enriched["plantId"])
        assertNotNull(enriched["permissionsMap"])
        assertNotNull(enriched["assignedBranchMap"])

        val payload = com.example.data.RtdbSyncHelper.toRtdbPayload(
            data = Customer(id = "cust_rtdb_1", name = "RTDB Shop", currentBalance = 450.0),
            ownerId = "uid_admin",
            memberUids = listOf("uid_admin"),
            memberEmails = listOf("mrafiqarain1998@gmail.com"),
            plantId = "plant_default",
            useServerTimestamp = false
        )
        assertEquals("cust_rtdb_1", payload["id"])
        assertEquals("plant_default", payload["plantId"])
        assertEquals("uid_admin", payload["ownerId"])
        assertTrue((payload["updatedAtMillis"] as Number).toLong() > 0L)
        assertFalse(payload.containsKey("password"))
    }
}
