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
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import android.content.Context;
import android.media.MediaDrm;
import android.media.MediaDrm.MediaDrmStateException;
import android.media.MediaFormat;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.drm.DrmSession;
import com.sonymobile.android.media.internal.drm.DrmSession.DrmLicenseException;
import com.sonymobile.android.media.internal.drm.DrmSessionFactory;
import com.sonymobile.android.media.internal.drm.DrmUUID;
import com.sonymobile.android.media.internal.streaming.mpegdash.DASHSource;

public final class Player {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "Player";

    private static final int MSG_ERROR = -1;

    private static final int MSG_PREPARE = 1;

    private static final int MSG_SCAN_SOURCES = 2;

    private static final int MSG_START = 3;

    private static final int MSG_PAUSE = 4;

    private static final int MSG_SET_SURFACE = 6;

    private static final int MSG_RESUME = 7;

    public static final int MSG_SOURCE_NOTIFY = 8;

    private static final int MSG_SEEK = 9;

    private static final int MSG_STOP = 10;

    public static final int MSG_CODEC_NOTIFY = 11;

    private static final int MSG_GET_VIDEO_WIDTH = 12;

    private static final int MSG_GET_VIDEO_HEIGHT = 13;

    private static final int MSG_GET_AUDIO_SESSION_ID = 14;

    private static final int MSG_WAIT_FOR_SETUP_COMPLETE = 15;

    private static final int MSG_GET_TRACK_INFO = 16;

    private static final int MSG_SELECT_TRACK = 17;

    private static final int MSG_SET_SPEED = 18;

    private static final int MSG_GET_MEDIA_META_DATA = 19;

    private static final int MSG_SET_BANDWIDTH_ESTIMATOR = 20;

    private static final int MSG_SET_REPRESENTATION_SELECTOR = 21;

    private static final int MSG_GET_STATISTICS = 22;

    private static final int MSG_SET_VIDEO_SCALING_MODE = 24;

    private static final int MSG_WAIT_FOR_VIDEO_READY_TO_RENDER = 25;

    public static final int MSG_DRM_NOTIFY = 26;

    public static final int NOTIFY_PREPARED = 1;

    public static final int NOTIFY_ERROR = 3;

    public static final int NOTIFY_BUFFERING_START = 4;

    public static final int NOTIFY_BUFFERING_END = 5;

    public static final int NOTIFY_SEEK_COMPLETE = 6;

    public static final int NOTIFY_SUBTITLE_DATA = 7;

    public static final int NOTIFY_PLAYBACK_COMPLETED = 8;

    public static final int NOTIFY_VIDEO_SIZE_CHANGED = 9;

    public static final int NOTIFY_REPRESENTATION_CHANGED = 10;

    public static final int NOTIFY_OUTPUTCONTROL = 11;

    public static final int NOTIFY_VIDEO_RENDERING_START = 12;

    public static final int NOTIFY_BUFFERING_UPDATE = 13;

    private static final int DEFAULT_SEEK_DELAY_MS = 200;

    private AudioThread mAudioThread;

    private VideoCodecThread mVideoThread;

    private SubtitleThread mSubtitleThread;

    private Surface mSurface;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private MediaSource mSource;

    private final Handler mCallbacks;

    private float mLeftVolume = -1;

    private float mRightVolume = -1;

    private boolean mAudioCompleted;

    private boolean mVideoCompleted;

    private int mDurationMs = -1;

    private long mSeekPositionMs = -1;

    private String mDataSourcePath;

    private FileDescriptor mDataSourceFd;

    private long mDataSourceOffset;

    private long mDataSourceLength;

    private BandwidthEstimator mBandwidthEstimator;

    private RepresentationSelector mRepresentationSelector;

    private int mAudioSessionId = 0;

    private DrmSession mDrmSession;

    private Clock mClockSource;

    private int mCurrentPositionMs = 0;

    private boolean mFlushingAudio = false;

    private final Object mGetPositionLock = new Object();

    private Handler mPrepareHandler;

    private final Context mContext;

    private int mVideoScalingMode;

    private int mMaxBufferSize = -1;

    private int mVideoWidth = 0;

    private int mVideoHeight = 0;

    private int mRotation = 0;

    private float mPlaybackSpeed = 1.0f;

    private boolean mStarted = false;

    private boolean mInternalSeekTriggered = false;

    private boolean mVideoSeekPending = false;

    private boolean mErrorHasOccured = false;

    private final HashMap<String, Integer> mCustomVideoMediaFormatParams;

    private Message mExecutingSeekMessage;

    private Message mPendingSeekMessage;

    private final HandlerHelper mHandlerHelper;

    public Player(Handler callbackListener, Context context, int audioSessionId) {
        mContext = context;

        if (audioSessionId > 0) {
            mAudioSessionId = audioSessionId;
        } else {
            mAudioSessionId = AudioSessionManager.generateNewAudioSessionId(mContext);
        }

        mCustomVideoMediaFormatParams = new HashMap<>();

        mEventThread = new HandlerThread("Player");
        mEventThread.start();

        mEventHandler = new EventHandler(new WeakReference<>(this),
                mEventThread.getLooper());

        mCallbacks = callbackListener;

        mHandlerHelper = new HandlerHelper();
    }

    public void setSurface(Surface surface) {
        mEventHandler.obtainMessage(MSG_SET_SURFACE, surface).sendToTarget();
    }

    public void setDataSource(String path, long offset, long length) {
        mDataSourcePath = path;
        mDataSourceFd = null;
        mDataSourceOffset = offset;
        mDataSourceLength = length;
    }

