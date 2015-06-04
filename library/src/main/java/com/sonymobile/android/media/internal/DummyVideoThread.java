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

import static com.sonymobile.android.media.internal.Player.MSG_CODEC_NOTIFY;

import android.annotation.SuppressLint;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.TrackInfo.TrackType;

public final class DummyVideoThread extends VideoCodecThread {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DummyVideoThread";

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private MediaSource mSource;

    private boolean mEOS = false;

    private final Clock mClock;

    private boolean mStarted = false;

    private final Handler mCallback;

    private boolean mSetupCompleted = false;

    private boolean mReadyToRender = false;

    private float mCurrentSpeed = 1.0f;

    private final HandlerHelper mHandlerHelper;

    public DummyVideoThread(MediaFormat format, MediaSource source, Clock clock,
            Handler callback) {
        super();
        mEventThread = new HandlerThread("DummyVideo", Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mEventThread.start();

        mEventHandler = new EventHandler(mEventThread.getLooper());

        mEventHandler.obtainMessage(MSG_SET_SOURCE, source).sendToTarget();
        mEventHandler.obtainMessage(MSG_SETUP, format).sendToTarget();

        mClock = clock;
        mCallback = callback;

        mHandlerHelper = new HandlerHelper();
    }

    @Override
    public void seek() {
        mEventHandler.removeMessages(MSG_DEQUEUE_INPUT_BUFFER);
        mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_INPUT_BUFFER,
                SystemClock.uptimeMillis() + 200);
        mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_VIDEO_SEEK_COMPLETED, 0).sendToTarget();
    }

    @Override
    public void start() {
        mEventHandler.sendEmptyMessage(MSG_START);
    }

    @Override
    public void pause() {
        mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_PAUSE));
    }

    @Override
    public void flush() {
        mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_FLUSH));
    }

    @Override
    public void stop() {
        mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_STOP));
        mEventThread.quit();
        mHandlerHelper.releaseAllLocks();
        mEventThread = null;
        mEventHandler = null;
    }

    @Override
    public boolean isSetupCompleted() {
        return mSetupCompleted;
    }

    @Override
    public boolean isReadyToRender() {
        return mReadyToRender;
    }

    @Override
    public void updateAudioClockOnNextVideoFrame() {
    }

    @Override
    public void setVideoScalingMode(int mode) {
    }

    @Override
    public void setSpeed(float speed) {
        mCurrentSpeed = speed;
    }

    private void doStart() {
        mStarted = true;
        mEventHandler.sendEmptyMessage(MSG_DEQUEUE_INPUT_BUFFER);
    }

    private void doPause() {
        mStarted = false;
        mEventHandler.removeMessages(MSG_DEQUEUE_INPUT_BUFFER);
    }

    private void doStop() {
        mStarted = false;
        mEventHandler.removeCallbacksAndMessages(null);
    }

    private void doFlush() {
        mReadyToRender = false;
        mEOS = false;
    }

    private void doDequeueInputBuffer() {
        if (mStarted && !mEOS) {
            long delay = 10;
            AccessUnit accessUnit = mSource.dequeueAccessUnit(TrackType.VIDEO);

            if (accessUnit.status == AccessUnit.OK) {
                mReadyToRender = true;
                delay = (accessUnit.timeUs - mClock.getCurrentTimeUs())
                        / (long)(1000 * mCurrentSpeed);
            } else if (accessUnit.status == AccessUnit.ERROR) {
                mCallback.obtainMessage(MSG_CODEC_NOTIFY, CODEC_ERROR, MediaError.UNKNOWN)
                        .sendToTarget();
                return;
            } else if (accessUnit.status == AccessUnit.END_OF_STREAM) {
                mCallback.obtainMessage(MSG_CODEC_NOTIFY,
                        CODEC_VIDEO_COMPLETED, 0).sendToTarget();
                mEOS = true;
                return;
            }

            mEventHandler.sendEmptyMessageAtTime(MSG_DEQUEUE_INPUT_BUFFER,
                    SystemClock.uptimeMillis() + delay);
        }

    }

    @SuppressLint("HandlerLeak")
    class EventHandler extends Handler {

        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_SET_SOURCE:
                    mSource = (MediaSource)msg.obj;
                    break;
                case MSG_SETUP:
                    mSetupCompleted = true;
                    break;
                case MSG_START:
                    doStart();
                    break;
                case MSG_PAUSE: {
                    doPause();

                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = new Object();
                    reply.sendToTarget();
                    break;
                }
                case MSG_DEQUEUE_INPUT_BUFFER:
                    doDequeueInputBuffer();
                    break;
                case MSG_FLUSH: {
                    doFlush();

                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = new Object();
                    reply.sendToTarget();
                    break;
                }
                case MSG_STOP: {
                    doStop();

                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = new Object();
                    reply.sendToTarget();
                    break;
                }
                default:
                    if (LOGS_ENABLED) Log.w(TAG, "Unknown message");
                    break;
            }
        }
    }
}
