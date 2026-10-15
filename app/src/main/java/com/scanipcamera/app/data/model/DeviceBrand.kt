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
                lower.contains("hikvision") || lower.contains("ds-2cd") || lower.contains("ds-7") || lower.contains("hilook") || lower.contains("hik-") -> HIKVISION
                lower.contains("ezviz") || lower.contains("cs-") -> EZVIZ
                lower.contains("kbvision") || lower.contains("kx-") -> KBVISION
                lower.contains("dahua") || lower.contains("dh-") || lower.contains("dhi-") -> DAHUA
                lower.contains("imou") || (lower.contains("ipc-") && lower.contains("imou")) -> IMOU
                lower.contains("uniview") || lower.contains("unv") || lower.contains("ipc3") || lower.contains("nvr3") -> UNIVIEW
                lower.contains("xiongmai") || lower.contains("xmeye") || lower.contains("xm_") || lower.contains("netsurveillance") -> XIONGMAI
                lower.contains("onvif") -> ONVIF
                else -> UNKNOWN
            }
        }
    }
}
