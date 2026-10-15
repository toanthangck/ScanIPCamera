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
            """
            <tr>
                <td style="text-align:center; font-weight:bold;">${index + 1}</td>
                <td><span style="display:inline-block; padding:2px 6px; border-radius:4px; background:#F1F5F9; font-weight:bold; font-size:11px;">${dev.brand.displayName}</span> <b>${dev.model.ifEmpty { "Camera IP" }}</b></td>
                <td style="font-family:monospace; font-weight:bold; color:#0284C7;">${dev.ipv4Address}</td>
                <td style="font-family:monospace; text-align:center;">${dev.httpPort} / ${dev.sdkPort}</td>
                <td style="font-family:monospace; font-size:11px;">${dev.macAddress.ifEmpty { "N/A" }}</td>
                <td style="font-family:monospace; font-size:11px; font-weight:bold; color:#0369A1;">${dev.serialNumber.ifEmpty { "N/A" }}</td>
                <td style="text-align:center;"><span style="color:${statusColor}; font-weight:bold; font-size:11px;">● ${statusText}</span></td>
            </tr>
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
                .header { border-bottom: 3px solid #0284C7; padding-bottom: 12px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }
                .company-title { font-size: 18px; font-weight: 800; color: #0369A1; text-transform: uppercase; margin: 0; }
                .company-sub { font-size: 12px; color: #64748B; margin: 4px 0 0 0; }
                .doc-title { text-align: center; font-size: 18px; font-weight: 800; color: #0F172A; text-transform: uppercase; margin: 20px 0 15px 0; }
                .info-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; margin-bottom: 20px; }
                .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
                .info-item { font-size: 12.5px; }
                .info-label { font-weight: bold; color: #475569; }
                table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 12px; }
                th, td { border: 1px solid #CBD5E1; padding: 8px 10px; text-align: left; }
                th { background-color: #F1F5F9; color: #334155; font-weight: 700; text-transform: uppercase; font-size: 11px; }
                .notice-box { background: #FEF3C7; border-left: 4px solid #F59E0B; padding: 10px 14px; margin-top: 20px; font-size: 12px; border-radius: 4px; }
                .signatures { display: grid; grid-template-columns: 1fr 1fr; margin-top: 40px; text-align: center; }
                .sign-col { font-weight: bold; }
                .sign-note { font-style: italic; font-weight: normal; font-size: 11px; color: #64748B; margin-top: 4px; }
                .sign-space { height: 70px; }
                .footer { margin-top: 30px; border-top: 1px solid #E2E8F0; padding-top: 10px; font-size: 11px; color: #94A3B8; text-align: center; }
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
                    <div class="info-item"><span class="info-label">Địa chỉ lắp đặt:</span> ${project.address.ifEmpty { "Theo hợp đồng" }}</div>
                    <div class="info-item"><span class="info-label">Kỹ thuật viên bàn giao:</span> <b>${project.technicianName}</b> (${project.technicianPhone})</div>
                    <div class="info-item"><span class="info-label">Mạng Wi-Fi:</span> ${project.wifiSsid.ifEmpty { "LAN" }}</div>
                    <div class="info-item"><span class="info-label">IP WAN (Public):</span> ${project.wanIp.ifEmpty { "N/A" }}</div>
                </div>
            </div>

            <h3 style="font-size:13px; text-transform:uppercase; margin-bottom:6px; color:#0369A1;">1. Danh mục thiết bị đã lắp đặt và bàn giao (${project.devices.size} thiết bị)</h3>
            <table>
                <thead>
                    <tr>
                        <th style="width:30px;">STT</th>
                        <th>Tên thiết bị / Model</th>
                        <th>Địa chỉ IP</th>
                        <th>Port (Web/SDK)</th>
                        <th>Địa chỉ MAC</th>
                        <th>Số Serial (S/N)</th>
                        <th>Trạng thái</th>
                    </tr>
                </thead>
                <tbody>
                    $devicesRows
                </tbody>
            </table>

            <div class="notice-box">
                <b>📌 HƯỚNG DẪN BẢO MẬT &amp; BẢO HÀNH CHO KHÁCH HÀNG:</b>
                <ul style="margin: 4px 0 0 16px; padding: 0;">
                    <li>Vui lòng đổi mật khẩu tài khoản quản trị (admin) và không chia sẻ mật khẩu cho người ngoài.</li>
                    <li>Sử dụng số Serial để đăng ký và quản lý xem từ xa trên ứng dụng điện thoại (Hik-Connect / DMSS / EZVIZ / Imou).</li>
                    <li>Mọi thắc mắc kỹ thuật hoặc bảo hành, vui lòng liên hệ Hotline: <b>0965.620.086</b> hoặc truy cập <b>cameratrungthuc.com</b>.</li>
                </ul>
            </div>

            <div class="signatures">
                <div class="sign-col">
                    ĐẠI DIỆN KHÁCH HÀNG
                    <div class="sign-note">(Ký và ghi rõ họ tên)</div>
                    <div class="sign-space"></div>
                    <div>${project.customerName.ifEmpty { "Khách hàng" }}</div>
                </div>
                <div class="sign-col">
                    KỸ THUẬT VIÊN NGHIỆM THU
                    <div class="sign-note">(Ký và xác nhận hoàn tất)</div>
                    <div class="sign-space"></div>
                    <div>${project.technicianName}</div>
                </div>
            </div>

            <div class="footer">
                Phần mềm Scan IP Camera & NVR &bull; Thuộc sở hữu: toanhangts@gmail.com &bull; Hotline: 0965.620.086 &bull; Website: cameratrungthuc.com - cameratuson.com
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * Print HTML document to PDF or physical printer using Android PrintManager
     */
    fun printReport(context: Context, htmlContent: String, jobName: String = "BienBanBanGiaoCamera") {
        val webView = WebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = createPrintDocumentAdapter(jobName)
                    printManager?.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }
            loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }
    }

    /**
     * Share HTML Report file via Android Share sheet
     */
    fun shareHtmlReport(context: Context, project: SiteProject) {
        try {
            val html = generateHtmlReport(project)
            val cacheFile = File(context.cacheDir, "BienBan_${project.siteName.replace(" ", "_")}.html")
            cacheFile.writeText(html, Charsets.UTF_8)

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Biên bản bàn giao: ${project.siteName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ Biên bản bàn giao"))
        } catch (e: Exception) {
            // Fallback to text share
            val textReport = DeviceExportHelper.generateTextReport(project.devices)
            DeviceExportHelper.shareReport(context, textReport)
        }
    }
}
