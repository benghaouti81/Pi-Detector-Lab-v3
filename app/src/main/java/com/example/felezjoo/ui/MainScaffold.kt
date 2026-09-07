package com.example.felezjoo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.felezjoo.ui.screens.AlgorithmCompareScreen
import com.example.felezjoo.ui.screens.CalibrationDiagnosticsScreen
import com.example.felezjoo.ui.screens.CommandConsoleScreen
import com.example.felezjoo.ui.screens.DashboardScreen
import com.example.felezjoo.ui.screens.DelayFinderScreen
import com.example.felezjoo.ui.screens.DetectorControlsScreen
import com.example.felezjoo.ui.screens.FieldModeScreen
import com.example.felezjoo.ui.screens.GroundNoiseScreen
import com.example.felezjoo.ui.screens.HelpSpecsScreen
import com.example.felezjoo.ui.screens.IntegrationAbcScreen
import com.example.felezjoo.ui.screens.LiveWaveformScreen
import com.example.felezjoo.ui.screens.PacketMonitorScreen
import com.example.felezjoo.ui.screens.RecorderReplayScreen
import com.example.felezjoo.ui.screens.SerialMonitorScreen
import com.example.felezjoo.ui.screens.SignalLabScreen
import com.example.felezjoo.ui.screens.TargetIronScreen
import com.example.felezjoo.ui.screens.UsbDeviceScreen
import com.example.felezjoo.usb.UsbConnectionState
import com.example.felezjoo.viewmodel.FelezJooViewModel
import com.example.ui.theme.LabBackground
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabError
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSurface
import com.example.ui.theme.LabSurfaceHighlight
import com.example.ui.theme.LabSurfaceVariant
import com.example.ui.theme.LabTertiary
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(viewModel: FelezJooViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val usbState by viewModel.usbState.collectAsState()
    val isSimulation by viewModel.isSimulationMode.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val audioManager = viewModel.audioManager

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = LabSurface,
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = LabPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, LabPrimary),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.ShowChart, contentDescription = null, tint = LabPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("FELEZJOO PI LAB", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White)
                            Text("PULSE INDUCTION DSP 2.0", fontSize = 10.sp, color = LabPrimary, fontFamily = FontFamily.Monospace)
                        }
                    }

                    HorizontalDivider(color = LabBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Grouped Navigation Items
                    val categories = listOf("Detection", "Research", "Hardware", "Diagnostics", "Data", "Info")
                    categories.forEach { cat ->
                        Text(
                            cat.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LabTextMuted,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
                        )

                        Screen.entries.filter { it.category == cat }.forEach { sc ->
                            val isSel = currentScreen == sc
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        getScreenIcon(sc),
                                        contentDescription = null,
                                        tint = if (isSel) LabPrimary else LabTextSecondary
                                    )
                                },
                                label = {
                                    Text(
                                        sc.title,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                selected = isSel,
                                onClick = {
                                    viewModel.navigateTo(sc)
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = LabSurfaceHighlight,
                                    selectedTextColor = LabPrimary,
                                    unselectedTextColor = Color(0xFFD0DCE8),
                                    selectedIconColor = LabPrimary,
                                    unselectedIconColor = LabTextSecondary
                                ),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .testTag("nav_item_${sc.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentScreen.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val stateColor = if (isSimulation) LabTertiary else when (usbState) {
                                    UsbConnectionState.STREAMING, UsbConnectionState.READY -> LabSecondary
                                    UsbConnectionState.DISCONNECTED -> LabTextMuted
                                    else -> LabError
                                }
                                Box(modifier = Modifier.size(6.dp).background(stateColor, RoundedCornerShape(3.dp)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isSimulation) "SIMULATION" else usbState.name,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        // Quick Mute Toggle
                        IconButton(
                            onClick = { audioManager.isMuted = !audioManager.isMuted },
                            modifier = Modifier.size(36.dp).testTag("quick_mute_btn")
                        ) {
                            Icon(
                                if (audioManager.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = if (audioManager.isMuted) LabError else Color.White
                            )
                        }

                        // Stream Toggle Action in Top Bar
                        IconButton(
                            onClick = {
                                if (isStreaming) viewModel.stopStreaming() else viewModel.startStreaming()
                            },
                            modifier = Modifier.size(36.dp).testTag("topbar_stream_toggle")
                        ) {
                            Icon(
                                if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Stream",
                                tint = if (isStreaming) LabError else LabSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LabSurface,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = LabBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.DASHBOARD -> DashboardScreen(viewModel)
                    Screen.LIVE_WAVEFORM -> LiveWaveformScreen(viewModel)
                    Screen.SIGNAL_LAB -> SignalLabScreen(viewModel)
                    Screen.DETECTOR_CONTROLS -> DetectorControlsScreen(viewModel)
                    Screen.TARGET_IRON -> TargetIronScreen(viewModel)
                    Screen.GROUND_NOISE -> GroundNoiseScreen(viewModel)
                    Screen.DELAY_FINDER -> DelayFinderScreen(viewModel)
                    Screen.INTEGRATION_ABC -> IntegrationAbcScreen(viewModel)
                    Screen.USB_DEVICE -> UsbDeviceScreen(viewModel)
                    Screen.SERIAL_MONITOR -> SerialMonitorScreen(viewModel)
                    Screen.COMMAND_CONSOLE -> CommandConsoleScreen(viewModel)
                    Screen.PACKET_MONITOR -> PacketMonitorScreen(viewModel)
                    Screen.RECORDER_REPLAY -> RecorderReplayScreen(viewModel)
                    Screen.ALGORITHM_COMPARE -> AlgorithmCompareScreen(viewModel)
                    Screen.CALIBRATION_DIAGNOSTICS -> CalibrationDiagnosticsScreen(viewModel)
                    Screen.FIELD_MODE -> FieldModeScreen(viewModel)
                    Screen.HELP_ABOUT -> HelpSpecsScreen()
                }
            }
        }
    }
}

private fun getScreenIcon(screen: Screen): ImageVector {
    return when (screen) {
        Screen.DASHBOARD -> Icons.Default.Dashboard
        Screen.LIVE_WAVEFORM -> Icons.Default.ShowChart
        Screen.SIGNAL_LAB -> Icons.Default.Science
        Screen.DETECTOR_CONTROLS -> Icons.Default.Build
        Screen.TARGET_IRON -> Icons.Default.Grain
        Screen.GROUND_NOISE -> Icons.Default.SelectAll
        Screen.DELAY_FINDER -> Icons.Default.Timer
        Screen.INTEGRATION_ABC -> Icons.Default.ViewInAr
        Screen.USB_DEVICE -> Icons.Default.Cable
        Screen.SERIAL_MONITOR -> Icons.Default.Terminal
        Screen.COMMAND_CONSOLE -> Icons.Default.Terminal
        Screen.PACKET_MONITOR -> Icons.Default.Visibility
        Screen.RECORDER_REPLAY -> Icons.Default.History
        Screen.ALGORITHM_COMPARE -> Icons.Default.Compare
        Screen.CALIBRATION_DIAGNOSTICS -> Icons.Default.HealthAndSafety
        Screen.FIELD_MODE -> Icons.Default.WbSunny
        Screen.HELP_ABOUT -> Icons.Default.Description
    }
}
