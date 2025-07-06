package com.koriit.positioner.android.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koriit.positioner.android.lidar.FakeLidarReader
import com.koriit.positioner.android.lidar.LidarDataSource
import com.koriit.positioner.android.lidar.LidarMeasurement
import com.koriit.positioner.android.lidar.LidarReader
import com.koriit.positioner.android.logging.AppLog
import com.google.firebase.ktx.Firebase
import com.google.firebase.crashlytics.ktx.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LidarViewModel(private val context: Context) : ViewModel() {
    val flushIntervalMs = MutableStateFlow(100f)
    val rotation = MutableStateFlow(0)
    val autoScale = MutableStateFlow(true)
    val showLogs = MutableStateFlow(false)
    val bufferSize = MutableStateFlow(480)
    val recording = MutableStateFlow(false)
    val confidenceThreshold = MutableStateFlow(200f)

    private val sessionData = mutableListOf<LidarMeasurement>()
    private val _measurements = MutableStateFlow<List<LidarMeasurement>>(emptyList())
    val measurements: StateFlow<List<LidarMeasurement>> = _measurements

    init {
        startReading()
    }

    fun rotate90() { rotation.value += 90 }
    fun toggleRecording() { recording.value = !recording.value }
    fun clearSession() { sessionData.clear() }

    fun saveSession(uri: Uri, context: Context) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val json = Json.encodeToString(sessionData)
            sessionData.clear()
            out.write(json.toByteArray())
        }
    }

    private fun startReading() {
        viewModelScope.launch {
            val realReader = withContext(Dispatchers.IO) { LidarReader.openDefault(context) }
            val source: LidarDataSource = realReader ?: FakeLidarReader()
            val buffer = ArrayDeque<LidarMeasurement>()
            var lastFlush = System.currentTimeMillis()
            try {
                source.measurements().flowOn(Dispatchers.IO).collect { m ->
                    if (m.confidence >= confidenceThreshold.value.toInt()) {
                        if (buffer.size >= bufferSize.value) buffer.removeFirst()
                        buffer.addLast(m)
                        if (recording.value) sessionData.add(m)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= flushIntervalMs.value.toLong()) {
                        lastFlush = now
                        _measurements.value = buffer.toList()
                    }
                }
            } catch (e: Exception) {
                AppLog.d("LidarViewModel", "Measurement loop failed", e)
                Firebase.crashlytics.recordException(e)
            }
        }
    }
}

class LidarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LidarViewModel(context.applicationContext) as T
    }
}
