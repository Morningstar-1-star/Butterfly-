package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.util.DnsProvider

import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SecureDnsSelectionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isSecureDnsEnabled by viewModel.isSecureDnsEnabled.collectAsState()
    val selectedProvider by viewModel.selectedDnsProvider.collectAsState()
    val customDnsUrl by viewModel.customDnsUrl.collectAsState()

    var customUrlInput by remember(customDnsUrl) { mutableStateOf(customDnsUrl) }
    var showCustomInput by remember { mutableStateOf(selectedProvider == DnsProvider.CUSTOM) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222226)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Secure DNS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isSecureDnsEnabled) selectedProvider.displayName else "Disabled (System ISP)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Secure DNS Toggle Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setSecureDnsEnabled(!isSecureDnsEnabled) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Encrypted Secure DNS",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Encrypts DNS queries via DNS-over-HTTPS to bypass ISP censorship & speed up video loading",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = isSecureDnsEnabled,
                    onCheckedChange = { viewModel.setSecureDnsEnabled(it) }
                )
            }

            if (isSecureDnsEnabled) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(DnsProvider.values()) { provider ->
                        val isSelected = (selectedProvider == provider)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSecureDnsEnabled(true)
                                    viewModel.setSelectedDnsProvider(provider)
                                    showCustomInput = (provider == DnsProvider.CUSTOM)
                                    if (provider != DnsProvider.CUSTOM) {
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                )
                                if (provider.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = provider.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Custom DoH URL Input if Custom is selected
                if (showCustomInput || selectedProvider == DnsProvider.CUSTOM) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = customUrlInput,
                            onValueChange = { customUrlInput = it },
                            label = { Text("Custom DoH Endpoint URL") },
                            placeholder = { Text("https://dns.nextdns.io/doh") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.setCustomDnsUrl(customUrlInput)
                                viewModel.setSelectedDnsProvider(DnsProvider.CUSTOM)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save & Apply Custom DNS", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}
