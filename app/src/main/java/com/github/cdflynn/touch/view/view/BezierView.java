package com.github.cdflynn.touch.view.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.github.cdflynn.touch.R;
import com.github.cdflynn.touch.processing.OnTouchElevator;
import com.github.cdflynn.touch.processing.TouchProcessor;
import com.github.cdflynn.touch.processing.TouchState;
import com.github.cdflynn.touch.processing.TouchStateTracker;
import com.github.cdflynn.touch.util.Geometry;
import com.github.cdflynn.touch.view.interfaces.MotionEventListener;
import com.github.cdflynn.touch.view.interfaces.MotionEventStream;

public class BezierView extends View implements MotionEventStream {

    private static final int ADD_RADIUS = 100;

    protected float mLastDownX = TouchState.NONE;
    protected float mLastDownY = TouchState.NONE;
    private MotionEventListener mListener = NO_OP_LISTENER;
    private OnTouchElevator mOnTouchElevator;
    private Paint mPaint;
    private Path mPath;
    private int mScaledTouchSlop;
    private TouchProcessor mTouchProcessor;
    protected TouchState mState;

    public BezierView(Context context) {
        super(context);
        init(context);
    }

    public BezierView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BezierView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public BezierView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        mOnTouchElevator = new OnTouchElevator();
        mState = new TouchState();
        mTouchProcessor = new TouchStateTracker(mState);
        mPaint = createPaint();
        mPath = new Path();
        mScaledTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop() + ADD_RADIUS;
    }

    /**
     * Override the default touch processor.
     */
    protected final void setTouchProcessor(TouchProcessor t) {
        mTouchProcessor = t;
    }

    protected final void setColor(@ColorInt int color) {
        mPaint.setColor(color);
    }

    @Override
    public void setMotionEventListener(MotionEventListener listener) {
        mListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mOnTouchElevator.onTouchEvent(this, event);
        mTouchProcessor.onTouchEvent(this, event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mListener.onMotionEvent(event);
                break;
            case MotionEvent.ACTION_DOWN:
                mLastDownX = event.getX();
                mLastDownY = event.getY();
                mListener.onMotionEvent(event);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mState.distance > mScaledTouchSlop) {
                    mListener.onMotionEvent(event);
                }
                break;
        }
        calculatePath(mState);
        invalidate();
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw a circle around the last known ACTION_DOWN event, if there was one.
        if ((mState.xDown == TouchState.NONE && mLastDownX != TouchState.NONE)
                || (mState.distance != TouchState.NONE && mState.distance < mScaledTouchSlop)) {
            canvas.drawCircle(mLastDownX, mLastDownY, mScaledTouchSlop, mPaint);
        }

        canvas.save();
        canvas.rotate(angle(mState), mState.xCurrent, mState.yCurrent);
        canvas.drawPath(mPath, mPaint);
        canvas.restore();
    }

    private Paint createPaint() {
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setAntiAlias(true);
        p.setStrokeWidth(4f);
        return p;
    }

    /**
     * The change in the x value that is required to move from the current touch point to
     * the tangent.
     */
    private float x(TouchState s) {
        final float currToTan = (float) Math.sqrt((s.distance * s.distance) - (mScaledTouchSlop * mScaledTouchSlop));
        return currToTan * (currToTan / s.distance);
    }

    /**
     * The change in the y value that is required to move from the current touch point to
     * the tangent.
     */
    private float y(TouchState s) {
        final float currToTan = (float) Math.sqrt((s.distance * s.distance) - (mScaledTouchSlop * mScaledTouchSlop));
        return currToTan * (mScaledTouchSlop / s.distance);
    }

    /**
     * Angle between the current touch coordinates and the down coordinates
     */
    private float angle(TouchState s) {
        return (float) Math.toDegrees(Math.atan2(s.yDown - s.yCurrent, s.xDown - s.xCurrent));
    }

    /**
     * Find the angle in degrees between two tangent points
     *
     * @param tan1X the x coordinate of the first tangent point
     * @param tan1Y the y coordinate of the first tangent point
     * @param tan2X the x coordinate of the second tangent point
     * @param tan2Y the y coordinate of the second tangent point
     * @return the major sweep angle between the two tangent point, in degrees.
     */
    private float sweep(float tan1X, float tan1Y, float tan2X, float tan2Y) {
        final float minorSweep = (float) Math.toDegrees(
                2 * (Math.asin(.5 * Geometry.distance(tan1X, tan1Y,
                        tan2X, tan2Y) / mScaledTouchSlop)));

        return 360 - minorSweep;
    }

    /**
     * Use the current touch state values to re-plot the path.
     */
    protected final void calculatePath(TouchState s) {
        mPath.reset();
        if (s.yCurrent == TouchState.NONE || s.xCurrent == TouchState.NONE || s.distance == TouchState.NONE) {
            return;
        }
        /* center around down point */
        final float xMod = x(s);
        final float yMod = y(s);
        mPath.moveTo(s.xCurrent, s.yCurrent);
        final float controlPointX = s.xCurrent + s.distance * .66f;
        final float controlPointY = s.yCurrent + yMod / 3;
        mPath.quadTo(controlPointX, controlPointY, s.xCurrent + xMod, s.yCurrent + yMod);

        final float sweep = sweep(s.xCurrent + xMod, s.yCurrent + yMod,
                s.xCurrent + xMod, s.yCurrent - yMod);

        mPath.arcTo(s.xCurrent + s.distance - mScaledTouchSlop,
                s.yCurrent - mScaledTouchSlop,
                s.xCurrent + s.distance + mScaledTouchSlop,
                s.yCurrent + mScaledTouchSlop,
                sweep / 2,
                -sweep,
                false);

        final float controlPointXMirror = s.xCurrent + s.distance * .66f;
        final float controlPointYMirror = s.yCurrent - yMod / 3;
        mPath.moveTo(s.xCurrent, s.yCurrent);
        mPath.quadTo(controlPointXMirror, controlPointYMirror, s.xCurrent + xMod, s.yCurrent - yMod);
    }

    private static final MotionEventListener NO_OP_LISTENER = new MotionEventListener() {
        @Override
        public void onMotionEvent(MotionEvent e) {
            // do nothing
        }
    };
}
