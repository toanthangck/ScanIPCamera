package com.scanipcamera.app.data.player

import android.util.Log
import android.view.SurfaceView
import com.company.NetSDK.CB_fRealDataCallBackEx
import com.company.NetSDK.EM_LOGIN_SPAC_CAP_TYPE
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_DEVICEINFO_Ex
import com.company.NetSDK.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.SDK_RealPlayType
import com.company.PlaySDK.IPlaySDK
import com.scanipcamera.app.data.scanner.DahuaNetSdkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles Dahua / IMOU / KBVISION live video playback using NetSDK (RealPlay) and PlaySDK (Decoding & Render).
 */
class DahuaLivePlayerManager {

    companion object {
        private const val TAG = "DahuaLivePlayerManager"
        private const val STREAM_BUF_SIZE = 1024 * 1024 * 2
    }

    private var loginHandle: Long = 0L
    private var realHandle: Long = 0L
    private var playPort: Int = -1
    private var dataCallback: CB_fRealDataCallBackEx? = null
    var channelCount: Int = 1
        private set

    val currentLoginHandle: Long get() = loginHandle
    val currentPlayPort: Int get() = playPort

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
                Log.d(TAG, "LoginWithHighLevelSecurity succeeded, channels=$channelCount")
                return@withContext Result.success(channelCount)
            }

            // Fallback: LoginEx2
            val devInfo = NET_DEVICEINFO_Ex()
            loginHandle = INetSDK.LoginEx2(ip.trim(), port, username.trim(), password.trim(), 0, null, devInfo, 0)
            if (loginHandle != 0L) {
                val chans = devInfo.nChanNum
                channelCount = if (chans > 0) chans else 1
                Log.d(TAG, "LoginEx2 succeeded, channels=$channelCount")
                return@withContext Result.success(channelCount)
            }

            val lastErr = INetSDK.GetLastError()
            Log.e(TAG, "Login failed, err=$lastErr, stuOut.nError=${stuOut.nError}")
            val errorMsg = when (stuOut.nError) {
                1 -> "Sai mật khẩu thiết bị!"
                2 -> "Tên người dùng không tồn tại!"
                3 -> "Hết thời gian chờ kết nối (Timeout)!"
                4 -> "Tài khoản bị khóa do nhập sai nhiều lần!"
                else -> "Đăng nhập thất bại (Mã lỗi: $lastErr)"
            }
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            Log.e(TAG, "Exception during login", e)
            Result.failure(e)
        }
    }

    fun initSurface(surfaceView: SurfaceView) {
        if (playPort < 0) {
            playPort = IPlaySDK.PLAYGetFreePort()
        }
        IPlaySDK.InitSurface(playPort, surfaceView)
    }

    fun startPlay(channel: Int, isSubStream: Boolean, surfaceView: SurfaceView): Boolean {
        if (loginHandle == 0L) {
            Log.e(TAG, "Cannot startPlay: Not logged in")
            return false
        }

        stopPlay()

        try {
            if (playPort < 0) {
                playPort = IPlaySDK.PLAYGetFreePort()
            }
            IPlaySDK.InitSurface(playPort, surfaceView)

            val openStream = IPlaySDK.PLAYOpenStream(playPort, null, 0, STREAM_BUF_SIZE)
            if (openStream == 0) {
                Log.e(TAG, "PLAYOpenStream failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                return false
            }

            val play = IPlaySDK.PLAYPlay(playPort, surfaceView)
            if (play == 0) {
                Log.e(TAG, "PLAYPlay failed: ${IPlaySDK.PLAYGetLastError(playPort)}")
                IPlaySDK.PLAYCloseStream(playPort)
                return false
            }

            IPlaySDK.PLAYPlaySoundShare(playPort)

            val streamType = if (isSubStream) {
                SDK_RealPlayType.SDK_RType_Realplay_1
            } else {
                SDK_RealPlayType.SDK_RType_Realplay_0
            }

            realHandle = INetSDK.RealPlayEx(loginHandle, channel, streamType)
            if (realHandle == 0L) {
                Log.e(TAG, "RealPlayEx failed: ${INetSDK.GetLastError()}")
                IPlaySDK.PLAYStop(playPort)
                IPlaySDK.PLAYCloseStream(playPort)
                return false
            }

            val currentPort = playPort
            dataCallback = CB_fRealDataCallBackEx { _, dataType, buffer, _, _ ->
                if (dataType == 0 && buffer != null && currentPort >= 0) {
                    IPlaySDK.PLAYInputData(currentPort, buffer, buffer.size)
                }
            }
            INetSDK.SetRealDataCallBackEx(realHandle, dataCallback, 1)

            Log.d(TAG, "Started real play on channel $channel, streamType=$streamType")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting play", e)
            return false
        }
    }

    fun stopPlay() {
        if (realHandle != 0L) {
            try {
                INetSDK.StopRealPlayEx(realHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error stopping real play: ${t.message}")
            }
            realHandle = 0L
        }
        if (playPort >= 0) {
            try {
                IPlaySDK.PLAYStop(playPort)
                IPlaySDK.PLAYStopSoundShare(playPort)
                IPlaySDK.PLAYCloseStream(playPort)
                IPlaySDK.PLAYRefreshPlay(playPort)
                IPlaySDK.UinitSurface(playPort)
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing play stream: ${t.message}")
            }
        }
        dataCallback = null
    }

    fun logout() {
        stopPlay()
        if (loginHandle != 0L) {
            try {
                INetSDK.Logout(loginHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Error logging out: ${t.message}")
            }
            loginHandle = 0L
        }
        if (playPort >= 0) {
            IPlaySDK.PLAYReleasePort(playPort)
            playPort = -1
        }
    }

    fun captureSnapshot(outputFile: java.io.File): Boolean {
        if (playPort < 0) return false
        outputFile.parentFile?.mkdirs()
        val ret = IPlaySDK.PLAYCatchPicEx(
            playPort,
            outputFile.absolutePath,
            com.company.PlaySDK.Constants.PicFormat_JPEG
        )
        return ret != 0
    }

    fun ptzControl(
        channel: Int,
        command: Int,
        speed: Int = 4,
        bStop: Boolean = false
    ): Boolean {
        if (loginHandle == 0L) return false
        return DahuaNetSdkManager.ptzControl(
            loginHandle = loginHandle,
            channel = channel,
            command = command,
            param1 = speed.toByte(),
            param2 = speed.toByte(),
            bStop = bStop
        )
    }
}
