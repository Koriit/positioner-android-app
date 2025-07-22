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
import com.koriit.positioner.android.lidar.MeasurementFilter
import com.koriit.positioner.android.logging.AppLog
import com.google.firebase.ktx.Firebase
import com.google.firebase.crashlytics.ktx.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

class LidarViewModel(private val context: Context) : ViewModel() {
    companion object {
        const val DEFAULT_FLUSH_INTERVAL_MS = 50f
        const val DEFAULT_BUFFER_SIZE = 480
        const val DEFAULT_CONFIDENCE_THRESHOLD = 220f
        const val DEFAULT_GRADIENT_MIN = 200f
        const val DEFAULT_MIN_DISTANCE = 0.5f
        const val DEFAULT_ISOLATION_DISTANCE = 1f
    }

    val flushIntervalMs = MutableStateFlow(DEFAULT_FLUSH_INTERVAL_MS)
    val rotation = MutableStateFlow(0)
    val autoScale = MutableStateFlow(true)
    val showLogs = MutableStateFlow(false)
    val bufferSize = MutableStateFlow(DEFAULT_BUFFER_SIZE)
    val recording = MutableStateFlow(false)
    val confidenceThreshold = MutableStateFlow(DEFAULT_CONFIDENCE_THRESHOLD)
    val gradientMin = MutableStateFlow(DEFAULT_GRADIENT_MIN)
    val minDistance = MutableStateFlow(DEFAULT_MIN_DISTANCE) // metres
    val isolationDistance = MutableStateFlow(DEFAULT_ISOLATION_DISTANCE) // metres
    val usbConnected = MutableStateFlow(false)
    val measurementsPerSecond = MutableStateFlow(0)
    val rotationsPerSecond = MutableStateFlow(0f)

    val replayMode = MutableStateFlow(false)
    val playing = MutableStateFlow(false)
    val replaySpeed = MutableStateFlow(1f)
    val replayPositionMs = MutableStateFlow(0L)
    val replayDurationMs = MutableStateFlow(0L)

    val loadingReplay = MutableStateFlow(false)

    private var replayData: List<LidarMeasurement> = emptyList()
    private var readJob: Job? = null

    private val sessionData = mutableListOf<LidarMeasurement>()
    private val _measurements = MutableStateFlow<List<LidarMeasurement>>(emptyList())
    val measurements: StateFlow<List<LidarMeasurement>> = _measurements

    init {
        startLiveReading()
    }

    fun rotate90() { rotation.value += 90 }
    fun toggleRecording() {
        if (replayMode.value) return
        recording.value = !recording.value
    }
    fun clearSession() { sessionData.clear() }

    fun resetFlushInterval() { flushIntervalMs.value = DEFAULT_FLUSH_INTERVAL_MS }
    fun resetGradientMin() { gradientMin.value = DEFAULT_GRADIENT_MIN }
    fun resetConfidenceThreshold() { confidenceThreshold.value = DEFAULT_CONFIDENCE_THRESHOLD }
    fun resetMinDistance() { minDistance.value = DEFAULT_MIN_DISTANCE }
    fun resetIsolationDistance() { isolationDistance.value = DEFAULT_ISOLATION_DISTANCE }
    fun resetBufferSize() { bufferSize.value = DEFAULT_BUFFER_SIZE }

