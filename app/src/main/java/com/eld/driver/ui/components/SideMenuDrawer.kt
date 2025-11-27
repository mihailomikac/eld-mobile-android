package com.eld.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.eld.driver.ui.theme.*

/**
 * Side Menu Drawer - Matches iOS design exactly
 * Navigational menu with user info and menu items
 */
@Composable
fun SideMenuDrawer(
    navController: NavController,
    userName: String,
    userEmail: String,
    onClose: () -> Unit,
    onLogout: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        drawerContainerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // User Info Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgSecondary)
                    .padding(Spacing.lg)
            ) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Menu Items
            DrawerMenuItem(
                icon = Icons.Default.Dashboard,
                title = "Dashboard",
                onClick = {
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.ShowChart,
                title = "Logs",
                onClick = {
                    navController.navigate("logs")
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Warning,
                title = "Unidentified Driving",
                onClick = {
                    // TODO: Navigate to unidentified driving screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Search,
                title = "DVIR",
                onClick = {
                    navController.navigate("inspections")
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Shield,
                title = "DOT Inspection",
                onClick = {
                    // TODO: Navigate to DOT inspection screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Build,
                title = "Maintenance",
                onClick = {
                    // TODO: Navigate to maintenance screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Description,
                title = "Documents",
                onClick = {
                    // TODO: Navigate to documents screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Headset,
                title = "Dispatch",
                onClick = {
                    // TODO: Navigate to dispatch screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.LocationOn,
                title = "Stations Maps",
                onClick = {
                    // TODO: Navigate to stations maps screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Bluetooth,
                title = "BLE Test",
                onClick = {
                    navController.navigate("ble_test")
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Settings,
                title = "Settings",
                onClick = {
                    // TODO: Navigate to settings screen
                    onClose()
                }
            )

            DrawerMenuItem(
                icon = Icons.Default.Info,
                title = "About",
                onClick = {
                    // TODO: Navigate to about screen
                    onClose()
                }
            )

            Divider(
                modifier = Modifier.padding(vertical = Spacing.sm),
                color = BorderLight
            )

            DrawerMenuItem(
                icon = Icons.Default.Logout,
                title = "Logout",
                onClick = {
                    onLogout()
                    onClose()
                }
            )

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Drawer Menu Item
 */
@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}
