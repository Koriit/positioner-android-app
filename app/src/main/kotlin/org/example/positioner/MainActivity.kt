package org.example.positioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.collect
import org.example.positioner.lidar.LidarMeasurement
import org.example.positioner.lidar.LidarPlot
import org.example.positioner.lidar.LidarReader

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
    val measurements by produceState(initialValue = emptyList<LidarMeasurement>()) {
        val context = LocalContext.current
        val buffer = mutableListOf<LidarMeasurement>()
        try {
            val reader = LidarReader.openDefault(context)
            if (reader == null) return@produceState
            reader.measurements().collect { m ->
                buffer.add(m)
                if (buffer.size >= 480) {
                    value = buffer.toList()
                    buffer.clear()
                }
            }
        } catch (_: Exception) {
            // In case opening the serial port fails just keep empty data
        }
    }
    LidarPlot(measurements, modifier = Modifier.fillMaxSize())
}

@Preview
@Composable
fun PreviewPositionerApp() {
    PositionerApp()
}
