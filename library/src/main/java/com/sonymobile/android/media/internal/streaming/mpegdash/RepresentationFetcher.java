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

package com.sonymobile.android.media.internal.streaming.mpegdash;

import java.io.IOException;
import java.util.ArrayList;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.DataSource;
import com.sonymobile.android.media.internal.streaming.mpegdash.DASHISOParser.SubSegment;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Representation;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.SegmentTimelineEntry;
import com.sonymobile.android.media.internal.streaming.common.PacketSource;

public class RepresentationFetcher {

    private enum State {
        INIT, SIDX, FRAGMENT
    }

    private static final int SIDX_HEADER_SNIFF_SIZE = 200;

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "RepresentationFetcher";

    private State mState = State.INIT;

    private final Representation mRepresentation;

    private final PacketSource mPacketSource;

    private final DASHSession mSession;

    private final DASHISOParser mParser = new DASHISOParser();

    private final TrackType mType;

    private ArrayList<SubSegment> mSegmentIndex;

    private long mNextTimeUs = 0;

    private long mCurrentTimeUs = -1;

    private int mSegmentNumber = -1;

    private boolean mStartUp = true;

    private boolean mSeek = false;

    private long mSeekTimeUs = -1;

    private final long mTimeOffset;

    private final int mTrackIndex;

    private String mLastFragmentUri;

    private boolean mEOS = false;

    public RepresentationFetcher(DASHSession session, Representation representation,
            PacketSource packetSource, TrackType type, long timeUs, long timeOffsetUs,
            int trackIndex) {
        mRepresentation = representation;
        mSession = session;
        mPacketSource = packetSource;
        mType = type;
        mTimeOffset = timeOffsetUs;
        mTrackIndex = trackIndex;

        if (timeUs >= 0) {
            mNextTimeUs = timeUs - timeOffsetUs;
            if (timeUs > 0) {
                mSeek = true;
                mSeekTimeUs = timeUs - timeOffsetUs;
            }
        } else {
            mNextTimeUs = packetSource.getNextTimeUs() - timeOffsetUs;
        }

        if (representation.segmentTemplate != null
                && representation.segmentTemplate.segmentTimeline == null) {
            mSegmentNumber = (int)(mNextTimeUs / (representation.segmentTemplate.durationTicks
                    * 1000000L / representation.segmentTemplate.timescale))
                    + representation.segmentTemplate.startNumber;
        }
    }

