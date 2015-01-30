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

public abstract class TestHelper {

    public static final int METHOD_SET_DATA_SOURCE = 1;

    public static final int METHOD_RELEASE = 2;

    public static final int METHOD_RESET = 3;

    public static final int METHOD_PREPARE = 4;

    public static final int METHOD_PREPARE_ASYNC = 5;

    public static final int METHOD_PLAY = 6;

    public static final int METHOD_SEEK_TO = 7;

    public static final int METHOD_PAUSE = 8;

    public static final int METHOD_STOP = 9;

    public static final int METHOD_GET_CURRENT_POSITION = 10;

    public static final int METHOD_GET_DURATION = 11;

    public static final int METHOD_GET_VIDEO_HEIGHT = 12;

    public static final int METHOD_GET_VIDEO_WIDTH = 13;

    public static final int METHOD_GET_AUDIO_SESSION_ID = 14;

    public static final int METHOD_SET_AUDIO_SESSION_ID = 15;

    public static final int METHOD_SET_DISPLAY = 16;

    public static final int METHOD_SET_VIDEO_SCALING_MODE = 17;

    public static final int METHOD_SET_ON_BUFFERING_UPDATE_LISTENER = 18;

    public static final int METHOD_SET_ON_COMPLETION_LISTENER = 19;

    public static final int METHOD_SET_ON_ERROR_LISTENER = 20;

    public static final int METHOD_SET_ON_PREPARED_LISTENER = 21;

    public static final int METHOD_SET_ON_SEEK_COMPLETE_LISTENER = 22;

    public static final int METHOD_SET_VOLUME = 23;

    public static final int METHOD_SET_WAKE_MODE = 24;

    public static final int METHOD_GET_TRACK_INFO = 25;

    public static final int METHOD_SELECT_TRACK = 26;

    public static final int METHOD_DESELECT_TRACK = 27;

    public static final int METHOD_GET_STATE = 28;

    public static final int METHOD_SET_SPEED = 29;

    public static final int METHOD_GET_STATISTICS = 30;

    public static final int METHOD_SET_DATA_SOURCE_OFFSET_LENGTH = 31;

    public static final int METHOD_SET_DATA_SOURCE_CONTEXT_URI = 32;

    public static final int METHOD_SET_DATA_SOURCE_FD = 33;

    public static final int METHOD_SET_DATA_SOURCE_FD_OFFSET_LENGTH = 34;

    public static final int METHOD_GET_MEDIA_METADATA = 35;

    public static final int STATE_IDLE = 101;

    public static final int STATE_INITIALIZED = 102;

    public static final int STATE_PREPARED = 103;

    public static final int STATE_PLAYING = 104;

    public static final int STATE_PAUSED = 105;

    public static final int STATE_COMPLETED = 106;

    public static final int STATE_END = 107;

    public static final int STATE_ERROR = 108;

    public static final int STATE_PREPARING = 109;

    protected static String getString(int code) {
        switch (code) {
            case METHOD_SET_DATA_SOURCE:
                return "setDataSource";
            case METHOD_RELEASE:
                return "release";
            case METHOD_RESET:
                return "reset";
            case METHOD_PREPARE:
                return "prepare";
            case METHOD_PREPARE_ASYNC:
                return "prepareAsync";
            case METHOD_PLAY:
                return "play";
            case METHOD_SEEK_TO:
                return "seekTo";
            case METHOD_PAUSE:
                return "pause";
            case METHOD_STOP:
                return "stop";
            case METHOD_GET_CURRENT_POSITION:
                return "getCurrentPosition";
            case METHOD_GET_DURATION:
                return "getDuration";
            case METHOD_GET_VIDEO_HEIGHT:
                return "getVideoHeight";
            case METHOD_GET_VIDEO_WIDTH:
                return "getVideoWidth";
            case METHOD_GET_AUDIO_SESSION_ID:
                return "getAudioSessionId";
            case METHOD_SET_AUDIO_SESSION_ID:
                return "setAudioSessionId";
            case METHOD_SET_DISPLAY:
                return "setDisplay";
            case METHOD_SET_VIDEO_SCALING_MODE:
                return "setVideoScalingMode";
            case METHOD_SET_ON_BUFFERING_UPDATE_LISTENER:
                return "setOnBufferingUpdateListener";
            case METHOD_SET_ON_COMPLETION_LISTENER:
                return "setOnCompletionListener";
            case METHOD_SET_ON_ERROR_LISTENER:
                return "setOnErrorListener";
            case METHOD_SET_ON_PREPARED_LISTENER:
                return "setOnPreparedListener";
            case METHOD_SET_ON_SEEK_COMPLETE_LISTENER:
                return "setOnSeekCompleteListener";
            case METHOD_SET_VOLUME:
                return "setVolume";
            case METHOD_SET_WAKE_MODE:
                return "setWakeMode";
            case METHOD_GET_TRACK_INFO:
                return "getTrackInfo";
            case METHOD_SELECT_TRACK:
                return "selectTrack";
            case METHOD_DESELECT_TRACK:
                return "deselectTrack";
            case METHOD_GET_STATE:
                return "getState";
            case METHOD_SET_SPEED:
                return "setSpeed";
            case METHOD_GET_STATISTICS:
                return "getStatistics";
            case METHOD_SET_DATA_SOURCE_OFFSET_LENGTH:
                return "setDataSourceOffsetLength";
            case METHOD_SET_DATA_SOURCE_CONTEXT_URI:
                return "setDataSourceContextUri";
            case METHOD_SET_DATA_SOURCE_FD:
                return "setDataSourceFD";
            case METHOD_SET_DATA_SOURCE_FD_OFFSET_LENGTH:
                return "setDataSourceFDOffsetLength";
            case METHOD_GET_MEDIA_METADATA:
                return "getMediaMetaData";
            case STATE_IDLE:
                return "IDLE";
            case STATE_INITIALIZED:
                return "INITIALIZED";
            case STATE_PREPARED:
                return "PREPARED";
            case STATE_PLAYING:
                return "PLAYING";
            case STATE_PAUSED:
                return "PAUSED";
            case STATE_COMPLETED:
                return "COMPLETED";
            case STATE_END:
                return "END";
            case STATE_ERROR:
                return "ERROR";
            case STATE_PREPARING:
                return "PREPARING";
            default:
                return "noSuchMethodOrState";
        }
    }

}
