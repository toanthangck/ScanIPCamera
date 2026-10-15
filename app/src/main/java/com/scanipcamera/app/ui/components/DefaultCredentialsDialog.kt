package com.scanipcamera.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefaultCredentialsDialog(
    currentUsername: String = "admin",
    currentCameraPassword: String = "admin123",
    currentNvrPassword: String = "admin12345",
    onDismiss: () -> Unit,
    onSave: (username: String, cameraPassword: String, nvrPassword: String) -> Unit
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf(currentUsername) }
    var cameraPassword by remember { mutableStateOf(currentCameraPassword) }
    var nvrPassword by remember { mutableStateOf(currentNvrPassword) }

    var showCamPass by remember { mutableStateOf(false) }
    var showNvrPass by remember { mutableStateOf(false) }

    val userPresets = remember { listOf("admin", "root", "supervisor") }
    val camPassPresets = remember { listOf("admin123", "Admin@123", "admin12345", "123456", "admin@2024", "Camera@123") }
    val nvrPassPresets = remember { listOf("admin12345", "Admin@123", "admin123", "12345678", "Dahua@123", "Hik@12345") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mật Khẩu Mặc Định Thiết Bị",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Cấu hình tài khoản & mật khẩu mặc định của Camera và Đầu ghi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Section 1: Username
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TÀI KHOẢN QUẢN TRỊ (ADMIN USERNAME)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Tên tài khoản mặc định") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Gợi ý:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            userPresets.forEach { u ->
                                SuggestionChip(
                                    onClick = { username = u },
                                    label = { Text(u, fontSize = 10.5.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }
                }

                // Section 2: Camera Default Password
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MẬT KHẨU MẮT CAMERA MẶC ĐỊNH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = cameraPassword,
                            onValueChange = { cameraPassword = it },
                            label = { Text("Mật khẩu mắt Camera") },
                            singleLine = true,
                            visualTransformation = if (showCamPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showCamPass = !showCamPass }) {
                                    Icon(
                                        imageVector = if (showCamPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Mật khẩu mắt cam thông dụng:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                camPassPresets.forEach { p ->
                                    SuggestionChip(
                                        onClick = { cameraPassword = p },
                                        label = { Text(p, fontSize = 10.sp) },
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: NVR Default Password
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dvr, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MẬT KHẨU ĐẦU GHI (NVR / DVR) MẶC ĐỊNH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED)
                            )
                        }
                        OutlinedTextField(
                            value = nvrPassword,
                            onValueChange = { nvrPassword = it },
                            label = { Text("Mật khẩu đầu ghi hình") },
                            singleLine = true,
                            visualTransformation = if (showNvrPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showNvrPass = !showNvrPass }) {
                                    Icon(
                                        imageVector = if (showNvrPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Mật khẩu đầu ghi thông dụng:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                nvrPassPresets.forEach { p ->
                                    SuggestionChip(
                                        onClick = { nvrPassword = p },
                                        label = { Text(p, fontSize = 10.sp) },
                                        modifier = Modifier.height(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = {
                            onSave(username.trim(), cameraPassword.trim(), nvrPassword.trim())
                            Toast.makeText(context, "Đã lưu cài đặt mật khẩu mặc định!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lưu Cấu Hình Mặc Định")
                    }
                }
            }
        }
    }
}
