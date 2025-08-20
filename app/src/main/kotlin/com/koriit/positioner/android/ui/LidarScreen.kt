package com.koriit.positioner.android.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.koriit.positioner.android.lidar.LidarPlot
import com.koriit.positioner.android.logging.AppLog
import com.koriit.positioner.android.logging.LogView
import com.koriit.positioner.android.viewmodel.LidarViewModel
import com.koriit.positioner.android.viewmodel.LidarViewModelFactory
import com.koriit.positioner.android.ui.SettingsPanel
import com.koriit.positioner.android.ui.ReplayControls
import kotlinx.datetime.Clock

@Composable
fun PositionerApp() {
    MaterialTheme {
        val context = LocalContext.current
        val vm: LidarViewModel = viewModel(factory = LidarViewModelFactory(context))
        LidarScreen(vm)
    }
}

@Composable
fun LidarScreen(vm: LidarViewModel) {
    val measurements by vm.measurements.collectAsState()
    val logs by AppLog.logs.collectAsState()
    val rotation by vm.rotation.collectAsState()
    val autoScale by vm.autoScale.collectAsState()
    val showLogs by vm.showLogs.collectAsState()
    val recording by vm.recording.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()
    val usbConnected by vm.usbConnected.collectAsState()
    val loading by vm.loadingReplay.collectAsState()
    val floorPlan by vm.floorPlan.collectAsState()
    val measurementOrientation by vm.measurementOrientation.collectAsState()
    val planScale by vm.planScale.collectAsState()
    val userPosition by vm.userPosition.collectAsState()
    val showGrid by vm.showOccupancyGrid.collectAsState()
    val occupancyGrid by vm.occupancyGridState.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.saveSession(it, context) }
    }
    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadRecording(it, context) }
    }
    val floorPlanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.loadFloorPlan(uris, context)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportSettings(it, context) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importSettings(it, context) }
    }

    var showSettings by remember { mutableStateOf(false) }

    val gradientMin by vm.gradientMin.collectAsState()
    val mps by vm.measurementsPerSecond.collectAsState()
    val rps by vm.rotationsPerSecond.collectAsState()
    val poseCombos by vm.poseCombinationsPerSecond.collectAsState()
    val poseMs by vm.poseEstimateMs.collectAsState()
    val poseScore by vm.poseScore.collectAsState()
    val poseAvg by vm.poseScoreAverage.collectAsState()

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(modifier = Modifier.fillMaxSize()) {
            val replaying by vm.replayMode.collectAsState()
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showSettings = !showSettings }) { Text("Settings") }
                Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import") }
                Button(onClick = { exportLauncher.launch("settings.json") }) { Text("Export") }
            }
            if (showSettings) {
                SettingsPanel(
                    vm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .border(2.dp, color = Color.Black)
            ) {
                LidarPlot(
                    measurements,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
                    floorPlan = floorPlan,
                    measurementOrientation = measurementOrientation,
                    planScale = planScale,
                    userPosition = userPosition,
                    occupancyGrid = occupancyGrid,
                    showOccupancyGrid = showGrid,
                )
                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Loading recording...")
                    }
                } else if (!usbConnected && !replaying) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Waiting for live LiDAR measurements...")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Measurements/s: $mps")
                    Text("Rotations/s: ${"%.2f".format(rps)}")
                    Text("Pose combos/s: ${"%.0f".format(poseCombos)}")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pose time ms: $poseMs")
                    Text("Pose score: $poseScore")
                    Text("Pose avg50: ${"%.1f".format(poseAvg)}")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.rotate90() },
                    enabled = floorPlan.isEmpty()
                ) { Text("Rotate 90°") }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.toggleRecording() },
                    enabled = !replaying
                ) {
                    Text(if (recording) "Stop Recording" else "Start Recording")
                }
                if (recording) {
                    Button(onClick = { vm.clearSession() }, enabled = !replaying) { Text("Reset") }
                    Button(
                        onClick = {
                            vm.toggleRecording()
                            val timestamp = Clock.System.now().toString().replace(":", "-")
                            saveLauncher.launch("lidar-session-$timestamp.json")
                        },
                        enabled = !replaying
                    ) { Text("Save") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { loadLauncher.launch(arrayOf("application/json")) },
                    enabled = !recording && !replaying && !loading
                ) { Text("Load") }
                if (replaying) {
                    Button(onClick = { vm.exitReplay() }) { Text("Exit Replay") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { floorPlanLauncher.launch(arrayOf("application/json")) },
                    enabled = !loading
                ) { Text("Load Floor Plan") }
            }
            if (replaying) {
                ReplayControls(vm)
            }
            if (showLogs) {
                LogView(logs, modifier = Modifier.height(160.dp))
            }
        }
    } else {
        val replaying by vm.replayMode.collectAsState()
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color.White)
                    .border(2.dp, color = Color.Black)
            ) {
                LidarPlot(
                    measurements,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
                    floorPlan = floorPlan,
                    measurementOrientation = measurementOrientation,
                    planScale = planScale,
                    userPosition = userPosition,
                    occupancyGrid = occupancyGrid,
                    showOccupancyGrid = showGrid,
                )
                if (loading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Loading recording...")
                    }
                } else if (!usbConnected && !replaying) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text("Waiting for live LiDAR measurements...")
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showSettings = !showSettings }) { Text("Settings") }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import") }
                    Button(onClick = { exportLauncher.launch("settings.json") }) { Text("Export") }
                }
                if (showSettings) {
                    SettingsPanel(
                        vm,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { vm.rotate90() },
                        enabled = floorPlan.isEmpty()
                    ) { Text("Rotate 90°") }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.toggleRecording() },
                        enabled = !replaying
                    ) {
                        Text(if (recording) "Stop Recording" else "Start Recording")
                    }
                    if (recording) {
                        Button(onClick = { vm.clearSession() }, enabled = !replaying) { Text("Reset") }
                        Button(
                            onClick = {
                                vm.toggleRecording()
                                val timestamp = Clock.System.now().toString().replace(":", "-")
                                saveLauncher.launch("lidar-session-$timestamp.json")
                            },
                            enabled = !replaying
                        ) { Text("Save") }
                    }
                }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { loadLauncher.launch(arrayOf("application/json")) },
                    enabled = !recording && !replaying && !loading
                ) { Text("Load") }
                if (replaying) {
                    Button(onClick = { vm.exitReplay() }) { Text("Exit Replay") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { floorPlanLauncher.launch(arrayOf("application/json")) },
                    enabled = !loading
                ) { Text("Load Floor Plan") }
            }
            if (replaying) {
                ReplayControls(vm)
            }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Measurements/s: $mps")
                        Text("Rotations/s: ${"%.2f".format(rps)}")
                        Text("Pose combos/s: ${"%.0f".format(poseCombos)}")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pose time ms: $poseMs")
                        Text("Pose score: $poseScore")
                        Text("Pose avg50: ${"%.1f".format(poseAvg)}")
                    }
                }
                if (showLogs) {
                    LogView(logs, modifier = Modifier.height(160.dp))
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
