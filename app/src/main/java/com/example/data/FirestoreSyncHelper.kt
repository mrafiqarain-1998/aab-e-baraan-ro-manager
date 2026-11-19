package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject

object FirestoreSyncHelper {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Suppress("UNCHECKED_CAST")
    fun toFirestorePayload(
        data: Any,
        ownerId: String,
        memberUids: List<String>,
        memberEmails: List<String>,
        plantId: String = com.example.model.PermissionDefaults.DEFAULT_PLANT_ID
    ): MutableMap<String, Any?> {
        val rawMap: MutableMap<String, Any?> = when (data) {
            is Map<*, *> -> {
                val out = linkedMapOf<String, Any?>()
                data.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    if (v != null && key !in SECRET_KEYS) {
                        out[key] = sanitizeAny(v)
                    }
                }
                out
            }
            else -> {
                try {
                    val adapter = moshi.adapter<Any>(data.javaClass)
                    val jsonStr = adapter.toJson(data)
                    jsonObjectToMap(JSONObject(jsonStr)).apply {
                        SECRET_KEYS.forEach { remove(it) }
                    }
                } catch (e: Exception) {
                    Log.e("FirestoreSyncHelper", "Failed to serialize ${data.javaClass.simpleName}: ${e.message}", e)
                    linkedMapOf()
                }
            }
        }

        val cleanMemberUids = (listOf(ownerId) + memberUids)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(25)

        val cleanMemberEmails = memberEmails
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(25)

        rawMap["ownerId"] = ownerId
        rawMap["plantId"] = plantId.ifBlank { com.example.model.PermissionDefaults.DEFAULT_PLANT_ID }
        rawMap["memberUids"] = cleanMemberUids
        rawMap["memberEmails"] = cleanMemberEmails
        rawMap["updatedAt"] = FieldValue.serverTimestamp()

        return rawMap.filterValues { it != null }.toMutableMap()
    }

    fun toPlainJsonString(data: Any): String {
        val map = when (data) {
            is Map<*, *> -> {
                val out = linkedMapOf<String, Any?>()
                data.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    if (v != null && key !in SECRET_KEYS && v !is FieldValue) {
                        out[key] = sanitizeAny(v)
                    }
                }
                out
            }
            else -> {
                try {
                    val adapter = moshi.adapter<Any>(data.javaClass)
                    val jsonStr = adapter.toJson(data)
                    jsonObjectToMap(JSONObject(jsonStr)).apply {
                        SECRET_KEYS.forEach { remove(it) }
                    }
                } catch (_: Exception) {
                    emptyMap()
                }
            }
        }
        return mapToJsonObject(map).toString()
    }

    private val SECRET_KEYS = setOf("password", "passwordHash", "passwordSalt", "salt")

    fun <T> fromFirestoreDocument(doc: DocumentSnapshot, clazz: Class<T>): T? {
        val raw = doc.data ?: return null
        return fromFirestoreMap(raw, doc.id, clazz)
    }

    fun <T> fromFirestoreMap(raw: Map<String, Any?>, docId: String, clazz: Class<T>): T? {
        return try {
            val cleaned = linkedMapOf<String, Any?>()
            raw.forEach { (k, v) ->
                if (k != "ownerId" && k != "memberUids" && k != "memberEmails" && k != "updatedAt" && v != null) {
                    cleaned[k] = firestoreValueToJsonValue(v)
                }
            }
            val existingId = (cleaned["id"] as? String)?.trim()
            if (existingId.isNullOrEmpty()) {
                cleaned["id"] = docId
            }
            val jsonObj = mapToJsonObject(cleaned)
            moshi.adapter(clazz).fromJson(jsonObj.toString())
        } catch (e: Exception) {
            Log.w("FirestoreSyncHelper", "Failed to deserialize ${clazz.simpleName} ($docId): ${e.message}")
            null
        }
    }

    private fun sanitizeAny(value: Any?): Any? {
        return when (value) {
            null -> null
            is Enum<*> -> value.name
            is FieldValue, is Timestamp -> value
            is Map<*, *> -> {
                val out = linkedMapOf<String, Any?>()
                value.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    val sanitized = sanitizeAny(v)
                    if (sanitized != null) out[key] = sanitized
                }
                out
            }
            is Iterable<*> -> value.mapNotNull { sanitizeAny(it) }
            is Double -> if (value.isFinite()) value else 0.0
            is Float -> if (value.isFinite()) value.toDouble() else 0.0
            else -> value
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): MutableMap<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val v = obj.opt(key)
            if (v != null && v != JSONObject.NULL) {
                map[key] = jsonValueToKotlin(v)
            }
        }
        return map
    }

    private fun jsonValueToKotlin(value: Any): Any? {
        return when (value) {
            JSONObject.NULL -> null
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length()) {
                    val elem = value.opt(i)
                    if (elem != null && elem != JSONObject.NULL) {
                        list.add(jsonValueToKotlin(elem))
                    }
                }
                list
            }
            else -> value
        }
    }

    private fun firestoreValueToJsonValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Timestamp -> value.toDate().time
            is Map<*, *> -> {
                val out = linkedMapOf<String, Any?>()
                value.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    if (v != null) out[key] = firestoreValueToJsonValue(v)
                }
                out
            }
            is Iterable<*> -> value.mapNotNull { firestoreValueToJsonValue(it) }
            else -> value
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    obj.put(k, mapToJsonObject(v as Map<String, Any?>))
                }
                is Iterable<*> -> obj.put(k, listToJsonArray(v))
                else -> obj.put(k, v)
            }
        }
        return obj
    }

    private fun listToJsonArray(list: Iterable<*>): JSONArray {
        val arr = JSONArray()
        list.forEach { item ->
            when (item) {
                null -> Unit
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    arr.put(mapToJsonObject(item as Map<String, Any?>))
                }
                is Iterable<*> -> arr.put(listToJsonArray(item))
                else -> arr.put(item)
            }
        }
        return arr
    }
}
