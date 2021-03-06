package net.z0kai.kkrefreshlayout;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.NestedScrollingChild;
import android.support.v4.view.NestedScrollingChildHelper;
import android.support.v4.view.NestedScrollingParent;
import android.support.v4.view.NestedScrollingParentHelper;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.FrameLayout;

import net.z0kai.kkrefreshlayout.vertical.DefaultFooterView;
import net.z0kai.kkrefreshlayout.vertical.DefaultHeaderView;

/**
 * Created by Z_0Kai on 16/9/29.
 */

public class KKRefreshLayout extends FrameLayout implements NestedScrollingParent,
        NestedScrollingChild {

    private final static String TAG = KKRefreshLayout.class.getSimpleName();
    private final static long MIN_REFRESH_TIME = 500;

    // for attributeSet
    private boolean isVertical;

    private boolean isRefreshing;
    private boolean isPageRefreshing; // isRefreshing would be true
    private boolean isLoadingMore;
    private boolean isRefreshEnable;
    private boolean isLoadMoreEnable;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];
    private boolean mNestedScrollInProgress;

    private int mTouchSlop;
    private float mTouchPos = -1;
    private float mCurrentPos;

    private View mTarget; // the target of the gesture
    private IHeaderView mHeaderView;
    private IFooterView mFooterView;
    private IPageView mPageView;

    private KKRefreshListener mListener;

    private float mOffset;
    private long mStartRefreshTime;
    private boolean isRefreshBeforeLayout;
    private Runnable mMoveBackRunnable = new Runnable() {
        @Override
        public void run() {
            smoothScrollBack(mOffset, 0);
        }
    };

    private boolean isFlingDown;
    private GestureDetector mGestureDetector;

    private int mOverScrollMode = OVER_SCROLL_ALWAYS;

    public KKRefreshLayout(Context context) {
        this(context, null);
    }

    public KKRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setNestedScrollingEnabled(true);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.KKRefreshLayout);
        isVertical = typedArray.getInt(R.styleable.KKRefreshLayout_rlOrientation, 1) == 1;// default vertical
        isRefreshEnable = typedArray.getBoolean(R.styleable.KKRefreshLayout_rlRefreshEnable, true);
        isLoadMoreEnable = typedArray.getBoolean(R.styleable.KKRefreshLayout_rlLoadMoreEnable, false);
        typedArray.recycle();
    }

    public void setHeaderView(IHeaderView headerView) {
        if (mHeaderView == null) {
            mHeaderView = headerView;
            addView(mHeaderView.getView());
        }
    }

    public void setFooterView(IFooterView footerView) {
        if (mFooterView == null) {
            mFooterView = footerView;
            addView(mFooterView.getView());
        }
    }

    public void setPageView(IPageView pageView) {
        if (mPageView == null) {
            mPageView = pageView;
            addView(mPageView.getView());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ensureHeaderView();
        ensureFooterView();
        ensureTarget();
        ensurePageView();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            return;
        }

        if (mTarget == null) {
            return;
        }
        layoutChildren();
        if (isRefreshBeforeLayout) {
            isRefreshBeforeLayout = false;
            if (isPageRefreshing) {
                startPageRefresh();
            } else {
                startRefresh();
            }
        }
    }

    public void startRefresh() {
        if (isRefreshing || !isRefreshEnable) {
            return;
        }
        if (getMeasuredWidth() == 0) {
            isRefreshBeforeLayout = true;
            return;
        }
        mStartRefreshTime = System.currentTimeMillis();
        isRefreshing = true;
        mHeaderView.startRefresh();

        ValueAnimator valueAnimator = ObjectAnimator.ofFloat(0, mHeaderView.getRefreshingSize()).setDuration(300);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (getContext() instanceof Activity && ((Activity) getContext()).isFinishing()) {
                    animation.cancel();
                    return;
                }
                float value = (float) animation.getAnimatedValue();
                mOffset = value;
                layoutChildren();
                if (value == mHeaderView.getRefreshingSize() && mListener != null) {
                    mListener.onRefresh();
                }
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setStartDelay(100);
        valueAnimator.start();
    }

    public void startPageRefresh() {
        if (isRefreshing) {
            return;
        }
        isPageRefreshing = true;
        if (getMeasuredWidth() == 0) {
            isRefreshBeforeLayout = true;
            return;
        }
        isRefreshing = true;
        if (mListener != null) {
            mListener.onRefresh();
        }
        if (mPageView != null) {
            mPageView.show();
        }
    }

    public void startLoadMore() {
        if (isLoadingMore || !isLoadMoreEnable) {
            return;
        }
        isLoadingMore = true;
        if (mListener != null) {
            mListener.onLoadMore();
        }
    }

    public void setRefreshEnable(boolean enable) {
        isRefreshEnable = enable;
    }

    public void setLoadMoreEnable(boolean enable) {
        isLoadMoreEnable = enable;
    }

    public void finishRefresh() {
        if (isRefreshing) {
            if (isPageRefreshing) {
                if (mPageView != null) {
                    mPageView.hide();
                }
                isRefreshing = false;
                isPageRefreshing = false;
                layoutChildren();
                if (mTarget != null) {
                    mTarget.requestLayout();
                }
            } else {
                long refreshTime = System.currentTimeMillis() - mStartRefreshTime;
                if (refreshTime < MIN_REFRESH_TIME) {
                    postDelayed(mMoveBackRunnable, MIN_REFRESH_TIME - refreshTime);
                } else {
                    mMoveBackRunnable.run();
                }
            }
        }
    }

    public void finishLoadMore() {
        if (isLoadingMore) {
            isLoadingMore = false;
            mOffset = 0;
            layoutChildren();
            if (mTarget != null) {
                mTarget.requestLayout();
            }
        }
    }

    public void setRefreshListener(KKRefreshListener listener) {
        mListener = listener;
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    public boolean isLoadingMore() {
        return isLoadingMore;
    }

    private void layoutChildren() {
        int offset = (int) mOffset;
        int left, right, top, bottom;

        if (mHeaderView != null) {
            View view = mHeaderView.getView();
            if (isVertical) {
                left = 0;
                top = -view.getMeasuredHeight() + offset;
            } else {
                left = -view.getMeasuredWidth() + offset;
                top = 0;
            }
            right = left + view.getMeasuredWidth();
            bottom = top + view.getMeasuredHeight();
            view.layout(left, top, right, bottom);
            mHeaderView.onScroll(offset);
        }

        if (mFooterView != null) {
            View view = mFooterView.getView();
            if (isVertical) {
                left = 0;
                top = getHeight() + offset;
            } else {
                left = getWidth() + offset;
                top = 0;
            }
            right = left + view.getMeasuredWidth();
            bottom = top + view.getMeasuredHeight();
            view.layout(left, top, right, bottom);
            mFooterView.onScroll(offset);
        }

        if (mTarget != null) {
            View view = mTarget;
            if (isVertical) {
                left = 0;
                right = left + getWidth();
                if (offset > 0) {
                    top = offset;
                    bottom = top + getHeight() - offset;
                } else {
                    top = 0;
                    bottom = top + getHeight() + offset;
                }
            } else {
                top = 0;
                bottom = top + getHeight();
                if (offset > 0) {
                    left = offset;
                    right = left + getWidth() - offset;
                } else {
                    left = 0;
                    right = left + getWidth() + offset;
                }
            }
            view.layout(left, top, right, bottom);
        }

        if (mPageView != null) {
            View view = mPageView.getView();
            if (isPageRefreshing) {
                view.layout(0, 0, getWidth(), getHeight());
            }
        }
    }

    private void ensureTarget() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!(child instanceof IHeaderView) && !(child instanceof IFooterView) && !(child instanceof IPageView)) {
                    mTarget = child;
                    mOverScrollMode = mTarget.getOverScrollMode();
                    bindEvent2Target();
                    break;
                }
            }
        }
    }

    private void ensurePageView() {
        if (mPageView == null) {
            mPageView = obtainPageView();
            if (mPageView != null) {
                addView(mPageView.getView());
            }
        }
    }

    private void ensureHeaderView() {
        if (mHeaderView == null) {
            IHeaderView headerView = obtainHeaderView();
            setHeaderView(headerView);
        }
    }

    private void ensureFooterView() {
        if (mFooterView == null) {
            IFooterView footerView = obtainFooterView();
            setFooterView(footerView);
        }
    }

    protected IPageView obtainPageView() {
        return null;
    }

    protected IHeaderView obtainHeaderView() {
        return new DefaultHeaderView(getContext());
    }

    protected IFooterView obtainFooterView() {
        return new DefaultFooterView(getContext());
    }

    private void bindEvent2Target() {
        if (mFooterView == null || mFooterView.autoLoadOnEndSize() <= 0) {
            return;
        }
        if (mTarget instanceof RecyclerView) {
            ((RecyclerView) mTarget).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    onScrollEnd(recyclerView, newState);
                }
            });
        } else if (mTarget instanceof AbsListView) {
            ((AbsListView) mTarget).setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int newState) {
                    onScrollEnd(view, newState);
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                }
            });
        }
        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (velocityY > 0 && isLoadMoreEnable) {
                    isFlingDown = true;
                    disableOverScrollMode();
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });
    }

    private void onScrollEnd(final View view, int newState) {
        if (newState == 0) {
            if (mOffset == 0 && isLoadMoreEnable && !canChildScrollDown()) {
                if (isFlingDown) {
                    ValueAnimator valueAnimator = ObjectAnimator.ofFloat(0, -mFooterView.autoLoadOnEndSize()).setDuration(500);
                    valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            if (!isLoadingMore || (float) animation.getAnimatedValue() <= -mFooterView.autoLoadOnEndSize()) {
                                resetOverScrollMode();
                            }
                            if (!isLoadingMore) {
                                return;
                            }
                            mOffset = (float) animation.getAnimatedValue();
                            layoutChildren();
                            if (view instanceof RecyclerView) {
                                if (isVertical) {
                                    ((RecyclerView) view).smoothScrollBy(0, (int) -mOffset);
                                } else {
                                    ((RecyclerView) view).smoothScrollBy((int) -mOffset, 0);
                                }
                            } else if (view instanceof AbsListView) {
                                ((AbsListView) view).smoothScrollBy((int) -mOffset, 0);
                            }
                        }
                    });
                    valueAnimator.setInterpolator(new DecelerateInterpolator());
                    valueAnimator.start();
                }
                isLoadingMore = true;
                mFooterView.showLoading();
                if (mListener != null) {
                    mListener.onLoadMore();
                }
                isFlingDown = false;
            }
        }
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    protected boolean canChildScrollUp() {
        return isVertical ? ViewCompat.canScrollVertically(mTarget, -1) : ViewCompat.canScrollHorizontally(mTarget, -1);
    }

    private boolean canChildScrollDown() {
        return isVertical ? ViewCompat.canScrollVertically(mTarget, 1) : ViewCompat.canScrollHorizontally(mTarget, 1);
    }

    private void finishSpinner(float overScrollTop) {
        if (overScrollTop > 0) {
            if (overScrollTop >= mHeaderView.getMinRefreshSize()) {
                if (mListener != null) {
                    mListener.onRefresh();
                }
                mStartRefreshTime = System.currentTimeMillis();
                mHeaderView.startRefresh();
                isRefreshing = true;
                smoothScrollBack(overScrollTop, mHeaderView.getRefreshingSize());
            } else {
                smoothScrollBack(overScrollTop, 0);
            }
        } else if (overScrollTop < 0 && mFooterView.getMinLoadMoreSize() > 0) {
            if (-overScrollTop >= mFooterView.getMinLoadMoreSize()) {
                mFooterView.showLoading();
                isLoadingMore = true;
                if (mListener != null) {
                    mListener.onLoadMore();
                }
                smoothScrollBack(overScrollTop, -mFooterView.getLoadingSize());
            } else {
                smoothScrollBack(overScrollTop, 0);
            }
        }
    }

    private void smoothScrollBack(final float fromOffset, float toOffset) {
        ValueAnimator valueAnimator = ObjectAnimator.ofFloat(fromOffset, toOffset).setDuration(300);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                mOffset = value;
                layoutChildren();
                if (value == 0) {
                    isRefreshing = false;
                    isLoadingMore = false;
                    mHeaderView.stopRefresh();
                }
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setStartDelay(100);
        valueAnimator.start();
    }

    private void offsetUp(int offset) {
        int size = isVertical ? getHeight() : getWidth();
        if (mOffset < size / 3) {
            offset /= 2;
        } else if (mOffset < size / 2) {
            offset /= 3;
        } else {
            offset /= 4;
        }
        mOffset += offset;
    }

    private void offsetDown(int offset) {
        int size = mFooterView.getMaxSize();
        if (mOffset < size / 3) {
            offset /= 2;
        } else if (mOffset < size / 2) {
            offset /= 3;
        } else {
            offset /= 4;
        }
        mOffset -= offset;
    }

    private void moveChild(int offset, int offsetConsumed) {
        resetOverScrollMode();
        // pull down
        if (offset < 0 && isRefreshEnable && !isLoadingMore && !canChildScrollUp()) {
            disableOverScrollMode();
            offsetUp(-offset);
            layoutChildren();
        }

        // pull up
        if (offset > 0 && isLoadMoreEnable && !isRefreshing && !canChildScrollDown()) {
            disableOverScrollMode();
            offsetDown(offset);
            if (-mOffset > mFooterView.getMaxSize()) {
                mOffset = -mFooterView.getMaxSize();
            }
            if (!isLoadingMore && mFooterView.getMinLoadMoreSize() <= 0) {
                mFooterView.showLoading();
                isLoadingMore = true;
                if (mListener != null) {
                    mListener.onLoadMore();
                }
            }
            layoutChildren();
        }

        // pull up, show no more
        if (offset > 0 && canChildScrollUp() && !isLoadMoreEnable) {
            disableOverScrollMode();
            mFooterView.showNoMore();
            offsetDown(offset);
            if (-mOffset > mFooterView.getMaxSize()) {
                mOffset = -mFooterView.getMaxSize();
            }
            layoutChildren();
        }

        // hide pull up item
        if (offsetConsumed < 0 && mOffset < 0) {
            disableOverScrollMode();
            offsetDown(offsetConsumed);
            if (mOffset > 0) {
                mOffset = 0;
            }
            layoutChildren();
        }

    }

    private void actionMove(int offset) {
        moveChild(offset, offset);

        // pull down, back
        if (offset > 0 && mOffset > 0) {
            if (offset > mOffset) {
                mOffset = 0;
            } else {
                mOffset -= offset;
            }
            layoutChildren();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {

        if (isRefreshing || mNestedScrollInProgress) {
            // Fail fast if we're not in a state where a swipe is possible
            return false;
        }

        if (mGestureDetector != null) {
            mGestureDetector.onTouchEvent(ev);
        }

        final int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTouchPos = isVertical ? ev.getY() : ev.getX();
                mCurrentPos = mTouchPos;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mTouchPos == -1) {
                    break;
                }
                float currentPos = isVertical ? ev.getY() : ev.getX();
                int offset = (int) (currentPos - mTouchPos);
                if (mOffset != 0) {

                } else if (Math.abs(offset) < mTouchSlop) {
                    break;
                } else if (offset > 0 && !canChildScrollUp() && isRefreshEnable) {
//                    return true;
                } else if (offset < 0 && !canChildScrollDown()) {
//                    return true;
                } else if (mOffset != 0) {
//                    return true;
                } else {
                    mTouchPos = currentPos;
                    break;
                }
                mCurrentPos = mTouchPos = currentPos;
                offset = -offset;
                actionMove(offset);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                onStopNestedScroll(mTarget);
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }


    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isRefreshing || mNestedScrollInProgress || canChildScrollUp()) {
            // Fail fast if we're not in a state where a swipe is possible
            return super.onTouchEvent(ev);
        }

        final int action = MotionEventCompat.getActionMasked(ev);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTouchPos = isVertical ? ev.getY() : ev.getX();
                mCurrentPos = mTouchPos;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mTouchPos == -1) {
                    break;
                }
                mCurrentPos = isVertical ? ev.getY() : ev.getX();
                int offset = -(int) (mCurrentPos - mTouchPos);
                mTouchPos = mCurrentPos;
                actionMove(offset);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                onStopNestedScroll(mTarget);
                break;
        }

        return true;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean b) {
        // if this is a List < L or another view that doesn't support nested
        // scrolling, ignore this request so that the vertical scroll event
        // isn't stolen
        if ((android.os.Build.VERSION.SDK_INT < 21 && mTarget instanceof AbsListView)
                || (mTarget != null && !ViewCompat.isNestedScrollingEnabled(mTarget))
                || !isRefreshEnable) {
            // Nope.
        } else {
            super.requestDisallowInterceptTouchEvent(b);
        }
    }

    // NestedScrollingParent
    @Override
    public boolean onStartNestedScroll(View child, View target, int nestedScrollAxes) {
//        return super.onStartNestedScroll(child, target, nestedScrollAxes);
        return isEnabled()
                && !isRefreshing
                && (isRefreshEnable) // and then load more will run on touch
                && (nestedScrollAxes & (isVertical ? ViewCompat.SCROLL_AXIS_VERTICAL : ViewCompat.SCROLL_AXIS_HORIZONTAL)) != 0;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes) {
        // Reset the counter of how much leftover scroll needs to be consumed.
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        // Dispatch up to the nested parent
        startNestedScroll(axes & (isVertical ? ViewCompat.SCROLL_AXIS_VERTICAL : ViewCompat.SCROLL_AXIS_HORIZONTAL));
        mNestedScrollInProgress = true;
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        // If we are in the middle of consuming, a scroll, then we want to move the spinner back up
        // before allowing the list to scroll
        int offset = isVertical ? dy : dx;
        if (offset > 0 && mOffset > 0) {
            if (offset > mOffset) {
                consumed[isVertical ? 1 : 0] = offset - (int) mOffset;
                mOffset = 0;
            } else {
                mOffset -= offset;
                consumed[isVertical ? 1 : 0] = offset;
            }
            layoutChildren();
        }

        // Now let our nested parent consume the leftovers
        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    @Override
    public void onStopNestedScroll(View target) {
        mNestedScrollingParentHelper.onStopNestedScroll(target);
        mNestedScrollInProgress = false;
        // Finish the spinner for nested scrolling if we ever consumed any
        // unconsumed nested scroll
        finishSpinner(mOffset);
        if (mOffset <= 0) {
            mHeaderView.stopRefresh();
        }
        if (mOffset < 0 && !isLoadMoreEnable) {
            smoothScrollBack(mOffset, 0);
        }
        // Dispatch up our nested parent
        stopNestedScroll();
        mTouchPos = -1;
        resetOverScrollMode();
    }

    @Override
    public void onNestedScroll(final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        // Dispatch up to the nested parent first
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, mParentOffsetInWindow);

        // This is a bit of a hack. Nested scrolling works from the bottom up, and as we are
        // sometimes between two nested scrolling views, we need a way to be able to know when any
        // nested scrolling parent has stopped handling events. We do that by using the
        // 'offset in window 'functionality to see if we have been moved from the event.
        // This is a decent indication of whether we should take over the event stream or not.
        int offset = isVertical ? (dyUnconsumed + mParentOffsetInWindow[1]) : (dxUnconsumed + mParentOffsetInWindow[0]);
        moveChild(offset, isVertical ? dyConsumed : dxConsumed);
    }

    // NestedScrollingChild
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return mNestedScrollingChildHelper.startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        mNestedScrollingChildHelper.stopNestedScroll();
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return mNestedScrollingChildHelper.hasNestedScrollingParent();
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
                                        int dyUnconsumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX,
                                    float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY,
                                 boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        if (velocityY > 0 && isLoadMoreEnable) {
            isFlingDown = true;
            disableOverScrollMode();
        }
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    private void disableOverScrollMode() {
        if (mTarget != null) {
            mTarget.setOverScrollMode(OVER_SCROLL_NEVER);
        }
    }

    private void resetOverScrollMode() {
        if (mTarget != null && !isFlingDown && mOffset == 0) {
            mTarget.setOverScrollMode(mOverScrollMode);
        }
    }

}
