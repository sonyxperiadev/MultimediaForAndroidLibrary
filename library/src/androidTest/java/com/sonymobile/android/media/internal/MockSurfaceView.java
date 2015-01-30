/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MockSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final int BITMAP_WIDTH = 100;

    private static final int BITMAP_HEIGHT = 100;

    private static final int RECT_LEFT = 20;

    private static final int RECT_TOP = 100;

    private static final int RECT_RIGHT = 200;

    private static final int RECT_BOTTOM = 200;

    private SurfaceHolder mHolder;

    private MockCanvas mCanvas;

    public MockSurfaceView(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public MockSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MockSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SurfaceHolder getMockHolder() {
        return mHolder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder sh, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder sh) {

        mCanvas = new MockCanvas(Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT,
                Bitmap.Config.ARGB_8888));
        draw(mCanvas);

        Canvas canvas = mHolder.lockCanvas();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        canvas.drawRect(RECT_LEFT, RECT_TOP, RECT_RIGHT, RECT_BOTTOM, paint);
        mHolder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        getHolder().removeCallback(this);
    }

}

class MockCanvas extends Canvas {

    public MockCanvas(Bitmap bitmap) {
        super(bitmap);
    }

    @Override
    public void drawColor(int color, Mode mode) {
        super.drawColor(color, mode);
    }
}