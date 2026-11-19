package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// ==========================================
// Aab-e-Baraan Official Typography & Fonts
// ==========================================

// 1. Plus Jakarta Sans with true weight files (crisp & bold mobile hierarchy)
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_500, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_600, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_600, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_700, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_800, FontWeight.ExtraBold)
)

// 2. Noto Nastaliq Urdu (Urdu Invoices, Receipts, Customer Notes, Ledgers)
val NotoNastaliqUrdu = FontFamily(
    Font(R.font.noto_nastaliq_urdu, FontWeight.Normal),
    Font(R.font.noto_nastaliq_urdu_700, FontWeight.Medium),
    Font(R.font.noto_nastaliq_urdu_700, FontWeight.SemiBold),
    Font(R.font.noto_nastaliq_urdu_700, FontWeight.Bold)
)

// 3. Compact & Bold Design System Size Hierarchy Helpers
// Micro (9sp) - باریک ٹائم اسٹیمپ، سب ٹیگز
val TextMicro = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    color = TextMuted
)

// Caption (9.5sp) - کسٹمر کوڈز (CUST-001), علاقہ کا سب ٹیکسٹ
val TextCaption = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 9.5.sp,
    lineHeight = 13.sp,
    color = TextSecondaryDark
)

// Compact (10sp) - ٹیبلز کے ڈیٹا سیلز، فون نمبرز، فارم لیبلز
val TextCompact = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    color = TextPrimaryDark
)

// Body Small (11sp) - کارڈز کا عمومی مواد، بٹنوں کا ٹیکسٹ، خلاصہ
val TextBodySmall = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    color = TextPrimaryDark
)

// Body (12sp) - سیکشن ہیڈنگز، کسٹمر کا نام، کل رقم (Grand Total)
val TextBody = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = TextPrimaryDark
)

// Heading (13.5sp) - اسکرین کے مین ٹائٹلز، ماڈل (Popups) کی ہیڈنگز
val TextHeading = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.Bold,
    fontSize = 13.5.sp,
    lineHeight = 18.sp,
    color = TextPrimaryDark
)

// Display (15sp - 18sp) - کیش دراز کے ٹوٹل اور ڈیش بورڈ کی بڑی رقوم
val TextDisplay = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    color = TextPrimaryDark
)

// Urdu Specific Style Helper (رسیدوں اور اردو نوٹس کے لیے نستعلیق رسم الخط)
val UrduReceiptStyle = TextStyle(
    fontFamily = NotoNastaliqUrdu,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 20.sp,
    color = TextPrimaryDark
)

// 4. Material 3 Typography Mapping (Compact & Bold across all unstyled M3 widgets)
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimaryDark
    ),
    displayMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimaryDark
    ),
    displaySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TextPrimaryDark
    ),
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TextPrimaryDark
    ),
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TextPrimaryDark
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        color = TextPrimaryDark
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TextPrimaryDark
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        color = TextPrimaryDark
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = TextPrimaryDark
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextPrimaryDark
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = TextPrimaryDark
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextSecondaryDark
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = TextPrimaryDark
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextSecondaryDark
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        color = TextMuted
    )
)
