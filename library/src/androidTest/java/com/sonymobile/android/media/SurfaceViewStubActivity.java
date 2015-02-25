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

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;

import com.sonymobile.android.media.MockSurfaceView;
import com.sonymobile.android.media.test.R;

public class SurfaceViewStubActivity extends Activity {

    private MockSurfaceView mSurfaceView;
    private SurfaceView mSurfaceView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mediaplayer);

        mSurfaceView = (MockSurfaceView)findViewById(R.id.surface);
        mSurfaceView2 = (SurfaceView)findViewById(R.id.surface2);
    }

    public MockSurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public SurfaceView getSurfaceView2() {
        return mSurfaceView2;
    }
}
