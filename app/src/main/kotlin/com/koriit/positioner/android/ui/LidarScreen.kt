package com.koriit.positioner.android.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.saveSession(it, context) }
    }
    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.loadRecording(it, context) }
    }

    var showSettings by remember { mutableStateOf(false) }

    val gradientMin by vm.gradientMin.collectAsState()
    val mps by vm.measurementsPerSecond.collectAsState()
    val rps by vm.rotationsPerSecond.collectAsState()

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(modifier = Modifier.fillMaxSize()) {
            val replaying by vm.replayMode.collectAsState()
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showSettings = !showSettings }) { Text("Settings") }
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
                    .border(2.dp, color = Color.Blue)
            ) {
                LidarPlot(
                    measurements,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
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
            Text("Measurements/s: $mps")
            Text("Rotations/s: ${"%.2f".format(rps)}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.rotate90() }) { Text("Rotate 90°") }
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
                    .border(2.dp, color = Color.Blue)
            ) {
                LidarPlot(
                    measurements,
                    modifier = Modifier.fillMaxSize(),
                    rotation = rotation,
                    autoScale = autoScale,
                    confidenceThreshold = confidence.toInt(),
                    gradientMin = gradientMin.toInt(),
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
            Column(modifier = Modifier
                .fillMaxHeight()
                .weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showSettings = !showSettings }) { Text("Settings") }
                }
                if (showSettings) {
                    SettingsPanel(
                        vm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.rotate90() }) { Text("Rotate 90°") }
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
                if (replaying) {
                    ReplayControls(vm)
                }
                Text("Measurements/s: $mps")
                Text("Rotations/s: ${"%.2f".format(rps)}")
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
