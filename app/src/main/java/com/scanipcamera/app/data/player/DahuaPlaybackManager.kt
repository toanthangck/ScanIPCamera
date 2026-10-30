package com.scanipcamera.app.data.player

import android.util.Log
import android.view.SurfaceView
import com.company.NetSDK.CB_fDataCallBack
import com.company.NetSDK.CB_fDownLoadPosCallBack
import com.company.NetSDK.CB_fTimeDownLoadPosCallBack
import com.company.NetSDK.EM_LOGIN_SPAC_CAP_TYPE
import com.company.NetSDK.EM_QUERY_RECORD_TYPE
import com.company.NetSDK.EM_USEDEV_MODE
import com.company.NetSDK.FinalVar
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_DEVICEINFO_Ex
import com.company.NetSDK.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_RECORDFILE_INFO
import com.company.NetSDK.NET_RECORD_TYPE
import com.company.NetSDK.NET_TIME
import com.company.PlaySDK.Constants
import com.company.PlaySDK.IPlaySDK
import com.scanipcamera.app.data.scanner.DahuaNetSdkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles Dahua / IMOU / KBVISION Playback by time, Timeline seek, Speed controls,
 * Snapshot during playback, and Video Download.
 */
class DahuaPlaybackManager {

    companion object {
        private const val TAG = "DahuaPlaybackManager"
        private const val STREAM_BUFFER_SIZE = 1024 * 1024 * 2
    }

    private var loginHandle: Long = 0L
    private var playHandle: Long = 0L
    private var playPort: Int = -1
    private var downloadHandle: Long = 0L

    // Credentials cache for independent search sessions
    private var currentIp: String = ""
    private var currentPort: Int = 37777
    private var currentUsername: String = "admin"
    private var currentPassword: String = ""

    // Retain strong references to native callbacks to prevent GC collection during playback
    private var dataCallback: CB_fDataCallBack? = null
    private var posCallback: CB_fDownLoadPosCallBack? = null
    private var downloadCallback: CB_fTimeDownLoadPosCallBack? = null

    var channelCount: Int = 1
        private set

    val isPlaying: Boolean get() = playHandle != 0L
    val isDownloading: Boolean get() = downloadHandle != 0L
    val isLoggedIn: Boolean get() = loginHandle != 0L

    init {
        DahuaNetSdkManager.init()
    }

