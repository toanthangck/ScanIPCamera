package com.scanipcamera.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.network.NetworkUtils
import com.scanipcamera.app.ui.theme.*

@Composable
fun DeviceCard(
    device: CameraDevice,
    phoneIp: String = "",
    phoneMask: String = "255.255.255.0",
    onConfigureClick: () -> Unit,
    onActivateClick: () -> Unit,
    onRtspClick: () -> Unit,
    onDiagnosticClick: () -> Unit,
    onWanPortClick: () -> Unit,
    onRebootClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val isSameSubnet = remember(device.ipv4Address, phoneIp, phoneMask) {
        if (phoneIp.isBlank()) true else NetworkUtils.isSameSubnet(device.ipv4Address, phoneIp, phoneMask)
    }

    val isRecorder = (device.category == DeviceCategory.NVR || device.category == DeviceCategory.DVR)

    // Palette tokens according to UI UX Pro Max standards
    val badgeTopColor = if (isRecorder) Color(0xFF0284C7) else Color(0xFFEA580C)
    val badgeBottomColor = if (isRecorder) Color(0xFF38BDF8) else Color(0xFFF97316)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left block: ConfigTool Mobile style (Model on top, Ports on bottom)
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top: Model name
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(badgeTopColor)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = device.model.ifEmpty { device.brand.displayName },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 13.sp
                    )
                }

                // Bottom: Ports stacked (e.g. 80 / 37777)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(badgeBottomColor)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${device.httpPort}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${device.sdkPort}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center block: IP Address, MAC, S/N, and Badges
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // Row 1: Big Bold IP & Status Badges
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = device.ipv4Address,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (device.channelCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${device.channelCount} CH",
                                color = Color(0xFF0284C7),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!isSameSubnet && phoneIp.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = SubnetMismatchColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Lệch IP",
                                color = SubnetMismatchColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (!device.isActivated) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = StatusInactive.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Chưa kích hoạt",
                                color = StatusInactive,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Row 2: MAC Address (Interactive click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (device.macAddress.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", device.macAddress))
                            Toast.makeText(context, "Đã sao chép MAC: ${device.macAddress}", Toast.LENGTH_SHORT).show()
                        } else {
                            onDiagnosticClick()
                        }
                    }
                ) {
                    Text(
                        text = "MAC: ${device.macAddress.ifEmpty { "Chưa có MAC" }}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Row 3: Serial Number (Highlighted in bright cyan/blue, interactive click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (device.serialNumber.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Serial Number", device.serialNumber))
                            Toast.makeText(context, "Đã sao chép S/N: ${device.serialNumber}", Toast.LENGTH_SHORT).show()
                        } else {
                            onDiagnosticClick()
                        }
                    }
                ) {
                    Text(
                        text = "S/N: ${device.serialNumber.ifEmpty { "N/A" }}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            }

            // Right block: 2x2 Circular action buttons with distinctive semantic colors
            Column(
                modifier = Modifier.padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Edit IP (Pencil)
                    IconButton(
                        onClick = onConfigureClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Đổi IP",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 2. Settings / Test Port WAN (Gear)
                    IconButton(
                        onClick = onWanPortClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D9488))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Test Port WAN / Cài đặt",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 3. Info / Details & Diagnostics (Info icon)
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF475569))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Menu chức năng",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                                text = { Text("Kiểm tra Ping & Port nội bộ") },
                                onClick = {
                                    showMenu = false
                                    onDiagnosticClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            if (!device.isActivated) {
                                DropdownMenuItem(
                                    text = { Text("Kích hoạt thiết bị") },
                                    onClick = {
                                        showMenu = false
                                        onActivateClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }

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

                    // 4. Web GUI (Globe icon)
                    IconButton(
                        onClick = {
                            val url = "http://${device.ipv4Address}:${device.httpPort}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Mở Web",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
