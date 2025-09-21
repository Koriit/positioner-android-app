package com.koriit.positioner.android.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koriit.positioner.android.lidar.LidarMeasurement
import com.koriit.positioner.android.gyro.GyroscopeMeasurement
import com.koriit.positioner.android.gyro.GyroscopeOrientationTracker
import com.koriit.positioner.android.gyro.GyroscopeReader
import com.koriit.positioner.android.lidar.LidarReader
import com.koriit.positioner.android.lidar.LidarRotationBatch
import com.koriit.positioner.android.lidar.MeasurementFilter
import com.koriit.positioner.android.lidar.GeoJsonParser
import com.koriit.positioner.android.lidar.LineDetector
import com.koriit.positioner.android.lidar.LineAlgorithm
import com.koriit.positioner.android.recording.Rotation
import com.koriit.positioner.android.recording.SessionReader
import com.koriit.positioner.android.recording.SessionWriter
import com.koriit.positioner.android.localization.OccupancyGrid
import com.koriit.positioner.android.localization.OccupancyPoseEstimator
import com.koriit.positioner.android.localization.ParticlePoseEstimator
import com.koriit.positioner.android.localization.PoseAlgorithm
import com.koriit.positioner.android.localization.PositionFilter
import com.koriit.positioner.android.logging.AppLog
import com.google.firebase.ktx.Firebase
import com.google.firebase.crashlytics.ktx.crashlytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

