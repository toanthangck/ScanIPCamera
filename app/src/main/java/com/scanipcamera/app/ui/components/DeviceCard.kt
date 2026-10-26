package com.scanipcamera.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.network.NetworkUtils
import com.scanipcamera.app.ui.theme.*

@Composable
fun DeviceCard(
    device: CameraDevice,
    itemIndex: Int = 0,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN: NUMBERED STT & DEVICE TYPE (Bên trái có danh sách đánh số thứ tự)
            if (itemIndex > 0) {
                Surface(
                    color = when (device.category) {
                        DeviceCategory.NVR -> Color(0xFF4F46E5).copy(alpha = 0.10f)
                        DeviceCategory.DVR -> Color(0xFFD97706).copy(alpha = 0.10f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    },
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp, horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = String.format("%02d", itemIndex),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = when (device.category) {
                                DeviceCategory.NVR -> Color(0xFF4338CA)
                                DeviceCategory.DVR -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Icon(
                            imageVector = when (device.category) {
                                DeviceCategory.NVR -> Icons.Default.Storage
                                DeviceCategory.DVR -> Icons.Default.Dns
                                else -> Icons.Default.Videocam
                            },
                            contentDescription = null,
                            tint = when (device.category) {
                                DeviceCategory.NVR -> Color(0xFF4338CA)
                                DeviceCategory.DVR -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = when (device.category) {
                                DeviceCategory.NVR -> "NVR"
                                DeviceCategory.DVR -> "DVR"
                                else -> "CAM"
                            },
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (device.category) {
                                DeviceCategory.NVR -> Color(0xFF4338CA)
                                DeviceCategory.DVR -> Color(0xFFB45309)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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

            Spacer(modifier = Modifier.height(2.dp))

            // ROW 2: IP ADDRESS + CỔNG WEB (XANH DƯƠNG) + CỔNG RTSP/ONVIF (TÍM) + CỔNG DỊCH VỤ (CAM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 1. IP Address
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onConfigureClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSameSubnet) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = device.ipv4Address,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Ghi thêm "IP máy bạn" nếu trùng IP điện thoại
                if (phoneIp.isNotBlank() && device.ipv4Address == phoneIp) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "IP máy bạn",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                // 2. Cổng Web (MÀU XANH DƯƠNG ĐẶC TRƯNG)
                Surface(
                    onClick = {
                        val url = "http://${device.ipv4Address}:${device.httpPort}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(intent) }
                            .onFailure { Toast.makeText(context, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show() }
                    },
                    color = Color(0xFF2563EB).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Web: ${device.httpPort}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8)
                        )
                    }
                }

                // 3. Cổng RTSP / ONVIF (MÀU TÍM ĐẶC TRƯNG)
                val rtspPortVal = if (device.rtspPort > 0) device.rtspPort else 554
                val isExplicitOnvif = device.brand == DeviceBrand.ONVIF || device.rawProtocol == "ONVIF"
                Surface(
                    onClick = onRtspClick,
                    color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isExplicitOnvif) "ONVIF: $rtspPortVal" else "RTSP: $rtspPortVal",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9)
                        )
                    }
                }

                // 4. Cổng Dịch vụ camera (MÀU CAM / HỔ PHÁCH ĐẶC TRƯNG)
                if (device.sdkPort > 0) {
                    Surface(
                        onClick = onDiagnosticClick,
                        color = Color(0xFFEA580C).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Dịch vụ: ${device.sdkPort}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ROW 3: MAC ADDRESS + SERIAL NUMBER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // MAC Address (Click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (device.macAddress.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MAC Address", device.macAddress))
                            Toast.makeText(context, "Đã sao chép MAC", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = "MAC: ${device.macAddress.ifEmpty { "Chưa có MAC" }}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Serial Number (Cyan Tech, Click to copy)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        if (device.serialNumber.isNotBlank()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Serial Number", device.serialNumber))
                            Toast.makeText(context, "Đã sao chép S/N", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = "S/N: ${device.serialNumber.ifEmpty { "N/A" }}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7)
                    )
                }
            }
        }
    }
}
}
