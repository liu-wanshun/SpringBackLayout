package com.lws.springback.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ListView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat.TYPE_TOUCH
import androidx.core.widget.ListViewCompat
import androidx.core.widget.NestedScrollView
import com.lws.springback.R
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import androidx.core.view.ViewCompat.SCROLL_AXIS_HORIZONTAL as HORIZONTAL
import androidx.core.view.ViewCompat.SCROLL_AXIS_NONE as UNCHECK_ORIENTATION
import androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL as VERTICAL

class SpringBackLayout @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null
) : ViewGroup(context, attributeSet), NestedScrollingParent3, NestedScrollingChild3,
    NestedCurrentFling {
    private var consumeNestFlingCounter: Int = 0
    private var mActivePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private val mHelper: SpringBackLayoutHelper
    private var mInitialDownX = 0f
    private var mInitialDownY = 0f
    private var mInitialMotionX = 0f
    private var mInitialMotionY = 0f
    private var mIsBeingDragged = false
    private var mNestedFlingInProgress = false
    private var mNestedScrollAxes = 0
    private var mNestedScrollInProgress = false
    private val mNestedScrollingChildHelper: NestedScrollingChildHelper =
        NestedScrollingChildHelper(this)
    private val mNestedScrollingParentHelper: NestedScrollingParentHelper =
        NestedScrollingParentHelper(this)
    private val mNestedScrollingV2ConsumedCompat: IntArray = IntArray(2)
    private val mOnScrollListeners: MutableList<OnScrollListener> = mutableListOf()
    private var mOnSpringListener: OnSpringListener? = null
    private var mOriginScrollOrientation: Int
    private val mParentOffsetInWindow: IntArray = IntArray(2)
    private val mParentScrollConsumed: IntArray = IntArray(2)
    private val mScreenHeight: Int
    private val mScreenWith: Int
    private var mScrollByFling = false
    private var mScrollOrientation = 0
    private var mScrollState: Int = STATE_IDLE
    private var springBackMode: Int
    private val mSpringScroller: SpringScroller = SpringScroller()
    private lateinit var mTarget: View
    private val mTargetId: Int
    private var mTotalFlingUnconsumed = 0f
    private var mTotalScrollBottomUnconsumed = 0f
    private var mTotalScrollTopUnconsumed = 0f
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var mVelocityX = 0f
    private var mVelocityY = 0f
    var springBackEnable: Boolean = true

    interface OnScrollListener {
        fun onScrolled(springBackLayout: SpringBackLayout?, dx: Int, dy: Int) {}
        fun onStateChanged(oldScrollState: Int, scrollState: Int, isFinished: Boolean) {}
    }

    interface OnSpringListener {
        fun onSpringBack(): Boolean
    }

    init {
        val obtainStyledAttributes =
            context.obtainStyledAttributes(attributeSet, R.styleable.SpringBackLayout)
        mTargetId =
            obtainStyledAttributes.getResourceId(R.styleable.SpringBackLayout_scrollableView, -1)
        mOriginScrollOrientation =
            obtainStyledAttributes.getInt(R.styleable.SpringBackLayout_scrollOrientation, 2)
        springBackMode =
            obtainStyledAttributes.getInt(R.styleable.SpringBackLayout_springBackMode, 3)
        obtainStyledAttributes.recycle()
        mHelper = SpringBackLayoutHelper(this, mOriginScrollOrientation)
        isNestedScrollingEnabled = true
        val displayMetrics = DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(
            displayMetrics
        )
        mScreenWith = displayMetrics.widthPixels
        mScreenHeight = displayMetrics.heightPixels
    }

    fun setScrollOrientation(orientation: Int) {
        mOriginScrollOrientation = orientation
        mHelper.mTargetScrollOrientation = orientation
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        val view = mTarget
        if (view is NestedScrollingChild3 && Build.VERSION.SDK_INT >= 21 && enabled != view.isNestedScrollingEnabled()) {
            view.setNestedScrollingEnabled(enabled)
        }
    }

    private fun supportTopSpringBackMode(): Boolean {
        return springBackMode and SPRING_BACK_TOP != 0
    }

    private fun supportBottomSpringBackMode(): Boolean {
        return springBackMode and SPRING_BACK_BOTTOM != 0
    }

    fun setTarget(view: View) {
        mTarget = view
        if (Build.VERSION.SDK_INT >= 21) {
            if (mTarget is NestedScrollingChild3 && !mTarget.isNestedScrollingEnabled) {
                mTarget.isNestedScrollingEnabled = true
            }
        }
    }

    private fun ensureTarget() {
        if (!this::mTarget.isInitialized) {
            val id = mTargetId
            if (id != -1) {
                mTarget = findViewById(id) ?: throw IllegalArgumentException("fail to get target")
            } else {
                throw IllegalArgumentException("invalid target Id")
            }
        }

        if (Build.VERSION.SDK_INT >= 21 && isEnabled) {
            if (mTarget is NestedScrollingChild3 && !mTarget.isNestedScrollingEnabled) {
                mTarget.isNestedScrollingEnabled = true
            }
        }
        mTarget.overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        mTarget.layout(
            paddingLeft,
            paddingTop,
            measuredWidth - getPaddingLeft() - paddingRight + paddingLeft,
            measuredHeight - getPaddingTop() - paddingBottom + paddingTop
        )
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ensureTarget()
        val modeWidth = MeasureSpec.getMode(widthMeasureSpec)
        val modeHeight = MeasureSpec.getMode(heightMeasureSpec)
        var sizeWidth = MeasureSpec.getSize(widthMeasureSpec)
        var sizeHeight = MeasureSpec.getSize(heightMeasureSpec)
        measureChild(mTarget, widthMeasureSpec, heightMeasureSpec)
        if (sizeWidth > mTarget.measuredWidth) {
            sizeWidth = mTarget.measuredWidth
        }
        if (sizeHeight > mTarget.measuredHeight) {
            sizeHeight = mTarget.measuredHeight
        }
        if (modeWidth != MeasureSpec.EXACTLY) {
            sizeWidth = mTarget.measuredWidth
        }
        if (modeHeight != MeasureSpec.EXACTLY) {
            sizeHeight = mTarget.measuredHeight
        }
        setMeasuredDimension(sizeWidth, sizeHeight)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (mSpringScroller.computeScrollOffset()) {
            scrollTo(mSpringScroller.currX, mSpringScroller.currY)
            if (!mSpringScroller.isFinished) {
                postInvalidateOnAnimation()
            } else {
                dispatchScrollState(STATE_IDLE)
            }
        }
    }

    override fun onScrollChanged(
        scrollX: Int,
        scrollY: Int,
        oldScrollX: Int,
        oldScrollY: Int
    ) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY)
        for (onScrolled in mOnScrollListeners) {
            onScrolled.onScrolled(this, scrollX - oldScrollX, scrollY - oldScrollY)
        }
    }

    private val isVerticalTargetScrollToTop: Boolean
        get() {
            val view = mTarget
            return if (view is ListView) {
                !ListViewCompat.canScrollList(view, -1)
            } else !view.canScrollVertically(-1)
        }

    private val isHorizontalTargetScrollToTop: Boolean
        get() = !mTarget.canScrollHorizontally(-1)

    private fun isTargetScrollOrientation(orientation: Int): Boolean {
        return mScrollOrientation == orientation
    }

    private fun isTargetScrollToTop(orientation: Int): Boolean {
        if (orientation != VERTICAL) {
            return !mTarget.canScrollHorizontally(-1)
        }
        val view = mTarget
        return if (view is ListView) {
            !ListViewCompat.canScrollList(view, -1)
        } else !view.canScrollVertically(-1)
    }

    private fun isTargetScrollToBottom(orientation: Int): Boolean {
        if (orientation != VERTICAL) {
            return !mTarget.canScrollHorizontally(HORIZONTAL)
        }
        val view = mTarget
        return if (view is ListView) {
            !ListViewCompat.canScrollList(view, 1)
        } else !view.canScrollVertically(1)
    }

    override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN
            && mScrollState == STATE_SETTLING
            && mHelper.isTouchInTarget(motionEvent)
        ) {
            dispatchScrollState(STATE_DRAGGING)
        }

        val dispatchTouchEvent = super.dispatchTouchEvent(motionEvent)

        if ((motionEvent.actionMasked == MotionEvent.ACTION_UP || motionEvent.actionMasked == MotionEvent.ACTION_CANCEL)
            && mScrollState == STATE_DRAGGING
        ) {
            if (mOriginScrollOrientation and VERTICAL != 0) {
                springBack(VERTICAL)
            }

            if (mOriginScrollOrientation and HORIZONTAL != 0) {
                springBack(HORIZONTAL)
            }
        }
        return dispatchTouchEvent
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        if (!springBackEnable
            || !isEnabled
            || mNestedFlingInProgress
            || mNestedScrollInProgress
            || (Build.VERSION.SDK_INT >= 21 && mTarget.isNestedScrollingEnabled)
        ) {
            return false
        }
        val actionMasked = motionEvent.actionMasked
        if (!mSpringScroller.isFinished && actionMasked == MotionEvent.ACTION_DOWN) {
            mSpringScroller.forceStop()
        }
        if (!supportTopSpringBackMode() && !supportBottomSpringBackMode()) {
            return false
        }
        val orientation = mOriginScrollOrientation
        if (orientation and ANGLE != 0) {
            checkOrientation(motionEvent)
            if (isTargetScrollOrientation(VERTICAL)
                && mOriginScrollOrientation and HORIZONTAL != 0
                && scrollX == 0
            ) {
                return false
            }
            if (isTargetScrollOrientation(HORIZONTAL)
                && mOriginScrollOrientation and VERTICAL != 0
                && scrollY == 0
            ) {
                return false
            }
            if (isTargetScrollOrientation(VERTICAL) || isTargetScrollOrientation(HORIZONTAL)) {
                disallowParentInterceptTouchEvent(true)
            }
        } else {
            mScrollOrientation = orientation
        }
        if (isTargetScrollOrientation(VERTICAL)) {
            return onVerticalInterceptTouchEvent(motionEvent)
        }
        return if (isTargetScrollOrientation(HORIZONTAL)) {
            onHorizontalInterceptTouchEvent(motionEvent)
        } else false
    }

    private fun disallowParentInterceptTouchEvent(disallowIntercept: Boolean) {
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun checkOrientation(motionEvent: MotionEvent) {
        mHelper.checkOrientation(motionEvent)
        val actionMasked = motionEvent.actionMasked
        if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
            onSecondaryPointerUp(motionEvent)
            return
        }
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            mInitialDownY = mHelper.mInitialDownY
            mInitialDownX = mHelper.mInitialDownX
            mActivePointerId = mHelper.mActivePointerId
            if (scrollY != 0) {
                mScrollOrientation = VERTICAL
                requestDisallowParentInterceptTouchEvent(true)
            } else if (scrollX != 0) {
                mScrollOrientation = HORIZONTAL
                requestDisallowParentInterceptTouchEvent(true)
            } else {
                mScrollOrientation = UNCHECK_ORIENTATION
            }
            if (mOriginScrollOrientation and VERTICAL != 0) {
                checkScrollStart(VERTICAL)
            } else {
                checkScrollStart(HORIZONTAL)
            }
        } else {
            if (actionMasked != MotionEvent.ACTION_UP) {
                if (actionMasked != MotionEvent.ACTION_MOVE) {
                    if (actionMasked != MotionEvent.ACTION_CANCEL) {
                        return
                    }
                } else if (mScrollOrientation == UNCHECK_ORIENTATION && mHelper.mScrollOrientation != UNCHECK_ORIENTATION) {
                    mScrollOrientation = mHelper.mScrollOrientation
                    return
                } else {
                    return
                }
            }
            disallowParentInterceptTouchEvent(false)
            if (mOriginScrollOrientation and VERTICAL != 0) {
                springBack(VERTICAL)
            } else {
                springBack(HORIZONTAL)
            }
        }
    }

    private fun onVerticalInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        var z = false
        if (!isTargetScrollToTop(VERTICAL) && !isTargetScrollToBottom(VERTICAL)) {
            return false
        }
        if (isTargetScrollToTop(VERTICAL) && !supportTopSpringBackMode()) {
            return false
        }
        if (isTargetScrollToBottom(VERTICAL) && !supportBottomSpringBackMode()) {
            return false
        }
        val actionMasked = motionEvent.actionMasked
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            if (actionMasked != MotionEvent.ACTION_UP) {
                if (actionMasked == MotionEvent.ACTION_MOVE) {
                    val i = mActivePointerId
                    if (i == -1) {
                        Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.")
                        return false
                    }
                    val findPointerIndex = motionEvent.findPointerIndex(i)
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                        return false
                    }
                    val y = motionEvent.getY(findPointerIndex)
                    if (isTargetScrollToBottom(VERTICAL) && isTargetScrollToTop(VERTICAL)) {
                        z = true
                    }
                    if ((z || !isTargetScrollToTop(VERTICAL)) && (!z || y <= mInitialDownY)) {
                        if (mInitialDownY - y > mTouchSlop.toFloat() && !mIsBeingDragged) {
                            mIsBeingDragged = true
                            dispatchScrollState(STATE_DRAGGING)
                            mInitialMotionY = y
                        }
                    } else if (y - mInitialDownY > mTouchSlop.toFloat() && !mIsBeingDragged) {
                        mIsBeingDragged = true
                        dispatchScrollState(STATE_DRAGGING)
                        mInitialMotionY = y
                    }
                } else if (actionMasked != MotionEvent.ACTION_CANCEL) {
                    if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                        onSecondaryPointerUp(motionEvent)
                    }
                }
            }
            mIsBeingDragged = false
            mActivePointerId = -1
        } else {
            mActivePointerId = motionEvent.getPointerId(0)
            val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
            if (findPointerIndex2 < 0) {
                return false
            }
            mInitialDownY = motionEvent.getY(findPointerIndex2)
            if (scrollY != 0) {
                mIsBeingDragged = true
                mInitialMotionY = mInitialDownY
            } else {
                mIsBeingDragged = false
            }
        }
        return mIsBeingDragged
    }

    private fun onHorizontalInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        var z = false
        if (!isTargetScrollToTop(HORIZONTAL) && !isTargetScrollToBottom(HORIZONTAL)) {
            return false
        }
        if (isTargetScrollToTop(HORIZONTAL) && !supportTopSpringBackMode()) {
            return false
        }
        if (isTargetScrollToBottom(HORIZONTAL) && !supportBottomSpringBackMode()) {
            return false
        }
        val actionMasked = motionEvent.actionMasked
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            if (actionMasked != MotionEvent.ACTION_UP) {
                if (actionMasked == MotionEvent.ACTION_MOVE) {
                    if (mActivePointerId == MotionEvent.INVALID_POINTER_ID) {
                        Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.")
                        return false
                    }
                    val findPointerIndex = motionEvent.findPointerIndex(mActivePointerId)
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                        return false
                    }
                    val x = motionEvent.getX(findPointerIndex)
                    if (isTargetScrollToBottom(HORIZONTAL) && isTargetScrollToTop(HORIZONTAL)) {
                        z = true
                    }
                    if ((z || !isTargetScrollToTop(HORIZONTAL)) && (!z || x <= mInitialDownX)) {
                        if (mInitialDownX - x > mTouchSlop.toFloat() && !mIsBeingDragged) {
                            mIsBeingDragged = true
                            dispatchScrollState(STATE_DRAGGING)
                            mInitialMotionX = x
                        }
                    } else if (x - mInitialDownX > mTouchSlop.toFloat() && !mIsBeingDragged) {
                        mIsBeingDragged = true
                        dispatchScrollState(STATE_DRAGGING)
                        mInitialMotionX = x
                    }
                } else if (actionMasked != MotionEvent.ACTION_CANCEL) {
                    if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                        onSecondaryPointerUp(motionEvent)
                    }
                }
            }
            mIsBeingDragged = false
            mActivePointerId = MotionEvent.INVALID_POINTER_ID
        } else {
            mActivePointerId = motionEvent.getPointerId(0)
            val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
            if (findPointerIndex2 < 0) {
                return false
            }
            mInitialDownX = motionEvent.getX(findPointerIndex2)
            if (scrollX != 0) {
                mIsBeingDragged = true
                mInitialMotionX = mInitialDownX
            } else {
                mIsBeingDragged = false
            }
        }
        return mIsBeingDragged
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (!isEnabled || !springBackEnable) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    fun superRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private fun requestDisallowParentInterceptTouchEvent(disallowIntercept: Boolean) {
        var parent = parent
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        while (parent != null) {
            if (parent is SpringBackLayout) {
                parent.superRequestDisallowInterceptTouchEvent(disallowIntercept)
            }
            parent = parent.parent
        }
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        val actionMasked = motionEvent.actionMasked
        if (!isEnabled
            || mNestedFlingInProgress
            || mNestedScrollInProgress
            || (Build.VERSION.SDK_INT >= 21 && mTarget.isNestedScrollingEnabled)
        ) {
            return false
        }
        if (!mSpringScroller.isFinished
            && actionMasked == MotionEvent.ACTION_DOWN
        ) {
            mSpringScroller.forceStop()
        }
        if (isTargetScrollOrientation(VERTICAL)) {
            return onVerticalTouchEvent(motionEvent)
        }
        return if (isTargetScrollOrientation(HORIZONTAL)) {
            onHorizontalTouchEvent(motionEvent)
        } else false
    }

    private fun onHorizontalTouchEvent(motionEvent: MotionEvent): Boolean {
        val actionMasked = motionEvent.actionMasked
        if (!isTargetScrollToTop(HORIZONTAL) && !isTargetScrollToBottom(HORIZONTAL)) {
            return false
        }
        if (isTargetScrollToTop(HORIZONTAL) && isTargetScrollToBottom(HORIZONTAL)) {
            return onScrollEvent(motionEvent, actionMasked, HORIZONTAL)
        }
        return if (isTargetScrollToBottom(HORIZONTAL)) {
            onScrollUpEvent(motionEvent, actionMasked, HORIZONTAL)
        } else onScrollDownEvent(motionEvent, actionMasked, HORIZONTAL)
    }

    private fun onVerticalTouchEvent(motionEvent: MotionEvent): Boolean {
        val actionMasked = motionEvent.actionMasked
        if (!isTargetScrollToTop(VERTICAL) && !isTargetScrollToBottom(VERTICAL)) {
            return false
        }
        if (isTargetScrollToTop(VERTICAL) && isTargetScrollToBottom(VERTICAL)) {
            return onScrollEvent(motionEvent, actionMasked, VERTICAL)
        }
        return if (isTargetScrollToBottom(VERTICAL)) {
            onScrollUpEvent(motionEvent, actionMasked, VERTICAL)
        } else onScrollDownEvent(motionEvent, actionMasked, VERTICAL)
    }

    private fun onScrollEvent(
        motionEvent: MotionEvent,
        actionMasked: Int,
        orientation: Int
    ): Boolean {
        val f: Float
        val f2: Float
        val pointerIndex: Int
        if (actionMasked == 0) {
            mActivePointerId = motionEvent.getPointerId(0)
            checkScrollStart(orientation)
        } else if (actionMasked != MotionEvent.ACTION_UP) {
            if (actionMasked == MotionEvent.ACTION_MOVE) {
                val findPointerIndex = motionEvent.findPointerIndex(mActivePointerId)
                if (findPointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                    return false
                } else if (mIsBeingDragged) {
                    if (orientation == VERTICAL) {
                        val y = motionEvent.getY(findPointerIndex)
                        f = sign(y - mInitialMotionY)
                        f2 = obtainSpringBackDistance(y - mInitialMotionY, orientation)
                    } else {
                        val x = motionEvent.getX(findPointerIndex)
                        f = sign(x - mInitialMotionX)
                        f2 = obtainSpringBackDistance(x - mInitialMotionX, orientation)
                    }
                    requestDisallowParentInterceptTouchEvent(true)
                    moveTarget(f * f2, orientation)
                }
            } else if (actionMasked == MotionEvent.ACTION_CANCEL) {
                return false
            } else {
                if (actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
                    if (findPointerIndex2 < 0) {
                        Log.e(
                            TAG,
                            "Got ACTION_POINTER_DOWN event but have an invalid active pointer id."
                        )
                        return false
                    }
                    if (orientation == VERTICAL) {
                        val y2 = motionEvent.getY(findPointerIndex2) - mInitialDownY
                        pointerIndex = motionEvent.actionIndex
                        if (pointerIndex < 0) {
                            Log.e(
                                TAG,
                                "Got ACTION_POINTER_DOWN event but have an invalid action index."
                            )
                            return false
                        }
                        mInitialDownY = motionEvent.getY(pointerIndex) - y2
                        mInitialMotionY = mInitialDownY
                    } else {
                        val x2 = motionEvent.getX(findPointerIndex2) - mInitialDownX
                        pointerIndex = motionEvent.actionIndex
                        if (pointerIndex < 0) {
                            Log.e(
                                TAG,
                                "Got ACTION_POINTER_DOWN event but have an invalid action index."
                            )
                            return false
                        }
                        mInitialDownX = motionEvent.getX(pointerIndex) - x2
                        mInitialMotionX = mInitialDownX
                    }
                    mActivePointerId = motionEvent.getPointerId(pointerIndex)
                } else if (actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    onSecondaryPointerUp(motionEvent)
                }
            }
        } else if (motionEvent.findPointerIndex(mActivePointerId) < 0) {
            Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.")
            return false
        } else {
            if (mIsBeingDragged) {
                mIsBeingDragged = false
                springBack(orientation)
            }
            mActivePointerId = MotionEvent.INVALID_POINTER_ID
            return false
        }
        return true
    }

    private fun checkVerticalScrollStart() {
        if (scrollY != 0) {
            mIsBeingDragged = true
            val obtainTouchDistance = obtainTouchDistance(abs(scrollY).toFloat(), VERTICAL)
            if (scrollY < 0) {
                mInitialDownY -= obtainTouchDistance
            } else {
                mInitialDownY += obtainTouchDistance
            }
            mInitialMotionY = mInitialDownY
            return
        }
        mIsBeingDragged = false
    }

    private fun checkScrollStart(orientation: Int) {
        if (orientation == VERTICAL) {
            checkVerticalScrollStart()
        } else {
            checkHorizontalScrollStart()
        }
    }

    private fun checkHorizontalScrollStart() {
        if (scrollX != 0) {
            mIsBeingDragged = true
            val obtainTouchDistance = obtainTouchDistance(abs(scrollX).toFloat(), VERTICAL)
            if (scrollX < 0) {
                mInitialDownX -= obtainTouchDistance
            } else {
                mInitialDownX += obtainTouchDistance
            }
            mInitialMotionX = mInitialDownX
            return
        }
        mIsBeingDragged = false
    }

    private fun onScrollDownEvent(
        motionEvent: MotionEvent,
        actionMasked: Int,
        orientation: Int
    ): Boolean {
        val f: Float
        val f2: Float
        val pointerIndex: Int
        if (actionMasked != MotionEvent.ACTION_DOWN) {
            if (actionMasked != MotionEvent.ACTION_UP) {
                if (actionMasked == MotionEvent.ACTION_MOVE) {
                    val findPointerIndex = motionEvent.findPointerIndex(mActivePointerId)
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                        return false
                    } else if (mIsBeingDragged) {
                        if (orientation == VERTICAL) {
                            val y = motionEvent.getY(findPointerIndex)
                            f = sign(y - mInitialMotionY)
                            f2 = obtainSpringBackDistance(y - mInitialMotionY, orientation)
                        } else {
                            val x = motionEvent.getX(findPointerIndex)
                            f = sign(x - mInitialMotionX)
                            f2 = obtainSpringBackDistance(x - mInitialMotionX, orientation)
                        }
                        val f3 = f * f2
                        if (f3 > 0.0f) {
                            requestDisallowParentInterceptTouchEvent(true)
                            moveTarget(f3, orientation)
                        } else {
                            moveTarget(0.0f, orientation)
                            return false
                        }
                    }
                } else if (actionMasked != MotionEvent.ACTION_CANCEL) {
                    if (actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
                        if (findPointerIndex2 < 0) {
                            Log.e(
                                TAG,
                                "Got ACTION_POINTER_DOWN event but have an invalid active pointer id."
                            )
                            return false
                        }
                        if (orientation == VERTICAL) {
                            val y2 = motionEvent.getY(findPointerIndex2) - mInitialDownY
                            pointerIndex = motionEvent.actionIndex
                            if (pointerIndex < 0) {
                                Log.e(
                                    TAG,
                                    "Got ACTION_POINTER_DOWN event but have an invalid action index."
                                )
                                return false
                            }
                            mInitialDownY = motionEvent.getY(pointerIndex) - y2
                            mInitialMotionY = mInitialDownY
                        } else {
                            val x2 = motionEvent.getX(findPointerIndex2) - mInitialDownX
                            pointerIndex = motionEvent.actionIndex
                            if (pointerIndex < 0) {
                                Log.e(
                                    TAG,
                                    "Got ACTION_POINTER_DOWN event but have an invalid action index."
                                )
                                return false
                            }
                            mInitialDownX = motionEvent.getX(pointerIndex) - x2
                            mInitialMotionX = mInitialDownX
                        }
                        mActivePointerId = motionEvent.getPointerId(pointerIndex)
                    } else if (actionMasked == 6) {
                        onSecondaryPointerUp(motionEvent)
                    }
                }
            }
            if (motionEvent.findPointerIndex(mActivePointerId) < 0) {
                Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.")
                return false
            }
            if (mIsBeingDragged) {
                mIsBeingDragged = false
                springBack(orientation)
            }
            mActivePointerId = MotionEvent.INVALID_POINTER_ID
            return false
        }
        mActivePointerId = motionEvent.getPointerId(0)
        checkScrollStart(orientation)
        return true
    }

    private fun moveTarget(delta: Float, axes: Int) {
        if (axes == 2) {
            scrollTo(0, (-delta).toInt())
        } else {
            scrollTo((-delta).toInt(), 0)
        }
    }

    private fun springBack(axes: Int) {
        springBack(0.0f, axes, true)
    }

    private fun springBack(velocity: Float, axes: Int, z: Boolean) {
        val onSpringListener = mOnSpringListener
        if (onSpringListener == null || !onSpringListener.onSpringBack()) {
            mSpringScroller.forceStop()
            mSpringScroller.scrollByFling(
                scrollX.toFloat(),
                0.0f,
                scrollY.toFloat(),
                0.0f,
                velocity,
                axes,
                false
            )
            dispatchScrollState(2)
            if (z) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun onScrollUpEvent(motionEvent: MotionEvent, actionMasked: Int, axes: Int): Boolean {
        val f: Float
        val f2: Float
        val i3: Int
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    val findPointerIndex = motionEvent.findPointerIndex(mActivePointerId)
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.")
                        return false
                    } else if (mIsBeingDragged) {
                        if (axes == 2) {
                            val y = motionEvent.getY(findPointerIndex)
                            f = sign(mInitialMotionY - y)
                            f2 = obtainSpringBackDistance(mInitialMotionY - y, axes)
                        } else {
                            val x = motionEvent.getX(findPointerIndex)
                            f = sign(mInitialMotionX - x)
                            f2 = obtainSpringBackDistance(mInitialMotionX - x, axes)
                        }
                        val f3 = f * f2
                        if (f3 > 0.0f) {
                            requestDisallowParentInterceptTouchEvent(true)
                            moveTarget(-f3, axes)
                        } else {
                            moveTarget(0.0f, axes)
                            return false
                        }
                    }
                } else if (actionMasked != 3) {
                    if (actionMasked == 5) {
                        val findPointerIndex2 = motionEvent.findPointerIndex(mActivePointerId)
                        if (findPointerIndex2 < 0) {
                            Log.e(
                                TAG,
                                "Got ACTION_POINTER_DOWN event but have an invalid active pointer id."
                            )
                            return false
                        }
                        if (axes == 2) {
                            val y2 = motionEvent.getY(findPointerIndex2) - mInitialDownY
                            i3 = motionEvent.actionIndex
                            if (i3 < 0) {
                                Log.e(
                                    TAG,
                                    "Got ACTION_POINTER_DOWN event but have an invalid action index."
                                )
                                return false
                            }
                            mInitialDownY = motionEvent.getY(i3) - y2
                            mInitialMotionY = mInitialDownY
                        } else {
                            val x2 = motionEvent.getX(findPointerIndex2) - mInitialDownX
                            i3 = motionEvent.actionIndex
                            if (i3 < 0) {
                                Log.e(
                                    TAG,
                                    "Got ACTION_POINTER_DOWN event but have an invalid action index."
                                )
                                return false
                            }
                            mInitialDownX = motionEvent.getX(i3) - x2
                            mInitialMotionX = mInitialDownX
                        }
                        mActivePointerId = motionEvent.getPointerId(i3)
                    } else if (actionMasked == 6) {
                        onSecondaryPointerUp(motionEvent)
                    }
                }
            }
            if (motionEvent.findPointerIndex(mActivePointerId) < 0) {
                Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.")
                return false
            }
            if (mIsBeingDragged) {
                mIsBeingDragged = false
                springBack(axes)
            }
            mActivePointerId = -1
            return false
        }
        mActivePointerId = motionEvent.getPointerId(0)
        checkScrollStart(axes)
        return true
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent) {
        val actionIndex = motionEvent.actionIndex
        if (motionEvent.getPointerId(actionIndex) == mActivePointerId) {
            mActivePointerId = motionEvent.getPointerId(if (actionIndex == 0) 1 else 0)
        }
    }

    private fun obtainSpringBackDistance(f: Float, i: Int): Float {
        return obtainDampingDistance(
            (abs(f) / (if (i == 2) mScreenHeight else mScreenWith).toFloat()).coerceAtMost(1.0f), i
        )
    }

    private fun obtainMaxSpringBackDistance(i: Int): Float {
        return obtainDampingDistance(1.0f, i)
    }

    private fun obtainDampingDistance(f: Float, i: Int): Float {
        val i2 = if (i == 2) mScreenHeight else mScreenWith
        val min = f.coerceAtMost(1.0f).toDouble()
        return (min.pow(3.0) / 3.0 - min.pow(2.0) + min).toFloat() * i2.toFloat()
    }

    private fun obtainTouchDistance(f: Float, orientation: Int): Float {
        val i2 = if (orientation == VERTICAL) mScreenHeight else mScreenWith
        val d = i2.toDouble()
        return (d - d.pow(0.6666666666666666) * ((i2.toFloat() - f * 3.0f).toDouble()
            .pow(0.3333333333333333))).toFloat()
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        var unconsumed = 0
        val isVertical = mNestedScrollAxes == VERTICAL
        val deltaConsumed = if (isVertical) dyConsumed else dxConsumed
        val oldConsumed = if (isVertical) consumed[1] else consumed[0]
        dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            mParentOffsetInWindow,
            type,
            consumed
        )
        if (springBackEnable) {
            val parentConsumed = (if (isVertical) consumed[1] else consumed[0]) - oldConsumed
            unconsumed =
                if (isVertical) dyUnconsumed - parentConsumed else dxUnconsumed - parentConsumed
            val orientation = if (isVertical) VERTICAL else HORIZONTAL
            if (unconsumed >= 0 || !isTargetScrollToTop(orientation) || !supportTopSpringBackMode()) {
                if (unconsumed > 0 && isTargetScrollToBottom(orientation) && supportBottomSpringBackMode()) {
                    if (type != TYPE_TOUCH) {
                        val obtainMaxSpringBackDistance = obtainMaxSpringBackDistance(orientation)
                        if (mVelocityY != 0.0f || mVelocityX != 0.0f) {
                            mScrollByFling = true
                            if (deltaConsumed != 0 && unconsumed.toFloat() <= obtainMaxSpringBackDistance) {
                                mSpringScroller.setFirstStep(unconsumed)
                            }
                            dispatchScrollState(STATE_SETTLING)
                        } else if (mTotalScrollBottomUnconsumed == 0.0f) {
                            val f = obtainMaxSpringBackDistance - mTotalFlingUnconsumed
                            if (consumeNestFlingCounter < MAX_FLING_CONSUME_COUNTER) {
                                if (f <= abs(unconsumed).toFloat()) {
                                    mTotalFlingUnconsumed += f
                                    consumed[1] = (consumed[1].toFloat() + f).toInt()
                                } else {
                                    mTotalFlingUnconsumed += abs(unconsumed).toFloat()
                                    consumed[1] = consumed[1] + unconsumed
                                }
                                dispatchScrollState(STATE_SETTLING)
                                moveTarget(
                                    -obtainSpringBackDistance(mTotalFlingUnconsumed, orientation),
                                    orientation
                                )
                                consumeNestFlingCounter++
                            }
                        }
                    } else if (mSpringScroller.isFinished) {
                        mTotalScrollBottomUnconsumed += abs(unconsumed).toFloat()
                        dispatchScrollState(STATE_DRAGGING)
                        moveTarget(
                            -obtainSpringBackDistance(mTotalScrollBottomUnconsumed, orientation),
                            orientation
                        )
                        consumed[1] = consumed[1] + unconsumed
                    }
                }
            } else if (type != TYPE_TOUCH) {
                val obtainMaxSpringBackDistance2 = obtainMaxSpringBackDistance(orientation)
                if (mVelocityY != 0.0f || mVelocityX != 0.0f) {
                    mScrollByFling = true
                    if (deltaConsumed != 0 && (-unconsumed).toFloat() <= obtainMaxSpringBackDistance2) {
                        mSpringScroller.setFirstStep(unconsumed)
                    }
                    dispatchScrollState(STATE_SETTLING)
                } else if (mTotalScrollTopUnconsumed == 0.0f) {
                    val f2 = obtainMaxSpringBackDistance2 - mTotalFlingUnconsumed
                    if (consumeNestFlingCounter < MAX_FLING_CONSUME_COUNTER) {
                        if (f2 <= abs(unconsumed).toFloat()) {
                            mTotalFlingUnconsumed += f2
                            consumed[1] = (consumed[1].toFloat() + f2).toInt()
                        } else {
                            mTotalFlingUnconsumed += abs(unconsumed).toFloat()
                            consumed[1] = consumed[1] + unconsumed
                        }
                        dispatchScrollState(STATE_SETTLING)
                        moveTarget(
                            obtainSpringBackDistance(mTotalFlingUnconsumed, orientation),
                            orientation
                        )
                        consumeNestFlingCounter++
                    }
                }
            } else if (mSpringScroller.isFinished) {
                mTotalScrollTopUnconsumed += abs(unconsumed).toFloat()
                dispatchScrollState(STATE_DRAGGING)
                moveTarget(
                    obtainSpringBackDistance(mTotalScrollTopUnconsumed, orientation),
                    orientation
                )
                consumed[1] = consumed[1] + unconsumed
            }
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        onNestedScroll(
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            mNestedScrollingV2ConsumedCompat
        )
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        onNestedScroll(
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            TYPE_TOUCH,
            mNestedScrollingV2ConsumedCompat
        )
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        if (axes and mOriginScrollOrientation == 0) {
            return false
        }
        if (springBackEnable) {
            mNestedScrollAxes = axes
            if (axes and mOriginScrollOrientation == 0
                || !onStartNestedScroll(child, child, axes)
            ) {
                return false
            }
            val isVertical = axes and VERTICAL != 0
            val currentScroll = if (isVertical) scrollY else scrollX
            if (!(type == 0 || currentScroll == 0 || mTarget !is NestedScrollView)) {
                return false
            }
        }


        if (mNestedScrollingChildHelper.startNestedScroll(axes, type)) {
        }
        return true
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return isEnabled
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        if (springBackEnable) {
            var orientation = VERTICAL
            val isVertical = mNestedScrollAxes == VERTICAL
            if (!isVertical) {
                orientation = HORIZONTAL
            }
            val scroll = (if (isVertical) scrollY else scrollX).toFloat()
            if (type != TYPE_TOUCH) {
                if (scroll == 0.0f) {
                    mTotalFlingUnconsumed = 0.0f
                } else {
                    mTotalFlingUnconsumed = obtainTouchDistance(abs(scroll), orientation)
                }
                mNestedFlingInProgress = true
                consumeNestFlingCounter = 0
            } else {
                if (scroll == 0.0f) {
                    mTotalScrollTopUnconsumed = 0.0f
                    mTotalScrollBottomUnconsumed = 0.0f
                } else if (scroll < 0.0f) {
                    mTotalScrollTopUnconsumed = obtainTouchDistance(abs(scroll), orientation)
                    mTotalScrollBottomUnconsumed = 0.0f
                } else {
                    mTotalScrollTopUnconsumed = 0.0f
                    mTotalScrollBottomUnconsumed = obtainTouchDistance(abs(scroll), orientation)
                }
                mNestedScrollInProgress = true
            }
            mVelocityY = 0.0f
            mVelocityX = 0.0f
            mScrollByFling = false
            mSpringScroller.forceStop()
        }
        onNestedScrollAccepted(child, target, axes)
    }


    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
        startNestedScroll(axes and VERTICAL)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (springBackEnable) {
            if (mNestedScrollAxes == VERTICAL) {
                onNestedPreScroll(dy, consumed, type)
            } else {
                onNestedPreScroll(dx, consumed, type)
            }
        }
        val iArr2 = mParentScrollConsumed
        if (dispatchNestedPreScroll(
                dx - consumed[0],
                dy - consumed[1],
                iArr2,
                null,
                type
            )
        ) {
            consumed[0] = consumed[0] + iArr2[0]
            consumed[1] = consumed[1] + iArr2[1]
        }
    }

    private fun onNestedPreScroll(delta: Int, consumed: IntArray, type: Int) {
        val isVertical = mNestedScrollAxes == VERTICAL
        val axes = if (isVertical) VERTICAL else HORIZONTAL
        val abs = abs(if (isVertical) scrollY else scrollX)
        var f = 0.0f
        if (type == TYPE_TOUCH) {
            if (delta > 0) {
                if (mTotalScrollTopUnconsumed > 0.0f) {
                    if (delta > mTotalScrollTopUnconsumed) {
                        consumeDelta(mTotalScrollTopUnconsumed.toInt(), consumed, axes)
                        mTotalScrollTopUnconsumed = 0.0f
                    } else {
                        mTotalScrollTopUnconsumed -= delta
                        consumeDelta(delta, consumed, axes)
                    }
                    dispatchScrollState(STATE_DRAGGING)
                    moveTarget(obtainSpringBackDistance(mTotalScrollTopUnconsumed, axes), axes)
                    return
                }
            }
            if (delta < 0) {
                val f4 = mTotalScrollBottomUnconsumed
                if (-f4 < 0.0f) {
                    val f5 = delta.toFloat()
                    if (f5 < -f4) {
                        consumeDelta(f4.toInt(), consumed, axes)
                        mTotalScrollBottomUnconsumed = 0.0f
                    } else {
                        mTotalScrollBottomUnconsumed = f4 + f5
                        consumeDelta(delta, consumed, axes)
                    }
                    dispatchScrollState(1)
                    moveTarget(-obtainSpringBackDistance(mTotalScrollBottomUnconsumed, axes), axes)
                    return
                }
                return
            }
            return
        }
        val velocity = if (axes == VERTICAL) mVelocityY else mVelocityX
        if (delta > 0) {
            val f7 = mTotalScrollTopUnconsumed
            if (f7 > 0.0f) {
                if (velocity > VELOCITY_THRESHOLD) {
                    val obtainSpringBackDistance = obtainSpringBackDistance(f7, axes)
                    val f8 = delta.toFloat()
                    if (f8 > obtainSpringBackDistance) {
                        consumeDelta(obtainSpringBackDistance.toInt(), consumed, axes)
                        mTotalScrollTopUnconsumed = 0.0f
                    } else {
                        consumeDelta(delta, consumed, axes)
                        f = obtainSpringBackDistance - f8
                        mTotalScrollTopUnconsumed = obtainTouchDistance(f, axes)
                    }
                    moveTarget(f, axes)
                    dispatchScrollState(STATE_DRAGGING)
                    return
                }
                if (!mScrollByFling) {
                    mScrollByFling = true
                    springBack(velocity, axes, false)
                }
                if (mSpringScroller.computeScrollOffset()) {
                    scrollTo(mSpringScroller.currX, mSpringScroller.currY)
                    mTotalScrollTopUnconsumed = obtainTouchDistance(abs.toFloat(), axes)
                } else {
                    mTotalScrollTopUnconsumed = 0.0f
                }
                consumeDelta(delta, consumed, axes)
                return
            }
        }
        if (delta < 0) {
            val f9 = mTotalScrollBottomUnconsumed
            if (-f9 < 0.0f) {
                if (velocity < -VELOCITY_THRESHOLD) {
                    val obtainSpringBackDistance2 = obtainSpringBackDistance(f9, axes)
                    val f10 = delta.toFloat()
                    if (f10 < -obtainSpringBackDistance2) {
                        consumeDelta(obtainSpringBackDistance2.toInt(), consumed, axes)
                        mTotalScrollBottomUnconsumed = 0.0f
                    } else {
                        consumeDelta(delta, consumed, axes)
                        f = obtainSpringBackDistance2 + f10
                        mTotalScrollBottomUnconsumed = obtainTouchDistance(f, axes)
                    }
                    dispatchScrollState(STATE_DRAGGING)
                    moveTarget(-f, axes)
                    return
                }
                if (!mScrollByFling) {
                    mScrollByFling = true
                    springBack(velocity, axes, false)
                }
                if (mSpringScroller.computeScrollOffset()) {
                    scrollTo(mSpringScroller.currX, mSpringScroller.currY)
                    mTotalScrollBottomUnconsumed = obtainTouchDistance(abs.toFloat(), axes)
                } else {
                    mTotalScrollBottomUnconsumed = 0.0f
                }
                consumeDelta(delta, consumed, axes)
                return
            }
        }
        if (delta == 0) {
            return
        }
        if ((mTotalScrollBottomUnconsumed == 0.0f || mTotalScrollTopUnconsumed == 0.0f) && mScrollByFling && scrollY == 0) {
            consumeDelta(delta, consumed, axes)
        }
    }

    private fun consumeDelta(delta: Int, consumed: IntArray, axes: Int) {
        if (axes == 2) {
            consumed[1] = delta
        } else {
            consumed[0] = delta
        }
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mNestedScrollingChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        mNestedScrollingParentHelper.onStopNestedScroll(target, type)
        stopNestedScroll(type)
        if (springBackEnable) {
            var axes = HORIZONTAL
            val isVertical = mNestedScrollAxes == VERTICAL
            if (isVertical) {
                axes = VERTICAL
            }
            if (mNestedScrollInProgress) {
                mNestedScrollInProgress = false
                val scrollY = (if (isVertical) scrollY else scrollX).toFloat()
                if (!mNestedFlingInProgress && scrollY != 0.0f) {
                    springBack(axes)
                } else if (scrollY != 0.0f) {
                    dispatchScrollState(STATE_SETTLING)
                }
            } else if (mNestedFlingInProgress) {
                mNestedFlingInProgress = false
                if (mScrollByFling) {
                    if (mSpringScroller.isFinished) {
                        springBack(if (axes == VERTICAL) mVelocityY else mVelocityX, axes, false)
                    }
                    postInvalidateOnAnimation()
                    return
                }
                springBack(axes)
            }
        }
    }

    override fun stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll()
    }

    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
            consumed
        )
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return mNestedScrollingChildHelper.startNestedScroll(axes, type)
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return mNestedScrollingChildHelper.startNestedScroll(axes)
    }

    override fun stopNestedScroll(type: Int) {
        mNestedScrollingChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mNestedScrollingChildHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
            dx,
            dy,
            consumed,
            offsetInWindow,
            type
        )
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    fun smoothScrollTo(x: Int, y: Int) {
        if (x - scrollX != 0 || y - scrollY != 0) {
            mSpringScroller.forceStop()
            mSpringScroller.scrollByFling(
                scrollX.toFloat(),
                x.toFloat(),
                scrollY.toFloat(),
                y.toFloat(),
                0.0f,
                2,
                true
            )
            dispatchScrollState(STATE_SETTLING)
            postInvalidateOnAnimation()
        }
    }

    private fun dispatchScrollState(scrollState: Int) {
        val oldScrollState = mScrollState
        if (oldScrollState != scrollState) {
            mScrollState = scrollState
            for (onStateChanged in mOnScrollListeners) {
                onStateChanged.onStateChanged(
                    oldScrollState,
                    scrollState,
                    mSpringScroller.isFinished
                )
            }
        }
    }

    fun addOnScrollListener(onScrollListener: OnScrollListener) {
        mOnScrollListeners.add(onScrollListener)
    }

    fun removeOnScrollListener(onScrollListener: OnScrollListener) {
        mOnScrollListeners.remove(onScrollListener)
    }

    fun setOnSpringListener(onSpringListener: OnSpringListener?) {
        mOnSpringListener = onSpringListener
    }

    fun hasSpringListener(): Boolean {
        return mOnSpringListener != null
    }

    override fun onNestedCurrentFling(velocityX: Float, velocityY: Float): Boolean {
        mVelocityX = velocityX
        mVelocityY = velocityY
        return true
    }

    companion object {
        const val ANGLE = 4
        private const val INVALID_ID = -1
        private const val INVALID_POINTER = -1
        private const val MAX_FLING_CONSUME_COUNTER = 4
        const val SPRING_BACK_BOTTOM = 2
        const val SPRING_BACK_TOP = 1
        const val STATE_DRAGGING = 1
        const val STATE_IDLE = 0
        const val STATE_SETTLING = 2
        private const val TAG = "SpringBackLayout"
        private const val VELOCITY_THRESHOLD = 2000

    }
}