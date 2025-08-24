package com.koriit.positioner.android.viewmodel

import com.koriit.positioner.android.localization.PoseAlgorithm
import kotlinx.serialization.Serializable

/**
 * Serializable container for user configurable settings.
 */
@Serializable
data class LidarSettings(
    val autoScale: Boolean = true,
    val showLogs: Boolean = false,
    val filterPoseInput: Boolean = true,
    val bufferSize: Int = LidarViewModel.DEFAULT_BUFFER_SIZE,
    val flushIntervalMs: Float = LidarViewModel.DEFAULT_FLUSH_INTERVAL_MS,
    /** Automatically match buffer size and flush interval to full rotations */
    val matchRotation: Boolean = false,
    val confidenceThreshold: Float = LidarViewModel.DEFAULT_CONFIDENCE_THRESHOLD,
    val gradientMin: Float = LidarViewModel.DEFAULT_GRADIENT_MIN,
    val minDistance: Float = LidarViewModel.DEFAULT_MIN_DISTANCE,
    val isolationDistance: Float = LidarViewModel.DEFAULT_ISOLATION_DISTANCE,
    val isolationMinNeighbours: Int = LidarViewModel.DEFAULT_MIN_NEIGHBOURS,
    val poseMissPenalty: Float = LidarViewModel.DEFAULT_POSE_MISS_PENALTY,
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
