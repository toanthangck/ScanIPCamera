package com.scanipcamera.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.network.NetworkUtils
import com.scanipcamera.app.ui.theme.StatusActive
import com.scanipcamera.app.ui.theme.StatusInactive
import com.scanipcamera.app.ui.theme.SubnetMismatchColor

@Composable
fun DeviceCard(
    device: CameraDevice,
    phoneIp: String = "",
    phoneMask: String = "255.255.255.0",
    onConfigureClick: () -> Unit,
    onActivateClick: () -> Unit,
    onRtspClick: () -> Unit,
    onDiagnosticClick: () -> Unit,
    onRebootClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val isSameSubnet = remember(device.ipv4Address, phoneIp, phoneMask) {
        if (phoneIp.isBlank()) true else NetworkUtils.isSameSubnet(device.ipv4Address, phoneIp, phoneMask)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            // Header: Brand Badge & Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand Badge
                    Surface(
                        color = device.brand.badgeColor,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = device.brand.displayName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Protocol Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = device.rawProtocol,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subnet warning badge if mismatched
                    if (!isSameSubnet && phoneIp.isNotBlank()) {
                        Surface(
                            color = SubnetMismatchColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Khác dải IP ⚠️",
                                color = SubnetMismatchColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Activation Status Badge
                    Surface(
                        color = if (device.isActivated) StatusActive.copy(alpha = 0.15f) else StatusInactive.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (device.isActivated) "Đã kích hoạt" else "Chưa kích hoạt",
                            color = if (device.isActivated) StatusActive else StatusInactive,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // More options menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn khác",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Đường dẫn luồng RTSP") },
                                onClick = {
                                    showMenu = false
                                    onRtspClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Kiểm tra Ping & Port") },
                                onClick = {
                                    showMenu = false
                                    onDiagnosticClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Khởi động lại (Reboot)") },
                                onClick = {
                                    showMenu = false
                                    onRebootClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Model Name & Serial
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = device.model,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (device.serialNumber.isNotBlank()) {
                        Text(
                            text = "SN: ${device.serialNumber}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // IP & Network Info Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "Địa chỉ IP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${device.ipv4Address}:${device.httpPort}",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = if (!isSameSubnet) SubnetMismatchColor else MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Địa chỉ MAC", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = device.macAddress.ifEmpty { "N/A" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (device.ipv4Gateway.isNotBlank() || device.ipv4SubnetMask.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Subnet: ${device.ipv4SubnetMask}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.ipv4Gateway.isNotBlank()) {
                        Text(
                            text = "Gateway: ${device.ipv4Gateway}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Config IP Button
                Button(
                    onClick = onConfigureClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Đổi IP", fontSize = 12.sp)
                }

                // If not activated, show Activation button
                if (!device.isActivated) {
                    FilledTonalButton(
                        onClick = onActivateClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StatusInactive.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Kích hoạt", fontSize = 12.sp, color = StatusInactive)
                    }
                }

                // RTSP Stream Quick Button
                OutlinedButton(
                    onClick = onRtspClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "RTSP", fontSize = 12.sp)
                }

                // Open Web Button
                OutlinedButton(
                    onClick = {
                        val url = "http://${device.ipv4Address}:${device.httpPort}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Web", fontSize = 12.sp)
                }
            }
        }
    }
}
