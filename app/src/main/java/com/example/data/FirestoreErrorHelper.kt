package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun friendlyFirestoreStatusMessage(exception: Throwable): String {
    val causes = generateSequence(exception) { it.cause }
    val fsEx = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    val msg = (fsEx?.message ?: exception.message ?: "").lowercase()
    return when (fsEx?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Permission Denied: Sign in with Google / Check Database Rules"
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Cloud Database Not Found on Firebase Project"
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Offline Mode (Auto-syncs when online)"
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Cloud Quota Exceeded"
        else -> when {
            msg.contains("permission") -> "Permission Denied: Publish RTDB Rules in Firebase Console"
            msg.contains("disconnected") || msg.contains("network") || msg.contains("unavailable") ->
                "Offline Mode (Auto-syncs when online)"
            msg.contains("not_found") || msg.contains("database") -> "Realtime DB URL Not Found"
            msg.contains("quota") -> "Cloud Quota Exceeded"
            else -> "Cloud Sync Pending (${fsEx?.code?.name ?: "Offline"})"
        }
    }
}
