package com.lws.springback.view

import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
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
        val i = mScrollOrientation
        if (i == 0 || i == mTargetScrollOrientation) {
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
        val iArr = intArrayOf(0, 0)
        mTarget.getLocationInWindow(iArr)
        val i = iArr[0]
        val i2 = iArr[1]
        return Rect(i, i2, mTarget.width + i, mTarget.height + i2).contains(x.toInt(), y.toInt())
    }

    /* access modifiers changed from: package-private */
    fun checkOrientation(motionEvent: MotionEvent) {
        var findPointerIndex: Int = -1
        val actionMasked = motionEvent.actionMasked
        if (actionMasked != 0) {
            var i = 1
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    val i2 = mActivePointerId
                    if (i2 != -1 && motionEvent.findPointerIndex(i2)
                            .also { findPointerIndex = it } >= 0
                    ) {
                        val y = motionEvent.getY(findPointerIndex)
                        val x = motionEvent.getX(findPointerIndex)
                        val f = y - mInitialDownY
                        val f2 = x - mInitialDownX
                        if (abs(f2) > mTouchSlop.toFloat() || abs(f) > mTouchSlop.toFloat()) {
                            if (abs(f2) <= abs(f)) {
                                i = 2
                            }
                            mScrollOrientation = i
                            return
                        }
                        return
                    }
                    return
                } else if (actionMasked != 3) {
                    return
                }
            }
            mScrollOrientation = 0
            mTarget.requestDisallowInterceptTouchEvent(false)
            return
        }
        mActivePointerId = motionEvent.getPointerId(0)
        val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
        if (findPointerIndex2 >= 0) {
            mInitialDownY = motionEvent.getY(findPointerIndex2)
            mInitialDownX = motionEvent.getX(findPointerIndex2)
            mScrollOrientation = 0
        }
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}