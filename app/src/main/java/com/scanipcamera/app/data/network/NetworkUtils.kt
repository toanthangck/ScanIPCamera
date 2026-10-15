package com.scanipcamera.app.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

class NetworkUtils(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Android drops multicast packets unless MulticastLock is acquired.
     */
    fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
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
     * Get the current device's local IPv4 address.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
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
     * Calculate or retrieve the broadcast address (e.g. 192.168.1.255 or 255.255.255.255).
     */
    fun getBroadcastAddresses(): List<InetAddress> {
        val broadcastList = mutableListOf<InetAddress>()
        try {
            // Global broadcast address
            broadcastList.add(InetAddress.getByName("255.255.255.255"))

            // Subnet-specific broadcast addresses
            val interfaces = NetworkInterface.getNetworkInterfaces()
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
    }
}
