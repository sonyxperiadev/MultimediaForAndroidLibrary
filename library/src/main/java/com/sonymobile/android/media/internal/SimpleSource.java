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
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;

public final class SimpleSource extends MediaSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SimpleSource";

    private static final int MSG_PREPARE = 11;

    private static final int MSG_SEEKTO = 12;

    MediaParser mMediaParser;

    private boolean mBuffering = false;

    private String mPath;

    private long mOffset;

    private long mLength;

    private int mMaxBufferSize;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private boolean mIsHttp = false;

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

        mEventHandler = new EventHandler(new WeakReference<SimpleSource>(this),
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

    @Override
    public void prepareAsync() {
        if (mMediaParser == null) {
            mEventHandler.sendEmptyMessage(MSG_PREPARE);
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
            AccessUnit accessUnit;
            try {
                if (mMediaParser.hasDataAvailable(type)) {
                    accessUnit = mMediaParser.dequeueAccessUnit(type);
                    if (mBuffering) {
                        mBuffering = false;
                        notify(SOURCE_BUFFERING_END);
                    }

                    return accessUnit;
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

    private void onPrepareAsync() {
        if (mMediaParser == null) {
            try {
                mMediaParser = MediaParserFactory.createParser(mPath, mOffset, mLength,
                        mMaxBufferSize, mEventHandler);
            } catch (IOException e) {
                notifyPrepareFailed(MediaError.IO);
                return;
            }
        }

        if (mMediaParser != null) {
            notifyPrepared();
        } else {
            notifyPrepareFailed(MediaError.UNSUPPORTED);
        }
    }

    private void onSeek(long timeUs) {
        mMediaParser.seekTo(timeUs);
    }

    private static class EventHandler extends Handler {

        private WeakReference<SimpleSource> mSource;

        public EventHandler(WeakReference<SimpleSource> source, Looper looper) {
            super(looper);

            mSource = source;
        }

        @Override
        public void handleMessage(Message msg) {
            SimpleSource thiz = mSource.get();
            switch (msg.what) {
                case MSG_PREPARE:
                    thiz.onPrepareAsync();
                    break;
                case MSG_SEEKTO:
                    thiz.onSeek(((Long)(msg.obj)).longValue());
                    break;
                case SOURCE_BUFFERING_UPDATE:
                    thiz.notify(SOURCE_BUFFERING_UPDATE, msg.arg1);
                    break;
                case SOURCE_BUFFERING_START:
                    if (!thiz.mBuffering) {
                        thiz.mBuffering = true;
                        thiz.notify(SOURCE_BUFFERING_START);
                    }
                    break;
                case SOURCE_BUFFERING_END:
                    thiz.mBuffering = false;
                    thiz.notify(SOURCE_BUFFERING_END);
                    break;
                case SOURCE_ERROR:
                    thiz.notify(SOURCE_ERROR);
                    break;
                default:
                    if (LOGS_ENABLED) Log.w(TAG, "Unknown message");
                    break;
            }
        }
    }
}
