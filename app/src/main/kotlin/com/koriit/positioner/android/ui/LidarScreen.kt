package com.koriit.positioner.android.ui

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val showMeasurements by vm.showMeasurements.collectAsState()
    val showLines by vm.showLines.collectAsState()
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
    val lineFeatures by vm.lineFeatures.collectAsState()
    val lineFilterEnabled by vm.lineFilterEnabled.collectAsState()
    val lineLengthPx by vm.lineLengthPx.collectAsState()
    val lineInlierPx by vm.lineInlierPx.collectAsState()
    val lengthPercentile by vm.lineFilterLengthPercentile.collectAsState()
    val inlierPercentile by vm.lineFilterInlierPercentile.collectAsState()
    val gyroscopeState by vm.gyroscopeState.collectAsState()
    val gyroscopeRotationEnabled by vm.gyroscopeRotationEnabled.collectAsState()
    val gyroscopeRotation by vm.gyroscopeRotation.collectAsState()
    val orientationState by vm.orientationState.collectAsState()
    val orientationRotationEnabled by vm.orientationRotationEnabled.collectAsState()
    val orientationRotation by vm.orientationRotation.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            vm.refreshGyroscope()
        }
    }
    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        uri?.let { vm.startRecording(it, context) }
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

    LaunchedEffect(gyroscopeState) {
        if (gyroscopeState == LidarViewModel.GyroscopeState.NO_PERMISSION) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BODY_SENSORS,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
                )
            )
        }
    }

    val gradientMin by vm.gradientMin.collectAsState()
    val mps by vm.measurementsPerSecond.collectAsState()
    val rps by vm.rotationsPerSecond.collectAsState()
    val poseCombos by vm.poseCombinationsPerSecond.collectAsState()
    val poseMs by vm.poseEstimateMs.collectAsState()
    val poseScore by vm.poseScore.collectAsState()
    val poseAvg by vm.poseScoreAverage.collectAsState()
    val filteredCount by vm.filteredMeasurements.collectAsState()
    val filteredPct by vm.filteredPercentage.collectAsState()
    val corruptedPackets by vm.corruptedPackets.collectAsState()

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val gyroStatusSuffix = if (gyroscopeRotationEnabled) "" else " (off)"
    val orientationStatusSuffix = when {
        !orientationRotationEnabled -> " (off)"
        orientationState == LidarViewModel.OrientationState.NO_SENSOR -> " (no sensor)"
        orientationState == LidarViewModel.OrientationState.DISABLED -> " (disabled)"
        else -> ""
    }

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
                    measurements = measurements,
                    lines = lineFeatures,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
                    floorPlan = floorPlan,
                    measurementOrientation = measurementOrientation,
                    gyroscopeRotation = if (gyroscopeRotationEnabled) gyroscopeRotation else 0f,
                    orientationRotation = if (orientationRotationEnabled) orientationRotation else 0f,
                    planScale = planScale,
                    userPosition = userPosition,
                    occupancyGrid = occupancyGrid,
                    showOccupancyGrid = showGrid,
                    showMeasurements = showMeasurements,
                    showLines = showLines,
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
                    Text("Filtered: $filteredCount (${"%.1f".format(filteredPct)}%)")
                    Text("Corrupted packets: $corruptedPackets")
                    Text("Lines: ${lineFeatures.size}")
                    if (lineFilterEnabled) {
                        Text("Len P${lengthPercentile.toInt()}: ${"%.2f".format(lineLengthPx)} m")
                        Text("Pts P${inlierPercentile.toInt()}: ${"%.0f".format(lineInlierPx)}")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pose time ms: $poseMs")
                    Text("Pose score: $poseScore")
                    Text("Pose avg50: ${"%.1f".format(poseAvg)}")
                    Text("Orientation: ${"%.1f".format(measurementOrientation)}°")
                    Text("Orientation sensor: ${"%.1f".format(orientationRotation)}°$orientationStatusSuffix")
                    Text("Gyro rotation: ${"%.1f".format(gyroscopeRotation)}°$gyroStatusSuffix")
                    Text("Scale: ${"%.2f".format(planScale)}")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (recording) {
                            vm.stopRecording()
                        } else {
                            val timestamp = Clock.System.now().toString().replace(":", "-")
                            recordLauncher.launch("lidar-session-$timestamp.json.gz")
                        }
                    },
                    enabled = !replaying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (recording) "Stop Recording" else "Start Recording")
                }
                Button(
                    onClick = { loadLauncher.launch(arrayOf("application/gzip", "application/json")) },
                    enabled = !recording && !replaying && !loading,
                    modifier = Modifier.weight(1f)
                ) { Text("Load") }
                if (replaying) {
                    Button(
                        onClick = { vm.exitReplay() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Exit Replay") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { vm.rotate90() },
                    enabled = floorPlan.isEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("Rotate 90°") }
                Button(
                    onClick = { floorPlanLauncher.launch(arrayOf("application/json")) },
                    enabled = !loading,
                    modifier = Modifier.weight(1f)
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
                    measurements = measurements,
                    lines = lineFeatures,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
                    floorPlan = floorPlan,
                    measurementOrientation = measurementOrientation,
                    gyroscopeRotation = if (gyroscopeRotationEnabled) gyroscopeRotation else 0f,
                    orientationRotation = if (orientationRotationEnabled) orientationRotation else 0f,
                    planScale = planScale,
                    userPosition = userPosition,
                    occupancyGrid = occupancyGrid,
                    showOccupancyGrid = showGrid,
                    showMeasurements = showMeasurements,
                    showLines = showLines,
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
                        modifier = Modifier.fillMaxWidth(),
                        scrollable = false,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (recording) {
                                vm.stopRecording()
                            } else {
                                val timestamp = Clock.System.now().toString().replace(":", "-")
                                recordLauncher.launch("lidar-session-$timestamp.json.gz")
                            }
                        },
                        enabled = !replaying,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (recording) "Stop Recording" else "Start Recording")
                    }
                    Button(
                        onClick = { loadLauncher.launch(arrayOf("application/gzip", "application/json")) },
                        enabled = !recording && !replaying && !loading,
                        modifier = Modifier.weight(1f)
                    ) { Text("Load") }
                    if (replaying) {
                        Button(
                            onClick = { vm.exitReplay() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Exit Replay") }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { vm.rotate90() },
                        enabled = floorPlan.isEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Rotate 90°") }
                    Button(
                        onClick = { floorPlanLauncher.launch(arrayOf("application/json")) },
                        enabled = !loading,
                        modifier = Modifier.weight(1f)
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
                        Text("Filtered: $filteredCount (${"%.1f".format(filteredPct)}%)")
                        Text("Corrupted packets: $corruptedPackets")
                        Text("Lines: ${lineFeatures.size}")
                        if (lineFilterEnabled) {
                            Text("Len P${lengthPercentile.toInt()}: ${"%.2f".format(lineLengthPx)} m")
                            Text("Pts P${inlierPercentile.toInt()}: ${"%.0f".format(lineInlierPx)}")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pose time ms: $poseMs")
                        Text("Pose score: $poseScore")
                        Text("Pose avg50: ${"%.1f".format(poseAvg)}")
                        Text("Orientation: ${"%.1f".format(measurementOrientation)}°")
                        Text("Orientation sensor: ${"%.1f".format(orientationRotation)}°$orientationStatusSuffix")
                        Text("Gyro rotation: ${"%.1f".format(gyroscopeRotation)}°$gyroStatusSuffix")
                        Text("Scale: ${"%.2f".format(planScale)}")
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
