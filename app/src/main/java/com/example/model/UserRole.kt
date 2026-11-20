package com.example.model

enum class UserRole(val displayName: String, val urduTitle: String) {
    ADMIN("Admin (ایڈمن)", "ایڈمن"),
    INCHARGE("Supervisor (سپروائزر)", "سپروائزر"),
    VIEW_ONLY("View-Only (صرف دیکھنے کی اجازت)", "صرف نظارہ")
}

data class UserProfile(
    val id: String = "",
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.ADMIN,
    val isSuperAdmin: Boolean = false,
    val permissions: Map<String, List<String>> = emptyMap(),
    val assignedBranchIds: List<String> = listOf(PermissionDefaults.DEFAULT_PLANT_ID),
    val defaultBranchId: String = PermissionDefaults.DEFAULT_PLANT_ID,
    val businessId: String = PermissionDefaults.DEFAULT_PLANT_ID,
    val active: Boolean = true,
    val restrictToOwnDataOnly: Boolean = false,
    val restrictToTodayOnly: Boolean = false
)

data class UserAccount(
    val id: String = "",
    val uid: String = "",
    val username: String = "",
    val password: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val name: String = "",
    val phone: String = "",
    val role: UserRole = UserRole.INCHARGE,
    val isSuperAdmin: Boolean = false,
    val permissions: Map<String, List<String>> = emptyMap(),
    val assignedBranchIds: List<String> = listOf(PermissionDefaults.DEFAULT_PLANT_ID),
    val defaultBranchId: String = PermissionDefaults.DEFAULT_PLANT_ID,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val googleEmail: String = "",
    val passwordUpdatedAtMillis: Long = 0L,
    val restrictToOwnDataOnly: Boolean = false,
    val restrictToTodayOnly: Boolean = false
)

data class PasswordResetRequest(
    val id: String = "",
    val username: String = "",
    val fullName: String = "",
    val role: UserRole = UserRole.INCHARGE,
    val requestTime: String = "",
    val reason: String = "Forgot Password / لاگ ان مسئلہ",
    val isResolved: Boolean = false,
    val resolvedAt: String = "",
    val resolvedBy: String = "",
    val newPasswordSet: String = ""
)
