package org.example.positioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.positioner.lidar.LidarMeasurement
import org.example.positioner.lidar.LidarPlot
import org.example.positioner.lidar.LidarReader
import org.example.positioner.lidar.LidarDataSource
import org.example.positioner.lidar.FakeLidarReader
import org.example.positioner.logging.AppLog
import org.example.positioner.logging.LogView

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
    val context = LocalContext.current
    val logs by AppLog.logs.collectAsState()
    val measurements by produceState(initialValue = emptyList<LidarMeasurement>(), context, flushIntervalMs) {
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
                source.measurements().collect { m ->
                    if (buffer.size >= 480) buffer.removeFirst()
                    buffer.addLast(m)
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= flushIntervalMs.toLong()) {
                        AppLog.d("MainActivity", "Flushing: ${buffer.size}")
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
        LidarPlot(measurements, modifier = Modifier
            .size(300.dp)
            .background(Color.White)
            .border(2.dp, color = Color.Blue))
        Slider(
            value = flushIntervalMs,
            onValueChange = { flushIntervalMs = it },
            valueRange = 50f..1000f,
            modifier = Modifier.fillMaxWidth()
        )
        Text("Flush interval: ${flushIntervalMs.toInt()} ms")
        LogView(logs, modifier = Modifier.height(160.dp))
    }
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
