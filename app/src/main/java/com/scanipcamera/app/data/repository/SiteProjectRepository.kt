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
            val json = serializeProject(project)
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
                list.add(deserializeProject(json))
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

    /**
     * Complete export of all projects to a single JSON package for Google Drive / Cloud Sync
     */
    suspend fun exportAllProjectsJson(): String = withContext(Dispatchers.IO) {
        val projects = getAllProjects()
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        for (p in projects) {
            arr.put(serializeProject(p))
        }
        root.put("projects", arr)
        root.toString(2)
    }

    /**
     * Restore projects from a JSON package
     */
    suspend fun importAllProjectsJson(jsonStr: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("projects") ?: return@withContext Result.failure(Exception("Định dạng file không hợp lệ!"))
            var count = 0
            for (i in 0 until arr.length()) {
                val pJson = arr.getJSONObject(i)
                val proj = deserializeProject(pJson)
                val file = File(projectsDir, "${proj.id}.json")
                file.writeText(pJson.toString(2), Charsets.UTF_8)
                count++
            }
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun serializeProject(project: SiteProject): JSONObject {
        return JSONObject().apply {
            put("id", project.id)
            put("siteName", project.siteName)
            put("customerName", project.customerName)
            put("customerPhone", project.customerPhone)
            put("address", project.address)
            put("companyName", project.companyName)
            put("technicianName", project.technicianName)
            put("technicianPhone", project.technicianPhone)
            put("createdAt", project.createdAt)
            put("wanIp", project.wanIp)
            put("wifiSsid", project.wifiSsid)
            put("warrantyMonths", project.warrantyMonths)
            put("notes", project.notes)
            put("lastSyncTime", project.lastSyncTime)

            val checklistArr = JSONArray()
            project.handoverChecklist.forEach { checklistArr.put(it) }
            put("handoverChecklist", checklistArr)

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
                    put("customName", dev.customName)
                    put("installLocation", dev.installLocation)
                    put("handoverUser", dev.handoverUser)
                    put("handoverPass", dev.handoverPass)
                })
            }
            put("devices", devicesArray)
        }
    }

    private fun deserializeProject(json: JSONObject): SiteProject {
        val devicesList = mutableListOf<CameraDevice>()
        val devicesArray = json.optJSONArray("devices")
        if (devicesArray != null) {
            for (i in 0 until devicesArray.length()) {
                val d = devicesArray.getJSONObject(i)
                devicesList.add(
                    CameraDevice(
                        id = d.optString("id"),
                        brand = runCatching { DeviceBrand.valueOf(d.optString("brand")) }.getOrDefault(DeviceBrand.UNKNOWN),
                        category = runCatching { DeviceCategory.valueOf(d.optString("category")) }.getOrDefault(DeviceCategory.CAMERA),
                        channelCount = d.optInt("channelCount", 0),
                        model = d.optString("model"),
                        serialNumber = d.optString("serialNumber"),
                        macAddress = d.optString("macAddress"),
                        ipv4Address = d.optString("ipv4Address"),
                        ipv4SubnetMask = d.optString("ipv4SubnetMask", "255.255.255.0"),
                        ipv4Gateway = d.optString("ipv4Gateway"),
                        httpPort = d.optInt("httpPort", 80),
                        sdkPort = d.optInt("sdkPort", 0),
                        rtspPort = d.optInt("rtspPort", 554),
                        isActivated = d.optBoolean("isActivated", true),
                        rawProtocol = d.optString("rawProtocol", "SAVED"),
                        customName = d.optString("customName"),
                        installLocation = d.optString("installLocation"),
                        handoverUser = d.optString("handoverUser", "admin"),
                        handoverPass = d.optString("handoverPass", "admin123")
                    )
                )
            }
        }

        val checklist = mutableListOf<String>()
        val checklistArr = json.optJSONArray("handoverChecklist")
        if (checklistArr != null) {
            for (i in 0 until checklistArr.length()) {
                checklist.add(checklistArr.getString(i))
            }
        } else {
            checklist.addAll(
                listOf(
                    "Đã cài đặt xem qua điện thoại cho chủ nhà",
                    "Đã hướng dẫn xem lại video và đổi mật khẩu",
                    "Đã kiểm tra góc nhìn ban ngày và hồng ngoại ban đêm",
                    "Đã bàn giao đầy đủ nguồn và phụ kiện"
                )
            )
        }

        return SiteProject(
            id = json.optString("id"),
            siteName = json.optString("siteName"),
            customerName = json.optString("customerName"),
            customerPhone = json.optString("customerPhone"),
            address = json.optString("address"),
            companyName = json.optString("companyName", "ĐƠN VỊ THI CÔNG CAMERA"),
            technicianName = json.optString("technicianName", "Kỹ thuật viên"),
            technicianPhone = json.optString("technicianPhone", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            wanIp = json.optString("wanIp"),
            wifiSsid = json.optString("wifiSsid"),
            warrantyMonths = json.optInt("warrantyMonths", 24),
            notes = json.optString("notes"),
            handoverChecklist = checklist,
            devices = devicesList,
            lastSyncTime = json.optLong("lastSyncTime", 0L)
        )
    }
}
