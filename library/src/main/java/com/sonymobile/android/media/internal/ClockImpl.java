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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

/*
 * Simple clock class using system time.
 *
 */
public final class ClockImpl implements Clock, Codec {

    private static final int MSG_UPDATE_TIME = 1;

    private static final int INCREASED_SPEED_THRESHOLD_US = 100000;

    private long mStartTimeUs;

    private long mStartTimeOffsetUs;

    private boolean mIsRunning = false;

    private float mPlaybackSpeed = Util.DEFAULT_PLAYBACK_SPEED;

    private final Handler mCallback;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private long mSpeedAnchorUs;

    private long mPreviousTimeUs;

    private boolean mSeekExecuted = false;

    public ClockImpl(Handler callback) {
        mStartTimeUs = 0;
        mStartTimeOffsetUs = 0;
        mCallback = callback;
        mSpeedAnchorUs = 0;
        mPreviousTimeUs = 0;

        mEventThread = new HandlerThread("Clock", Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mEventThread.start();

        mEventHandler = new EventHandler(mEventThread.getLooper());
    }

    @Override
    public long getCurrentTimeUs() {
        /**
         * If speed is changed we detect it here and recalculate the
         * mStartTimeUs value to compensate for the increased/decreased value.
         * And by having an anchor set in setSpeed() we can continue playing
         * from the same spot with an increased/decreased speed by taking the
         * difference from anchor and multiply with the set speed. The decrease
         * scenario happens when you go from speed larger than 1x to 1x. The
         * increase scenario handles going from speed less than 1x to 1x.
         */
        if (mIsRunning) {
            long systemClockUs = System.nanoTime() / 1000;
            long currentTime = (long)(systemClockUs - mStartTimeUs + mStartTimeOffsetUs
                    + ((systemClockUs - mSpeedAnchorUs)
                    * (mPlaybackSpeed - Util.DEFAULT_PLAYBACK_SPEED)));
            // Decreased speed
            if (currentTime < mPreviousTimeUs && !mSeekExecuted) {
                mStartTimeUs -= mPreviousTimeUs - currentTime;
                mPreviousTimeUs = systemClockUs - mStartTimeUs + mStartTimeOffsetUs;
                return mPreviousTimeUs;
             }
            // Increased speed
            else if (currentTime - mPreviousTimeUs > INCREASED_SPEED_THRESHOLD_US
                    && mPreviousTimeUs != 0 && !mSeekExecuted) {
                mStartTimeUs += currentTime - mPreviousTimeUs;
                mPreviousTimeUs = systemClockUs - mStartTimeUs + mStartTimeOffsetUs;
                return mPreviousTimeUs;
            }
            mSeekExecuted = false;
            mPreviousTimeUs = currentTime;
            return currentTime;
        }
        return mStartTimeOffsetUs;
    }

    @Override
    public void start() {
        if (mIsRunning) {
            return;
        }
        mIsRunning = true;
        mStartTimeUs = System.nanoTime() / 1000;
        mSpeedAnchorUs = mStartTimeUs;
        if (mStartTimeOffsetUs != 0) {
            mStartTimeUs -= mStartTimeOffsetUs;
            mStartTimeOffsetUs = 0;
        }
        mEventHandler.obtainMessage(MSG_UPDATE_TIME).sendToTarget();
    }

    @Override
    public void pause() {
        if (!mIsRunning) {
            return;
        }
        long systemClockUs = (long)(System.nanoTime() * mPlaybackSpeed / 1000);
        mStartTimeOffsetUs = (long)(systemClockUs - mStartTimeUs + mStartTimeOffsetUs
                + ((systemClockUs - mSpeedAnchorUs)
                * (mPlaybackSpeed - Util.DEFAULT_PLAYBACK_SPEED)));
        mIsRunning = false;
        mEventHandler.removeMessages(MSG_UPDATE_TIME);
    }

    @Override
    public void stop() {
        mStartTimeUs = 0;
        mStartTimeOffsetUs = 0;
        mIsRunning = false;
        mEventThread.quitSafely();
        mEventHandler = null;
        mEventThread = null;
    }

    @Override
    public void setSeekTimeUs(long timeUs) {
        if (mIsRunning) {
            mStartTimeUs += timeUs * (1.0f + (mPlaybackSpeed - Util.DEFAULT_PLAYBACK_SPEED));
        }
        mStartTimeOffsetUs += timeUs - mStartTimeOffsetUs;
        mSeekExecuted = true;
        mSpeedAnchorUs = System.nanoTime() / 1000;
    }

    @Override
    public void setSpeed(float speed) {
        mSpeedAnchorUs = System.nanoTime() / 1000;
        mPlaybackSpeed = speed;
    }

    class EventHandler extends Handler {

        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_TIME) {
                if (mIsRunning) {
                    mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
                            CODEC_NOTIFY_POSITION, (int)(getCurrentTimeUs() / 1000))
                            .sendToTarget();
                    mEventHandler.sendMessageDelayed(mEventHandler.obtainMessage(MSG_UPDATE_TIME),
                            40);
                }
            }
        }
    }
}