    public void downloadNext() {
        switch (mState) {
            case INIT: {
                DataSource source = null;
                try {
                    source = createInitDataSource();
                } catch (IllegalArgumentException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IllegalArgumentException caught.");
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_ERROR;
                    callback.sendToTarget();
                }

                if (source == null) {
                    if (LOGS_ENABLED) Log.e(TAG, "Source is null");
                    // Signal error
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_ERROR;
                    callback.sendToTarget();
                    return;
                }

                int err = mParser.parseInit(source);

                long initSize = mParser.getInitSize();
                updateInitSize(initSize);
                if (err == DASHISOParser.ERROR_BUFFER_TO_SMALL) {
                    try {
                        if (initSize == -1 || source.length() < initSize) {
                            // Retry
                            return;
                        }
                    } catch (IOException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Failed to get source length");
                    } finally {
                        try {
                            source.close();
                        } catch (IOException e) {
                            if (LOGS_ENABLED) Log.e(TAG, "Failed to close source");
                        }
                    }
                }

                if (mType == TrackType.SUBTITLE) {
                    mParser.selectTrack(true, 0);
                }

                long sidxSize = mParser.getSidxSize();
                updateSidxSize(sidxSize);
                try {
                    mSegmentIndex = mParser.getSegmentIndex();

                    if (mSegmentIndex != null) {
                        mState = State.FRAGMENT;
                        return;
                    }
                } finally {
                    try {
                        source.close();
                    } catch (IOException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Failed to close source");
                    }
                }

                MetaData metadata = mParser.getMetaData();
                if (metadata.containsKey(MetaData.KEY_DRM_UUID)) {
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_DRM_INFO;
                    callback.getData().putByteArray(MetaData.KEY_DRM_UUID,
                            metadata.getByteBuffer(MetaData.KEY_DRM_UUID));
                    callback.getData().putByteArray(MetaData.KEY_DRM_PSSH_DATA,
                            metadata.getByteBuffer(MetaData.KEY_DRM_PSSH_DATA));
                    callback.sendToTarget();
                }

                mState = State.SIDX;

                break;
            }

            case SIDX: {
                DataSource source = null;
                try {
                    source = createSidxDataSource();
                } catch (IllegalArgumentException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IllegalArgumentException caught");
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_ERROR;
                    callback.sendToTarget();
                }

                if (source == null) {
                    if (!mEOS) {
                        // Signal error
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_ERROR;
                        callback.sendToTarget();
                    }
                    return;
                }

                int err = mParser.parseSidx(source);

                updateSidxSize(mParser.getSidxSize());
                if (err == DASHISOParser.ERROR_BUFFER_TO_SMALL) {
                    // Retry
                    return;
                } else if (err != DASHISOParser.OK) {
                    if (LOGS_ENABLED) Log.e(TAG, "Error " + err + " while parsing sidx");
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_ERROR;
                    callback.sendToTarget();
                    return;
                }

                mSegmentIndex = mParser.getSegmentIndex();

                try {
                    source.close();
                } catch (IOException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "Failed to close source");
                }

                mState = State.FRAGMENT;
                break;
            }
            case FRAGMENT: {
                if (!mStartUp && mType == TrackType.VIDEO && mSession.checkBandwidth()) {
                    return;
                }

                DataSource source = null;
                try {
                    source = createFragmentDataSource();
                } catch (IllegalArgumentException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IllegalArgumentException caught.");
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_ERROR;
                    callback.sendToTarget();
                }

                if (source != null) {
                    MediaFormat format = mParser.getFormat(mType);

                    if (mStartUp && mType == TrackType.VIDEO) {
                        if (format != null) {
                            if (format.getInteger(MediaFormat.KEY_WIDTH) == 0) {
                                if (LOGS_ENABLED)
                                    Log.w(TAG, "No video width and height in file, use mpd values");
                                format.setInteger(MediaFormat.KEY_HEIGHT, mRepresentation.height);
                                format.setInteger(MediaFormat.KEY_WIDTH, mRepresentation.width);
                            }

                            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,
                                    mSession.getMaxVideoInputSize());
                        }
                        AccessUnit accessUnit = new AccessUnit(AccessUnit.FORMAT_CHANGED);
                        accessUnit.timeUs = -1;
                        accessUnit.format = format;
                        mPacketSource.queueAccessUnit(accessUnit);
                    }

                    queueCSD(format);

                    if (!mParser.parseMoof(source, mCurrentTimeUs + mTimeOffset)) {
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_ERROR;
                        callback.sendToTarget();
                        return;
                    }

                    if (mSeek && mType == TrackType.VIDEO) {
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_TIME_ESTABLISHED;
                        Bundle data = new Bundle(1);
                        data.putLong(DASHSession.KEY_TIMEUS, mCurrentTimeUs + mTimeOffset);
                        callback.setData(data);
                        callback.sendToTarget();

                        mSeek = false;
                    }

                    while (true) {
                        AccessUnit accessUnit = mParser.dequeueAccessUnit(mType);

                        if (accessUnit.status == AccessUnit.OK) {
                            accessUnit.format = format;

                            if (mType == TrackType.SUBTITLE) {
                                accessUnit.trackIndex = mTrackIndex;
                            }

                            if (mSeek && mType == TrackType.AUDIO) {
                                if (accessUnit.timeUs < mSeekTimeUs) {
                                    continue;
                                }
                            }

                            mPacketSource.queueAccessUnit(accessUnit);
                        } else {
                            break;
                        }
                    }
                    if (mRepresentation.segmentTemplate != null &&
                            mRepresentation.segmentTemplate.segmentTimeline == null &&
                            (mPacketSource.getLastEnqueuedTimeUs() >
                                    mSession.getActivePeriodEndTime() ||
                            (mNextTimeUs + mTimeOffset) >= mSession.getActivePeriodEndTime()) &&
                            !mEOS) {
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_EOS;
                        callback.sendToTarget();
                        mEOS = true;
                    }

                    if (mType == TrackType.VIDEO) {
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_UPDATE_STATISTICS;
                        callback.getData().putString(DASHSession.KEY_REMOTE_IP,
                                source.getRemoteIP());
                        callback.getData().putString(DASHSession.KEY_VIDEO_URI, mLastFragmentUri);
                        callback.sendToTarget();
                    }

                    try {
                        source.close();
                    } catch (IOException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Failed to close source");
                    }

                    mStartUp = false;
                    mSeek = false;

                    mPacketSource.setNextTimeUs(mNextTimeUs + mTimeOffset);
                } else {
                    if (!mEOS) {
                        // Signal error
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_ERROR;
                        callback.sendToTarget();
                        return;
                    }
                }

                break;
            }
        }
    }

    private void queueCSD(MediaFormat format) {
        if (mStartUp && mType == TrackType.VIDEO) {
            int i = 0;
            while (format.containsKey("csd-" + i)) {
                AccessUnit csd = new AccessUnit(AccessUnit.OK);
                csd.data = format.getByteBuffer("csd-" + i).array();
                csd.size = csd.data.length;
                csd.timeUs = -1;
                csd.format = format;

                csd.cryptoInfo = new CryptoInfo();
                csd.cryptoInfo.mode = MediaCodec.CRYPTO_MODE_UNENCRYPTED;
                csd.cryptoInfo.numSubSamples = 1;
                csd.cryptoInfo.numBytesOfClearData = new int[] {
                        csd.size
                };
                csd.cryptoInfo.numBytesOfEncryptedData = new int[] {
                        0
                };
                csd.isSyncSample = true;

                synchronized (mPacketSource) {
                    mPacketSource.queueAccessUnit(csd);
                }

                i++;
            }
        }
    }

    private void updateSidxSize(long sidxSize) {
        if (mRepresentation.segmentBase != null && sidxSize > -1) {
            mRepresentation.segmentBase.sidxSize = sidxSize;
        }
    }

    private void updateInitSize(long initSize) {
        if (mRepresentation.segmentBase != null) {
            if (initSize == -1) {
                mRepresentation.segmentBase.initSize *= 2;
                mRepresentation.segmentBase.sidxOffset = mRepresentation.segmentBase.initSize;
            } else {
                mRepresentation.segmentBase.initSize = initSize;
                mRepresentation.segmentBase.sidxOffset = initSize;
            }
        }
    }

    private DataSource createFragmentDataSource() {
        DataSource source = null;
        BandwidthEstimator bandwidthEstimator = null;

        if (mType == TrackType.VIDEO) {
            bandwidthEstimator = mSession.getBandwidthEstimator();
        }

        if (mSegmentIndex != null) {
            source = createFragmentedDataSourceFromSegmentTable(bandwidthEstimator);
        } else if (mRepresentation.segmentTemplate != null) {
            if (mRepresentation.segmentTemplate.segmentTimeline != null) {
                boolean found = false;
                long segmentTimelineTemplateTicks = 0;
                for (SegmentTimelineEntry entry : mRepresentation.segmentTemplate.segmentTimeline) {
                    long segmentDurationUs = entry.durationTicks * 1000000L
                            / mRepresentation.segmentTemplate.timescale;
                    segmentTimelineTemplateTicks = entry.timeTicks;

                    int r = 0;
                    do {
                        long timelineTimeUs = segmentTimelineTemplateTicks * 1000000L
                                / mRepresentation.segmentTemplate.timescale;

                        if ((mSeek && mSeekTimeUs >= timelineTimeUs
                                && mSeekTimeUs < timelineTimeUs + segmentDurationUs) ||
                                timelineTimeUs >= mNextTimeUs) {
                            try {
                                source = DataSource.create(getTemplatedUri(
                                        mRepresentation.segmentTemplate.media,
                                        segmentTimelineTemplateTicks), bandwidthEstimator, true);
                            } catch (IOException e) {
                                return null;
                            }

                            mNextTimeUs = timelineTimeUs + segmentDurationUs;
                            found = true;
                            break;
                        }

                        segmentTimelineTemplateTicks += entry.durationTicks;
                    } while (r++ < entry.repeat);

                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_EOS;
                    callback.sendToTarget();
                    mEOS = true;
                    return null;
                }

                mLastFragmentUri = getTemplatedUri(mRepresentation.segmentTemplate.media,
                        segmentTimelineTemplateTicks);

                mCurrentTimeUs = segmentTimelineTemplateTicks * 1000000L
                        / mRepresentation.segmentTemplate.timescale;
            } else {
                mLastFragmentUri = getTemplatedUri(mRepresentation.segmentTemplate.media);
                try {
                    source = DataSource.create(getTemplatedUri(
                            mRepresentation.segmentTemplate.media), bandwidthEstimator, true);
                } catch (IOException e) {
                    return null;
                }

                mSegmentNumber++;

                mCurrentTimeUs = mNextTimeUs;
                mNextTimeUs += (mRepresentation.segmentTemplate.durationTicks * 1000000L /
                        mRepresentation.segmentTemplate.timescale);
            }
        }

        return source;
    }

    private DataSource createFragmentedDataSourceFromSegmentTable(
            BandwidthEstimator bandwidthEstimator) {
        DataSource source = null;

        for (int i = 0; i < mSegmentIndex.size(); i++) {
            SubSegment subsegment = mSegmentIndex.get(i);

            if ((mSeek && subsegment.timeUs <= mNextTimeUs && subsegment.timeUs
                    + subsegment.durationUs > mNextTimeUs)
                    || (!mSeek && (subsegment.timeUs >= mNextTimeUs
                    || subsegment.timeUs + subsegment.durationUs > mNextTimeUs))) {

                if (mRepresentation.segmentTemplate != null) {
                    if (mRepresentation.segmentTemplate.segmentTimeline != null) {
                        boolean found = false;
                        long segmentTimelineTemplateTicks;
                        for (SegmentTimelineEntry entry :
                                mRepresentation.segmentTemplate.segmentTimeline) {
                            long segmentDurationUs = entry.durationTicks * 1000000L
                                    / mRepresentation.segmentTemplate.timescale;
                            segmentTimelineTemplateTicks = entry.timeTicks;

                            int r = 0;
                            do {
                                long timelineTime = segmentTimelineTemplateTicks * 1000000L
                                        / mRepresentation.segmentTemplate.timescale;

                                if (mSeek) {
                                    if (mSeekTimeUs >= timelineTime
                                            && mSeekTimeUs < timelineTime + segmentDurationUs) {
                                        found = true;
                                        try {
                                            source = DataSource.create(
                                                    getTemplatedUri(
                                                            mRepresentation.segmentTemplate.media,
                                                            segmentTimelineTemplateTicks),
                                                    subsegment.offset, subsegment.size,
                                                    bandwidthEstimator, true);
                                        } catch (IOException e) {
                                            return null;
                                        }
                                        mNextTimeUs = timelineTime + segmentDurationUs;
                                    }
                                } else if (timelineTime >= mNextTimeUs) {
                                    found = true;

                                    mLastFragmentUri = getTemplatedUri(
                                            mRepresentation.segmentTemplate.media,
                                            segmentTimelineTemplateTicks);
                                    try {
                                        source = DataSource.create(
                                                getTemplatedUri(
                                                        mRepresentation.segmentTemplate.media,
                                                        segmentTimelineTemplateTicks),
                                                subsegment.offset, subsegment.size,
                                                bandwidthEstimator, true);
                                    } catch (IOException e) {
                                        return null;
                                    }
                                    mNextTimeUs = timelineTime + segmentDurationUs;
                                    break;
                                }

                                segmentTimelineTemplateTicks += entry.durationTicks;
                            } while (r++ < entry.repeat);

                            if (found) {
                                break;
                            }
                        }
                    } else {

                        mLastFragmentUri =
                                getTemplatedUri(mRepresentation.segmentTemplate.media);
                        try {
                            source = DataSource.create(
                                    getTemplatedUri(mRepresentation.segmentTemplate.media),
                                    subsegment.offset, subsegment.size, bandwidthEstimator, true);
                        } catch (IOException e) {
                            return null;
                        }
                        mNextTimeUs = subsegment.timeUs + subsegment.durationUs;
                    }

                    if (i == mSegmentIndex.size() - 1) {
                        mSegmentNumber++;
                        mState = State.SIDX;
                        mSegmentIndex = null;
                    }
                } else if (mRepresentation.segmentBase != null) {

                    mLastFragmentUri = mRepresentation.segmentBase.url;
                    try {
                        source = DataSource.create(mRepresentation.segmentBase.url,
                                subsegment.offset, subsegment.size, bandwidthEstimator, true);
                    } catch (IOException e) {
                        return null;
                    }
                    mNextTimeUs = subsegment.timeUs + subsegment.durationUs;
                    if (i == mSegmentIndex.size() - 1) {
                        Message callback = mSession.getFetcherCallbackMessage(mType);
                        callback.arg1 = DASHSession.FETCHER_EOS;
                        callback.sendToTarget();
                        mEOS = true;
                    }
                } else if (LOGS_ENABLED) {
                    Log.e(TAG, "No fragment uri information");
                }

                if (source == null) {
                    if (LOGS_ENABLED) Log.e(TAG, "no fragment source");
                    return null;
                }

                mCurrentTimeUs = subsegment.timeUs;
                break;
            }
        }

        return source;
    }

    private DataSource createSidxDataSource() {
        if (mRepresentation.segmentTemplate != null) {

            if (mRepresentation.segmentTemplate.segmentTimeline != null) {
                boolean found = false;
                long segmentTimelineTemplateTicks = 0;
                for (SegmentTimelineEntry entry : mRepresentation.segmentTemplate.segmentTimeline) {
                    long segmentDurationUs = entry.durationTicks * 1000000L
                            / mRepresentation.segmentTemplate.timescale;
                    segmentTimelineTemplateTicks = entry.timeTicks;

                    int r = 0;
                    do {
                        long timelineTimeUs = segmentTimelineTemplateTicks * 1000000L
                                / mRepresentation.segmentTemplate.timescale;

                        if (mSeek) {
                            if (mSeekTimeUs >= timelineTimeUs
                                    && mSeekTimeUs < timelineTimeUs + segmentDurationUs) {
                                found = true;
                                break;
                            }
                        }
                        if (timelineTimeUs >= mNextTimeUs) {
                            found = true;
                            break;
                        }

                        segmentTimelineTemplateTicks += entry.durationTicks;
                    } while (r++ < entry.repeat);

                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    Message callback = mSession.getFetcherCallbackMessage(mType);
                    callback.arg1 = DASHSession.FETCHER_EOS;
                    callback.sendToTarget();
                    mEOS = true;
                    return null;
                }

                try {
                    return DataSource.create(
                            getTemplatedUri(mRepresentation.segmentTemplate.media,
                                    segmentTimelineTemplateTicks), 0, SIDX_HEADER_SNIFF_SIZE, true);
                } catch (IOException e) {
                    return null;
                }
            } else {
                try {
                    return DataSource.create(getTemplatedUri(mRepresentation.segmentTemplate.media),
                            0, SIDX_HEADER_SNIFF_SIZE, true);
                } catch (IOException e) {
                    return null;
                }
            }
        } else if (mRepresentation.segmentBase != null) {
            try {
                return DataSource.create(mRepresentation.segmentBase.url,
                        mRepresentation.segmentBase.sidxOffset,
                        (int)mRepresentation.segmentBase.sidxSize, true);
            } catch (IOException e) {
                return null;
            }
        }

        return null;
    }

    private DataSource createInitDataSource() {
        if (mRepresentation.segmentTemplate != null) {
            try {
                return DataSource
                        .create(getTemplatedUri(mRepresentation.segmentTemplate.initialization),
                                true);
            } catch (IOException e) {
                return null;
            }
        } else if (mRepresentation.segmentBase != null) {
            try {
                return DataSource.create(mRepresentation.segmentBase.url,
                        mRepresentation.segmentBase.initOffset,
                        (int)mRepresentation.segmentBase.initSize, true);
            } catch (IOException e) {
                return null;
            }
        }
        if (LOGS_ENABLED) Log.e(TAG, "No init url");
        return null;
    }

    private String getTemplatedUri(String uri) {
        return getTemplatedUri(uri, -1);
    }

    private String getTemplatedUri(String uri, long time) {

        uri = uri.replaceAll("\\$RepresentationID\\$", mRepresentation.id);

        uri = uri.replaceAll("\\$Number\\$", String.valueOf(mSegmentNumber));

        uri = uri.replaceAll("\\$Time\\$", String.valueOf(time));

        uri = uri.replaceAll("\\$Bandwidth\\$", String.valueOf(mRepresentation.bandwidth));

        return uri;
    }

    public boolean isBufferFull(long minBufferTime, int maxBufferDataSize) {
        // Assume previous moof is a good enough approximation of next moof
        long nextFragmentSize = mParser.getMoofDataSize();
        return (maxBufferDataSize > 0 &&
                mPacketSource.getBufferSize() + nextFragmentSize > maxBufferDataSize)
                || mPacketSource.getBufferDuration() > minBufferTime;
    }

    public long getNextTimeUs() {
        return mNextTimeUs;
    }

    public int getState() {
        return mState.ordinal();
    }

    public Representation getRepresentation() {
        return mRepresentation;
    }

    public void release() {
        mParser.release();
    }
}
