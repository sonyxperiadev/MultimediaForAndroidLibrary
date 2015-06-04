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

import java.util.Arrays;

import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.Util;

/**
 * Class to describe Subtitle Data.
 */
public class SubtitleData {
    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SubtitleData";

    private final int mTrackIndex;

    private final long mStartTimeUs;

    private final long mDurationUs;

    private final byte[] mData;

    /**
     * Creates a new SubtitleData object. SubtitleData should normally not be
     * created by Applications.
     *
     * @param index of the Track for this subtitle.
     * @param startTimeUs start time in microseconds for the subtitle.
     * @param durationUs duration in microseconds for the subtitle.
     * @param data the subtitle data.
     * @param size size of the data.
     */
    public SubtitleData(int index, long startTimeUs, long durationUs, byte[] data, int size) {
        mTrackIndex = index;
        mStartTimeUs = startTimeUs;
        mDurationUs = durationUs;
        mData = Arrays.copyOf(data, size);
    }

    /**
     * Gets the track index.
     *
     * @return the index no. of the track.
     */
    public int getTrackIndex() {
        return mTrackIndex;
    }

    /**
     * Gets the start time for this subtitle.
     *
     * @return Start time for subtitle in microseconds.
     */
    public long getStartTimeUs() {
        return mStartTimeUs;
    }

    /**
     * Gets the duration of the subtitle.
     *
     * @return duration of subtitle in microseconds.
     */
    public long getDurationUs() {
        return mDurationUs;
    }

    /**
     * Gets subtitle data.
     *
     * @return the subtitle data.
     */
    public byte[] getData() {
        return mData.clone();
    }

    /**
     * Get this subtitle object as a string, for debugging purposes.
     */
    public String toString() {
        return "Track " + mTrackIndex +
                ", From " + mStartTimeUs +
                " Us, For " + mDurationUs +
                " Us, " + mData.length + " bytes, Starting with 0x" +
                Util.bytesToHex(mData, 0, (mData.length < 8 ? mData.length : 8));
    }
}
