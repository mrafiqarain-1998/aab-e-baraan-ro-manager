package com.example.model

enum class NotificationSeverity { INFO, SUCCESS, WARNING, ALERT }

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val timeAgo: String = "",
    val severity: NotificationSeverity = NotificationSeverity.INFO,
    val isRead: Boolean = false,
    val targetRole: UserRole? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class BusinessProfile(
    val id: String = "aab_e_baraan_plant_01",
    val businessName: String = "Aab e Baraan RO Water Plant",
    val tagline: String = "Pure Mineral Drinking Water Service",
    val phone: String = "+92 300 1234567",
    val whatsapp: String = "+92 300 1234567",
    val address: String = "Plot 42, Water Works Road, Industrial Area",
    val city: String = "Lahore, Pakistan",
    val currency: String = "Rs.",
    val tdsTarget: String = "120 - 150 PPM",
    val phTarget: String = "7.2 - 7.6 pH"
)
