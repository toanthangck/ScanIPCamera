package com.scanipcamera.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class WifiNetworkInfo(
    val isConnected: Boolean = false,
    val ssid: String = "Chưa kết nối",
    val ipAddress: String = "",
    val subnetMask: String = "255.255.255.0",
    val gateway: String = "",
    val broadcastAddress: String = "255.255.255.255"
)

class NetworkUtils(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Android drops multicast packets unless MulticastLock is acquired.
     */
    fun acquireMulticastLock() {
        try {
            if (multicastLock == null && wifiManager != null) {
                multicastLock = wifiManager.createMulticastLock("ScanIPCamera_MulticastLock").apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                    Log.d(TAG, "MulticastLock acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring MulticastLock", e)
        }
    }

    fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "MulticastLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MulticastLock", e)
        }
    }

    /**
     * Find active Wi-Fi or local network interface (wlan0, eth0, etc.)
     */
    fun getWifiNetworkInterface(): NetworkInterface? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("eth") || name.contains("wlan")) {
                    return iface
                }
            }
            // Fallback: first up, non-loopback with IPv4
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return iface
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving WiFi NetworkInterface", e)
        }
        return null
    }

    /**
     * Get the current device's local IPv4 address.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (address in networkInterface.inetAddresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving local IP", e)
        }
        return null
    }

    /**
     * Retrieve detailed info about current Wi-Fi connection.
     */
    fun getCurrentWifiInfo(): WifiNetworkInfo {
        var ip = ""
        var mask = "255.255.255.0"
        var gateway = ""
        var broadcast = "255.255.255.255"
        var ssid = "Wi-Fi"
        var isConnected = false

        try {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNetwork)
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (isWifi) {
                isConnected = true
                val connectionInfo = wifiManager?.connectionInfo
                val rawSsid = connectionInfo?.ssid ?: ""
                ssid = if (rawSsid.isNotBlank() && rawSsid != "<unknown ssid>") {
                    rawSsid.removeSurrounding("\"")
                } else {
                    "Mạng Wi-Fi nội bộ"
                }

                val dhcpInfo = wifiManager?.dhcpInfo
                if (dhcpInfo != null) {
                    val ipInt = dhcpInfo.ipAddress
                    if (ipInt != 0) {
                        ip = intToIp(ipInt)
                    }
                    val gwInt = dhcpInfo.gateway
                    if (gwInt != 0) {
                        gateway = intToIp(gwInt)
                    }
                    val maskInt = dhcpInfo.netmask
                    if (maskInt != 0) {
                        mask = intToIp(maskInt)
                    }
                }
            }

            // Fallback from NetworkInterface if DHCP info was 0
            if (ip.isBlank()) {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                if (interfaces != null) {
                    for (iface in interfaces) {
                        if (iface.isLoopback || !iface.isUp) continue
                        for (addr in iface.interfaceAddresses) {
                            val inet = addr.address
                            if (!inet.isLoopbackAddress && inet is Inet4Address) {
                                ip = inet.hostAddress ?: ""
                                addr.broadcast?.hostAddress?.let { broadcast = it }
                                val prefix = addr.networkPrefixLength.toInt()
                                mask = prefixLengthToSubnetMask(prefix)
                                isConnected = true
                                break
                            }
                        }
                        if (ip.isNotBlank()) break
                    }
                }
            }

            // Infer gateway if missing
            if (gateway.isBlank() && ip.isNotBlank()) {
                val parts = ip.split(".")
                if (parts.size == 4) {
                    gateway = "${parts[0]}.${parts[1]}.${parts[2]}.1"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Wifi info", e)
        }

        return WifiNetworkInfo(
            isConnected = isConnected,
            ssid = ssid,
            ipAddress = ip,
            subnetMask = mask,
            gateway = gateway,
            broadcastAddress = broadcast
        )
    }

    /**
     * Calculate or retrieve the broadcast addresses (e.g. 192.168.1.255 or 255.255.255.255).
     */
    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            // Global broadcast address
            broadcastList.add(InetAddress.getByName("255.255.255.255"))

            // Subnet-specific broadcast addresses
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return broadcastList
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && !broadcastList.contains(broadcast)) {
                        broadcastList.add(broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving broadcast addresses", e)
        }
        return broadcastList
    }

    companion object {
        private const val TAG = "NetworkUtils"

        fun intToIp(ipInt: Int): String {
            return (ipInt and 0xFF).toString() + "." +
                    (ipInt shr 8 and 0xFF) + "." +
                    (ipInt shr 16 and 0xFF) + "." +
                    (ipInt shr 24 and 0xFF)
        }

        fun prefixLengthToSubnetMask(prefixLength: Int): String {
            val mask = -0x1 shl (32 - prefixLength)
            return (mask shr 24 and 0xFF).toString() + "." +
                    (mask shr 16 and 0xFF) + "." +
                    (mask shr 8 and 0xFF) + "." +
                    (mask and 0xFF)
        }

        fun ipToLong(ip: String): Long {
            val parts = ip.trim().split(".")
            if (parts.size != 4) return 0L
            var result = 0L
            for (i in 0..3) {
                val part = parts[i].toLongOrNull() ?: 0L
                result = (result shl 8) or (part and 0xFF)
            }
            return result
        }

        /**
         * Check if two IP addresses are in the same subnet according to mask.
         */
        fun isSameSubnet(ip1: String, ip2: String, mask: String = "255.255.255.0"): Boolean {
            if (ip1.isBlank() || ip2.isBlank()) return true
            return try {
                val ip1Val = ipToLong(ip1)
                val ip2Val = ipToLong(ip2)
                val maskVal = ipToLong(mask)
                (ip1Val and maskVal) == (ip2Val and maskVal)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Suggest a vacant IP in the same subnet (e.g. .200, .201, ...).
         */
        fun suggestSameSubnetIp(baseIp: String, usedIps: Set<String> = emptySet()): String {
            val parts = baseIp.split(".")
            if (parts.size != 4) return "192.168.1.200"
            val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            for (lastOctet in 200..250) {
                val candidate = "$prefix.$lastOctet"
                if (!usedIps.contains(candidate) && candidate != baseIp) {
                    return candidate
                }
            }
            return "$prefix.200"
        }
    }
}
