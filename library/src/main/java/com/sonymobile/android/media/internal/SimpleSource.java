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

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.Vector;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;

public final class SimpleSource extends MediaSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SimpleSource";

    private static final int MSG_PREPARE = 11;

    private static final int MSG_SEEKTO = 12;

    private static final int MSG_CHECK_BUFFERING = 13;

    private MediaParser mMediaParser;

    private boolean mBuffering = false;

    private String mPath;

    private long mOffset;

    private long mLength;

    private HttpURLConnection mUrlConnection;

    private int mMaxBufferSize;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private boolean mIsHttp = false;

    private boolean mPrepared = false;

    public SimpleSource(String path, long offset, long length, Handler notify, int maxBufferSize) {
        super(notify);
        if (maxBufferSize == -1) {
            maxBufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;
        }

        mPath = path;
        mOffset = offset;
        mLength = length;
        mMaxBufferSize = maxBufferSize;

        mEventThread = new HandlerThread("SimpleSource");
        mEventThread.start();

        mEventHandler = new EventHandler(new WeakReference<>(this),
                mEventThread.getLooper());

        if (path.startsWith("/") || path.startsWith("file")) {
            mSupportsPreview = true;
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            mIsHttp = true;
            mBuffering = true;
            notify(SOURCE_BUFFERING_START);
        }
    }

    public SimpleSource(FileDescriptor fd, long offset, long length, Handler notify) {
        super(notify);
        mMediaParser = MediaParserFactory.createParser(fd, offset, length);

        if (mMediaParser == null) {
            throw new IllegalArgumentException("Invalid content!");
        }
        mSupportsPreview = true;
    }

    public SimpleSource(HttpURLConnection urlConnection, Handler notify, int maxBufferSize) {
        super(notify);
        if (maxBufferSize == -1) {
            maxBufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;
        }

        mOffset = 0;
        mLength = -1;
        mUrlConnection = urlConnection;
        mMaxBufferSize = maxBufferSize;

        mEventThread = new HandlerThread("SimpleSource");
        mEventThread.start();

        mEventHandler = new EventHandler(new WeakReference<SimpleSource>(this),
                mEventThread.getLooper());

        mIsHttp = true;
        mBuffering = true;
        notify(SOURCE_BUFFERING_START);
    }

    @Override
    public void prepareAsync() {
        if (mMediaParser == null) {
            mEventHandler.obtainMessage(MSG_PREPARE, mUrlConnection).sendToTarget();
        } else {
            notifyPrepared();
        }
    }

    @Override
    public void start() {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub

    }

    @Override
    public MediaFormat getFormat(TrackType type) {
        return mMediaParser.getFormat(type);
    }

    @Override
    public AccessUnit dequeueAccessUnit(TrackType type) {
        if (mIsHttp) {
            try {
                if (mMediaParser.hasDataAvailable(type)) {
                    if (mBuffering) {
                        HttpBufferedDataSource httpBufferedDataSource =
                                (HttpBufferedDataSource) mMediaParser.mDataSource;
                        if (httpBufferedDataSource.getBufferedSize() <
                                (((double)httpBufferedDataSource.length() /
                                        mMediaParser.getDurationUs()) *
                                        Configuration.HTTP_MIN_BUFFERING_DURATION_US)
                                && !httpBufferedDataSource.isAtEndOfStream()) {
                            return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
                        }
                        mBuffering = false;
                        notify(SOURCE_BUFFERING_END);
                    }

                    return mMediaParser.dequeueAccessUnit(type);
                } else {
                    if (!mBuffering) {
                        mBuffering = true;
                        notify(SOURCE_BUFFERING_START);
                    }
                    return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception checking if we have data", e);
                return AccessUnit.ACCESS_UNIT_ERROR;
            }
        }
        return mMediaParser.dequeueAccessUnit(type);
    }

    @Override
    public long getDurationUs() {
        return mMediaParser.getDurationUs();
    }

    @Override
    public TrackInfo[] getTrackInfo() {
        return mMediaParser.getTrackInfo();
    }

    @Override
    public TrackType selectTrack(boolean select, int index) {
        return mMediaParser.selectTrack(select, index);
    }

    @Override
    public void seekTo(long timeUs) {
        if (mIsHttp) {
            mEventHandler.obtainMessage(MSG_SEEKTO, timeUs).sendToTarget();
            mBuffering = true;
            notify(SOURCE_BUFFERING_START);
        } else {
            onSeek(timeUs);
        }

    }

    @Override
    public void release() {
        if (mEventThread != null) {
            mEventThread.quit();
            mEventThread = null;
        }
        if (mMediaParser != null) {
            mMediaParser.release();
        }
    }

    @Override
    public MetaData getMetaData() {
        return mMediaParser.getMetaData();
    }

    @Override
    public int getSelectedTrackIndex(TrackType type) {
        return mMediaParser.getSelectedTrackIndex(type);
    }

    @Override
    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        // Not supported
    }

    @Override
    public void setRepresentationSelector(RepresentationSelector selector) {
        // Not supported
    }

    @Override
    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        // Not supported
    }

    @Override
    public Statistics getStatistics() {
        // Not supported
        return null;
    }

    private void onPrepareAsync(HttpURLConnection urlConnection) {
        if (mMediaParser == null) {
            try {
                if (urlConnection != null) {
                    mMediaParser = MediaParserFactory.createParser(urlConnection,
                            mMaxBufferSize, mEventHandler);
                } else {
                    mMediaParser = MediaParserFactory.createParser(mPath, mOffset, mLength,
                            mMaxBufferSize, mEventHandler);
                }
            } catch (IOException e) {
                notifyPrepareFailed(MediaError.IO);
                return;
            }
        }

        if (mMediaParser != null) {
            notifyPrepared();
            mPrepared = true;
            if (mIsHttp) {
                mEventHandler.sendEmptyMessage(MSG_CHECK_BUFFERING);
            }
        } else {
            notifyPrepareFailed(MediaError.UNSUPPORTED);
        }
    }

    private void onSeek(long timeUs) {
        mMediaParser.seekTo(timeUs);
    }

    private void onCheckBuffering() {
        int percentage = ((HttpBufferedDataSource)mMediaParser.mDataSource).getBuffering();

        notify(SOURCE_BUFFERING_UPDATE, percentage);

        if (percentage < 100) {
            mEventHandler.sendEmptyMessageAtTime(MSG_CHECK_BUFFERING,
                    SystemClock.uptimeMillis() + 1000);
        } else if (mBuffering) {
            mBuffering = false;
            notify(SOURCE_BUFFERING_END);
        }
    }

    private static class EventHandler extends Handler {

        private final WeakReference<SimpleSource> mSource;

        public EventHandler(WeakReference<SimpleSource> source, Looper looper) {
            super(looper);

            mSource = source;
        }

        @Override
        public void handleMessage(Message msg) {
            SimpleSource thiz = mSource.get();
            switch (msg.what) {
                case MSG_PREPARE:
                    thiz.onPrepareAsync((HttpURLConnection)msg.obj);
                    break;
                case MSG_SEEKTO:
                    thiz.onSeek((Long) msg.obj);
                    break;
                case SOURCE_BUFFERING_UPDATE:
                    if (thiz.mPrepared && !thiz.mEventHandler.hasMessages(MSG_CHECK_BUFFERING)) {
                        thiz.onCheckBuffering();
                    }
                    break;
                case SOURCE_ERROR:
                    thiz.notify(SOURCE_ERROR);
                    break;
                case MSG_CHECK_BUFFERING:
                    thiz.onCheckBuffering();
                    break;
                default:
                    if (LOGS_ENABLED) Log.w(TAG, "Unknown message");
                    break;
            }
        }
    }
}
