package com.scanipcamera.app.data.model

data class NetworkConfigPayload(
    val macAddress: String,
    val newIp: String,
    val newSubnetMask: String,
    val newGateway: String,
    val newPort: Int,
    val isDhcp: Boolean,
    val currentPassword: String
)
