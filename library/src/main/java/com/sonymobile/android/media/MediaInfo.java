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
 * Class holding media information. Used in OnInfoLister for callbacks.
 */
public class MediaInfo {
    /**
     * Unspecified media player info.
     */
    public static final int UNKNOWN = 1;

    /**
     * The player just pushed the very first video frame for rendering.
     */
    public static final int VIDEO_RENDERING_START = 3;

    /**
     * MediaPlayer is temporarily pausing playback internally in order to buffer
     * more data.
     */
    public static final int BUFFERING_START = 701;

    /**
     * MediaPlayer is resuming playback after filling buffers.
     */
    public static final int BUFFERING_END = 702;

    /**
     * The media cannot be seeked (e.g live stream)
     */
    public static final int NOT_SEEKABLE = 801;

    /**
     * A new set of metadata is available.
     */
    public static final int METADATA_UPDATE = 802;

    /**
     * Subtitle track was not supported by the media framework.
     */
    public static final int UNSUPPORTED_SUBTITLE = 901;

    /**
     * Reading the subtitle track takes too long.
     */
    public static final int SUBTITLE_TIMED_OUT = 902;

    /**
     * DLNA Play speed is not supported
     */
    public static final int PLAY_SPEED_NOT_SUPPORTED = 1001;
}
