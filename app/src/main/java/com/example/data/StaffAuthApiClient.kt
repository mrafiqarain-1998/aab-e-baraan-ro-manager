package com.example.data

import android.os.Build
import com.example.BuildConfig
import com.example.model.PermissionDefaults
import com.example.model.UserAccount
import com.example.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class StaffAuthResult(
    val ok: Boolean = false,
    val customToken: String = "",
    val uid: String = "",
    val plantId: String = PermissionDefaults.DEFAULT_PLANT_ID,
    val account: UserAccount? = null,
    val errorCode: String = "",
    val message: String = ""
)

class StaffAuthApiClient {

    @Volatile
    private var overrideBaseUrl: String = ""

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun setOverrideBaseUrl(url: String) {
        overrideBaseUrl = url.trim().trimEnd('/')
    }

    fun getEffectiveBaseUrl(): String {
        val override = overrideBaseUrl.trim().trimEnd('/')
        if (override.isNotEmpty()) return override
        return try {
            BuildConfig.STAFF_AUTH_API_URL.trim().trimEnd('/')
        } catch (_: Exception) {
            ""
        }
    }

    fun isConfigured(): Boolean {
        val base = getEffectiveBaseUrl()
        return base.startsWith("https://") || base.startsWith("http://")
    }

    private fun isRobolectricWithoutOverride(): Boolean {
        val isRobolectric = try {
            Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
        return isRobolectric && overrideBaseUrl.isBlank()
    }

    suspend fun checkHealth(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val base = getEffectiveBaseUrl()
        if (base.isBlank()) {
            return@withContext false to "Backend URL is not configured (leave blank to use Google Sign-In on Spark plan)."
        }
        try {
            val req = Request.Builder()
                .url("$base/health")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = runCatching { JSONObject(body) }.getOrNull()
                    val db = json?.optString("databaseId").orEmpty()
                    val project = json?.optString("projectId").orEmpty()
                    true to "Connected ($project${if (db.isNotBlank()) ", db=$db" else ""})"
                } else if (resp.code == 404) {
                    false to "Cloud Functions endpoint not deployed (HTTP 404). Google Sign-In & Firestore sync work natively on Spark plan; Cloud Functions require Blaze."
                } else {
                    false to "Health check failed (HTTP ${resp.code}): ${extractMessage(body)}"
                }
            }
        } catch (e: Exception) {
            false to "Backend unreachable: ${e.localizedMessage ?: "Network error"}"
        }
    }

    suspend fun staffLogin(
        username: String,
        password: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE", message = "Offline test environment")
        }
        val payload = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("deviceInfo", deviceInfo)
        }
        return postJson("/staff/login", null, payload)
    }

    suspend fun bootstrapAdmin(
        idToken: String,
        plantId: String,
        plantName: String,
        name: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = JSONObject().apply {
            put("plantId", plantId)
            put("plantName", plantName)
            put("name", name)
        }
        return postJson("/admin/bootstrap", idToken, payload)
    }

    suspend fun createStaff(
        idToken: String,
        account: UserAccount,
        rawPassword: String,
        plantId: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = accountToJson(account, plantId, deviceInfo).apply {
            put("password", rawPassword)
        }
        return postJson("/staff/create", idToken, payload)
    }

    suspend fun updateStaff(
        idToken: String,
        account: UserAccount,
        plantId: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = accountToJson(account, plantId, deviceInfo)
        return postJson("/staff/update", idToken, payload)
    }

    suspend fun resetStaffPassword(
        idToken: String,
        uid: String,
        newPassword: String,
        plantId: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = JSONObject().apply {
            put("uid", uid)
            put("newPassword", newPassword)
            put("plantId", plantId)
            put("deviceInfo", deviceInfo)
        }
        return postJson("/staff/reset-password", idToken, payload)
    }

    suspend fun deleteStaff(
        idToken: String,
        uid: String,
        plantId: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = JSONObject().apply {
            put("uid", uid)
            put("plantId", plantId)
            put("deviceInfo", deviceInfo)
        }
        return postJson("/staff/delete", idToken, payload)
    }

    suspend fun toggleStaffActive(
        idToken: String,
        uid: String,
        active: Boolean,
        plantId: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = JSONObject().apply {
            put("uid", uid)
            put("active", active)
            put("plantId", plantId)
            put("deviceInfo", deviceInfo)
        }
        return postJson("/staff/toggle-active", idToken, payload)
    }

    suspend fun changeOwnPassword(
        idToken: String,
        currentPassword: String,
        newPassword: String,
        deviceInfo: String
    ): StaffAuthResult {
        if (isRobolectricWithoutOverride()) {
            return StaffAuthResult(ok = false, errorCode = "BACKEND_UNAVAILABLE")
        }
        val payload = JSONObject().apply {
            put("currentPassword", currentPassword)
            put("newPassword", newPassword)
            put("deviceInfo", deviceInfo)
        }
        return postJson("/staff/change-password", idToken, payload)
    }

    private suspend fun postJson(
        path: String,
        idToken: String?,
        bodyJson: JSONObject
    ): StaffAuthResult = withContext(Dispatchers.IO) {
        val base = getEffectiveBaseUrl()
        if (base.isBlank()) {
            return@withContext StaffAuthResult(ok = false, errorCode = "NOT_CONFIGURED", message = "Backend URL not configured")
        }
        try {
            val reqBuilder = Request.Builder()
                .url("$base$path")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
            if (!idToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $idToken")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                if (resp.isSuccessful && json != null) {
                    val ok = json.optBoolean("ok", true)
                    val parsedAccount = json.optJSONObject("account")?.let { parseAccount(it) }
                    val uid = json.optString("uid", parsedAccount?.uid.orEmpty())
                    val plantId = json.optString(
                        "plantId",
                        parsedAccount?.defaultBranchId ?: PermissionDefaults.DEFAULT_PLANT_ID
                    ).ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
                    StaffAuthResult(
                        ok = ok,
                        customToken = json.optString("customToken", ""),
                        uid = uid,
                        plantId = plantId,
                        account = parsedAccount,
                        errorCode = json.optString("errorCode", ""),
                        message = json.optString("message", "")
                    )
                } else {
                    val errCode = json?.optString("errorCode").orEmpty().ifBlank {
                        if (resp.code == 404 || resp.code >= 500) "BACKEND_UNAVAILABLE" else "HTTP_${resp.code}"
                    }
                    val msg = json?.optString("message").orEmpty().ifBlank { extractMessage(raw) }
                    StaffAuthResult(
                        ok = false,
                        errorCode = errCode,
                        message = msg
                    )
                }
            }
        } catch (e: Exception) {
            StaffAuthResult(
                ok = false,
                errorCode = "BACKEND_UNAVAILABLE",
                message = e.localizedMessage ?: "Network error"
            )
        }
    }

    private fun accountToJson(
        account: UserAccount,
        plantId: String,
        deviceInfo: String
    ): JSONObject {
        val permsObj = JSONObject()
        account.permissions.forEach { (mod, actions) ->
            permsObj.put(mod, JSONArray(actions))
        }
        return JSONObject().apply {
            put("id", account.id)
            put("uid", account.uid.ifBlank { account.id })
            put("username", account.username)
            put("name", account.name)
            put("phone", account.phone)
            put("googleEmail", account.googleEmail)
            put("role", account.role.name)
            put("isSuperAdmin", account.isSuperAdmin)
            put("active", account.active)
            put("permissions", permsObj)
            put("assignedBranchIds", JSONArray(account.assignedBranchIds))
            put("defaultBranchId", account.defaultBranchId.ifBlank { plantId })
            put("plantId", plantId.ifBlank { PermissionDefaults.DEFAULT_PLANT_ID })
            put("deviceInfo", deviceInfo)
        }
    }

    private fun parseAccount(obj: JSONObject): UserAccount? {
        return try {
            val roleStr = obj.optString("role", "INCHARGE")
            val role = runCatching { UserRole.valueOf(roleStr.uppercase()) }.getOrDefault(UserRole.INCHARGE)
            val permsObj = obj.optJSONObject("permissions")
            val permsMap = linkedMapOf<String, List<String>>()
            if (permsObj != null) {
                val keys = permsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = permsObj.optJSONArray(k) ?: continue
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        arr.optString(i)?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                    }
                    permsMap[k] = list
                }
            }
            val branchesArr = obj.optJSONArray("assignedBranchIds")
            val branches = mutableListOf<String>()
            if (branchesArr != null) {
                for (i in 0 until branchesArr.length()) {
                    branchesArr.optString(i)?.takeIf { it.isNotBlank() }?.let { branches.add(it) }
                }
            }
            val plantId = obj.optString("plantId", PermissionDefaults.DEFAULT_PLANT_ID)
                .ifBlank { PermissionDefaults.DEFAULT_PLANT_ID }
            val defBranch = obj.optString("defaultBranchId", plantId).ifBlank { plantId }
            val uid = obj.optString("uid", "").ifBlank { obj.optString("id", "") }
            val id = obj.optString("id", "").ifBlank { uid }
            UserAccount(
                id = id,
                uid = uid,
                username = obj.optString("username", ""),
                name = obj.optString("name", ""),
                phone = obj.optString("phone", ""),
                googleEmail = obj.optString("googleEmail", ""),
                role = role,
                isSuperAdmin = obj.optBoolean("isSuperAdmin", false),
                permissions = PermissionDefaults.normalizePermissions(permsMap, role, obj.optBoolean("isSuperAdmin", false)),
                assignedBranchIds = branches.ifEmpty { listOf(defBranch) },
                defaultBranchId = defBranch,
                active = obj.optBoolean("active", true)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractMessage(raw: String): String {
        return raw.trim().take(160).ifBlank { "Unexpected response" }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
