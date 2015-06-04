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

package com.sonymobile.android.media.internal;

import com.sonymobile.android.media.MediaPlayer.OutputBlockedInfo;
import com.sonymobile.android.media.MediaPlayer.OutputControlInfo;
import com.sonymobile.android.media.internal.drm.OnOutputControllerUpdateListener;
import com.sonymobile.android.media.internal.drm.OutputControlEvent;

public class OutputControllerUpdateListener implements OnOutputControllerUpdateListener {

    public static final int OUTPUT_CONTROLINFO = 1;

    public static final int OUTPUT_BLOCKED = 2;

    private final Player mPlayer;

    public OutputControllerUpdateListener(Player p) {
        mPlayer = p;
    }

    @Override
    public void onHeadphonesRestricted(OutputControlEvent outputControlEvent) {
        OutputBlockedInfo info = new OutputBlockedInfo();
        info.what = OutputBlockedInfo.AUDIO_MUTED;
        mPlayer.onOutputControlEvent(OUTPUT_BLOCKED, info);
    }

    @Override
    public void onExternalWifiRestricted(OutputControlEvent outputControlEvent) {
        OutputBlockedInfo info = new OutputBlockedInfo();
        info.what = OutputBlockedInfo.AUDIO_MUTED | OutputBlockedInfo.EXTERNAL_DISPLAY_BLOCKED;
        mPlayer.onOutputControlEvent(OUTPUT_BLOCKED, info);
    }

    @Override
    public void onExternalHDMIRestricted(OutputControlEvent outputControlEvent) {
        OutputBlockedInfo info = new OutputBlockedInfo();
        info.what = OutputBlockedInfo.AUDIO_MUTED | OutputBlockedInfo.EXTERNAL_DISPLAY_BLOCKED;
        mPlayer.onOutputControlEvent(OUTPUT_BLOCKED, info);
    }

    @Override
    public void onOutputControlInfo(OutputControlInfo info) {
        mPlayer.onOutputControlEvent(OUTPUT_CONTROLINFO, info);
    }
}