package com.example.data

import com.example.model.UserAccount
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

/**
 * Helper for Firebase Realtime Database (RTDB) key encoding, payload preparation,
 * and DataSnapshot <-> Moshi model deserialization.
 *
 * RTDB Spark free tier provides UNLIMITED daily reads/writes (10 GB/month download,
 * 1 GB storage, 100 simultaneous connections) on the same Firebase project.
 */
object RtdbSyncHelper {

    private val FORBIDDEN_KEY_CHARS = Regex("""[.#$\[\]/]""")

    /**
     * Converts an email address into an RTDB-safe key matching database.rules.json:
     * `auth.token.email.toLowerCase().replace('.', ',')`
     */
    fun emailToRtdbKey(email: String): String {
        return email.trim()
            .lowercase()
            .replace(".", ",")
            .replace(Regex("""[#$\[\]/]"""), "_")
    }

    /**
     * Ensures any document/node key is valid for Firebase Realtime Database.
     */
    fun sanitizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return "doc_empty"
        return trimmed.replace(FORBIDDEN_KEY_CHARS, "_")
    }

    /**
     * Converts a Map<String, List<String>> permission matrix into O(1) lookup booleans
     * for RTDB Security Rules (`permissionsMap/$module/$action === true`).
     */
    fun permissionsToRtdbMap(permissions: Map<String, List<String>>): Map<String, Map<String, Boolean>> {
        val out = linkedMapOf<String, Map<String, Boolean>>()
        permissions.forEach { (module, actions) ->
            val modKey = sanitizeKey(module)
            val actMap = linkedMapOf<String, Boolean>()
            actions.forEach { act ->
                if (act.isNotBlank()) {
                    actMap[sanitizeKey(act)] = true
                }
            }
            if (actMap.isNotEmpty()) {
                out[modKey] = actMap
            }
        }
        return out
    }

    /**
     * Converts assigned branch list into O(1) lookup booleans
     * for RTDB Security Rules (`assignedBranchMap/$plantId === true`).
     */
    fun assignedBranchesToRtdbMap(branchIds: List<String>, fallbackPlantId: String): Map<String, Boolean> {
        val out = linkedMapOf<String, Boolean>()
        branchIds.forEach { id ->
            if (id.isNotBlank()) out[sanitizeKey(id)] = true
        }
        if (fallbackPlantId.isNotBlank()) {
            out[sanitizeKey(fallbackPlantId)] = true
        }
        return out
    }

    /**
     * Builds an RTDB-safe payload map from any data class or map, stripping secret credential keys
     * and stamping plantId, ownership metadata, and server timestamp.
     */
    fun toRtdbPayload(
        data: Any,
        ownerId: String,
        memberUids: List<String>,
        memberEmails: List<String>,
        plantId: String,
        useServerTimestamp: Boolean = true,
        lastModifiedByUid: String = ""
    ): MutableMap<String, Any?> {
        val json = FirestoreSyncHelper.toPlainJsonString(data)
        val rawMap = OfflineSyncManager.mapFromPayloadJson(json) ?: linkedMapOf()
        val sanitized = sanitizeMapKeys(rawMap)
        sanitized.remove("password")
        sanitized.remove("passwordHash")
        sanitized.remove("passwordSalt")
        sanitized.remove("newPasswordSet")
        sanitized["plantId"] = plantId
        sanitized["ownerId"] = ownerId
        sanitized["memberUids"] = memberUids.distinct()
        sanitized["memberEmails"] = memberEmails.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        if (lastModifiedByUid.isNotBlank()) {
            sanitized["lastModifiedByUid"] = lastModifiedByUid
        }
        val now = System.currentTimeMillis()
        sanitized["updatedAtMillis"] = now
        if (useServerTimestamp) {
            sanitized["updatedAt"] = ServerValue.TIMESTAMP
        } else {
            sanitized["updatedAt"] = now
        }
        return sanitized
    }

    /**
     * Enriches a sanitized staff account map with `permissionsMap` and `assignedBranchMap`
     * so Firebase Realtime Database Security Rules can validate RBAC and plant access in O(1).
     */
    fun enrichStaffMapForRtdb(
        baseMap: Map<String, Any?>,
        account: UserAccount,
        plantId: String
    ): MutableMap<String, Any?> {
        val copy = sanitizeMapKeys(baseMap)
        copy.remove("password")
        copy.remove("passwordHash")
        copy.remove("passwordSalt")
        copy["plantId"] = plantId
        copy["permissionsMap"] = permissionsToRtdbMap(account.permissions)
        copy["assignedBranchMap"] = assignedBranchesToRtdbMap(account.assignedBranchIds, plantId)
        return copy
    }

    /**
     * Recursively converts any Map keys to RTDB-safe strings and strips nulls/empty containers cleanly.
     */
    fun sanitizeMapKeys(input: Map<*, *>): MutableMap<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        input.forEach { (k, v) ->
            val keyStr = k?.toString() ?: return@forEach
            val safeKey = sanitizeKey(keyStr)
            val cleanVal = sanitizeValue(v)
            if (cleanVal != null) {
                result[safeKey] = cleanVal
            }
        }
        return result
    }

    private fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> sanitizeMapKeys(value)
            is List<*> -> value.mapNotNull { sanitizeValue(it) }
            is Enum<*> -> value.name
            is String, is Boolean, is Int, is Long, is Double, is Float -> value
            else -> value.toString()
        }
    }

    /**
     * Converts an RTDB DataSnapshot node into a plain Kotlin Map<String, Any?>
     * suitable for Moshi deserialization via FirestoreSyncHelper.fromFirestoreMap.
     */
    fun snapshotToMap(snapshot: DataSnapshot): Map<String, Any?>? {
        val raw = snapshot.value
        if (raw is Map<*, *>) {
            val out = linkedMapOf<String, Any?>()
            raw.forEach { (k, v) ->
                if (k != null) out[k.toString()] = normalizeRtdbValue(v)
            }
            return out
        }
        return null
    }

    private fun normalizeRtdbValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> {
                val out = linkedMapOf<String, Any?>()
                value.forEach { (k, v) ->
                    if (k != null) out[k.toString()] = normalizeRtdbValue(v)
                }
                out
            }
            is List<*> -> value.filterNotNull().map { normalizeRtdbValue(it) }
            else -> value
        }
    }

    fun <T> fromDataSnapshot(snapshot: DataSnapshot, clazz: Class<T>): T? {
        val map = snapshotToMap(snapshot) ?: return null
        val docId = snapshot.key.orEmpty()
        return FirestoreSyncHelper.fromFirestoreMap(map, docId, clazz)
    }

    fun extractUpdatedAtMillis(snapshot: DataSnapshot): Long {
        val direct = (snapshot.child("updatedAtMillis").value as? Number)?.toLong()
        if (direct != null && direct > 0L) return direct
        return (snapshot.child("updatedAt").value as? Number)?.toLong() ?: 0L
    }
}
