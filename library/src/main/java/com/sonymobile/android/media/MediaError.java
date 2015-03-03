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
 * Class used to describe the media errors. Used in OnErrorListener for
 * callbacks.
 */
public class MediaError {

    /**
     * Unspecified media player error.
     */
    public static final int UNKNOWN = 1;

    /**
     * Error indicating that a function was called in an invalid state.
     */
    public static final int INVALID_STATE = 2;

    /**
     * Media server died. Calling reset() on the MediaPlayer object will make it
     * usable again.
     */
    public static final int SERVER_DIED = 100;

    /**
     * The video is streamed and its container is not valid for progressive
     * playback i.e the video's index (e.g moov atom) is not at the start of the
     * file.
     */
    public static final int NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;

    /** File or network related operation errors. */
    public static final int IO = -1004;

    /**
     * Bitstream is not conforming to the related coding standard or file spec.
     */
    public static final int MALFORMED = -1007;

    /**
     * Bitstream is conforming to the related coding standard or file spec, but
     * the media framework does not support the feature.
     */
    public static final int UNSUPPORTED = -1010;

    /**
     * Some operation takes too long to complete, usually more than 3-5 seconds.
     */
    public static final int TIMED_OUT = -110;

    /**
     * Unspecified drm error.
     */
    public static final int DRM_UNKNOWN = -2000;

    /**
     * No valid license exists.
     */
    public static final int DRM_NO_LICENSE = -2001;

    /**
     * License is expired
     */
    public static final int DRM_LICENSE_EXPIRED = -2002;

    /**
     * No drm session is open.
     */
    public static final int DRM_SESSION_NOT_OPENED = -2003;

    /**
     * License is valid in the future.
     */
    public static final int DRM_LICENSE_FUTURE = -2004;

    /**
     * The output protection is insufficient to be able to render the content on an external
     * device.
     */
    public static final int DRM_INSUFFICIENT_OUTPUT_PROTECTION = -2005;

}
