package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleSignInHelper
import com.example.data.LoginOutcome
import com.example.ui.theme.*
import com.example.viewmodel.WaterPlantViewModel
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    viewModel: WaterPlantViewModel,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }

    // Both fields empty by default as requested
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(WaterNavyDark, WaterNavyPrimary, WaterBlueDeep)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Official Aab e Baraan Brand Logo Card (matched to 1393:1129 intrinsic aspect ratio)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, WaterSkyLight.copy(alpha = 0.8f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .widthIn(max = 230.dp)
                    .aspectRatio(1393f / 1129f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.aab_e_baraan_logo),
                    contentDescription = "Aab e Baraan Official Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Aab e Baraan",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "آبِ باراں • RO Drinking Water Plant",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = WaterSkyLight,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Credential Card with High Contrast Colors
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Plant Login Portal",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = WaterNavyDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "اپنا یوزر نیم (لاگ ان آئی ڈی) اور پاس ورڈ درج کریں",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark
                        )
                    }

                    // Login ID TextField
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = {
                            Text(
                                "Login ID / Username*",
                                color = TextSecondaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        placeholder = {
                            Text("e.g. admin or supervisor", color = TextMuted)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "User Icon",
                                tint = WaterNavyPrimary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = WaterNavyPrimary,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = WaterNavyPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_id_input")
                    )

                    // Password TextField
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = {
                            Text(
                                "Password*",
                                color = TextSecondaryDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        placeholder = {
                            Text("Enter password", color = TextMuted)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Password Icon",
                                tint = WaterNavyPrimary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextSecondaryDark
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = WaterNavyPrimary,
                            unfocusedBorderColor = BorderLight,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = WaterNavyPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input")
                    )

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ErrorRedLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = ErrorRed,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Login Action Button
                    Button(
                        onClick = {
                            val cleanUser = username.trim().removePrefix("@").trim()
                            if (cleanUser.isEmpty() || password.isEmpty()) {
                                errorMessage = "براہ کرم لاگ ان آئی ڈی اور پاس ورڈ دونوں درج کریں"
                                return@Button
                            }
                            isLoggingIn = true
                            errorMessage = null
                            coroutineScope.launch {
                                val outcome = viewModel.loginWithOutcome(cleanUser, password)
                                isLoggingIn = false
                                when (outcome) {
                                    LoginOutcome.SUCCESS -> onAuthSuccess()
                                    LoginOutcome.INACTIVE_ACCOUNT -> {
                                        errorMessage = "یہ اکاؤنٹ ایڈمن نے غیر فعال (Inactive) کر دیا ہے۔ ایڈمن سے رابطہ کریں۔"
                                    }
                                    LoginOutcome.CLOUD_SYNC_REQUIRED -> {
                                        errorMessage = "عملے کی تصدیق کے لیے انٹرنیٹ کنکشن (یا ایڈمن گوگل لاگ ان سنک) درکار ہے۔ بغیر جی میل کے اسٹاف لاگ ان کے لیے انٹرنیٹ آن کر کے دوبارہ کوشش کریں۔"
                                    }
                                    LoginOutcome.INVALID_CREDENTIALS -> {
                                        errorMessage = "غلط یوزر نیم یا پاس ورڈ ہے۔ دوبارہ کوشش کریں۔"
                                    }
                                }
                            }
                        },
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WaterNavyPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Checking Credentials / تصدیق ہو رہی ہے...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Login (لاگ ان کریں)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Google Sign-In Button for Cloud Sync (Credential Manager)
                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            isGoogleLoading = true
                            GoogleSignInHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                onAuthSuccess = { uid, email, displayName ->
                                    coroutineScope.launch {
                                        val authorized = viewModel.handleGoogleAuthSuccessAsync(uid, email, displayName)
                                        isGoogleLoading = false
                                        if (authorized) {
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = "Google account '$email' is not authorized. Ask Admin to add your Gmail in Staff Accounts or use Admin Google account."
                                        }
                                    }
                                },
                                onAuthError = { errMsg ->
                                    isGoogleLoading = false
                                    errorMessage = errMsg
                                },
                                scope = coroutineScope,
                                onAuthCancelled = {
                                    isGoogleLoading = false
                                }
                            )
                        },
                        enabled = !isGoogleLoading && !isLoggingIn,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, WaterBlueDeep),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = WaterSkyLight.copy(alpha = 0.35f),
                            contentColor = WaterNavyDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(
                                color = WaterNavyPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connecting Google Cloud...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyDark
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sign in with Google",
                                tint = WaterBlueDeep,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign in with Google (گوگل کلاؤڈ سنک)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyDark
                            )
                        }
                    }

                    // Quick Fingerprint Sensor Icon Login
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = WaterAqua.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, WaterAqua),
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    errorMessage = null
                                    val lastUser = viewModel.getLastLoggedInUser()
                                    if (lastUser.isNullOrBlank()) {
                                        errorMessage = "پہلے یوزر نیم اور پاس ورڈ سے کم از کم ایک بار لاگ ان کریں۔ اس کے بعد آئندہ فنگر پرنٹ لاگ ان خودکار فعال ہو جائے گا۔"
                                        return@clickable
                                    }
                                    if (username.isNotBlank() && username.trim().lowercase() != lastUser.trim().lowercase()) {
                                        errorMessage = "دوسرے یوزر میں سوئچ کرنے کے لیے پہلے پاس ورڈ سے لاگ ان کرنا ضروری ہے۔"
                                        return@clickable
                                    }
                                    triggerBiometricPrompt(
                                        context = context,
                                        onSuccess = {
                                            val success = viewModel.loginWithBiometrics(username.ifBlank { null })
                                            if (success) {
                                                onAuthSuccess()
                                            } else {
                                                errorMessage = "فنگر پرنٹ لاگ ان ممکن نہیں ہو سکا۔ پہلے یوزر نیم اور پاس ورڈ سے لاگ ان کریں۔"
                                            }
                                        },
                                        onError = { err ->
                                            errorMessage = err
                                        }
                                    )
                                }
                                .testTag("fingerprint_login_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Quick Fingerprint Login",
                                    tint = WaterNavyPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fingerprint Login (فنگر پرنٹ پر کلک کریں)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryDark
                        )
                    }

                    // Forgot Password Text Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = true },
                            modifier = Modifier.testTag("forgot_password_button")
                        ) {
                            Icon(
                                Icons.Default.HelpOutline,
                                contentDescription = null,
                                tint = WaterBlueDeep,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Forgot Password? (پاس ورڈ بھول گئے؟)",
                                color = WaterBlueDeep,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Forgot Password Information & Reset Dialog
    if (showForgotPasswordDialog) {
        var recoveryUsername by remember { mutableStateOf("") }
        var resetMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            containerColor = Color.White,
            titleContentColor = WaterNavyDark,
            textContentColor = TextPrimaryDark,
            icon = {
                Icon(
                    Icons.Default.LockReset,
                    contentDescription = null,
                    tint = WaterNavyPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Forgot Password / Reset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = WaterNavyDark,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "اگر آپ Supervisor کا پاس ورڈ بھول گئے ہیں، تو پلانٹ ایڈمنسٹریٹر اپنے لاگ ان سے آپ کا پاس ورڈ فوری ری سیٹ کر سکتا ہے۔",
                        fontSize = 11.5.sp,
                        color = TextPrimaryDark,
                        lineHeight = 15.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WaterSkyLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Plant Administrator:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WaterNavyPrimary
                            )
                            Text(
                                text = "Muhammad Rafiq (Admin)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "Phone: 0300-1234567",
                                fontSize = 10.5.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }

                    OutlinedTextField(
                        value = recoveryUsername,
                        onValueChange = {
                            recoveryUsername = it
                            resetMessage = null
                        },
                        label = { Text("Your Username / ID") },
                        placeholder = { Text("e.g. supervisor") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark,
                            focusedBorderColor = WaterNavyPrimary,
                            unfocusedBorderColor = BorderLight
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resetMessage != null) {
                        Text(
                            text = resetMessage!!,
                            color = SuccessGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = recoveryUsername.trim().lowercase()
                        if (clean.isNotEmpty()) {
                            viewModel.requestPasswordReset(clean)
                            resetMessage = "درخواست کامیابی سے ایڈمن کو بھیج دی گئی ہے۔ ایڈمن ڈیش بورڈ میں پینڈنگ ویری فکیشن کے ذریعے آپ کا پاس ورڈ ری سیٹ کر دے گا۔"
                        } else {
                            resetMessage = "Enter your username to submit request."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WaterNavyPrimary, contentColor = Color.White)
                ) {
                    Text("Submit Request (درخواست بھیجیں)", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Close", color = TextSecondaryDark)
                }
            }
        )
    }
}

fun triggerBiometricPrompt(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        val cancellationSignal = android.os.CancellationSignal()
        val executor = context.mainExecutor

        try {
            val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
                .setTitle("Fingerprint Unlock (فنگر پرنٹ انلاک)")
                .setSubtitle("Aab e Baraan RO Water Plant")
                .setDescription("اپنا رجسٹرڈ فنگر پرنٹ سینسر پر لگائیں")
                .setNegativeButton("Cancel / منسوخ", executor) { _, _ ->
                    cancellationSignal.cancel()
                }
                .build()

            prompt.authenticate(
                cancellationSignal,
                executor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                            errorCode != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                        ) {
                            val userMsg = when (errorCode) {
                                android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ->
                                    "اس موبائل میں کوئی فنگر پرنٹ رجسٹر نہیں ہے۔ براہ کرم موبائل سیٹنگز (Security > Fingerprint) میں فنگر پرنٹ سیٹ کریں۔"
                                android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                                android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ->
                                    "فنگر پرنٹ سینسر دستیاب نہیں ہے۔"
                                android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                                android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
                                    "کئی بار غلط فنگر لگانے پر سینسر عارضی لاک ہے۔ پاس ورڈ سے لاگ ان کریں۔"
                                else -> errString?.toString() ?: "فنگر پرنٹ کی تصدیق نہیں ہو سکی"
                            }
                            onError(userMsg)
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("فنگر پرنٹ میچ نہیں ہوا۔ دوبارہ سینسر پر لگائیں۔")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("AuthScreen", "Biometric prompt creation failed", e)
            onError("فنگر پرنٹ سینسر شروع کرنے میں مسئلہ ہوا۔ یوزر نیم اور پاس ورڈ سے لاگ ان کریں۔")
        }
    } else {
        onError("فنگر پرنٹ لاگ ان کے لیے اینڈرائڈ 9 یا نیا درکار ہے۔")
    }
}
