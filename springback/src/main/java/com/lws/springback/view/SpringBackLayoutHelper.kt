package com.lws.springback.view

import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import kotlin.math.abs

class SpringBackLayoutHelper(private val mTarget: ViewGroup, var mTargetScrollOrientation: Int) {
    @JvmField
    var mActivePointerId = -1

    @JvmField
    var mInitialDownX = 0f

    @JvmField
    var mInitialDownY = 0f

    @JvmField
    var mScrollOrientation = 0

    private val mTouchSlop: Int = ViewConfiguration.get(mTarget.context).scaledTouchSlop

    fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        checkOrientation(motionEvent)
        if (mScrollOrientation == 0 || mScrollOrientation == mTargetScrollOrientation) {
            mTarget.requestDisallowInterceptTouchEvent(false)
            return true
        }
        mTarget.requestDisallowInterceptTouchEvent(true)
        return false
    }

    fun isTouchInTarget(motionEvent: MotionEvent): Boolean {
        val findPointerIndex = motionEvent.findPointerIndex(motionEvent.getPointerId(0))
        if (findPointerIndex < 0) {
            return false
        }
        val y = motionEvent.getY(findPointerIndex)
        val x = motionEvent.getX(findPointerIndex)
        val outLocation = intArrayOf(0, 0)
        mTarget.getLocationInWindow(outLocation)
        val left = outLocation[0]
        val top = outLocation[1]
        val targetRect = Rect(left, top, mTarget.width + left, mTarget.height + top)
        return targetRect.contains(x.toInt(), y.toInt())
    }

    fun checkOrientation(motionEvent: MotionEvent) {
        var findPointerIndex: Int = -1
        val actionMasked = motionEvent.actionMasked
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mActivePointerId = motionEvent.getPointerId(0)
            val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
            if (findPointerIndex2 >= 0) {
                mInitialDownY = motionEvent.getY(findPointerIndex2)
                mInitialDownX = motionEvent.getX(findPointerIndex2)
                mScrollOrientation = ViewCompat.SCROLL_AXIS_NONE
            }
        } else {
            var orientation = ViewCompat.SCROLL_AXIS_HORIZONTAL
            if (actionMasked == MotionEvent.ACTION_MOVE) {
                if (mActivePointerId != -1
                    && motionEvent.findPointerIndex(mActivePointerId)
                        .also { findPointerIndex = it } >= 0
                ) {
                    val y = motionEvent.getY(findPointerIndex)
                    val x = motionEvent.getX(findPointerIndex)
                    val deltaY = y - mInitialDownY
                    val deltaX = x - mInitialDownX
                    if (abs(deltaX) > mTouchSlop || abs(deltaY) > mTouchSlop) {
                        if (abs(deltaX) <= abs(deltaY)) {
                            orientation = ViewCompat.SCROLL_AXIS_VERTICAL
                        }
                        mScrollOrientation = orientation
                        return
                    }
                    return
                }
                return
            }
            if (actionMasked != MotionEvent.ACTION_UP && actionMasked != MotionEvent.ACTION_CANCEL) {
                return
            }
            mScrollOrientation = ViewCompat.SCROLL_AXIS_NONE
            mTarget.requestDisallowInterceptTouchEvent(false)
        }
    }
}