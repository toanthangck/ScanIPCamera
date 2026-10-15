package com.scanipcamera.app.data.config

import android.util.Log
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.NetworkConfigPayload
import com.scanipcamera.app.data.scanner.HikvisionSADPScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID

class HikvisionConfigurator {

    companion object {
        private const val TAG = "HikvisionConfigurator"
    }

    /**
     * Send SADP Modify packet to reconfigure IP, Subnet, Gateway, Port, DHCP.
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

            val uuid = UUID.randomUUID().toString()
            val modifyXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Probe>
                    <Uuid>$uuid</Uuid>
                    <Types>modify</Types>
                    <MAC>${payload.macAddress.lowercase()}</MAC>
                    <IPv4Address>${payload.newIp}</IPv4Address>
                    <IPv4SubnetMask>${payload.newSubnetMask}</IPv4SubnetMask>
                    <IPv4Gateway>${payload.newGateway}</IPv4Gateway>
                    <IPv4Port>${if (payload.newPort > 0) payload.newPort else device.sdkPort}</IPv4Port>
                    <HttpPort>${device.httpPort}</HttpPort>
                    <DHCP>${payload.isDhcp}</DHCP>
                    <Password>${payload.currentPassword}</Password>
                </Probe>
            """.trimIndent()

            val packetBytes = modifyXml.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(
                if (device.ipv4Address.isNotBlank()) device.ipv4Address else "255.255.255.255"
            )
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, HikvisionSADPScanner.SADP_PORT)
            socket.send(packet)

            // Also broadcast in case the camera is on a different subnet
            val broadcastPacket = DatagramPacket(
                packetBytes,
                packetBytes.size,
                InetAddress.getByName("255.255.255.255"),
                HikvisionSADPScanner.SADP_PORT
            )
            socket.send(broadcastPacket)

            // Wait for confirmation response
            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            try {
                socket.receive(receivePacket)
                val responseStr = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                if (responseStr.contains("<Result>success</Result>") || responseStr.contains("success")) {
                    return@withContext Result.success("Đổi IP Hikvision thành công!")
                } else if (responseStr.contains("Password error") || responseStr.contains("401")) {
                    return@withContext Result.failure(Exception("Sai mật khẩu thiết bị."))
                }
            } catch (e: SocketTimeoutException) {
                // If timeout, some cameras apply changes and reboot without replying
                return@withContext Result.success("Đã gửi lệnh đổi IP (đang chờ thiết bị áp dụng).")
            }

            Result.success("Đã gửi cấu hình thành công.")
        } catch (e: Exception) {
            Log.e(TAG, "Hikvision modify failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }

    /**
     * Activate a brand new Hikvision camera with an initial admin password.
     */
    suspend fun activateDevice(
        macAddress: String,
        targetIp: String,
        newAdminPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val uuid = UUID.randomUUID().toString()
            val activateXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Probe>
                    <Uuid>$uuid</Uuid>
                    <Types>activate</Types>
                    <MAC>${macAddress.lowercase()}</MAC>
                    <Password>$newAdminPassword</Password>
                </Probe>
            """.trimIndent()

            val packetBytes = activateXml.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(if (targetIp.isNotBlank()) targetIp else "255.255.255.255")
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, HikvisionSADPScanner.SADP_PORT)
            socket.send(packet)

            Result.success("Đã gửi lệnh kích hoạt camera.")
        } catch (e: Exception) {
            Log.e(TAG, "Hikvision activation failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }
}
