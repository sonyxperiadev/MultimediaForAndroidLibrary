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

package com.sonymobile.android.media.internal.streaming.smoothstreaming;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.DataSource;
import com.sonymobile.android.media.internal.MimeType;
import com.sonymobile.android.media.internal.Util;
import com.sonymobile.android.media.internal.streaming.common.PacketSource;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static com.sonymobile.android.media.internal.streaming.smoothstreaming.ManifestParser.AudioQualityLevel;
import static com.sonymobile.android.media.internal.streaming.smoothstreaming.ManifestParser.FragmentEntry;
import static com.sonymobile.android.media.internal.streaming.smoothstreaming.ManifestParser.QualityLevel;
import static com.sonymobile.android.media.internal.streaming.smoothstreaming.ManifestParser.VideoQualityLevel;

public class QualityLevelFetcher {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "QualityLevelFetcher";

    private final QualityLevel mQualityLevel;

    private final PacketSource mPacketSource;

    private final SmoothStreamingSession mSession;

    private final MoofParser mParser;

    private final TrackType mType;

    private final long mTimeScale;

    private long mNextTimeUs = 0;

    private long mCurrentTimeUs = -1;

    private boolean mStartUp = true;

    private boolean mSeek = false;

    private long mSeekTimeUs = -1;

    private final int mTrackIndex;

    private boolean mEOS = false;

    private MediaFormat mFormat;

    public QualityLevelFetcher(SmoothStreamingSession session, QualityLevel qualityLevel,
                               PacketSource packetSource, TrackType type, long timeUs,
                               int trackIndex, byte[] defaultKID) {
        mQualityLevel = qualityLevel;
        mSession = session;
        mPacketSource = packetSource;
        mType = type;
        mTrackIndex = trackIndex;
        mTimeScale = qualityLevel.streamIndex.timeScale;

        mParser = new MoofParser(qualityLevel.mime, mTimeScale, defaultKID);

        if (mType == TrackType.VIDEO && mQualityLevel instanceof VideoQualityLevel) {
            mParser.setNALLengthSize(
                    ((VideoQualityLevel) mQualityLevel).NALLengthSize);
        }

        if (timeUs >= 0) {
            mNextTimeUs = timeUs;
            if (timeUs > 0) {
                mSeek = true;
                mSeekTimeUs = timeUs;
            }
        } else {
            mNextTimeUs = packetSource.getNextTimeUs();
        }
    }

    public void downloadNext() {
        if (!mStartUp && mType == TrackType.VIDEO && mSession.checkBandwidth()) {
            return;
        }

        DataSource source = null;
        try {
            source = createFragmentDataSource();
        } catch (IllegalArgumentException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IllegalArgumentException caught.");
            Message callback = mSession.getFetcherCallbackMessage(mType);
            callback.arg1 = SmoothStreamingSession.FETCHER_ERROR;
            callback.sendToTarget();
        }

        if (source != null) {
            if (mStartUp) {
                if (mType == TrackType.VIDEO) {
                    mFormat = MediaFormat.createVideoFormat(mQualityLevel.mime,
                            ((VideoQualityLevel) mQualityLevel).width,
                            ((VideoQualityLevel) mQualityLevel).height);

                    if (mQualityLevel.mime.equals(MimeType.AVC)) {
                        if (mQualityLevel.cpd.startsWith("00000001")) {
                            int ppsStart = mQualityLevel.cpd.indexOf("00000001", 4);

                            byte[] sps = Util.hexStringToByteArray(
                                    mQualityLevel.cpd.substring(0, ppsStart));
                            byte[] pps = Util.hexStringToByteArray(
                                    mQualityLevel.cpd.substring(ppsStart));

                            mFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
                            mFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
                        }
                    } else {
                        mFormat.setByteBuffer("csd-0",
                                ByteBuffer.wrap(Util.hexStringToByteArray(mQualityLevel.cpd)));
                    }

                    mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,
                            mSession.getMaxVideoInputSize());

                    AccessUnit accessUnit = new AccessUnit(AccessUnit.FORMAT_CHANGED);
                    accessUnit.timeUs = -1;
                    accessUnit.format = mFormat;
                    mPacketSource.queueAccessUnit(accessUnit);

                    queueSPSPPS(mFormat);
                } else if (mType == TrackType.AUDIO) {
                    mFormat = MediaFormat.createAudioFormat(mQualityLevel.mime,
                            ((AudioQualityLevel) mQualityLevel).sampleRate,
                            ((AudioQualityLevel) mQualityLevel).channels);
                    mFormat.setByteBuffer("csd-0",
                            ByteBuffer.wrap(Util.hexStringToByteArray(mQualityLevel.cpd)));
                    mFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192 * 4);

                    if (mQualityLevel.mime.equals(MimeType.WMA)) {
                        mFormat.setInteger("wma-version", 9);
                        mFormat.setInteger("bitrate", mQualityLevel.bitrate);
                        mFormat.setInteger("wma-encode-opt", 0);
                        mFormat.setInteger("wma-block-align", 0);
                    }
                } else if (mType == TrackType.SUBTITLE) {
                    mFormat = MediaFormat.createSubtitleFormat(mQualityLevel.mime,
                            mQualityLevel.streamIndex.language);
                }
            }

