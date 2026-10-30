package com.scanipcamera.app.ui.components

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
    val now = Calendar.getInstance()
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }

    // Recorded days calendar state
    var recordedDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoadingCalendar by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(true) }

    // Current seek time in seconds of day (0..86399)
    var currentSecOfDay by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60) }
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
            val loggedIn = if (!playbackManager.isLoggedIn) {
                val loginRes = playbackManager.login(device.ipv4Address, port, username, password)
                if (loginRes.isSuccess) {
                    totalChannels = maxOf(loginRes.getOrDefault(1), totalChannels)
                    true
                } else {
                    isConnecting = false
                    isPlaying = false
                    errorMessage = loginRes.exceptionOrNull()?.message ?: "Đăng nhập thất bại"
                    false
                }
            } else true

            if (loggedIn) {
                val startH = currentSecOfDay / 3600
                val startM = (currentSecOfDay % 3600) / 60
                val startS = currentSecOfDay % 60

                val startTime = makeNetTime(startH, startM, startS)
                val endTime = makeNetTime(23, 59, 59)

                val ok = playbackManager.startPlayback(selectedChannel, startTime, endTime, sv)
                isConnecting = false
                isPlaying = ok
                if (!ok) {
                    errorMessage = "Không có video ngày %02d/%02d/%04d lúc %s (CH %d)".format(
                        selectedDay, selectedMonth, selectedYear, formatTime(currentSecOfDay), selectedChannel + 1
                    )
                }
            }
        }
    }

    fun loadCalendarForMonth(year: Int, month: Int) {
        coroutineScope.launch {
            isLoadingCalendar = true
            val port = if (device.sdkPort > 0) device.sdkPort else 37777
            val days = playbackManager.queryRecordedDaysInMonth(
                ip = device.ipv4Address,
                port = port,
                username = username,
                password = password,
                channel = selectedChannel,
                year = year,
                month = month
            )
            recordedDays = days
            isLoadingCalendar = false

            // Auto-switch to latest recorded day if selected day has no recording
            if (days.isNotEmpty() && !days.contains(selectedDay)) {
                days.maxOrNull()?.let { latestDay ->
                    selectedDay = latestDay
                    currentSecOfDay = 0
                    startPlaybackFromCurrentTime()
                }
            }
        }
    }

    LaunchedEffect(selectedYear, selectedMonth, selectedChannel) {
        loadCalendarForMonth(selectedYear, selectedMonth)
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
            playbackManager.logoutSync()
        }
    }

    BackHandler {
        playbackManager.logoutSync()
        onDismiss()
    }

    // In-Activity Modal Overlay (Avoids MediaTek Dialog-SurfaceView crash)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .zIndex(150f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Keep open when tapping backdrop scrim */ },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        Column {
                            Text(
                                text = "Xem lại ghi hình: ${device.ipv4Address}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "Ngày: %02d/%02d/%04d • Đang phát: %s".format(
                                    selectedDay, selectedMonth, selectedYear, formatTime(currentSecOfDay)
                                ),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            playbackManager.logoutSync()
                            onDismiss()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", modifier = Modifier.size(19.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Fixed Video Box (16:9 ratio, height 180.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                                        playbackManager.stopPlaybackSync()
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay when connecting or error
                    if (isConnecting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.height(5.dp))
                            Text("Đang tải dữ liệu ghi hình...", color = Color.White, fontSize = 11.sp)
                        }
                    } else if (errorMessage != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(errorMessage ?: "", color = MaterialTheme.colorScheme.errorContainer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { startPlaybackFromCurrentTime() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thử lại", fontSize = 10.5.sp)
                            }
                        }
                    }
                }

                // Scrollable Controls & Calendar below video
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Timeline Seekbar (00:00:00 -> 23:59:59)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentSecOfDay),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(52.dp)
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
                            fontSize = 9.5.sp,
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
                                    isPaused = nextPause
                                    coroutineScope.launch {
                                        playbackManager.pause(nextPause)
                                    }
                                } else {
                                    startPlaybackFromCurrentTime()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused || !isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Phát" else "Tạm dừng",
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Slow 1/2x
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (playbackManager.slowMotion()) {
                                        currentSpeedText = "1/2x"
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("1/2x", fontSize = 10.sp)
                        }

                        // Normal 1x
                        FilledTonalButton(
                            onClick = {
                                surfaceViewRef?.let { sv ->
                                    coroutineScope.launch {
                                        if (playbackManager.normalSpeed(sv)) {
                                            currentSpeedText = "1x"
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(currentSpeedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Fast 2x/4x
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (playbackManager.fastForward()) {
                                        currentSpeedText = if (currentSpeedText == "2x") "4x" else "2x"
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = "Tua nhanh", modifier = Modifier.size(13.dp))
                        }

                        // Snapshot
                        FilledTonalIconButton(
                            onClick = {
                                coroutineScope.launch {
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
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Chụp ảnh", modifier = Modifier.size(17.dp))
                        }

                        // Download Segment
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
                                                Toast.makeText(context, "Đã tải video về máy: ${recFile.name}", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, msg ?: "Lỗi tải video", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isDownloading) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (isDownloading) Icons.Default.Close else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isDownloading) "$downloadProgress%" else "Tải đoạn", fontSize = 10.sp)
                        }
                    }

                    // Download progress bar
                    if (isDownloading || downloadStatusText != null) {
                        if (isDownloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                        downloadStatusText?.let {
                            Text(text = it, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

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
                                    label = { Text("CH ${ch + 1}", fontSize = 10.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }

                    // 📅 Monthly Record Calendar Header Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showCalendar = !showCalendar }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lịch ghi hình tháng %02d/%04d".format(selectedMonth, selectedYear),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (recordedDays.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFF059669),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${recordedDays.size} ngày có video",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoadingCalendar) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Icon(
                                imageVector = if (showCalendar) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCalendar) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            MonthRecordCalendar(
                                year = selectedYear,
                                month = selectedMonth,
                                selectedDay = selectedDay,
                                recordedDays = recordedDays,
                                isLoading = isLoadingCalendar,
                                onPreviousMonth = {
                                    if (selectedMonth == 1) {
                                        selectedMonth = 12
                                        selectedYear -= 1
                                    } else {
                                        selectedMonth -= 1
                                    }
                                    loadCalendarForMonth(selectedYear, selectedMonth)
                                },
                                onNextMonth = {
                                    if (selectedMonth == 12) {
                                        selectedMonth = 1
                                        selectedYear += 1
                                    } else {
                                        selectedMonth += 1
                                    }
                                    loadCalendarForMonth(selectedYear, selectedMonth)
                                },
                                onSelectDay = { day ->
                                    selectedDay = day
                                    // Start from beginning of that day
                                    currentSecOfDay = 0
                                    startPlaybackFromCurrentTime()
                                }
                            )

                            // Visual Legend
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 12.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFE0F2FE))
                                            .border(1.5.dp, Color(0xFF0284C7), RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tô đậm: Có video",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0369A1)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 12.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF0284C7))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Đang chọn",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Chấm xanh: File ghi",
                                        fontSize = 9.sp,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthRecordCalendar(
    year: Int,
    month: Int,
    selectedDay: Int,
    recordedDays: Set<Int>,
    isLoading: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (day: Int) -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // 0 = Monday, ..., 6 = Sunday
    val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val totalSlots = firstDayOfWeek + daysInMonth
    val totalRows = (totalSlots + 6) / 7

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            // Month Header & Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Tháng trước", modifier = Modifier.size(19.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tháng %02d / %04d".format(month, year),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(6.dp))
                        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                    } else if (recordedDays.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF059669),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "${recordedDays.size} ngày có video",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau", modifier = Modifier.size(19.dp))
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Weekday Headers (T2 -> CN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Calendar Grid: Bold highlighting for recorded days
            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0 until 7) {
                        val slotIndex = row * 7 + col
                        val dayNum = slotIndex - firstDayOfWeek + 1
                        if (dayNum in 1..daysInMonth) {
                            val hasRecord = recordedDays.contains(dayNum)
                            val isSelected = (dayNum == selectedDay)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.5.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isSelected && hasRecord -> Color(0xFF0284C7)
                                            isSelected && !hasRecord -> MaterialTheme.colorScheme.primary
                                            hasRecord -> Color(0xFFE0F2FE) // Tô đậm: Nền xanh sáng rõ nét
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        when {
                                            hasRecord && !isSelected -> Modifier.border(
                                                width = 2.dp,
                                                color = Color(0xFF0284C7), // Tô đậm: Viền xanh đậm nổi bật
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            isSelected && hasRecord -> Modifier.border(
                                                width = 1.5.dp,
                                                color = Color(0xFFFDE047), // Viền vàng nổi bật khi đang chọn ngày có video
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            else -> Modifier
                                        }
                                    )
                                    .clickable { onSelectDay(dayNum) }
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$dayNum",
                                        fontSize = if (hasRecord) 12.5.sp else 11.sp,
                                        fontWeight = if (hasRecord) FontWeight.Black else if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            hasRecord -> Color(0xFF0369A1) // Chữ số đậm màu xanh sắc nét
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                                        }
                                    )
                                    if (hasRecord) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 12.dp, height = 3.5.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isSelected) Color(0xFF86EFAC) else Color(0xFF16A34A))
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(3.5.dp))
                                    }
                                }
                            }
                        } else {
                            // Blank cell
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
