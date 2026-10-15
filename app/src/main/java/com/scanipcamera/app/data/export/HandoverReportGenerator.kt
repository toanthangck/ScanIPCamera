package com.scanipcamera.app.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.SiteProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HandoverReportGenerator {

    fun generateHtmlReport(project: SiteProject): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(project.createdAt))

        val devicesRows = project.devices.mapIndexed { index, dev ->
            val statusColor = if (dev.isActivated) "#059669" else "#D97706"
            val statusText = if (dev.isActivated) "Hoạt động tốt" else "Chưa active"
            val location = if (dev.installLocation.isNotBlank()) dev.installLocation else "Vị trí ${index + 1}"
            val modelName = if (dev.customName.isNotBlank()) dev.customName else dev.model.ifEmpty { "Camera IP" }
            val credentials = "${dev.handoverUser} / ${dev.handoverPass}"

            """
            <tr>
                <td style="text-align:center; font-weight:bold;">${index + 1}</td>
                <td style="font-weight:600; color:#0F172A;">${location}</td>
                <td><span style="display:inline-block; padding:2px 6px; border-radius:4px; background:#F1F5F9; font-weight:bold; font-size:11px;">${dev.brand.displayName}</span> <b>${modelName}</b></td>
                <td style="font-family:monospace; font-weight:bold; color:#0284C7;">${dev.ipv4Address}</td>
                <td style="font-family:monospace; font-size:11px;">${dev.macAddress.ifEmpty { "N/A" }}</td>
                <td style="font-family:monospace; font-size:11px; font-weight:bold; color:#0369A1;">${dev.serialNumber.ifEmpty { "N/A" }}</td>
                <td style="font-family:monospace; font-size:11px; color:#475569;">${credentials}</td>
                <td style="text-align:center;"><span style="color:${statusColor}; font-weight:bold; font-size:11px;">● ${statusText}</span></td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val checklistHtml = project.handoverChecklist.map { item ->
            """
            <div style="margin-bottom:5px; font-size:12px;">
                <span style="color:#059669; font-weight:bold;">&#10004;</span> ${item}
            </div>
            """.trimIndent()
        }.joinToString("\n")

        return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Biên bản nghiệm thu & Bàn giao hệ thống Camera</title>
            <style>
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #1E293B; margin: 20px; line-height: 1.5; font-size: 13px; }
                .header { border-bottom: 3px solid #0284C7; padding-bottom: 12px; margin-bottom: 18px; display: flex; justify-content: space-between; align-items: center; }
                .company-title { font-size: 18px; font-weight: 800; color: #0369A1; text-transform: uppercase; margin: 0; }
                .company-sub { font-size: 12px; color: #64748B; margin: 3px 0 0 0; }
                .doc-title { text-align: center; font-size: 19px; font-weight: 800; color: #0F172A; text-transform: uppercase; margin: 15px 0 12px 0; letter-spacing: 0.5px; }
                .info-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; margin-bottom: 18px; }
                .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
                .info-item { font-size: 12.5px; }
                .info-label { font-weight: bold; color: #475569; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 12px; }
                th, td { border: 1px solid #CBD5E1; padding: 7px 9px; text-align: left; }
                th { background-color: #F1F5F9; color: #334155; font-weight: 700; text-transform: uppercase; font-size: 11px; }
                .notice-box { background: #FEF3C7; border-left: 4px solid #F59E0B; padding: 10px 14px; margin-top: 18px; font-size: 12px; border-radius: 4px; }
                .checklist-box { background: #F0FDF4; border-left: 4px solid #10B981; padding: 10px 14px; margin-top: 15px; border-radius: 4px; }
                .signatures { display: grid; grid-template-columns: 1fr 1fr; margin-top: 35px; text-align: center; }
                .sign-col { font-weight: bold; }
                .sign-note { font-style: italic; font-weight: normal; font-size: 11px; color: #64748B; margin-top: 4px; }
                .sign-space { height: 65px; }
                .footer { margin-top: 25px; border-top: 1px solid #E2E8F0; padding-top: 8px; font-size: 11px; color: #94A3B8; text-align: center; }
            </style>
        </head>
        <body>
            <div class="header">
                <div>
                    <h1 class="company-title">CÔNG TY GIẢI PHÁP CAMERA AN NINH</h1>
                    <p class="company-sub">Website: <b>cameratrungthuc.com</b> &bull; <b>cameratuson.com</b></p>
                    <p class="company-sub">Hotline / Zalo: <b>0965.620.086</b> &bull; Email: <b>toanhangts@gmail.com</b></p>
                </div>
                <div style="text-align:right;">
                    <span style="font-size:11px; background:#0284C7; color:white; padding:4px 8px; border-radius:4px; font-weight:bold;">BẢN NGHIỆM THU</span>
                </div>
            </div>

            <h2 class="doc-title">BIÊN BẢN NGHIỆM THU &amp; BÀN GIAO THIẾT BỊ</h2>

            <div class="info-box">
                <div class="info-grid">
                    <div class="info-item"><span class="info-label">Tên công trình:</span> <b>${project.siteName}</b></div>
                    <div class="info-item"><span class="info-label">Thời gian bàn giao:</span> ${dateStr}</div>
                    <div class="info-item"><span class="info-label">Khách hàng:</span> <b>${project.customerName.ifEmpty { "Anh/Chị khách hàng" }}</b></div>
                    <div class="info-item"><span class="info-label">SĐT liên hệ:</span> ${project.customerPhone.ifEmpty { "N/A" }}</div>
                    <div class="info-item"><span class="info-label">Địa chỉ lắp đặt:</span> ${project.address.ifEmpty { "Theo địa chỉ công trình" }}</div>
                    <div class="info-item"><span class="info-label">Thời hạn bảo hành:</span> <b>${project.warrantyMonths} tháng</b> chính hãng</div>
                    <div class="info-item"><span class="info-label">Kỹ thuật viên:</span> <b>${project.technicianName}</b> (${project.technicianPhone})</div>
                    <div class="info-item"><span class="info-label">Mạng Wi-Fi / IP WAN:</span> ${project.wifiSsid.ifEmpty { "LAN" }} &bull; ${project.wanIp.ifEmpty { "N/A" }}</div>
                </div>
            </div>

            <h3 style="font-size:13px; text-transform:uppercase; margin-bottom:6px; color:#0369A1;">1. Danh mục thiết bị đã lắp đặt và bàn giao (${project.devices.size} thiết bị)</h3>
            <table>
                <thead>
                    <tr>
                        <th style="width:25px; text-align:center;">STT</th>
                        <th style="width:110px;">Vị trí lắp</th>
                        <th>Hãng &amp; Thiết bị</th>
                        <th style="width:95px;">Địa chỉ IP</th>
                        <th style="width:110px;">Địa chỉ MAC</th>
                        <th style="width:115px;">Mã Serial</th>
                        <th style="width:105px;">Tài khoản/MK</th>
                        <th style="width:85px; text-align:center;">Tình trạng</th>
                    </tr>
                </thead>
                <tbody>
                    $devicesRows
                </tbody>
            </table>

            <div class="checklist-box">
                <b style="color:#065F46; font-size:12.5px;">2. Các hạng mục kỹ thuật đã nghiệm thu:</b>
                <div style="margin-top:6px;">
                    $checklistHtml
                </div>
            </div>

            ${if (project.notes.isNotBlank()) """
            <div class="notice-box">
                <b>3. Ghi chú &amp; Lưu ý kỹ thuật cho khách hàng:</b><br/>
                ${project.notes.replace("\n", "<br/>")}
            </div>
            """ else ""}

            <div class="signatures">
                <div class="sign-col">
                    ĐẠI DIỆN KHÁCH HÀNG
                    <div class="sign-note">(Ký và ghi rõ họ tên)</div>
                    <div class="sign-space"></div>
                    <div><b>${project.customerName.ifEmpty { "Khách hàng nghiệm thu" }}</b></div>
                </div>
                <div class="sign-col">
                    ĐẠI DIỆN ĐƠN VỊ THI CÔNG
                    <div class="sign-note">(Ký và ghi rõ họ tên)</div>
                    <div class="sign-space"></div>
                    <div><b>${project.technicianName}</b></div>
                </div>
            </div>

            <div class="footer">
                Hệ thống camera an ninh được bảo hành chính hãng ${project.warrantyMonths} tháng &bull; Hỗ trợ kỹ thuật 24/7: 0965.620.086 &bull; cameratrungthuc.com
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Generates a clean text summary ready to be sent to the homeowner via Zalo/SMS
     */
    fun generateZaloSummaryText(project: SiteProject): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(project.createdAt))

        val devLines = project.devices.mapIndexed { i, d ->
            val loc = if (d.installLocation.isNotBlank()) d.installLocation else "Camera ${i + 1}"
            "📍 ${i + 1}. $loc: ${d.brand.displayName} ${d.model}\n   - IP: ${d.ipv4Address} | TK: ${d.handoverUser} | MK: ${d.handoverPass}\n   - Serial: ${d.serialNumber.ifEmpty { "N/A" }}"
        }.joinToString("\n\n")

        return """
📋 BÀN GIAO HỆ THỐNG CAMERA AN NINH
Công trình: ${project.siteName}
Khách hàng: ${project.customerName} - ${project.customerPhone}
Ngày bàn giao: $dateStr - BH: ${project.warrantyMonths} tháng

DANH SÁCH THIẾT BỊ & TÀI KHOẢN:
$devLines

${if (project.notes.isNotBlank()) "LƯU Ý:\n${project.notes}\n" else ""}
Kỹ thuật viên phụ trách: ${project.technicianName}
Hotline hỗ trợ: 0965.620.086
Website: cameratrungthuc.com - cameratuson.com
        """.trimIndent()
    }

    /**
     * Prints the HTML report via Android PrintManager.
     */
    fun printReport(context: Context, project: SiteProject) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = webView.createPrintDocumentAdapter("BienBanBanGiao_${project.siteName}")
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("id", "print", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager?.print("Biên bản bàn giao - ${project.siteName}", printAdapter, printAttributes)
            }
        }
        val htmlContent = generateHtmlReport(project)
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    /**
     * Saves the HTML report to a temporary cache file and opens the Android Share Sheet.
     */
    fun shareReport(context: Context, project: SiteProject) {
        try {
            val htmlContent = generateHtmlReport(project)
            val cacheDir = File(context.cacheDir, "reports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val safeSiteName = project.siteName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val reportFile = File(cacheDir, "BienBan_${safeSiteName}.html")
            reportFile.writeText(htmlContent, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                reportFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Biên bản nghiệm thu & Bàn giao - ${project.siteName}")
                putExtra(Intent.EXTRA_TEXT, "Kính gửi quý khách biên bản bàn giao hệ thống camera an ninh cho ${project.siteName}.\nHotline hỗ trợ: 0965.620.086")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ Biên bản Bàn giao"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
