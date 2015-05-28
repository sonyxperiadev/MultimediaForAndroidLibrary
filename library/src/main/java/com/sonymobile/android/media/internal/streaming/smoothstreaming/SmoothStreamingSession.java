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

import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MetaDataImpl;
import com.sonymobile.android.media.internal.MimeType;
import com.sonymobile.android.media.internal.streaming.common.DefaultBandwidthEstimator;
import com.sonymobile.android.media.internal.streaming.common.DefaultRepresentationSelector;
import com.sonymobile.android.media.internal.streaming.common.PacketSource;
import com.sonymobile.android.media.internal.streaming.smoothstreaming.ManifestParser.QualityLevel;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;

public final class SmoothStreamingSession {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SmoothStreamingSession";

    private static final int MSG_CONNECT = 0;

    private static final int MSG_DOWNLOAD_NEXT = 1;

    private static final int MSG_FETCHER_CALLBACK = 2;

    private static final int MSG_CHANGE_CONFIGURATION = 3;

    private static final int MSG_SEEK = 4;

    private static final int MSG_DISCONNECT = 5;

    private static final int MSG_SELECT_TRACK = 6;

    public static final int FETCHER_EOS = 0;

    public static final int FETCHER_ERROR = 1;

    public static final int FETCHER_TIME_ESTABLISHED = 2;

    public static final String KEY_TIMEUS = "timeus";

    private static final int MAX_BUFFER_DURATION_US = 10000000;

    private static final int MIN_BUFFER_DURATION_US = 4000000;

    private final HandlerThread mEventThread;

    private final EventHandler mEventHandler;

    private final Handler mCallbackHandler;

    private ManifestParser mManifestParser;

    private boolean mBuffering = true;

    private final EnumMap<TrackType, QualityLevelFetcher> mFetchers =
            new EnumMap<>(TrackType.class);

    private final EnumMap<TrackType, PacketSource> mPacketSources = new EnumMap<>(TrackType.class);

    private BandwidthEstimator mBandwidthEstimator;

    private RepresentationSelector mRepresentationSelector;

    private long mLastDequeuedTimeUs;

    private final MetaDataImpl mMetaData = new MetaDataImpl();

    private boolean mSeekPending = false;

    private final int mMaxBufferSize;

    private final int[] mMaxBufferSizes;

    private byte[] mDefaultKID;

