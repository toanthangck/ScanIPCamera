package com.scanipcamera.app.data.player

import android.util.Log
import com.company.NetSDK.EM_LOCAL_MODE
import com.company.NetSDK.EM_LOGIN_SPAC_CAP_TYPE
import com.company.NetSDK.INetSDK
import com.company.NetSDK.NET_DEVICEINFO_Ex
import com.company.NetSDK.NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.company.NetSDK.NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY
import com.mm.android.dhproxy.client.DHProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Dahua Cloud P2P Manager (ToUProxy)
 * Connects directly using device Serial Number (SN) without port forwarding.
 */
class DahuaP2PManager {
    companion object {
        private const val TAG = "DahuaP2PManager"
        const val DEFAULT_SERVER = "p2p.easy4ip.com"
        const val DEFAULT_PORT = 8888
        const val DEFAULT_KEY = "5&ph9,."
    }

    private val p2pClient = DHProxyClient()
    private var localPort: Int = 0
    private var loginHandle: Long = 0L

    val isConnected: Boolean get() = loginHandle != 0L
    val p2pLoginHandle: Long get() = loginHandle

    suspend fun loginP2P(
        serialNumber: String,
        username: String = "admin",
        password: String = "",
        serverAddress: String = DEFAULT_SERVER,
        serverPort: Int = DEFAULT_PORT,
        serverKey: String = DEFAULT_KEY
    ): Result<Long> = withContext(Dispatchers.IO) {
        disconnect()

        val cleanSn = serialNumber.trim()
        if (cleanSn.isBlank()) {
            return@withContext Result.failure(Exception("Số S/N không hợp lệ"))
        }

        try {
            val initOk = p2pClient.initWithName(serverAddress, serverPort, serverKey, "ScanIPCam", username)
            if (!initOk) {
                return@withContext Result.failure(Exception("Không thể khởi tạo P2P Client"))
            }

            // Extract randsalt & devp2pver if available
            val devInfo = p2pClient.getDeviceInfo(cleanSn) ?: ""
            var strVersion = ""
            var strRandsalt = ""
            val verPos = devInfo.indexOf("devp2pver")
            if (verPos != -1) {
                val start = verPos + 12
                val end = devInfo.indexOf('"', start + 1)
                if (end != -1) strVersion = devInfo.substring(start, end)
            }
            val saltPos = devInfo.indexOf("randsalt")
            if (saltPos != -1) {
                val start = saltPos + 11
                val end = devInfo.indexOf('"', start + 1)
                if (end != -1) strRandsalt = devInfo.substring(start, end)
            }

            localPort = p2pClient.addPortEx(cleanSn, 37777, 0, username, password, strRandsalt, strVersion)
            if (localPort <= 0) {
                return@withContext Result.failure(Exception("Không thể gán cổng P2P (Mã lỗi: $localPort)"))
            }

            // Poll port status up to 8 seconds (80 x 100ms)
            var isPortReady = false
            for (i in 0 until 80) {
                val status = p2pClient.portStatus(localPort)
                if (status == 1) {
                    isPortReady = true
                    break
                }
                delay(100)
            }

            if (!isPortReady) {
                disconnect()
                return@withContext Result.failure(Exception("Thiết bị P2P đang Ngoại tuyến (Offline)"))
            }

            // Login via 127.0.0.1:localPort with P2P spec cap
            val stuIn = NET_IN_LOGIN_WITH_HIGHLEVEL_SECURITY().apply {
                val ipBytes = "127.0.0.1".toByteArray(Charsets.UTF_8)
                System.arraycopy(ipBytes, 0, szIP, 0, ipBytes.size)
                nPort = localPort
                val userBytes = username.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(userBytes, 0, szUserName, 0, minOf(userBytes.size, szUserName.size))
                val pwdBytes = password.trim().toByteArray(Charsets.UTF_8)
                System.arraycopy(pwdBytes, 0, szPassword, 0, minOf(pwdBytes.size, szPassword.size))
                emSpecCap = EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_P2P
            }
            val stuOut = NET_OUT_LOGIN_WITH_HIGHLEVEL_SECURITY()
            loginHandle = INetSDK.LoginWithHighLevelSecurity(stuIn, stuOut)

            if (loginHandle == 0L) {
                val devInfoFallback = NET_DEVICEINFO_Ex()
                loginHandle = INetSDK.LoginEx2("127.0.0.1", localPort, username.trim(), password.trim(), EM_LOGIN_SPAC_CAP_TYPE.EM_LOGIN_SPEC_CAP_P2P, "", devInfoFallback, 0)
            }

            if (loginHandle != 0L) {
                INetSDK.SetLocalMode(loginHandle, EM_LOCAL_MODE.EM_LOCAL_PLAY_FLAG_MODE, 0x01 or 0x02)
                INetSDK.SetLocalMode(loginHandle, EM_LOCAL_MODE.EM_LOCAL_PLAYBACK_FLAG_MODE, 0x01)
                Log.d(TAG, "P2P Login success for SN=$cleanSn, handle=$loginHandle")
                Result.success(loginHandle)
            } else {
                val err = INetSDK.GetLastError()
                disconnect()
                Result.failure(Exception("Đăng nhập P2P thất bại (Mã lỗi: $err)"))
            }
        } catch (t: Throwable) {
            disconnect()
            Result.failure(t)
        }
    }

    fun disconnect() {
        if (loginHandle != 0L) {
            try {
                INetSDK.Logout(loginHandle)
            } catch (t: Throwable) {
                Log.w(TAG, "Logout error: ${t.message}")
            }
            loginHandle = 0L
        }
        if (localPort > 0) {
            try {
                p2pClient.delPort(localPort)
            } catch (t: Throwable) {
                Log.w(TAG, "delPort error: ${t.message}")
            }
            localPort = 0
        }
        try {
            p2pClient.exit()
        } catch (t: Throwable) {
            Log.w(TAG, "p2p exit error: ${t.message}")
        }
    }
}
