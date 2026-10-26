package com.scanipcamera.app.ui.components

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.player.DahuaLivePlayerManager
import kotlinx.coroutines.launch

@Composable
fun DahuaLiveViewDialog(
    device: CameraDevice,
    defaultUser: String = "admin",
    defaultPassword: String = "",
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val player = remember { DahuaLivePlayerManager() }

    var username by remember { mutableStateOf(defaultUser) }
    var password by remember { mutableStateOf(defaultPassword) }

    var totalChannels by remember { mutableIntStateOf(if (device.channelCount > 0) device.channelCount else 1) }
    var selectedChannel by remember { mutableIntStateOf(0) } // 0-indexed
    var isSubStream by remember { mutableStateOf(true) } // default sub stream for fast mobile loading

    var isConnecting by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    fun connectAndPlay(ch: Int, sub: Boolean) {
        val sv = surfaceViewRef ?: return
        isConnecting = true
        errorMessage = null

        coroutineScope.launch {
            val port = if (device.sdkPort > 0) device.sdkPort else 37777
            val loginResult = player.login(
                ip = device.ipv4Address,
                port = port,
                username = username,
                password = password
            )

            if (loginResult.isSuccess) {
                totalChannels = maxOf(loginResult.getOrDefault(1), totalChannels)
                val ok = player.startPlay(channel = ch, isSubStream = sub, surfaceView = sv)
                isConnecting = false
                isPlaying = ok
                if (!ok) {
                    errorMessage = "Không thể lấy luồng video kênh ${ch + 1}"
                }
            } else {
                isConnecting = false
                isPlaying = false
                errorMessage = loginResult.exceptionOrNull()?.message ?: "Đăng nhập thất bại"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.logout()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xem trực tiếp Dahua: ${device.ipv4Address}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "${device.model} • ${if (totalChannels > 1) "$totalChannels kênh" else "Camera đơn"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Video Surface View Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        surfaceViewRef = this@apply
                                        player.initSurface(this@apply)
                                        connectAndPlay(selectedChannel, isSubStream)
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        player.stopPlay()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay when connecting or error
                    if (isConnecting) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Đang kết nối Kênh ${selectedChannel + 1}...",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    } else if (errorMessage != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "Lỗi phát video",
                                color = MaterialTheme.colorScheme.errorContainer,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { connectAndPlay(selectedChannel, isSubStream) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thử lại", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Channels Selector (for NVR/DVR multi-channel)
                if (totalChannels > 1) {
                    Text(
                        text = "Chọn mắt camera (${totalChannels} kênh):",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (ch in 0 until totalChannels) {
                            val isSelected = (selectedChannel == ch)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (selectedChannel != ch) {
                                        selectedChannel = ch
                                        connectAndPlay(ch, isSubStream)
                                    }
                                },
                                label = { Text("CH ${ch + 1}", fontSize = 10.5.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Stream Quality Selector: Sub (Smooth) vs Main (HD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chất lượng:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isSubStream,
                            onClick = {
                                if (!isSubStream) {
                                    isSubStream = true
                                    connectAndPlay(selectedChannel, true)
                                }
                            },
                            label = { Text("Phụ (Mượt)", fontSize = 10.5.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                        FilterChip(
                            selected = !isSubStream,
                            onClick = {
                                if (isSubStream) {
                                    isSubStream = false
                                    connectAndPlay(selectedChannel, false)
                                }
                            },
                            label = { Text("Chính (HD)", fontSize = 10.5.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Credentials row: Username & Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("User", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.38f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu", fontSize = 10.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.weight(0.42f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                    )
                    Button(
                        onClick = { connectAndPlay(selectedChannel, isSubStream) },
                        modifier = Modifier.height(48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Phát", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
