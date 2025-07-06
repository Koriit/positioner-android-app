package com.koriit.positioner.android.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import kotlinx.datetime.Clock

private enum class Screen { LIDAR, SETTINGS }

@Composable
fun PositionerApp() {
    MaterialTheme {
        val context = LocalContext.current
        val vm: LidarViewModel = viewModel(factory = LidarViewModelFactory(context))
        var screen by remember { mutableStateOf(Screen.LIDAR) }
        when (screen) {
            Screen.LIDAR -> LidarScreen(vm) { screen = Screen.SETTINGS }
            Screen.SETTINGS -> SettingsScreen(vm) { screen = Screen.LIDAR }
        }
    }
}

@Composable
fun LidarScreen(vm: LidarViewModel, onOpenSettings: () -> Unit) {
    val measurements by vm.measurements.collectAsState()
    val logs by AppLog.logs.collectAsState()
    val rotation by vm.rotation.collectAsState()
    val autoScale by vm.autoScale.collectAsState()
    val showLogs by vm.showLogs.collectAsState()
    val recording by vm.recording.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.saveSession(it, context) }
    }

    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onOpenSettings) { Text("Settings") }
            }
            LidarPlot(
                measurements,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .border(2.dp, color = Color.Blue),
                rotation = rotation,
                autoScale = autoScale,
                confidenceThreshold = confidence.toInt(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.rotate90() }) { Text("Rotate 90°") }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { vm.toggleRecording() }) {
                    Text(if (recording) "Stop Recording" else "Start Recording")
                }
                Button(onClick = { vm.clearSession() }) { Text("Reset") }
                Button(onClick = {
                    vm.toggleRecording()
                    val timestamp = Clock.System.now().toString().replace(":", "-")
                    saveLauncher.launch("lidar-session-$timestamp.json")
                }) { Text("Save") }
            }
            if (showLogs) {
                LogView(logs, modifier = Modifier.height(160.dp))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            LidarPlot(
                measurements,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(Color.White)
                    .border(2.dp, color = Color.Blue),
                rotation = rotation,
                autoScale = autoScale,
                confidenceThreshold = confidence.toInt(),
            )
            Column(modifier = Modifier
                .fillMaxHeight()
                .weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpenSettings) { Text("Settings") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.rotate90() }) { Text("Rotate 90°") }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.toggleRecording() }) {
                        Text(if (recording) "Stop Recording" else "Start Recording")
                    }
                    Button(onClick = { vm.clearSession() }) { Text("Reset") }
                    Button(onClick = {
                        vm.toggleRecording()
                        val timestamp = Clock.System.now().toString().replace(":", "-")
                        saveLauncher.launch("lidar-session-$timestamp.json")
                    }) { Text("Save") }
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
