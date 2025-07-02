package com.koriit.positioner.android.logging

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Simple scrolling list that shows log messages stored in [AppLog].
 */
@Composable
fun LogView(logs: List<String>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(logs) { entry ->
            Text(entry, style = MaterialTheme.typography.bodySmall)
        }
    }
}
