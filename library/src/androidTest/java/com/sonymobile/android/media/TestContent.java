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

    private int mWidth = -1;

    private int mHeight = -1;

    private int mRotation = -1;

    private int mBitrate = -1;

    private int mSubtitleDataLength = -1;

    private int mSubtitleLengthInterval = -1;

    private int mTrackCount = -1;

    private int mSubtitleTrack = -1;

    private Time mDuration;

    private float mFramerate = -1;

    private int mMaxIFrameInterval = -1;

    private long mOffset = -1;

    private long mLength = 1;

    private String mMimeType;

    private String mTrackMimeTypeAudio;

    private String mTrackMimeTypeVideo;

    private String mProtocolType;

    private String mContentType;

    private int mAlbumArtSize = -1;

    private int mAlbumArtWidth = -1;

    private int mAlbumArtHeight = -1;

    private final HashMap<String, Object> mMetaDataValues;

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

    public final static String ID_TYPE_LOCAL_WITH_ROTATION = "LOCAL_WITH_ROTATION";

    public final static String ID_TYPE_LOCAL_WITH_ALBUMART = "LOCAL_WITH_ALBUMART";

    public final static String ID_TYPE_LOCAL_NO_MFRA = "LOCAL_NO_MFRA";

    public static final String ID_TYPE_LOCAL_AMR_NB = "LOCAL_AMR_NB";

    public static final String ID_TYPE_LOCAL_AMR_WB = "LOCAL_AMR_WB";

    public static final String ID_TYPE_LOCAL_WITH_SONY_MOBILE_FLAGS =
            "LOCAL_WITH_SONY_MOBILE_FLAGS";

    public TestContent() {
        mMetaDataValues = new HashMap<>();
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

    public void setRotation(int rotation) {
        mRotation = rotation;
    }

    public int getRotation() {
        return mRotation;
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
        if (mDuration == null) {
            return -1;
        }
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

    public void setMetaDataValue(String key, Object value) {
        mMetaDataValues.put(key, value);
    }

    public Object getMetaDataValue(String key) {
        return mMetaDataValues.get(key);
    }

    public void setContentType(String contentType) {
        mContentType = contentType;
    }

    public String getContentType() {
        return mContentType;
    }

    public void setProtocolType(String protocolType) {
        mProtocolType = protocolType;
    }

    public String getProtocolType() {
        return mProtocolType;
    }

    public void setAlbumArtSize(int albumArtSize) {
        mAlbumArtSize = albumArtSize;
    }

    public int getAlbumArtSize() {
        return mAlbumArtSize;
    }

    public void setAlbumArtWidth(int albumArtWidth) {
        mAlbumArtWidth = albumArtWidth;
    }

    public int getAlbumArtWidth() {
        return mAlbumArtWidth;
    }

    public void setAlbumArtHeight(int albumArtHeight) {
        mAlbumArtHeight = albumArtHeight;
    }

    public int getAlbumArtHeight() {
        return mAlbumArtHeight;
    }

}
