package com.koriit.positioner.android.localization

import kotlinx.serialization.Serializable

/**
 * Available pose estimation algorithms.
 */
@Serializable
enum class PoseAlgorithm {
    /** Brute-force search using an occupancy grid. */
    OCCUPANCY,

    /** Particle filter exploring pose space. */
    PARTICLE;

    val displayName: String
        get() = when (this) {
            OCCUPANCY -> "Occupancy grid"
            PARTICLE -> "Particle filter"
        }
}

