package com.lws.springback.view

import android.view.animation.AnimationUtils
import kotlin.math.abs
import kotlin.math.sign

class SpringScroller {
    private var mCurrX = 0.0
    private var mCurrY = 0.0
    private var mCurrentTime: Long = 0
    private var mEndX = 0.0
    private var mEndY = 0.0
    var isFinished = true
        private set

    private var mFirstStep = 0
    private var mLastStep = false
    private var mOrientation = 0
    private var mOriginStartX = 0.0
    private var mOriginStartY = 0.0
    private var mOriginVelocity = 0.0
    private var mSpringOperator: SpringOperator? = null
    private var mStartTime: Long = 0
    private var mStartX = 0.0
    private var mStartY = 0.0
    private var mVelocity = 0.0

    fun scrollByFling(
        startX: Float,
        endX: Float,
        startY: Float,
        endY: Float,
        velocity: Float,
        orientation: Int,
        z: Boolean
    ) {
        isFinished = false
        mLastStep = false
        mStartX = startX.toDouble()
        mOriginStartX = mStartX
        mEndX = endX.toDouble()

        mStartY = startY.toDouble()
        mOriginStartY = mStartY
        mCurrY = mStartY
        mEndY = endY.toDouble()

        mOriginVelocity = velocity.toDouble()
        mVelocity = mOriginVelocity
        mSpringOperator = if (abs(mVelocity) <= 5000.0 || z) {
            SpringOperator(1.0f, 0.4f)
        } else {
            SpringOperator(1.0f, 0.55f)
        }
        mOrientation = orientation
        mStartTime = AnimationUtils.currentAnimationTimeMillis()
    }

    fun computeScrollOffset(): Boolean {
        if (mSpringOperator == null || isFinished) {
            return false
        }
        val firstStep = mFirstStep
        return if (firstStep != 0) {
            if (mOrientation == 1) {
                mCurrX = firstStep.toDouble()
                mStartX = mCurrX
            } else {
                mCurrY = firstStep.toDouble()
                mStartY = mCurrY
            }
            mFirstStep = 0
            true
        } else if (mLastStep) {
            isFinished = true
            true
        } else {
            mCurrentTime = AnimationUtils.currentAnimationTimeMillis()
            var min = ((mCurrentTime - mStartTime).toFloat() / 1000.0f).coerceAtMost(MAX_DELTA_TIME)
            if (min == 0.0f) {
                min = 0.016f
            }
            mStartTime = mCurrentTime
            if (mOrientation == 2) {
                val updateVelocity =
                    mSpringOperator!!.updateVelocity(mVelocity, min, mEndY, mStartY)
                mCurrY = mStartY + min.toDouble() * updateVelocity
                mVelocity = updateVelocity
                if (isAtEquilibrium(mCurrY, mOriginStartY, mEndY)) {
                    mLastStep = true
                    mCurrY = mEndY
                } else {
                    mStartY = mCurrY
                }
            } else {
                val updateVelocity2 =
                    mSpringOperator!!.updateVelocity(mVelocity, min, mEndX, mStartX)
                mCurrX = mStartX + min.toDouble() * updateVelocity2
                mVelocity = updateVelocity2
                if (isAtEquilibrium(mCurrX, mOriginStartX, mEndX)) {
                    mLastStep = true
                    mCurrX = mEndX
                } else {
                    mStartX = mCurrX
                }
            }
            true
        }
    }

    private fun isAtEquilibrium(d: Double, d2: Double, d3: Double): Boolean {
        if (d2 < d3 && d > d3) {
            return true
        }
        val i = d2.compareTo(d3)
        if (i > 0 && d < d3) {
            return true
        }
        return !((i != 0 || sign(mOriginVelocity) == sign(d)) && abs(d - d3) >= 1.0)
    }

    val currX: Int
        get() = mCurrX.toInt()
    val currY: Int
        get() = mCurrY.toInt()

    fun forceStop() {
        isFinished = true
        mFirstStep = 0
    }

    fun setFirstStep(firstStep: Int) {
        mFirstStep = firstStep
    }

    companion object {
        private const val MAX_DELTA_TIME = 0.016f
        private const val VALUE_THRESHOLD = 1.0f
    }
}