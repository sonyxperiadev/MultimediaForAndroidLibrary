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

package com.sonymobile.android.media.testmediaplayer.subtitles;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.testmediaplayer.PlayerConfiguration;
import com.sonymobile.android.media.testmediaplayer.subtitles.ttml.TtmlData;
import com.sonymobile.android.media.testmediaplayer.subtitles.ttml.TtmlParser;
import com.sonymobile.android.media.testmediaplayer.subtitles.ttml.TtmlSubtitle;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class SubtitleRenderer {

    private static final boolean LOGS_ENABLED = PlayerConfiguration.DEBUG || false;

    private static final int MSG_RENDER_TEXT = 1;

    private static final int MSG_RENDER_NOTHING = 2;

    private static final int FIFTY_MS = 50;

    private static final String TAG = "SubtitleRenderer";

    private final TextView mTextView;

    private final TtmlParser mTtmlParser;

    private long mBaseTimeMs;

    private int mSubtitleIndex = 0;

    private long mCurrentTimeMs;

    private HashMap<Integer, TtmlData> mStringsAtTimes;

    private String mCurrentText;

    private List<TimeAndPosition> mSeekList;

    private final EventHandler mHandler;

    private final RenderHandler mRenderer;

    private final HandlerThread mHandlerThread;

    private final HandlerThread mRenderThread;

    private final MediaPlayer mMediaPlayer;

    public SubtitleRenderer(TextView textview, Looper looper, MediaPlayer mp) {
        mHandlerThread = new HandlerThread("SubtitleHandlerThread");
        mHandlerThread.start();
        mRenderThread = new HandlerThread("SubtitleRenderThread");
        mRenderThread.start();
        mTextView = textview;
        mHandler = new EventHandler(mHandlerThread.getLooper());
        mRenderer = new RenderHandler(looper);
        mTtmlParser = new TtmlParser();
        mMediaPlayer = mp;
    }

    public void parseSubtitle(InputStream is) {
        try {
            mBaseTimeMs = 0;
            mSeekList = new ArrayList<>();
            TtmlSubtitle ttmlSubtitle = (TtmlSubtitle)mTtmlParser.parse(is, "UTF-8", 0);
            mStringsAtTimes = new HashMap<>();
            if (LOGS_ENABLED) Log.d(TAG, "eventcounters: " + ttmlSubtitle.getEventTimeCount());
            int maxCount = ttmlSubtitle.getEventTimeCount();
            for (int i = 0; i < maxCount; i++) {

                if (LOGS_ENABLED) Log.d(TAG, "Text: " +
                        ttmlSubtitle.getText(ttmlSubtitle.getEventTime(i)) + " EventTime: "
                        + ttmlSubtitle.getEventTime(i) + " NextEvent: "
                        + ttmlSubtitle.getNextEventTimeIndex(ttmlSubtitle.getEventTime(i)));

                mStringsAtTimes.put(i,
                        new TtmlData(ttmlSubtitle.getText(ttmlSubtitle.getEventTime(i)),
                                ttmlSubtitle.getEventTime(i) / 1000, ttmlSubtitle
                                        .getNextEventTimeIndex(ttmlSubtitle.getEventTime(i))));

                mSeekList.add(new TimeAndPosition(ttmlSubtitle.getEventTime(i) / 1000, mSeekList
                        .size()));

                if (LOGS_ENABLED) Log.d(TAG, "Adding to mSeekList, timeTracker: "
                        + (ttmlSubtitle.getEventTime(i) / 1000) + " position: "
                        + (mSeekList.size() - 1));
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error in parsing subtitle " + e.getMessage());
        }
    }

    public void setText(String s) {
        mTextView.setText(s);
    }

    public void startRendering(long mediaPlayerCurrentPosition) {
        mBaseTimeMs = SystemClock.uptimeMillis() - mediaPlayerCurrentPosition;
        seekTo(mediaPlayerCurrentPosition);
    }

    public void stopRendering() {
        mRenderer.obtainMessage(MSG_RENDER_NOTHING).sendToTarget();
        mHandler.removeCallbacksAndMessages(null);
    }

    public void seekTo(long seekTimeMs) {
        mHandler.removeCallbacksAndMessages(null);
        mCurrentTimeMs = 0;
        for (TimeAndPosition tap : mSeekList) {
            if (seekTimeMs < tap.timeMs) {
                mSubtitleIndex = tap.position;
                mCurrentTimeMs = mBaseTimeMs + tap.timeMs;
                mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_RENDER_TEXT), tap.timeMs
                        - seekTimeMs + SystemClock.uptimeMillis());
                break;
            }
        }
    }

    public void pause() {
        mHandler.removeCallbacksAndMessages(null);
    }

    public void clearText() {
        mRenderer.obtainMessage(MSG_RENDER_NOTHING).sendToTarget();
    }

    private long checkBaseTime() {
        long baseTimeMs = SystemClock.uptimeMillis() - mMediaPlayer.getCurrentPosition();
        long diff = Math.abs(mBaseTimeMs - baseTimeMs);
        if (diff > FIFTY_MS) {
            if (LOGS_ENABLED) Log.d(TAG, "Adjusting baseTime");
            mBaseTimeMs = baseTimeMs;
            return diff;
        }
        return 0;
    }

    private static class TimeAndPosition {

        public final long timeMs;

        public final int position;

        private TimeAndPosition(long time, int pos) {
            timeMs = time;
            position = pos;
        }
    }

    class EventHandler extends Handler {
        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RENDER_TEXT: {
                    TtmlData data = mStringsAtTimes.get(mSubtitleIndex);
                    long messageDelay = checkBaseTime();
                    if (data.getNextIndex() != -1) {
                        mCurrentTimeMs = mBaseTimeMs
                                + mStringsAtTimes.get(mSubtitleIndex + 1).getDuration();
                        mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_RENDER_TEXT),
                                mCurrentTimeMs);
                        if (LOGS_ENABLED)
                            Log.d(TAG, "delay posted:"
                                    + (mCurrentTimeMs - SystemClock.uptimeMillis()));
                    }
                    mCurrentText = data.getText();
                    if (LOGS_ENABLED) Log.d(TAG, "Setting text to: " + mCurrentText);
                    mRenderer.sendMessageAtTime(mRenderer.obtainMessage(MSG_RENDER_TEXT),
                            SystemClock.uptimeMillis() + messageDelay);
                    mSubtitleIndex = data.getNextIndex();
                    break;
                }
                case MSG_RENDER_NOTHING: {
                    mRenderer.obtainMessage(MSG_RENDER_NOTHING).sendToTarget();
                    break;
                }
                default:
                    break;
            }
        }
    }

    class RenderHandler extends Handler {
        public RenderHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RENDER_TEXT: {
                    if (LOGS_ENABLED) Log.d(TAG, "Rendering text: " + mCurrentText);
                    setText(mCurrentText);
                    break;
                }
                case MSG_RENDER_NOTHING: {
                    setText("");
                    break;
                }
                default:
                    break;
            }
        }
    }
}
