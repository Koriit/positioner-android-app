package com.koriit.positioner.android.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SliderWithActions(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = { onValueChange(valueRange.start) }) {
            Icon(Icons.Filled.FirstPage, contentDescription = "Min")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset")
        }
        IconButton(onClick = { onValueChange(valueRange.endInclusive) }) {
            Icon(Icons.Filled.LastPage, contentDescription = "Max")
        }
    }
}
