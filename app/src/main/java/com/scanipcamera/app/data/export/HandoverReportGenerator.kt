package com.scanipcamera.app.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.scanipcamera.app.data.model.CameraDevice
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.data.model.SiteProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HandoverReportGenerator {

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    fun generateHtmlReport(project: SiteProject): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(project.createdAt))

        val companyName = project.companyName.ifBlank { "ĐƠN VỊ THI CÔNG & LẮP ĐẶT CAMERA" }
        val techName = project.technicianName.ifBlank { "Kỹ thuật viên" }
        val techPhone = project.technicianPhone
        val customerName = project.customerName.ifBlank { "Quý khách hàng" }
        val customerPhone = project.customerPhone.ifBlank { "N/A" }
        val address = project.address.ifBlank { "Theo công trình thực tế" }

        val devicesRows = project.devices.mapIndexed { index, dev ->
            val statusColor = if (dev.isActivated) "#059669" else "#D97706"
            val statusText = if (dev.isActivated) "Hoạt động tốt" else "Chưa active"
            val location = if (dev.installLocation.isNotBlank()) dev.installLocation else "Vị trí ${index + 1}"
            val modelName = if (dev.customName.isNotBlank()) dev.customName else dev.model.ifEmpty { "Camera IP" }
            val credentials = "User: <b>${escapeHtml(dev.handoverUser)}</b><br/>MK: <b>${escapeHtml(dev.handoverPass)}</b>"
            val portText = if (dev.httpPort != 80 && dev.httpPort > 0) ":${dev.httpPort}" else ""
            val extraDetails = buildList {
                if (dev.serialNumber.isNotBlank()) add("SN: ${escapeHtml(dev.serialNumber)}")
                if (dev.macAddress.isNotBlank()) add("MAC: ${escapeHtml(dev.macAddress)}")
            }.joinToString(" &bull; ")

            """
            <tr>
                <td style="text-align:center; font-weight:bold; color:#64748B;">${index + 1}</td>
                <td style="font-weight:700; color:#0F172A;">${escapeHtml(location)}</td>
                <td>
                    <span style="display:inline-block; padding:2px 7px; border-radius:4px; background:#F1F5F9; font-weight:bold; font-size:11px; color:#334155;">${escapeHtml(dev.brand.displayName)}</span>
                    <b style="color:#0F172A; font-size:12.5px;">${escapeHtml(modelName)}</b>
                    ${if (extraDetails.isNotBlank()) """<div style="font-family:monospace; font-size:10.5px; color:#64748B; margin-top:2px;">$extraDetails</div>""" else ""}
                </td>
                <td style="font-family:monospace; font-weight:bold; color:#0284C7; font-size:12px;">
                    ${escapeHtml(dev.ipv4Address)}${portText}
                </td>
                <td style="font-family:monospace; font-size:11.5px; color:#334155; line-height:1.4;">
                    ${credentials}
                </td>
                <td style="text-align:center;">
                    <span style="color:${statusColor}; font-weight:bold; font-size:11px; background:#F0FDF4; padding:3px 8px; border-radius:12px; border:1px solid #BBF7D0;">
                        ● ${statusText}
                    </span>
                </td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val checklistHtml = project.handoverChecklist.map { item ->
            """
            <div style="margin-bottom:6px; font-size:12.5px; display:flex; align-items:center;">
                <span style="color:#059669; font-weight:bold; margin-right:8px; font-size:14px;">&#10004;</span>
                <span>${escapeHtml(item)}</span>
            </div>
            """.trimIndent()
        }.joinToString("\n")

        // Recommend mobile viewing apps based on installed camera brands
        val installedBrands = project.devices.map { it.brand }.toSet()
        val appRecommendations = buildList {
            if (installedBrands.contains(DeviceBrand.HIKVISION)) {
                add("<b>Hikvision / Hilook</b>: Tải app <b>Hik-Connect</b> hoặc <b>HilookVision</b>")
            }
            if (installedBrands.contains(DeviceBrand.DAHUA) || installedBrands.contains(DeviceBrand.KBVISION)) {
                add("<b>Dahua / KBVision</b>: Tải app <b>DMSS</b> hoặc <b>KBView Plus</b>")
            }
            if (installedBrands.contains(DeviceBrand.IMOU)) {
                add("<b>Imou</b>: Tải app <b>Imou Life</b>")
            }
            if (installedBrands.contains(DeviceBrand.EZVIZ)) {
                add("<b>EZVIZ</b>: Tải app <b>EZVIZ</b>")
            }
            if (installedBrands.contains(DeviceBrand.UNIVIEW)) {
                add("<b>Uniview</b>: Tải app <b>EZView</b>")
            }
            if (installedBrands.contains(DeviceBrand.XIONGMAI)) {
                add("<b>Xiongmai / H.264 / XM</b>: Tải app <b>XMEye</b> hoặc <b>iCSee</b>")
            }
            if (isEmpty()) {
                add("<b>Xem trực tiếp:</b> Dùng trình duyệt web nội mạng hoặc ứng dụng xem camera chuyên dụng theo hãng")
            }
        }.joinToString("<br/>")

        return """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Biên bản nghiệm thu & Bàn giao hệ thống Camera</title>
            <style>
                @page { size: A4 portrait; margin: 12mm 15mm; }
                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #1E293B; margin: 0; padding: 15px; line-height: 1.5; font-size: 12.5px; }
                .header { border-bottom: 2.5px solid #0284C7; padding-bottom: 12px; margin-bottom: 14px; display: flex; justify-content: space-between; align-items: flex-start; }
                .company-title { font-size: 17px; font-weight: 800; color: #0369A1; text-transform: uppercase; margin: 0 0 4px 0; letter-spacing: 0.3px; }
                .company-sub { font-size: 11.5px; color: #475569; margin: 2px 0 0 0; }
                .badge-title { font-size: 11px; background: #0284C7; color: white; padding: 4px 10px; border-radius: 4px; font-weight: bold; letter-spacing: 0.5px; display: inline-block; }
                .doc-title { text-align: center; font-size: 18px; font-weight: 800; color: #0F172A; text-transform: uppercase; margin: 12px 0 10px 0; letter-spacing: 0.5px; }
                .info-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 6px; padding: 10px 14px; margin-bottom: 14px; }
                .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px 14px; }
                .info-item { font-size: 12px; }
                .info-label { font-weight: bold; color: #475569; }
                table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 11.5px; }
                th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
                th { background-color: #F1F5F9; color: #334155; font-weight: 700; text-transform: uppercase; font-size: 10.5px; letter-spacing: 0.3px; }
                .app-box { background: #F0F9FF; border-left: 3.5px solid #0284C7; padding: 8px 12px; margin-top: 12px; border-radius: 4px; font-size: 11.5px; line-height: 1.6; }
                .checklist-box { background: #F0FDF4; border-left: 3.5px solid #10B981; padding: 8px 12px; margin-top: 12px; border-radius: 4px; }
                .notice-box { background: #FEF3C7; border-left: 3.5px solid #F59E0B; padding: 8px 12px; margin-top: 12px; font-size: 11.5px; border-radius: 4px; }
                .terms-box { background: #FAF5FF; border-left: 3.5px solid #9333EA; padding: 8px 12px; margin-top: 12px; font-size: 11.5px; border-radius: 4px; }
                .signatures { display: grid; grid-template-columns: 1fr 1fr; margin-top: 30px; text-align: center; page-break-inside: avoid; }
                .sign-col { font-weight: bold; font-size: 12px; }
                .sign-note { font-style: italic; font-weight: normal; font-size: 10.5px; color: #64748B; margin-top: 3px; }
                .sign-space { height: 60px; }
                .footer { margin-top: 20px; border-top: 1px solid #E2E8F0; padding-top: 6px; font-size: 10.5px; color: #94A3B8; text-align: center; }
            </style>
        </head>
        <body>
            <div class="header">
                <div>
                    <h1 class="company-title">${escapeHtml(companyName)}</h1>
                    <p class="company-sub">Kỹ thuật viên phụ trách: <b>${escapeHtml(techName)}</b>${if (techPhone.isNotBlank()) " &bull; Hotline: <b>${escapeHtml(techPhone)}</b>" else ""}</p>
                </div>
                <div style="text-align:right;">
                    <span class="badge-title">BIÊN BẢN BÀN GIAO</span>
                </div>
            </div>

            <h2 class="doc-title">BIÊN BẢN NGHIỆM THU &amp; BÀN GIAO THIẾT BỊ</h2>

            <div class="info-box">
                <div class="info-grid">
                    <div class="info-item"><span class="info-label">Công trình / Dự án:</span> <b>${escapeHtml(project.siteName)}</b></div>
                    <div class="info-item"><span class="info-label">Thời gian bàn giao:</span> ${dateStr}</div>
                    <div class="info-item"><span class="info-label">Khách hàng nghiệm thu:</span> <b>${escapeHtml(customerName)}</b></div>
                    <div class="info-item"><span class="info-label">Số điện thoại liên hệ:</span> ${escapeHtml(customerPhone)}</div>
                    <div class="info-item"><span class="info-label">Địa chỉ lắp đặt:</span> ${escapeHtml(address)}</div>
                    <div class="info-item"><span class="info-label">Thời hạn bảo hành:</span> <b>${project.warrantyMonths} tháng</b> chính hãng</div>
                    <div class="info-item"><span class="info-label">Kỹ thuật viên thực hiện:</span> <b>${escapeHtml(techName)}</b>${if (techPhone.isNotBlank()) " ($techPhone)" else ""}</div>
                    <div class="info-item"><span class="info-label">Mạng Wi-Fi / IP WAN:</span> ${escapeHtml(project.wifiSsid.ifEmpty { "LAN" })} &bull; ${escapeHtml(project.wanIp.ifEmpty { "Tự động (DHCP)" })}</div>
                </div>
            </div>

            <h3 style="font-size:12.5px; text-transform:uppercase; margin-bottom:4px; color:#0369A1;">1. Danh mục thiết bị đã lắp đặt và bàn giao (${project.devices.size} thiết bị)</h3>
            <table>
                <thead>
                    <tr>
                        <th style="width:30px; text-align:center;">STT</th>
                        <th style="width:130px;">Vị trí lắp đặt</th>
                        <th>Hãng &amp; Thiết bị / Serial</th>
                        <th style="width:120px;">Địa chỉ IP / Cổng</th>
                        <th style="width:150px;">Tài khoản bàn giao</th>
                        <th style="width:100px; text-align:center;">Tình trạng</th>
                    </tr>
                </thead>
                <tbody>
                    $devicesRows
                </tbody>
            </table>

            <div class="checklist-box">
                <b style="color:#065F46; font-size:12px;">2. Các hạng mục kỹ thuật đã hoàn thành &amp; nghiệm thu:</b>
                <div style="margin-top:6px;">
                    $checklistHtml
                </div>
            </div>

            <div class="app-box">
                <b style="color:#0369A1; font-size:12px;">3. Hướng dẫn ứng dụng xem camera trên điện thoại &amp; máy tính:</b>
                <div style="margin-top:4px;">
                    $appRecommendations
                </div>
            </div>

            ${if (project.notes.isNotBlank()) """
            <div class="notice-box">
                <b>4. Ghi chú &amp; Lưu ý kỹ thuật cho khách hàng:</b><br/>
                ${escapeHtml(project.notes).replace("\n", "<br/>")}
            </div>
            """ else ""}

            <div class="terms-box">
                <b style="color:#7E22CE; font-size:12px;">5. Cam kết bàn giao &amp; Bảo hành kỹ thuật:</b>
                <div style="margin-top:4px; line-height:1.5;">
                    &bull; Đơn vị thi công đã hoàn thành lắp đặt, căn chỉnh góc nhìn, kiểm tra chất lượng hình ảnh ngày và đêm.<br/>
                    &bull; Khách hàng đã được bàn giao đầy đủ quyền quản trị và mật khẩu đăng nhập, được hướng dẫn xem lại video và đổi mật khẩu an toàn.<br/>
                    &bull; Hệ thống được bảo hành chính hãng <b>${project.warrantyMonths} tháng</b> theo tiêu chuẩn của nhà sản xuất.
                </div>
            </div>

            <div class="signatures">
                <div class="sign-col">
                    ĐẠI DIỆN KHÁCH HÀNG
                    <div class="sign-note">(Ký và ghi rõ họ tên)</div>
                    <div class="sign-space"></div>
                    <div><b>${escapeHtml(customerName)}</b></div>
                </div>
                <div class="sign-col">
                    ĐẠI DIỆN ĐƠN VỊ THI CÔNG
                    <div class="sign-note">(Ký và ghi rõ họ tên)</div>
                    <div class="sign-space"></div>
                    <div><b>${escapeHtml(techName)}</b></div>
                </div>
            </div>

            <div class="footer">
                Hệ thống camera an ninh được bảo hành chính hãng ${project.warrantyMonths} tháng${if (techPhone.isNotBlank()) " &bull; Hotline kỹ thuật hỗ trợ: $techPhone" else ""}
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

        val companyName = project.companyName.ifBlank { "ĐƠN VỊ THI CÔNG CAMERA" }
        val techName = project.technicianName.ifBlank { "Kỹ thuật viên" }
        val techPhone = project.technicianPhone
        val customerName = project.customerName.ifBlank { "Quý khách" }
        val customerPhone = if (project.customerPhone.isNotBlank()) " - ${project.customerPhone}" else ""

        val devLines = project.devices.mapIndexed { i, d ->
            val loc = if (d.installLocation.isNotBlank()) d.installLocation else "Camera ${i + 1}"
            val model = if (d.customName.isNotBlank()) d.customName else d.model.ifEmpty { "Camera IP" }
            val portText = if (d.httpPort != 80 && d.httpPort > 0) ":${d.httpPort}" else ""
            val sn = if (d.serialNumber.isNotBlank()) "\n   - Serial: ${d.serialNumber}" else ""
            "📍 ${i + 1}. $loc: [${d.brand.displayName}] $model\n   - IP: ${d.ipv4Address}${portText} | TK: ${d.handoverUser} | MK: ${d.handoverPass}$sn"
        }.joinToString("\n\n")

        // Mobile apps
        val installedBrands = project.devices.map { it.brand }.toSet()
        val appList = buildList {
            if (installedBrands.contains(DeviceBrand.HIKVISION)) add("Hik-Connect (Hikvision)")
            if (installedBrands.contains(DeviceBrand.DAHUA) || installedBrands.contains(DeviceBrand.KBVISION)) add("DMSS / KBView (Dahua/KB)")
            if (installedBrands.contains(DeviceBrand.IMOU)) add("Imou Life")
            if (installedBrands.contains(DeviceBrand.EZVIZ)) add("EZVIZ")
            if (installedBrands.contains(DeviceBrand.UNIVIEW)) add("EZView (Uniview)")
            if (installedBrands.contains(DeviceBrand.XIONGMAI)) add("XMEye / iCSee")
        }.joinToString(", ")

        return """
📋 BIÊN BẢN BÀN GIAO HỆ THỐNG CAMERA
🏢 $companyName
🏡 Công trình: ${project.siteName}
👤 Khách hàng: $customerName$customerPhone
📅 Ngày bàn giao: $dateStr | Bảo hành: ${project.warrantyMonths} tháng chính hãng

DANH SÁCH THIẾT BỊ & TÀI KHOẢN:
$devLines

${if (appList.isNotBlank()) "📱 Ứng dụng xem trên điện thoại: $appList\n" else ""}${if (project.notes.isNotBlank()) "📝 Lưu ý: ${project.notes}\n" else ""}👨‍🔧 Kỹ thuật phụ trách: $techName${if (techPhone.isNotBlank()) "\n☎️ Hotline hỗ trợ: $techPhone" else ""}
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

            val techContact = if (project.technicianPhone.isNotBlank()) "\nHotline hỗ trợ kỹ thuật: ${project.technicianPhone}" else ""
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Biên bản bàn giao hệ thống camera - ${project.siteName}")
                putExtra(Intent.EXTRA_TEXT, "Kính gửi quý khách biên bản bàn giao và thông tin hệ thống camera an ninh cho ${project.siteName}.$techContact")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ Biên bản Bàn giao"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
