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

package com.sonymobile.android.media;

/**
 * Class representing an audio track.
 */
public class AudioTrackRepresentation extends TrackRepresentation {

    private final int mChannelCount;

    private final String mChannelConfiguration;

    private final int mSampleRate;

    /**
     * Create a new audio track representation. AudioTrackRepresentation should
     * not normally be created by Applications.
     *
     * @param bitrate the bitrate of the audiotrack.
     * @param channelCount number of channels of the audiotrack.
     * @param channelConfiguration describes the configuration of the audio
     *            channels. See
     *            {@link android.media.AudioFormat#CHANNEL_OUT_MONO} and
     *            {@link android.media.AudioFormat#CHANNEL_OUT_STEREO}.
     * @param sampleRate samplerate of the audiotrack, in Hertz.
     */
    public AudioTrackRepresentation(int bitrate, int channelCount, String channelConfiguration,
            int sampleRate) {
        super(bitrate);

        mChannelCount = channelCount;
        mChannelConfiguration = channelConfiguration;
        mSampleRate = sampleRate;
    }

    /**
     * Get the channel count.
     *
     * @return channel count. Returns 0 if channel count is not available.
     */
    public int getChannelCount() {
        return mChannelCount;
    }

    /**
     * Get the channel configuration.
     *
     * @return the channel configuration.
     */
    public String getChannelConfiguration() {
        return mChannelConfiguration;
    }

    /**
     * Get the sample rate.
     *
     * @return the sample rate.
     */
    public int getSampleRate() {
        return mSampleRate;
    }
}
