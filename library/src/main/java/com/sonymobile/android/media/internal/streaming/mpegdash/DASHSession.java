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
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import java.util.Vector;

import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.DASHTrackInfo;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MetaDataImpl;
import com.sonymobile.android.media.internal.MimeType;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.ContentProtection;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Period;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Representation;
import com.sonymobile.android.media.internal.streaming.common.DefaultBandwidthEstimator;
import com.sonymobile.android.media.internal.streaming.common.PacketSource;

public final class DASHSession {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DASHSession";

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

    public static final int FETCHER_DRM_INFO = 3;

    public static final int FETCHER_UPDATE_STATISTICS = 4;

    public static final String KEY_TIMEUS = "timeus";

    public static final String KEY_REMOTE_IP = "remoteIP";

    public static final String KEY_VIDEO_URI = "videoURI";

    private static final int MAX_BUFFER_DURATION_US = 10000000;

    private final HandlerThread mEventThread;

    private final EventHandler mEventHandler;

    private final Handler mCallbackHandler;

    private MPDParser mMPDParser;

    private boolean mBuffering = true;

    private final EnumMap<TrackType, RepresentationFetcher> mFetchers = new EnumMap<>(TrackType.class);

    private final EnumMap<TrackType, PacketSource> mPacketSources = new EnumMap<>(TrackType.class);

    private BandwidthEstimator mBandwidthEstimator;

    private RepresentationSelector mRepresentationSelector;

    private long mLastDequeuedTimeUs;

    private final MetaDataImpl mMetaData = new MetaDataImpl();

    private boolean mSeekPending = false;

    private String mVideoServerIP;

    private String mVideoURI;

    private final int mMaxBufferSize;

    private final int[] mMaxBufferSizes;

    public DASHSession(Handler callbackHandler, BandwidthEstimator estimator,
            RepresentationSelector selector, int maxBufferSize) {

        mCallbackHandler = callbackHandler;

        mEventThread = new HandlerThread("DASH");
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
            long minBufferTimeUs = mMPDParser.getMinBufferTimeUs();
            PacketSource audioPacketSource = mPacketSources.get(TrackType.AUDIO);

            if (audioPacketSource.getBufferDuration() < minBufferTimeUs
                    && (mFetchers.containsKey(TrackType.AUDIO) || mSeekPending)) {
                return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
            }

            PacketSource videoPacketSource = mPacketSources.get(TrackType.VIDEO);

            if (videoPacketSource.getBufferDuration() < minBufferTimeUs
                    && mFetchers.containsKey(TrackType.VIDEO)) {
                return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
            }

            mBuffering = false;

            mCallbackHandler.obtainMessage(DASHSource.MSG_BUFFERING_END).sendToTarget();
        }

        PacketSource packetSource = mPacketSources.get(type);

