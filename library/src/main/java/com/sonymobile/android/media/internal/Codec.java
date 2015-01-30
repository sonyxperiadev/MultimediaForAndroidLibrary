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

public interface Codec {

    public static final int CODEC_ERROR = -1;

    public static final int CODEC_AUDIO_COMPLETED = 1;

    public static final int CODEC_VIDEO_COMPLETED = 2;

    public static final int CODEC_SUBTITLE_DATA = 3;

    public static final int CODEC_VIDEO_FORMAT_CHANGED = 4;

    public static final int CODEC_NOTIFY_POSITION = 6;

    public static final int CODEC_FLUSH_COMPLETED = 7;

    public static final int CODEC_VIDEO_RENDERING_START = 8;

    public static final int CODEC_VIDEO_SEEK_COMPLETED = 9;
}
