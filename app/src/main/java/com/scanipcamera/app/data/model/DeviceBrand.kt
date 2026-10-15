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
    ONVIF("ONVIF", OnvifGreen),
    UNKNOWN("Unknown", UnknownGray);

    companion object {
        fun fromModelOrVendor(value: String): DeviceBrand {
            val lower = value.lowercase()
            return when {
                lower.contains("hikvision") || lower.contains("ds-2cd") || lower.contains("ds-7") -> HIKVISION
                lower.contains("ezviz") || lower.contains("cs-") -> EZVIZ
                lower.contains("dahua") || lower.contains("dh-") || lower.contains("dhi-") -> DAHUA
                lower.contains("imou") || lower.contains("ipc-") && lower.contains("imou") -> IMOU
                else -> ONVIF
            }
        }
    }
}
