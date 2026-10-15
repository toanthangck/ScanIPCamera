package com.scanipcamera.app.data.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.scanipcamera.app.data.model.CameraDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceExportHelper {

    fun generateTextReport(devices: List<CameraDevice>): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val now = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.append("📋 BÁO CÁO DANH SÁCH THIẾT BỊ CAMERA\n")
        sb.append("Thời gian xuất: $now\n")
        sb.append("Tổng số thiết bị: ${devices.size}\n")
        sb.append("=========================================\n\n")

        devices.forEachIndexed { index, device ->
            sb.append("${index + 1}. [${device.brand.displayName}] ${device.model}\n")
            sb.append("   - Địa chỉ IP: ${device.ipv4Address}:${device.httpPort}\n")
            sb.append("   - Địa chỉ MAC: ${device.macAddress.ifEmpty { "N/A" }}\n")
            if (device.serialNumber.isNotBlank()) {
                sb.append("   - Số Serial: ${device.serialNumber}\n")
            }
            sb.append("   - Subnet Mask: ${device.ipv4SubnetMask}\n")
            sb.append("   - Gateway: ${device.ipv4Gateway.ifEmpty { "N/A" }}\n")
            sb.append("   - Trạng thái: ${if (device.isActivated) "Đã kích hoạt" else "Chưa kích hoạt"}\n")
            sb.append("   - Giao thức: ${device.rawProtocol}\n\n")
        }

        return sb.toString()
    }

    fun generateCsvReport(devices: List<CameraDevice>): String {
        val sb = StringBuilder()
        sb.append("STT,ThuongHieu,Model,IP,HttpPort,MAC,SerialNumber,SubnetMask,Gateway,TrangThai,GiaoThuc\n")

        devices.forEachIndexed { index, device ->
            val status = if (device.isActivated) "Da kich hoat" else "Chua kich hoat"
            sb.append("${index + 1},\"${device.brand.displayName}\",\"${device.model}\",\"${device.ipv4Address}\",${device.httpPort},\"${device.macAddress}\",\"${device.serialNumber}\",\"${device.ipv4SubnetMask}\",\"${device.ipv4Gateway}\",\"$status\",\"${device.rawProtocol}\"\n")
        }

        return sb.toString()
    }

    fun shareReport(context: Context, reportText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Danh sách Camera Scan")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        val chooser = Intent.createChooser(intent, "Chia sẻ danh sách thiết bị")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Camera Devices", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
    }
}
