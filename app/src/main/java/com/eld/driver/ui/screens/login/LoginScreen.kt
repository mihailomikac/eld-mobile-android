package com.eld.driver.ui.screens.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eld.driver.ui.components.CurvedWaveShape
import com.eld.driver.ui.theme.*

/**
 * Login Screen - Professional authentication UI
 */
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val passwordFocusRequester = remember { FocusRequester() }

    // Permission checking
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For older devices, location permission is enough for BLE
            hasLocationPermission()
        }
    }

    fun hasAllPermissions(): Boolean {
        return hasLocationPermission() && hasBlePermissions()
    }

    var permissionsGranted by remember { mutableStateOf(hasAllPermissions()) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) {
            // Permissions granted, proceed with login
            if (username.isNotBlank() && password.isNotBlank()) {
                viewModel.login(username, password)
            }
        } else {
            showPermissionDialog = true
        }
    }

    // Function to request permissions
    fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Location permission
        if (!hasLocationPermission()) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // BLE permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions already granted
            if (username.isNotBlank() && password.isNotBlank()) {
                viewModel.login(username, password)
            }
        }
    }

    // Observe auth token to know if we should navigate
    val authToken by viewModel.authToken.collectAsState()

    // Handle auth state changes
    LaunchedEffect(authToken) {
        if (authToken == null) {
            // User logged out - reset state
            viewModel.resetState()
        }
        // NOTE: Navigation on login success is handled in the LaunchedEffect(uiState) below
        // and in ELDApp for session restore. DO NOT navigate here to avoid race conditions.
    }

    // Handle successful login - but ONLY for fresh logins, not session restore
    // Session restore is handled in ELDApp which navigates directly to dashboard if vehicle is set
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success && authToken != null) {
            // Check if we already have a vehicle ID (session was restored)
            val existingVehicleId = com.eld.driver.ELDDriverApplication.getCurrentVehicleId()
            if (existingVehicleId != null) {
                // Session restore with existing vehicle - go to dashboard
                android.util.Log.d("LoginScreen", "✅ Session has vehicle ID $existingVehicleId - skipping vehicle selection")
                // ELDApp handles navigation to dashboard, so don't navigate here
            } else {
                // Fresh login - need to select vehicle
                android.util.Log.d("LoginScreen", "📋 Fresh login - navigating to vehicle selection")
                navController.navigate("vehicle_selection") {
                    popUpTo("login") { inclusive = true }
                }
            }
        }
    }

    // Permission denied dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Blue600
                )
            },
            title = {
                Text(
                    "Permissions Required",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "ELD Mate requires Location and Bluetooth permissions to connect to ELD devices and track your location for logging purposes. Please grant these permissions to continue."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        requestPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600)
                ) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Force logout confirmation dialog
    if (uiState is LoginUiState.NeedsForceLogout) {
        val forceLogoutState = uiState as LoginUiState.NeedsForceLogout
        val isDriving = forceLogoutState.isDriverCurrentlyDriving
        AlertDialog(
            onDismissRequest = { viewModel.cancelForceLogout() },
            icon = {
                Icon(
                    imageVector = if (isDriving) Icons.Default.LocalShipping else Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (isDriving) AccentRed else AccentOrange
                )
            },
            title = {
                Text(
                    if (isDriving) "Driver Currently Driving!" else "Already Logged In",
                    fontWeight = FontWeight.Bold,
                    color = if (isDriving) AccentRed else TextPrimary
                )
            },
            text = {
                Text(forceLogoutState.message)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmForceLogout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDriving) AccentRed else Blue600
                    )
                ) {
                    Text(if (isDriving) "Force Logout Anyway" else "Logout & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelForceLogout() }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SecondaryBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Blue gradient header with curved bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)  // Increased height for logo area
            ) {
                // Blue gradient background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Blue700, Blue600)
                            )
                        )
                )

                // Logo and branding - centered
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Truck icon
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = "ELDMATE Logo",
                        modifier = Modifier.size(80.dp),
                        tint = Color.White
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    Text(
                        text = "eldmate",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 42.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Curved white shape at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)  // Height of the curved section
                        .align(Alignment.BottomCenter)
                        .clip(CurvedWaveShape())
                        .background(SecondaryBackground)
                )
            }

            // Login form
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.md))

                // Welcome text
                Text(
                    text = "Welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 36.sp
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                // Recent emails list - read fresh on each recomposition
                val recentEmails = viewModel.getRecentEmails()

                // Email field
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Email", color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = BackgroundLight,
                        focusedContainerColor = BackgroundLight,
                        focusedBorderColor = BorderLight,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = Blue600,
                        focusedPlaceholderColor = TextSecondary,
                        unfocusedPlaceholderColor = TextSecondary
                    )
                )

                // Recent emails shortcuts (below email field)
                if (recentEmails.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentEmails.forEach { email ->
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        username = email
                                        passwordFocusRequester.requestFocus()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                color = Blue600.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = email,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Blue600,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = TextSecondary) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (username.isNotBlank() && password.isNotBlank()) {
                                requestPermissions()
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Default.Visibility
                                else
                                    Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible)
                                    "Hide password"
                                else
                                    "Show password",
                                tint = TextSecondary
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        unfocusedContainerColor = BackgroundLight,
                        focusedContainerColor = BackgroundLight,
                        focusedBorderColor = BorderLight,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = Blue600,
                        focusedPlaceholderColor = TextSecondary,
                        unfocusedPlaceholderColor = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Forgot password link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { /* TODO: Forgot password */ }) {
                        Text(
                            text = "Forgot your password?",
                            color = Blue600,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Error message (if any)
                if (uiState is LoginUiState.Error) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = (uiState as LoginUiState.Error).message,
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xxl))

                // Login button
                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            requestPermissions()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = username.isNotBlank() &&
                             password.isNotBlank() &&
                             uiState !is LoginUiState.Loading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Blue600,
                        disabledContainerColor = Blue600.copy(alpha = 0.5f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    if (uiState is LoginUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Website link
                TextButton(onClick = { /* TODO: Open website */ }) {
                    Text(
                        text = "eldmate.cloud",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Blue600,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }
    }
}
