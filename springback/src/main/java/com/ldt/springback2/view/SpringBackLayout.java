package com.ldt.springback2.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ListViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ldt.springback.R;

import java.util.ArrayList;
import java.util.List;

public class SpringBackLayout extends ViewGroup implements NestedScrollingParent3, NestedScrollingChild3, NestedCurrentFling {
    public static final int ANGLE = 4;
    public static final int HORIZONTAL = 1;
    private static final int INVALID_ID = -1;
    private static final int INVALID_POINTER = -1;
    private static final int MAX_FLING_CONSUME_COUNTER = 4;
    public static final int SPRING_BACK_BOTTOM = 2;
    public static final int SPRING_BACK_TOP = 1;
    public static final int STATE_DRAGGING = 1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_SETTLING = 2;
    private static final String TAG = "SpringBackLayout";
    public static final int UNCHECK_ORIENTATION = 0;
    private static final int VELOCITY_THRADHOLD = 2000;
    public static final int VERTICAL = 2;
    private int consumeNestFlingCounter;
    private int mActivePointerId;
    private SpringBackLayoutHelper mHelper;
    private float mInitialDownX;
    private float mInitialDownY;
    private float mInitialMotionX;
    private float mInitialMotionY;
    private boolean mIsBeingDragged;
    private boolean mNestedFlingInProgress;
    private int mNestedScrollAxes;
    private boolean mNestedScrollInProgress;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final int[] mNestedScrollingV2ConsumedCompat;
    private List<OnScrollListener> mOnScrollListeners;
    private OnSpringListener mOnSpringListener;
    private int mOriginScrollOrientation;
    private final int[] mParentOffsetInWindow;
    private final int[] mParentScrollConsumed;
    private final int mScreenHeight;
    private final int mScreenWith;
    private boolean mScrollByFling;
    private int mScrollOrientation;
    private int mScrollState;
    private boolean mSpringBackEnable;
    private int mSpringBackMode;
    private SpringScroller mSpringScroller;
    private View mTarget;
    private int mTargetId;
    private float mTotalFlingUnconsumed;
    private float mTotalScrollBottomUnconsumed;
    private float mTotalScrollTopUnconsumed;
    private int mTouchSlop;
    private float mVelocityX;
    private float mVelocityY;

    public interface OnScrollListener {
        default void onScrolled(SpringBackLayout springBackLayout, int dx, int dy) {
        }

        default void onStateChanged(int oldScrollState, int scrollState, boolean isFinished) {
        }
    }

    public interface OnSpringListener {
        boolean onSpringBack();
    }

    public SpringBackLayout(Context context) {
        this(context, (AttributeSet) null);
    }

    public SpringBackLayout(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mActivePointerId = -1;
        this.consumeNestFlingCounter = 0;
        this.mParentScrollConsumed = new int[2];
        this.mParentOffsetInWindow = new int[2];
        this.mNestedScrollingV2ConsumedCompat = new int[2];
        this.mSpringBackEnable = true;
        this.mOnScrollListeners = new ArrayList<>();
        this.mScrollState = 0;
        this.mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        this.mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        this.mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        TypedArray obtainStyledAttributes = context.obtainStyledAttributes(attributeSet, R.styleable.SpringBackLayout);
        this.mTargetId = obtainStyledAttributes.getResourceId(R.styleable.SpringBackLayout_scrollableView, -1);
        this.mOriginScrollOrientation = obtainStyledAttributes.getInt(R.styleable.SpringBackLayout_scrollOrientation, 2);
        this.mSpringBackMode = obtainStyledAttributes.getInt(R.styleable.SpringBackLayout_springBackMode, 3);
        obtainStyledAttributes.recycle();
        this.mSpringScroller = new SpringScroller();
        this.mHelper = new SpringBackLayoutHelper(this, this.mOriginScrollOrientation);
        setNestedScrollingEnabled(true);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        this.mScreenWith = displayMetrics.widthPixels;
        this.mScreenHeight = displayMetrics.heightPixels;

    }

    public void setSpringBackEnable(boolean enable) {
        this.mSpringBackEnable = enable;
    }

    public boolean springBackEnable() {
        return this.mSpringBackEnable;
    }

    public void setScrollOrientation(int orientation) {
        this.mOriginScrollOrientation = orientation;
        this.mHelper.mTargetScrollOrientation = orientation;
    }

    public void setSpringBackMode(int mode) {
        this.mSpringBackMode = mode;
    }

    public int getSpringBackMode() {
        return this.mSpringBackMode;
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        View view = this.mTarget;
        if ((view instanceof NestedScrollingChild3) && Build.VERSION.SDK_INT >= 21 && enabled != this.mTarget.isNestedScrollingEnabled()) {
            this.mTarget.setNestedScrollingEnabled(enabled);
        }
    }

    private boolean supportTopSpringBackMode() {
        return (this.mSpringBackMode & 1) != 0;
    }

    private boolean supportBottomSpringBackMode() {
        return (this.mSpringBackMode & 2) != 0;
    }

    public void setTarget(@NonNull View view) {
        this.mTarget = view;
        if (Build.VERSION.SDK_INT >= 21) {
            View view2 = this.mTarget;
            if ((view2 instanceof NestedScrollingChild3) && !view2.isNestedScrollingEnabled()) {
                this.mTarget.setNestedScrollingEnabled(true);
            }
        }
    }

