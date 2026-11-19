package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FirestoreSyncHelper
import com.example.data.OfflineSyncManager
import com.example.data.PlantDataStorage
import com.example.data.WaterPlantRepository
import com.example.model.*
import com.example.util.PasswordHashUtil
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnterpriseUpgradeTest {

    private lateinit var context: Context
    private lateinit var storage: PlantDataStorage
    private lateinit var repository: WaterPlantRepository

    @Before
    fun setUp() {
        WaterPlantRepository.resetInstanceForTesting()
        context = ApplicationProvider.getApplicationContext()
        // Ensure clean filesDir between test cases
        listOf(
            "db_user_accounts.json",
            "db_sync_queue.json",
            "db_sync_conflicts.json",
            "db_audit_logs.json",
            "db_plant_branches.json",
            "db_active_plant_id.txt",
            "db_session_login.txt"
        ).forEach { File(context.filesDir, it).delete() }

        storage = PlantDataStorage(context)
        repository = WaterPlantRepository.getInstance(context)
        repository.switchUserRole(UserRole.ADMIN)
    }

    @Test
    fun `salted PBKDF2 hashing strips plaintext passwords from disk and survives restart`() {
        val createdId = repository.createUserAccount(
            UserAccount(
                username = "bilal_plant",
                password = "StrongPass#2026",
                name = "Bilal Operator",
                role = UserRole.INCHARGE
            )
        )
        assertTrue(createdId.isNotBlank())

        // Verify disk file does NOT contain plaintext password anywhere
        val rawAccountsJson = File(context.filesDir, "db_user_accounts.json").readText()
        assertFalse(rawAccountsJson.contains("StrongPass#2026"))

        val savedAccounts = storage.loadUserAccounts().orEmpty()
        val bilal = savedAccounts.find { it.username == "bilal_plant" }
        assertNotNull(bilal)
        assertEquals("", bilal!!.password)
        assertTrue(bilal.passwordSalt.isNotBlank())
        assertTrue(bilal.passwordHash.isNotBlank())
        assertTrue(PasswordHashUtil.verifyPassword("StrongPass#2026", bilal.passwordSalt, bilal.passwordHash))
        assertFalse(PasswordHashUtil.verifyPassword("WrongPassword", bilal.passwordSalt, bilal.passwordHash))

        // Simulate app restart and authenticate
        WaterPlantRepository.resetInstanceForTesting()
        val restartedRepo = WaterPlantRepository.getInstance(context)
        val loggedIn = restartedRepo.authenticate("bilal_plant", "StrongPass#2026")
        assertNotNull(loggedIn)
        assertEquals("Bilal Operator", loggedIn!!.name)
        assertNull(restartedRepo.authenticate("bilal_plant", "WrongPassword"))
    }

    @Test
    fun `granular RBAC matrix enforces custom permissions and protects super admin`() {
        val customPermissions = PermissionDefaults.defaultPermissionsForRole(UserRole.INCHARGE).toMutableMap().apply {
            this[AppModule.DELIVERIES.name] = listOf(PermissionAction.VIEW.name, PermissionAction.ADD.name, PermissionAction.EDIT.name)
            this[AppModule.EXPENSES.name] = listOf(PermissionAction.VIEW.name) // Explicitly disallow ADD
            this[AppModule.REPORTS.name] = listOf(PermissionAction.VIEW.name, PermissionAction.MANAGE.name)
        }
        val staffId = repository.createUserAccount(
            UserAccount(
                username = "zainab_desk",
                password = "DeskPass#123",
                name = "Zainab Desk",
                role = UserRole.INCHARGE,
                permissions = customPermissions
            )
        )
        assertTrue(staffId.isNotBlank())

        // Attempt to deactivate or delete primary super admin must fail
        val toggleRes = repository.toggleUserAccountActive("user_admin_01")
        assertFalse(toggleRes.first)
        val deleteRes = repository.deleteUserAccount("user_admin_01")
        assertFalse(deleteRes.first)

        // Attempt to demote primary admin keeps ADMIN role & active=true
        val adminAcc = repository.userAccounts.value.first { it.id == "user_admin_01" }
        repository.updateUserAccount(adminAcc.copy(role = UserRole.VIEW_ONLY, active = false))
        val afterAdmin = repository.userAccounts.value.first { it.id == "user_admin_01" }
        assertEquals(UserRole.ADMIN, afterAdmin.role)
        assertTrue(afterAdmin.active)
        assertTrue(afterAdmin.isSuperAdmin)

        // Log in as Zainab and validate granular module/action checks
        val zainabProfile = repository.authenticate("zainab_desk", "DeskPass#123")
        assertNotNull(zainabProfile)
        assertTrue(repository.hasPermission(AppModule.DELIVERIES, PermissionAction.EDIT))
        assertTrue(repository.hasPermission(AppModule.REPORTS, PermissionAction.MANAGE))
        assertFalse(repository.hasPermission(AppModule.EXPENSES, PermissionAction.ADD))
        assertFalse(repository.hasPermission(AppModule.STAFF_MANAGEMENT, PermissionAction.VIEW))
    }

    @Test
    fun `offline sync queue persists across restart and tracks backoff and conflicts`() {
        var restoredCallbackHits = 0
        val manager = OfflineSyncManager(context, storage) {
            restoredCallbackHits++
        }

        val queued = manager.enqueueUpsert(
            collection = "deliveries",
            docId = "del_1001",
            plantId = PermissionDefaults.DEFAULT_PLANT_ID,
            payloadJson = """{"id":"del_1001","totalAmount":650.0}""",
            actorName = "Admin",
            baseRemoteUpdatedAtMillis = 1_700_000_000_000L
        )
        assertEquals(SyncQueueStatus.PENDING, queued.status)
        assertTrue(manager.hasPendingOrFailedMutation("deliveries", "del_1001"))

        manager.markFailed(queued.id, "Network timeout")
        val afterFail = manager.queueItems.value.first { it.id == queued.id }
        assertEquals(SyncQueueStatus.FAILED, afterFail.status)
        assertEquals(1, afterFail.attemptCount)
        assertTrue(afterFail.nextRetryAtMillis > System.currentTimeMillis())

        // Recreate OfflineSyncManager simulating cold restart
        manager.release()
        val restartedManager = OfflineSyncManager(context, storage) {
            restoredCallbackHits++
        }
        val persisted = restartedManager.queueItems.value.find { it.id == queued.id }
        assertNotNull(persisted)
        assertEquals(SyncQueueStatus.FAILED, persisted!!.status)
        assertEquals("Network timeout", persisted.lastError)

        // Force retry resets status to PENDING and clears retry delay
        restartedManager.retryItemNow(queued.id)
        val pendingAgain = restartedManager.getPendingQueueItems()
        assertTrue(pendingAgain.any { it.id == queued.id && it.status == SyncQueueStatus.PENDING })

        restartedManager.setNetworkStateForTesting(true)
        assertTrue(restoredCallbackHits >= 1)

        // Conflict recording & resolution
        val conflict = SyncConflictRecord(
            id = "conf_1",
            collection = "deliveries",
            docId = "del_1001",
            recordLabel = "DEL-1001",
            localSummary = "Edited locally",
            remoteSummary = "Updated remotely at plant",
            resolution = "Cloud state retained"
        )
        restartedManager.recordConflict(conflict)
        assertEquals(1, restartedManager.conflicts.value.count { !it.resolved })
        restartedManager.markConflictResolved("conf_1")
        assertEquals(0, restartedManager.conflicts.value.count { !it.resolved })
        restartedManager.release()
    }

    @Test
    fun `firestore payload injects plantId and strips all secret credential keys`() {
        val account = UserAccount(
            id = "u_staff_1",
            uid = "u_staff_1",
            username = "tariq",
            password = "RawSecretPassword!",
            passwordHash = "pbkdf2\$deadbeef",
            passwordSalt = "randomsalt",
            name = "Tariq Driver",
            role = UserRole.INCHARGE
        )
        val payload = FirestoreSyncHelper.toFirestorePayload(
            data = account,
            ownerId = "owner_uid",
            memberUids = listOf("u_staff_1"),
            memberEmails = listOf("mrafiqarain1998@gmail.com"),
            plantId = "lahore_branch_02"
        )
        assertEquals("lahore_branch_02", payload["plantId"])
        assertEquals("owner_uid", payload["ownerId"])
        assertFalse(payload.containsKey("password"))
        assertFalse(payload.containsKey("passwordHash"))
        assertFalse(payload.containsKey("passwordSalt"))
        assertFalse(payload.containsKey("salt"))
    }

    @Test
    fun `audit trail records logins, staff mutations, and conflict resolution`() {
        val staffId = repository.createUserAccount(
            UserAccount(
                username = "audit_tester",
                password = "AuditPass#99",
                name = "Audit Tester",
                role = UserRole.INCHARGE
            )
        )
        repository.resetUserPassword(staffId, "AuditPass#100")
        repository.authenticate("audit_tester", "AuditPass#100")
        repository.resolveSyncConflict("conf_test_1", "Kept latest cloud total")

        val logs = repository.auditLogs.value
        assertTrue(logs.any { it.action == AuditActionType.CREATE.name && it.module == AppModule.STAFF_MANAGEMENT.name })
        assertTrue(logs.any { it.action == AuditActionType.PASSWORD_RESET.name && it.module == AppModule.STAFF_MANAGEMENT.name })
        assertTrue(logs.any { it.action == AuditActionType.LOGIN.name && it.actorUsername == "audit_tester" })
        assertTrue(logs.any { it.action == AuditActionType.SYNC.name && it.recordId == "conf_test_1" })
    }

    @Test
    fun `fresh install login state defaults to logged out`() {
        assertFalse(repository.isUserLoggedIn())
    }
}
