package com.koriit.positioner.android.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val flushInterval by vm.flushIntervalMs.collectAsState()
    val rotation by vm.rotation.collectAsState()
    val autoScale by vm.autoScale.collectAsState()
    val recording by vm.recording.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.saveSession(it, context) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        LidarPlot(
            measurements,
            modifier = Modifier
                .size(300.dp)
                .background(Color.White)
                .border(2.dp, color = Color.Blue),
            rotation = rotation,
            autoScale = autoScale,
            confidenceThreshold = confidence.toInt(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.rotate90() }) { Text("Rotate 90°") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoScale, onCheckedChange = { vm.autoScale.value = it })
            Text("Auto scale")
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
        Slider(
            value = flushInterval,
            onValueChange = { vm.flushIntervalMs.value = it },
            valueRange = 50f..1000f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Flush interval: ${'$'}{flushInterval.toInt()} ms")
        Slider(
            value = confidence,
            onValueChange = { vm.confidenceThreshold.value = it },
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Confidence threshold: ${'$'}{confidence.toInt()}")
        LogView(logs, modifier = Modifier.height(160.dp))
    }
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
