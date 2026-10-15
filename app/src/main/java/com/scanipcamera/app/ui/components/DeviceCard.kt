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
    isIpConflict: Boolean = false,
    onConfigureClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onActivateClick: () -> Unit,
    onRtspClick: () -> Unit,
    onDiagnosticClick: () -> Unit,
    onWanPortClick: () -> Unit,
    onRebootClick: () -> Unit,
    onTimeSyncClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val isSameSubnet = remember(device.ipv4Address, phoneIp, phoneMask) {
        if (phoneIp.isBlank()) true else NetworkUtils.isSameSubnet(device.ipv4Address, phoneIp, phoneMask)
    }

    val isRecorder = (device.category == DeviceCategory.NVR || device.category == DeviceCategory.DVR)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // ROW 1: BRAND BADGE + FULL MODEL NAME + STATUS BADGES
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Badge (e.g. DAHUA, HIKVISION, EZVIZ)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = device.brand.badgeColor
                ) {
                    Text(
                        text = device.brand.displayName.uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Full Camera / NVR Model Code (NO TRUNCATION!)
                Text(
                    text = device.model.ifEmpty {
                        if (isRecorder) "Đầu ghi hình ${device.brand.displayName}" else "Camera IP ${device.brand.displayName}"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Channel Badge if NVR / DVR
                if (device.channelCount > 0) {
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${device.channelCount} CH",
                            color = Color(0xFF0284C7),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Subnet Mismatch Warning Badge
                if (!isSameSubnet && phoneIp.isNotBlank()) {
                    Surface(
                        color = SubnetMismatchColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Lệch IP",
                            color = SubnetMismatchColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // IP Conflict Critical Warning Badge
                if (isIpConflict) {
                    Surface(
                        color = Color(0xFFDC2626),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "TRÙNG IP!",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Unactivated Device Warning Badge
                if (!device.isActivated) {
                    Surface(
                        color = StatusInactive.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Chưa active",
                            color = StatusInactive,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ROW 2: IP ADDRESS + PORTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // IP Address (Bold Monospace)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSameSubnet) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = device.ipv4Address,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Ports: HTTP and SDK/TCP
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val portText = if (device.sdkPort > 0 && device.sdkPort != device.httpPort) {
                        "Port: ${device.httpPort} / ${device.sdkPort}"
                    } else {
                        "Port: ${device.httpPort}"
                    }
                    Text(
                        text = portText,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // ROW 3: MAC ADDRESS + SERIAL NUMBER (Clickable to copy)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // MAC Address (Click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            if (device.macAddress.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", device.macAddress))
                                Toast.makeText(context, "Đã sao chép MAC: ${device.macAddress}", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Text(
                        text = "MAC: ${device.macAddress.ifEmpty { "Chưa có MAC" }}",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Serial Number (Cyan Tech, Click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            if (device.serialNumber.isNotBlank()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Serial Number", device.serialNumber))
                                Toast.makeText(context, "Đã sao chép S/N: ${device.serialNumber}", Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Text(
                        text = "S/N: ${device.serialNumber.ifEmpty { "N/A" }}",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ROW 4: 4 CLEAR ACTION BUTTONS - ABSOLUTELY NO OVERLAPPING, ZERO MISCLICKS!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Button 1: Đổi IP (Configure IP)
                Surface(
                    onClick = onConfigureClick,
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f).height(31.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Đổi IP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            maxLines = 1
                        )
                    }
                }

                // Button 2: Mở Web GUI
                Surface(
                    onClick = {
                        val url = "http://${device.ipv4Address}:${device.httpPort}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                            .onFailure { Toast.makeText(context, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show() }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2563EB).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f).height(31.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Mở Web",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB),
                            maxLines = 1
                        )
                    }
                }

                // Button 3: Cài đặt (WDR, Độ phân giải, NTP, Hình ảnh)
                Surface(
                    onClick = onSettingsClick,
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0D9488).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.45f)),
                    modifier = Modifier.weight(1f).height(31.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFF0D9488),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Cài đặt",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0D9488),
                            maxLines = 1
                        )
                    }
                }

                // Button 4: Tiện ích / Thêm...
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF475569).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().height(31.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Thêm ▾",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                maxLines = 1
                            )
                        }
                    }

                    // Dropdown menu for RTSP, Ping diagnostic, WAN Port, Time Sync, Activate, Reboot
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kiểm tra mở Port WAN (NAT)", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onWanPortClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Đồng bộ giờ & Cấu hình NTP", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onTimeSyncClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Xem luồng RTSP & Video", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onRtspClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Kiểm tra Ping & Port nội bộ", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onDiagnosticClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )

                        if (!device.isActivated) {
                            DropdownMenuItem(
                                text = { Text("Kích hoạt thiết bị", fontSize = 13.sp) },
                                onClick = {
                                    showMenu = false
                                    onActivateClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Khởi động lại (Reboot)", fontSize = 13.sp) },
                            onClick = {
                                showMenu = false
                                onRebootClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}