        if (!packetSource.hasBufferAvailable()) {
            if (type != TrackType.SUBTITLE) {
                mBuffering = true;

                mCallbackHandler.obtainMessage(DASHSource.MSG_BUFFERING_START).sendToTarget();
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
        return mMPDParser.getMaxVideoInputBufferSize();
    }

    private static class EventHandler extends Handler {

        private final WeakReference<DASHSession> mSession;

        public EventHandler(WeakReference<DASHSession> session, Looper looper) {
            super(looper);

            mSession = session;
        }

        @Override
        public void handleMessage(Message msg) {
            DASHSession thiz = mSession.get();
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

                            if (!thiz.mMPDParser.hasNextPeriod()) {
                                packetSource.queueAccessUnit(AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                                if (thiz.mFetchers.size() == 0 &&
                                        packetSource.getFormat() == null) {
                                    if (thiz.mSeekPending) {
                                        thiz.mCallbackHandler.obtainMessage(DASHSource
                                                .SOURCE_BUFFERING_END).sendToTarget();
                                        PacketSource audioPacketSource = thiz.mPacketSources
                                                .get(TrackType.AUDIO);
                                        PacketSource subtitlePacketSource = thiz.mPacketSources
                                                .get(TrackType.SUBTITLE);
                                        audioPacketSource.queueAccessUnit(
                                                AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                                        subtitlePacketSource.queueAccessUnit(
                                                AccessUnit.ACCESS_UNIT_END_OF_STREAM);
                                        thiz.mSeekPending = false;
                                    } else {
                                        thiz.mCallbackHandler.obtainMessage(DASHSource.MSG_ERROR)
                                                .sendToTarget();
                                    }
                                }
                            } else if (thiz.mFetchers.size() == 0) {
                                thiz.mMPDParser.nextPeriod();

                                int[] selectedRepresentations = thiz.mMPDParser
                                        .getSelectedRepresentations();
                                int[] selectedTracks = thiz.mMPDParser.getSelectedTracks();
                                thiz.mRepresentationSelector
                                        .selectRepresentations(
                                                thiz.mBandwidthEstimator.getEstimatedBandwidth(),
                                                selectedTracks,
                                                selectedRepresentations);
                                thiz.mMPDParser.updateRepresentations(selectedRepresentations);
                                thiz.changeConfiguration(-1);
                            }
                            break;
                        }
                        case FETCHER_TIME_ESTABLISHED: {
                            Bundle data = msg.getData();
                            long videoTimeUs = data.getLong(KEY_TIMEUS, 0);

                            Representation audioRepresentation = thiz.mMPDParser
                                    .getRepresentation(TrackType.AUDIO);

                            if (audioRepresentation != null) {
                                thiz.addFetcher(TrackType.AUDIO, audioRepresentation, videoTimeUs);
                            }

                            thiz.mLastDequeuedTimeUs = videoTimeUs;
                            thiz.mSeekPending = false;

                            Representation subtitleRepresentation = thiz.mMPDParser
                                    .getRepresentation(TrackType.SUBTITLE);

                            if (subtitleRepresentation != null) {
                                thiz.addFetcher(TrackType.SUBTITLE, subtitleRepresentation,
                                        videoTimeUs);
                            }

                            break;
                        }
                        case FETCHER_ERROR: {
                            PacketSource audioPacketSource = thiz.mPacketSources.
                                    get(TrackType.AUDIO);
                            PacketSource videoPacketSource = thiz.mPacketSources.
                                    get(TrackType.VIDEO);

                            boolean audioOnly = thiz.mMPDParser.
                                    getRepresentation(TrackType.VIDEO) == null;

                            if (!audioPacketSource.hasBufferAvailable() || (!audioOnly &&
                                    !videoPacketSource.hasBufferAvailable())) {
                                if (LOGS_ENABLED) Log.e(TAG, "Fetcher reported error");
                                PacketSource packetSource = thiz.mPacketSources.get(type);
                                thiz.mCallbackHandler.obtainMessage(DASHSource.MSG_ERROR)
                                        .sendToTarget();
                                packetSource.queueAccessUnit(AccessUnit.ACCESS_UNIT_ERROR);
                                thiz.mFetchers.remove(type);
                            }

                            break;
                        }
                        case FETCHER_DRM_INFO: {
                            thiz.mMetaData.addValue(MetaData.KEY_DRM_UUID,
                                    msg.getData().getByteArray(MetaData.KEY_DRM_UUID));
                            thiz.mMetaData.addValue(MetaData.KEY_DRM_PSSH_DATA, msg.getData()
                                    .getByteArray(MetaData.KEY_DRM_PSSH_DATA));
                            break;
                        }
                        case FETCHER_UPDATE_STATISTICS: {
                            thiz.mVideoServerIP = msg.getData().getString(KEY_REMOTE_IP);
                            thiz.mVideoURI = msg.getData().getString(KEY_VIDEO_URI);
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
                        for (Map.Entry<TrackType, RepresentationFetcher> item : thiz.mFetchers
                                .entrySet()) {
                            RepresentationFetcher fetcher = item.getValue();
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
        String uri;
        HttpURLConnection urlConnection;
        try {
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

                mMPDParser = new MPDParser(uri);

                if (mBandwidthEstimator == null) {
                    mBandwidthEstimator = new DefaultBandwidthEstimator();
                }

                if (mRepresentationSelector == null) {
                    // Fall back to default.
                    mRepresentationSelector =
                            new DefaultDASHRepresentationSelector(mMPDParser, mMaxBufferSize);
                }

                success = mMPDParser.parse(urlConnection.getInputStream());
                if (success) {
                    mMetaData.addValue(MetaData.KEY_MIME_TYPE, MimeType.MPEG_DASH);
                    mMetaData.addValue(MetaData.KEY_MPD, mMPDParser.getMPDFile());

                    boolean isLive = mMPDParser.getDurationUs() == -1;
                    ContentProtection contentProtection = mMPDParser.getContentProtection();
                    if (contentProtection != null ) {
                        if (contentProtection.uuid != null) {
                            mMetaData.addValue(MetaData.KEY_DRM_UUID, contentProtection.uuid);
                        }
                        if (contentProtection.psshData != null) {
                            mMetaData.addValue(MetaData.KEY_DRM_PSSH_DATA,
                                    contentProtection.psshData);
                        }
                    }
                    mMetaData.addValue(MetaData.KEY_PAUSE_AVAILABLE, isLive ? 0 : 1);
                    mMetaData.addValue(MetaData.KEY_SEEK_AVAILABLE, isLive ? 0 : 1);

                    mCallbackHandler.obtainMessage(DASHSource.MSG_PREPARED).sendToTarget();

                    int[] selectedRepresentations = mMPDParser.getSelectedRepresentations();
                    int[] selectedTracks = mMPDParser.getSelectedTracks();
                    TrackInfo[] trackInfo = mMPDParser.getTrackInfo();
                    mRepresentationSelector.selectDefaultRepresentations(selectedTracks, trackInfo,
                            selectedRepresentations);
                    mMPDParser.updateRepresentations(selectedRepresentations);
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
            mCallbackHandler.obtainMessage(DASHSource.MSG_PREPARE_FAILED, error, 0).sendToTarget();
        }
    }

    public void onSelectTrack(int index, TrackType type) {
        mPacketSources.get(type).clear();
        mFetchers.remove(type);

        int[] selectedRepresentations = mMPDParser.getSelectedRepresentations();
        int[] selectedTracks = mMPDParser.getSelectedTracks();
        mRepresentationSelector.selectRepresentations(mBandwidthEstimator.getEstimatedBandwidth(),
                selectedTracks, selectedRepresentations);
        mMPDParser.updateRepresentation(type, selectedRepresentations[type.ordinal()]);

        Representation representation = mMPDParser.getRepresentation(type);

        if (!mSeekPending && representation != null) {
            addFetcher(type, representation, mLastDequeuedTimeUs);
        }
    }

    public void onChangeConfiguration(Message msg) {
        changeConfiguration(msg.arg1);
    }

    public void onDownloadNext() {
        if (mFetchers.size() == 0) {
            return;
        }

        RepresentationFetcher selectedFetcher = null;
        for (Map.Entry<TrackType, RepresentationFetcher> item : mFetchers.entrySet()) {
            RepresentationFetcher fetcher = item.getValue();
            TrackType type = item.getKey();
            int maxBufferSize = mMaxBufferSizes[type.ordinal()];

            if (selectedFetcher == null) {
                if (!fetcher.isBufferFull(MAX_BUFFER_DURATION_US, maxBufferSize)) {
                    selectedFetcher = fetcher;
                }
            } else if (!fetcher.isBufferFull(MAX_BUFFER_DURATION_US, maxBufferSize) &&
                    (fetcher.getNextTimeUs() < selectedFetcher.getNextTimeUs() ||
                    (fetcher.getNextTimeUs() == selectedFetcher.getNextTimeUs() &&
                    fetcher.getState() < selectedFetcher.getState()))) {
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

        Representation audioRepresentation = mMPDParser.getRepresentation(TrackType.AUDIO);

        Representation videoRepresentation = mMPDParser.getRepresentation(TrackType.VIDEO);

        Representation subtitleRepresentation = mMPDParser.getRepresentation(TrackType.SUBTITLE);

        RepresentationFetcher audioFetcher = mFetchers.get(TrackType.AUDIO);

        if (audioFetcher != null
                && (timeUs > -1 || audioFetcher.getRepresentation() != audioRepresentation)) {
            mFetchers.remove(TrackType.AUDIO);
            audioFetcher = null;
        }

        RepresentationFetcher videoFetcher = mFetchers.get(TrackType.VIDEO);

        if (videoFetcher != null
                && (timeUs > -1 || videoFetcher.getRepresentation() != videoRepresentation)) {
            if (videoRepresentation != null) {
                if (videoRepresentation.segmentBase != null) {
                    mVideoURI = videoRepresentation.segmentBase.url;
                }

                mCallbackHandler.obtainMessage(DASHSource.MSG_REPRESENTATION_CHANGED,
                        getStatistics()).sendToTarget();
            }

            mFetchers.remove(TrackType.VIDEO);
            videoFetcher = null;
        }

        RepresentationFetcher subtitleFetcher = mFetchers.get(TrackType.SUBTITLE);

        if (subtitleFetcher != null
                && (timeUs > -1 || subtitleFetcher.getRepresentation() != subtitleRepresentation)) {
            mFetchers.remove(TrackType.SUBTITLE);
            subtitleFetcher = null;
        }

        mCallbackHandler.obtainMessage(DASHSource.MSG_CHANGE_SUBTITLE,
                subtitleRepresentation != null ? 1 : 0, 0).sendToTarget();

        if (videoFetcher == null && videoRepresentation != null) {
            addFetcher(TrackType.VIDEO, videoRepresentation, timeUs);
            if (timeUs > 0) {
                mSeekPending = true;
            }
        }

        if (!mSeekPending && audioFetcher == null && audioRepresentation != null) {
            addFetcher(TrackType.AUDIO, audioRepresentation, timeUs);
        }

        if (!mSeekPending && subtitleFetcher == null && subtitleRepresentation != null) {
            addFetcher(TrackType.SUBTITLE, subtitleRepresentation, timeUs);
        }

        if (mMaxBufferSize > 0) {
            // Recalculate buffer allocation between streams
            mMaxBufferSizes[TrackType.VIDEO.ordinal()] = 0;
            mMaxBufferSizes[TrackType.AUDIO.ordinal()] = 0;
            mMaxBufferSizes[TrackType.SUBTITLE.ordinal()] = 0;
            int totalBandwidth = 0;

            if (videoRepresentation != null) {
                totalBandwidth += videoRepresentation.bandwidth;
            }
            if (audioRepresentation != null) {
                totalBandwidth += audioRepresentation.bandwidth;
            }
            if (subtitleRepresentation != null) {
                totalBandwidth += subtitleRepresentation.bandwidth;
            }

            if (subtitleRepresentation != null) {
                double bufferShare = ((double)subtitleRepresentation.bandwidth) / totalBandwidth;
                mMaxBufferSizes[TrackType.SUBTITLE.ordinal()] = (int)(mMaxBufferSize * bufferShare);
            }
            if (audioRepresentation != null) {
                if (videoRepresentation == null) {
                    // no video, let audio have all remaining buffer
                    mMaxBufferSizes[TrackType.AUDIO.ordinal()] = mMaxBufferSize
                            - mMaxBufferSizes[TrackType.SUBTITLE.ordinal()];
                } else {
                    double bufferShare = ((double)audioRepresentation.bandwidth) / totalBandwidth;
                    mMaxBufferSizes[TrackType.AUDIO.ordinal()] = (int)(mMaxBufferSize * bufferShare);
                }
            }
            if (videoRepresentation != null) {
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
        mMPDParser.seekTo(timeUs);

        mPacketSources.get(TrackType.AUDIO).clear();
        mPacketSources.get(TrackType.VIDEO).clear();
        mPacketSources.get(TrackType.SUBTITLE).clear();

        mPacketSources.get(TrackType.AUDIO).setClosed(false);
        mPacketSources.get(TrackType.VIDEO).setClosed(false);
        mPacketSources.get(TrackType.SUBTITLE).setClosed(false);

        int[] selectedRepresentations = mMPDParser.getSelectedRepresentations();
        int[] selectedTracks = mMPDParser.getSelectedTracks();
        mRepresentationSelector.selectRepresentations(
                mBandwidthEstimator.getEstimatedBandwidth(),
                selectedTracks,
                selectedRepresentations);
        mMPDParser.updateRepresentations(selectedRepresentations);

        changeConfiguration(timeUs);
    }

    private void addFetcher(TrackType type, Representation representation, long nextTimeUs) {
        if (mFetchers.containsKey(type)) {
            throw new RuntimeException();
        }

        int trackIndex = -1;
        if (type == TrackType.SUBTITLE) {
            trackIndex = mMPDParser.getSelectedTrackIndex(type);
        }

        mFetchers.put(type,
                new RepresentationFetcher(this, representation, mPacketSources.get(type), type,
                        nextTimeUs, mMPDParser.getPeriodTimeOffsetUs(), trackIndex));
    }

    public Message getFetcherCallbackMessage(TrackType type) {
        return mEventHandler.obtainMessage(MSG_FETCHER_CALLBACK, type);
    }

    public boolean checkBandwidth() {
        int[] selectedTracks = mMPDParser.getSelectedTracks();
        int[] selectedRepresentations = mMPDParser.getSelectedRepresentations();

        if (mRepresentationSelector.selectRepresentations(
                mBandwidthEstimator.getEstimatedBandwidth(), selectedTracks,
                selectedRepresentations)) {
            mMPDParser.updateRepresentations(selectedRepresentations);
            mEventHandler.obtainMessage(MSG_CHANGE_CONFIGURATION, -1, 0).sendToTarget();
            return true;
        }
        return false;
    }

    public long getDurationUs() {
        return mMPDParser.getDurationUs();
    }

    public long getActivePeriodEndTime() {
        Period period = mMPDParser.getActivePeriod();
        return period.durationUs + period.startTimeUs;
    }

    public DASHTrackInfo[] getTrackInfo() {
        return mMPDParser.getTrackInfo();
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
        TrackType type = mMPDParser.selectTrack(select, index);

        if (type != TrackType.UNKNOWN) {
            mEventHandler.obtainMessage(MSG_SELECT_TRACK, index, 0, type).sendToTarget();
        }

        return type;
    }

    public int getSelectedTrackIndex(TrackType type) {
        return mMPDParser.getSelectedTrackIndex(type);
    }

    public void disconnect() {
        mEventHandler.obtainMessage(MSG_DISCONNECT).sendToTarget();
    }

    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        mMPDParser.selectRepresentations(trackIndex, representations);
    }

    public Statistics getStatistics() {
        return new Statistics((int)mBandwidthEstimator.getEstimatedBandwidth(), mVideoServerIP,
                mVideoURI);
    }
}
