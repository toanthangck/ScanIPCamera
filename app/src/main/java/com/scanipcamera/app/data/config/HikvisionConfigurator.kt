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

class HikvisionConfigurator(
    private val configService: CameraConfigService = CameraConfigService()
) {

    companion object {
        private const val TAG = "HikvisionConfigurator"
    }

    /**
     * Send SADP Modify packet to reconfigure IP, Subnet, Gateway, Port, DHCP via ISAPI HTTP & SADP UDP.
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

        // 1. Try ISAPI HTTP directly if IP is accessible
        if (device.ipv4Address.isNotBlank()) {
            val httpResult = configService.configureHikvisionNetworkViaHttp(device, safePayload)
            if (httpResult.isSuccess) {
                sendSadpUdpModify(device, safePayload)
                return@withContext httpResult
            } else if (httpResult.exceptionOrNull()?.message?.contains("Sai mật khẩu") == true) {
                return@withContext httpResult
            }
        }

        // 2. Fallback to SADP UDP on port 37020
        val sadpResult = sendSadpUdpModify(device, safePayload)
        return@withContext sadpResult
    }

    private fun sendSadpUdpModify(
        device: CameraDevice,
        payload: NetworkConfigPayload
    ): Result<String> {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val uuid = UUID.randomUUID().toString()
            val dashMac = payload.macAddress.replace(":", "-").lowercase()
            val colonMac = payload.macAddress.lowercase()

            val xmlList = listOf(
                // Standard SADP with dashes
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <Probe>
                        <Uuid>$uuid</Uuid>
                        <Types>modify</Types>
                        <MAC>$dashMac</MAC>
                        <IPv4Address>${payload.newIp}</IPv4Address>
                        <IPv4SubnetMask>${payload.newSubnetMask}</IPv4SubnetMask>
                        <IPv4Gateway>${payload.newGateway}</IPv4Gateway>
                        <IPv4Port>${if (payload.newPort > 0) payload.newPort else device.sdkPort}</IPv4Port>
                        <HttpPort>${device.httpPort}</HttpPort>
                        <DHCP>${payload.isDhcp}</DHCP>
                        <Password>${payload.currentPassword}</Password>
                    </Probe>
                """.trimIndent(),
                // SADP with colons
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <Probe>
                        <Uuid>$uuid</Uuid>
                        <Types>modify</Types>
                        <MAC>$colonMac</MAC>
                        <IPv4Address>${payload.newIp}</IPv4Address>
                        <IPv4SubnetMask>${payload.newSubnetMask}</IPv4SubnetMask>
                        <IPv4Gateway>${payload.newGateway}</IPv4Gateway>
                        <IPv4Port>${if (payload.newPort > 0) payload.newPort else device.sdkPort}</IPv4Port>
                        <HttpPort>${device.httpPort}</HttpPort>
                        <DHCP>${payload.isDhcp}</DHCP>
                        <Password>${payload.currentPassword}</Password>
                    </Probe>
                """.trimIndent()
            )

            val targets = mutableListOf<InetAddress>()
            if (device.ipv4Address.isNotBlank()) {
                try { targets.add(InetAddress.getByName(device.ipv4Address)) } catch (ignored: Exception) {}
            }
            try { targets.add(InetAddress.getByName("255.255.255.255")) } catch (ignored: Exception) {}

            for (target in targets) {
                for (xml in xmlList) {
                    try {
                        val packetBytes = xml.toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(packetBytes, packetBytes.size, target, HikvisionSADPScanner.SADP_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.v(TAG, "Error sending SADP modify packet: ${e.message}")
                    }
                }
            }

            // Wait for confirmation response
            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            try {
                socket.receive(receivePacket)
                val responseStr = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                if (responseStr.contains("<Result>success</Result>") || responseStr.contains("success")) {
                    return Result.success("Đổi IP Hikvision thành công sang ${payload.newIp}!")
                } else if (responseStr.contains("Password error", ignoreCase = true) || responseStr.contains("401")) {
                    return Result.failure(Exception("Sai mật khẩu thiết bị."))
                }
            } catch (e: SocketTimeoutException) {
                return Result.success("Đã gửi lệnh đổi IP Hikvision sang ${payload.newIp}.")
            }

            return Result.success("Đã hoàn tất gửi cấu hình tới thiết bị.")
        } catch (e: Exception) {
            Log.e(TAG, "Hikvision modify failed", e)
            return Result.failure(e)
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

    /**
     * Send remote reboot packet to Hikvision camera.
     */
    suspend fun rebootDevice(
        device: CameraDevice,
        adminPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }

            val uuid = UUID.randomUUID().toString()
            val rebootXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Probe>
                    <Uuid>$uuid</Uuid>
                    <Types>reboot</Types>
                    <MAC>${device.macAddress.lowercase()}</MAC>
                    <Password>$adminPassword</Password>
                </Probe>
            """.trimIndent()

            val packetBytes = rebootXml.toByteArray(Charsets.UTF_8)
            val targetAddr = InetAddress.getByName(if (device.ipv4Address.isNotBlank()) device.ipv4Address else "255.255.255.255")
            val packet = DatagramPacket(packetBytes, packetBytes.size, targetAddr, HikvisionSADPScanner.SADP_PORT)
            socket.send(packet)

            Result.success("Đã gửi lệnh khởi động lại thiết bị Hikvision.")
        } catch (e: Exception) {
            Log.e(TAG, "Hikvision reboot failed", e)
            Result.failure(e)
        } finally {
            socket?.close()
        }
    }
}
