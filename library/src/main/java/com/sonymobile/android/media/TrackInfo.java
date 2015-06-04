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
 * Class holding relevant information about a track.
 */
public class TrackInfo {

    /**
     * Track types used to differentiate the different type of tracks.
     */
    public enum TrackType {
        /**
         * Indicates an Audio track.
         */
        AUDIO,
        /**
         * Indicates a Video track.
         */
        VIDEO,
        /**
         * Indicates a subtitle track.
         */
        SUBTITLE,
        /**
         * Indicates an unknown track.
         */
        UNKNOWN
    }

    private final TrackType mTrackType;

    private final String mMimeType;

    private final long mDurationUs;

    private final String mLanguage;

    private final TrackRepresentation[] mRepresentations;

    /**
     * Create a new track info. TrackInfo should normally not be created by
     * Applications.
     *
     * @param trackType the track type.
     * @param mimeType the mime type.
     * @param durationUs duration of the track, in microseconds.
     * @param language language of the track.
     */
    public TrackInfo(TrackType trackType, String mimeType, long durationUs, String language,
                     TrackRepresentation[] representations) {
        mTrackType = trackType;
        mMimeType = mimeType;
        mDurationUs = durationUs;
        mLanguage = language;
        mRepresentations = representations.clone();
    }

    /**
     * Get the track type.
     *
     * @return the track type.
     */
    public TrackType getTrackType() {
        return mTrackType;
    }

    /**
     * Get the mime type.
     *
     * @return the mime type.
     */
    public String getMimeType() {
        return mMimeType;
    }

    /**
     * Get the duration.
     *
     * @return the duration, in microseconds.
     */
    public long getDurationUs() {
        return mDurationUs;
    }

    /**
     * Get the language.
     *
     * @return the language.
     */
    public String getLanguage() {
        return mLanguage;
    }

    /**
     * Get the track representations.
     *
     * @return The track representations, null if they have not been set before.
     */
    public TrackRepresentation[] getRepresentations() {
        return mRepresentations.clone();
    }
}
