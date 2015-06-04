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

import android.annotation.SuppressLint;
import android.media.MediaDrm;
import android.media.MediaDrm.CryptoSession;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.drm.DrmUUID;

public final class SubtitleThread implements Codec {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SubtitleThread";

    private static final int MSG_SET_SOURCE = 1;

    private static final int MSG_HANDLE_SUBTITLE = 3;

    private static final int MSG_START = 7;

    private static final int MSG_PAUSE = 8;

    private static final int MSG_FLUSH = 9;

    private static final int MSG_STOP = 10;

    private static final int MSG_SEEK = 11;

    private HandlerThread mEventThread;

    private EventHandler mEventHandler;

    private MediaSource mSource;

    private final Clock mClock;

    private boolean mStarted = false;

    private SubtitleData mCurrentSubtitle = null;

    private final Handler mCallback;

    private MediaDrm mMediaDrm;

    private byte[] mMarlinSessionId;

    private CryptoSession mCryptoSession;

    private boolean mEos = false;

    private final HandlerHelper mHandlerHelper;

    public SubtitleThread(MediaSource source, Clock clock,
            Handler callback) {
        if (LOGS_ENABLED) Log.v(TAG, "Creating Subtitle thread");

        mClock = clock;
        mCallback = callback;

        mEventThread = new HandlerThread("Subtitle", Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mEventThread.start();

        mEventHandler = new EventHandler(mEventThread.getLooper());

        mEventHandler.obtainMessage(MSG_SET_SOURCE, source).sendToTarget();

        mHandlerHelper = new HandlerHelper();
    }

    public void start() {
        mEventHandler.obtainMessage(MSG_START).sendToTarget();
    }

    public void pause() {
        mEventHandler.obtainMessage(MSG_PAUSE).sendToTarget();
    }

    public void flush() {
        mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_FLUSH));
    }

    public void stop() {
        mHandlerHelper.sendMessageAndAwaitResponse(mEventHandler.obtainMessage(MSG_STOP));
        mEventThread.quit();
        mHandlerHelper.releaseAllLocks();
        mEventThread = null;
        mEventHandler = null;
        mCurrentSubtitle = null;
    }

    public void seek() {
        mEventHandler.obtainMessage(MSG_SEEK).sendToTarget();
    }

    private boolean openDrmSession(MediaFormat mediaFormat) {
        try {
            mMediaDrm = new MediaDrm(DrmUUID.MARLIN);
        } catch (UnsupportedSchemeException e) {
            if (LOGS_ENABLED) Log.e(TAG, "DRM scheme not supported", e);
            return false;
        }

        try {
            mMarlinSessionId = mMediaDrm.openSession();
        } catch (NotProvisionedException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Provisioning is needed", e);
            return false;
        } catch (ResourceBusyException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Resource busy", e);
            return false;
        }

        try {
            mMediaDrm.restoreKeys(mMarlinSessionId,
                    mediaFormat.getString(MetaData.KEY_MARLIN_JSON).getBytes("UTF-8"));
        } catch (Exception e) {
            if (LOGS_ENABLED) Log.e(TAG, "Could not get license", e);
            return false;
        }

        mCryptoSession = mMediaDrm.getCryptoSession(mMarlinSessionId,
                Util.MARLIN_SUBTITLE_CIPHER_ALGORITHM, "");

        return true;
    }

    private void closeDrmSession() {
        if (mMediaDrm != null && mMarlinSessionId != null) {
            try {
                mMediaDrm.closeSession(mMarlinSessionId);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to closeDrmSession");
            } finally {
                mMediaDrm = null;
                mMarlinSessionId = null;
                mCryptoSession = null;
            }
        }
    }

    private SubtitleData makeSubtitleData(AccessUnit accessUnit) {
        if (mSource.getMetaData().containsKey(MetaData.KEY_IPMP_DATA)) {
            if (mCryptoSession == null && !openDrmSession(accessUnit.format)) {
                mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY, CODEC_ERROR,
                        MediaError.UNKNOWN).sendToTarget();
                return null;
            }

            byte[] keyid = new byte[0];
            byte[] iv = new byte[0];
            accessUnit.data = mCryptoSession.decrypt(keyid,
                    accessUnit.data, iv);
            accessUnit.size = accessUnit.data.length;
        }

        return new SubtitleData(accessUnit.trackIndex,
                accessUnit.timeUs, accessUnit.durationUs,
                accessUnit.data, accessUnit.size);
    }

    @SuppressLint("HandlerLeak")
    class EventHandler extends Handler {

        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case MSG_SET_SOURCE:
                    mSource = (MediaSource)msg.obj;
                    break;
                case MSG_START: {
                    if (!mStarted) {
                        mStarted = true;
                        if (LOGS_ENABLED) Log.v(TAG, "Starting Subtitle thread");

                        if (!mEos) {
                            long delayMs = 0;
                            if (mCurrentSubtitle != null) {
                                // Retrigger event for paused subtitle.
                                delayMs = (mCurrentSubtitle.getStartTimeUs()
                                        - mClock.getCurrentTimeUs()) / 1000;
                                delayMs -= Configuration.SUBTITLE_PRETRIGGER_TIME_MS;
                                delayMs = (delayMs < 0) ? 0 : delayMs;
                            }

                            Message nMsg = mEventHandler.obtainMessage(MSG_HANDLE_SUBTITLE);
                            mEventHandler.sendMessageAtTime(nMsg, SystemClock.uptimeMillis()
                                    + delayMs);
                        }
                    }
                    break;
                }
                case MSG_PAUSE:
                    mStarted = false;
                    mEventHandler.removeMessages(MSG_HANDLE_SUBTITLE);
                    break;
                case MSG_FLUSH: {
                    if (LOGS_ENABLED) Log.v(TAG, "Flushing Subtitle thread");
                    mEventHandler.removeMessages(MSG_HANDLE_SUBTITLE);
                    mCurrentSubtitle = null;
                    mEos = false;
                    if (mStarted) {
                        Message nMsg = mEventHandler.obtainMessage(MSG_HANDLE_SUBTITLE);
                        mEventHandler.sendMessageDelayed(nMsg, 10);
                    }

                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = new Object();
                    reply.sendToTarget();

                    break;
                }
                case MSG_HANDLE_SUBTITLE: {
                    long delayMs;
                    if (mCurrentSubtitle != null) {
                        if ((mCurrentSubtitle.getStartTimeUs() - mClock.getCurrentTimeUs()) / 1000
                                < Configuration.SUBTITLE_PRETRIGGER_TIME_MS) {
                            if (LOGS_ENABLED) Log.v(TAG, "sending SUBTITLE data for time "
                                    + mCurrentSubtitle.getStartTimeUs());
                            mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
                                    CODEC_SUBTITLE_DATA, 0, mCurrentSubtitle).sendToTarget();
                        } else {
                            delayMs = (mCurrentSubtitle.getStartTimeUs() -
                                    mClock.getCurrentTimeUs()) / 1000;
                            delayMs -= Configuration.SUBTITLE_PRETRIGGER_TIME_MS;
                            mEventHandler.sendEmptyMessageAtTime(MSG_HANDLE_SUBTITLE,
                                    SystemClock.uptimeMillis() + delayMs);
                            break;
                        }
                    }

                    try {
                        AccessUnit accessUnit = mSource.dequeueAccessUnit(TrackType.SUBTITLE);
                        mCurrentSubtitle = null; // Should have expired by now.

                        if (accessUnit.status == AccessUnit.OK) {
                            mCurrentSubtitle = makeSubtitleData(accessUnit);

                            delayMs = (accessUnit.timeUs - mClock.getCurrentTimeUs()) / 1000;
                            delayMs -= Configuration.SUBTITLE_PRETRIGGER_TIME_MS;
                            if (LOGS_ENABLED) Log.v(TAG, "queueing SUBTITLE with "
                                    + accessUnit.size + " bytes, for time "
                                    + (accessUnit.timeUs / 1000)
                                    + " with " + delayMs + " ms delay");
                        } else if (accessUnit.status == AccessUnit.NO_DATA_AVAILABLE) {
                            if (LOGS_ENABLED) Log.v(TAG, "no data available");
                            mEventHandler.sendEmptyMessageAtTime(MSG_HANDLE_SUBTITLE,
                                    SystemClock.uptimeMillis() + 100);
                            break;
                        } else if (accessUnit.status == AccessUnit.END_OF_STREAM) {
                            if (LOGS_ENABLED) Log.v(TAG, "End of stream");
                            mEos = true;
                            break;
                        } else {
                            if (LOGS_ENABLED) Log.v(TAG, "queue EOS");
                            mEos = true;
                            break;
                        }

                    } catch (IllegalStateException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Codec error", e);
                        mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
                                CODEC_ERROR, 0).sendToTarget();
                        break;
                    }

                    mEventHandler.sendEmptyMessageAtTime(MSG_HANDLE_SUBTITLE,
                            SystemClock.uptimeMillis() + delayMs);
                    break;
                }
                case MSG_STOP: {
                    if (LOGS_ENABLED) Log.v(TAG, "Stopping Subtitle thread");

                    closeDrmSession();

                    Handler replyHandler = (Handler)msg.obj;
                    Message reply = replyHandler.obtainMessage();
                    reply.obj = new Object();
                    reply.sendToTarget();

                    break;
                }
                case MSG_SEEK: {
                    AccessUnit accessUnit = mSource.dequeueAccessUnit(TrackType.SUBTITLE);

                    if (accessUnit.status == AccessUnit.OK) {
                        SubtitleData subtitle = makeSubtitleData(accessUnit);

                        if (subtitle != null) {
                            mCallback.obtainMessage(Player.MSG_CODEC_NOTIFY,
                                    CODEC_SUBTITLE_DATA, 0, subtitle).sendToTarget();

                            if (mStarted) {
                                mEventHandler.removeMessages(MSG_HANDLE_SUBTITLE);
                                Message nMsg = mEventHandler.obtainMessage(MSG_HANDLE_SUBTITLE);
                                mEventHandler.sendMessageDelayed(nMsg, 10);
                            }
                        }
                    } else if (accessUnit.status == AccessUnit.END_OF_STREAM) {
                        if (LOGS_ENABLED) Log.v(TAG, "End of stream");
                        mEos = true;
                        break;
                    } else if (accessUnit.status != AccessUnit.NO_DATA_AVAILABLE) {
                        if (LOGS_ENABLED) Log.v(TAG, "queue EOS");
                        mEos = true;
                        break;
                    }
                }
            }
        }
    }
}
