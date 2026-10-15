package com.scanipcamera.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.export.HandoverReportGenerator
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.SiteProject
import java.util.UUID

@Composable
fun HandoverReportDialog(
    devices: List<CameraDevice>,
    wanIp: String = "",
    wifiSsid: String = "",
    onDismiss: () -> Unit,
    onSaveProject: (SiteProject) -> Unit
) {
    val context = LocalContext.current

    var siteName by remember { mutableStateOf("Công trình Camera ${wifiSsid.ifEmpty { "Gia đình" }}") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var technicianName by remember { mutableStateOf("Nguyễn Toàn Thắng") }
    var technicianPhone by remember { mutableStateOf("0965620086") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Phiếu Nghiệm Thu & Bàn Giao", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Xuất biên bản bàn giao PDF A4 chuẩn chuyên nghiệp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = siteName,
                        onValueChange = { siteName = it },
                        label = { Text("Tên công trình / Dự án *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            label = { Text("Tên khách hàng") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { customerPhone = it },
                            label = { Text("SĐT khách") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Địa chỉ lắp đặt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = technicianName,
                            onValueChange = { technicianName = it },
                            label = { Text("Kỹ thuật viên") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = technicianPhone,
                            onValueChange = { technicianPhone = it },
                            label = { Text("Hotline KTV") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Summary of devices
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Thiết bị sẽ bàn giao (${devices.size} camera / đầu ghi):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            devices.take(4).forEachIndexed { i, dev ->
                                Text(
                                    text = "${i + 1}. [${dev.brand.displayName}] ${dev.model} - IP: ${dev.ipv4Address} (S/N: ${dev.serialNumber.ifEmpty { "N/A" }})",
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            if (devices.size > 4) {
                                Text(text = "... và ${devices.size - 4} thiết bị khác.", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Branding banner
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Báo cáo có kèm đóng dấu bản quyền cameratrungthuc.com, cameratuson.com và hotline 0965.620.086",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                val project = remember(siteName, customerName, customerPhone, address, technicianName, technicianPhone, devices) {
                    SiteProject(
                        id = UUID.randomUUID().toString(),
                        siteName = siteName.ifEmpty { "Công trình Camera" },
                        customerName = customerName,
                        customerPhone = customerPhone,
                        address = address,
                        technicianName = technicianName,
                        technicianPhone = technicianPhone,
                        wanIp = wanIp,
                        wifiSsid = wifiSsid,
                        devices = devices
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button 1: In / Lưu PDF
                    Button(
                        onClick = {
                            val html = HandoverReportGenerator.generateHtmlReport(project)
                            HandoverReportGenerator.printReport(context, html, "BienBan_${project.siteName.replace(" ", "_")}")
                            onSaveProject(project)
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("In / Lưu PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Button 2: Chia sẻ file HTML
                    Button(
                        onClick = {
                            HandoverReportGenerator.shareHtmlReport(context, project)
                            onSaveProject(project)
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chia sẻ biên bản", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
