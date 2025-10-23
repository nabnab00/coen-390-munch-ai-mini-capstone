package com.example.munchai.frontend;


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

public class CircularProgressView extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private RectF rectF;

    private float strokeWidth = 40f;
    private int progress = 0;
    private int max = 100;
    private int progressColor = Color.RED;
    private int backgroundColor = Color.GRAY;


    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        rectF = new RectF();

        // Load attributes from XML
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.CircularProgressView,
                0, 0);
        try {
            strokeWidth = typedArray.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, 40f);
            progress = typedArray.getInt(R.styleable.CircularProgressView_cpv_progress, 0);
            max = typedArray.getInt(R.styleable.CircularProgressView_cpv_max, 100);
            progressColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_progressColor, Color.RED);
            backgroundColor = typedArray.getColor(R.styleable.CircularProgressView_cpv_backgroundColor, Color.GRAY);
        } finally {
            typedArray.recycle();
        }


        // Paint for the background ring
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft() + strokeWidth / 2;
        float top = getPaddingTop() + strokeWidth / 2;
        float right = getWidth() - getPaddingRight() - strokeWidth / 2;
        float bottom = getHeight() - getPaddingBottom() - strokeWidth / 2;

        rectF.set(left, top, right, bottom);

        // Draw the background ring
        canvas.drawOval(rectF, backgroundPaint);

        // Calculate sweep angle for the progress
        float sweepAngle = 360f * progress / max;

        // Draw the progress arc
        canvas.drawArc(rectF, -90, sweepAngle, false, progressPaint);
    }

    public void setProgress(int progress) {
        this.progress = progress;
        invalidate(); // Redraw the view
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }

    public void setProgressColor(int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }
}
