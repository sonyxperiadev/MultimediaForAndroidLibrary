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
 * Class representing a generic track.
 */
public class TrackRepresentation {

    private final int mBitrate;

    /**
     * Creates a new TrackRepresentation. TrackRepresentation should normally
     * not be created by Applications.
     *
     * @param bitrate for the trackrepresentation.
     */
    public TrackRepresentation(int bitrate) {
        mBitrate = bitrate;
    }

    /**
     * Get the bitrate.
     *
     * @return the bitrate. Returns 0 if bitrate is not available
     */
    public int getBitrate() {
        return mBitrate;
    }
}
