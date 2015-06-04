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

package com.sonymobile.android.media.testmediaplayer.GUI;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.testmediaplayer.PlayerConfiguration;

import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class TimeTracker {
    private static final boolean LOGS_ENABLED = PlayerConfiguration.DEBUG || false;

    private static final String TAG = "DEMOAPPLICATION_TIMETRACKER";
    private final TextView mTextView;
    private final Timer timer;
    private MediaPlayer mMediaPlayer;
    private final Handler mHandler;
    private TimerTaskUpdater taskUpdater;

    private final Runnable r;

    public TimeTracker(MediaPlayer mp, TextView txt) {
        mMediaPlayer = mp;
        mTextView = txt;
        mHandler = new Handler();
        timer = new Timer();
        r = new Runnable() {
            @Override
            public void run() {
                int position = mMediaPlayer.getCurrentPosition();
                if (position > 0) {
                    mTextView.setText(String.format(
                            "%02d:%02d:%02d",
                            TimeUnit.MILLISECONDS.toHours(position),
                            TimeUnit.MILLISECONDS.toMinutes(position)
                                    - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS
                                            .toHours(position)),
                            TimeUnit.MILLISECONDS.toSeconds(position)
                                    - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS
                                            .toMinutes(position))));
                }
                if (LOGS_ENABLED) Log.d(TAG, "time is: " + mTextView.getText());
            }
        };
    }

    public void startUpdating(){
        taskUpdater = new TimerTaskUpdater();
        timer.scheduleAtFixedRate(taskUpdater, 10, 1000);
    }

    public void stopUpdating(){
        if (taskUpdater != null) {
            taskUpdater.cancel();
            taskUpdater = null;
        }
    }

    public void setMediaPlayer(MediaPlayer mp) {
        mMediaPlayer = mp;
    }

    private class TimerTaskUpdater extends TimerTask{

        @Override
        public void run() {
            mHandler.post(r);
        }

    }
}
