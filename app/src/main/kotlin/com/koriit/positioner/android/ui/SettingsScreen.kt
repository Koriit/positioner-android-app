package com.koriit.positioner.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.koriit.positioner.android.viewmodel.LidarViewModel

@Composable
fun SettingsScreen(vm: LidarViewModel, onBack: () -> Unit) {
    val autoScale by vm.autoScale.collectAsState()
    val showLogs by vm.showLogs.collectAsState()
    val bufferSize by vm.bufferSize.collectAsState()
    val flushInterval by vm.flushIntervalMs.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack) { Text("Back") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoScale, onCheckedChange = { vm.autoScale.value = it })
            Text("Auto scale")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showLogs, onCheckedChange = { vm.showLogs.value = it })
            Text("Show logs")
        }
        Text("Flush interval: ${flushInterval.toInt()} ms")
        Slider(
            value = flushInterval,
            onValueChange = { vm.flushIntervalMs.value = it },
            valueRange = 50f..1000f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Confidence threshold: ${confidence.toInt()}")
        Slider(
            value = confidence,
            onValueChange = { vm.confidenceThreshold.value = it },
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Buffer size: $bufferSize")
        Slider(
            value = bufferSize.toFloat(),
            onValueChange = { vm.bufferSize.value = it.toInt() },
            valueRange = 100f..1000f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
