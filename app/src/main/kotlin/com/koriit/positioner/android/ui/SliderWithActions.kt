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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = { onValueChange(valueRange.start) }, enabled = enabled) {
            Icon(Icons.Filled.FirstPage, contentDescription = "Min")
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onReset, enabled = enabled) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset")
        }
        IconButton(onClick = { onValueChange(valueRange.endInclusive) }, enabled = enabled) {
            Icon(Icons.Filled.LastPage, contentDescription = "Max")
        }
    }
}
