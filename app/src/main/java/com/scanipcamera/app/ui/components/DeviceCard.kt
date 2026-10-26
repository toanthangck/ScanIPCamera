package com.scanipcamera.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.scanipcamera.app.data.scanner.PortScannerUtils
import com.scanipcamera.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

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

    var openPorts by remember(device.ipv4Address, device.verifiedOpenPorts) {
        mutableStateOf(device.verifiedOpenPorts)
    }

    var activeHttpPort by remember(device.httpPort) {
        mutableStateOf(device.httpPort)
    }

    // Tự động quét dải cổng 80..9000 trên thiết bị để phát hiện chính xác port web
    LaunchedEffect(device.ipv4Address) {
        if (device.ipv4Address.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val open = PortScannerUtils.scanPortsRange(device.ipv4Address, 80, 9000).toSet()
                if (open.isNotEmpty()) {
                    openPorts = openPorts + open
                    // Tìm chính xác cổng web thực sự trả về HTTP (hỗ trợ bất kỳ port nào từ 80-9000, e.g. 8089)
                    val httpCandidates = open.filter { it != 554 && it != 8554 && it != 37777 && it != 34567 }
                    val detectedWeb = httpCandidates.firstOrNull { PortScannerUtils.isHttpPort(device.ipv4Address, it) }
                        ?: (if (open.contains(device.httpPort)) device.httpPort else httpCandidates.firstOrNull())
                        ?: activeHttpPort

                    if (detectedWeb != activeHttpPort) {
                        activeHttpPort = detectedWeb
                    }
                }
            }
        }
    }

    // Hiệu ứng nhấp nháy cho cổng đầu ghi được mở
    val infiniteTransition = rememberInfiniteTransition(label = "nvr_port_blink")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink_alpha"
    )

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
                    .padding(horizontal = 7.dp, vertical = 2.dp)
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

            Spacer(modifier = Modifier.height(1.dp))

            // ROW 2: IP ADDRESS (BÊN TRÁI) + CÁC CỔNG GỌN GÀNG (BÊN PHẢI - VỪA KHÍT 1 MÀN HÌNH, KHÔNG KÉO TAY)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (phoneIp.isNotBlank() && device.ipv4Address == phoneIp) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "Máy bạn",
                                color = Color.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.5.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }

                // 2. Cổng Web (Xanh dương), RTSP/ONVIF (Tím), Dịch vụ (Cam)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val rtspPortVal = if (device.rtspPort > 0) device.rtspPort else 554
                    val isExplicitOnvif = device.brand == DeviceBrand.ONVIF || device.rawProtocol == "ONVIF"
                    val sdkPortVal = when {
                        openPorts.contains(37777) -> 37777
                        openPorts.contains(34567) -> 34567
                        openPorts.contains(8000) && device.brand == DeviceBrand.HIKVISION -> 8000
                        device.sdkPort > 0 && (device.sdkPort != 8000 || device.brand == DeviceBrand.HIKVISION) -> device.sdkPort
                        device.brand == DeviceBrand.DAHUA || device.brand == DeviceBrand.KBVISION || device.brand == DeviceBrand.IMOU -> 37777
                        device.brand == DeviceBrand.HIKVISION || device.brand == DeviceBrand.EZVIZ -> 8000
                        device.brand == DeviceBrand.XIONGMAI -> 34567
                        else -> device.sdkPort
                    }
                    val isWebOpen = openPorts.contains(activeHttpPort) || openPorts.contains(device.httpPort) || openPorts.contains(80) || openPorts.contains(8080)
                    val isRtspOpen = openPorts.contains(rtspPortVal) || openPorts.contains(554)
                    val isSdkOpen = (sdkPortVal > 0 && openPorts.contains(sdkPortVal)) ||
                            (device.brand == DeviceBrand.DAHUA && openPorts.contains(37777)) ||
                            (device.brand == DeviceBrand.HIKVISION && openPorts.contains(8000))

                    // Cổng Web (Màu xanh dương - nhấp nháy nếu là đầu ghi và cổng mở)
                    val webBlinking = isRecorder && isWebOpen
                    Surface(
                        onClick = {
                            val url = "http://${device.ipv4Address}:$activeHttpPort"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            runCatching { context.startActivity(intent) }
                                .onFailure { Toast.makeText(context, "Không mở được trình duyệt", Toast.LENGTH_SHORT).show() }
                        },
                        color = Color(0xFF2563EB).copy(alpha = if (webBlinking) 0.22f else 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (webBlinking) 1.2.dp else 0.8.dp,
                            color = Color(0xFF2563EB).copy(alpha = if (webBlinking) blinkAlpha else 0.5f)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = if (webBlinking) Modifier.graphicsLayer { alpha = blinkAlpha } else Modifier
                    ) {
                        Text(
                            text = "Web:$activeHttpPort",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Cổng RTSP / ONVIF (Màu tím - nhấp nháy nếu là đầu ghi và cổng mở)
                    val rtspBlinking = isRecorder && isRtspOpen
                    Surface(
                        onClick = onRtspClick,
                        color = Color(0xFF8B5CF6).copy(alpha = if (rtspBlinking) 0.22f else 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (rtspBlinking) 1.2.dp else 0.8.dp,
                            color = Color(0xFF8B5CF6).copy(alpha = if (rtspBlinking) blinkAlpha else 0.5f)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = if (rtspBlinking) Modifier.graphicsLayer { alpha = blinkAlpha } else Modifier
                    ) {
                        Text(
                            text = if (isExplicitOnvif) "ONVIF:$rtspPortVal" else "RTSP:$rtspPortVal",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Cổng Dịch vụ camera (Màu cam - nhấp nháy nếu là đầu ghi và cổng mở)
                    if (sdkPortVal > 0) {
                        val sdkBlinking = isRecorder && isSdkOpen
                        Surface(
                            onClick = onDiagnosticClick,
                            color = Color(0xFFEA580C).copy(alpha = if (sdkBlinking) 0.22f else 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (sdkBlinking) 1.2.dp else 0.8.dp,
                                color = Color(0xFFEA580C).copy(alpha = if (sdkBlinking) blinkAlpha else 0.5f)
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = if (sdkBlinking) Modifier.graphicsLayer { alpha = blinkAlpha } else Modifier
                        ) {
                            Text(
                                text = "D.Vụ:$sdkPortVal",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

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
