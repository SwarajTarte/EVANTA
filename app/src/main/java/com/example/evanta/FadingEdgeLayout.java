package com.example.evanta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class FadingEdgeLayout extends FrameLayout {

    private final Paint fadePaint = new Paint();
    private final int fadeHeightPx;

    public FadingEdgeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        fadeHeightPx = (int) (32 * getResources().getDisplayMetrics().density);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        // Xfermode compositing needs a software layer to render correctly
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Top edge: fully erase content at y=0, fade back to normal by fadeHeightPx
        fadePaint.setShader(new LinearGradient(
                0, 0, 0, fadeHeightPx,
                Color.argb(255, 0, 0, 0), Color.argb(0, 0, 0, 0),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, fadeHeightPx, fadePaint);

        // Bottom edge: normal until fadeHeightPx from bottom, fully erased at the very edge
        fadePaint.setShader(new LinearGradient(
                0, height - fadeHeightPx, 0, height,
                Color.argb(0, 0, 0, 0), Color.argb(255, 0, 0, 0),
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, height - fadeHeightPx, width, height, fadePaint);
    }
}