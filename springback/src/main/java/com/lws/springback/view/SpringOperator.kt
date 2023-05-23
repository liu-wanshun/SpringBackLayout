package com.lws.springback.view

import kotlin.math.pow

class SpringOperator(f: Float, f2: Float) {
    private val damping: Double
    private val tension: Double

    init {
        tension = (6.283185307179586 / f2).pow(2.0)
        damping = f * 12.566370614359172 / f2
    }

    fun updateVelocity(velocity: Double, min: Float, end: Double, start: Double): Double {
        return velocity * (1.0 - damping * min) + (tension * (end - start) * min).toFloat()
            .toDouble()
    }
}