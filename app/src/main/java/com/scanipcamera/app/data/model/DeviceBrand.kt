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
                lower.contains("imou") || lower.contains("ipc-a") || lower.contains("ipc-f") || lower.contains("ipc-c2") || lower.contains("ipc-ta") || lower.contains("ranger") -> IMOU
                lower.contains("dahua") || lower.contains("dh-") || lower.contains("dhi-") || lower.contains("ipc-hfw") || lower.contains("ipc-hdw") || lower.contains("ipc-hdbw") -> DAHUA
                lower.contains("uniview") || lower.contains("unv") || lower.contains("ipc3") || lower.contains("nvr3") -> UNIVIEW
                lower.contains("xiongmai") || lower.contains("xmeye") || lower.contains("xm_") || lower.contains("netsurveillance") -> XIONGMAI
                lower.contains("onvif") -> ONVIF
                else -> UNKNOWN
            }
        }
    }
}
