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
 * Class representing a video track.
 */
public class VideoTrackRepresentation extends TrackRepresentation {

    private final int mWidth;

    private final int mHeight;

    private final float mFrameRate;

    /**
     * Create a new VideoTrackRepresentation. VideoTrackRepresentation should
     * normally not be created by Applications.
     *
     * @param bitrate the bitrate of the video track.
     * @param width the width of the video track.
     * @param height the height of the video track.
     * @param frameRate the frame rate for the video.
     */
    public VideoTrackRepresentation(int bitrate, int width, int height, float frameRate) {
        super(bitrate);

        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
    }

    /**
     * Get the width for this video track.
     *
     * @return the width.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Get the height for this video track.
     *
     * @return the height.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * Get the frame rate for this video track.
     *
     * @return the frame rate. Returns 0 if frame rate is not available.
     */
    public float getFrameRate() {
        return mFrameRate;
    }
}
