package com.scanipcamera.app.data.config

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.scanner.DahuaDiscoveryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DahuaConfigurator(
    private val configService: CameraConfigService = CameraConfigService()
) {

    companion object {
        private const val TAG = "DahuaConfigurator"
    }

    /**
     * Send Dahua ConfigTool modify packet to update IP, Subnet, Gateway using HTTP CGI & DHIP UDP.
     */
    suspend fun configureNetwork(
        device: CameraDevice,
        payload: NetworkConfigPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        val effectiveGateway = if (payload.newGateway.isNotBlank()) {
            payload.newGateway.trim()
        } else {
            val parts = payload.newIp.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.1" else "192.168.1.1"
        }
        val safePayload = payload.copy(newGateway = effectiveGateway)

        // 1. Try HTTP CGI directly if current IP is reachable
        if (device.ipv4Address.isNotBlank()) {
            val httpResult = configService.configureDahuaNetworkViaHttp(device, safePayload)
            if (httpResult.isSuccess) {
                // Send UDP broadcast for cache coherence across tools
                sendUdpModify(device, safePayload)
                return@withContext httpResult
            } else if (httpResult.exceptionOrNull()?.message?.contains("Sai mật khẩu") == true) {
                return@withContext httpResult
            }
        }

        // 2. Send UDP packets on port 37810 (framed DHIP + raw JSON)
        val udpResult = sendUdpModify(device, safePayload)
        return@withContext udpResult
    }

    private fun sendUdpModify(
        device: CameraDevice,
        payload: NetworkConfigPayload
    ): Result<String> {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            // Packet 1: client.modify structure
            val clientModifyJson = JSONObject().apply {
                put("method", "client.modify")
                put("params", JSONObject().apply {
                    put("device", JSONObject().apply {
                        put("MAC", payload.macAddress)
                        put("IPv4Address", JSONObject().apply {
                            put("HostIP", payload.newIp)
                            put("SubnetMask", payload.newSubnetMask)
                            put("GateWay", payload.newGateway)
                        })
                        put("DHCP", payload.isDhcp)
                        put("Password", payload.currentPassword)
                    })
                })
            }.toString()

            // Packet 2: DHDiscover.modify structure
            val dhDiscoverModifyJson = JSONObject().apply {
                put("method", "DHDiscover.modify")
                put("params", JSONObject().apply {
                    put("mac", payload.macAddress)
                    put("ip", payload.newIp)
                    put("mask", payload.newSubnetMask)
                    put("gateway", payload.newGateway)
                    put("dhcp", payload.isDhcp)
                    put("password", payload.currentPassword)
                })
            }.toString()

            val packets = listOf(
                DahuaDiscoveryScanner.buildDhipPacket(clientModifyJson),
                DahuaDiscoveryScanner.buildDhipPacket(dhDiscoverModifyJson),
                clientModifyJson.toByteArray(Charsets.UTF_8)
            )

            val targets = mutableListOf<InetAddress>()
            if (device.ipv4Address.isNotBlank()) {
                try { targets.add(InetAddress.getByName(device.ipv4Address)) } catch (ignored: Exception) {}
            }
            try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (ignored: Exception) {}

            for (target in targets) {
                for (packetBytes in packets) {
                    try {
                        val packet = DatagramPacket(packetBytes, packetBytes.size, target, DahuaDiscoveryScanner.DAHUA_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.v(TAG, "Error sending UDP modify packet: ${e.message}")
                    }
                }
            }

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            try {
                socket.receive(receivePacket)
                val responseStr = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                if (responseStr.contains("\"result\":true") || responseStr.contains("success")) {
                    return Result.success("Đổi IP Dahua/IMOU thành công sang ${payload.newIp}!")
                } else if (responseStr.contains("password", ignoreCase = true) && responseStr.contains("error", ignoreCase = true)) {
                    return Result.failure(Exception("Sai mật khẩu thiết bị!"))
                }
            } catch (e: SocketTimeoutException) {
                return Result.success("Đã gửi lệnh đổi IP Dahua/IMOU sang ${payload.newIp}.")
            }

            return Result.success("Đã hoàn tất gửi cấu hình tới thiết bị.")
        } catch (e: Exception) {
            Log.e(TAG, "Dahua modify failed", e)
            return Result.failure(e)
        } finally {
            socket?.close()
        }
    }

    /**
     * Activate a new uninitialized Dahua / IMOU device with admin password.
     */
    suspend fun activateDevice(
        macAddress: String,
        targetIp: String,
        newPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val initJson = JSONObject().apply {
                put("method", "client.initDevice")
                put("params", JSONObject().apply {
                    put("mac", macAddress)
                    put("pwd", newPassword)
                    put("cellphone", "")
                    put("mail", "")
                })
            }.toString()

            val packetBytes = initJson.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(if (targetIp.isNotBlank()) targetIp else "255.255.255.255")
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, DahuaDiscoveryScanner.DAHUA_PORT)
            socket.send(packet)

            val broadcastPacket = DatagramPacket(
                packetBytes,
                packetBytes.size,
                InetAddress.getByName("255.255.255.255"),
                DahuaDiscoveryScanner.DAHUA_PORT
            )
            socket.send(broadcastPacket)

            Result.success("Đã gửi lệnh kích hoạt Dahua/IMOU thành công!")
        } catch (e: Exception) {
            Log.e(TAG, "Dahua activation failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }

    /**
     * Reboot a Dahua device remotely.
     */
    suspend fun rebootDevice(
        device: CameraDevice,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val rebootJson = JSONObject().apply {
                put("method", "client.reboot")
                put("params", JSONObject().apply {
                    put("mac", device.macAddress)
                    put("password", password)
                })
            }.toString()

            val packetBytes = rebootJson.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(if (device.ipv4Address.isNotBlank()) device.ipv4Address else "255.255.255.255")
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, DahuaDiscoveryScanner.DAHUA_PORT)
            socket.send(packet)

            Result.success("Đã gửi lệnh khởi động lại thiết bị Dahua/IMOU.")
        } catch (e: Exception) {
            Log.e(TAG, "Dahua reboot failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }
}
