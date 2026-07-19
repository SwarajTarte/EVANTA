package com.evanta.app;

import android.content.Context;
import android.content.res.TypedArray;
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
    private int fadeSizePx;

    // Which edges to fade. Defaults keep the original top+bottom behaviour so
    // existing usages (home screen) are unchanged when no attrs are supplied.
    private boolean fadeTop = true;
    private boolean fadeBottom = true;
    private boolean fadeLeft = false;
    private boolean fadeRight = false;

    public FadingEdgeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        fadeSizePx = (int) (32 * getResources().getDisplayMetrics().density);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FadingEdgeLayout);
            fadeTop = a.getBoolean(R.styleable.FadingEdgeLayout_fadeTop, true);
            fadeBottom = a.getBoolean(R.styleable.FadingEdgeLayout_fadeBottom, true);
            fadeLeft = a.getBoolean(R.styleable.FadingEdgeLayout_fadeLeft, false);
            fadeRight = a.getBoolean(R.styleable.FadingEdgeLayout_fadeRight, false);
            fadeSizePx = a.getDimensionPixelSize(R.styleable.FadingEdgeLayout_fadeSize, fadeSizePx);
            a.recycle();
        }

        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        // Xfermode compositing needs a software layer to render correctly
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (fadeTop) {
            // Fully erase content at y=0, fade back to normal by fadeSizePx
            fadePaint.setShader(new LinearGradient(
                    0, 0, 0, fadeSizePx,
                    Color.argb(255, 0, 0, 0), Color.argb(0, 0, 0, 0),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, fadeSizePx, fadePaint);
        }

        if (fadeBottom) {
            // Normal until fadeSizePx from bottom, fully erased at the very edge
            fadePaint.setShader(new LinearGradient(
                    0, height - fadeSizePx, 0, height,
                    Color.argb(0, 0, 0, 0), Color.argb(255, 0, 0, 0),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, height - fadeSizePx, width, height, fadePaint);
        }

        if (fadeLeft) {
            // Fully erase content at x=0, fade back to normal by fadeSizePx
            fadePaint.setShader(new LinearGradient(
                    0, 0, fadeSizePx, 0,
                    Color.argb(255, 0, 0, 0), Color.argb(0, 0, 0, 0),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, fadeSizePx, height, fadePaint);
        }

        if (fadeRight) {
            // Normal until fadeSizePx from right, fully erased at the very edge
            fadePaint.setShader(new LinearGradient(
                    width - fadeSizePx, 0, width, 0,
                    Color.argb(0, 0, 0, 0), Color.argb(255, 0, 0, 0),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(width - fadeSizePx, 0, width, height, fadePaint);
        }
    }
}