    public void setDataSource(FileDescriptor fd, long offset, long length) {
        mDataSourcePath = null;
        mDataSourceFd = fd;
        mDataSourceOffset = offset;
        mDataSourceLength = length;
    }

    public void setAudioSessionId(int audioSessionId) {
        mAudioSessionId = audioSessionId;
    }

    public void setVolume(float leftVolume, float rightVolume) {
        if (leftVolume < 0 || leftVolume > 1 || rightVolume < 0 || rightVolume > 1) {
            throw new IllegalArgumentException("Volume must be between 0.0 and 1.0");
        }

        mLeftVolume = leftVolume;
        mRightVolume = rightVolume;

        if (mAudioThread != null) {
            mAudioThread.setVolume(mLeftVolume, mRightVolume);
        }
    }

    public boolean prepare() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_PREPARE));

        return reply != null ? (Boolean)reply : false;
    }

    public void prepareAsync() {
        mEventHandler.obtainMessage(MSG_PREPARE).sendToTarget();
    }

    public synchronized void start() {
        mStarted = true;
        mEventHandler.obtainMessage(MSG_START).sendToTarget();
    }

    public synchronized void pause() {
        mStarted = false;
        mEventHandler.obtainMessage(MSG_PAUSE).sendToTarget();
    }

    public synchronized void resume() {
        mStarted = true;
        mEventHandler.sendEmptyMessage(MSG_RESUME);
    }

    public synchronized void stop() {
        mStarted = false;
        Message nMsg = mEventHandler.obtainMessage(MSG_STOP, 0, 0);
        mEventHandler.sendMessageAtFrontOfQueue(nMsg);
    }

    public synchronized void seekTo(int msec) {
        Message msg = mEventHandler.obtainMessage(MSG_SEEK, msec, -1);
        if (mSource.supportsPreview()) {
            msg.sendToTarget();
        } else {
            mEventHandler
                    .sendMessageAtTime(msg, SystemClock.uptimeMillis() + DEFAULT_SEEK_DELAY_MS);
        }
    }

    public int getDurationMs() {
        return mDurationMs;
    }

    public TrackInfo[] getTrackInfo() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage
                (MSG_GET_TRACK_INFO));

        return (TrackInfo[])reply;
    }

    public void selectTrack(boolean select, int index, Vector<Integer> representations) {
        mEventHandler.obtainMessage(MSG_SELECT_TRACK, select ? 1 : 0, index, representations)
                .sendToTarget();
    }

    public int getCurrentPosition() {
        synchronized (mGetPositionLock) {
            return mCurrentPositionMs;
        }
    }

    public MetaData getMediaMetaData() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_GET_MEDIA_META_DATA));

        return reply != null ? (MetaData)reply : new MetaDataImpl();
    }

    private void onSeek(int msec, boolean internal) {
        if (!mSource.supportsPreview() && msec == 0 && mCurrentPositionMs == 0 &&
                mAudioThread == null && mVideoThread == null) {
            mCallbacks.obtainMessage(NOTIFY_SEEK_COMPLETE).sendToTarget();
            mExecutingSeekMessage = null;
            mPendingSeekMessage = null;
            return;
        }

        mInternalSeekTriggered = internal;
        mCurrentPositionMs = msec;
        if (mSeekPositionMs < 0) {
            mSeekPositionMs = msec;
            if (mClockSource != null) {
                mClockSource.setSeekTimeUs(msec * 1000L);
            }

            if (mAudioThread != null) {
                mFlushingAudio = true;
                mAudioThread.flush();
            }
            if (mVideoThread != null) {
                mVideoThread.flush();
                mVideoThread.updateAudioClockOnNextVideoFrame();
            }
            if (mSubtitleThread != null) {
                mSubtitleThread.flush();
            }

            mSource.seekTo(msec * 1000L);

            if (mVideoThread == null && mAudioThread == null) {
                // Both Audio and Video are Null. Prepared state send callback
                // directly.
                mSeekPositionMs = -1;
                mExecutingSeekMessage = null;
                mCallbacks.obtainMessage(NOTIFY_SEEK_COMPLETE).sendToTarget();
                return;
            }

            if ((mVideoThread == null && mSource.getSelectedTrackIndex(TrackType.VIDEO) == -1)
                    && (mAudioThread != null ||
                    mSource.getSelectedTrackIndex(TrackType.AUDIO) > -1)) {
                // Audio only. Send callback directly and mark as seek complete.
                mSeekPositionMs = -1;
                mExecutingSeekMessage = null;
                mCallbacks.obtainMessage(NOTIFY_SEEK_COMPLETE).sendToTarget();
                return;
            }

            if (mSource.supportsPreview() && mVideoThread != null) {
                // Source support frame preview.
                mVideoSeekPending = true;
                mVideoThread.seek();
            } else {
                mCallbacks.obtainMessage(NOTIFY_SEEK_COMPLETE).sendToTarget();
                mSeekPositionMs = -1;
                mExecutingSeekMessage = null;
            }
            if (mSource.supportsPreview() && mSubtitleThread != null) {
                mSubtitleThread.seek();
            }
        } else {
            if (LOGS_ENABLED) Log.d(TAG, "Seek in progress, queue next seek to: " + msec);
        }
    }

    public synchronized void release() {
        // Call stop and mark as release request.
        Message nMsg = mEventHandler.obtainMessage(MSG_STOP, 1, 0);
        mEventHandler.sendMessageAtFrontOfQueue(nMsg);

        if (mDrmSession != null) {
            mDrmSession.close();
            mDrmSession = null;
        }
    }

    public int getVideoHeight() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_GET_VIDEO_HEIGHT));

        return reply != null ? (Integer)reply : 0;
    }

    public int getVideoWidth() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_GET_VIDEO_WIDTH));

        return reply != null ? (Integer)reply : 0;
    }

    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        mEventHandler.obtainMessage(MSG_SET_BANDWIDTH_ESTIMATOR, estimator).sendToTarget();
    }

    public void setRepresentationSelector(RepresentationSelector selector) {
        mEventHandler.obtainMessage(MSG_SET_REPRESENTATION_SELECTOR, selector).sendToTarget();
    }

    public int getAudioSessionId() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_GET_AUDIO_SESSION_ID));

        return reply != null ? (Integer)reply : -1;
    }

    public void setSpeed(float speed) {
        if (speed < Util.MIN_PLAYBACK_SPEED || speed > Util.MAX_PLAYBACK_SPEED) {
            throw new IllegalArgumentException("Speed must be between " + Util.MIN_PLAYBACK_SPEED
                    + " and " + Util.MAX_PLAYBACK_SPEED);
        }

        mEventHandler.obtainMessage(MSG_SET_SPEED, speed).sendToTarget();
    }

    public Statistics getStatistics() {
        Object reply = mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler
                .obtainMessage(MSG_GET_STATISTICS));
        if (reply instanceof Statistics) {
            return (Statistics)reply;
        }

        return null;
    }

    public void setVideoScalingMode(int mode) {
        mEventHandler.obtainMessage(MSG_SET_VIDEO_SCALING_MODE, mode, 0).sendToTarget();
    }

    public void setMaxBufferSize(int size) {
        mMaxBufferSize = size;
    }

    public void setCustomVideoConfigurationParameter(String key, int value) {
        mCustomVideoMediaFormatParams.put(key, value);
    }

    public int getCustomVideoConfigurationParameter(String key) {
        if (mCustomVideoMediaFormatParams.containsKey(key)) {
            return mCustomVideoMediaFormatParams.get(key);
        }

        return Integer.MIN_VALUE;
    }

    private boolean isVideoSetupComplete() {
        boolean hasVideo = mSource.getSelectedTrackIndex(TrackType.VIDEO) != -1;

        return !hasVideo || (mVideoThread != null && mVideoThread.isSetupCompleted());
    }

    private boolean isAudioSetupComplete() {
        boolean hasAudio = mSource.getSelectedTrackIndex(TrackType.AUDIO) != -1;

        return !hasAudio || (mAudioThread != null && mAudioThread.isSetupCompleted());
    }

    private static class EventHandler extends Handler {

        private final WeakReference<Player> mPlayer;

        public EventHandler(WeakReference<Player> player, Looper looper) {
            super(looper);

            mPlayer = player;
        }

        @Override
        public void handleMessage(Message msg) {
            Player thiz = mPlayer.get();
            switch (msg.what) {
                case MSG_SET_SURFACE:
                    thiz.mSurface = (Surface)msg.obj;

                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.stop();
                        thiz.mVideoThread = null;

                        thiz.mEventHandler.obtainMessage(MSG_SCAN_SOURCES).sendToTarget();
                    }

                    break;
                case MSG_PREPARE:
                    if (msg.obj != null) {
                        thiz.mPrepareHandler = (Handler)msg.obj;
                    }
                    if (thiz.mDataSourceFd != null) {
                        try {
                            thiz.mSource = new SimpleSource(thiz.mDataSourceFd, thiz.mDataSourceOffset,
                                    thiz.mDataSourceLength, thiz.mEventHandler);
                        } catch (IllegalArgumentException e) {
                            // Ignore will be handled below.
                        }
                    } else {
                        if (HttpSnifferSource.canHandle(thiz.mDataSourcePath)) {
                            thiz.mSource = new HttpSnifferSource(thiz.mDataSourcePath,
                                    thiz.mEventHandler, thiz.mMaxBufferSize);
                        } else if (DASHSource.canHandle(thiz.mDataSourcePath)) {
                            thiz.mSource = new DASHSource(thiz.mDataSourcePath, thiz.mEventHandler,
                                    thiz.mMaxBufferSize);
                        } else {
                            thiz.mSource = new SimpleSource(thiz.mDataSourcePath,
                                    thiz.mDataSourceOffset,
                                    thiz.mDataSourceLength,
                                    thiz.mEventHandler,
                                    thiz.mMaxBufferSize);
                        }
                    }

                    if (thiz.mSource == null) {
                        if (thiz.mPrepareHandler != null) {
                            Message reply = thiz.mPrepareHandler.obtainMessage();
                            reply.obj = false;
                            reply.sendToTarget();
                            thiz.mPrepareHandler = null;
                        }
                        thiz.onError(MediaError.UNSUPPORTED);
                    } else {
                        thiz.mSource.setBandwidthEstimator(thiz.mBandwidthEstimator);
                        thiz.mSource.setRepresentationSelector(thiz.mRepresentationSelector);
                        thiz.mSource.prepareAsync();
                    }

                    break;
                case MSG_SCAN_SOURCES:
                    boolean audioTrackAvailable =
                            thiz.mSource.getSelectedTrackIndex(TrackType.AUDIO) > -1;
                    boolean videoTrackAvailable =
                            thiz.mSource.getSelectedTrackIndex(TrackType.VIDEO) > -1;
                    boolean subtitleTrackAvailable =
                            thiz.mSource.getSelectedTrackIndex(TrackType.SUBTITLE) > -1;

                    if (!audioTrackAvailable && !videoTrackAvailable
                            && !subtitleTrackAvailable) {
                        // No recognized tracks, signal error
                        thiz.onError(MediaError.UNSUPPORTED);
                        return;
                    }

                    if (thiz.mAudioThread == null) {
                        MediaFormat audioFormat = thiz.mSource.getFormat(TrackType.AUDIO);

                        if (audioFormat != null) {
                            thiz.mAudioThread = new AudioThread(audioFormat, thiz.mSource,
                                    thiz.mAudioSessionId, thiz.mEventHandler, thiz.mDrmSession);
                            if (thiz.mLeftVolume != -1 && thiz.mRightVolume != -1) {
                                thiz.mAudioThread.setVolume(thiz.mLeftVolume, thiz.mRightVolume);
                            }

                            thiz.mClockSource = thiz.mAudioThread;
                            thiz.mClockSource.setSeekTimeUs(thiz.mCurrentPositionMs * 1000L);
                        }
                    }

                    if ((thiz.mAudioThread != null || !audioTrackAvailable)
                            && thiz.mVideoThread == null) {
                        MediaFormat videoFormat = thiz.mSource.getFormat(TrackType.VIDEO);

                        if (videoFormat != null) {
                            if (thiz.mAudioThread == null) {
                                thiz.mClockSource = new ClockImpl(thiz.mEventHandler);
                                thiz.mClockSource.setSeekTimeUs(thiz.mCurrentPositionMs * 1000L);
                            }
                            if (thiz.mSurface != null) {
                                thiz.mVideoThread = new VideoThread(videoFormat, thiz.mSource,
                                        thiz.mSurface, thiz.mClockSource, thiz.mEventHandler,
                                        thiz.mDrmSession, thiz.mVideoScalingMode,
                                        thiz.mCustomVideoMediaFormatParams);

                                if (thiz.mVideoWidth != 0 || thiz.mVideoHeight != 0) {
                                    // We have already found a video size,
                                    // inform the VideoThread about that.
                                    thiz.mVideoThread.setWidth(thiz.mVideoWidth);
                                    thiz.mVideoThread.setHeight(thiz.mVideoHeight);
                                }
                            } else {
                                thiz.mVideoThread = new DummyVideoThread(videoFormat, thiz.mSource,
                                        thiz.mClockSource, thiz.mEventHandler);
                            }
                        }
                    }

                    if (subtitleTrackAvailable && thiz.mSubtitleThread == null
                            && thiz.mClockSource != null) {
                        MediaFormat subtitleFormat =
                                thiz.mSource.getFormat(TrackType.SUBTITLE);
                        if (subtitleFormat != null) {
                            thiz.mSubtitleThread = new
                                    SubtitleThread(thiz.mSource, thiz.mClockSource,
                                            thiz.mEventHandler);
                        }
                    }

                    if (thiz.mClockSource != null) {
                        thiz.mClockSource.setSpeed(thiz.mPlaybackSpeed);
                    }
                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.setSpeed(thiz.mPlaybackSpeed);
                    }

                    if ((thiz.mAudioThread == null && audioTrackAvailable)
                            || (thiz.mVideoThread == null && videoTrackAvailable)
                            || (subtitleTrackAvailable && thiz.mSubtitleThread == null)) {
                        Message nMsg = thiz.mEventHandler.obtainMessage(MSG_SCAN_SOURCES);
                        thiz.mEventHandler.sendMessageDelayed(nMsg, 100);
                    } else {
                        sendEmptyMessageAtTime(MSG_WAIT_FOR_SETUP_COMPLETE,
                                SystemClock.uptimeMillis() + 50);
                    }
                    break;
                case MSG_WAIT_FOR_SETUP_COMPLETE: {
                    if (thiz.isVideoSetupComplete() && thiz.isAudioSetupComplete()) {

                        synchronized (thiz) {
                            if (!thiz.mStarted && !thiz.mVideoSeekPending
                                    && thiz.mSource.supportsPreview()) {
                                // This call is only to trigger that one frame
                                // is rendered due to a seek in prepared state
                                // where the player is then started and paused
                                // rapidly.
                                thiz.onSeek(thiz.mCurrentPositionMs, true);
                            }

                            if (thiz.mStarted) {
                                if (thiz.mVideoThread != null) {
                                    thiz.mVideoThread.start();
                                    Message nMsg = thiz.mEventHandler
                                            .obtainMessage(MSG_WAIT_FOR_VIDEO_READY_TO_RENDER);
                                    thiz.mEventHandler.sendMessageAtTime(nMsg,
                                            SystemClock.uptimeMillis() + 3);
                                } else {
                                    if (thiz.mSubtitleThread != null) {
                                        thiz.mSubtitleThread.start();
                                    }
                                    thiz.mClockSource.start();
                                }
                            }
                        }
                    } else {
                        Message nMsg = thiz.mEventHandler
                                .obtainMessage(MSG_WAIT_FOR_SETUP_COMPLETE);
                        thiz.mEventHandler.sendMessageDelayed(nMsg, 50);
                    }
                    break;
                }
                case MSG_WAIT_FOR_VIDEO_READY_TO_RENDER: {
                    if (thiz.mVideoThread != null) {
                        if (thiz.mVideoThread.isReadyToRender()) {
                            if (thiz.mSubtitleThread != null) {
                                thiz.mSubtitleThread.start();
                            }
                            thiz.mClockSource.start();
                        } else {
                            Message nMsg = thiz.mEventHandler
                                    .obtainMessage(MSG_WAIT_FOR_VIDEO_READY_TO_RENDER);
                            thiz.mEventHandler.sendMessageAtTime(nMsg,
                                    SystemClock.uptimeMillis() + 3);
                        }
                    }
                    break;
                }
                case MSG_START:
                    thiz.mSource.start();
                    thiz.mEventHandler.obtainMessage(MSG_SCAN_SOURCES).sendToTarget();
                    thiz.mVideoCompleted = false;
                    thiz.mAudioCompleted = false;
                    if (thiz.mDrmSession != null &&
                            thiz.mDrmSession.getOutputController() != null) {
                        thiz.mDrmSession.getOutputController().update();
                    }
                    break;
                case MSG_PAUSE:
                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.pause();
                    }

                    if (thiz.mClockSource != null) {
                        thiz.mClockSource.pause();
                    }

                    if (thiz.mSubtitleThread != null) {
                        thiz.mSubtitleThread.pause();
                    }
                    break;
                case MSG_RESUME:
                    if (thiz.mExecutingSeekMessage != null || thiz.mPendingSeekMessage != null
                            || hasMessages(MSG_SEEK)) {
                        break;
                    }
                    if (hasMessages(MSG_SCAN_SOURCES)
                            || hasMessages(MSG_WAIT_FOR_SETUP_COMPLETE)) {
                        // Still in setup-loop, mStarted should have been set
                        // and playback will start when ready.
                        break;
                    }

                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.start();
                    }

                    if (thiz.mVideoThread == null || thiz.mVideoThread.isReadyToRender()) {
                        // Either this is a audio only file or video is ready to
                        // render.
                        if (thiz.mClockSource != null) {
                            thiz.mClockSource.start();
                        }

                        if (thiz.mSubtitleThread != null) {
                            thiz.mSubtitleThread.start();
                        }
                    } else {
                        // Wait for video to be ready to render.
                        sendEmptyMessage(MSG_WAIT_FOR_VIDEO_READY_TO_RENDER);
                    }

                    thiz.mVideoCompleted = false;
                    thiz.mAudioCompleted = false;
                    if (thiz.mDrmSession != null &&
                            thiz.mDrmSession.getOutputController() != null) {
                        thiz.mDrmSession.getOutputController().update();
                    }
                    break;
                case MSG_STOP:
                    // Remove any messages in queue. If sources are scanned when
                    // stop or release is called there could be messages in
                    // queue.
                    thiz.mEventHandler.removeCallbacksAndMessages(null);
                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.pause();
                        thiz.mVideoThread.stop();
                    }
                    if (thiz.mAudioThread != null) {
                        thiz.mAudioThread.pause();
                    }
                    if (thiz.mClockSource != null) {
                        thiz.mClockSource.stop();
                    }
                    if (thiz.mSubtitleThread != null) {
                        thiz.mSubtitleThread.pause();
                        thiz.mSubtitleThread.stop();
                    }
                    if (thiz.mSource != null) {
                        thiz.mSource.release();
                    }
                    if (thiz.mDrmSession != null) {
                        thiz.mDrmSession.close();
                        thiz.mDrmSession = null;
                    }
                    thiz.mVideoThread = null;
                    thiz.mAudioThread = null;
                    thiz.mSubtitleThread = null;
                    thiz.mClockSource = null;
                    thiz.mSource = null;
                    // Marked as a release request
                    if (msg.arg1 == 1) {
                        thiz.mEventThread.quit();
                        thiz.mEventHandler = null;
                        thiz.mEventThread = null;
                    }
                    thiz.mHandlerHelper.releaseAllLocks();
                    break;
                case MSG_SEEK:
                    if (thiz.mExecutingSeekMessage == null) {
                        if (hasMessages(MSG_SEEK)) {
                            break;
                        }
                        // No seek in progress.
                        thiz.mExecutingSeekMessage = msg;
                        thiz.onSeek(msg.arg1, false);
                    } else if (hasMessages(MSG_SEEK)) {
                        // There are more incoming seeks, ignore this one!
                        break;
                    } else if (msg.arg1 == thiz.mSeekPositionMs) {
                        // No need to seek to same position as the running seek.
                        break;
                    } else {
                        // Seek in progress, queue it will be picked up when seek is complete.
                        thiz.mPendingSeekMessage =
                                thiz.mEventHandler.obtainMessage(MSG_SEEK, msg.arg1, -1);
                    }
                    break;
                case MSG_ERROR:
                    if (LOGS_ENABLED)
                        Log.e(TAG, "Player Error occurred!");
                    thiz.onError(MediaError.UNKNOWN);
                    break;
                case MSG_GET_TRACK_INFO: {
                    TrackInfo[] info = thiz.mSource.getTrackInfo();
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = info;
                    reply.sendToTarget();
                    break;
                }
                case MSG_SELECT_TRACK:
                    boolean select = msg.arg1 == 1;
                    @SuppressWarnings("unchecked")
                    Vector<Integer> representations = (Vector<Integer>)msg.obj;

                    if (representations != null) {
                        thiz.mSource.selectRepresentations(msg.arg2, representations);
                    }

                    TrackType type = thiz.mSource.selectTrack(select, msg.arg2);

                    if (type == TrackType.AUDIO && thiz.mAudioThread != null) {
                        thiz.mAudioThread.flush();
                    } else if (type == TrackType.VIDEO && thiz.mVideoThread != null) {
                        thiz.mVideoThread.flush();
                    } else if (type == TrackType.SUBTITLE) {
                        if (select) {
                            if (thiz.mClockSource != null) {
                                if (thiz.mSubtitleThread != null) {
                                    thiz.mSubtitleThread.stop();
                                }
                                thiz.mSubtitleThread = new SubtitleThread(thiz.mSource,
                                        thiz.mClockSource, thiz.mEventHandler);
                                thiz.mSubtitleThread.start();
                            }
                        } else {
                            if (thiz.mSubtitleThread != null) {
                                thiz.mSubtitleThread.stop();
                                thiz.mSubtitleThread = null;
                            }
                        }
                    }
                    break;
                case MSG_GET_MEDIA_META_DATA: {
                    MetaData metadata = thiz.mSource.getMetaData();
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = metadata;
                    reply.sendToTarget();
                    break;
                }
                case MSG_GET_VIDEO_WIDTH: {
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = thiz.mVideoWidth;
                    reply.sendToTarget();
                    break;
                }
                case MSG_GET_VIDEO_HEIGHT: {
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = thiz.mVideoHeight;
                    reply.sendToTarget();
                    break;
                }
                case MSG_SET_BANDWIDTH_ESTIMATOR:
                    if (thiz.mSource == null) {
                        thiz.mBandwidthEstimator = (BandwidthEstimator)msg.obj;
                    } else {
                        thiz.mSource.setBandwidthEstimator((BandwidthEstimator)msg.obj);
                    }
                    break;
                case MSG_SET_REPRESENTATION_SELECTOR:
                    if (thiz.mSource == null) {
                        thiz.mRepresentationSelector = (RepresentationSelector)msg.obj;
                    } else {
                        thiz.mSource.setRepresentationSelector((RepresentationSelector)msg.obj);
                    }
                    break;
                case MSG_GET_AUDIO_SESSION_ID: {
                    int audioSessionId = thiz.mAudioSessionId;
                    if (thiz.mAudioThread != null) {
                        audioSessionId = thiz.mAudioThread.getAudioSessionId();
                    }
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = audioSessionId;
                    reply.sendToTarget();
                    break;
                }
                case MSG_SET_SPEED:
                    if (thiz.mClockSource != null) {
                        thiz.mClockSource.setSpeed((Float)msg.obj);
                    }
                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.setSpeed((Float)msg.obj);
                    }
                    thiz.mPlaybackSpeed = (Float)msg.obj;
                    break;
                case MSG_GET_STATISTICS: {
                    Statistics statistics = thiz.mSource.getStatistics();
                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    // Return dummy object in case of null or the call would
                    // hang.
                    reply.obj = statistics == null ? 0 : statistics;
                    reply.sendToTarget();
                    break;
                }
                case MSG_SET_VIDEO_SCALING_MODE:
                    thiz.mVideoScalingMode = msg.arg1;

                    if (thiz.mVideoThread != null) {
                        thiz.mVideoThread.setVideoScalingMode(msg.arg1);
                    }
                    break;
                case MSG_CODEC_NOTIFY:
                    switch (msg.arg1) {
                        case Codec.CODEC_ERROR:
                            if (LOGS_ENABLED)
                                Log.e(TAG, "Codec Error occurred!");
                            thiz.onError(msg.arg2);
                            break;
                        case Codec.CODEC_AUDIO_COMPLETED:
                            if (thiz.mVideoThread == null || thiz.mVideoCompleted) {
                                if (!thiz.mAudioCompleted) {
                                    thiz.mCurrentPositionMs = thiz.mDurationMs;
                                    thiz.mCallbacks.obtainMessage(NOTIFY_PLAYBACK_COMPLETED)
                                            .sendToTarget();
                                }
                            }
                            thiz.mAudioCompleted = true;
                            break;
                        case Codec.CODEC_VIDEO_COMPLETED:
                            if (thiz.mAudioThread == null) {
                                thiz.mClockSource.pause();
                            }

                            if (thiz.mAudioThread == null || thiz.mAudioCompleted) {
                                if (!thiz.mVideoCompleted) {
                                    thiz.mCurrentPositionMs = thiz.mDurationMs;
                                    thiz.mCallbacks.obtainMessage(NOTIFY_PLAYBACK_COMPLETED)
                                            .sendToTarget();
                                }
                            }
                            thiz.mVideoCompleted = true;
                            break;
                        case Codec.CODEC_SUBTITLE_DATA:
                            if (LOGS_ENABLED) Log.v(TAG, "Got Subtitle data");
                            int index = thiz.mSource.getSelectedTrackIndex(TrackType.SUBTITLE);
                            if (((SubtitleData) msg.obj).getTrackIndex() == index) {
                                thiz.mCallbacks.obtainMessage(NOTIFY_SUBTITLE_DATA, msg.obj)
                                        .sendToTarget();
                            } else {
                                if (LOGS_ENABLED) Log.w(TAG, "Wrong subtitle source:" + index +
                                        " data:" + ((SubtitleData) msg.obj).getTrackIndex());
                            }
                            break;
                        case Codec.CODEC_VIDEO_FORMAT_CHANGED:
                            thiz.mVideoWidth = msg.getData().getInt(MetaData.KEY_WIDTH);
                            thiz.mVideoHeight = msg.getData().getInt(MetaData.KEY_HEIGHT);

                            if (thiz.mRotation == 90 || thiz.mRotation == 270) {
                                int temp = thiz.mVideoWidth;
                                thiz.mVideoWidth = thiz.mVideoHeight;
                                thiz.mVideoHeight = temp;
                            }

                            if (thiz.mCallbacks != null) {
                                thiz.mCallbacks.obtainMessage(NOTIFY_VIDEO_SIZE_CHANGED,
                                        thiz.mVideoWidth, thiz.mVideoHeight).sendToTarget();
                            }
                            break;
                        case Codec.CODEC_NOTIFY_POSITION:
                            if (!thiz.mFlushingAudio) {
                                synchronized (thiz.mGetPositionLock) {
                                    thiz.mCurrentPositionMs = msg.arg2;
                                }
                            }
                            break;
                        case Codec.CODEC_FLUSH_COMPLETED:
                            thiz.mFlushingAudio = false;
                            break;
                        case Codec.CODEC_VIDEO_SEEK_COMPLETED:
                            if (thiz.mPendingSeekMessage != null) {
                                // Execute queued seek
                                thiz.mEventHandler.sendMessage(thiz.mPendingSeekMessage);
                                thiz.mPendingSeekMessage = null;
                            } else {
                                if (!thiz.mInternalSeekTriggered) {
                                    thiz.mCallbacks.obtainMessage(NOTIFY_SEEK_COMPLETE)
                                            .sendToTarget();
                                } else {
                                    thiz.mCurrentPositionMs = msg.arg2;
                                }
                                thiz.mInternalSeekTriggered = false;
                            }
                            thiz.mExecutingSeekMessage = null;
                            thiz.mVideoSeekPending = false;
                            thiz.mSeekPositionMs = -1;
                            break;
                        case Codec.CODEC_VIDEO_RENDERING_START:
                            if (thiz.mCallbacks != null) {
                                thiz.mCallbacks.obtainMessage(NOTIFY_VIDEO_RENDERING_START)
                                        .sendToTarget();
                            }
                            break;
                        default:
                            if (LOGS_ENABLED) Log.v(TAG, "Unknown codec message");
                            break;
                    }
                    break;
                case MSG_SOURCE_NOTIFY:
                    switch (msg.arg1) {
                        case MediaSource.SOURCE_PREPARED:
                            thiz.mDurationMs = (int) (thiz.mSource.getDurationUs() / 1000);
                            boolean setupDrmSessionOk = thiz.tryOpenDrmSession();

                            if (setupDrmSessionOk) {
                                if (!thiz.tryRestoreDrmKeys()) {
                                    thiz.tryRequestDrmKeys();
                                    break;
                                }
                            }

                            if (thiz.mPrepareHandler != null) {
                                Message replyMsg = thiz.mPrepareHandler.obtainMessage();
                                replyMsg.obj = setupDrmSessionOk;
                                replyMsg.sendToTarget();
                                thiz.mPrepareHandler = null;
                            } else if (setupDrmSessionOk) {
                                thiz.mCallbacks.obtainMessage(NOTIFY_PREPARED).sendToTarget();
                            }
                            if (setupDrmSessionOk) {
                                thiz.notifyVideoSize(thiz.mSource.getMetaData());
                            }
                            break;
                        case MediaSource.SOURCE_PREPARE_FAILED:
                            if (thiz.mPrepareHandler != null) {
                                Message replyMsg = thiz.mPrepareHandler.obtainMessage();
                                replyMsg.obj = false;
                                replyMsg.sendToTarget();
                                thiz.mPrepareHandler = null;
                            }

                            thiz.onError(msg.arg2);
                            break;
                        case MediaSource.SOURCE_BUFFERING_START:
                            thiz.mCallbacks.obtainMessage(NOTIFY_BUFFERING_START).sendToTarget();
                            break;
                        case MediaSource.SOURCE_BUFFERING_END:
                            thiz.mCallbacks.obtainMessage(NOTIFY_BUFFERING_END).sendToTarget();
                            break;
                        case MediaSource.SOURCE_CHANGE_SUBTITLE:
                            boolean haveSubtitle = msg.arg2 == 1;

                            if (thiz.mSubtitleThread == null && haveSubtitle
                                    && thiz.mClockSource != null) {
                                thiz.mSubtitleThread = new
                                        SubtitleThread(thiz.mSource, thiz.mClockSource,
                                                thiz.mEventHandler);
                                thiz.mSubtitleThread.start();
                            } else if (thiz.mSubtitleThread != null && !haveSubtitle) {
                                thiz.mSubtitleThread.stop();
                                thiz.mSubtitleThread = null;
                            }
                            break;
                        case MediaSource.SOURCE_REPRESENTATION_CHANGED:
                            thiz.mCallbacks.obtainMessage(NOTIFY_REPRESENTATION_CHANGED, msg.obj)
                                    .sendToTarget();
                            break;
                        case MediaSource.SOURCE_ERROR:
                            thiz.onError(MediaError.IO);
                            break;
                        case MediaSource.SOURCE_BUFFERING_UPDATE:
                                thiz.mCallbacks.obtainMessage(NOTIFY_BUFFERING_UPDATE, msg.arg2, 0)
                                        .sendToTarget();
                            break;
                        default:
                            if (LOGS_ENABLED) Log.v(TAG, "Unknown source message");
                            break;
                    }
                    break;
                case MSG_DRM_NOTIFY: {
                    boolean drmSetupOk = (boolean)msg.obj;

                    if (thiz.mPrepareHandler != null) {
                        Message replyMsg = thiz.mPrepareHandler.obtainMessage();
                        replyMsg.obj = drmSetupOk;
                        replyMsg.sendToTarget();
                        thiz.mPrepareHandler = null;
                    } else if (drmSetupOk) {
                        thiz.mCallbacks.obtainMessage(NOTIFY_PREPARED).sendToTarget();
                    }

                    if (drmSetupOk) {
                        thiz.mDrmSession.initOutputController(thiz.mContext,
                                new OutputControllerUpdateListener(thiz));
                        thiz.notifyVideoSize(thiz.mSource.getMetaData());
                    } else {
                        thiz.onError(msg.arg1);
                    }
                    break;
                }
                default:
                    if (LOGS_ENABLED) Log.v(TAG, "Unknown message");
                    break;
            }
        }
    }

    private void onError(int what) {
        if(!mErrorHasOccured) {
            mErrorHasOccured = true;
            if (mVideoThread != null) {
                mVideoThread.pause();
            }
            if (mAudioThread != null) {
                mAudioThread.pause();
            }
            if (mSubtitleThread != null) {
                mSubtitleThread.pause();
            }
            mCallbacks.obtainMessage(NOTIFY_ERROR, what, 0).sendToTarget();
        }
    }

    private boolean tryOpenDrmSession() {
        MetaData fileMeta = mSource.getMetaData();
        if (mDrmSession == null
                && fileMeta.containsKey(MetaData.KEY_DRM_UUID)
                && Util.bytesToHex(fileMeta.getByteBuffer(MetaData.KEY_DRM_UUID))
                        .equals(Util.PLAY_READY_SYSTEM_ID)) {
            if (mContext == null) {
                if (LOGS_ENABLED)
                    Log.e(TAG, "No context provided. Unable to create DRM session.");
                onError(MediaError.DRM_UNKNOWN);
                return false;
            } else {
                try {
                    byte[] psshData =
                            fileMeta.getByteBuffer(MetaData.KEY_DRM_PSSH_DATA);

                    Map<UUID, byte[]> psshInfo = new HashMap<>();
                    psshInfo.put(DrmUUID.PLAY_READY, psshData);

                    mDrmSession = DrmSessionFactory.create(DrmUUID.PLAY_READY,
                            psshInfo);
                    mDrmSession.open();

                    return true;

                } catch (DrmLicenseException e) {
                    if (LOGS_ENABLED)
                        Log.e(TAG, "DrmLicenseException when creating DrmSession", e);
                    onError(e.getErrorCode());
                    return false;
                } catch (IllegalArgumentException e) {
                    if (LOGS_ENABLED)
                        Log.e(TAG, "IllegalArgumentException when creating DrmSession", e);
                    onError(MediaError.DRM_UNKNOWN);
                    return false;
                } catch (UnsupportedSchemeException e) {
                    if (LOGS_ENABLED)
                        Log.e(TAG, "UnsupportedSchemeException when creating DrmSession", e);
                    onError(MediaError.DRM_UNKNOWN);
                    return false;
                } catch (RuntimeException e) { // MediaDrmStateException API Level 21
                    if (LOGS_ENABLED)
                        Log.e(TAG, "RuntimeException when creating DrmSession", e);
                    onError(MediaError.DRM_UNKNOWN);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean tryRestoreDrmKeys() {
        if (mDrmSession != null) {
            try {
                mDrmSession.restoreKey();
                return true;
            } catch (DrmLicenseException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "DrmLicenseException when trying to restore keys", e);
                }
                return false;
            } catch (IllegalArgumentException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "IllegalArgumentException when trying to restore keys", e);
                }
                return false;
            } catch (RuntimeException e) { // MediaDrmStateException API Level 21
                if (LOGS_ENABLED) {
                    Log.e(TAG, "RuntimeException when when trying to restore keys", e);
                }
                return false;
            }
        }
        return true;
    }

    private void tryRequestDrmKeys() {
        if (mDrmSession != null) {
            MetaData fileMeta = mSource.getMetaData();
            int keyType = mSource.isStreaming() ?
                    MediaDrm.KEY_TYPE_STREAMING : MediaDrm.KEY_TYPE_OFFLINE;

            mDrmSession.requestKey(fileMeta.getString(MetaData.KEY_MIME_TYPE),
                    keyType, mEventHandler);
        }
    }

    void onOutputControlEvent(int type, Object obj) {
        mCallbacks.obtainMessage(NOTIFY_OUTPUTCONTROL, type, 0, obj).sendToTarget();
    }

    private void notifyVideoSize(MetaData format) {
        if (format != null) {
            boolean hasVideoSize = false;

            if (format.containsKey(MetaData.KEY_WIDTH)) {
                mVideoWidth = format.getInteger(MetaData.KEY_WIDTH);
                hasVideoSize = true;
            }

            if (format.containsKey(MetaData.KEY_HEIGHT)) {
                mVideoHeight = format.getInteger(MetaData.KEY_HEIGHT);
                hasVideoSize = true;
            }

            if (format.containsKey(MetaData.KEY_ROTATION_DEGREES)) {
                mRotation = format.getInteger(MetaData.KEY_ROTATION_DEGREES);
            }

            if (mRotation == 90 || mRotation == 270) {
                int temp = mVideoWidth;
                mVideoWidth = mVideoHeight;
                mVideoHeight = temp;
            }

            if (hasVideoSize) {
                if (mCallbacks != null) {
                    mCallbacks.obtainMessage(NOTIFY_VIDEO_SIZE_CHANGED,
                            mVideoWidth, mVideoHeight).sendToTarget();
                }
            }
        }
    }
}
