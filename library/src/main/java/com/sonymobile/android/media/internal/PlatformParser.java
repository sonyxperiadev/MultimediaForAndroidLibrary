/*
 * Copyright (C) 2015 Sony Mobile Communications Inc.
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
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;
import com.sonymobile.android.media.internal.streaming.common.PacketSource;

import static com.sonymobile.android.media.TrackInfo.TrackType;

public final class PlatformParser extends MediaParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "PlatformParser";

    private static final int DEFAULT_AUDIO_BUFFER_SIZE = 8192 * 4;

    private static final int MSG_READ_DATA = 11;

    private static final int MSG_SEEK = 12;

    private static final int MIN_BUFFER_DURATION_US = 2000000;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private String mUri = null;

    private final MediaExtractor mExtractor = new MediaExtractor();

    private EnumMap<TrackType, PacketSource> mPacketSources = new EnumMap<>(TrackType.class);

    private TrackInfo[] mTrackInfos;

    private final int[] mSelectedTracks = new int[TrackType.UNKNOWN.ordinal()];

    private ByteBuffer mInputBuffer;

    private long mDurationUs;

    private int mMaxBufferSize;

    private boolean mEOS = false;

    private long mLastTimestamp;

    private FileDescriptor mFD;

    private long mFDOffset;

    private long mFDLength;

    private final ArrayList<MetaData> mTrackMetadata = new ArrayList<>();

    public PlatformParser(String uri, int maxBufferSize) {
        mUri = uri;
        mMaxBufferSize = maxBufferSize;

        setup();
    }

    public PlatformParser(FileDescriptor fd, long offset, long length) {
        mFD = fd;
        mFDOffset = offset;
        mFDLength = length;
        mMaxBufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;

        setup();
    }

    private void setup() {
        mSelectedTracks[TrackType.AUDIO.ordinal()] = -1;
        mSelectedTracks[TrackType.VIDEO.ordinal()] = -1;
        mSelectedTracks[TrackType.SUBTITLE.ordinal()] = -1;

        mPacketSources.put(TrackType.AUDIO, new PacketSource());
        mPacketSources.put(TrackType.VIDEO, new PacketSource());
        mPacketSources.put(TrackType.SUBTITLE, new PacketSource());

        mEventThread = new HandlerThread("PlatformParser");
        mEventThread.start();

        mEventHandler = new EventHandler(new WeakReference<>(this), mEventThread.getLooper());
    }

    @Override
    public boolean parse() {
        try {
            extractMetadata();

            if (mFD != null) {
                mExtractor.setDataSource(mFD, mFDOffset, mFDLength);
            } else {
                mExtractor.setDataSource(mUri);
            }

            int trackCount = mExtractor.getTrackCount();

            if (trackCount <= 0) {
                if (LOGS_ENABLED) Log.w(TAG, "No tracks found");
                return false;
            }

            mTrackInfos = new TrackInfo[trackCount];
            int maxInputBufferSize = -1;

            for (int i = 0; i < trackCount; i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                MetaDataImpl meta = new MetaDataImpl();
                meta.addValue(MetaData.KEY_MIME_TYPE, mime);

                TrackType type = TrackType.UNKNOWN;
                TrackRepresentation[] representations = new TrackRepresentation[1];
                if (mime.startsWith("video")) {
                    type = TrackType.VIDEO;
                    representations[0] = new VideoTrackRepresentation(-1, 0, 0, -1f);

                    int width = format.getInteger(MediaFormat.KEY_WIDTH);
                    int height = format.getInteger(MediaFormat.KEY_HEIGHT);

                    meta.addValue(MetaData.KEY_WIDTH, width);
                    meta.addValue(MetaData.KEY_HEIGHT, height);

                    if (mSelectedTracks[TrackType.VIDEO.ordinal()] == -1) {
                        mSelectedTracks[TrackType.VIDEO.ordinal()] = i;
                        mExtractor.selectTrack(i);

                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            if (format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) >
                                        maxInputBufferSize) {
                                maxInputBufferSize =
                                        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                            }
                        } else {
                            int bufferSize = width * height * 3 / 2;

                            if (bufferSize > maxInputBufferSize) {
                                maxInputBufferSize = bufferSize;
                            }
                        }
                    }
                } else if (mime.startsWith("audio")) {
                    type = TrackType.AUDIO;
                    representations[0] = new AudioTrackRepresentation(-1, 2, "", -1);

                    int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    meta.addValue(MetaData.KEY_CHANNEL_COUNT, channels);

                    if (mSelectedTracks[TrackType.AUDIO.ordinal()] == -1) {
                        mSelectedTracks[TrackType.AUDIO.ordinal()] = i;
                        mExtractor.selectTrack(i);

                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            if (format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) >
                                        maxInputBufferSize) {
                                maxInputBufferSize =
                                        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                            }
                        } else {
                            if (DEFAULT_AUDIO_BUFFER_SIZE > maxInputBufferSize) {
                                maxInputBufferSize = DEFAULT_AUDIO_BUFFER_SIZE;
                            }
                        }
                    }
                } else {
                    representations[0] = new TrackRepresentation(-1);
                }

                long duration = format.getLong(MediaFormat.KEY_DURATION);

                if (duration > mDurationUs) {
                    mDurationUs = duration;
                }

                mTrackInfos[i] = new TrackInfo(type, mime, duration, "und", representations);
                mTrackMetadata.add(meta);
            }

            mMetaDataValues.put(MetaData.KEY_DURATION, mDurationUs);
            mMetaDataValues.put(MetaData.KEY_NUM_TRACKS, trackCount);
            mMetaDataValues.put(MetaData.KEY_PAUSE_AVAILABLE, 1);
            mMetaDataValues.put(MetaData.KEY_SEEK_AVAILABLE, 1);

            mInputBuffer = ByteBuffer.allocate(maxInputBufferSize);

            mEventHandler.sendEmptyMessage(MSG_READ_DATA);

            return true;
        } catch (IOException e) {
        }

        return false;
    }

    private void extractMetadata() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (mFD != null) {
                retriever.setDataSource(mFD, mFDOffset, mFDLength);
            } else {
                retriever.setDataSource(mUri);
            }

            mMetaDataValues.put(MetaData.KEY_MIME_TYPE,
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));

            HashMap<Integer, String> metadataMapping = new HashMap<>();
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_ALBUM, MetaData.KEY_ALBUM);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, MetaData.KEY_ALBUM_ARTIST);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_ARTIST, MetaData.KEY_ARTIST);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_AUTHOR, MetaData.KEY_AUTHOR);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, MetaData.KEY_TRACK_NUMBER);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER, MetaData.KEY_DISC_NUMBER);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_COMPILATION, MetaData.KEY_COMPILATION);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_COMPOSER, MetaData.KEY_COMPOSER);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_GENRE, MetaData.KEY_GENRE);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_TITLE, MetaData.KEY_TITLE);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_WRITER, MetaData.KEY_WRITER);
            metadataMapping.put(
                    MediaMetadataRetriever.METADATA_KEY_YEAR, MetaData.KEY_YEAR);

            for (Map.Entry<Integer, String> entry : metadataMapping.entrySet()) {
                String value = retriever.extractMetadata(entry.getKey());
                if (value != null) {
                    mMetaDataValues.put(entry.getValue(), value);
                }
            }
        } catch (IllegalArgumentException e) {
            if (LOGS_ENABLED) Log.w(TAG, "Failed to extract metadata");
        } catch (RuntimeException e) {
            if (LOGS_ENABLED) Log.w(TAG, "Failed to extract metadata");
        }

        retriever.release();
    }

    @Override
    public int getTrackCount() {
        return mTrackInfos.length;
    }

    @Override
    public MetaData getTrackMetaData(int index) {
        return mTrackMetadata.get(index);
    }

    @Override
    public AccessUnit dequeueAccessUnit(TrackType type) {
        if (mPacketSources.get(type).hasBufferAvailable()) {

            if (needMoreBuffer()) {
                mEventHandler.sendEmptyMessage(MSG_READ_DATA);
            }

            return mPacketSources.get(type).dequeueAccessUnit();
        }

        mEventHandler.sendEmptyMessage(MSG_READ_DATA);

        return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
    }

    @Override
    public long getDurationUs() {
        return mDurationUs;
    }

    @Override
    public MediaFormat getFormat(TrackType type) {
        int selectedTrack = mSelectedTracks[type.ordinal()];

        if (selectedTrack == -1) {
            return null;
        }

        return mExtractor.getTrackFormat(selectedTrack);
    }

    @Override
    public TrackInfo[] getTrackInfo() {
        return mTrackInfos.clone();
    }

    @Override
    public void seekTo(long seekTimeUs) {
        mPacketSources.get(TrackType.AUDIO).setClosed(true);
        mPacketSources.get(TrackType.VIDEO).setClosed(true);
        mPacketSources.get(TrackType.SUBTITLE).setClosed(true);

        mPacketSources.get(TrackType.AUDIO).clear();
        mPacketSources.get(TrackType.VIDEO).clear();
        mPacketSources.get(TrackType.SUBTITLE).clear();

        mEOS = true;

        mEventHandler.obtainMessage(MSG_SEEK, seekTimeUs).sendToTarget();
    }

    @Override
    public TrackType selectTrack(boolean select, int index) {
        if (index < 0 || index >= mTrackInfos.length) {
            if (LOGS_ENABLED) Log.w(TAG, "Invalid track index");
            return TrackType.UNKNOWN;
        }

        TrackType type = mTrackInfos[index].getTrackType();

        if (select) {
            if (mSelectedTracks[type.ordinal()] == index) {
                if (LOGS_ENABLED) Log.w(TAG, "Track " + index + " is already selected");
                return TrackType.UNKNOWN;
            }
            mSelectedTracks[type.ordinal()] = index;
            mExtractor.selectTrack(index);
        } else {
            if (mSelectedTracks[type.ordinal()] == -1) {
                if (LOGS_ENABLED) Log.w(TAG, "Track " + index + " is not selected");
                return TrackType.UNKNOWN;
            }

            mSelectedTracks[type.ordinal()] = -1;
            mExtractor.unselectTrack(index);
        }

        mPacketSources.get(type).clear();

        return type;
    }

    @Override
    public int getSelectedTrackIndex(TrackType type) {
        return mSelectedTracks[type.ordinal()];
    }

    @Override
    public boolean canParse() {
        return true;
    }

    @Override
    public boolean hasDataAvailable(TrackType type) throws IOException {
        return true;
    }

    private static boolean isIDR(byte[] buffer) {
        int nalStartOffset = 0;
        for (int i = 0; i < buffer.length - 4; i++) {
            if (buffer[i] == 0x00 && buffer[i + 1] == 0x00 &&
                    buffer[i + 2] == 0x01) {

                if (nalStartOffset > 0) {
                    if ((buffer[nalStartOffset + 3] & 0x1f) == 5) {
                        return true;
                    }
                }

                nalStartOffset = i;
            }
        }

        if (nalStartOffset > 0) {
            if ((buffer[nalStartOffset + 3] & 0x1f) == 5) {
                return true;
            }
        }

        return false;
    }

    private long getCurrentBufferedSize() {
        return mPacketSources.get(TrackType.AUDIO).getBufferSize() +
        mPacketSources.get(TrackType.VIDEO).getBufferSize() +
        mPacketSources.get(TrackType.SUBTITLE).getBufferSize();
    }

    @Override
    public int getBuffering() {
        long cachedDuration = mExtractor.getCachedDuration();

        if (cachedDuration == -1 || mExtractor.hasCacheReachedEndOfStream()) {
            return 100;
        }

        return (int) ((mLastTimestamp + cachedDuration) * 100 / mDurationUs);
    }

    @Override
    public boolean needMoreBuffer() {
        return !mEOS && (mPacketSources.get(TrackType.AUDIO).
                getBufferDuration() < MIN_BUFFER_DURATION_US ||
                mPacketSources.get(TrackType.VIDEO).getBufferDuration() < MIN_BUFFER_DURATION_US);
    }

    @Override
    public void release() {
        mEventThread.quit();
    }

    private void onSeek(long seekTimeUs) {
        mPacketSources.get(TrackType.AUDIO).setClosed(false);
        mPacketSources.get(TrackType.VIDEO).setClosed(false);
        mPacketSources.get(TrackType.SUBTITLE).setClosed(false);

        mExtractor.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        mEOS = false;
    }

    private void onReadData() {
        long currentBufferedSize = getCurrentBufferedSize();

        while (!mEOS && currentBufferedSize < mMaxBufferSize) {
            try {
                int sampleSize = mExtractor.readSampleData(mInputBuffer, 0);


                if (sampleSize == -1) {
                    mPacketSources.get(TrackType.AUDIO).
                            queueAccessUnit(AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                    mPacketSources.get(TrackType.VIDEO).
                            queueAccessUnit(AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                    mPacketSources.get(TrackType.SUBTITLE).
                            queueAccessUnit(AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                    mEOS = true;
                    break;
                }

                int sampleTrackIndex = mExtractor.getSampleTrackIndex();
                TrackType sampleType;
                if (sampleTrackIndex == mSelectedTracks[TrackType.AUDIO.ordinal()]) {
                    sampleType = TrackType.AUDIO;
                } else if (sampleTrackIndex == mSelectedTracks[TrackType.VIDEO.ordinal()]) {
                    sampleType = TrackType.VIDEO;
                } else {
                    if (LOGS_ENABLED) Log.w(TAG, "Unknown track");
                    break;
                }

                AccessUnit accessUnit = new AccessUnit(AccessUnit.OK);
                accessUnit.size = sampleSize;
                byte[] buffer = new byte[sampleSize];
                mInputBuffer.get(buffer);
                accessUnit.data = buffer;
                accessUnit.timeUs = mExtractor.getSampleTime();
                accessUnit.isSyncSample =
                        (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) > 0;
                String mime = mTrackInfos[sampleTrackIndex].getMimeType();
                if (mime.equalsIgnoreCase(MimeType.AVC) || mime.equalsIgnoreCase(MimeType.HEVC)) {
                    accessUnit.isSyncSample = isIDR(buffer);
                }
                accessUnit.trackIndex = sampleTrackIndex;

                mLastTimestamp = mExtractor.getSampleTime();

                mExtractor.advance();

                mInputBuffer.clear();

                mPacketSources.get(sampleType).queueAccessUnit(accessUnit);

                currentBufferedSize = getCurrentBufferedSize();
            } catch (IllegalArgumentException e) {
                mEOS = true;
                mPacketSources.get(TrackType.AUDIO).queueAccessUnit(AccessUnit.ACCESS_UNIT_ERROR);
                mPacketSources.get(TrackType.VIDEO).queueAccessUnit(AccessUnit.ACCESS_UNIT_ERROR);
            }
        }
    }

    private static class EventHandler extends Handler {

        private WeakReference<PlatformParser> mParser;

        public EventHandler(WeakReference<PlatformParser> parser, Looper looper) {
            super(looper);

            mParser = parser;
        }

        @Override
        public void handleMessage(Message msg) {
            PlatformParser thiz = mParser.get();

            if (msg.what == MSG_READ_DATA) {
                thiz.onReadData();
            } else if (msg.what == MSG_SEEK) {
                thiz.onSeek((long)msg.obj);
            }
        }
    }
}
