package com.scanipcamera.app.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.company.NetSDK.NET_TIME
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.player.DahuaPlaybackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DahuaPlaybackDialog(
    device: CameraDevice,
    defaultUser: String = "admin",
    defaultPassword: String = "",
    initialChannel: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playbackManager = remember { DahuaPlaybackManager() }

    var username by remember { mutableStateOf(defaultUser) }
    var password by remember { mutableStateOf(defaultPassword) }

    var totalChannels by remember { mutableIntStateOf(if (device.channelCount > 0) device.channelCount else 1) }
    var selectedChannel by remember { mutableIntStateOf(initialChannel) }

    // Date state (default today)
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }

    // Current seek time in seconds of day (0..86399)
    var currentSecOfDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 3600 + Calendar.getInstance().get(Calendar.MINUTE) * 60) }
    var isDraggingSlider by remember { mutableStateOf(false) }

    var isConnecting by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentSpeedText by remember { mutableStateOf("1x") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    // Download state
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadStatusText by remember { mutableStateOf<String?>(null) }

    fun formatTime(sec: Int): String {
        val s = sec.coerceIn(0, 86399)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sc = s % 60
        return "%02d:%02d:%02d".format(h, m, sc)
    }

    fun makeNetTime(hour: Int, min: Int, sec: Int): NET_TIME {
        return NET_TIME().apply {
            setTime(
                selectedYear.toLong(),
                selectedMonth.toLong(),
                selectedDay.toLong(),
                hour.toLong(),
                min.toLong(),
                sec.toLong()
            )
        }
    }

    fun startPlaybackFromCurrentTime() {
        val sv = surfaceViewRef ?: return
        isConnecting = true
        errorMessage = null
        isPaused = false
        currentSpeedText = "1x"

        coroutineScope.launch {
            val port = if (device.sdkPort > 0) device.sdkPort else 37777
            val loginRes = playbackManager.login(device.ipv4Address, port, username, password)
            if (loginRes.isSuccess) {
                totalChannels = maxOf(loginRes.getOrDefault(1), totalChannels)

                val startH = currentSecOfDay / 3600
                val startM = (currentSecOfDay % 3600) / 60
                val startS = currentSecOfDay % 60

                val startTime = makeNetTime(startH, startM, startS)
                val endTime = makeNetTime(23, 59, 59)

                val ok = playbackManager.startPlayback(selectedChannel, startTime, endTime, sv)
                isConnecting = false
                isPlaying = ok
                if (!ok) {
                    errorMessage = "Không tìm thấy dữ liệu ghi hình kênh ${selectedChannel + 1} tại thời điểm này"
                }
            } else {
                isConnecting = false
                isPlaying = false
                errorMessage = loginRes.exceptionOrNull()?.message ?: "Đăng nhập thất bại"
            }
        }
    }

    // Polling OSD time to update timeline slider
    LaunchedEffect(isPlaying, isPaused, isDraggingSlider) {
        while (isPlaying && !isPaused && !isDraggingSlider) {
            delay(1000)
            val osd = playbackManager.getOsdTime()
            if (osd != null && osd.dwYear > 2000) {
                val sec = (osd.dwHour * 3600 + osd.dwMinute * 60 + osd.dwSecond).toInt()
                currentSecOfDay = sec.coerceIn(0, 86399)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playbackManager.logout()
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xem lại ghi hình: ${device.ipv4Address}",
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
                    text = "Ngày: %04d-%02d-%02d • Đang xem: %s".format(selectedYear, selectedMonth, selectedDay, formatTime(currentSecOfDay)),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // SurfaceView Box
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
                                        playbackManager.initSurface(this@apply)
                                        startPlaybackFromCurrentTime()
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        playbackManager.stopPlayback()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay when connecting or error
                    if (isConnecting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Đang tải dữ liệu ghi hình...", color = Color.White, fontSize = 11.sp)
                        }
                    } else if (errorMessage != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.errorContainer, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { startPlaybackFromCurrentTime() },
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

                // Timeline Seekbar (00:00:00 -> 23:59:59)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentSecOfDay),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(55.dp)
                    )
                    Slider(
                        value = currentSecOfDay.toFloat(),
                        onValueChange = {
                            isDraggingSlider = true
                            currentSecOfDay = it.toInt()
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            startPlaybackFromCurrentTime()
                        },
                        valueRange = 0f..86399f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "23:59",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Playback Control Buttons (Pause/Play, Fast, Slow, Snapshot, Download)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause
                    FilledTonalIconButton(
                        onClick = {
                            if (isPlaying) {
                                val nextPause = !isPaused
                                playbackManager.pause(nextPause)
                                isPaused = nextPause
                            } else {
                                startPlaybackFromCurrentTime()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused || !isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Phát" else "Tạm dừng",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Slow 1/2x
                    FilledTonalButton(
                        onClick = {
                            if (playbackManager.slowMotion()) {
                                currentSpeedText = "1/2x"
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("1/2x", fontSize = 10.5.sp)
                    }

                    // Normal 1x
                    FilledTonalButton(
                        onClick = {
                            surfaceViewRef?.let {
                                if (playbackManager.normalSpeed(it)) {
                                    currentSpeedText = "1x"
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(currentSpeedText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Fast 2x/4x
                    FilledTonalButton(
                        onClick = {
                            if (playbackManager.fastForward()) {
                                currentSpeedText = if (currentSpeedText == "2x") "4x" else "2x"
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Tua nhanh", modifier = Modifier.size(14.dp))
                    }

                    // Snapshot from Playback
                    FilledTonalIconButton(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val storageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "ScanIPCamera")
                            val picFile = File(storageDir, "Playback_${device.ipv4Address}_${timeStamp}.jpg")
                            val ok = playbackManager.captureSnapshot(picFile)
                            if (ok) {
                                MediaScannerConnection.scanFile(context, arrayOf(picFile.absolutePath), arrayOf("image/jpeg"), null)
                                Toast.makeText(context, "Đã lưu ảnh xem lại: ${picFile.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Chụp ảnh xem lại thất bại", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Chụp ảnh", modifier = Modifier.size(18.dp))
                    }

                    // Download Record Segment (5-minute segment from current seek time)
                    FilledTonalButton(
                        onClick = {
                            if (isDownloading) {
                                playbackManager.stopDownload()
                                isDownloading = false
                                downloadStatusText = "Đã hủy tải video"
                            } else {
                                isDownloading = true
                                downloadProgress = 0
                                downloadStatusText = "Đang tải đoạn video..."

                                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ScanIPCamera")
                                val recFile = File(moviesDir, "Record_CH${selectedChannel + 1}_${timeStamp}.dav")

                                val startH = currentSecOfDay / 3600
                                val startM = (currentSecOfDay % 3600) / 60
                                val startS = currentSecOfDay % 60
                                val endSec = minOf(currentSecOfDay + 300, 86399) // 5 minutes segment
                                val endH = endSec / 3600
                                val endM = (endSec % 3600) / 60
                                val endS = endSec % 60

                                val sTime = makeNetTime(startH, startM, startS)
                                val eTime = makeNetTime(endH, endM, endS)

                                playbackManager.startDownload(
                                    channel = selectedChannel,
                                    startTime = sTime,
                                    endTime = eTime,
                                    outputFile = recFile,
                                    onProgress = { pct -> downloadProgress = pct },
                                    onFinished = { success, msg ->
                                        isDownloading = false
                                        downloadStatusText = msg
                                        if (success) {
                                            MediaScannerConnection.scanFile(context, arrayOf(recFile.absolutePath), null, null)
                                            Toast.makeText(context, "Đã tải đoạn video về máy: ${recFile.name}", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, msg ?: "Lỗi tải video", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDownloading) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isDownloading) Icons.Default.Close else Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDownloading) "$downloadProgress%" else "Tải đoạn", fontSize = 10.5.sp)
                    }
                }

                // Download progress bar
                if (isDownloading || downloadStatusText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                    downloadStatusText?.let {
                        Text(text = it, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
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
                                        startPlaybackFromCurrentTime()
                                    }
                                },
                                label = { Text("CH ${ch + 1}", fontSize = 10.5.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
