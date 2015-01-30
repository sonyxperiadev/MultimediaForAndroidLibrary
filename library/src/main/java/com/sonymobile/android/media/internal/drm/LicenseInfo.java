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

package com.sonymobile.android.media.internal.drm;

public class LicenseInfo {

    protected static final String OPL_COMPRESSED_DIGITAL_AUDIO =
            "opl_compressed_digital_audio_output";

    protected static final String OPL_UNCOMPRESSED_DIGITAL_AUDIO =
            "opl_uncompressed_digital_audio_output";

    protected static final String OPL_COMPRESSED_DIGITAL_VIDEO =
            "opl_compressed_digital_video_output";

    protected static final String OPL_UNCOMPRESSED_DIGITAL_VIDEO =
            "opl_uncompressed_digital_video_output";

    protected static final String PLAYENABLER_TYPE = "player_enabler_type";

    protected static final String VALID_RINGTONE_LICENSE = "valid_ringtone_license";

    private int mOplUncompressedDigitalAudio;

    private int mOplCompressedDigitalAudio;

    private int mOplUncompressedDigitalVideo;

    private int mOplCompressedDigitalVideo;

    private boolean mPlayEnabler;

    private boolean mRingtoneAllowed;

    public LicenseInfo() {
        setOplValues(0, 0, 0, 0);
        setPlayEnable(true);
        setAllowRingtone(true);
    }

    protected void setOplValues(int compressedDigiatalAudio, int unCompressedDigiatalAudio,
                int compressedDigiatalVideo, int uncompressedDigiatalVideo) {
        mOplCompressedDigitalAudio = compressedDigiatalAudio;
        mOplUncompressedDigitalAudio = unCompressedDigiatalAudio;
        mOplCompressedDigitalVideo = compressedDigiatalVideo;
        mOplUncompressedDigitalVideo = uncompressedDigiatalVideo;
    }

    protected void setPlayEnable(boolean enabled) {
        mPlayEnabler = enabled;
    }

    protected void setAllowRingtone(boolean allow) {
        mRingtoneAllowed = allow;
    }

    protected int getOplCompressedDigitalAudio() {
        return mOplCompressedDigitalAudio;
    }

    protected int getOplUncompressedDigitalAudio() {
        return mOplUncompressedDigitalAudio;
    }

    protected int getOplCompressedDigitalVideo() {
        return mOplCompressedDigitalVideo;
    }

    protected int getOplUncompressedDigitalVideo() {
        return mOplUncompressedDigitalVideo;
    }

    protected boolean getPlayEnable() {
        return mPlayEnabler;
    }

    protected boolean isRingtoneAllowed() {
        return mRingtoneAllowed;
    }

    public String toString() {
        return "OPL Compressed Digital Audio = " +mOplCompressedDigitalAudio+ " "+
                "OPL Uncompressed Digital Audio = " +mOplUncompressedDigitalAudio+ " "+
                "OPL Compressed Digital Video = " +mOplCompressedDigitalVideo+ " "+
                "OPL Uncompressed Digital Video = " +mOplUncompressedDigitalVideo+ " "+
                "OPL Playenabler = " +mPlayEnabler+ " "+
                "OPL Allow ringtone = " +mRingtoneAllowed;

    }
}
