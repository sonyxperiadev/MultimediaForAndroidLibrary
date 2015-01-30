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

import java.util.HashMap;

import android.text.format.Time;

public class TestContent {

    private String mContentUri;

    private String mId;

    private int mWidth;

    private int mHeight;

    private int mBitrate;

    private int mSubtitleDataLength;

    private int mSubtitleLengthInterval;

    private int mTrackCount;

    private int mSubtitleTrack;

    private Time mDuration;

    private float mFramerate;

    private int mMaxIFrameInterval;

    private long mOffset;

    private long mLength;

    private String mMimeType;

    private String mTrackMimeTypeAudio;

    private String mTrackMimeTypeVideo;

    private HashMap<String, String> mMetaDataValues;

    public final static String ID_TYPE_LOCAL = "LOCAL";

    public final static String ID_TYPE_LOCAL_HEVC = "LOCAL_HEVC";

    public final static String ID_TYPE_HTTP = "HTTP";

    public final static String ID_TYPE_DASH = "DASH";

    public final static String ID_TYPE_MEDIASTORE = "MEDIA_STORE";

    public final static String ID_TYPE_INVALID = "INVALID";

    public final static String ID_TYPE_LOCAL_WITH_SUBTITLE_TTML = "LOCAL_WITH_SUBTITLE_TTML";

    public final static String ID_TYPE_LOCAL_WITH_SUBTITLE_GRAP = "LOCAL_WITH_SUBTITLE_GRAP";

    public final static String ID_TYPE_LOCAL_WITH_OFFSET = "LOCAL_WITH_OFFSET";

    public final static String ID_TYPE_LOCAL_WITH_SAMPLE_ASPECT_RATIO =
            "LOCAL_WITH_SAMPLE_ASPECT_RATIO";

    public final static String ID_TYPE_LOCAL_WITH_METADATA = "LOCAL_WITH_METADATA";

    public final static String ID_TYPE_LOCAL_NO_MFRA = "LOCAL_NO_MFRA";

    public TestContent() {
        mMetaDataValues = new HashMap<String, String>();
    }

    public String getContentUri() {
        return mContentUri;
    }

    public void setContentUri(String contentUri) {
        this.mContentUri = contentUri;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int height) {
        this.mHeight = height;
    }

    public float getFramerate() {
        return mFramerate;
    }

    public void setFramerate(float framerate) {
        this.mFramerate = framerate;
    }

    public int getBitrate() {
        return mBitrate;
    }

    public void setBitrate(int bitrate) {
        this.mBitrate = bitrate;
    }

    public int getDuration() {
        return mDuration.hour * 60 * 60 * 1000 + mDuration.minute * 60 * 1000 + mDuration.second
                * 1000;
    }

    public void setDuration(Time duration) {
        this.mDuration = duration;
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        this.mId = id;
    }

    public void setMaxIFrameInterval(int maxIFrameInterval) {
        mMaxIFrameInterval = maxIFrameInterval;
    }

    public int getMaxIFrameInterval() {
        return mMaxIFrameInterval;
    }

    public long getOffset() {
        return mOffset;
    }


    public void setOffset(long mOffset) {
        this.mOffset = mOffset;
    }

    public long getLength() {
        return mLength;
    }

    public void setLength(long mLength) {
        this.mLength = mLength;
    }

    public void setSubtitleDataLength(int length) {
        mSubtitleDataLength = length;
    }

    public int getSubtitleDataLength() {
        return mSubtitleDataLength;
    }

    public void setSubtitleLengthInterval(int length) {
        mSubtitleLengthInterval = length;
    }

    public int getSubtitleLengthInterval() {
        return mSubtitleLengthInterval;
    }

    public void setSubtitleTrack(int track) {
        mSubtitleTrack = track;
    }

    public int getSubtitleTrack() {
        return mSubtitleTrack;
    }

    public int getTrackCount() {
        return mTrackCount;
    }

    public void setTrackCount(int mTrackCount) {
        this.mTrackCount = mTrackCount;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setMimeType(String mMimeType) {
        this.mMimeType = mMimeType;
    }

    public String getTrackMimeTypeAudio() {
        return mTrackMimeTypeAudio;
    }

    public void setTrackMimeTypeAudio(String mTrackMimeTypeAudio) {
        this.mTrackMimeTypeAudio = mTrackMimeTypeAudio;
    }

    public String getTrackMimeTypeVideo() {
        return mTrackMimeTypeVideo;
    }

    public void setTrackMimeTypeVideo(String mTrackMimeTypeVideo) {
        this.mTrackMimeTypeVideo = mTrackMimeTypeVideo;
    }

    public void setMetaDataValue(String key, String value) {
        mMetaDataValues.put(key, value);
    }

    public String getMetaDataValue(String key) {
        return mMetaDataValues.get(key);
    }

}
