package com.koriit.positioner.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.koriit.positioner.android.localization.PoseAlgorithm
import com.koriit.positioner.android.ui.SliderWithActions
import com.koriit.positioner.android.viewmodel.LidarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(vm: LidarViewModel, modifier: Modifier = Modifier) {
    val autoScale by vm.autoScale.collectAsState()
    val showLogs by vm.showLogs.collectAsState()
    val filterPoseInput by vm.filterPoseInput.collectAsState()
    val bufferSize by vm.bufferSize.collectAsState()
    val flushInterval by vm.flushIntervalMs.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()
    val gradientMin by vm.gradientMin.collectAsState()
    val minDistance by vm.minDistance.collectAsState()
    val isolationDistance by vm.isolationDistance.collectAsState()
    val isolationMinNeighbours by vm.isolationMinNeighbours.collectAsState()
    val poseMissPenalty by vm.poseMissPenalty.collectAsState()
    val showGrid by vm.showOccupancyGrid.collectAsState()
    val gridCellSize by vm.gridCellSize.collectAsState()
    val useLastPose by vm.useLastPose.collectAsState()
    val poseAlgorithm by vm.poseAlgorithm.collectAsState()

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoScale, onCheckedChange = { vm.autoScale.value = it })
            Text("Auto scale")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showLogs, onCheckedChange = { vm.showLogs.value = it })
            Text("Show logs")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = filterPoseInput, onCheckedChange = { vm.filterPoseInput.value = it })
            Text("Filter pose input")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showGrid, onCheckedChange = { vm.showOccupancyGrid.value = it })
            Text("Show occupancy grid")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useLastPose, onCheckedChange = { vm.useLastPose.value = it })
            Text("Use last pose")
        }
        var algoExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = algoExpanded, onExpandedChange = { algoExpanded = !algoExpanded }) {
            TextField(
                value = poseAlgorithm.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Pose algorithm") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algoExpanded) },
                modifier = Modifier.menuAnchor()
            )
            DropdownMenu(expanded = algoExpanded, onDismissRequest = { algoExpanded = false }) {
                PoseAlgorithm.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            vm.poseAlgorithm.value = option
                            algoExpanded = false
                        }
                    )
                }
            }
        }
        Text("Flush interval: ${flushInterval.toInt()} ms")
        SliderWithActions(
            value = flushInterval,
            onValueChange = { vm.flushIntervalMs.value = it },
            valueRange = 50f..1000f,
            onReset = { vm.resetFlushInterval() }
        )
        Text("Color gradient min: ${gradientMin.toInt()}")
        SliderWithActions(
            value = gradientMin,
            onValueChange = { vm.gradientMin.value = it },
            valueRange = 0f..255f,
            onReset = { vm.resetGradientMin() }
        )
        Text("Confidence threshold: ${confidence.toInt()}")
        SliderWithActions(
            value = confidence,
            onValueChange = { vm.confidenceThreshold.value = it },
            valueRange = 0f..255f,
            onReset = { vm.resetConfidenceThreshold() }
        )
        Text("Min distance: ${"%.2f".format(minDistance)} m")
        SliderWithActions(
            value = minDistance,
            onValueChange = { vm.minDistance.value = it },
            valueRange = 0f..2f,
            onReset = { vm.resetMinDistance() }
        )
        Text("Isolation distance: ${"%.2f".format(isolationDistance)} m")
        SliderWithActions(
            value = isolationDistance,
            onValueChange = { vm.isolationDistance.value = it },
            valueRange = 0f..5f,
            onReset = { vm.resetIsolationDistance() }
        )
        Text("Isolation neighbours: $isolationMinNeighbours")
        SliderWithActions(
            value = isolationMinNeighbours.toFloat(),
            onValueChange = { vm.isolationMinNeighbours.value = it.toInt() },
            valueRange = 0f..10f,
            onReset = { vm.resetIsolationMinNeighbours() }
        )
        Text("Grid cell size: ${"%.2f".format(gridCellSize)} m")
        SliderWithActions(
            value = gridCellSize,
            onValueChange = { vm.updateGridCellSize(it) },
            valueRange = 0.05f..0.5f,
            onReset = { vm.resetGridCellSize() }
        )
        Text("Buffer size: $bufferSize")
        SliderWithActions(
            value = bufferSize.toFloat(),
            onValueChange = { vm.bufferSize.value = it.toInt() },
            valueRange = 100f..1000f,
            onReset = { vm.resetBufferSize() }
        )
        Text("Pose miss penalty: ${poseMissPenalty.toInt()}")
        SliderWithActions(
            value = poseMissPenalty,
            onValueChange = { vm.poseMissPenalty.value = it },
            valueRange = 0f..5f,
            onReset = { vm.resetPoseMissPenalty() }
        )
    }
}
