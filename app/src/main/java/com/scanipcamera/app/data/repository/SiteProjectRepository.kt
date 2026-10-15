package com.scanipcamera.app.data.repository

import android.content.Context
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.DeviceCategory
import com.scanipcamera.app.data.model.SiteProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SiteProjectRepository(private val context: Context) {

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    suspend fun saveProject(project: SiteProject): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(projectsDir, "${project.id}.json")
            val json = JSONObject().apply {
                put("id", project.id)
                put("siteName", project.siteName)
                put("customerName", project.customerName)
                put("customerPhone", project.customerPhone)
                put("address", project.address)
                put("technicianName", project.technicianName)
                put("technicianPhone", project.technicianPhone)
                put("createdAt", project.createdAt)
                put("wanIp", project.wanIp)
                put("wifiSsid", project.wifiSsid)

                val devicesArray = JSONArray()
                project.devices.forEach { dev ->
                    devicesArray.put(JSONObject().apply {
                        put("id", dev.id)
                        put("brand", dev.brand.name)
                        put("category", dev.category.name)
                        put("channelCount", dev.channelCount)
                        put("model", dev.model)
                        put("serialNumber", dev.serialNumber)
                        put("macAddress", dev.macAddress)
                        put("ipv4Address", dev.ipv4Address)
                        put("ipv4SubnetMask", dev.ipv4SubnetMask)
                        put("ipv4Gateway", dev.ipv4Gateway)
                        put("httpPort", dev.httpPort)
                        put("sdkPort", dev.sdkPort)
                        put("rtspPort", dev.rtspPort)
                        put("isActivated", dev.isActivated)
                        put("rawProtocol", dev.rawProtocol)
                    })
                }
                put("devices", devicesArray)
            }
            file.writeText(json.toString(2), Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllProjects(): List<SiteProject> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SiteProject>()
        val files = projectsDir.listFiles { _, name -> name.endsWith(".json") } ?: return@withContext emptyList()
        for (file in files) {
            try {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                val devicesList = mutableListOf<CameraDevice>()
                val devicesArray = json.optJSONArray("devices")
                if (devicesArray != null) {
                    for (i in 0 until devicesArray.length()) {
                        val d = devicesArray.getJSONObject(i)
                        devicesList.add(
                            CameraDevice(
                                id = d.optString("id"),
                                brand = runCatching { DeviceBrand.valueOf(d.optString("brand")) }.getOrDefault(DeviceBrand.ONVIF),
                                category = runCatching { DeviceCategory.valueOf(d.optString("category")) }.getOrDefault(DeviceCategory.CAMERA),
                                channelCount = d.optInt("channelCount", 0),
                                model = d.optString("model"),
                                serialNumber = d.optString("serialNumber"),
                                macAddress = d.optString("macAddress"),
                                ipv4Address = d.optString("ipv4Address"),
                                ipv4SubnetMask = d.optString("ipv4SubnetMask", "255.255.255.0"),
                                ipv4Gateway = d.optString("ipv4Gateway"),
                                httpPort = d.optInt("httpPort", 80),
                                sdkPort = d.optInt("sdkPort", 8000),
                                rtspPort = d.optInt("rtspPort", 554),
                                isActivated = d.optBoolean("isActivated", true),
                                rawProtocol = d.optString("rawProtocol", "SAVED")
                            )
                        )
                    }
                }

                list.add(
                    SiteProject(
                        id = json.optString("id"),
                        siteName = json.optString("siteName"),
                        customerName = json.optString("customerName"),
                        customerPhone = json.optString("customerPhone"),
                        address = json.optString("address"),
                        technicianName = json.optString("technicianName", "Nguyễn Toàn Thắng"),
                        technicianPhone = json.optString("technicianPhone", "0965620086"),
                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                        wanIp = json.optString("wanIp"),
                        wifiSsid = json.optString("wifiSsid"),
                        devices = devicesList
                    )
                )
            } catch (e: Exception) {
                // Ignore corrupted file
            }
        }
        list.sortedByDescending { it.createdAt }
    }

    suspend fun deleteProject(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(projectsDir, "$id.json")
            if (file.exists()) file.delete()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
