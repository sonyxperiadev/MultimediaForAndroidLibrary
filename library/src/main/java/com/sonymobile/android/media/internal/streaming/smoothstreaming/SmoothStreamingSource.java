/*
 * Copyright (C) 2015 Sony Mobile Communications Inc.
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

package com.sonymobile.android.media.internal.streaming.smoothstreaming;

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
import com.sonymobile.android.media.internal.MimeType;

import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.Vector;

public class SmoothStreamingSource extends MediaSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SmoothStreamingSource";

    public static final int MSG_PREPARED = 0;

    public static final int MSG_PREPARE_FAILED = 1;

    public static final int MSG_BUFFERING_START = 2;

    public static final int MSG_BUFFERING_END = 3;

    public static final int MSG_CHANGE_SUBTITLE = 4;

    public static final int MSG_ERROR = 6;

    private String mUrl;

    private HttpURLConnection mUrlConnection;

    private SmoothStreamingSession mSession;

    private EventHandler mEventHandler;

    private BandwidthEstimator mBandwidthEstimator = null;

    private RepresentationSelector mRepresentationSelector = null;

    private final int mMaxBufferSize;

    public SmoothStreamingSource(String url, Handler notify, int maxBufferSize) {
        super(notify);

        mUrl = url;
        mMaxBufferSize = maxBufferSize;
    }

    public SmoothStreamingSource(HttpURLConnection urlConnection,
                                 Handler notify, int maxBufferSize) {
        super(notify);

        mUrlConnection = urlConnection;
        mMaxBufferSize = maxBufferSize;
    }

    @Override
    public void prepareAsync() {
        mEventHandler = new EventHandler(new WeakReference<>(this));

        mSession = new SmoothStreamingSession(mEventHandler, mBandwidthEstimator,
                mRepresentationSelector, mMaxBufferSize);
        mSession.connect(mUrl, mUrlConnection);
    }

    @Override
    public void start() {
        notify(SOURCE_BUFFERING_START);
    }

    @Override
    public void stop() {
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

        private final WeakReference<SmoothStreamingSource> mSource;

        public EventHandler(WeakReference<SmoothStreamingSource> source) {
            super();

            mSource = source;
        }

        @Override
        public void handleMessage(Message msg) {
            SmoothStreamingSource thiz = mSource.get();
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
        return null;
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    public static boolean canHandle(String uri) {
        return ((uri.startsWith("http://") || uri.startsWith("https://"))
                && (uri.toLowerCase(Locale.US).endsWith("/manifest") ||
                uri.toLowerCase(Locale.US).indexOf("/manifest?") > 0));
    }

    public static boolean canHandle(String mime, String uri) {
        return mime.equalsIgnoreCase(MimeType.SMOOTH_STREAMING) ||
                uri.toLowerCase(Locale.US).endsWith("/manifest") ||
                uri.toLowerCase(Locale.US).contains("/manifest?");
    }
}
