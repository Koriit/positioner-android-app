package com.koriit.positioner.android.localization

/**
 * Simple exponential moving average filter for position updates.
 */
object PositionFilter {
    private var smoothed: Pair<Float, Float>? = null

    fun update(newPos: Pair<Float, Float>, alpha: Float = 0.2f): Pair<Float, Float> {
        val current = smoothed ?: newPos
        val x = current.first * (1 - alpha) + newPos.first * alpha
        val y = current.second * (1 - alpha) + newPos.second * alpha
        smoothed = x to y
        return smoothed!!
    }

    fun reset() { smoothed = null }
}
