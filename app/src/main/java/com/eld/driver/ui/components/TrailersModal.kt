package com.eld.driver.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eld.driver.ui.theme.*

/**
 * Trailers & Shipping Docs Modal - Matches iOS design exactly
 * Shows fields for trailers and shipping docs
 */
@Composable
fun TrailersModal(
    currentTrailers: String = "",
    currentShippingDocs: String = "",
    onDismiss: () -> Unit,
    onSave: (trailers: String, shippingDocs: String) -> Unit
) {
    var trailers by remember { mutableStateOf(currentTrailers) }
    var shippingDocs by remember { mutableStateOf(currentShippingDocs) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(CornerRadius.large),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                // Title
                Text(
                    text = "Trailers & Shipping Docs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )

                // Trailers field
                OutlinedTextField(
                    value = trailers,
                    onValueChange = { trailers = it },
                    placeholder = { Text("Trailers", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BackgroundLight,
                        focusedContainerColor = BackgroundLight,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Blue600
                    ),
                    shape = RoundedCornerShape(CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Shipping Docs field
                OutlinedTextField(
                    value = shippingDocs,
                    onValueChange = { shippingDocs = it },
                    placeholder = { Text("Shipping Docs", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BackgroundLight,
                        focusedContainerColor = BackgroundLight,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Blue600
                    ),
                    shape = RoundedCornerShape(CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "Cancel",
                            color = Blue600,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    // Save button
                    Button(
                        onClick = { onSave(trailers, shippingDocs) },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(CornerRadius.medium),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
