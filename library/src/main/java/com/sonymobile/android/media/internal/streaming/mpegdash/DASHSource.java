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

package com.sonymobile.android.media.internal.streaming.mpegdash;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MediaSource;

import java.lang.ref.WeakReference;
import java.util.Vector;

public class DASHSource extends MediaSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DASHSource";

    public static final int MSG_PREPARED = 0;

    public static final int MSG_PREPARE_FAILED = 1;

    public static final int MSG_BUFFERING_START = 2;

    public static final int MSG_BUFFERING_END = 3;

    public static final int MSG_CHANGE_SUBTITLE = 4;

    public static final int MSG_REPRESENTATION_CHANGED = 5;

    public static final int MSG_ERROR = 6;

    private String mUrl;

    private DASHSession mSession;

    private EventHandler mEventHandler;

    private BandwidthEstimator mBandwidthEstimator = null;

    private RepresentationSelector mRepresentationSelector = null;

    private int mMaxBufferSize;

    public DASHSource(String url, Handler notify, int maxBufferSize) {
        super(notify);

        mUrl = url;
        mMaxBufferSize = maxBufferSize;
    }

    @Override
    public void prepareAsync() {
        mEventHandler = new EventHandler(new WeakReference<DASHSource>(this));

        mSession = new DASHSession(mEventHandler, mBandwidthEstimator, mRepresentationSelector,
                mMaxBufferSize);
        mSession.connect(mUrl);
    }

    @Override
    public void start() {
        notify(SOURCE_BUFFERING_START);
    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

    @Override
    public MediaFormat getFormat(TrackType type) {
        return mSession.getFormat(type);
    }

    @Override
    public AccessUnit dequeueAccessUnit(TrackType type) {
        return mSession.dequeueAccessUnit(type);
    }

    @Override
    public long getDurationUs() {
        return mSession.getDurationUs();
    }

    @Override
    public TrackInfo[] getTrackInfo() {
        return mSession.getTrackInfo();
    }

    @Override
    public TrackType selectTrack(boolean select, int index) {
        return mSession.selectTrack(select, index);
    }

    @Override
    public int getSelectedTrackIndex(TrackType type) {
        return mSession.getSelectedTrackIndex(type);
    }

    private static class EventHandler extends Handler {

        private WeakReference<DASHSource> mSource;

        public EventHandler(WeakReference<DASHSource> source) {
            super();

            mSource = source;
        }

        @Override
        public void handleMessage(Message msg) {
            DASHSource thiz = mSource.get();
            switch (msg.what) {
                case MSG_PREPARED:
                    thiz.notifyPrepared();
                    break;
                case MSG_PREPARE_FAILED:
                    thiz.notifyPrepareFailed(msg.arg1);
                    break;
                case MSG_BUFFERING_START:
                    thiz.notify(SOURCE_BUFFERING_START);
                    break;
                case MSG_BUFFERING_END:
                    thiz.notify(SOURCE_BUFFERING_END);
                    break;
                case MSG_CHANGE_SUBTITLE:
                    thiz.notify(SOURCE_CHANGE_SUBTITLE, msg.arg1);
                    break;
                case MSG_REPRESENTATION_CHANGED:
                    thiz.notify(SOURCE_REPRESENTATION_CHANGED, msg.obj);
                    break;
                case MSG_ERROR:
                    thiz.notify(SOURCE_ERROR);
                    break;
                default:
                    if (LOGS_ENABLED) Log.w(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    @Override
    public void seekTo(long timeUs) {
        mSession.seekTo(timeUs);
    }

    @Override
    public void release() {
        mSession.disconnect();
    }

    @Override
    public MetaData getMetaData() {
        return mSession.getMetaData();
    }

    @Override
    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        if (mSession != null) {
            mSession.setBandwidthEstimator(estimator);
        } else {
            mBandwidthEstimator = estimator;
        }
    }

    @Override
    public void setRepresentationSelector(RepresentationSelector selector) {
        if (mSession != null) {
            mSession.setRepresentationSelector(selector);
        } else {
            mRepresentationSelector = selector;
        }
    }

    @Override
    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        mSession.selectRepresentations(trackIndex, representations);
    }

    @Override
    public Statistics getStatistics() {
        return mSession.getStatistics();
    }
}
