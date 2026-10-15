package com.scanipcamera.app.data.model

import java.net.URLEncoder

data class RtspStreamInfo(
    val title: String,
    val description: String,
    val rtspUrl: String
)

object RtspHelper {

    fun generateStreams(
        device: CameraDevice,
        username: String = "admin",
        password: String = ""
    ): List<RtspStreamInfo> {
        val userEnc = URLEncoder.encode(username, "UTF-8")
        val passEnc = URLEncoder.encode(password, "UTF-8")
        val auth = if (password.isNotEmpty()) "$userEnc:$passEnc@" else if (username.isNotEmpty()) "$userEnc@" else ""
        val ip = device.ipv4Address
        val port = if (device.rtspPort > 0) device.rtspPort else 554

        return when (device.brand) {
            DeviceBrand.HIKVISION, DeviceBrand.EZVIZ -> listOf(
                RtspStreamInfo(
                    title = "Hikvision / EZVIZ - Luồng chính (Full HD / 2K)",
                    description = "Độ nét cao nhất, dùng cho ghi hình và hiển thị chính",
                    rtspUrl = "rtsp://$auth$ip:$port/Streaming/Channels/101"
                ),
                RtspStreamInfo(
                    title = "Hikvision / EZVIZ - Luồng phụ (Sub-stream)",
                    description = "Băng thông nhẹ, xem mượt trên 4G/di động",
                    rtspUrl = "rtsp://$auth$ip:$port/Streaming/Channels/102"
                )
            )

            DeviceBrand.DAHUA, DeviceBrand.IMOU, DeviceBrand.KBVISION -> listOf(
                RtspStreamInfo(
                    title = "Dahua / IMOU / KBVISION - Luồng chính (Main)",
                    description = "Độ phân giải gốc tối đa (Channel 1, Subtype 0)",
                    rtspUrl = "rtsp://$auth$ip:$port/cam/realmonitor?channel=1&subtype=0"
                ),
                RtspStreamInfo(
                    title = "Dahua / IMOU / KBVISION - Luồng phụ (Sub)",
                    description = "Băng thông nhẹ (Channel 1, Subtype 1)",
                    rtspUrl = "rtsp://$auth$ip:$port/cam/realmonitor?channel=1&subtype=1"
                )
            )

            DeviceBrand.UNIVIEW -> listOf(
                RtspStreamInfo(
                    title = "Uniview (UNV) - Luồng chính",
                    description = "Unicast channel 1 stream 0",
                    rtspUrl = "rtsp://$auth$ip:$port/unicast/c1/s0/live"
                ),
                RtspStreamInfo(
                    title = "Uniview (UNV) - Luồng phụ",
                    description = "Unicast channel 1 stream 1",
                    rtspUrl = "rtsp://$auth$ip:$port/unicast/c1/s1/live"
                )
            )

            DeviceBrand.XIONGMAI -> listOf(
                RtspStreamInfo(
                    title = "Xiongmai / XMeye - Luồng chính",
                    description = "Standard Xiongmai RTSP channel 0",
                    rtspUrl = "rtsp://$auth$ip:$port/user=$userEnc&password=$passEnc&channel=1&stream=0.sdp"
                ),
                RtspStreamInfo(
                    title = "Xiongmai / XMeye - Luồng phụ",
                    description = "Channel 1 sub-stream",
                    rtspUrl = "rtsp://$auth$ip:$port/user=$userEnc&password=$passEnc&channel=1&stream=1.sdp"
                )
            )

            else -> listOf(
                RtspStreamInfo(
                    title = "Chuẩn ONVIF 1 (Chính)",
                    description = "Đường dẫn ONVIF thông dụng",
                    rtspUrl = "rtsp://$auth$ip:$port/onvif1"
                ),
                RtspStreamInfo(
                    title = "Chuẩn ONVIF 2 (Phụ / Live)",
                    description = "Đường dẫn RTSP phổ thông cho camera IP",
                    rtspUrl = "rtsp://$auth$ip:$port/live/ch0"
                )
            )
        }
    }
}
