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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

public class AudioSessionManager {

    private static final boolean DEBUG_ENABLE = Configuration.DEBUG || false;

    private static final String TAG = "AudioSessionManager";

    public static int generateNewAudioSessionId(Context context) {

        int audioSessionId = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && context != null) {
            audioSessionId = generateNewAudioSessionIdLollipop(context);
        }

        // Fallback function is used if:
        // * We are not running on a Lollipop device.
        // * Context is null.
        // * We failed to get a audioSessionId from AudioManager on a Lollipop
        // device.
        if (audioSessionId == 0) {
            audioSessionId = generateNewAudioSessionIdFallback();
        }

        return audioSessionId;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static int generateNewAudioSessionIdLollipop(Context context) {
        // Function for Lollipop.

        if (context != null) {
            AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                return am.generateAudioSessionId();
            }
        }

        return 0;
    }

    private static int generateNewAudioSessionIdFallback() {
        // Fallback function, see criteria in generateNewAudioSessionId.

        AudioTrack dummyTrack = null;
        try {
            int sampleRate = 8000;
            int minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_8BIT);

            dummyTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT, minBuffer,
                    AudioTrack.MODE_STREAM);

            return dummyTrack.getAudioSessionId();

        } catch (IllegalArgumentException e) {
            if (DEBUG_ENABLE) Log.e(TAG, "Failed to create a dummy audio track", e);
        } finally {
            if (dummyTrack != null) {
                dummyTrack.release();
                dummyTrack = null;
            }
        }

        return 0;
    }
}