    suspend fun login(
        ip: String,
        port: Int = 37777,
        username: String = "admin",
        password: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        logout()
        currentIp = ip
        currentPort = port
        currentUsername = username
        currentPassword = password

        try {
            val stuIn = NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY().apply {
                val ipBytes = ip.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(ipBytes, 0, szIP, 0, minOf(ipBytes.size, szIP.size))
                nPort = port
                val userBytes = username.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                val pwdBytes = password.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(pwdBytes, 0, szPassword, 0, minOf(pwdBytes.size, szPassword.size))
                emSpecCap = EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_TCP
            }
            val stuOut = NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY()
            loginHandle = INetSDK.LoginWithHighLevelSecurity(stuIn, stuOut)

            if (loginHandle != 0L) {
                val chans = stuOut.stuDeviceInfo?.nChanNum ?: 1
                channelCount = if (chans > 0) chans else 1
                return@withContext Result.success(channelCount)
            }

            // Fallback: LoginEx2
            val devInfo = NET_DEVICEINFO_Ex()
            loginHandle = INetSDK.LoginEx2(ip.trim(), port, username.trim(), password.trim(), 0, null, devInfo, 0)
            if (loginHandle != 0L) {
                val chans = devInfo.nChanNum
                channelCount = if (chans > 0) chans else 1
                return@withContext Result.success(channelCount)
            }

            val lastErr = INetSDK.GetLastError()
            Result.failure(Exception("Đăng nhập thất bại (Mã lỗi: $lastErr)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun initSurface(surfaceView: SurfaceView) {
        if (playPort < 0) {
            playPort = IPlaySDK.PLAYGetFreePort()
        }
        IPlaySDK.InitSurface(playPort, surfaceView)
    }

    suspend fun startPlayback(
        channel: Int,
        startTime: NET_TIME,
        endTime: NET_TIME,
        surfaceView: SurfaceView,
        onPosUpdate: ((totalSize: Int, currentSize: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (loginHandle == 0L) {
            Log.e(TAG, "Cannot startPlayback: Not logged in")
            return@withContext false
        }

        stopPlaybackInternal()

        try {
            if (playPort < 0) {
                playPort = IPlaySDK.PLAYGetFreePort()
            }
            IPlaySDK.InitSurface(playPort, surfaceView)

            // Setup Stream
            val openStream = IPlaySDK.PLAYOpenStream(playPort, null, 0, STREAM_BUFFER_SIZE)
            if (openStream == 0) {
                Log.e(TAG, "PLAYOpenStream failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                return@withContext false
            }

            IPlaySDK.PLAYSetStreamOpenMode(playPort, Constants.STREAME_FILE)

            val play = IPlaySDK.PLAYPlay(playPort, surfaceView)
            if (play == 0) {
                Log.e(TAG, "PLAYPlay failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                IPlaySDK.PLAYCloseStream(playPort)
                return@withContext false
            }

            IPlaySDK.PLAYSetCacheMode(playPort, 1)
            IPlaySDK.PLAYEnableLargePicAdjustment(playPort, 1)
            IPlaySDK.PLAYPlaySoundShare(playPort)

            // Device Record Type
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_RECORD_STREAM_TYPE, 0)
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_RECORD_TYPE, NET_RECORD_TYPE.NET_RECORD_TYPE_ALL)

            val targetPort = playPort
            this@DahuaPlaybackManager.dataCallback = CB_fDataCallBack { _, dwDataType, buffer, dwBufSize ->
                if (playHandle != 0L && playPort >= 0 && dwDataType == 0 && buffer != null) {
                    try {
                        IPlaySDK.PLAYInputData(targetPort, buffer, dwBufSize)
                    } catch (t: Throwable) {
                        0
                    }
                } else 1
            }

            this@DahuaPlaybackManager.posCallback = CB_fDownLoadPosCallBack { _, total, current ->
                onPosUpdate?.invoke(total, current)
            }

            playHandle = INetSDK.PlayBackByTimeEx(loginHandle, channel, startTime, endTime, posCallback, dataCallback)
            if (playHandle == 0L) {
                val err = INetSDK.GetLastError()
                Log.e(TAG, "PlayBackByTimeEx failed, err=$err")
                IPlaySDK.PLAYStop(playPort)
                IPlaySDK.PLAYCloseStream(playPort)
                return@withContext false
            }

            IPlaySDK.PLAYSetPlayedTimeEx(playPort, 0)
            IPlaySDK.PLAYResetBuffer(playPort, 1) // EMPTY_BIT_STREAM_LIBRARY
            IPlaySDK.PLAYResetBuffer(playPort, 3) // EMPTY_PLAY_QUEUE

            Log.d(TAG, "Started playback on channel $channel from $startTime to $endTime")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting playback", e)
            false
        }
    }

    suspend fun pause(isPause: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (playHandle == 0L || playPort < 0) return@withContext false
        val pauseShort: Short = if (isPause) 1 else 0
        IPlaySDK.PLAYPause(playPort, pauseShort)
        INetSDK.PausePlayBack(playHandle, isPause)
    }

    suspend fun fastForward(): Boolean = withContext(Dispatchers.IO) {
        if (playHandle == 0L || playPort < 0) return@withContext false
        IPlaySDK.PLAYFast(playPort)
        INetSDK.FastPlayBack(playHandle)
    }

    suspend fun slowMotion(): Boolean = withContext(Dispatchers.IO) {
        if (playHandle == 0L || playPort < 0) return@withContext false
        IPlaySDK.PLAYSlow(playPort)
        INetSDK.SlowPlayBack(playHandle)
    }

    suspend fun normalSpeed(surfaceView: SurfaceView): Boolean = withContext(Dispatchers.IO) {
        if (playHandle == 0L || playPort < 0) return@withContext false
        IPlaySDK.PLAYPlay(playPort, surfaceView)
        INetSDK.NormalPlayBack(playHandle)
    }

    suspend fun getOsdTime(): NET_TIME? = withContext(Dispatchers.IO) {
        if (playPort < 0 || playHandle == 0L) return@withContext null
        try {
            val buffer = ByteArray(24)
            if (IPlaySDK.PLAYQueryInfo(playPort, Constants.PLAY_CMD_GetTime, buffer, buffer.size, 0) != 0) {
                NET_TIME().apply {
                    dwYear = ((buffer[3].toLong() and 0xffL) shl 24) or ((buffer[2].toLong() and 0xffL) shl 16) or ((buffer[1].toLong() and 0xffL) shl 8) or (buffer[0].toLong() and 0xffL)
                    dwMonth = ((buffer[7].toLong() and 0xffL) shl 24) or ((buffer[6].toLong() and 0xffL) shl 16) or ((buffer[5].toLong() and 0xffL) shl 8) or (buffer[4].toLong() and 0xffL)
                    dwDay = ((buffer[11].toLong() and 0xffL) shl 24) or ((buffer[10].toLong() and 0xffL) shl 16) or ((buffer[9].toLong() and 0xffL) shl 8) or (buffer[8].toLong() and 0xffL)
                    dwHour = ((buffer[15].toLong() and 0xffL) shl 24) or ((buffer[14].toLong() and 0xffL) shl 16) or ((buffer[13].toLong() and 0xffL) shl 8) or (buffer[12].toLong() and 0xffL)
                    dwMinute = ((buffer[19].toLong() and 0xffL) shl 24) or ((buffer[18].toLong() and 0xffL) shl 16) or ((buffer[17].toLong() and 0xffL) shl 8) or (buffer[16].toLong() and 0xffL)
                    dwSecond = ((buffer[23].toLong() and 0xffL) shl 24) or ((buffer[22].toLong() and 0xffL) shl 16) or ((buffer[21].toLong() and 0xffL) shl 8) or (buffer[20].toLong() and 0xffL)
                }
            } else null
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun captureSnapshot(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        if (playPort < 0) return@withContext false
        outputFile.parentFile?.mkdirs()
        val ret = IPlaySDK.PLAYCatchPicEx(
            playPort,
            outputFile.absolutePath,
            Constants.PicFormat_JPEG
        )
        ret != 0
    }

    fun startDownload(
        channel: Int,
        startTime: NET_TIME,
        endTime: NET_TIME,
        outputFile: File,
        onProgress: (percent: Int) -> Unit,
        onFinished: (success: Boolean, message: String?) -> Unit
    ): Boolean {
        if (loginHandle == 0L) {
            onFinished(false, "Chưa đăng nhập thiết bị")
            return false
        }
        if (downloadHandle != 0L) {
            onFinished(false, "Đang có tiến trình tải video khác")
            return false
        }

        outputFile.parentFile?.mkdirs()

        INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_RECORD_STREAM_TYPE, 0)

        this.downloadCallback = CB_fTimeDownLoadPosCallBack { _, dwTotalSize, dwDownLoadSize, _, _ ->
            if (dwDownLoadSize == -1) {
                // Completed
                stopDownload()
                onProgress(100)
                onFinished(true, "Tải đoạn video thành công!")
            } else if (dwTotalSize > 0) {
                val pct = ((dwDownLoadSize.toLong() * 100) / dwTotalSize).toInt()
                onProgress(minOf(pct, 99))
                if (dwDownLoadSize >= dwTotalSize) {
                    stopDownload()
                    onProgress(100)
                    onFinished(true, "Tải đoạn video thành công!")
                }
            }
        }

        downloadHandle = INetSDK.DownloadByTimeEx(
            loginHandle,
            channel,
            EM_QUERY_RECORD_TYPE.EM_RECORD_TYPE_ALL,
            startTime,
            endTime,
            outputFile.absolutePath,
            downloadCallback,
            null,
            null
        )

        if (downloadHandle == 0L) {
            val err = INetSDK.GetLastError()
            onFinished(false, "Khởi tạo tải video thất bại (Mã lỗi: $err)")
            return false
        }

        Log.d(TAG, "Started download to ${outputFile.absolutePath}, handle=$downloadHandle")
        return true
    }

    fun stopDownload() {
        if (downloadHandle != 0L) {
            try {
                INetSDK.StopDownload(downloadHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping download: ${t.message}")
            }
            downloadHandle = 0L
        }
        downloadCallback = null
    }

    private fun stopPlaybackInternal() {
        if (playHandle != 0L) {
            try {
                INetSDK.StopPlayBack(playHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping playback: ${t.message}")
            }
            playHandle = 0L
        }
        if (playPort >= 0) {
            try {
                IPlaySDK.PLAYStop(playPort)
                IPlaySDK.PLAYStopSoundShare(playPort)
                IPlaySDK.PLAYCloseStream(playPort)
                IPlaySDK.PLAYRefreshPlay(playPort)
                IPlaySDK.UinitSurface(playPort)
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing stream: ${t.message}")
            }
        }
        dataCallback = null
        posCallback = null
    }

    suspend fun stopPlayback() = withContext(Dispatchers.IO) {
        stopPlaybackInternal()
    }

    fun stopPlaybackSync() {
        stopPlaybackInternal()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        logoutInternal()
    }

    fun logoutSync() {
        logoutInternal()
    }

    private fun logoutInternal() {
        stopDownload()
        stopPlaybackInternal()
        if (loginHandle != 0L) {
            try {
                INetSDK.Logout(loginHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error logging out: ${t.message}")
            }
            loginHandle = 0L
        }
        if (playPort >= 0) {
            try {
                IPlaySDK.PLAYReleasePort(playPort)
            } catch (t: Throwable) {
                Log.w(TAG, "Error releasing port: ${t.message}")
            }
            playPort = -1
        }
    }

    /**
     * Query which days of the specified month have recorded video footage.
     */
    suspend fun queryRecordedDaysInMonth(
        ip: String,
        port: Int = 37777,
        username: String = "admin",
        password: String = "",
        channel: Int,
        year: Int,
        month: Int
    ): Set<Int> = withContext(Dispatchers.IO) {
        val recordedDays = mutableSetOf<Int>()

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

        val startTime = NET_TIME().apply {
            setTime(year.toLong(), month.toLong(), 1L, 0L, 0L, 0L)
        }
        val endTime = NET_TIME().apply {
            setTime(year.toLong(), month.toLong(), maxDay.toLong(), 23L, 59L, 59L)
        }

        // Dedicated search handle to avoid multiplexing collisions on active playback stream
        var searchHandle = 0L
        val searchIp = ip.ifBlank { currentIp }
        val searchPort = if (port > 0) port else currentPort
        val searchUser = username.ifBlank { currentUsername }
        val searchPwd = password.ifBlank { currentPassword }

        if (searchIp.isNotBlank()) {
            try {
                val stuIn = NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY().apply {
                    val ipBytes = searchIp.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(ipBytes, 0, szIP, 0, minOf(ipBytes.size, szIP.size))
                    nPort = searchPort
                    val userBytes = searchUser.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                    val pwdBytes = searchPwd.trim().toByteArray(Charsets.UTF_8)
                    System.arraycopy(pwdBytes, 0, szPassword, 0, minOf(pwdBytes.size, szPassword.size))
                    emSpecCap = EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_TCP
                }
                val stuOut = NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY()
                searchHandle = INetSDK.LoginWithHighLevelSecurity(stuIn, stuOut)
                if (searchHandle == 0L) {
                    val devInfo = NET_DEVICEINFO_Ex()
                    searchHandle = INetSDK.LoginEx2(searchIp.trim(), searchPort, searchUser.trim(), searchPwd.trim(), 0, null, devInfo, 0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dedicated search handle login failed, using primary handle: ${e.message}")
            }
        }

        val activeHandle = if (searchHandle != 0L) searchHandle else loginHandle
        if (activeHandle == 0L) {
            Log.w(TAG, "No valid login handle for record search")
            return@withContext recordedDays
        }

        try {
            Log.d(TAG, "Querying record files for channel $channel, month $month/$year (1..$maxDay)")
            val findHandle = INetSDK.FindFile(activeHandle, channel, EM_QUERY_RECORD_TYPE.EM_RECORD_TYPE_ALL, null, startTime, endTime, true, 4000)
            if (findHandle != 0L) {
                val fileInfo = NET_RECORDFILE_INFO()
                var count = 0
                while (count < 2000) {
                    val ret = INetSDK.FindNextFile(findHandle, fileInfo)
                    if (ret > 0) {
                        val d = fileInfo.starttime?.dwDay?.toInt() ?: 0
                        if (d in 1..maxDay) {
                            recordedDays.add(d)
                        }
                        count++
                    } else {
                        break
                    }
                }
                INetSDK.FindClose(findHandle)
                Log.d(TAG, "FindFile found $count files across ${recordedDays.size} days")
            } else {
                Log.w(TAG, "FindFile returned 0, error=${INetSDK.GetLastError()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in FindFile", e)
        }

        // Secondary Strategy: If FindFile returned 0 (e.g. Imou IPC firmware requires QueryRecordTime per day),
        // query day-by-day using QueryRecordTime
        if (recordedDays.isEmpty()) {
            try {
                Log.d(TAG, "Trying QueryRecordTime fallback day by day...")
                val bRet = com.company.NetSDK.BOOL_RET()
                val now = java.util.Calendar.getInstance()
                val isCurrentMonth = (year == now.get(java.util.Calendar.YEAR) && month == (now.get(java.util.Calendar.MONTH) + 1))
                val limitDay = if (isCurrentMonth) now.get(java.util.Calendar.DAY_OF_MONTH) else maxDay

                for (d in 1..limitDay) {
                    val dStart = NET_TIME().apply { setTime(year.toLong(), month.toLong(), d.toLong(), 0L, 0L, 0L) }
                    val dEnd = NET_TIME().apply { setTime(year.toLong(), month.toLong(), d.toLong(), 23L, 59L, 59L) }
                    bRet.value = false
                    val ok = INetSDK.QueryRecordTime(activeHandle, channel, 0, dStart, dEnd, null, bRet, 800)
                    if (ok && bRet.value) {
                        recordedDays.add(d)
                    }
                }
                Log.d(TAG, "QueryRecordTime fallback found ${recordedDays.size} days")
            } catch (t: Throwable) {
                Log.w(TAG, "QueryRecordTime fallback failed: ${t.message}")
            }
        }

        if (searchHandle != 0L) {
            try {
                INetSDK.Logout(searchHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error logging out search handle: ${t.message}")
            }
        }

        Log.i(TAG, "Final recorded days in month $month/$year for channel $channel: $recordedDays")
        recordedDays
    }
}
