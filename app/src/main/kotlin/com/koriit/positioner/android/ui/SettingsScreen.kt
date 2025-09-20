package com.koriit.positioner.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.koriit.positioner.android.localization.PoseAlgorithm
import com.koriit.positioner.android.lidar.LineAlgorithm
import com.koriit.positioner.android.ui.SliderWithActions
import com.koriit.positioner.android.viewmodel.LidarViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import com.koriit.positioner.android.gyro.GyroscopeReader

/**
 * Panel displaying configurable LiDAR options.
 *
 * @param vm provides setting state and actions.
 * @param modifier modifier for the panel container.
 * @param scrollable wraps content in a [verticalScroll] when true. Disable when a parent already
 * provides scrolling to avoid nested scroll exceptions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    vm: LidarViewModel,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val autoScale by vm.autoScale.collectAsState()
    val showLogs by vm.showLogs.collectAsState()
    val showMeasurements by vm.showMeasurements.collectAsState()
    val showLines by vm.showLines.collectAsState()
    val filterPoseInput by vm.filterPoseInput.collectAsState()
    val confidence by vm.confidenceThreshold.collectAsState()
    val gradientMin by vm.gradientMin.collectAsState()
    val minDistance by vm.minDistance.collectAsState()
    val isolationFilterEnabled by vm.isolationFilterEnabled.collectAsState()
    val isolationDistance by vm.isolationDistance.collectAsState()
    val isolationMinNeighbours by vm.isolationMinNeighbours.collectAsState()
    val detectLines by vm.detectLines.collectAsState()
    val lineDistanceThreshold by vm.lineDistanceThreshold.collectAsState()
    val lineMinPoints by vm.lineMinPoints.collectAsState()
    val lineAngleTolerance by vm.lineAngleTolerance.collectAsState()
    val lineGapTolerance by vm.lineGapTolerance.collectAsState()
    val lineMergeEnabled by vm.lineMergeEnabled.collectAsState()
    val lineFilterEnabled by vm.lineFilterEnabled.collectAsState()
    val lineFilterLengthPercentile by vm.lineFilterLengthPercentile.collectAsState()
    val lineFilterLengthFactor by vm.lineFilterLengthFactor.collectAsState()
    val lineFilterLengthMin by vm.lineFilterLengthMin.collectAsState()
    val lineFilterLengthMax by vm.lineFilterLengthMax.collectAsState()
    val lineFilterInlierPercentile by vm.lineFilterInlierPercentile.collectAsState()
    val lineFilterInlierFactor by vm.lineFilterInlierFactor.collectAsState()
    val lineFilterInlierMin by vm.lineFilterInlierMin.collectAsState()
    val lineFilterInlierMax by vm.lineFilterInlierMax.collectAsState()
    val lineAlgorithm by vm.lineAlgorithm.collectAsState()
    val showGrid by vm.showOccupancyGrid.collectAsState()
    val gyroscopeRate by vm.gyroscopeRate.collectAsState()
    val gyroscopeRotationEnabled by vm.gyroscopeRotationEnabled.collectAsState()
    val gridCellSize by vm.gridCellSize.collectAsState()
    val useLastPose by vm.useLastPose.collectAsState()
    val poseAlgorithm by vm.poseAlgorithm.collectAsState()
    val occupancyOrientationStep by vm.occupancyOrientationStep.collectAsState()
    val occupancyOrientationSpan by vm.occupancyOrientationSpan.collectAsState()
    val occupancyScaleMin by vm.occupancyScaleMin.collectAsState()
    val occupancyScaleMax by vm.occupancyScaleMax.collectAsState()
    val occupancyScaleStep by vm.occupancyScaleStep.collectAsState()
    val particleCount by vm.particleCount.collectAsState()
    val particleIterations by vm.particleIterations.collectAsState()

    val containerModifier = if (scrollable) {
        modifier.verticalScroll(rememberScrollState())
    } else {
        modifier
    }

    Column(modifier = containerModifier) {
        Text("Display", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoScale, onCheckedChange = { vm.autoScale.value = it })
            Text("Auto scale")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showLogs, onCheckedChange = { vm.showLogs.value = it })
            Text("Show logs")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showMeasurements, onCheckedChange = { vm.showMeasurements.value = it })
            Text("Show measurements")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showLines, onCheckedChange = { vm.showLines.value = it })
            Text("Show lines")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showGrid, onCheckedChange = { vm.showOccupancyGrid.value = it })
            Text("Show occupancy grid")
        }
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Gyroscope", style = MaterialTheme.typography.titleMedium)
        Text("Sampling rate: ${gyroscopeRate} Hz")
        SliderWithActions(
            value = gyroscopeRate.toFloat(),
            onValueChange = { vm.gyroscopeRate.value = it.toInt() },
            valueRange = GyroscopeReader.MIN_RATE_HZ.toFloat()..400f,
            onReset = { vm.resetGyroscopeRate() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = gyroscopeRotationEnabled,
                onCheckedChange = { vm.setGyroscopeRotationEnabled(it) }
            )
            Text("Rotate measurements")
        }
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Filtering", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = filterPoseInput, onCheckedChange = { vm.filterPoseInput.value = it })
            Text("Filter pose input")
        }
        Text("Confidence threshold: ${confidence.toInt()}")
        SliderWithActions(
            value = confidence,
            onValueChange = { vm.confidenceThreshold.value = it },
            valueRange = 0f..255f,
            onReset = { vm.resetConfidenceThreshold() }
        )
        Text("Color gradient min: ${gradientMin.toInt()}")
        SliderWithActions(
            value = gradientMin,
            onValueChange = { vm.gradientMin.value = it },
            valueRange = 0f..255f,
            onReset = { vm.resetGradientMin() }
        )
        Text("Min distance: ${"%.2f".format(minDistance)} m")
        SliderWithActions(
            value = minDistance,
            onValueChange = { vm.minDistance.value = it },
            valueRange = 0f..2f,
            onReset = { vm.resetMinDistance() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isolationFilterEnabled,
                onCheckedChange = { vm.isolationFilterEnabled.value = it }
            )
            Text("Remove isolated points")
        }
        val isolationAlpha = if (isolationFilterEnabled) 1f else 0.6f
        Text(
            "Isolation distance: ${"%.2f".format(isolationDistance)} m",
            modifier = Modifier.alpha(isolationAlpha)
        )
        SliderWithActions(
            value = isolationDistance,
            onValueChange = { vm.isolationDistance.value = it },
            valueRange = 0f..5f,
            onReset = { vm.resetIsolationDistance() },
            enabled = isolationFilterEnabled,
        )
        Text(
            "Isolation neighbours: $isolationMinNeighbours",
            modifier = Modifier.alpha(isolationAlpha)
        )
        SliderWithActions(
            value = isolationMinNeighbours.toFloat(),
            onValueChange = { vm.isolationMinNeighbours.value = it.toInt() },
            valueRange = 0f..10f,
            onReset = { vm.resetIsolationMinNeighbours() },
            enabled = isolationFilterEnabled,
        )
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Line detection", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = detectLines, onCheckedChange = { vm.detectLines.value = it })
            Text("Detect lines")
        }
        var lineAlgoExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = lineAlgoExpanded, onExpandedChange = { lineAlgoExpanded = !lineAlgoExpanded }) {
            TextField(
                value = lineAlgorithm.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Line algorithm") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lineAlgoExpanded) },
                modifier = Modifier.menuAnchor(),
            )
            DropdownMenu(expanded = lineAlgoExpanded, onDismissRequest = { lineAlgoExpanded = false }) {
                LineAlgorithm.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            vm.lineAlgorithm.value = option
                            lineAlgoExpanded = false
                        },
                    )
                }
            }
        }
        Text("Distance threshold: ${"%.2f".format(lineDistanceThreshold)} m")
        SliderWithActions(
            value = lineDistanceThreshold,
            onValueChange = { vm.lineDistanceThreshold.value = it },
            valueRange = 0f..0.1f,
            onReset = { vm.resetLineDistanceThreshold() }
        )
        Text("Min points: $lineMinPoints")
        SliderWithActions(
            value = lineMinPoints.toFloat(),
            onValueChange = { vm.lineMinPoints.value = it.toInt() },
            valueRange = 2f..20f,
            onReset = { vm.resetLineMinPoints() }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = lineMergeEnabled, onCheckedChange = { vm.lineMergeEnabled.value = it })
            Text("Merge lines")
        }
        if (lineMergeEnabled) {
            Text("Angle tolerance: ${lineAngleTolerance.toInt()}°")
            SliderWithActions(
                value = lineAngleTolerance,
                onValueChange = { vm.lineAngleTolerance.value = it },
                valueRange = 0f..30f,
                onReset = { vm.resetLineAngleTolerance() }
            )
        }
        if (lineAlgorithm == LineAlgorithm.CLUSTER) {
            Text("Gap tolerance: ${lineGapTolerance.toInt()}°")
            SliderWithActions(
                value = lineGapTolerance,
                onValueChange = { vm.lineGapTolerance.value = it },
                valueRange = 0f..10f,
                onReset = { vm.resetLineGapTolerance() }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = lineFilterEnabled, onCheckedChange = { vm.lineFilterEnabled.value = it })
            Text("Adaptive line filter")
        }
        if (lineFilterEnabled) {
            Text("Length percentile: ${lineFilterLengthPercentile.toInt()}%")
            SliderWithActions(
                value = lineFilterLengthPercentile,
                onValueChange = { vm.lineFilterLengthPercentile.value = it },
                valueRange = 0f..100f,
                onReset = { vm.resetLineFilterLengthPercentile() }
            )
            Text("Length factor: ${"%.2f".format(lineFilterLengthFactor)}")
            SliderWithActions(
                value = lineFilterLengthFactor,
                onValueChange = { vm.lineFilterLengthFactor.value = it },
                valueRange = 0f..1f,
                onReset = { vm.resetLineFilterLengthFactor() }
            )
            Text("Length clamp: ${"%.2f".format(lineFilterLengthMin)}-${"%.2f".format(lineFilterLengthMax)} m")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RangeSlider(
                    value = lineFilterLengthMin..lineFilterLengthMax,
                    onValueChange = {
                        vm.lineFilterLengthMin.value = it.start
                        vm.lineFilterLengthMax.value = it.endInclusive
                    },
                    valueRange = 0f..2f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.resetLineFilterLengthClamp() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                }
            }
            Text("Inliers percentile: ${lineFilterInlierPercentile.toInt()}%")
            SliderWithActions(
                value = lineFilterInlierPercentile,
                onValueChange = { vm.lineFilterInlierPercentile.value = it },
                valueRange = 0f..100f,
                onReset = { vm.resetLineFilterInlierPercentile() }
            )
            Text("Inliers factor: ${"%.2f".format(lineFilterInlierFactor)}")
            SliderWithActions(
                value = lineFilterInlierFactor,
                onValueChange = { vm.lineFilterInlierFactor.value = it },
                valueRange = 0f..1f,
                onReset = { vm.resetLineFilterInlierFactor() }
            )
            Text("Inliers clamp: $lineFilterInlierMin-$lineFilterInlierMax")
            Row(verticalAlignment = Alignment.CenterVertically) {
                RangeSlider(
                    value = lineFilterInlierMin.toFloat()..lineFilterInlierMax.toFloat(),
                    onValueChange = {
                        vm.lineFilterInlierMin.value = it.start.toInt()
                        vm.lineFilterInlierMax.value = it.endInclusive.toInt()
                    },
                    valueRange = 0f..30f,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { vm.resetLineFilterInlierClamp() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset")
                }
            }
        }
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        Text("Pose estimation", style = MaterialTheme.typography.titleMedium)
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
        Text("Grid cell size: ${"%.2f".format(gridCellSize)} m")
        SliderWithActions(
            value = gridCellSize,
            onValueChange = { vm.updateGridCellSize(it) },
            valueRange = 0.05f..0.5f,
            onReset = { vm.resetGridCellSize() }
        )

        when (poseAlgorithm) {
            PoseAlgorithm.OCCUPANCY -> {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Occupancy grid", style = MaterialTheme.typography.titleMedium)
                Text("Orientation span: $occupancyOrientationSpan°")
                SliderWithActions(
                    value = occupancyOrientationSpan.toFloat(),
                    onValueChange = { vm.occupancyOrientationSpan.value = it.toInt() },
                    valueRange = 0f..180f,
                    onReset = { vm.resetOccupancyOrientationSpan() }
                )
                Text("Orientation step: $occupancyOrientationStep°")
                SliderWithActions(
                    value = occupancyOrientationStep.toFloat(),
                    onValueChange = { vm.occupancyOrientationStep.value = it.toInt() },
                    valueRange = 1f..45f,
                    onReset = { vm.resetOccupancyOrientationStep() }
                )
                Text("Scale min: ${"%.2f".format(occupancyScaleMin)}")
                SliderWithActions(
                    value = occupancyScaleMin,
                    onValueChange = { vm.occupancyScaleMin.value = it },
                    valueRange = 0.5f..1f,
                    onReset = { vm.resetOccupancyScaleMin() }
                )
                Text("Scale max: ${"%.2f".format(occupancyScaleMax)}")
                SliderWithActions(
                    value = occupancyScaleMax,
                    onValueChange = { vm.occupancyScaleMax.value = it },
                    valueRange = 1f..1.5f,
                    onReset = { vm.resetOccupancyScaleMax() }
                )
                Text("Scale step: ${"%.2f".format(occupancyScaleStep)}")
                SliderWithActions(
                    value = occupancyScaleStep,
                    onValueChange = { vm.occupancyScaleStep.value = it },
                    valueRange = 0.01f..0.2f,
                    onReset = { vm.resetOccupancyScaleStep() }
                )
            }
            PoseAlgorithm.PARTICLE -> {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Particle filter", style = MaterialTheme.typography.titleMedium)
                Text("Particles: $particleCount")
                SliderWithActions(
                    value = particleCount.toFloat(),
                    onValueChange = { vm.particleCount.value = it.toInt() },
                    valueRange = 100f..1000f,
                    onReset = { vm.resetParticleCount() }
                )
                Text("Iterations: $particleIterations")
                SliderWithActions(
                    value = particleIterations.toFloat(),
                    onValueChange = { vm.particleIterations.value = it.toInt() },
                    valueRange = 1f..20f,
                    onReset = { vm.resetParticleIterations() }
                )
            }
        }
    }
}
