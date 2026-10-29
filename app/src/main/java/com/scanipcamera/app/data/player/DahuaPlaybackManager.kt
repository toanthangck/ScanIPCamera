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

    // Retain strong references to native callbacks to prevent GC collection during playback
    private var dataCallback: CB_fDataCallBack? = null
    private var posCallback: CB_fDownLoadPosCallBack? = null
    private var downloadCallback: CB_fTimeDownLoadPosCallBack? = null

    var channelCount: Int = 1
        private set

    val isPlaying: Boolean get() = playHandle != 0L
    val isDownloading: Boolean get() = downloadHandle != 0L

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

    fun startPlayback(
        channel: Int,
        startTime: NET_TIME,
        endTime: NET_TIME,
        surfaceView: SurfaceView,
        onPosUpdate: ((totalSize: Int, currentSize: Int) -> Unit)? = null
    ): Boolean {
        if (loginHandle == 0L) {
            Log.e(TAG, "Cannot startPlayback: Not logged in")
            return false
        }

        stopPlayback()

        try {
            if (playPort < 0) {
                playPort = IPlaySDK.PLAYGetFreePort()
            }
            IPlaySDK.InitSurface(playPort, surfaceView)

            // Setup Stream
            val openStream = IPlaySDK.PLAYOpenStream(playPort, null, 0, STREAM_BUFFER_SIZE)
            if (openStream == 0) {
                Log.e(TAG, "PLAYOpenStream failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                return false
            }

            IPlaySDK.PLAYSetStreamOpenMode(playPort, Constants.STREAME_FILE)

            val play = IPlaySDK.PLAYPlay(playPort, surfaceView)
            if (play == 0) {
                Log.e(TAG, "PLAYPlay failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                IPlaySDK.PLAYCloseStream(playPort)
                return false
            }

            IPlaySDK.PLAYSetCacheMode(playPort, 1)
            IPlaySDK.PLAYEnableLargePicAdjustment(playPort, 1)
            IPlaySDK.PLAYPlaySoundShare(playPort)

            // Device Record Type
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_RECORD_STREAM_TYPE, 0)
            INetSDK.SetDeviceMode(loginHandle, EM_USEDEV_MODE.SDK_RECORD_TYPE, NET_RECORD_TYPE.NET_RECORD_TYPE_ALL)

            val targetPort = playPort
            this.dataCallback = CB_fDataCallBack { _, dwDataType, buffer, dwBufSize ->
                if (playHandle != 0L && playPort >= 0 && dwDataType == 0 && buffer != null) {
                    try {
                        IPlaySDK.PLAYInputData(targetPort, buffer, dwBufSize)
                    } catch (t: Throwable) {
                        0
                    }
                } else 1
            }

            this.posCallback = CB_fDownLoadPosCallBack { _, total, current ->
                onPosUpdate?.invoke(total, current)
            }

            playHandle = INetSDK.PlayBackByTimeEx(loginHandle, channel, startTime, endTime, posCallback, dataCallback)
            if (playHandle == 0L) {
                val err = INetSDK.GetLastError()
                Log.e(TAG, "PlayBackByTimeEx failed, err=$err")
                IPlaySDK.PLAYStop(playPort)
                IPlaySDK.PLAYCloseStream(playPort)
                return false
            }

            IPlaySDK.PLAYSetPlayedTimeEx(playPort, 0)
            IPlaySDK.PLAYResetBuffer(playPort, 1) // EMPTY_BIT_STREAM_LIBRARY
            IPlaySDK.PLAYResetBuffer(playPort, 3) // EMPTY_PLAY_QUEUE

            Log.d(TAG, "Started playback on channel $channel from $startTime to $endTime")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting playback", e)
            return false
        }
    }

    fun pause(isPause: Boolean): Boolean {
        if (playHandle == 0L || playPort < 0) return false
        val pauseShort: Short = if (isPause) 1 else 0
        IPlaySDK.PLAYPause(playPort, pauseShort)
        return INetSDK.PausePlayBack(playHandle, isPause)
    }

    fun fastForward(): Boolean {
        if (playHandle == 0L || playPort < 0) return false
        IPlaySDK.PLAYFast(playPort)
        return INetSDK.FastPlayBack(playHandle)
    }

    fun slowMotion(): Boolean {
        if (playHandle == 0L || playPort < 0) return false
        IPlaySDK.PLAYSlow(playPort)
        return INetSDK.SlowPlayBack(playHandle)
    }

    fun normalSpeed(surfaceView: SurfaceView): Boolean {
        if (playHandle == 0L || playPort < 0) return false
        IPlaySDK.PLAYPlay(playPort, surfaceView)
        return INetSDK.NormalPlayBack(playHandle)
    }

    fun getOsdTime(): NET_TIME? {
        if (playPort < 0 || playHandle == 0L) return null
        return try {
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

    fun captureSnapshot(outputFile: File): Boolean {
        if (playPort < 0) return false
        outputFile.parentFile?.mkdirs()
        val ret = IPlaySDK.PLAYCatchPicEx(
            playPort,
            outputFile.absolutePath,
            Constants.PicFormat_JPEG
        )
        return ret != 0
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

    fun stopPlayback() {
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

    fun logout() {
        stopDownload()
        stopPlayback()
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
    suspend fun queryRecordedDaysInMonth(channel: Int, year: Int, month: Int): Set<Int> = withContext(Dispatchers.IO) {
        val recordedDays = mutableSetOf<Int>()
        if (loginHandle == 0L) return@withContext recordedDays

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

        try {
            val findHandle = INetSDK.FindFile(loginHandle, channel, EM_QUERY_RECORD_TYPE.EM_RECORD_TYPE_ALL, null, startTime, endTime, false, 3000)
            if (findHandle != 0L) {
                val fileInfo = NET_RECORDFILE_INFO()
                var count = 0
                while (count < 1500) {
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying recorded days", e)
        }

        Log.d(TAG, "Found ${recordedDays.size} recorded days in month $month/$year for channel $channel: $recordedDays")
        recordedDays
    }
}
