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

import java.util.Vector;

import android.media.MediaFormat;
import android.os.Handler;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;

public abstract class MediaSource {

    public static final int SOURCE_PREPARED = 1;

    public static final int SOURCE_PREPARE_FAILED = 2;

    public static final int SOURCE_BUFFERING_START = 3;

    public static final int SOURCE_BUFFERING_END = 4;

    public static final int SOURCE_CHANGE_SUBTITLE = 5;

    public static final int SOURCE_REPRESENTATION_CHANGED = 6;

    public static final int SOURCE_ERROR = 7;

    public static final int SOURCE_BUFFERING_UPDATE = 8;

    private final Handler mNotify;

    protected boolean mSupportsPreview;

    protected MediaSource(Handler notify) {
        mNotify = notify;
    }

    public abstract void prepareAsync();

    public abstract void start();

    public abstract void stop();

    public abstract MediaFormat getFormat(TrackType type);

    public abstract AccessUnit dequeueAccessUnit(TrackType type);

    public abstract long getDurationUs();

    public abstract TrackInfo[] getTrackInfo();

    public abstract TrackType selectTrack(boolean select, int index);

    public abstract int getSelectedTrackIndex(TrackType type);

    protected void notifyPrepared() {
        mNotify.obtainMessage(Player.MSG_SOURCE_NOTIFY, SOURCE_PREPARED, 0).sendToTarget();
    }

    protected void notifyPrepareFailed(int error) {
        mNotify.obtainMessage(Player.MSG_SOURCE_NOTIFY, SOURCE_PREPARE_FAILED, error)
                .sendToTarget();
    }

    protected void notify(int what) {
        mNotify.obtainMessage(Player.MSG_SOURCE_NOTIFY, what, 0).sendToTarget();
    }

    protected void notify(int what, int arg) {
        mNotify.obtainMessage(Player.MSG_SOURCE_NOTIFY, what, arg).sendToTarget();
    }

    protected void notify(int what, Object obj) {
        mNotify.obtainMessage(Player.MSG_SOURCE_NOTIFY, what, 0, obj).sendToTarget();
    }


    public abstract void seekTo(long timeUs);

    public abstract void release();

    public abstract MetaData getMetaData();

    public abstract void setBandwidthEstimator(BandwidthEstimator estimator);

    public abstract void setRepresentationSelector(RepresentationSelector selector);

    public abstract void selectRepresentations(int trackIndex, Vector<Integer> representations);

    public abstract Statistics getStatistics();

    public boolean supportsPreview() {
        return mSupportsPreview;
    }

}
