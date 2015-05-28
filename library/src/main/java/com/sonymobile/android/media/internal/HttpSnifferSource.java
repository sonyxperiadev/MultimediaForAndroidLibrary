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

package com.sonymobile.android.media.internal;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.internal.streaming.mpegdash.DASHSource;
import com.sonymobile.android.media.internal.streaming.smoothstreaming.SmoothStreamingSource;

public class HttpSnifferSource extends MediaSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "HttpSnifferSource";

    private static final int MSG_CONNECT = 0;

    private static final int NOTIFY_CONNECTED = 0;

    private static final int NOTIFY_CONNECT_FAILED = -1;

    private String mUrl;

    private int mMaxBufferSize;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private MediaSource mSource;

    private Handler mNotify;

    private BandwidthEstimator mBandwidthEstimator;

    private RepresentationSelector mRepresentationSelector;

    public HttpSnifferSource(String url, Handler notify, int maxBufferSize) {
        super(notify);

        mUrl = url;
        mNotify = notify;
        mMaxBufferSize = maxBufferSize;
    }

    @Override
    public void prepareAsync() {
        mEventThread = new HandlerThread("HttpSniffer");
        mEventThread.start();

        mEventHandler = new EventHandler(new CallbackHandler(new WeakReference<>(this)),
                mEventThread.getLooper());

        mEventHandler.obtainMessage(MSG_CONNECT, mUrl).sendToTarget();
    }

    @Override
    public void start() {
        mSource.start();
    }

    @Override
    public void stop() {
        mSource.stop();
    }

    @Override
    public MediaFormat getFormat(TrackInfo.TrackType type) {
        return mSource.getFormat(type);
    }

    @Override
    public AccessUnit dequeueAccessUnit(TrackInfo.TrackType type) {
        return mSource.dequeueAccessUnit(type);
    }

    @Override
    public long getDurationUs() {
        return mSource.getDurationUs();
    }

    @Override
    public TrackInfo[] getTrackInfo() {
        return mSource.getTrackInfo();
    }

    @Override
    public TrackInfo.TrackType selectTrack(boolean select, int index) {
        return mSource.selectTrack(select, index);
    }

    @Override
    public int getSelectedTrackIndex(TrackInfo.TrackType type) {
        return mSource.getSelectedTrackIndex(type);
    }

    @Override
    public void seekTo(long timeUs) {
        mSource.seekTo(timeUs);
    }

    @Override
    public void release() {
        if (mSource != null) {
            mSource.release();
        }
    }

    @Override
    public MetaData getMetaData() {
        return mSource.getMetaData();
    }

    @Override
    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        if (mSource != null) {
            mSource.setBandwidthEstimator(estimator);
        } else {
            mBandwidthEstimator = estimator;
        }
    }

    @Override
    public void setRepresentationSelector(RepresentationSelector selector) {
        if (mSource != null) {
            mSource.setRepresentationSelector(selector);
        } else {
            mRepresentationSelector = selector;
        }
    }

    @Override
    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        mSource.selectRepresentations(trackIndex, representations);
    }

    @Override
    public MediaPlayer.Statistics getStatistics() {
        return mSource.getStatistics();
    }

    public static boolean canHandle(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    @Override
    public boolean isStreaming() {
        return mSource.isStreaming();
    }

    private static class CallbackHandler extends Handler {

        private WeakReference<HttpSnifferSource> mParent;

        public CallbackHandler(WeakReference<HttpSnifferSource> parent) {
            mParent = parent;
        }

        @Override
        public void handleMessage(Message msg) {
            HttpSnifferSource thiz = mParent.get();

            if (msg.what == NOTIFY_CONNECTED) {
                HttpURLConnection urlConnection = (HttpURLConnection) msg.obj;

                String mime = urlConnection.getHeaderField("Content-Type");

                if (LOGS_ENABLED) Log.i(TAG, "Found mime: " + mime);

                if (DASHSource.canHandle(mime, thiz.mUrl)) {
                    thiz.mSource = new DASHSource(urlConnection,
                            thiz.mNotify, thiz.mMaxBufferSize);
                } else if (SmoothStreamingSource.canHandle(mime, thiz.mUrl)) {
                    thiz.mSource = new SmoothStreamingSource(urlConnection,
                            thiz.mNotify, thiz.mMaxBufferSize);
                } else {
                    thiz.mSource = new SimpleSource(urlConnection,
                            thiz.mNotify, thiz.mMaxBufferSize);
                }

                if (thiz.mSource != null) {
                    thiz.mSource.setBandwidthEstimator(thiz.mBandwidthEstimator);
                    thiz.mSource.setRepresentationSelector(thiz.mRepresentationSelector);
                    thiz.mSource.prepareAsync();
                } else {
                    thiz.notifyPrepareFailed(MediaError.UNSUPPORTED);
                }
            } else if (msg.what == NOTIFY_CONNECT_FAILED) {
                thiz.notifyPrepareFailed(msg.arg1);
            }
        }
    }

    private class EventHandler extends Handler {

        private CallbackHandler mCallback;

        public EventHandler(CallbackHandler callback, Looper looper) {
            super(looper);

            mCallback = callback;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_CONNECT) {
                String uri = (String)msg.obj;
                try {
                    URL url = new URL(uri);
                    HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();

                    urlConnection.setConnectTimeout(5000); // 5s timeout
                    urlConnection.setReadTimeout(5000); // 5s timeout
                    urlConnection.setRequestProperty("Accept-Encoding", "identity");

                    urlConnection.connect();

                    if (urlConnection.getResponseCode() / 100 == 2) {

                        mCallback.obtainMessage(NOTIFY_CONNECTED,
                                urlConnection).sendToTarget();
                    } else {
                        mCallback.obtainMessage(NOTIFY_CONNECT_FAILED,
                                MediaError.IO, 0).sendToTarget();
                    }
                } catch (MalformedURLException e) {
                    mCallback.obtainMessage(NOTIFY_CONNECT_FAILED,
                            MediaError.MALFORMED, 0).sendToTarget();
                } catch (IOException e) {
                    mCallback.obtainMessage(NOTIFY_CONNECT_FAILED,
                            MediaError.IO, 0).sendToTarget();
                }
            }

            mEventThread.quitSafely();
        }
    }
}
