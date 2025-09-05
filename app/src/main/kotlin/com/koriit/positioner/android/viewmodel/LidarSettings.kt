package com.koriit.positioner.android.viewmodel

import com.koriit.positioner.android.localization.PoseAlgorithm
import com.koriit.positioner.android.lidar.LineAlgorithm
import kotlinx.serialization.Serializable

/**
 * Serializable container for user configurable settings.
 */
@Serializable
data class LidarSettings(
    val autoScale: Boolean = true,
    val showLogs: Boolean = false,
    val showMeasurements: Boolean = LidarViewModel.DEFAULT_SHOW_MEASUREMENTS,
    val showLines: Boolean = LidarViewModel.DEFAULT_SHOW_LINES,
    val filterPoseInput: Boolean = true,
    val bufferSize: Int = LidarViewModel.DEFAULT_BUFFER_SIZE,
    val flushIntervalMs: Float = LidarViewModel.DEFAULT_FLUSH_INTERVAL_MS,
    /** Automatically match buffer size and flush interval to full rotations */
    val matchRotation: Boolean = true,
    val confidenceThreshold: Float = LidarViewModel.DEFAULT_CONFIDENCE_THRESHOLD,
    val gradientMin: Float = LidarViewModel.DEFAULT_GRADIENT_MIN,
    val minDistance: Float = LidarViewModel.DEFAULT_MIN_DISTANCE,
    val isolationDistance: Float = LidarViewModel.DEFAULT_ISOLATION_DISTANCE,
    val isolationMinNeighbours: Int = LidarViewModel.DEFAULT_MIN_NEIGHBOURS,
    val detectLines: Boolean = false,
    val lineDistanceThreshold: Float = LidarViewModel.DEFAULT_LINE_DISTANCE_THRESHOLD,
    val lineMinPoints: Int = LidarViewModel.DEFAULT_LINE_MIN_POINTS,
    val lineAngleTolerance: Float = LidarViewModel.DEFAULT_LINE_ANGLE_TOLERANCE,
    val lineGapTolerance: Float = LidarViewModel.DEFAULT_LINE_GAP_TOLERANCE,
    val lineMergeEnabled: Boolean = LidarViewModel.DEFAULT_LINE_MERGE_ENABLED,
    val lineFilterEnabled: Boolean = LidarViewModel.DEFAULT_LINE_FILTER_ENABLED,
    val lineFilterLengthPercentile: Float = LidarViewModel.DEFAULT_LINE_FILTER_LENGTH_PERCENTILE,
    val lineFilterLengthFactor: Float = LidarViewModel.DEFAULT_LINE_FILTER_LENGTH_FACTOR,
    val lineFilterLengthMin: Float = LidarViewModel.DEFAULT_LINE_FILTER_LENGTH_MIN,
    val lineFilterLengthMax: Float = LidarViewModel.DEFAULT_LINE_FILTER_LENGTH_MAX,
    val lineFilterInlierPercentile: Float = LidarViewModel.DEFAULT_LINE_FILTER_INLIER_PERCENTILE,
    val lineFilterInlierFactor: Float = LidarViewModel.DEFAULT_LINE_FILTER_INLIER_FACTOR,
    val lineFilterInlierMin: Int = LidarViewModel.DEFAULT_LINE_FILTER_INLIER_MIN,
    val lineFilterInlierMax: Int = LidarViewModel.DEFAULT_LINE_FILTER_INLIER_MAX,
    val lineAlgorithm: LineAlgorithm = LineAlgorithm.CLUSTER,
    val showOccupancyGrid: Boolean = false,
    val gridCellSize: Float = LidarViewModel.DEFAULT_GRID_CELL_SIZE,
    val useLastPose: Boolean = true,
    val poseAlgorithm: PoseAlgorithm = PoseAlgorithm.OCCUPANCY,
    val occupancyOrientationSpan: Int = LidarViewModel.DEFAULT_OCCUPANCY_ORIENTATION_SPAN,
    val occupancyOrientationStep: Int = LidarViewModel.DEFAULT_OCCUPANCY_ORIENTATION_STEP,
    val occupancyScaleMin: Float = LidarViewModel.DEFAULT_OCCUPANCY_SCALE_MIN,
    val occupancyScaleMax: Float = LidarViewModel.DEFAULT_OCCUPANCY_SCALE_MAX,
    val occupancyScaleStep: Float = LidarViewModel.DEFAULT_OCCUPANCY_SCALE_STEP,
    val particleCount: Int = LidarViewModel.DEFAULT_PARTICLE_COUNT,
    val particleIterations: Int = LidarViewModel.DEFAULT_PARTICLE_ITERATIONS,
)
