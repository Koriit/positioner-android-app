package com.koriit.positioner.android.lidar

import kotlinx.serialization.Serializable

/**
 * Available line detection algorithms.
 */
@Serializable
enum class LineAlgorithm {
    /** Sequential clustering with linear regression. */
    CLUSTER,

    /** Random sample consensus based detection. */
    RANSAC;

    val displayName: String
        get() = when (this) {
            CLUSTER -> "Cluster"
            RANSAC -> "RANSAC"
        }
}
