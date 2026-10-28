package com.scanipcamera.app.ui.components

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.company.NetSDK.SDK_EXTPTZ_ControlType
import com.company.NetSDK.SDK_PTZ_ControlType
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.player.DahuaLivePlayerManager
import com.scanipcamera.app.data.player.DahuaTalkManager
import com.scanipcamera.app.data.scanner.DahuaNetSdkManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DahuaLiveViewDialog(
    device: CameraDevice,
    defaultUser: String = "admin",
    defaultPassword: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val player = remember { DahuaLivePlayerManager() }
    val talkManager = remember { DahuaTalkManager() }

    var username by remember { mutableStateOf(defaultUser) }
    var password by remember { mutableStateOf(defaultPassword) }

    var totalChannels by remember { mutableIntStateOf(if (device.channelCount > 0) device.channelCount else 1) }
    var selectedChannel by remember { mutableIntStateOf(0) } // 0-indexed
    var isSubStream by remember { mutableStateOf(true) }

    var isConnecting by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    // Sub-dialogs & panels
    var showPlaybackDialog by remember { mutableStateOf(false) }
    var showPtzPanel by remember { mutableStateOf(false) }
    var isTalking by remember { mutableStateOf(false) }
    var ptzSpeed by remember { mutableIntStateOf(4) } // 1..8

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

    fun takeSnapshot() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ScanIPCamera")
        val picFile = File(storageDir, "Live_${device.ipv4Address}_CH${selectedChannel + 1}_${timeStamp}.jpg")

        val ok = player.captureSnapshot(picFile)
        if (ok) {
            MediaScannerConnection.scanFile(context, arrayOf(picFile.absolutePath), arrayOf("image/jpeg"), null)
            Toast.makeText(context, "Đã lưu ảnh chụp: ${picFile.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Chụp ảnh trực tiếp thất bại", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerTimeSync() {
        coroutineScope.launch {
            val port = if (device.sdkPort > 0) device.sdkPort else 37777
            val res = DahuaNetSdkManager.syncDeviceTime(device.ipv4Address, port, username, password)
            if (res.isSuccess) {
                Toast.makeText(context, "Đã đồng bộ giờ camera thành công!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Đồng bộ giờ thất bại", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerReboot() {
        coroutineScope.launch {
            val port = if (device.sdkPort > 0) device.sdkPort else 37777
            val res = DahuaNetSdkManager.rebootDevice(device.ipv4Address, port, username, password)
            if (res.isSuccess) {
                Toast.makeText(context, "Đã gửi lệnh khởi động lại camera!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Khởi động lại thất bại", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            talkManager.stopTalk()
            player.logout()
        }
    }

    if (showPlaybackDialog) {
        DahuaPlaybackDialog(
            device = device,
            defaultUser = username,
            defaultPassword = password,
            initialChannel = selectedChannel,
            onDismiss = { showPlaybackDialog = false }
        )
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
                            text = "Xem Dahua: ${device.ipv4Address}",
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

                Spacer(modifier = Modifier.height(6.dp))

                // Video Surface View Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

                // QUICK ACTION TOOLBAR (Snapshot, Playback, PTZ, Two-way Talk, TimeSync, Reboot)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Snapshot Button
                    FilledTonalButton(
                        onClick = { takeSnapshot() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chụp ảnh", fontSize = 11.sp)
                    }

                    // 2. Playback Button
                    FilledTonalButton(
                        onClick = { showPlaybackDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xem lại", fontSize = 11.sp)
                    }

                    // 3. PTZ Panel Toggle
                    FilledTonalButton(
                        onClick = { showPtzPanel = !showPtzPanel },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (showPtzPanel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.ControlCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PTZ", fontSize = 11.sp)
                    }

                    // 4. Two-Way Audio Talk
                    FilledTonalButton(
                        onClick = {
                            if (isTalking) {
                                talkManager.stopTalk()
                                isTalking = false
                                Toast.makeText(context, "Đã tắt đàm thoại", Toast.LENGTH_SHORT).show()
                            } else {
                                val handle = player.currentLoginHandle
                                if (handle != 0L) {
                                    val ok = talkManager.startTalk(handle, selectedChannel)
                                    isTalking = ok
                                    if (ok) {
                                        Toast.makeText(context, "Đang đàm thoại 2 chiều...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Bật đàm thoại thất bại", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isTalking) Color(0xFF16A34A).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (isTalking) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (isTalking) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTalking) "Đang nói" else "Đàm thoại", fontSize = 11.sp)
                    }

                    // 5. Time Sync Button
                    OutlinedButton(
                        onClick = { triggerTimeSync() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đồng bộ giờ", fontSize = 11.sp)
                    }

                    // 6. Reboot Device Button
                    OutlinedButton(
                        onClick = { triggerReboot() },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Khởi động lại", fontSize = 11.sp)
                    }
                }

                // Collapsible PTZ Control Pad
                AnimatedVisibility(visible = showPtzPanel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Điều khiển xoay PTZ (Tốc độ: $ptzSpeed)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))

                        // 8-Direction Pad
                        // Row Up (LeftUp, Up, RightUp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PtzButton(
                                icon = Icons.Default.NorthWest,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_EXTPTZ_ControlType.SDK_EXTPTZ_LEFTTOP, ptzSpeed, stop) }
                            )
                            PtzButton(
                                icon = Icons.Default.KeyboardArrowUp,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_UP_CONTROL, ptzSpeed, stop) }
                            )
                            PtzButton(
                                icon = Icons.Default.NorthEast,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_EXTPTZ_ControlType.SDK_EXTPTZ_RIGHTTOP, ptzSpeed, stop) }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row Center (Left, Center Zoom-, Right)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PtzButton(
                                icon = Icons.Default.KeyboardArrowLeft,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_LEFT_CONTROL, ptzSpeed, stop) }
                            )
                            // Zoom Out
                            PtzButton(
                                icon = Icons.Default.ZoomOut,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_ZOOM_DEC_CONTROL, ptzSpeed, stop) }
                            )
                            // Zoom In
                            PtzButton(
                                icon = Icons.Default.ZoomIn,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_ZOOM_ADD_CONTROL, ptzSpeed, stop) }
                            )
                            PtzButton(
                                icon = Icons.Default.KeyboardArrowRight,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_RIGHT_CONTROL, ptzSpeed, stop) }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Row Down (LeftDown, Down, RightDown)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PtzButton(
                                icon = Icons.Default.SouthWest,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_EXTPTZ_ControlType.SDK_EXTPTZ_LEFTDOWN, ptzSpeed, stop) }
                            )
                            PtzButton(
                                icon = Icons.Default.KeyboardArrowDown,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_PTZ_ControlType.SDK_PTZ_DOWN_CONTROL, ptzSpeed, stop) }
                            )
                            PtzButton(
                                icon = Icons.Default.SouthEast,
                                onAction = { stop -> player.ptzControl(selectedChannel, SDK_EXTPTZ_ControlType.SDK_EXTPTZ_RIGHTDOWN, ptzSpeed, stop) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Channels Selector (for NVR/DVR multi-channel)
                if (totalChannels > 1) {
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PtzButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: (isStop: Boolean) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(36.dp)
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        onAction(false)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onAction(true)
                        true
                    }
                    else -> false
                }
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