    fun loadRecording(uri: Uri, context: Context) {
        if (recording.value || replayMode.value || loadingReplay.value) return
        loadingReplay.value = true
        readJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val data = context.contentResolver.openInputStream(uri)?.use { input ->
                Json.decodeFromStream<List<LidarMeasurement>>(input)
            }
            withContext(Dispatchers.Main) {
                loadingReplay.value = false
                if (data.isNullOrEmpty()) {
                    startLiveReading()
                } else {
                    replayData = data
                    replayDurationMs.value =
                        replayData.last().timestamp.toEpochMilliseconds() -
                            replayData.first().timestamp.toEpochMilliseconds()
                    replayPositionMs.value = 0
                    replaySpeed.value = 1f
                    playing.value = true
                    replayMode.value = true
                    startReplay()
                }
            }
        }
    }

    fun exitReplay() {
        replayMode.value = false
        playing.value = false
        replayData = emptyList()
        replayPositionMs.value = 0
        _measurements.value = emptyList()
        loadingReplay.value = false
        readJob?.cancel()
        startLiveReading()
    }

    fun togglePlay() { playing.value = !playing.value }

    fun seekBy(ms: Long) { seekTo(replayPositionMs.value + ms) }

    fun changeSpeed(factor: Float) {
        replaySpeed.value = (replaySpeed.value * factor).coerceIn(0.25f, 4f)
        if (replayMode.value) startReplay()
    }

    /**
     * Seek to the requested timestamp within the loaded replay.
     *
     * Unlike the previous implementation this does not restart the replay
     * coroutine on every value change. The running replay loop observes
     * [replayPositionMs] and jumps to the requested frame when it changes so
     * dragging the UI slider results in immediate feedback.
     */
    fun seekTo(ms: Long) {
        replayPositionMs.value = ms.coerceIn(0L, replayDurationMs.value)
    }

    fun saveSession(uri: Uri, context: Context) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            val json = Json.encodeToString(sessionData)
            sessionData.clear()
            out.write(json.toByteArray())
        }
    }

    private fun startLiveReading() {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val buffer = ArrayDeque<LidarMeasurement>()
            var lastFlush = System.currentTimeMillis()
            var lastSecond = System.currentTimeMillis()
            var count = 0
            var angleAccum = 0f
            var lastAngle: Float? = null
            var currentBufferSize = bufferSize.value
            while (true) {
                val source = withContext(Dispatchers.IO) { LidarReader.openDefault(context) }
                if (source == null) {
                    usbConnected.value = false
                    _measurements.value = emptyList()
                    delay(1000)
                    continue
                }
                usbConnected.value = true
                try {
                    source.measurements().flowOn(Dispatchers.IO).collect { m ->
                        if (bufferSize.value != currentBufferSize) {
                            currentBufferSize = bufferSize.value
                            buffer.clear()
                        }
                        if (buffer.size >= currentBufferSize) buffer.removeFirst()
                        buffer.addLast(m)
                        if (recording.value) sessionData.add(m)
                        lastAngle?.let { prev ->
                            var diff = m.angle - prev
                            if (diff < 0f) diff += 360f
                            angleAccum += diff
                        }
                        lastAngle = m.angle
                        count++
                        val now = System.currentTimeMillis()
                        if (now - lastFlush >= flushIntervalMs.value.toLong()) {
                            lastFlush = now
                            _measurements.value = MeasurementFilter.apply(
                                buffer.toList(),
                                confidenceThreshold.value.toInt(),
                                minDistance.value,
                                isolationDistance.value,
                            )
                        }
                        if (now - lastSecond >= 1000) {
                            measurementsPerSecond.value = count
                            rotationsPerSecond.value = angleAccum / 360f
                            angleAccum = 0f
                            count = 0
                            lastSecond = now
                        }
                    }
                } catch (e: Exception) {
                    usbConnected.value = false
                    _measurements.value = emptyList()
                    AppLog.d("LidarViewModel", "Measurement loop failed", e)
                    Firebase.crashlytics.recordException(e)
                    delay(1000)
                }
            }
        }
    }

    private fun findIndexForPosition(ms: Long): Int {
        if (replayData.isEmpty()) return 0
        val start = replayData.first().timestamp.toEpochMilliseconds()
        val target = start + ms
        val idx = replayData.binarySearch { it.timestamp.toEpochMilliseconds().compareTo(target) }
        return if (idx >= 0) idx else -idx - 1
    }

    private fun startReplay() {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val buffer = ArrayDeque<LidarMeasurement>()
            var lastFlush = System.currentTimeMillis()
            var lastSecond = System.currentTimeMillis()
            var count = 0
            var angleAccum = 0f
            var lastAngle: Float? = null
            var currentBufferSize = bufferSize.value
            val firstMs = replayData.first().timestamp.toEpochMilliseconds()
            var index = findIndexForPosition(replayPositionMs.value)
            while (replayMode.value && index < replayData.size) {
                if (!playing.value) {
                    delay(50)
                    continue
                }
                val desired = findIndexForPosition(replayPositionMs.value)
                if (desired != index) {
                    index = desired
                    buffer.clear()
                    lastAngle = null
                }
                if (bufferSize.value != currentBufferSize) {
                    currentBufferSize = bufferSize.value
                    buffer.clear()
                }
                val m = replayData[index]
                if (buffer.size >= currentBufferSize) buffer.removeFirst()
                buffer.addLast(m)
                lastAngle?.let { prev ->
                    var diff = m.angle - prev
                    if (diff < 0f) diff += 360f
                    angleAccum += diff
                }
                lastAngle = m.angle
                count++
                val now = System.currentTimeMillis()
                if (now - lastFlush >= flushIntervalMs.value.toLong()) {
                    lastFlush = now
                    _measurements.value = MeasurementFilter.apply(
                        buffer.toList(),
                        confidenceThreshold.value.toInt(),
                        minDistance.value,
                        isolationDistance.value,
                    )
                }
                if (now - lastSecond >= 1000) {
                    measurementsPerSecond.value = count
                    rotationsPerSecond.value = angleAccum / 360f
                    angleAccum = 0f
                    count = 0
                    lastSecond = now
                }

                replayPositionMs.value = m.timestamp.toEpochMilliseconds() - firstMs
                index++
                if (index >= replayData.size) break
                val nextDiff = replayData[index].timestamp.toEpochMilliseconds() - m.timestamp.toEpochMilliseconds()
                val delayMs = (nextDiff / replaySpeed.value).toLong()
                delay(delayMs)
            }
        }
    }

}

class LidarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LidarViewModel(context.applicationContext) as T
    }
}
