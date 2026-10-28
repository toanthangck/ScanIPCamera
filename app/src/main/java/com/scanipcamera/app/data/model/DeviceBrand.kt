package com.scanipcamera.app.data.model

import androidx.compose.ui.graphics.Color
import com.scanipcamera.app.ui.theme.*

enum class DeviceBrand(
    val displayName: String,
    val badgeColor: Color
) {
    HIKVISION("Hikvision", HikvisionRed),
    DAHUA("Dahua", DahuaOrange),
    EZVIZ("EZVIZ", EzvizBlue),
    IMOU("IMOU", ImouYellow),
    UNIVIEW("Uniview", UniviewBlue),
    KBVISION("KBVISION", KbvisionBlue),
    XIONGMAI("Xiongmai", XiongmaiGreen),
    ONVIF("ONVIF", OnvifGreen),
    UNKNOWN("Thiết bị khác", UnknownGray);

    companion object {
        fun fromModelOrVendor(value: String): DeviceBrand {
            val lower = value.lowercase()
            return when {
                lower.contains("hikvision") || lower.contains("ds-2cd") || lower.contains("ds-2cv") || lower.contains("ds-7") || lower.contains("hilook") || lower.contains("hik-") -> HIKVISION
                lower.contains("ezviz") || lower.contains("cs-") -> EZVIZ
                lower.contains("kbvision") || lower.contains("kx-") -> KBVISION
                lower.contains("imou") || lower.contains("lechange") || lower.contains("ranger") || lower.contains("cruiser") ||
                    lower.contains("cue") || lower.contains("bullet") || lower.contains("ipc-a") || lower.contains("ipc-f") ||
                    lower.contains("ipc-c2") || lower.contains("ipc-c") || lower.contains("ipc-ta") || lower.contains("ipc-s") ||
                    lower.contains("paja") || lower.contains("38:af:29") ||
                    Regex("(?:\\b|[_-])[4-9][d|h|l|k][0-9a-z]{5,}", RegexOption.IGNORE_CASE).containsMatchIn(value) -> IMOU
                lower.contains("dahua") || lower.contains("dh-") || lower.contains("dhi-") || lower.contains("ipc-hfw") || lower.contains("ipc-hdw") || lower.contains("ipc-hdbw") -> DAHUA
                lower.contains("uniview") || lower.contains("unv") || lower.contains("ipc3") || lower.contains("nvr3") -> UNIVIEW
                lower.contains("xiongmai") || lower.contains("xmeye") || lower.contains("xm_") || lower.contains("netsurveillance") -> XIONGMAI
                lower.contains("onvif") -> ONVIF
                else -> UNKNOWN
            }
        }

        fun fromMacAddress(mac: String): DeviceBrand {
            if (mac.length < 8) return UNKNOWN
            val oui = mac.take(8).uppercase().replace("-", ":")
            return when (oui) {
                "38:AF:29", "D4:40:F0", "74:AC:B9", "5C:E2:8C", "3C:A6:2F", "B4:0C:25" -> IMOU
                "3C:EF:8C", "A0:BD:1D", "E0:50:8B", "4C:11:BF", "14:A7:8B", "BC:32:5F",
                "90:02:A9", "54:C4:15", "B8:F8:83", "70:B1:3D", "68:9C:E2", "40:2C:76",
                "E4:24:9C", "88:29:9C", "A8:9C:ED", "6C:1C:71", "54:89:98", "F4:39:09",
                "64:DB:8B", "2C:26:17", "00:1A:07", "04:CF:8C", "44:32:C8", "9C:14:63" -> DAHUA
                "B0:D5:9D", "38:D2:69", "E4:AA:EC", "44:03:77", "D8:B0:4C" -> EZVIZ
                "44:19:B6", "C4:2F:90", "BC:54:36", "28:57:BE", "18:68:CB", "68:6D:BC",
                "70:AF:6A", "8C:E7:48", "A4:14:37", "C0:56:E3", "08:EA:44", "10:D0:7A",
                "24:18:C6", "38:1C:1A", "48:EA:63", "54:27:58", "60:21:C0", "74:03:BD",
                "80:61:5F", "84:9A:40", "94:E1:AC", "A0:41:5E", "AC:64:17", "C8:02:8F",
                "D4:6E:5C", "E4:1F:13", "EC:69:FE", "F8:4D:FC" -> HIKVISION
                "00:0F:7C", "2C:6B:7D" -> UNIVIEW
                else -> UNKNOWN
            }
        }
    }
}
