/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.sonymobile.android.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

        Canvas canvas = new Canvas(Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT,
                Bitmap.Config.ARGB_8888));
        draw(canvas);

        canvas = mHolder.lockCanvas();
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