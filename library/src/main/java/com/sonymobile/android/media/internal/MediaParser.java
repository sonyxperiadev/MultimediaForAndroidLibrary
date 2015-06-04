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

    protected ArrayList<Track> mTracks;

    protected boolean mIsParsed;

    protected boolean mParseResult;

    public MediaParser() {

    }

    /**
     * Create a new MediaParser. User is responsible for calling release() on
     * the parser after usage.
     *
     * @param source of the parser.
     */
    public MediaParser(DataSource source) {
        mDataSource = source;
        mTracks = new ArrayList<>();
        mMetaDataValues = new Hashtable<>();
    }

    /**
     * Create a new MediaParser. User is responsible for calling release() on
     * the parser after usage.
     *
     * @param uri to the content.
     * @param maxBufferSize for http content.
     */
    public MediaParser(String uri, int maxBufferSize) throws IOException {
        if (maxBufferSize == -1) {
            maxBufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;
        }
        mDataSource = DataSource.create(uri, maxBufferSize, false);
        mTracks = new ArrayList<>();
        mMetaDataValues = new Hashtable<>();
    }

    /**
     * Create a new MediaParser. User is responsible for calling release() on
     * the parser after usage.
     *
     * @param uri to the content.
     * @param offset to the content.
     * @param length of the content.
     * @param maxBufferSize for http content.
     */
    public MediaParser(String uri, long offset, long length, int maxBufferSize) throws IOException {
        if (maxBufferSize == -1) {
            maxBufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;
        }
        mDataSource = DataSource.create(uri, offset, (int)length, maxBufferSize, null, null, false);
        mTracks = new ArrayList<>();
        mMetaDataValues = new Hashtable<>();
    }

    /**
     * Create a new MediaParser. User is responsible for calling release() on
     * the parser after usage and also provide a valid FileDescriptor during the
     * entire life cycle of the MediaParser as well as closing the provided
     * FileDescriptor after usage.
     *
     * @param fd FileDescriptor to the content.
     * @param offset to the content.
     * @param length of the content.
     */
    public MediaParser(FileDescriptor fd, long offset, long length) {
        mDataSource = DataSource.create(fd, offset, length);
        mTracks = new ArrayList<>();
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
    public int getTrackCount() {
        return mTracks.size();
    }

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
    public MetaData getTrackMetaData(int index) {
        Track t = mTracks.get(index);
        if (t != null) {
            return t.getMetaData();
        }
        return null;
    }

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

    /**
     * Get the track media format.
     *
     * @param trackIndex of the track.
     * @return MediaFormat for the track. If no track is found for the specified
     *         index null is returned.
     */
    public MediaFormat getTrackMediaFormat(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= mTracks.size()) {
            return null;
        }
        return mTracks.get(trackIndex).getMediaFormat();
    }

    /**
     * Dequeues an AccessUnit. This should never be called by the application.
     *
     * @param type the TrackType to dequeue
     * @return the AccessUnit that's dequeued.
     */
    public abstract AccessUnit dequeueAccessUnit(TrackType type);

    /**
     * Interface definition for a generic track.
     */
    public interface Track {
        /**
         * Get the meta data.
         *
         * @return A MetaData object.
         */
        public MetaData getMetaData();

        /**
         * Get the media format.
         *
         * @return MediaFormat for the track.
         */
        public MediaFormat getMediaFormat();

        /**
         * Get the track type.
         *
         * @return TrackType for this track. See {@link TrackType}.
         */
        public TrackType getTrackType();
    }

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

    protected abstract Track createTrack();

    /**
     * Checks if there is data available.
     *
     * @return true if it is, false if it isn't.
     * @throws IOException if something went wrong in the check.
     */
    public abstract boolean hasDataAvailable(TrackType type) throws IOException;

}
