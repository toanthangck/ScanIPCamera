package com.scanipcamera.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanipcamera.app.data.model.DeviceBrand
import com.scanipcamera.app.ui.components.ActivationDialog
import com.scanipcamera.app.ui.components.ConfigDialog
import com.scanipcamera.app.ui.components.DeviceCard
import com.scanipcamera.app.ui.viewmodel.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val selectedBrandFilter by viewModel.selectedBrandFilter.collectAsState()
    val editingDevice by viewModel.editingDevice.collectAsState()
    val activatingDevice by viewModel.activatingDevice.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scan IP Camera",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "SADP & Dahua ConfigTool for Mobile",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startScanning() },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Quét lại")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.startScanning() },
                icon = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    }
                },
                text = { Text(if (isScanning) "Đang dò mạng..." else "Quét thiết bị") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Linear Progress Indicator when scanning
            AnimatedVisibility(visible = isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Brand Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedBrandFilter == null,
                    onClick = { viewModel.setBrandFilter(null) },
                    label = { Text("Tất cả (${devices.size})") }
                )

                DeviceBrand.entries.forEach { brand ->
                    FilterChip(
                        selected = selectedBrandFilter == brand,
                        onClick = {
                            viewModel.setBrandFilter(
                                if (selectedBrandFilter == brand) null else brand
                            )
                        },
                        label = { Text(brand.displayName) }
                    )
                }
            }

            // Device List or Empty State
            if (devices.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chưa tìm thấy camera nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hãy kết nối cùng mạng Wi-Fi với Camera / Đầu ghi và bấm nút \"Quét thiết bị\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onConfigureClick = { viewModel.openConfigDialog(device) },
                            onActivateClick = { viewModel.openActivationDialog(device) }
                        )
                    }
                }
            }
        }
    }

    // Config Dialog
    editingDevice?.let { device ->
        ConfigDialog(
            device = device,
            onDismiss = { viewModel.closeConfigDialog() },
            onSave = { payload -> viewModel.applyNetworkConfig(device, payload) }
        )
    }

    // Activation Dialog
    activatingDevice?.let { device ->
        ActivationDialog(
            device = device,
            onDismiss = { viewModel.closeActivationDialog() },
            onActivate = { newPassword -> viewModel.activateDevice(device, newPassword) }
        )
    }
}
