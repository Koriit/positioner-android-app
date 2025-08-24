package com.koriit.positioner.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.koriit.positioner.android.viewmodel.LidarViewModel
import com.koriit.positioner.android.ui.SliderWithActions

/**
 * Playback controls shown when a recording is loaded.
 *
 * The composable exposes seek buttons, a progress slider and speed
 * adjustments so users can inspect captured data.
 */

@Composable
fun ReplayControls(vm: LidarViewModel) {
    val pos by vm.replayPositionMs.collectAsState()
    val duration by vm.replayDurationMs.collectAsState()
    val playing by vm.playing.collectAsState()
    val speed by vm.replaySpeed.collectAsState()

    Column {
        SliderWithActions(
            value = pos.toFloat(),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            onReset = { vm.seekTo(0L) }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.stepBuffer(-1) }, enabled = !playing) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous buffer")
            }
            Button(onClick = { vm.togglePlay() }) { Text(if (playing) "Pause" else "Play") }
            IconButton(onClick = { vm.stepBuffer(1) }, enabled = !playing) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next buffer")
            }
            IconButton(onClick = { vm.changeSpeed(0.5f) }) {
                Icon(Icons.Filled.FastRewind, contentDescription = "Slower")
            }
            Text(String.format("%.1fx", speed))
            IconButton(onClick = { vm.changeSpeed(2f) }) {
                Icon(Icons.Filled.FastForward, contentDescription = "Faster")
            }
        }
        Text("${pos/1000}s / ${duration/1000}s")
    }
}
