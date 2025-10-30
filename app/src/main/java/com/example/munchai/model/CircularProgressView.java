package com.example.munchai.model;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.example.munchai.R;

public class CircularProgressView extends View
{
    private Paint backgroundPaint, progressPaint;
    private RectF rectF;
    private float strokeWidth = 40f;
    private int progress = 0;
    private int max = 100;
    private int progressColor = Color.RED;
    private int backgroundColor = Color.GRAY;

    public CircularProgressView(Context context)
    {
        super(context);
        init(context, null);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs)
    {
        super(context, attrs);
        init(context, attrs);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs)
    {
        rectF = new RectF();

        if (attrs != null) {
            TypedArray ta = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.CircularProgressView,
                    0, 0
            );

            try
            {
                strokeWidth    = ta.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, 40f);
                progress       = ta.getInt(R.styleable.CircularProgressView_cpv_progress, 0);
                max            = ta.getInt(R.styleable.CircularProgressView_cpv_max, 100);
                progressColor  = ta.getColor(R.styleable.CircularProgressView_cpv_progressColor, Color.RED);
                backgroundColor= ta.getColor(R.styleable.CircularProgressView_cpv_backgroundColor, Color.GRAY);
            }
            finally
            {
                ta.recycle();
            }
        }

        // Background ring paint
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        float half = strokeWidth / 2f;
        float left   = getPaddingLeft() + half;
        float top    = getPaddingTop() + half;
        float right  = getWidth() - getPaddingRight() - half;
        float bottom = getHeight() - getPaddingBottom() - half;
        rectF.set(left, top, right, bottom);

        canvas.drawOval(rectF, backgroundPaint);

        int safeMax = (max <= 0) ? 1 : max;
        float clampedProgress = Math.max(0, Math.min(progress, safeMax));
        float sweepAngle = 360f * clampedProgress / safeMax;

        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint);
    }

    public void setProgress(int progress)
    {
        this.progress = progress;
        invalidate();
    }

    public void setMax(int max)
    {
        this.max = max;
        invalidate();
    }

    public void setProgressColor(int color)
    {
        this.progressColor = color;
        if (progressPaint != null) progressPaint.setColor(color);
        invalidate();
    }

    public void setRingBackgroundColor(int color)
    {
        this.backgroundColor = color;
        if (backgroundPaint != null) backgroundPaint.setColor(color);
        invalidate();
    }
}
