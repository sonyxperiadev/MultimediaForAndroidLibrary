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

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import android.media.MediaFormat;

import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.MetaDataParser;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;

/**
 * Class representing a mediaparser.
 */
abstract public class MediaParser implements MetaDataParser, MetaData {

    protected DataSource mDataSource;

    protected long mCurrentOffset;

    protected boolean mInitDone;

    protected Hashtable<String, Object> mMetaDataValues;

    protected boolean mIsParsed;

    protected boolean mParseResult;

    public MediaParser() {
        mMetaDataValues = new Hashtable<>();
    }

    /**
     * Create a new MediaParser. User is responsible for calling release() on
     * the parser after usage.
     *
     * @param source of the parser.
     */
    public MediaParser(DataSource source) {
        mDataSource = source;
        mMetaDataValues = new Hashtable<>();
    }

    /**
     * Parses the media.
     *
     * @return true if successful, false if not.
     */
    public abstract boolean parse();

    /**
     * Get the track count.
     *
     * @return the track count.
     */
    @Override
    public abstract int getTrackCount();

    /**
     * Get the meta data.
     *
     * @return A MetaData object.
     */
    @Override
    public MetaData getMetaData() {
        return this;
    }

    /**
     * Get the track meta data.
     *
     * @param index of the track.
     * @return the track meta data. If no track is found for specified index,
     *         null is returned.
     */
    @Override
    public abstract MetaData getTrackMetaData(int index);

    /**
     * Releases this parser and closes open resources used by it.
     */
    @Override
    public void release() {
        if (mDataSource != null) {
            try {
                mDataSource.close();
            } catch (IOException e) {
            }
        }
    }

    protected void addMetaDataValue(String key, Object value) {
        // Hashtable doesn't allow null values.
        if (value != null) {
            mMetaDataValues.put(key, value);
        }
    }

    /* MetaData interface functions */

    public int getInteger(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Integer val = (Integer)mMetaDataValues.get(key);
            return val.intValue();
        }
        return Integer.MIN_VALUE;
    }

    public long getLong(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Long val = (Long)(mMetaDataValues.get(key));
            return val.longValue();
        }
        return Long.MIN_VALUE;
    }

    public float getFloat(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Float val = (Float)(mMetaDataValues.get(key));
            return val.floatValue();
        }
        return Float.MIN_VALUE;
    }

    public String getString(String key) {
        Object value = mMetaDataValues.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public String getString(String key1, String key2) {
        return null;
    }

    public byte[] getByteBuffer(String key) {
        return (byte[])(mMetaDataValues.get(key));
    }

    public byte[] getByteBuffer(String key1, String key2) {
        return null;
    }

    public String[] getStringArray(String key) {
        if (mMetaDataValues.containsKey(key)) {
            Object[] values = (Object[])mMetaDataValues.get(key);
            String[] strings = new String[values.length];
            System.arraycopy(values, 0, strings, 0, values.length);
            return strings;
        }
        return null;
    }

    public boolean containsKey(String key) {
        return mMetaDataValues.containsKey(key);
    }

    /**
     * Dequeues an AccessUnit. This should never be called by the application.
     *
     * @param type the TrackType to dequeue
     * @return the AccessUnit that's dequeued.
     */
    public abstract AccessUnit dequeueAccessUnit(TrackType type);

    /**
     * Get the duration in microseconds.
     *
     * @return the duration in microseconds.
     */
    public abstract long getDurationUs();

    /**
     * Get the MediaFormat.
     *
     * @param type the TrackType.
     * @return the MediaFormat.
     */
    public abstract MediaFormat getFormat(TrackType type);

    /**
     * Get the track info.
     *
     * @return the TrackInfo array.
     */
    public abstract TrackInfo[] getTrackInfo();

    /**
     * Seek to current time.
     *
     * @param seekTimeUs the time to seek to, in microseconds.
     */
    public abstract void seekTo(long seekTimeUs);

    /**
     * Selects or deselects a track.
     *
     * @param select true for select, false for deselect.
     * @param index index of the track.
     * @return the TrackType for selected/deselected track.
     */
    public abstract TrackType selectTrack(boolean select, int index);

    /**
     * Get the selected track index for this tracktype.
     *
     * @param type the TrackType.
     * @return index of selected track.
     */
    public abstract int getSelectedTrackIndex(TrackType type);

    /**
     * Checks weather a parse is possible.
     *
     * @return true if successful, false if not.
     */
    public abstract boolean canParse();

    /**
     * Checks if there is data available.
     *
     * @return true if it is, false if it isn't.
     * @throws IOException if something went wrong in the check.
     */
    public abstract boolean hasDataAvailable(TrackType type) throws IOException;

    /**
     * Query the source about how much data is played and buffered
     *
     * @return percentage of the file duration that is played or buffered
     */
    public int getBuffering() {
        if (mDataSource == null || !(mDataSource instanceof HttpBufferedDataSource)) {
            return 100;
        }

        return ((HttpBufferedDataSource)mDataSource).getBuffering();
    }

    /**
     * Check if the playback needs to be stalled to wait for more data
     *
     * @return true if more data is needed before playback can continue, false otherwise.
     */
    public boolean needMoreBuffer() {
        try {
            if (mDataSource instanceof HttpBufferedDataSource) {
                HttpBufferedDataSource httpBufferedDataSource =
                        (HttpBufferedDataSource) mDataSource;
                return httpBufferedDataSource.getBufferedSize() <
                        (((double)httpBufferedDataSource.length() /
                                getDurationUs()) *
                                Configuration.HTTP_MIN_BUFFERING_DURATION_US)
                        && !httpBufferedDataSource.isAtEndOfStream();
            }
        } catch (IOException e) {
        }

        return false;
    }

}
