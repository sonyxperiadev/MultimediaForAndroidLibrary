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
 * Class representing DASH track info. For detailed information of each value,
 * refer to the DASH specification.
 */
public class DASHTrackInfo extends TrackInfo {

    private final long mStartTimeUs;

    private final String mAccessibility;

    private final String mRole;

    private final String mRating;

    /**
     * Create a new DASHTrackInfo. Normally a DASHTrackInfo should not be
     * created by the Application.
     *
     * @param trackType The track type.
     * @param mimeType The mime type.
     * @param durationUs Duration of the track in microseconds.
     * @param language Language of the track.
     * @param startTimeUs Start time of the Period that the track belongs to in
     *            microseconds.
     * @param accessibility the accessibility scheme employed.
     * @param role the role of the media content components.
     * @param rating the rating scheme employed.
     */
    public DASHTrackInfo(TrackType trackType, String mimeType, long durationUs, String language,
                         TrackRepresentation[] representations, long startTimeUs,
                         String accessibility, String role, String rating) {
        super(trackType, mimeType, durationUs, language, representations);

        mStartTimeUs = startTimeUs;
        mAccessibility = accessibility;
        mRole = role;
        mRating = rating;
    }

    /**
     * Get the start time of the Period that the track belongs to.
     *
     * @return the start time in microseconds.
     */
    public long getStartTimeUs() {
        return mStartTimeUs;
    }

    /**
     * Get the accessibility scheme employed.
     *
     * @return the accessibility scheme.
     */
    public String getAccessibility() {
        return mAccessibility;
    }

    /**
     * Get the role of the media content components.
     *
     * @return the role scheme.
     */
    public String getRole() {
        return mRole;
    }

    /**
     * Get the rating scheme employed.
     *
     * @return the rating scheme.
     */
    public String getRating() {
        return mRating;
    }
}
