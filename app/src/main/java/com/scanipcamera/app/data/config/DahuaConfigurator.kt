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

class DahuaConfigurator {

    companion object {
        private const val TAG = "DahuaConfigurator"
    }

    /**
     * Send Dahua ConfigTool modify packet to update IP, Subnet, Gateway.
     */
    suspend fun configureNetwork(
        device: CameraDevice,
        payload: NetworkConfigPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val modifyJson = JSONObject().apply {
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

            val packetBytes = modifyJson.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(
                if (device.ipv4Address.isNotBlank()) device.ipv4Address else "255.255.255.255"
            )
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, DahuaDiscoveryScanner.DAHUA_PORT)
            socket.send(packet)

            // Also broadcast
            val broadcastPacket = DatagramPacket(
                packetBytes,
                packetBytes.size,
                InetAddress.getByName("255.255.255.255"),
                DahuaDiscoveryScanner.DAHUA_PORT
            )
            socket.send(broadcastPacket)

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            try {
                socket.receive(receivePacket)
                val responseStr = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                if (responseStr.contains("\"result\":true") || responseStr.contains("success")) {
                    return@withContext Result.success("Đổi IP Dahua/IMOU thành công!")
                }
            } catch (e: SocketTimeoutException) {
                return@withContext Result.success("Đã gửi lệnh đổi IP tới thiết bị Dahua/IMOU.")
            }

            Result.success("Đã hoàn tất cấu hình.")
        } catch (e: Exception) {
            Log.e(TAG, "Dahua modify failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }
}
