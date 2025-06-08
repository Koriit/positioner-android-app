package org.example.positioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
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
    val context = LocalContext.current
    val logs by AppLog.logs.collectAsState()
    val measurements by produceState(initialValue = emptyList<LidarMeasurement>(), context) {
        val buffer = ArrayDeque<LidarMeasurement>()
        var counter = 0
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
                    counter++
                    if (counter >= 25) {
                        counter = 0
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
        LidarPlot(measurements, modifier = Modifier.weight(1f))
        LogView(logs, modifier = Modifier.height(160.dp))
    }
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