    public SmoothStreamingSession(Handler callbackHandler, BandwidthEstimator estimator,
                                  RepresentationSelector selector, int maxBufferSize) {

        mCallbackHandler = callbackHandler;

        mEventThread = new HandlerThread("SmoothStreaming");
        mEventThread.start();

        mEventHandler = new EventHandler(new WeakReference<>(this),
                mEventThread.getLooper());

        mPacketSources.put(TrackType.AUDIO, new PacketSource());
        mPacketSources.put(TrackType.VIDEO, new PacketSource());
        mPacketSources.put(TrackType.SUBTITLE, new PacketSource());

        mBandwidthEstimator = estimator;
        mRepresentationSelector = selector;

        mMaxBufferSize = maxBufferSize;
        mMaxBufferSizes = new int[TrackType.UNKNOWN.ordinal()];
        mMaxBufferSizes[TrackType.VIDEO.ordinal()] = -1;
        mMaxBufferSizes[TrackType.AUDIO.ordinal()] = -1;
        mMaxBufferSizes[TrackType.SUBTITLE.ordinal()] = -1;
    }

    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        mBandwidthEstimator = estimator;
    }

    public BandwidthEstimator getBandwidthEstimator() {
        return mBandwidthEstimator;
    }

    public void setRepresentationSelector(RepresentationSelector selector) {
        mRepresentationSelector = selector;
    }

    public MediaFormat getFormat(TrackType type) {
        PacketSource packetSource = mPacketSources.get(type);

        if (packetSource == null) {
            return null;
        }

        return packetSource.getFormat();
    }

    public AccessUnit dequeueAccessUnit(TrackType type) {
        if (type != TrackType.AUDIO && type != TrackType.VIDEO && type != TrackType.SUBTITLE) {
            return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
        }

        if (mBuffering) {

            PacketSource audioPacketSource = mPacketSources.get(TrackType.AUDIO);

            if (audioPacketSource.getBufferDuration() < MIN_BUFFER_DURATION_US
                    && (mFetchers.containsKey(TrackType.AUDIO) || mSeekPending)) {
                return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
            }

            PacketSource videoPacketSource = mPacketSources.get(TrackType.VIDEO);

            if (videoPacketSource.getBufferDuration() < MIN_BUFFER_DURATION_US
                    && mFetchers.containsKey(TrackType.VIDEO)) {
                return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
            }

            mBuffering = false;

            mCallbackHandler.obtainMessage(SmoothStreamingSource.MSG_BUFFERING_END).sendToTarget();
        }

        PacketSource packetSource = mPacketSources.get(type);

        if (!packetSource.hasBufferAvailable()) {
            if (type != TrackType.SUBTITLE) {
                mBuffering = true;

                mCallbackHandler.
                        obtainMessage(SmoothStreamingSource.MSG_BUFFERING_START).sendToTarget();
            }

            if (LOGS_ENABLED) Log.e(TAG, "No data available");
            return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
        }

        AccessUnit accessUnit = packetSource.dequeueAccessUnit();

        mLastDequeuedTimeUs = accessUnit.timeUs;
        return accessUnit;
    }

    public void connect(String url, HttpURLConnection urlConnection) {
        mEventHandler.obtainMessage(MSG_CONNECT,
                urlConnection != null ? urlConnection : url).sendToTarget();
    }

    public MetaData getMetaData() {
        return mMetaData;
    }

    public int getMaxVideoInputSize() {
        return mManifestParser.getMaxVideoInputBufferSize();
    }

    private static class EventHandler extends Handler {

        private final WeakReference<SmoothStreamingSession> mSession;

        public EventHandler(WeakReference<SmoothStreamingSession> session, Looper looper) {
            super(looper);

            mSession = session;
        }

        @Override
        public void handleMessage(Message msg) {
            SmoothStreamingSession thiz = mSession.get();
            switch (msg.what) {
                case MSG_CONNECT:
                    thiz.onConnect(msg);
                    break;
                case MSG_DOWNLOAD_NEXT:
                    thiz.onDownloadNext();
                    break;
                case MSG_CHANGE_CONFIGURATION:
                    thiz.onChangeConfiguration(msg);
                    break;
                case MSG_FETCHER_CALLBACK:
                    TrackType type = (TrackType)msg.obj;
                    switch (msg.arg1) {
                        case FETCHER_EOS: {
                            PacketSource packetSource = thiz.mPacketSources.get(type);

                            thiz.mFetchers.remove(type);

                            packetSource.queueAccessUnit(AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                            break;
                        }
                        case FETCHER_TIME_ESTABLISHED: {
                            Bundle data = msg.getData();
                            long videoTimeUs = data.getLong(KEY_TIMEUS, 0);

                            QualityLevel audioQualityLevel = thiz.mManifestParser
                                    .getQualityLevel(TrackType.AUDIO);

                            if (audioQualityLevel != null) {
                                thiz.addFetcher(TrackType.AUDIO, audioQualityLevel, videoTimeUs);
                            }

                            thiz.mLastDequeuedTimeUs = videoTimeUs;
                            thiz.mSeekPending = false;

                            QualityLevel subtitleQualityLevel = thiz.mManifestParser
                                    .getQualityLevel(TrackType.SUBTITLE);

                            if (subtitleQualityLevel != null) {
                                thiz.addFetcher(TrackType.SUBTITLE, subtitleQualityLevel,
                                        videoTimeUs);
                            }

                            break;
                        }
                        case FETCHER_ERROR: {
                            if (LOGS_ENABLED) Log.e(TAG, "Fetcher reported error");
                            PacketSource packetSource = thiz.mPacketSources.get(type);

                            thiz.mCallbackHandler.obtainMessage(SmoothStreamingSource.MSG_ERROR)
                                    .sendToTarget();
                            packetSource.queueAccessUnit(AccessUnit.ACCESS_UNIT_ERROR);
                            thiz.mFetchers.remove(type);
                            break;
                        }
                        default:
                            if (LOGS_ENABLED) Log.w(TAG, "Unhandled fetcher message: " + msg.arg1);
                            break;
                    }
                    break;
                case MSG_SEEK:
                    thiz.onSeek((Long)msg.obj);
                    break;
                case MSG_DISCONNECT:
                    if (thiz.mEventThread != null) {
                        for (Map.Entry<TrackType, QualityLevelFetcher> item : thiz.mFetchers
                                .entrySet()) {
                            QualityLevelFetcher fetcher = item.getValue();
                            fetcher.release();
                        }
                        thiz.mEventThread.quitSafely();
                    }
                    break;
                case MSG_SELECT_TRACK:
                    thiz.onSelectTrack(msg.arg1, (TrackType)msg.obj);
                    break;
                default:
                    if (LOGS_ENABLED) Log.w(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private void onConnect(Message msg) {
        boolean success = false;
        int error = MediaError.UNKNOWN;
        try {
            String uri;
            HttpURLConnection urlConnection;
            if (msg.obj instanceof HttpURLConnection) {
                urlConnection = (HttpURLConnection)msg.obj;
                uri = urlConnection.getURL().toString();
            } else {
                uri = (String)msg.obj;
                URL url = new URL(uri);
                urlConnection = (HttpURLConnection)url.openConnection();
            }
            if (LOGS_ENABLED) Log.i(TAG, "onConnect " + uri);

            if (urlConnection.getResponseCode() / 100 == 2) {
                mManifestParser = new ManifestParser(uri);

                if (mBandwidthEstimator == null) {
                    mBandwidthEstimator = new DefaultBandwidthEstimator();
                }

                if (mRepresentationSelector == null) {
                    // Fall back to default.
                    mRepresentationSelector =
                            new DefaultRepresentationSelector(mMaxBufferSize);
                }

                success = mManifestParser.parse(urlConnection.getInputStream());
                if (success) {
                    mMetaData.addValue(MetaData.KEY_MIME_TYPE, MimeType.SMOOTH_STREAMING);

                    ManifestParser.Protection protection = mManifestParser.getProtection();
                    if (protection != null ) {
                        if (protection.uuid != null) {
                            mMetaData.addValue(MetaData.KEY_DRM_UUID, protection.uuid);
                        }
                        if (protection.content != null) {
                            mMetaData.addValue(MetaData.KEY_DRM_PSSH_DATA, protection.content);
                        }

                        mDefaultKID = protection.kID;
                    }
                    boolean isLive = mManifestParser.getDurationUs() == -1;
                    mMetaData.addValue(MetaData.KEY_PAUSE_AVAILABLE, isLive ? 0 : 1);
                    mMetaData.addValue(MetaData.KEY_SEEK_AVAILABLE, isLive ? 0 : 1);

                    mCallbackHandler.
                            obtainMessage(SmoothStreamingSource.MSG_PREPARED).sendToTarget();

                    int[] selectedQualityLevels = mManifestParser.getSelectedQualityLevels();
                    int[] selectedTracks = mManifestParser.getSelectedTracks();
                    TrackInfo[] trackInfo = mManifestParser.getTrackInfo();
                    mRepresentationSelector.selectDefaultRepresentations(selectedTracks, trackInfo,
                            selectedQualityLevels);
                    mManifestParser.updateQualityLevels(selectedQualityLevels);
                    changeConfiguration(0);
                } else {
                    error = MediaError.MALFORMED;
                }
            } else {
                if (LOGS_ENABLED) Log.e(TAG, "Error: " + urlConnection.getResponseCode());
                error = MediaError.IO;
            }

        } catch (MalformedURLException e) {
            if (LOGS_ENABLED) Log.e(TAG, "MalformedURLException in onConnect", e);
            error = MediaError.UNSUPPORTED;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException in onConnect", e);
            error = MediaError.IO;
        }

        if (!success) {
            mCallbackHandler.obtainMessage(SmoothStreamingSource.MSG_PREPARE_FAILED,
                    error, 0).sendToTarget();
        }
    }

    public void onSelectTrack(int index, TrackType type) {
        mPacketSources.get(type).clear();
        mFetchers.remove(type);

        int[] selectedQualityLevels = mManifestParser.getSelectedQualityLevels();
        int[] selectedTracks = mManifestParser.getSelectedTracks();
        mRepresentationSelector.selectRepresentations(mBandwidthEstimator.getEstimatedBandwidth(),
                selectedTracks, selectedQualityLevels);
        mManifestParser.updateQualityLevel(type, selectedQualityLevels[type.ordinal()]);

        QualityLevel qualityLevel = mManifestParser.getQualityLevel(type);

        if (!mSeekPending && qualityLevel != null) {
            addFetcher(type, qualityLevel, mLastDequeuedTimeUs);
        }
    }

    public void onChangeConfiguration(Message msg) {
        changeConfiguration(msg.arg1);
    }

    public void onDownloadNext() {
        if (mFetchers.size() == 0) {
            return;
        }

        QualityLevelFetcher selectedFetcher = null;
        for (Map.Entry<TrackType, QualityLevelFetcher> item : mFetchers.entrySet()) {
            QualityLevelFetcher fetcher = item.getValue();
            TrackType type = item.getKey();
            int maxBufferSize = mMaxBufferSizes[type.ordinal()];

            if (selectedFetcher == null) {
                if (!fetcher.isBufferFull(MAX_BUFFER_DURATION_US, maxBufferSize)) {
                    selectedFetcher = fetcher;
                }
            } else if (!fetcher.isBufferFull(MAX_BUFFER_DURATION_US, maxBufferSize) &&
                    (fetcher.getNextTimeUs() < selectedFetcher.getNextTimeUs())) {
                selectedFetcher = fetcher;
            }
        }

        if (selectedFetcher != null) {
            selectedFetcher.downloadNext();

            Message msg = mEventHandler.obtainMessage(MSG_DOWNLOAD_NEXT);
            mEventHandler.sendMessageDelayed(msg, 10);
        } else {
            Message msg = mEventHandler.obtainMessage(MSG_DOWNLOAD_NEXT);
            mEventHandler.sendMessageDelayed(msg, 1000);
        }
    }

    private void changeConfiguration(long timeUs) {
        mEventHandler.removeMessages(MSG_DOWNLOAD_NEXT);

        QualityLevel audioQualityLevel = mManifestParser.getQualityLevel(TrackType.AUDIO);

        QualityLevel videoQualityLevel = mManifestParser.getQualityLevel(TrackType.VIDEO);

        QualityLevel subtitleQualityLevel = mManifestParser.getQualityLevel(TrackType.SUBTITLE);

        if (audioQualityLevel == null && videoQualityLevel == null) {
            if (LOGS_ENABLED) Log.e(TAG, "No quality levels available");
            mCallbackHandler.obtainMessage(SmoothStreamingSource.MSG_ERROR).sendToTarget();
            return;
        }

        QualityLevelFetcher audioFetcher = mFetchers.get(TrackType.AUDIO);

        if (audioFetcher != null
                && (timeUs > -1 || audioFetcher.getQualityLevel() != audioQualityLevel)) {
            mFetchers.remove(TrackType.AUDIO);
            audioFetcher = null;
        }

        QualityLevelFetcher videoFetcher = mFetchers.get(TrackType.VIDEO);

        if (videoFetcher != null
                && (timeUs > -1 || videoFetcher.getQualityLevel() != videoQualityLevel)) {
            mFetchers.remove(TrackType.VIDEO);
            videoFetcher = null;
        }

        QualityLevelFetcher subtitleFetcher = mFetchers.get(TrackType.SUBTITLE);

        if (subtitleFetcher != null
                && (timeUs > -1 || subtitleFetcher.getQualityLevel() != subtitleQualityLevel)) {
            mFetchers.remove(TrackType.SUBTITLE);
            subtitleFetcher = null;
        }

        mCallbackHandler.obtainMessage(SmoothStreamingSource.MSG_CHANGE_SUBTITLE,
                subtitleQualityLevel != null ? 1 : 0, 0).sendToTarget();

        if (videoFetcher == null && videoQualityLevel != null) {
            addFetcher(TrackType.VIDEO, videoQualityLevel, timeUs);
            if (timeUs > 0) {
                mSeekPending = true;
            }
        }

        if (!mSeekPending && audioFetcher == null && audioQualityLevel != null) {
            addFetcher(TrackType.AUDIO, audioQualityLevel, timeUs);
        }

        if (!mSeekPending && subtitleFetcher == null && subtitleQualityLevel != null) {
            addFetcher(TrackType.SUBTITLE, subtitleQualityLevel, timeUs);
        }

        if (mMaxBufferSize > 0) {
            // Recalculate buffer allocation between streams
            mMaxBufferSizes[TrackType.VIDEO.ordinal()] = 0;
            mMaxBufferSizes[TrackType.AUDIO.ordinal()] = 0;
            mMaxBufferSizes[TrackType.SUBTITLE.ordinal()] = 0;
            int totalBandwidth = 0;

            if (videoQualityLevel != null) {
                totalBandwidth += videoQualityLevel.bitrate;
            }
            if (audioQualityLevel != null) {
                totalBandwidth += audioQualityLevel.bitrate;
            }
            if (subtitleQualityLevel != null) {
                totalBandwidth += subtitleQualityLevel.bitrate;
            }

            if (subtitleQualityLevel != null) {
                double bufferShare = ((double)subtitleQualityLevel.bitrate) / totalBandwidth;
                mMaxBufferSizes[TrackType.SUBTITLE.ordinal()] =
                        (int)(mMaxBufferSize * bufferShare);
            }
            if (audioQualityLevel != null) {
                if (videoQualityLevel == null) {
                    // no video, let audio have all remaining buffer
                    mMaxBufferSizes[TrackType.AUDIO.ordinal()] = mMaxBufferSize
                            - mMaxBufferSizes[TrackType.SUBTITLE.ordinal()];
                } else {
                    double bufferShare = ((double)audioQualityLevel.bitrate) / totalBandwidth;
                    mMaxBufferSizes[TrackType.AUDIO.ordinal()] =
                            (int)(mMaxBufferSize * bufferShare);
                }
            }
            if (videoQualityLevel != null) {
                // let video have all remaining buffer
                mMaxBufferSizes[TrackType.VIDEO.ordinal()] = mMaxBufferSize -
                        mMaxBufferSizes[TrackType.AUDIO.ordinal()] -
                        mMaxBufferSizes[TrackType.SUBTITLE.ordinal()];
            }
        }

        Message msg = mEventHandler.obtainMessage(MSG_DOWNLOAD_NEXT);

        mEventHandler.sendMessageDelayed(msg, 500);
    }

    private void onSeek(long timeUs) {
        mSeekPending = false;
        mEventHandler.removeMessages(MSG_FETCHER_CALLBACK);

        mPacketSources.get(TrackType.AUDIO).clear();
        mPacketSources.get(TrackType.VIDEO).clear();
        mPacketSources.get(TrackType.SUBTITLE).clear();

        mPacketSources.get(TrackType.AUDIO).setClosed(false);
        mPacketSources.get(TrackType.VIDEO).setClosed(false);
        mPacketSources.get(TrackType.SUBTITLE).setClosed(false);

        int[] selectedRepresentations = mManifestParser.getSelectedQualityLevels();
        int[] selectedTracks = mManifestParser.getSelectedTracks();
        mRepresentationSelector.selectRepresentations(
                mBandwidthEstimator.getEstimatedBandwidth(),
                selectedTracks,
                selectedRepresentations);
        mManifestParser.updateQualityLevels(selectedRepresentations);

        changeConfiguration(timeUs);
    }

    private void addFetcher(TrackType type, QualityLevel qualityLevel, long nextTimeUs) {
        if (mFetchers.containsKey(type)) {
            throw new RuntimeException();
        }

        int trackIndex = -1;
        if (type == TrackType.SUBTITLE) {
            trackIndex = mManifestParser.getSelectedTrackIndex(type);
        }

        mFetchers.put(type,
                new QualityLevelFetcher(this, qualityLevel, mPacketSources.get(type), type,
                        nextTimeUs, trackIndex, mDefaultKID));
    }

    public Message getFetcherCallbackMessage(TrackType type) {
        return mEventHandler.obtainMessage(MSG_FETCHER_CALLBACK, type);
    }

    public boolean checkBandwidth() {
        int[] selectedTracks = mManifestParser.getSelectedTracks();
        int[] selectedRepresentations = mManifestParser.getSelectedQualityLevels();

        if (mRepresentationSelector.selectRepresentations(
                mBandwidthEstimator.getEstimatedBandwidth(), selectedTracks,
                selectedRepresentations)) {
            mManifestParser.updateQualityLevels(selectedRepresentations);
            mEventHandler.obtainMessage(MSG_CHANGE_CONFIGURATION, -1, 0).sendToTarget();
            return true;
        }
        return false;
    }

    public long getDurationUs() {
        return mManifestParser.getDurationUs();
    }

    public TrackInfo[] getTrackInfo() {
        return mManifestParser.getTrackInfo();
    }

    public void seekTo(long timeUs) {

        mPacketSources.get(TrackType.AUDIO).setClosed(true);
        mPacketSources.get(TrackType.VIDEO).setClosed(true);
        mPacketSources.get(TrackType.SUBTITLE).setClosed(true);

        mPacketSources.get(TrackType.AUDIO).clear();
        mPacketSources.get(TrackType.VIDEO).clear();
        mPacketSources.get(TrackType.SUBTITLE).clear();

        mEventHandler.removeMessages(MSG_DOWNLOAD_NEXT);
        mEventHandler.obtainMessage(MSG_SEEK, timeUs).sendToTarget();
    }

    public TrackType selectTrack(boolean select, int index) {
        TrackType type = mManifestParser.selectTrack(select, index);

        if (type != TrackType.UNKNOWN) {
            mEventHandler.obtainMessage(MSG_SELECT_TRACK, index, 0, type).sendToTarget();
        }

        return type;
    }

    public int getSelectedTrackIndex(TrackType type) {
        return mManifestParser.getSelectedTrackIndex(type);
    }

    public void disconnect() {
        mEventHandler.obtainMessage(MSG_DISCONNECT).sendToTarget();
    }

    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        mManifestParser.selectRepresentations(trackIndex, representations);
    }
}