    private void ensureTarget() {
        if (this.mTarget == null) {
            int i = this.mTargetId;
            if (i != -1) {
                this.mTarget = findViewById(i);
            } else {
                throw new IllegalArgumentException("invalid target Id");
            }
        }
        if (this.mTarget != null) {
            if (Build.VERSION.SDK_INT >= 21 && isEnabled()) {
                View view = this.mTarget;
                if ((view instanceof NestedScrollingChild3) && !view.isNestedScrollingEnabled()) {
                    this.mTarget.setNestedScrollingEnabled(true);
                }
            }
            if (this.mTarget.getOverScrollMode() != 2) {
                this.mTarget.setOverScrollMode(2);
                return;
            }
            return;
        }
        throw new IllegalArgumentException("fail to get target");
    }

    /* access modifiers changed from: protected */
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        this.mTarget.layout(paddingLeft, paddingTop, ((measuredWidth - getPaddingLeft()) - getPaddingRight()) + paddingLeft, ((measuredHeight - getPaddingTop()) - getPaddingBottom()) + paddingTop);
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ensureTarget();
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        int mode2 = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(widthMeasureSpec);
        int size2 = MeasureSpec.getSize(heightMeasureSpec);
        measureChild(this.mTarget, widthMeasureSpec, heightMeasureSpec);
        if (size > this.mTarget.getMeasuredWidth()) {
            size = this.mTarget.getMeasuredWidth();
        }
        if (size2 > this.mTarget.getMeasuredHeight()) {
            size2 = this.mTarget.getMeasuredHeight();
        }
        if (mode != 1073741824) {
            size = this.mTarget.getMeasuredWidth();
        }
        if (mode2 != 1073741824) {
            size2 = this.mTarget.getMeasuredHeight();
        }
        setMeasuredDimension(size, size2);
    }

    public void computeScroll() {
        super.computeScroll();
        if (this.mSpringScroller.computeScrollOffset()) {
            scrollTo(this.mSpringScroller.getCurrX(), this.mSpringScroller.getCurrY());
            if (!this.mSpringScroller.isFinished()) {
                postInvalidateOnAnimation();
            } else {
                dispatchScrollState(0);
            }
        }
    }

    /* access modifiers changed from: protected */
    public void onScrollChanged(int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY);
        for (OnScrollListener onScrolled : this.mOnScrollListeners) {
            onScrolled.onScrolled(this, scrollX - oldScrollX, scrollY - oldScrollY);
        }
    }

    private boolean isVerticalTargetScrollToTop() {
        View view = this.mTarget;
        if (view instanceof ListView) {
            return !ListViewCompat.canScrollList((ListView) view, -1);
        }
        return !view.canScrollVertically(-1);
    }

    private boolean isHorizontalTargetScrollToTop() {
        return !this.mTarget.canScrollHorizontally(-1);
    }

    private boolean isTargetScrollOrientation(int orientation) {
        return this.mScrollOrientation == orientation;
    }

    private boolean isTargetScrollToTop(int i) {
        if (i != 2) {
            return !this.mTarget.canScrollHorizontally(-1);
        }
        View view = this.mTarget;
        if (view instanceof ListView) {
            return !ListViewCompat.canScrollList((ListView) view, -1);
        }
        return !view.canScrollVertically(-1);
    }

    private boolean isTargetScrollToBottom(int i) {
        if (i != 2) {
            return !this.mTarget.canScrollHorizontally(1);
        }
        View view = this.mTarget;
        if (view instanceof ListView) {
            return !ListViewCompat.canScrollList((ListView) view, 1);
        }
        return !view.canScrollVertically(1);
    }

    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() == 0 && this.mScrollState == 2 && this.mHelper.isTouchInTarget(motionEvent)) {
            dispatchScrollState(1);
        }
        boolean dispatchTouchEvent = super.dispatchTouchEvent(motionEvent);
        if (motionEvent.getActionMasked() == 1 && this.mScrollState != 2) {
            dispatchScrollState(0);
        }
        return dispatchTouchEvent;
    }

    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        if (!this.mSpringBackEnable || !isEnabled() || this.mNestedFlingInProgress || this.mNestedScrollInProgress || (Build.VERSION.SDK_INT >= 21 && this.mTarget.isNestedScrollingEnabled())) {
            return false;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (!this.mSpringScroller.isFinished() && actionMasked == 0) {
            this.mSpringScroller.forceStop();
        }
        if (!supportTopSpringBackMode() && !supportBottomSpringBackMode()) {
            return false;
        }
        int i = this.mOriginScrollOrientation;
        if ((i & 4) != 0) {
            checkOrientation(motionEvent);
            if (isTargetScrollOrientation(2) && (this.mOriginScrollOrientation & 1) != 0 && ((float) getScrollX()) == 0.0f) {
                return false;
            }
            if (isTargetScrollOrientation(1) && (this.mOriginScrollOrientation & 2) != 0 && ((float) getScrollY()) == 0.0f) {
                return false;
            }
            if (isTargetScrollOrientation(2) || isTargetScrollOrientation(1)) {
                disallowParentInterceptTouchEvent(true);
            }
        } else {
            this.mScrollOrientation = i;
        }
        if (isTargetScrollOrientation(2)) {
            return onVerticalInterceptTouchEvent(motionEvent);
        }
        if (isTargetScrollOrientation(1)) {
            return onHorizontalInterceptTouchEvent(motionEvent);
        }
        return false;
    }

    private void disallowParentInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private void checkOrientation(MotionEvent motionEvent) {
        this.mHelper.checkOrientation(motionEvent);
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked != 2) {
                    if (actionMasked != 3) {
                        if (actionMasked == 6) {
                            onSecondaryPointerUp(motionEvent);
                            return;
                        }
                        return;
                    }
                } else if (this.mScrollOrientation == 0 && this.mHelper.mScrollOrientation != 0) {
                    this.mScrollOrientation = this.mHelper.mScrollOrientation;
                    return;
                } else {
                    return;
                }
            }
            disallowParentInterceptTouchEvent(false);
            if ((this.mOriginScrollOrientation & 2) != 0) {
                springBack(2);
            } else {
                springBack(1);
            }
        } else {
            this.mInitialDownY = this.mHelper.mInitialDownY;
            this.mInitialDownX = this.mHelper.mInitialDownX;
            this.mActivePointerId = this.mHelper.mActivePointerId;
            if (getScrollY() != 0) {
                this.mScrollOrientation = 2;
                requestDisallowParentInterceptTouchEvent(true);
            } else if (getScrollX() != 0) {
                this.mScrollOrientation = 1;
                requestDisallowParentInterceptTouchEvent(true);
            } else {
                this.mScrollOrientation = 0;
            }
            if ((this.mOriginScrollOrientation & 2) != 0) {
                checkScrollStart(2);
            } else {
                checkScrollStart(1);
            }
        }
    }

    private boolean onVerticalInterceptTouchEvent(MotionEvent motionEvent) {
        boolean z = false;
        if (!isTargetScrollToTop(2) && !isTargetScrollToBottom(2)) {
            return false;
        }
        if (isTargetScrollToTop(2) && !supportTopSpringBackMode()) {
            return false;
        }
        if (isTargetScrollToBottom(2) && !supportBottomSpringBackMode()) {
            return false;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    int i = this.mActivePointerId;
                    if (i == -1) {
                        Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                        return false;
                    }
                    int findPointerIndex = motionEvent.findPointerIndex(i);
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                        return false;
                    }
                    float y = motionEvent.getY(findPointerIndex);
                    if (isTargetScrollToBottom(2) && isTargetScrollToTop(2)) {
                        z = true;
                    }
                    if ((z || !isTargetScrollToTop(2)) && (!z || y <= this.mInitialDownY)) {
                        if (this.mInitialDownY - y > ((float) this.mTouchSlop) && !this.mIsBeingDragged) {
                            this.mIsBeingDragged = true;
                            dispatchScrollState(1);
                            this.mInitialMotionY = y;
                        }
                    } else if (y - this.mInitialDownY > ((float) this.mTouchSlop) && !this.mIsBeingDragged) {
                        this.mIsBeingDragged = true;
                        dispatchScrollState(1);
                        this.mInitialMotionY = y;
                    }
                } else if (actionMasked != 3) {
                    if (actionMasked == 6) {
                        onSecondaryPointerUp(motionEvent);
                    }
                }
            }
            this.mIsBeingDragged = false;
            this.mActivePointerId = -1;
        } else {
            this.mActivePointerId = motionEvent.getPointerId(0);
            int findPointerIndex2 = motionEvent.findPointerIndex(this.mActivePointerId);
            if (findPointerIndex2 < 0) {
                return false;
            }
            this.mInitialDownY = motionEvent.getY(findPointerIndex2);
            if (getScrollY() != 0) {
                this.mIsBeingDragged = true;
                this.mInitialMotionY = this.mInitialDownY;
            } else {
                this.mIsBeingDragged = false;
            }
        }
        return this.mIsBeingDragged;
    }

    private boolean onHorizontalInterceptTouchEvent(MotionEvent motionEvent) {
        boolean z = false;
        if (!isTargetScrollToTop(1) && !isTargetScrollToBottom(1)) {
            return false;
        }
        if (isTargetScrollToTop(1) && !supportTopSpringBackMode()) {
            return false;
        }
        if (isTargetScrollToBottom(1) && !supportBottomSpringBackMode()) {
            return false;
        }
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    int i = this.mActivePointerId;
                    if (i == -1) {
                        Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                        return false;
                    }
                    int findPointerIndex = motionEvent.findPointerIndex(i);
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                        return false;
                    }
                    float x = motionEvent.getX(findPointerIndex);
                    if (isTargetScrollToBottom(1) && isTargetScrollToTop(1)) {
                        z = true;
                    }
                    if ((z || !isTargetScrollToTop(1)) && (!z || x <= this.mInitialDownX)) {
                        if (this.mInitialDownX - x > ((float) this.mTouchSlop) && !this.mIsBeingDragged) {
                            this.mIsBeingDragged = true;
                            dispatchScrollState(1);
                            this.mInitialMotionX = x;
                        }
                    } else if (x - this.mInitialDownX > ((float) this.mTouchSlop) && !this.mIsBeingDragged) {
                        this.mIsBeingDragged = true;
                        dispatchScrollState(1);
                        this.mInitialMotionX = x;
                    }
                } else if (actionMasked != 3) {
                    if (actionMasked == 6) {
                        onSecondaryPointerUp(motionEvent);
                    }
                }
            }
            this.mIsBeingDragged = false;
            this.mActivePointerId = -1;
        } else {
            this.mActivePointerId = motionEvent.getPointerId(0);
            int findPointerIndex2 = motionEvent.findPointerIndex(this.mActivePointerId);
            if (findPointerIndex2 < 0) {
                return false;
            }
            this.mInitialDownX = motionEvent.getX(findPointerIndex2);
            if (getScrollX() != 0) {
                this.mIsBeingDragged = true;
                this.mInitialMotionX = this.mInitialDownX;
            } else {
                this.mIsBeingDragged = false;
            }
        }
        return this.mIsBeingDragged;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (!isEnabled() || !this.mSpringBackEnable) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    public void internalRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public void requestDisallowParentInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        while (parent != null) {
            if (parent instanceof SpringBackLayout) {
                ((SpringBackLayout) parent).internalRequestDisallowInterceptTouchEvent(disallowIntercept);
            }
            parent = parent.getParent();
        }
    }

    public boolean onTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (!isEnabled() || this.mNestedFlingInProgress || this.mNestedScrollInProgress || (Build.VERSION.SDK_INT >= 21 && this.mTarget.isNestedScrollingEnabled())) {
            return false;
        }
        if (!this.mSpringScroller.isFinished() && actionMasked == 0) {
            this.mSpringScroller.forceStop();
        }
        if (isTargetScrollOrientation(2)) {
            return onVerticalTouchEvent(motionEvent);
        }
        if (isTargetScrollOrientation(1)) {
            return onHorizontalTouchEvent(motionEvent);
        }
        return false;
    }

    private boolean onHorizontalTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (!isTargetScrollToTop(1) && !isTargetScrollToBottom(1)) {
            return false;
        }
        if (isTargetScrollToTop(1) && isTargetScrollToBottom(1)) {
            return onScrollEvent(motionEvent, actionMasked, 1);
        }
        if (isTargetScrollToBottom(1)) {
            return onScrollUpEvent(motionEvent, actionMasked, 1);
        }
        return onScrollDownEvent(motionEvent, actionMasked, 1);
    }

    private boolean onVerticalTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (!isTargetScrollToTop(2) && !isTargetScrollToBottom(2)) {
            return false;
        }
        if (isTargetScrollToTop(2) && isTargetScrollToBottom(2)) {
            return onScrollEvent(motionEvent, actionMasked, 2);
        }
        if (isTargetScrollToBottom(2)) {
            return onScrollUpEvent(motionEvent, actionMasked, 2);
        }
        return onScrollDownEvent(motionEvent, actionMasked, 2);
    }

    private boolean onScrollEvent(MotionEvent motionEvent, int actionMasked, int i2) {
        float f;
        float f2;
        int i3;
        if (actionMasked == 0) {
            this.mActivePointerId = motionEvent.getPointerId(0);
            checkScrollStart(i2);
        } else if (actionMasked != 1) {
            if (actionMasked == 2) {
                int findPointerIndex = motionEvent.findPointerIndex(this.mActivePointerId);
                if (findPointerIndex < 0) {
                    Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                    return false;
                } else if (this.mIsBeingDragged) {
                    if (i2 == 2) {
                        float y = motionEvent.getY(findPointerIndex);
                        f = Math.signum(y - this.mInitialMotionY);
                        f2 = obtainSpringBackDistance(y - this.mInitialMotionY, i2);
                    } else {
                        float x = motionEvent.getX(findPointerIndex);
                        f = Math.signum(x - this.mInitialMotionX);
                        f2 = obtainSpringBackDistance(x - this.mInitialMotionX, i2);
                    }
                    requestDisallowParentInterceptTouchEvent(true);
                    moveTarget(f * f2, i2);
                }
            } else if (actionMasked == 3) {
                return false;
            } else {
                if (actionMasked == 5) {
                    int findPointerIndex2 = motionEvent.findPointerIndex(this.mActivePointerId);
                    if (findPointerIndex2 < 0) {
                        Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid active pointer id.");
                        return false;
                    }
                    if (i2 == 2) {
                        float y2 = motionEvent.getY(findPointerIndex2) - this.mInitialDownY;
                        i3 = motionEvent.getActionIndex();
                        if (i3 < 0) {
                            Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                            return false;
                        }
                        this.mInitialDownY = motionEvent.getY(i3) - y2;
                        this.mInitialMotionY = this.mInitialDownY;
                    } else {
                        float x2 = motionEvent.getX(findPointerIndex2) - this.mInitialDownX;
                        i3 = motionEvent.getActionIndex();
                        if (i3 < 0) {
                            Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                            return false;
                        }
                        this.mInitialDownX = motionEvent.getX(i3) - x2;
                        this.mInitialMotionX = this.mInitialDownX;
                    }
                    this.mActivePointerId = motionEvent.getPointerId(i3);
                } else if (actionMasked == 6) {
                    onSecondaryPointerUp(motionEvent);
                }
            }
        } else if (motionEvent.findPointerIndex(this.mActivePointerId) < 0) {
            Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.");
            return false;
        } else {
            if (this.mIsBeingDragged) {
                this.mIsBeingDragged = false;
                springBack(i2);
            }
            this.mActivePointerId = -1;
            return false;
        }
        return true;
    }

    private void checkVerticalScrollStart() {
        if (getScrollY() != 0) {
            this.mIsBeingDragged = true;
            float obtainTouchDistance = obtainTouchDistance((float) Math.abs(getScrollY()), 2);
            if (getScrollY() < 0) {
                this.mInitialDownY -= obtainTouchDistance;
            } else {
                this.mInitialDownY += obtainTouchDistance;
            }
            this.mInitialMotionY = this.mInitialDownY;
            return;
        }
        this.mIsBeingDragged = false;
    }

    private void checkScrollStart(int i) {
        if (i == 2) {
            checkVerticalScrollStart();
        } else {
            checkHorizontalScrollStart();
        }
    }

    private void checkHorizontalScrollStart() {
        if (getScrollX() != 0) {
            this.mIsBeingDragged = true;
            float obtainTouchDistance = obtainTouchDistance((float) Math.abs(getScrollX()), 2);
            if (getScrollX() < 0) {
                this.mInitialDownX -= obtainTouchDistance;
            } else {
                this.mInitialDownX += obtainTouchDistance;
            }
            this.mInitialMotionX = this.mInitialDownX;
            return;
        }
        this.mIsBeingDragged = false;
    }

    private boolean onScrollDownEvent(MotionEvent motionEvent, int i, int i2) {
        float f;
        float f2;
        int i3;
        if (i != 0) {
            if (i != 1) {
                if (i == 2) {
                    int findPointerIndex = motionEvent.findPointerIndex(this.mActivePointerId);
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                        return false;
                    } else if (this.mIsBeingDragged) {
                        if (i2 == 2) {
                            float y = motionEvent.getY(findPointerIndex);
                            f = Math.signum(y - this.mInitialMotionY);
                            f2 = obtainSpringBackDistance(y - this.mInitialMotionY, i2);
                        } else {
                            float x = motionEvent.getX(findPointerIndex);
                            f = Math.signum(x - this.mInitialMotionX);
                            f2 = obtainSpringBackDistance(x - this.mInitialMotionX, i2);
                        }
                        float f3 = f * f2;
                        if (f3 > 0.0f) {
                            requestDisallowParentInterceptTouchEvent(true);
                            moveTarget(f3, i2);
                        } else {
                            moveTarget(0.0f, i2);
                            return false;
                        }
                    }
                } else if (i != 3) {
                    if (i == 5) {
                        int findPointerIndex2 = motionEvent.findPointerIndex(this.mActivePointerId);
                        if (findPointerIndex2 < 0) {
                            Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid active pointer id.");
                            return false;
                        }
                        if (i2 == 2) {
                            float y2 = motionEvent.getY(findPointerIndex2) - this.mInitialDownY;
                            i3 = motionEvent.getActionIndex();
                            if (i3 < 0) {
                                Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                                return false;
                            }
                            this.mInitialDownY = motionEvent.getY(i3) - y2;
                            this.mInitialMotionY = this.mInitialDownY;
                        } else {
                            float x2 = motionEvent.getX(findPointerIndex2) - this.mInitialDownX;
                            i3 = motionEvent.getActionIndex();
                            if (i3 < 0) {
                                Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                                return false;
                            }
                            this.mInitialDownX = motionEvent.getX(i3) - x2;
                            this.mInitialMotionX = this.mInitialDownX;
                        }
                        this.mActivePointerId = motionEvent.getPointerId(i3);
                    } else if (i == 6) {
                        onSecondaryPointerUp(motionEvent);
                    }
                }
            }
            if (motionEvent.findPointerIndex(this.mActivePointerId) < 0) {
                Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.");
                return false;
            }
            if (this.mIsBeingDragged) {
                this.mIsBeingDragged = false;
                springBack(i2);
            }
            this.mActivePointerId = -1;
            return false;
        }
        this.mActivePointerId = motionEvent.getPointerId(0);
        checkScrollStart(i2);
        return true;
    }

    private void moveTarget(float delta, int axes) {
        if (axes == 2) {
            scrollTo(0, (int) (-delta));
        } else {
            scrollTo((int) (-delta), 0);
        }
    }

    private void springBack(int axes) {
        springBack(0.0f, axes, true);
    }

    private void springBack(float velocity, int axes, boolean z) {
        OnSpringListener onSpringListener = this.mOnSpringListener;
        if (onSpringListener == null || !onSpringListener.onSpringBack()) {
            this.mSpringScroller.forceStop();
            this.mSpringScroller.scrollByFling((float) getScrollX(), 0.0f, (float) getScrollY(), 0.0f, velocity, axes, false);
            dispatchScrollState(2);
            if (z) {
                postInvalidateOnAnimation();
            }
        }
    }

    private boolean onScrollUpEvent(MotionEvent motionEvent, int actionMasked, int axes) {
        float f;
        float f2;
        int i3;
        if (actionMasked != 0) {
            if (actionMasked != 1) {
                if (actionMasked == 2) {
                    int findPointerIndex = motionEvent.findPointerIndex(this.mActivePointerId);
                    if (findPointerIndex < 0) {
                        Log.e(TAG, "Got ACTION_MOVE event but have an invalid active pointer id.");
                        return false;
                    } else if (this.mIsBeingDragged) {
                        if (axes == 2) {
                            float y = motionEvent.getY(findPointerIndex);
                            f = Math.signum(this.mInitialMotionY - y);
                            f2 = obtainSpringBackDistance(this.mInitialMotionY - y, axes);
                        } else {
                            float x = motionEvent.getX(findPointerIndex);
                            f = Math.signum(this.mInitialMotionX - x);
                            f2 = obtainSpringBackDistance(this.mInitialMotionX - x, axes);
                        }
                        float f3 = f * f2;
                        if (f3 > 0.0f) {
                            requestDisallowParentInterceptTouchEvent(true);
                            moveTarget(-f3, axes);
                        } else {
                            moveTarget(0.0f, axes);
                            return false;
                        }
                    }
                } else if (actionMasked != 3) {
                    if (actionMasked == 5) {
                        int findPointerIndex2 = motionEvent.findPointerIndex(this.mActivePointerId);
                        if (findPointerIndex2 < 0) {
                            Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid active pointer id.");
                            return false;
                        }
                        if (axes == 2) {
                            float y2 = motionEvent.getY(findPointerIndex2) - this.mInitialDownY;
                            i3 = motionEvent.getActionIndex();
                            if (i3 < 0) {
                                Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                                return false;
                            }
                            this.mInitialDownY = motionEvent.getY(i3) - y2;
                            this.mInitialMotionY = this.mInitialDownY;
                        } else {
                            float x2 = motionEvent.getX(findPointerIndex2) - this.mInitialDownX;
                            i3 = motionEvent.getActionIndex();
                            if (i3 < 0) {
                                Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                                return false;
                            }
                            this.mInitialDownX = motionEvent.getX(i3) - x2;
                            this.mInitialMotionX = this.mInitialDownX;
                        }
                        this.mActivePointerId = motionEvent.getPointerId(i3);
                    } else if (actionMasked == 6) {
                        onSecondaryPointerUp(motionEvent);
                    }
                }
            }
            if (motionEvent.findPointerIndex(this.mActivePointerId) < 0) {
                Log.e(TAG, "Got ACTION_UP event but don't have an active pointer id.");
                return false;
            }
            if (this.mIsBeingDragged) {
                this.mIsBeingDragged = false;
                springBack(axes);
            }
            this.mActivePointerId = -1;
            return false;
        }
        this.mActivePointerId = motionEvent.getPointerId(0);
        checkScrollStart(axes);
        return true;
    }

    private void onSecondaryPointerUp(MotionEvent motionEvent) {
        int actionIndex = motionEvent.getActionIndex();
        if (motionEvent.getPointerId(actionIndex) == this.mActivePointerId) {
            this.mActivePointerId = motionEvent.getPointerId(actionIndex == 0 ? 1 : 0);
        }
    }

    private float obtainSpringBackDistance(float f, int i) {
        return obtainDampingDistance(Math.min(Math.abs(f) / ((float) (i == 2 ? this.mScreenHeight : this.mScreenWith)), 1.0f), i);
    }

    private float obtainMaxSpringBackDistance(int i) {
        return obtainDampingDistance(1.0f, i);
    }

    private float obtainDampingDistance(float f, int i) {
        int i2 = i == 2 ? this.mScreenHeight : this.mScreenWith;
        double min = (double) Math.min(f, 1.0f);
        return ((float) (((Math.pow(min, 3.0d) / 3.0d) - Math.pow(min, 2.0d)) + min)) * ((float) i2);
    }

    private float obtainTouchDistance(float f, int i) {
        int i2 = i == 2 ? this.mScreenHeight : this.mScreenWith;
        double d = (double) i2;
        return (float) (d - (Math.pow(d, 0.6666666666666666d) * Math.pow((double) (((float) i2) - (f * 3.0f)), 0.3333333333333333d)));
    }

    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        int i6 = 0;
        boolean isVertical = this.mNestedScrollAxes == 2;
        int i7 = isVertical ? dyConsumed : dxConsumed;
        int i8 = isVertical ? consumed[1] : consumed[0];
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, this.mParentOffsetInWindow, type, consumed);
        if (this.mSpringBackEnable) {
            int i9 = (isVertical ? consumed[1] : consumed[0]) - i8;
            int i10 = isVertical ? dyUnconsumed - i9 : dxUnconsumed - i9;
            if (i10 != 0) {
                i6 = i10;
            }
            int i11 = isVertical ? 2 : 1;
            if (i6 >= 0 || !isTargetScrollToTop(i11) || !supportTopSpringBackMode()) {
                if (i6 > 0 && isTargetScrollToBottom(i11) && supportBottomSpringBackMode()) {
                    if (type != 0) {
                        float obtainMaxSpringBackDistance = obtainMaxSpringBackDistance(i11);
                        if (this.mVelocityY != 0.0f || this.mVelocityX != 0.0f) {
                            this.mScrollByFling = true;
                            if (i7 != 0 && ((float) i6) <= obtainMaxSpringBackDistance) {
                                this.mSpringScroller.setFirstStep(i6);
                            }
                            dispatchScrollState(2);
                        } else if (this.mTotalScrollBottomUnconsumed == 0.0f) {
                            float f = obtainMaxSpringBackDistance - this.mTotalFlingUnconsumed;
                            if (this.consumeNestFlingCounter < 4) {
                                if (f <= ((float) Math.abs(i6))) {
                                    this.mTotalFlingUnconsumed += f;
                                    consumed[1] = (int) (((float) consumed[1]) + f);
                                } else {
                                    this.mTotalFlingUnconsumed += (float) Math.abs(i6);
                                    consumed[1] = consumed[1] + i10;
                                }
                                dispatchScrollState(2);
                                moveTarget(-obtainSpringBackDistance(this.mTotalFlingUnconsumed, i11), i11);
                                this.consumeNestFlingCounter++;
                            }
                        }
                    } else if (this.mSpringScroller.isFinished()) {
                        this.mTotalScrollBottomUnconsumed += (float) Math.abs(i6);
                        dispatchScrollState(1);
                        moveTarget(-obtainSpringBackDistance(this.mTotalScrollBottomUnconsumed, i11), i11);
                        consumed[1] = consumed[1] + i10;
                    }
                }
            } else if (type != 0) {
                float obtainMaxSpringBackDistance2 = obtainMaxSpringBackDistance(i11);
                if (this.mVelocityY != 0.0f || this.mVelocityX != 0.0f) {
                    this.mScrollByFling = true;
                    if (i7 != 0 && ((float) (-i6)) <= obtainMaxSpringBackDistance2) {
                        this.mSpringScroller.setFirstStep(i6);
                    }
                    dispatchScrollState(2);
                } else if (this.mTotalScrollTopUnconsumed == 0.0f) {
                    float f2 = obtainMaxSpringBackDistance2 - this.mTotalFlingUnconsumed;
                    if (this.consumeNestFlingCounter < 4) {
                        if (f2 <= ((float) Math.abs(i6))) {
                            this.mTotalFlingUnconsumed += f2;
                            consumed[1] = (int) (((float) consumed[1]) + f2);
                        } else {
                            this.mTotalFlingUnconsumed += (float) Math.abs(i6);
                            consumed[1] = consumed[1] + i10;
                        }
                        dispatchScrollState(2);
                        moveTarget(obtainSpringBackDistance(this.mTotalFlingUnconsumed, i11), i11);
                        this.consumeNestFlingCounter++;
                    }
                }
            } else if (this.mSpringScroller.isFinished()) {
                this.mTotalScrollTopUnconsumed += (float) Math.abs(i6);
                dispatchScrollState(1);
                moveTarget(obtainSpringBackDistance(this.mTotalScrollTopUnconsumed, i11), i11);
                consumed[1] = consumed[1] + i10;
            }
        }
    }

    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, this.mNestedScrollingV2ConsumedCompat);
    }

    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, ViewCompat.TYPE_TOUCH, this.mNestedScrollingV2ConsumedCompat);
    }

    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        if ((axes & ViewCompat.SCROLL_AXIS_VERTICAL) == 0){
            return false;
        }
        if (this.mSpringBackEnable) {
            this.mNestedScrollAxes = axes;
            int i3 = 2;
            boolean z = this.mNestedScrollAxes == 2;
            if (!z) {
                i3 = 1;
            }
            if ((i3 & this.mOriginScrollOrientation) == 0 || !onStartNestedScroll(child, child, axes)) {
                return false;
            }
            float scrollY = (float) (z ? getScrollY() : getScrollX());
            if (!(type == 0 || scrollY == 0.0f || !(this.mTarget instanceof NestedScrollView))) {
                return false;
            }
        }
        if (this.mNestedScrollingChildHelper.startNestedScroll(axes, type)) {
        }
        return true;
    }

    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
        return isEnabled() && (nestedScrollAxes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        if (this.mSpringBackEnable) {
            int i3 = 2;
            boolean z = this.mNestedScrollAxes == 2;
            if (!z) {
                i3 = 1;
            }
            float scrollY = (float) (z ? getScrollY() : getScrollX());
            if (type != 0) {
                if (scrollY == 0.0f) {
                    this.mTotalFlingUnconsumed = 0.0f;
                } else {
                    this.mTotalFlingUnconsumed = obtainTouchDistance(Math.abs(scrollY), i3);
                }
                this.mNestedFlingInProgress = true;
                this.consumeNestFlingCounter = 0;
            } else {
                if (scrollY == 0.0f) {
                    this.mTotalScrollTopUnconsumed = 0.0f;
                    this.mTotalScrollBottomUnconsumed = 0.0f;
                } else if (scrollY < 0.0f) {
                    this.mTotalScrollTopUnconsumed = obtainTouchDistance(Math.abs(scrollY), i3);
                    this.mTotalScrollBottomUnconsumed = 0.0f;
                } else {
                    this.mTotalScrollTopUnconsumed = 0.0f;
                    this.mTotalScrollBottomUnconsumed = obtainTouchDistance(Math.abs(scrollY), i3);
                }
                this.mNestedScrollInProgress = true;
            }
            this.mVelocityY = 0.0f;
            this.mVelocityX = 0.0f;
            this.mScrollByFling = false;
            this.mSpringScroller.forceStop();
        }
        onNestedScrollAccepted(child, target, axes);
    }

    @SuppressLint("WrongConstant")
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes) {
        this.mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        startNestedScroll(axes & 2);
    }

    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (this.mSpringBackEnable) {
            if (this.mNestedScrollAxes == 2) {
                onNestedPreScroll(dy, consumed, type);
            } else {
                onNestedPreScroll(dx, consumed, type);
            }
        }
        int[] iArr2 = this.mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], iArr2, (int[]) null, type)) {
            consumed[0] = consumed[0] + iArr2[0];
            consumed[1] = consumed[1] + iArr2[1];
        }
    }

    private void onNestedPreScroll(int delta, @NonNull int[] consumed, int type) {
        boolean isVertical = this.mNestedScrollAxes == 2;
        int axes = isVertical ? 2 : 1;
        int abs = Math.abs(isVertical ? getScrollY() : getScrollX());
        float f = 0.0f;
        if (type == 0) {
            if (delta > 0) {
                float f2 = this.mTotalScrollTopUnconsumed;
                if (f2 > 0.0f) {
                    float f3 = (float) delta;
                    if (f3 > f2) {
                        consumeDelta((int) f2, consumed, axes);
                        this.mTotalScrollTopUnconsumed = 0.0f;
                    } else {
                        this.mTotalScrollTopUnconsumed = f2 - f3;
                        consumeDelta(delta, consumed, axes);
                    }
                    dispatchScrollState(1);
                    moveTarget(obtainSpringBackDistance(this.mTotalScrollTopUnconsumed, axes), axes);
                    return;
                }
            }
            if (delta < 0) {
                float f4 = this.mTotalScrollBottomUnconsumed;
                if ((-f4) < 0.0f) {
                    float f5 = (float) delta;
                    if (f5 < (-f4)) {
                        consumeDelta((int) f4, consumed, axes);
                        this.mTotalScrollBottomUnconsumed = 0.0f;
                    } else {
                        this.mTotalScrollBottomUnconsumed = f4 + f5;
                        consumeDelta(delta, consumed, axes);
                    }
                    dispatchScrollState(1);
                    moveTarget(-obtainSpringBackDistance(this.mTotalScrollBottomUnconsumed, axes), axes);
                    return;
                }
                return;
            }
            return;
        }
        float f6 = axes == 2 ? this.mVelocityY : this.mVelocityX;
        if (delta > 0) {
            float f7 = this.mTotalScrollTopUnconsumed;
            if (f7 > 0.0f) {
                if (f6 > 2000.0f) {
                    float obtainSpringBackDistance = obtainSpringBackDistance(f7, axes);
                    float f8 = (float) delta;
                    if (f8 > obtainSpringBackDistance) {
                        consumeDelta((int) obtainSpringBackDistance, consumed, axes);
                        this.mTotalScrollTopUnconsumed = 0.0f;
                    } else {
                        consumeDelta(delta, consumed, axes);
                        f = obtainSpringBackDistance - f8;
                        this.mTotalScrollTopUnconsumed = obtainTouchDistance(f, axes);
                    }
                    moveTarget(f, axes);
                    dispatchScrollState(1);
                    return;
                }
                if (!this.mScrollByFling) {
                    this.mScrollByFling = true;
                    springBack(f6, axes, false);
                }
                if (this.mSpringScroller.computeScrollOffset()) {
                    scrollTo(this.mSpringScroller.getCurrX(), this.mSpringScroller.getCurrY());
                    this.mTotalScrollTopUnconsumed = obtainTouchDistance((float) abs, axes);
                } else {
                    this.mTotalScrollTopUnconsumed = 0.0f;
                }
                consumeDelta(delta, consumed, axes);
                return;
            }
        }
        if (delta < 0) {
            float f9 = this.mTotalScrollBottomUnconsumed;
            if ((-f9) < 0.0f) {
                if (f6 < -2000.0f) {
                    float obtainSpringBackDistance2 = obtainSpringBackDistance(f9, axes);
                    float f10 = (float) delta;
                    if (f10 < (-obtainSpringBackDistance2)) {
                        consumeDelta((int) obtainSpringBackDistance2, consumed, axes);
                        this.mTotalScrollBottomUnconsumed = 0.0f;
                    } else {
                        consumeDelta(delta, consumed, axes);
                        f = obtainSpringBackDistance2 + f10;
                        this.mTotalScrollBottomUnconsumed = obtainTouchDistance(f, axes);
                    }
                    dispatchScrollState(1);
                    moveTarget(-f, axes);
                    return;
                }
                if (!this.mScrollByFling) {
                    this.mScrollByFling = true;
                    springBack(f6, axes, false);
                }
                if (this.mSpringScroller.computeScrollOffset()) {
                    scrollTo(this.mSpringScroller.getCurrX(), this.mSpringScroller.getCurrY());
                    this.mTotalScrollBottomUnconsumed = obtainTouchDistance((float) abs, axes);
                } else {
                    this.mTotalScrollBottomUnconsumed = 0.0f;
                }
                consumeDelta(delta, consumed, axes);
                return;
            }
        }
        if (delta == 0) {
            return;
        }
        if ((this.mTotalScrollBottomUnconsumed == 0.0f || this.mTotalScrollTopUnconsumed == 0.0f) && this.mScrollByFling && getScrollY() == 0) {
            consumeDelta(delta, consumed, axes);
        }
    }

    private void consumeDelta(int delta, @NonNull int[] consumed, int axes) {
        if (axes == 2) {
            consumed[1] = delta;
        } else {
            consumed[0] = delta;
        }
    }

    public void setNestedScrollingEnabled(boolean enabled) {
        this.mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    public boolean isNestedScrollingEnabled() {
        return this.mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    public void onStopNestedScroll(@NonNull View target, int type) {
        this.mNestedScrollingParentHelper.onStopNestedScroll(target, type);
        stopNestedScroll(type);
        if (this.mSpringBackEnable) {
            int i2 = 1;
            boolean z = this.mNestedScrollAxes == 2;
            if (z) {
                i2 = 2;
            }
            if (this.mNestedScrollInProgress) {
                this.mNestedScrollInProgress = false;
                float scrollY = (float) (z ? getScrollY() : getScrollX());
                if (!this.mNestedFlingInProgress && scrollY != 0.0f) {
                    springBack(i2);
                } else if (scrollY != 0.0f) {
                    dispatchScrollState(2);
                }
            } else if (this.mNestedFlingInProgress) {
                this.mNestedFlingInProgress = false;
                if (this.mScrollByFling) {
                    if (this.mSpringScroller.isFinished()) {
                        springBack(i2 == 2 ? this.mVelocityY : this.mVelocityX, i2, false);
                    }
                    postInvalidateOnAnimation();
                    return;
                }
                springBack(i2);
            }
        }
    }

    public void stopNestedScroll() {
        this.mNestedScrollingChildHelper.stopNestedScroll();
    }

    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
        this.mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed);
    }

    public boolean startNestedScroll(int axes, int type) {
        return this.mNestedScrollingChildHelper.startNestedScroll(axes, type);
    }

    public boolean startNestedScroll(int axes) {
        return this.mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    public void stopNestedScroll(int type) {
        this.mNestedScrollingChildHelper.stopNestedScroll(type);
    }

    public boolean hasNestedScrollingParent(int type) {
        return this.mNestedScrollingChildHelper.hasNestedScrollingParent(type);
    }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return this.mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow, int type) {
        return this.mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return this.mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return this.mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return this.mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    public void smoothScrollTo(int x, int y) {
        if (x - getScrollX() != 0 || y - getScrollY() != 0) {
            this.mSpringScroller.forceStop();
            this.mSpringScroller.scrollByFling((float) getScrollX(), (float) x, (float) getScrollY(), (float) y, 0.0f, 2, true);
            dispatchScrollState(2);
            postInvalidateOnAnimation();
        }
    }

    private void dispatchScrollState(int scrollState) {
        int oldScrollState = this.mScrollState;
        if (oldScrollState != scrollState) {
            this.mScrollState = scrollState;
            for (OnScrollListener onStateChanged : this.mOnScrollListeners) {
                onStateChanged.onStateChanged(oldScrollState, scrollState, this.mSpringScroller.isFinished());
            }
        }
    }

    public void addOnScrollListener(OnScrollListener onScrollListener) {
        this.mOnScrollListeners.add(onScrollListener);
    }

    public void removeOnScrollListener(OnScrollListener onScrollListener) {
        this.mOnScrollListeners.remove(onScrollListener);
    }

    public void setOnSpringListener(OnSpringListener onSpringListener) {
        this.mOnSpringListener = onSpringListener;
    }

    public boolean hasSpringListener() {
        return this.mOnSpringListener != null;
    }

    public boolean onNestedCurrentFling(float velocityX, float velocityY) {
        this.mVelocityX = velocityX;
        this.mVelocityY = velocityY;
        return true;
    }
}
