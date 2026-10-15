package com.scanipcamera.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.export.HandoverReportGenerator
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.SiteProject
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoverReportDialog(
    devices: List<CameraDevice>,
    wanIp: String = "",
    wifiSsid: String = "",
    initialProject: SiteProject? = null,
    defaultTechnicianName: String = "Nguyễn Toàn Thắng",
    defaultTechnicianPhone: String = "0965620086",
    onDismiss: () -> Unit,
    onSaveProject: (SiteProject) -> Unit
) {
    val context = LocalContext.current

    // Tabs: 0 -> Thông tin công trình, 1 -> Chọn & Gán vị trí Camera, 2 -> Hạng mục nghiệm thu
    var selectedTab by remember { mutableIntStateOf(0) }

    // Form fields
    var siteName by remember {
        mutableStateOf(initialProject?.siteName ?: "")
    }
    var customerName by remember { mutableStateOf(initialProject?.customerName ?: "") }
    var customerPhone by remember { mutableStateOf(initialProject?.customerPhone ?: "") }
    var address by remember { mutableStateOf(initialProject?.address ?: "") }
    var warrantyMonths by remember { mutableIntStateOf(initialProject?.warrantyMonths ?: 24) }
    var notes by remember { mutableStateOf(initialProject?.notes ?: "") }

    // Selected cameras mapping & custom fields
    // Key: device.id, Value: Pair(isSelected, CameraDevice with custom location and pass)
    val deviceConfigs = remember {
        mutableStateMapOf<String, CameraDevice>().apply {
            if (initialProject != null && initialProject.devices.isNotEmpty()) {
                initialProject.devices.forEach { dev -> put(dev.id, dev) }
            } else {
                devices.forEachIndexed { idx, dev ->
                    val defaultLoc = when (idx) {
                        0 -> "Cổng chính"
                        1 -> "Sân trước"
                        2 -> "Phòng khách"
                        3 -> "Hành lang"
                        4 -> "Sân sau"
                        else -> "Vị trí ${idx + 1}"
                    }
                    put(dev.id, dev.copy(installLocation = defaultLoc, handoverUser = "admin", handoverPass = "admin123"))
                }
            }
        }
    }

    val selectedDeviceIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            if (initialProject != null && initialProject.devices.isNotEmpty()) {
                initialProject.devices.forEach { dev -> put(dev.id, true) }
            } else {
                devices.forEach { dev -> put(dev.id, true) }
            }
        }
    }

    // Handover checklist
    val checklistItems = remember {
        mutableStateListOf(
            "Đã cài đặt phần mềm xem qua điện thoại cho chủ nhà",
            "Đã hướng dẫn xem lại video và cách đổi mật khẩu an toàn",
            "Đã kiểm tra góc nhìn ban ngày và hồng ngoại ban đêm",
            "Đã cấu hình tự động đồng bộ giờ NTP chính xác",
            "Đã bàn giao đầy đủ nguồn adapter, jack nối và phụ kiện"
        )
    }

    fun buildCurrentProject(): SiteProject {
        val chosenDevices = deviceConfigs.values.filter { selectedDeviceIds[it.id] == true }
        return SiteProject(
            id = initialProject?.id ?: UUID.randomUUID().toString(),
            siteName = siteName.ifEmpty { "Công trình Camera" },
            customerName = customerName,
            customerPhone = customerPhone,
            address = address,
            technicianName = defaultTechnicianName,
            technicianPhone = defaultTechnicianPhone,
            createdAt = initialProject?.createdAt ?: System.currentTimeMillis(),
            wanIp = wanIp,
            wifiSsid = wifiSsid,
            warrantyMonths = warrantyMonths,
            notes = notes,
            handoverChecklist = checklistItems.toList(),
            devices = chosenDevices
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.95f),
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (initialProject != null) "Chỉnh Sửa Dự Án & Biên Bản" else "Thêm Dự Án & Bàn Giao Camera",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Lập biên bản bàn giao, gán vị trí và nghiệm thu công trình",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("1. Khách hàng", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val count = selectedDeviceIds.values.count { it }
                            Text("2. Thiết bị ($count)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("3. Nghiệm thu", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        // TAB 0: Customer & Site Details
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = siteName,
                                onValueChange = { siteName = it },
                                label = { Text("Tên công trình / Dự án *") },
                                placeholder = { Text("Tên công trình của bạn") },
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
                                    placeholder = { Text("Anh Hưng") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                                OutlinedTextField(
                                    value = customerPhone,
                                    onValueChange = { customerPhone = it },
                                    label = { Text("SĐT khách hàng") },
                                    placeholder = { Text("0988xxxxxx") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it },
                                label = { Text("Địa chỉ lắp đặt") },
                                placeholder = { Text("Số nhà, đường phố, phường/xã, quận/huyện") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Warranty Duration Chips
                            Column {
                                Text(
                                    text = "Thời hạn bảo hành chính hãng:",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(12, 24, 36).forEach { months ->
                                        FilterChip(
                                            selected = warrantyMonths == months,
                                            onClick = { warrantyMonths = months },
                                            label = { Text("$months Tháng", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.height(30.dp)
                                        )
                                    }
                                }
                            }

                            // Technician info card from Profile
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "KTV: $defaultTechnicianName • $defaultTechnicianPhone",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Được cấu hình trong mục Cài đặt tài khoản",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Ghi chú / Lưu ý cho khách hàng") },
                                placeholder = { Text("Ví dụ: Đã cài app DMSS/iVMS trên 2 điện thoại; Thẻ nhớ 64GB bảo hành theo hãng...") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // TAB 1: Cameras Selection & Location assignment
                        1 -> Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tích chọn camera bàn giao & đặt vị trí:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    TextButton(
                                        onClick = {
                                            deviceConfigs.keys.forEach { selectedDeviceIds[it] = true }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Chọn tất cả", fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            deviceConfigs.keys.forEach { selectedDeviceIds[it] = false }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Bỏ chọn", fontSize = 11.sp)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(deviceConfigs.values.toList(), key = { _, dev -> dev.id }) { index, dev ->
                                    val isSelected = selectedDeviceIds[dev.id] == true
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { selectedDeviceIds[dev.id] = it },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = dev.brand.badgeColor
                                                ) {
                                                    Text(
                                                        text = dev.brand.displayName,
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = dev.model.ifEmpty { "Camera IP" },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = dev.ipv4Address,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF0284C7)
                                                )
                                            }

                                            if (isSelected) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = dev.installLocation,
                                                        onValueChange = { newLoc ->
                                                            deviceConfigs[dev.id] = dev.copy(installLocation = newLoc)
                                                        },
                                                        label = { Text("Vị trí lắp *", fontSize = 10.sp) },
                                                        placeholder = { Text("VD: Cổng chính, Sân trước...", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1.2f)
                                                    )
                                                    OutlinedTextField(
                                                        value = dev.handoverPass,
                                                        onValueChange = { newPass ->
                                                            deviceConfigs[dev.id] = dev.copy(handoverPass = newPass)
                                                        },
                                                        label = { Text("Mật khẩu bàn giao", fontSize = 10.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                // Location quick chips
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    listOf("Cổng chính", "Sân trước", "Phòng khách", "Hành lang", "Bếp", "Kho hàng", "Sân thượng").forEach { chipLoc ->
                                                        SuggestionChip(
                                                            onClick = {
                                                                deviceConfigs[dev.id] = dev.copy(installLocation = chipLoc)
                                                            },
                                                            label = { Text(chipLoc, fontSize = 9.5.sp) },
                                                            modifier = Modifier.height(26.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TAB 2: Handover Checklist & Verification
                        2 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Các tiêu chuẩn kỹ thuật & Nghiệm thu:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0369A1)
                            )

                            checklistItems.forEachIndexed { i, item ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.08f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = item, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Đơn vị thi công bảo hành:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "• Hotline / Zalo hỗ trợ: 0965.620.086\n• Email: toanhangts@gmail.com\n• Website: cameratrungthuc.com - cameratuson.com",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Bottom Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Print A4 Button
                        Button(
                            onClick = {
                                val project = buildCurrentProject()
                                onSaveProject(project)
                                HandoverReportGenerator.printReport(context, project)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("In A4 / PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Share PDF/HTML file
                        FilledTonalButton(
                            onClick = {
                                val project = buildCurrentProject()
                                onSaveProject(project)
                                HandoverReportGenerator.shareReport(context, project)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gửi file", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // 3. Quick Zalo Text Copy
                        OutlinedButton(
                            onClick = {
                                val project = buildCurrentProject()
                                onSaveProject(project)
                                val zaloText = HandoverReportGenerator.generateZaloSummaryText(project)
                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clip.setPrimaryClip(ClipData.newPlainText("Bàn giao camera", zaloText))
                                Toast.makeText(context, "Đã sao chép nội dung bàn giao gửi Zalo!", Toast.LENGTH_SHORT).show()
                                val zaloIntent = context.packageManager.getLaunchIntentForPackage("com.zing.zalo")
                                if (zaloIntent != null) context.startActivity(zaloIntent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gửi Zalo", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                    }

                    // Save Project Button
                    Button(
                        onClick = {
                            val project = buildCurrentProject()
                            onSaveProject(project)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LƯU HỒ SƠ DỰ ÁN", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
