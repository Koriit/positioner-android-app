package com.koriit.positioner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import com.koriit.positioner.android.lidar.LidarMeasurement
import com.koriit.positioner.android.lidar.LidarPlot
import com.koriit.positioner.android.lidar.LidarReader
import com.koriit.positioner.android.lidar.LidarDataSource
import com.koriit.positioner.android.lidar.FakeLidarReader
import com.koriit.positioner.android.logging.AppLog
import com.koriit.positioner.android.logging.LogView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PositionerApp()
        }
    }
}

@Composable
fun PositionerApp() {
    MaterialTheme {
        LidarScreen()
    }
}

@Composable
private fun LidarScreen() {
    var flushIntervalMs by remember { mutableStateOf(100f) }
    var rotation by remember { mutableStateOf(0) }
    var autoScale by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var confidenceThreshold by remember { mutableStateOf(200f) }
    val sessionData = remember { mutableStateListOf<LidarMeasurement>() }
    val context = LocalContext.current
    val logs by AppLog.logs.collectAsState()
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                val json = Json.encodeToString(sessionData.toList())
                sessionData.clear()
                out.write(json.toByteArray())
            }

        }
    }
    val measurements by produceState(
        initialValue = emptyList<LidarMeasurement>(),
        context,
        flushIntervalMs,
        recording,
        confidenceThreshold
    ) {
        val buffer = ArrayDeque<LidarMeasurement>()
        var lastFlush = System.currentTimeMillis()
        try {
            AppLog.d("MainActivity", "Opening LidarReader")
            val realReader = withContext(Dispatchers.IO) { LidarReader.openDefault(context) }
            val source: LidarDataSource = if (realReader == null) {
                AppLog.d("MainActivity", "Using FakeLidarReader")
                FakeLidarReader()
            } else realReader
            AppLog.d("MainActivity", "Starting measurement collection")
            withContext(Dispatchers.IO) {
                source.measurements().flowOn(Dispatchers.IO).collect { m ->
                    if (m.confidence >= confidenceThreshold.toInt()) {
                        if (buffer.size >= 480) buffer.removeFirst()
                        buffer.addLast(m)
                        if (recording) sessionData.add(m)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= flushIntervalMs.toLong()) {
//                        AppLog.d("MainActivity", "Flushing: ${buffer.size}")
                        lastFlush = now
                        val result = buffer.toList()
                        withContext(Dispatchers.Main) { value = result }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.d("MainActivity", "Measurement loop failed", e)
        }
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
            confidenceThreshold = confidenceThreshold.toInt(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { rotation += 90 }) {
                Text("Rotate 90°")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoScale, onCheckedChange = { autoScale = it })
            Text("Auto scale")
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { recording = !recording }) {
                Text(if (recording) "Stop Recording" else "Start Recording")
            }
            Button(onClick = { sessionData.clear() }) {
                Text("Reset")
            }
            Button(onClick = {
                recording = !recording
                val timestamp = Clock.System.now().toString().replace(":", "-")
                saveLauncher.launch("lidar-session-$timestamp.json")
            }) {
                Text("Save")
            }
        }
        Slider(
            value = flushIntervalMs,
            onValueChange = { flushIntervalMs = it },
            valueRange = 50f..1000f,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Flush interval: ${flushIntervalMs.toInt()} ms")
        Slider(
            value = confidenceThreshold,
            onValueChange = { confidenceThreshold = it },
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Confidence threshold: ${confidenceThreshold.toInt()}")
        LogView(logs, modifier = Modifier.height(160.dp))
    }
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