            if (!mParser.parseMoof(source, mCurrentTimeUs)) {
                Message callback = mSession.getFetcherCallbackMessage(mType);
                callback.arg1 = SmoothStreamingSession.FETCHER_ERROR;
                callback.sendToTarget();
                return;
            }

            if (mSeek && mType == TrackType.VIDEO) {
                Message callback = mSession.getFetcherCallbackMessage(mType);
                callback.arg1 = SmoothStreamingSession.FETCHER_TIME_ESTABLISHED;
                Bundle data = new Bundle(1);
                data.putLong(SmoothStreamingSession.KEY_TIMEUS, mCurrentTimeUs);
                callback.setData(data);
                callback.sendToTarget();

                mSeek = false;
            }

            while (true) {
                AccessUnit accessUnit = mParser.dequeueAccessUnit(mType);

                if (accessUnit.status == AccessUnit.OK) {
                    accessUnit.format = mFormat;

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

            try {
                source.close();
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Failed to close source");
            }

            mStartUp = false;
            mSeek = false;

            mPacketSource.setNextTimeUs(mNextTimeUs);
        } else {
            if (!mEOS) {
                // Signal error
                Message callback = mSession.getFetcherCallbackMessage(mType);
                callback.arg1 = SmoothStreamingSession.FETCHER_ERROR;
                callback.sendToTarget();
            }
        }
    }

    private void queueSPSPPS(MediaFormat format) {
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
            csd.cryptoInfo.numBytesOfClearData = new int[]{
                    csd.size
            };
            csd.cryptoInfo.numBytesOfEncryptedData = new int[]{
                    0
            };
            csd.isSyncSample = true;

            synchronized (mPacketSource) {
                mPacketSource.queueAccessUnit(csd);
            }

            i++;
        }
    }

    private DataSource createFragmentDataSource() {
        DataSource source = null;
        BandwidthEstimator bandwidthEstimator = null;

        if (mType == TrackType.VIDEO) {
            bandwidthEstimator = mSession.getBandwidthEstimator();
        }

        ArrayList<FragmentEntry> fragments = mQualityLevel.streamIndex.fragments;

        if (fragments != null) {
            boolean found = false;
            long fragmentTimeTicks = 0;

            for (FragmentEntry entry : fragments) {

                fragmentTimeTicks = entry.timeTicks;

                int r = 1;
                do {
                    long timeUs = fragmentTimeTicks * 1000000L / mTimeScale;
                    if (mSeek) {
                        if (mSeekTimeUs >= timeUs
                                && mSeekTimeUs < (timeUs +
                                (entry.durationTicks * 1000000L / mTimeScale))) {
                            mCurrentTimeUs = timeUs;
                            mNextTimeUs =
                                    (entry.timeTicks + r * entry.durationTicks)
                                            * 1000000L / mTimeScale;
                            found = true;
                            break;
                        }
                    } else {
                        if (timeUs >= mNextTimeUs) {
                            mNextTimeUs = (entry.timeTicks + r * entry.durationTicks)
                                    * 1000000L / mTimeScale;
                            mCurrentTimeUs = timeUs;
                            found = true;
                            break;
                        }
                    }

                    fragmentTimeTicks += entry.durationTicks;
                } while (r++ < entry.repeat);

                if (found) {
                    break;
                }
            }

            if (!found) {
                Message callback = mSession.getFetcherCallbackMessage(mType);
                callback.arg1 = SmoothStreamingSession.FETCHER_EOS;
                callback.sendToTarget();
                mEOS = true;
                return null;
            }

            try {
                source = DataSource.create(
                        getTemplatedUri(mQualityLevel.streamIndex.url, fragmentTimeTicks),
                        bandwidthEstimator, true);
            } catch (IOException e) {
                return null;
            }
        }

        return source;
    }

    private String getTemplatedUri(String uri, long time) {
        uri = uri.replaceAll("\\{start time\\}", String.valueOf(time));
        uri = uri.replaceAll("\\{start_time\\}", String.valueOf(time));

        uri = uri.replaceAll("\\{bitrate\\}", String.valueOf(mQualityLevel.bitrate));

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

    public QualityLevel getQualityLevel() {
        return mQualityLevel;
    }

    public void release() {
        mParser.release();
    }
}