class LidarViewModel(private val context: Context) : ViewModel() {
    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD = 210f
        const val DEFAULT_GRADIENT_MIN = 180f
        const val DEFAULT_MIN_DISTANCE = 0.5f
        const val DEFAULT_ISOLATION_DISTANCE = 0.75f
        const val DEFAULT_ISOLATION_FILTER_ENABLED = true
        const val DEFAULT_MIN_NEIGHBOURS = 2
        const val DEFAULT_LINE_DISTANCE_THRESHOLD = 0.02f
        const val DEFAULT_LINE_MIN_POINTS = 5
        const val DEFAULT_LINE_ANGLE_TOLERANCE = 5f
        const val DEFAULT_LINE_GAP_TOLERANCE = 3f
        const val DEFAULT_LINE_MERGE_ENABLED = true
        const val DEFAULT_LINE_FILTER_ENABLED = false
        const val DEFAULT_LINE_FILTER_LENGTH_PERCENTILE = 75f
        const val DEFAULT_LINE_FILTER_LENGTH_FACTOR = 0.5f
        const val DEFAULT_LINE_FILTER_LENGTH_MIN = 0.2f
        const val DEFAULT_LINE_FILTER_LENGTH_MAX = 1f
        const val DEFAULT_LINE_FILTER_INLIER_PERCENTILE = 75f
        const val DEFAULT_LINE_FILTER_INLIER_FACTOR = 0.5f
        const val DEFAULT_LINE_FILTER_INLIER_MIN = 3
        const val DEFAULT_LINE_FILTER_INLIER_MAX = 10
        const val DEFAULT_GRID_CELL_SIZE = 0.1f
        const val DEFAULT_OCCUPANCY_ORIENTATION_STEP = 2
        const val DEFAULT_OCCUPANCY_ORIENTATION_SPAN = 90
        const val DEFAULT_OCCUPANCY_SCALE_MIN = 0.8f
        const val DEFAULT_OCCUPANCY_SCALE_MAX = 1.2f
        const val DEFAULT_OCCUPANCY_SCALE_STEP = 0.025f
        const val DEFAULT_PARTICLE_COUNT = 200
        const val DEFAULT_PARTICLE_ITERATIONS = 5
        const val DEFAULT_SHOW_MEASUREMENTS = true
        const val DEFAULT_SHOW_LINES = true
        const val DEFAULT_GYROSCOPE_RATE = GyroscopeReader.DEFAULT_RATE_HZ
        const val DEFAULT_GYROSCOPE_ROTATION_ENABLED = false
    }

    val rotation = MutableStateFlow(0)
    val autoScale = MutableStateFlow(true)
    val showLogs = MutableStateFlow(false)
    val showMeasurements = MutableStateFlow(DEFAULT_SHOW_MEASUREMENTS)
    val showLines = MutableStateFlow(DEFAULT_SHOW_LINES)
    val filterPoseInput = MutableStateFlow(true)
    val recording = MutableStateFlow(false)
    val confidenceThreshold = MutableStateFlow(DEFAULT_CONFIDENCE_THRESHOLD)
    val gradientMin = MutableStateFlow(DEFAULT_GRADIENT_MIN)
    val minDistance = MutableStateFlow(DEFAULT_MIN_DISTANCE) // metres
    val isolationDistance = MutableStateFlow(DEFAULT_ISOLATION_DISTANCE) // metres
    val isolationMinNeighbours = MutableStateFlow(DEFAULT_MIN_NEIGHBOURS)
    val isolationFilterEnabled = MutableStateFlow(DEFAULT_ISOLATION_FILTER_ENABLED)
    val detectLines = MutableStateFlow(false)
    val lineDistanceThreshold = MutableStateFlow(DEFAULT_LINE_DISTANCE_THRESHOLD)
    val lineMinPoints = MutableStateFlow(DEFAULT_LINE_MIN_POINTS)
    val lineAngleTolerance = MutableStateFlow(DEFAULT_LINE_ANGLE_TOLERANCE)
    val lineGapTolerance = MutableStateFlow(DEFAULT_LINE_GAP_TOLERANCE)
    val lineMergeEnabled = MutableStateFlow(DEFAULT_LINE_MERGE_ENABLED)
    val lineFilterEnabled = MutableStateFlow(DEFAULT_LINE_FILTER_ENABLED)
    val lineFilterLengthPercentile = MutableStateFlow(DEFAULT_LINE_FILTER_LENGTH_PERCENTILE)
    val lineFilterLengthFactor = MutableStateFlow(DEFAULT_LINE_FILTER_LENGTH_FACTOR)
    val lineFilterLengthMin = MutableStateFlow(DEFAULT_LINE_FILTER_LENGTH_MIN)
    val lineFilterLengthMax = MutableStateFlow(DEFAULT_LINE_FILTER_LENGTH_MAX)
    val lineFilterInlierPercentile = MutableStateFlow(DEFAULT_LINE_FILTER_INLIER_PERCENTILE)
    val lineFilterInlierFactor = MutableStateFlow(DEFAULT_LINE_FILTER_INLIER_FACTOR)
    val lineFilterInlierMin = MutableStateFlow(DEFAULT_LINE_FILTER_INLIER_MIN)
    val lineFilterInlierMax = MutableStateFlow(DEFAULT_LINE_FILTER_INLIER_MAX)
    val lineAlgorithm = MutableStateFlow(LineAlgorithm.CLUSTER)
    val lineLengthPx = MutableStateFlow(0f)
    val lineInlierPx = MutableStateFlow(0f)
    val showOccupancyGrid = MutableStateFlow(false)
    val gridCellSize = MutableStateFlow(DEFAULT_GRID_CELL_SIZE)
    val useLastPose = MutableStateFlow(true)
    val poseAlgorithm = MutableStateFlow(PoseAlgorithm.OCCUPANCY)
    val occupancyOrientationStep = MutableStateFlow(DEFAULT_OCCUPANCY_ORIENTATION_STEP)
    val occupancyOrientationSpan = MutableStateFlow(DEFAULT_OCCUPANCY_ORIENTATION_SPAN)
    val occupancyScaleMin = MutableStateFlow(DEFAULT_OCCUPANCY_SCALE_MIN)
    val occupancyScaleMax = MutableStateFlow(DEFAULT_OCCUPANCY_SCALE_MAX)
    val occupancyScaleStep = MutableStateFlow(DEFAULT_OCCUPANCY_SCALE_STEP)
    val particleCount = MutableStateFlow(DEFAULT_PARTICLE_COUNT)
    val particleIterations = MutableStateFlow(DEFAULT_PARTICLE_ITERATIONS)
    val usbConnected = MutableStateFlow(false)
    val measurementsPerSecond = MutableStateFlow(0)
    val rotationsPerSecond = MutableStateFlow(0f)
    val filteredMeasurements = MutableStateFlow(0)
    val filteredPercentage = MutableStateFlow(0f)
    val corruptedPackets = MutableStateFlow(0)
    /** Average number of pose combinations evaluated per second */
    val poseCombinationsPerSecond = MutableStateFlow(0f)
    /** Time in milliseconds to compute last pose estimate */
    val poseEstimateMs = MutableStateFlow(0L)
    /** Score of the most recent pose estimate */
    val poseScore = MutableStateFlow(0)
    /** Average score of the last 50 pose estimates */
    val poseScoreAverage = MutableStateFlow(0f)

    val replayMode = MutableStateFlow(false)
    val playing = MutableStateFlow(false)
    val replaySpeed = MutableStateFlow(1f)
    val replayPositionMs = MutableStateFlow(0L)
    val replayDurationMs = MutableStateFlow(0L)

    val loadingReplay = MutableStateFlow(false)

    private var replayRotations: List<Rotation> = emptyList()
    private var replayRotationStarts: List<Long> = emptyList()
    private var readJob: Job? = null

    private var corruptedPacketTotal = 0
    private var replayCorruptedTotals = IntArray(0)

    private var sessionWriter: SessionWriter? = null
    private val _measurements = MutableStateFlow<List<LidarMeasurement>>(emptyList())
    val measurements: StateFlow<List<LidarMeasurement>> = _measurements
    val lineFeatures = MutableStateFlow<List<LineDetector.LineFeature>>(emptyList())
    private var lastRotation: Rotation? = null
    val floorPlan = MutableStateFlow<List<List<Pair<Float, Float>>>>(emptyList())

    val gyroscopeRate = MutableStateFlow(DEFAULT_GYROSCOPE_RATE)
    enum class GyroscopeState { OK, NO_SENSOR, NO_PERMISSION, DISABLED }
    val gyroscopeState = MutableStateFlow(GyroscopeState.NO_SENSOR)
    val gyroscopeRotationEnabled = MutableStateFlow(DEFAULT_GYROSCOPE_ROTATION_ENABLED)
    val gyroscopeRotation = MutableStateFlow(0f)
    private val gyroscopeBuffer = mutableListOf<GyroscopeMeasurement>()
    private var gyroJob: Job? = null
    private val gyroscopeOrientation = GyroscopeOrientationTracker()

    val measurementOrientation = MutableStateFlow(0f)
    val planScale = MutableStateFlow(1f)
    val userPosition = MutableStateFlow(0f to 0f)

    private var occupancyGrid: OccupancyGrid? = null
    val occupancyGridState = MutableStateFlow<OccupancyGrid?>(null)
    private var lastEstimate: OccupancyPoseEstimator.Estimate? = null
    private val lastPoseScores = ArrayDeque<Int>()

    init {
        startGyroscope()
        viewModelScope.launch { gyroscopeRate.collect { startGyroscope() } }
        startLiveReading()
        // When replay is paused and settings change reapply the last buffer so
        // the user immediately sees the effect of the new configuration.
        viewModelScope.launch {
            merge(
                confidenceThreshold.map { },
                minDistance.map { },
                isolationFilterEnabled.map { },
                isolationDistance.map { },
                isolationMinNeighbours.map { },
                detectLines.map { },
                lineDistanceThreshold.map { },
                lineMinPoints.map { },
                lineAngleTolerance.map { },
                lineGapTolerance.map { },
                lineMergeEnabled.map { },
                lineAlgorithm.map { },
                filterPoseInput.map { },
                gridCellSize.map { },
                useLastPose.map { },
                poseAlgorithm.map { },
                occupancyOrientationSpan.map { },
                occupancyOrientationStep.map { },
                occupancyScaleMin.map { },
                occupancyScaleMax.map { },
                occupancyScaleStep.map { },
                particleCount.map { },
                particleIterations.map { },
            ).collect { reapplyCurrentRotation() }
        }
    }

    /**
     * Rotate the LiDAR plot by 90° increments when no floor plan is loaded.
     * Once a floor plan is present orientation is determined automatically so
     * manual rotation is ignored.
     */
    fun rotate90() {
        if (floorPlan.value.isNotEmpty()) return
        rotation.value += 90
    }
    fun startRecording(uri: Uri, context: Context) {
        if (replayMode.value || recording.value) return
        sessionWriter = SessionWriter.open(context, uri)
        recording.value = sessionWriter != null
    }

    fun stopRecording() {
        sessionWriter?.close()
        sessionWriter = null
        recording.value = false
    }

    fun resetGradientMin() { gradientMin.value = DEFAULT_GRADIENT_MIN }
    fun resetConfidenceThreshold() { confidenceThreshold.value = DEFAULT_CONFIDENCE_THRESHOLD }
    fun resetMinDistance() { minDistance.value = DEFAULT_MIN_DISTANCE }
    fun resetIsolationDistance() { isolationDistance.value = DEFAULT_ISOLATION_DISTANCE }
    fun resetIsolationMinNeighbours() { isolationMinNeighbours.value = DEFAULT_MIN_NEIGHBOURS }
    fun resetLineDistanceThreshold() { lineDistanceThreshold.value = DEFAULT_LINE_DISTANCE_THRESHOLD }
    fun resetLineMinPoints() { lineMinPoints.value = DEFAULT_LINE_MIN_POINTS }
    fun resetLineAngleTolerance() { lineAngleTolerance.value = DEFAULT_LINE_ANGLE_TOLERANCE }
    fun resetLineGapTolerance() { lineGapTolerance.value = DEFAULT_LINE_GAP_TOLERANCE }
    fun resetLineFilterLengthPercentile() { lineFilterLengthPercentile.value = DEFAULT_LINE_FILTER_LENGTH_PERCENTILE }
    fun resetLineFilterLengthFactor() { lineFilterLengthFactor.value = DEFAULT_LINE_FILTER_LENGTH_FACTOR }
    fun resetLineFilterLengthClamp() {
        lineFilterLengthMin.value = DEFAULT_LINE_FILTER_LENGTH_MIN
        lineFilterLengthMax.value = DEFAULT_LINE_FILTER_LENGTH_MAX
    }
    fun resetLineFilterInlierPercentile() { lineFilterInlierPercentile.value = DEFAULT_LINE_FILTER_INLIER_PERCENTILE }
    fun resetLineFilterInlierFactor() { lineFilterInlierFactor.value = DEFAULT_LINE_FILTER_INLIER_FACTOR }
    fun resetLineFilterInlierClamp() {
        lineFilterInlierMin.value = DEFAULT_LINE_FILTER_INLIER_MIN
        lineFilterInlierMax.value = DEFAULT_LINE_FILTER_INLIER_MAX
    }
    fun resetGridCellSize() { gridCellSize.value = DEFAULT_GRID_CELL_SIZE; rebuildGrid() }
    fun resetOccupancyOrientationSpan() { occupancyOrientationSpan.value = DEFAULT_OCCUPANCY_ORIENTATION_SPAN }
    fun resetOccupancyOrientationStep() { occupancyOrientationStep.value = DEFAULT_OCCUPANCY_ORIENTATION_STEP }
    fun resetOccupancyScaleMin() { occupancyScaleMin.value = DEFAULT_OCCUPANCY_SCALE_MIN }
    fun resetOccupancyScaleMax() { occupancyScaleMax.value = DEFAULT_OCCUPANCY_SCALE_MAX }
    fun resetOccupancyScaleStep() { occupancyScaleStep.value = DEFAULT_OCCUPANCY_SCALE_STEP }
    fun resetParticleCount() { particleCount.value = DEFAULT_PARTICLE_COUNT }
    fun resetParticleIterations() { particleIterations.value = DEFAULT_PARTICLE_ITERATIONS }
    fun resetGyroscopeRate() { gyroscopeRate.value = DEFAULT_GYROSCOPE_RATE }

    fun setGyroscopeRotationEnabled(enabled: Boolean) {
        if (gyroscopeRotationEnabled.value == enabled) return
        gyroscopeRotationEnabled.value = enabled
        viewModelScope.launch { reapplyCurrentRotation() }
    }

    fun updateGridCellSize(size: Float) {
        gridCellSize.value = size
        rebuildGrid()
    }

    private fun rebuildGrid() {
        val plan = floorPlan.value.firstOrNull() ?: return
        occupancyGrid = OccupancyGrid.fromPolygon(plan, gridCellSize.value)
        occupancyGridState.value = occupancyGrid
    }

    /**
     * Export current settings to the given [uri] as a JSON file.
     */
    fun exportSettings(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = LidarSettings(
                autoScale = autoScale.value,
                showLogs = showLogs.value,
                showMeasurements = showMeasurements.value,
                showLines = showLines.value,
                filterPoseInput = filterPoseInput.value,
                confidenceThreshold = confidenceThreshold.value,
                gradientMin = gradientMin.value,
                minDistance = minDistance.value,
                isolationFilterEnabled = isolationFilterEnabled.value,
                isolationDistance = isolationDistance.value,
                isolationMinNeighbours = isolationMinNeighbours.value,
                detectLines = detectLines.value,
                lineDistanceThreshold = lineDistanceThreshold.value,
                lineMinPoints = lineMinPoints.value,
                lineAngleTolerance = lineAngleTolerance.value,
                lineGapTolerance = lineGapTolerance.value,
                lineMergeEnabled = lineMergeEnabled.value,
                lineFilterEnabled = lineFilterEnabled.value,
                lineFilterLengthPercentile = lineFilterLengthPercentile.value,
                lineFilterLengthFactor = lineFilterLengthFactor.value,
                lineFilterLengthMin = lineFilterLengthMin.value,
                lineFilterLengthMax = lineFilterLengthMax.value,
                lineFilterInlierPercentile = lineFilterInlierPercentile.value,
                lineFilterInlierFactor = lineFilterInlierFactor.value,
                lineFilterInlierMin = lineFilterInlierMin.value,
                lineFilterInlierMax = lineFilterInlierMax.value,
                lineAlgorithm = lineAlgorithm.value,
                showOccupancyGrid = showOccupancyGrid.value,
                gridCellSize = gridCellSize.value,
                useLastPose = useLastPose.value,
                poseAlgorithm = poseAlgorithm.value,
                occupancyOrientationSpan = occupancyOrientationSpan.value,
                occupancyOrientationStep = occupancyOrientationStep.value,
                occupancyScaleMin = occupancyScaleMin.value,
                occupancyScaleMax = occupancyScaleMax.value,
                occupancyScaleStep = occupancyScaleStep.value,
                particleCount = particleCount.value,
                particleIterations = particleIterations.value,
                gyroscopeRate = gyroscopeRate.value,
                gyroscopeRotationEnabled = gyroscopeRotationEnabled.value,
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val json = Json.encodeToString(settings)
                out.write(json.toByteArray())
            }
        }
    }

    /**
     * Import settings from the JSON file at the given [uri].
     */
    fun importSettings(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = context.contentResolver.openInputStream(uri)?.use { input ->
                Json.decodeFromStream<LidarSettings>(input)
            }
            settings?.let {
                withContext(Dispatchers.Main) {
                    autoScale.value = it.autoScale
                    showLogs.value = it.showLogs
                    showMeasurements.value = it.showMeasurements
                    showLines.value = it.showLines
                    filterPoseInput.value = it.filterPoseInput
                    confidenceThreshold.value = it.confidenceThreshold
                    gradientMin.value = it.gradientMin
                    minDistance.value = it.minDistance
                    isolationFilterEnabled.value = it.isolationFilterEnabled
                    isolationDistance.value = it.isolationDistance
                    isolationMinNeighbours.value = it.isolationMinNeighbours
                    detectLines.value = it.detectLines
                    lineDistanceThreshold.value = it.lineDistanceThreshold
                    lineMinPoints.value = it.lineMinPoints
                    lineAngleTolerance.value = it.lineAngleTolerance
                    lineGapTolerance.value = it.lineGapTolerance
                    lineFilterEnabled.value = it.lineFilterEnabled
                    lineFilterLengthPercentile.value = it.lineFilterLengthPercentile
                    lineFilterLengthFactor.value = it.lineFilterLengthFactor
                    lineFilterLengthMin.value = it.lineFilterLengthMin
                    lineFilterLengthMax.value = it.lineFilterLengthMax
                    lineFilterInlierPercentile.value = it.lineFilterInlierPercentile
                    lineFilterInlierFactor.value = it.lineFilterInlierFactor
                    lineFilterInlierMin.value = it.lineFilterInlierMin
                    lineFilterInlierMax.value = it.lineFilterInlierMax
                    lineAlgorithm.value = it.lineAlgorithm
                    lineMergeEnabled.value = it.lineMergeEnabled
                    showOccupancyGrid.value = it.showOccupancyGrid
                    gridCellSize.value = it.gridCellSize
                    useLastPose.value = it.useLastPose
                    poseAlgorithm.value = it.poseAlgorithm
                    occupancyOrientationSpan.value = it.occupancyOrientationSpan
                    occupancyOrientationStep.value = it.occupancyOrientationStep
                    occupancyScaleMin.value = it.occupancyScaleMin
                    occupancyScaleMax.value = it.occupancyScaleMax
                    occupancyScaleStep.value = it.occupancyScaleStep
                    particleCount.value = it.particleCount
                    particleIterations.value = it.particleIterations
                    gyroscopeRate.value = it.gyroscopeRate
                    setGyroscopeRotationEnabled(it.gyroscopeRotationEnabled)
                    rebuildGrid()
                }
            }
        }
    }

    private suspend fun updateTransform(measurements: List<LidarMeasurement>) {
        val grid = occupancyGrid ?: return
        val start = System.nanoTime()
        val result = when (poseAlgorithm.value) {
            PoseAlgorithm.OCCUPANCY -> OccupancyPoseEstimator.estimate(
                measurements,
                grid,
                orientationStep = occupancyOrientationStep.value,
                orientationSpan = occupancyOrientationSpan.value,
                scaleRange = occupancyScaleMin.value..occupancyScaleMax.value,
                scaleStep = occupancyScaleStep.value,
                initial = if (useLastPose.value) lastEstimate else null,
            )
            PoseAlgorithm.PARTICLE -> ParticlePoseEstimator.estimate(
                measurements,
                grid,
                particles = particleCount.value,
                iterations = particleIterations.value,
            )
        }
        val durationNs = System.nanoTime() - start
        poseEstimateMs.value = durationNs / 1_000_000
        poseCombinationsPerSecond.value =
            if (durationNs > 0) result.combinations * 1_000_000_000f / durationNs else 0f
        poseScore.value = result.score
        if (result.score >= 0) {
            lastPoseScores.addLast(result.score)
            if (lastPoseScores.size > 50) lastPoseScores.removeFirst()
            poseScoreAverage.value = lastPoseScores.average().toFloat()
        }
        result.estimate?.let { estimate ->
            measurementOrientation.value = estimate.orientation
            planScale.value = estimate.scale
            userPosition.value = PositionFilter.update(estimate.position)
            lastEstimate = estimate
        }
    }

    fun loadRecording(uri: Uri, context: Context) {
        if (recording.value || replayMode.value || loadingReplay.value) return
        loadingReplay.value = true
        readJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            val rotations = SessionReader.read(context, uri)
            withContext(Dispatchers.Main) {
                loadingReplay.value = false
                if (rotations.isEmpty()) {
                    replayCorruptedTotals = IntArray(0)
                    startLiveReading()
                } else {
                    val orientedRotations = rotationsWithOrientation(rotations)
                    val firstMs = orientedRotations.first().start.toEpochMilliseconds()
                    replayRotations = orientedRotations
                    replayRotationStarts = orientedRotations.map { it.start.toEpochMilliseconds() - firstMs }
                    replayCorruptedTotals = IntArray(orientedRotations.size)
                    var cumulativeCorrupted = 0
                    orientedRotations.forEachIndexed { index, rotation ->
                        cumulativeCorrupted += rotation.corruptedPackets
                        replayCorruptedTotals[index] = cumulativeCorrupted
                    }
                    val lastMs = rotations.last().measurements.last().timestamp.toEpochMilliseconds()
                    replayDurationMs.value = lastMs - firstMs
                    replayPositionMs.value = 0
                    replaySpeed.value = 1f
                    playing.value = true
                    replayMode.value = true
                    resetGyroscopeTracking()
                    clearGyroscopeBuffer()
                    startReplay()
                }
            }
        }
    }

    fun exitReplay() {
        replayMode.value = false
        playing.value = false
        replayRotations = emptyList()
        replayRotationStarts = emptyList()
        replayPositionMs.value = 0
        _measurements.value = emptyList()
        loadingReplay.value = false
        resetCorruptedPacketTotal()
        replayCorruptedTotals = IntArray(0)
        readJob?.cancel()
        resetGyroscopeTracking()
        clearGyroscopeBuffer()
        startLiveReading()
    }

    fun togglePlay() {
        val newState = !playing.value
        playing.value = newState
        if (newState && replayMode.value && (readJob == null || !readJob!!.isActive)) {
            startReplay()
        }
    }

    fun seekBy(ms: Long) { seekTo(replayPositionMs.value + ms) }

    fun changeSpeed(factor: Float) {
        replaySpeed.value = (replaySpeed.value * factor).coerceIn(0.25f, 4f)
        if (replayMode.value) startReplay()
    }

    fun stepRotation(direction: Int) {
        if (!replayMode.value || replayRotations.isEmpty()) return
        val currentIdx = findRotationIndex(replayPositionMs.value)
        val targetIdx = (currentIdx + direction).coerceIn(0, replayRotations.size - 1)
        replayPositionMs.value = replayRotationStarts[targetIdx]
        viewModelScope.launch {
            val rot = replayRotations[targetIdx]
            updateCorruptedPacketTotal(replayCorruptedTotalIncluding(targetIdx))
            processRotation(rot.measurements, rot.gyroscope, rot.gyroscopeOrientation, rot.corruptedPackets)
        }
    }

    /**
     * Seek to the requested timestamp within the loaded replay.
     */
    fun seekTo(ms: Long) {
        replayPositionMs.value = ms.coerceIn(0L, replayDurationMs.value)
        if (replayMode.value && playing.value && (readJob == null || !readJob!!.isActive)) {
            startReplay()
        }
    }

    /**
     * Load a GeoJSON floor plan and initialise pose estimation.
     * Any previous manual rotation is cleared so the plan dictates orientation.
     */
    fun loadFloorPlan(uris: List<Uri>, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val plans = GeoJsonParser.readFloorPlans(context, uris)
            withContext(Dispatchers.Main) {
                floorPlan.value = plans
                rotation.value = 0
                occupancyGrid = plans.firstOrNull()?.let { OccupancyGrid.fromPolygon(it, gridCellSize.value) }
                occupancyGridState.value = occupancyGrid
                lastEstimate = null
                PositionFilter.reset()
            }
        }
    }

    private suspend fun processRotation(
        raw: List<LidarMeasurement>,
        gyro: List<GyroscopeMeasurement> = emptyList(),
        orientation: Float? = null,
        corrupted: Int = 0,
    ) {
        val rotationOrientation = orientation ?: gyroscopeOrientation.currentOrientation()
        applyGyroscopeOrientation(rotationOrientation, gyro.lastOrNull()?.timestamp)
        lastRotation = Rotation(
            measurements = raw,
            start = Clock.System.now(),
            gyroscope = gyro,
            gyroscopeOrientation = rotationOrientation,
            corruptedPackets = corrupted,
        )
        val measurementsForPose = if (filterPoseInput.value) {
            val filtered = MeasurementFilter.apply(
                raw,
                confidenceThreshold.value.toInt(),
                minDistance.value,
                if (isolationFilterEnabled.value) isolationDistance.value else 0f,
                if (isolationFilterEnabled.value) isolationMinNeighbours.value else 0,
            )
            val removed = raw.size - filtered.size
            filteredMeasurements.value = removed
            filteredPercentage.value = if (raw.isNotEmpty()) removed * 100f / raw.size else 0f
            filtered
        } else {
            filteredMeasurements.value = 0
            filteredPercentage.value = 0f
            raw
        }
        _measurements.value = measurementsForPose
        val poseInput = if (detectLines.value) {
            val lines = LineDetector.detect(
                measurementsForPose,
                lineDistanceThreshold.value,
                lineMinPoints.value,
                lineAngleTolerance.value,
                lineGapTolerance.value,
                lineAlgorithm.value,
                lineMergeEnabled.value,
            )
            val (filteredLines, stats) = LineDetector.filterAdaptive(
                lines,
                LineDetector.AdaptiveFilterParams(
                    lineFilterEnabled.value,
                    lineFilterLengthPercentile.value.toDouble(),
                    lineFilterLengthFactor.value.toDouble(),
                    lineFilterLengthMin.value.toDouble(),
                    lineFilterLengthMax.value.toDouble(),
                    lineFilterInlierPercentile.value.toDouble(),
                    lineFilterInlierFactor.value.toDouble(),
                    lineFilterInlierMin.value.toDouble(),
                    lineFilterInlierMax.value.toDouble(),
                ),
            )
            lineLengthPx.value = stats.lengthPx.toFloat()
            lineInlierPx.value = stats.inliersPx.toFloat()
            lineFeatures.value = filteredLines
            LineDetector.asMeasurements(filteredLines)
        } else {
            lineFeatures.value = emptyList()
            lineLengthPx.value = 0f
            lineInlierPx.value = 0f
            measurementsForPose
        }
        updateTransform(poseInput)
    }

    private suspend fun reapplyCurrentRotation() {
        val rotation = lastRotation
        if (replayMode.value && !playing.value && rotation != null) {
            processRotation(
                rotation.measurements,
                rotation.gyroscope,
                rotation.gyroscopeOrientation,
                rotation.corruptedPackets,
            )
        }
    }

    private fun startGyroscope() {
        gyroJob?.cancel()
        gyroJob = viewModelScope.launch(Dispatchers.Default) {
            when (val result = GyroscopeReader.open(context, gyroscopeRate.value)) {
                is GyroscopeReader.OpenResult.NoSensor -> gyroscopeState.value = GyroscopeState.NO_SENSOR
                is GyroscopeReader.OpenResult.NoPermission -> gyroscopeState.value = GyroscopeState.NO_PERMISSION
                is GyroscopeReader.OpenResult.Success -> {
                    gyroscopeState.value = GyroscopeState.OK
                    result.reader.measurements()
                        .catch { e ->
                            gyroscopeState.value = if (e is SecurityException) GyroscopeState.NO_PERMISSION else GyroscopeState.DISABLED
                        }
                        .collect { m ->
                            synchronized(gyroscopeBuffer) { gyroscopeBuffer.add(m) }
                        }
                }
            }
        }
    }

    /**
     * Restart gyroscope readings, typically after permissions change.
     */
    fun refreshGyroscope() = startGyroscope()

    private fun drainGyroscope(): List<GyroscopeMeasurement> = synchronized(gyroscopeBuffer) {
        val copy = gyroscopeBuffer.toList()
        gyroscopeBuffer.clear()
        copy
    }

    private fun clearGyroscopeBuffer() = synchronized(gyroscopeBuffer) { gyroscopeBuffer.clear() }

    private fun updateOrientationFromGyroscope(samples: List<GyroscopeMeasurement>): Float {
        return gyroscopeOrientation.integrate(samples)
    }

    private fun applyGyroscopeOrientation(orientation: Float, lastTimestamp: Instant?) {
        gyroscopeOrientation.apply(orientation, lastTimestamp)
        gyroscopeRotation.value = gyroscopeOrientation.currentOrientation()
    }

    private fun resetGyroscopeTracking() {
        gyroscopeOrientation.reset()
        gyroscopeRotation.value = 0f
    }

    private fun rotationsWithOrientation(rotations: List<Rotation>): List<Rotation> {
        return GyroscopeOrientationTracker.withOrientation(rotations)
    }

    private fun resetCorruptedPacketTotal() {
        updateCorruptedPacketTotal(0)
    }

    private fun updateCorruptedPacketTotal(total: Int) {
        corruptedPacketTotal = total
        corruptedPackets.value = total
    }

    private fun addCorruptedPackets(count: Int) {
        if (count == 0) return
        updateCorruptedPacketTotal(corruptedPacketTotal + count)
    }

    private fun replayCorruptedTotalBefore(index: Int): Int {
        if (index <= 0 || replayCorruptedTotals.isEmpty()) return 0
        val clamped = (index - 1).coerceAtMost(replayCorruptedTotals.lastIndex)
        return replayCorruptedTotals[clamped]
    }

    private fun replayCorruptedTotalIncluding(index: Int): Int {
        if (replayCorruptedTotals.isEmpty()) return 0
        val clamped = index.coerceIn(0, replayCorruptedTotals.lastIndex)
        return replayCorruptedTotals[clamped]
    }

    private fun startLiveReading() {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.Default) {
            var lastRotationTime = System.currentTimeMillis()
            while (true) {
                val source = withContext(Dispatchers.IO) { LidarReader.openDefault(context) }
                if (source == null) {
                    usbConnected.value = false
                    _measurements.value = emptyList()
                    filteredMeasurements.value = 0
                    filteredPercentage.value = 0f
                    resetCorruptedPacketTotal()
                    delay(1000)
                    continue
                }
                usbConnected.value = true
                try {
                    source.rotations().collect { rotationBatch: LidarRotationBatch ->
                        val now = System.currentTimeMillis()
                        val duration = now - lastRotationTime
                        rotationsPerSecond.value = if (duration > 0) 1000f / duration else 0f
                        measurementsPerSecond.value = (rotationBatch.measurements.size * rotationsPerSecond.value).toInt()
                        addCorruptedPackets(rotationBatch.corruptedPackets)
                        lastRotationTime = now
                        val gyro = drainGyroscope()
                        val orientation = updateOrientationFromGyroscope(gyro)
                        val startTimestamp = rotationBatch.measurements.firstOrNull()?.timestamp ?: Clock.System.now()
                        val rotationRecord = Rotation(
                            measurements = rotationBatch.measurements,
                            startTimestamp,
                            gyro,
                            orientation,
                            corruptedPackets = rotationBatch.corruptedPackets,
                        )
                        if (recording.value) {
                            withContext(Dispatchers.IO) {
                                sessionWriter?.write(rotationRecord)
                            }
                        }
                        processRotation(
                            rotationRecord.measurements,
                            rotationRecord.gyroscope,
                            rotationRecord.gyroscopeOrientation,
                            rotationRecord.corruptedPackets,
                        )
                    }
                } catch (e: Exception) {
                    usbConnected.value = false
                    _measurements.value = emptyList()
                    filteredMeasurements.value = 0
                    filteredPercentage.value = 0f
                    resetCorruptedPacketTotal()
                    AppLog.d("LidarViewModel", "Measurement loop failed", e)
                    Firebase.crashlytics.recordException(e)
                    delay(1000)
                }
            }
        }
    }

    private fun findRotationIndex(ms: Long): Int {
        if (replayRotationStarts.isEmpty()) return 0
        val idx = replayRotationStarts.binarySearch(ms)
        return if (idx >= 0) idx else -idx - 1
    }

    private fun startReplay() {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.Default) {
            val firstMs = replayRotationStarts.firstOrNull() ?: 0L
            var index = findRotationIndex(replayPositionMs.value)
            updateCorruptedPacketTotal(replayCorruptedTotalBefore(index))
            var lastStart = if (index < replayRotationStarts.size) replayRotationStarts[index] else firstMs
            while (replayMode.value && index < replayRotations.size) {
                if (!playing.value) {
                    delay(50)
                    continue
                }
                val rotation = replayRotations[index]
                val startMs = replayRotationStarts[index]
                val nextStart = replayRotationStarts.getOrNull(index + 1)
                val duration = (nextStart ?: (startMs + 1)) - startMs
                rotationsPerSecond.value = if (duration > 0) 1000f / duration else 0f
                measurementsPerSecond.value = (rotation.measurements.size * rotationsPerSecond.value).toInt()
                replayPositionMs.value = startMs
                updateCorruptedPacketTotal(replayCorruptedTotalIncluding(index))
                processRotation(
                    rotation.measurements,
                    rotation.gyroscope,
                    rotation.gyroscopeOrientation,
                    rotation.corruptedPackets,
                )
                if (nextStart != null) {
                    val delayMs = ((nextStart - startMs) / replaySpeed.value).toLong()
                    delay(delayMs)
                }
                lastStart = startMs
                index++
            }
            withContext(Dispatchers.Main) {
                playing.value = false
                rotationsPerSecond.value = 0f
                measurementsPerSecond.value = 0
                filteredMeasurements.value = 0
                filteredPercentage.value = 0f
            }
        }
    }

}

class LidarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LidarViewModel(context.applicationContext) as T
    }
}
