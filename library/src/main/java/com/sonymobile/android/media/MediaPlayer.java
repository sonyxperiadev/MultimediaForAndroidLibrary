/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * NOTE: This file contains code from:
 *
 *     MediaPlayer.java
 *
 * taken from The Android Open Source Project
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

import static com.sonymobile.android.media.MediaInfo.BUFFERING_END;
import static com.sonymobile.android.media.MediaInfo.BUFFERING_START;
import static com.sonymobile.android.media.MediaInfo.VIDEO_RENDERING_START;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Vector;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.util.Log;
import android.view.SurfaceHolder;

import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.OutputControllerUpdateListener;
import com.sonymobile.android.media.internal.Player;

/**
 * MediaPlayer class for controlling media playback. <h3>State Diagram</h3>
 * <p>
 * Control of the media playback is managed via a state machine. The following
 * state diagram show the MediaPlayer state machine life cycle.
 * </p>
 * <p>
 * <img src="../../../../../images/media_player_state_diagram.png"
 * alt="MediaPlayer State diagram" border="0"/>
 * </p>
 * <h3>Valid and invalid states</h3> Table for valid and invalid states. Methods
 * that implies a state change, for instance play(), pause() etc. moves the
 * object to that state when called from a valid state. Methods that does not
 * imply a state change, for instance getCurrentPosition(), getDuration() etc.
 * does not change state when called from a valid state. Calling a method from
 * an invalid state moves the object to ERROR state. Calling any method except
 * getState() from END state always generates a InvalidStateException and does
 * not change the state. There is no way to change state from the END state
 * without recreating the player.
 * <table border="1" cellspacing="0" cellpadding="0">
 * <tr>
 * <td>Method </p></td>
 * <td>Valid states </p></td>
 * <td>Invalid states </p></td>
 * <td>Note </p></td>
 * </tr>
 * <td>getAudioSessionId </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td> </tr>
 * <tr>
 * <td>getCurrentPosition </p></td>
 * <td>{INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getDuration </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, END, ERROR, PREPARING} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getVideoHeight </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getVideoWidth </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>pause </p></td>
 * <td>{PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, ERROR, END} </p></td>
 * <td>Calls from a valid state moves to PAUSED state.</p></td>
 * </tr>
 * <tr>
 * <td>prepare </p></td>
 * <td>{INITIALIZED} </p></td>
 * <td>{IDLE, PREPARING, PREPARED, PLAYING, PAUSED, ERROR, END, COMPLETED} </p></td>
 * <td>Calls from a valid state moves to PREPARED state.</p></td>
 * </tr>
 * <tr>
 * <td>prepareAsync </p></td>
 * <td>{INITIALIZED} </p></td>
 * <td>{IDLE, PREPARING, PREPARED, PLAYING, PAUSED, ERROR, END, COMPLETED} </p></td>
 * <td>Sets the state to PREPARING until the prepare is done. After that the
 * state will be PREPARED. </p></td>
 * </tr>
 * <tr>
 * <td>release </p></td>
 * <td>any </p></td>
 * <td>{} </p></td>
 * <td>Calling release always move the object to END state. </p></td>
 * </tr>
 * <tr>
 * <td>reset </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td>When called from a valid state object state changes to IDLE. </p></td>
 * </tr>
 * <tr>
 * <td>seekTo </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setDataSource </p></td>
 * <td>{IDLE} </p></td>
 * <td>{INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR, END,
 * COMPLETED} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setDisplay </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnBufferingUpdateListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnCompletionListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnErrorListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnInfoListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnPreparedListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnSeekCompleteListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnVideoSizeChangedListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnOutputControlEventListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnSubtitleDataListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setOnRepresentationChangedListener </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setScreenOnWhilePlaying </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, ERROR,
 * COMPLETED} </p></td>
 * <td>{END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>stop </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td>Calls from a valid state moves to STOPPED state.</p></td>
 * </tr>
 * <tr>
 * <td>play </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td>Calls from a valid state moves to PLAYING state.</p></td>
 * </tr>
 * <tr>
 * <td>getState </p></td>
 * <td>any </p></td>
 * <td>{} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getMediaMetaData </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setRepresentationSelector </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setBandwidthEstimator </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setVideoScalingMode </p></td>
 * <td>{INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setVolume </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED} </p>
 * </td>
 * <td>{ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getTrackInfo </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>selectTrack </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>deselectTrack </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <tr>
 * <td>setAudioSessionId </p></td>
 * <td>{IDLE} </p></td>
 * <td>{INITIALIZED, PREPARING, PREPARED, PLAYING, PAUSED, COMPLETED, ERROR,
 * END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setWakeMode </p></td>
 * <td>any </p></td>
 * <td>{} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>getStatistics </p></td>
 * <td>{PREPARED, PLAYING, PAUSED, COMPLETED} </p></td>
 * <td>{IDLE, INITIALIZED, PREPARING, ERROR, END} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setMaxBufferSize </p></td>
 * <td>{IDLE, INITIALIZED} </p></td>
 * <td>{PREPARING, PREPARED, PLAYING, PAUSED, ERROR, END, COMPLETED} </p></td>
 * <td></p></td>
 * </tr>
 * <tr>
 * <td>setCustomVideoConfigurationParameter </p></td>
 * <td>{IDLE, INITIALIZED} </p></td>
 * <td>{PREPARING, PREPARED, PLAYING, PAUSED, ERROR, END, COMPLETED} </p></td>
 * <td></p></td>
 * </tr>
 * <td>getCustomVideoConfigurationParameter </p></td>
 * <td>any </p></td>
 * <td>{} </p></td>
 * <td></p></td>
 * </tr>
 * </table>
 */
public final class MediaPlayer {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MediaPlayer";

    // a valid iso file needs to be at least 8 bytes (ftyp header size)
    private static final int MIN_FILE_LENGTH = 8;

    /**
     * Interface definition of a callback to be invoked when video size has
     * changed.
     */
    public interface OnVideoSizeChangedListener {
        /**
         * Called when video size has changed.
         *
         * @param mp the MediaPlayer this pertains to.
         * @param width the width of the video in pixels.
         * @param height the height of the video in pixels.
         */
        void onVideoSizeChanged(MediaPlayer mp, int width, int height);
    }

    /**
     * Interface definition of a callback to be invoked when there has been an
     * error.
     */
    public interface OnErrorListener {

        /**
         * Called to indicate an error.
         *
         * @param mp the MediaPlayer the error pertains to.
         * @param what the type of error that has occurred. See
         *            {@link MediaError}.
         * @param extra an extra code specific to the error. See
         *            {@link MediaError}.
         * @return true if the method handled the error, false if it didn't.
         */
        boolean onError(MediaPlayer mp, int what, int extra);
    }

    /**
     * Interface definition of a callback to be invoked when MediaPlayer has
     * completed playback of media.
     */
    public interface OnCompletionListener {

        /**
         * Called to indicate the completion of playback.
         *
         * @param mp the MediaPlayer that has completed playback.
         */
        void onCompletion(MediaPlayer mp);
    }

    /**
     * Interface definition of a callback to be invoked when MediaPlayer has
     * been prepared and is ready for playback.
     */
    public interface OnPreparedListener {
        /**
         * Called to indicate that the MediaPlayer has been prepared.
         *
         * @param mp the MediaPlayer that has been prepared.
         */
        void onPrepared(MediaPlayer mp);
    }

    /**
     * Interface definition of a callback to be invoked to indicate buffering
     * status of a media resource being streamed over the network.
     */
    public interface OnBufferingUpdateListener {
        /**
         * Called to update the buffering level of a media played via HTTP
         * progressive download.
         *
         * @param mp the MediaPlayer that is buffering.
         * @param percent how much data that has been buffered or played, in
         *            percent.
         */
        void onBufferingUpdate(MediaPlayer mp, int percent);
    }

    /**
     * Interface definition of a callback to be invoked to communicate some info
     * and/or warning about the media or its playback.
     */
    public interface OnInfoListener {

        /**
         * Called to indicate an info or a warning.
         *
         * @param mp the MediaPlayer the info pertains to.
         * @param what the type of info or warning. See {@link MediaInfo}.
         * @param extra an extra code, specific to the info.
         * @return true if the method handled the info, false if it didn't.
         *         Returning false, or not having an OnInfoListener at all, will
         *         cause the info to be discarded.
         */
        boolean onInfo(MediaPlayer mp, int what, int extra);
    }

    /**
     * Interface definition of a callback to be invoked indicating the
     * completion of a seek operation.
     */
    public interface OnSeekCompleteListener {
        /**
         * Called to inform that the seek has been completed.
         *
         * @param mp the MediaPlayer that has completed the seek.
         */
        void onSeekComplete(MediaPlayer mp);
    }

    /**
     * Interface definition of a callback to be invoked when playing media to
     * supply subtitle data.
     */
    public interface OnSubtitleDataListener {
        /**
         * Called when new subtitle data is available.
         *
         * @param mp the MediaPlayer that the subtitles belongs to.
         * @param sd the Subtitle data.
         */
        void onSubtitleData(MediaPlayer mp, SubtitleData sd);
    }

    /**
     * Interface definition of a callback to be invoked when the representation
     * of streamed dynamic content is changed.
     */
    public interface OnRepresentationChangedListener {
        /**
         * Called when the representation is changed for the streamed data.
         *
         * @param mp the MediaPlayer playing the stream.
         * @param statistics Information regarding the representation.
         */
        public void onRepresentationChanged(MediaPlayer mp, Statistics statistics);
    }

    /**
     * Interface definition of a callback to be invoked when displaying DRM
     * protected content with output restrictions on another device.
     */
    public interface OnOutputControlEventListener {
        /**
         * Called to inform that the played media is not allowed to be rendered
         * on a connected output device.
         *
         * @param mp the MediaPlayer this pertains to.
         * @param info What type of info.
         */
        public void onOutputControlInfo(MediaPlayer mp, OutputControlInfo info);

        /**
         * Called to inform that the played media is not allowed to be rendered
         * on a connected output device and was blocked.
         *
         * @param mp the MediaPlayer this pertains to.
         * @param info What is blocked.
         */
        public void onOutputBlocked(MediaPlayer mp, OutputBlockedInfo info);
    }

    /**
     * Class that holds information regarding restrictions for DRM protected
     * content.
     */
    public static class OutputBlockedInfo {
        // multiple flags can be set at the same time, ie both // AUDIO_MUTED
        // and EXTERNAL_DISPLAY_BLOCKED
        /**
         * Flag for indicating that the audio should be muted.
         */
        public static final int AUDIO_MUTED = 1;

        /**
         * Flag for indicating that this content is not allowed to be displayed
         * on an external display.
         */
        public static final int EXTERNAL_DISPLAY_BLOCKED = 2;

        /**
         * Flag for representing what type of info this is. Used in callbacks.
         */
        public int what;
    }

    /**
     * Class that holds MetaData info for OutputControl.
     */
    public static class OutputControlInfo {
        public MetaData info;
    }

    /**
     * Class that holds information relevant to streamed dynamic content.
     */
    public static class Statistics {
        private final int linkSpeed;

        private final String serverIP;

        private String videoURI;

        /**
         * Create a new Statistics object.
         *
         * @param linkSpeed initvalue the link speed, in bits per second.
         * @param serverIP initvalue for the server IP.
         * @param videoURI initvalue for video Uri.
         */
        public Statistics(int linkSpeed, String serverIP, String videoURI) {
            this.linkSpeed = linkSpeed;
            this.serverIP = serverIP;
            this.videoURI = videoURI;
        }

        /**
         * Get the link speed of this stream.
         *
         * @return the link speed in bits per second.
         */
        public int getLinkSpeed() {
            return linkSpeed;
        }

        /**
         * Get the server IP address.
         *
         * @return the server IP address.
         */
        public String getServerIP() {
            return serverIP;
        }

        /**
         * Get the video Uri.
         *
         * @return the video Uri.
         */
        public String getVideoUri() {
            return videoURI;
        }

        /**
         * Sets the video Uri.
         *
         * @param uri the Uri to set.
         */
        public void setVideoUri(String uri) {
            videoURI = uri;
        }
    }

    /**
     * All possible states for the MediaPlayer during its life cycle.
     */
    public enum State {
        /**
         * First state after creation.
         */
        IDLE,
        /**
         * State after the data source has been set.
         */
        INITIALIZED,
        /**
         * State of the player during a prepare (i.e. after a prepareAsync()
         * call.
         */
        PREPARING,
        /**
         * State after the player has been prepared.
         */
        PREPARED,
        /**
         * State when playing media.
         */
        PLAYING,
        /**
         * State when in paused playback.
         */
        PAUSED,
        /**
         * State when something has gone wrong.
         */
        ERROR,
        /**
         * State after release() has been called on the MediaPlayer.
         */
        END,
        /**
         * State when playback has been completed.
         */
        COMPLETED
    }

    /**
     * Set the representation selector. If no representation selector is set a
     * default implementation will be used.
     *
     * @param selector the RepresentationSelector to be used.
     */
    public void setRepresentationSelector(RepresentationSelector selector) {
        synchronized (mStateLock) {
            if (mState == State.ERROR || mState == State.END) {
                throw new IllegalStateException(
                        "Can't call setRepresentationSelector in " + mState + " state.");
            }
        }

        mPlayer.setRepresentationSelector(selector);
    }

    /**
     * Set the bandwidth estimator. If no bandwidth estimator is set a default
     * implementation will be used.
     *
     * @param estimator The BandwidthEstimator to be used.
     */
    public void setBandwidthEstimator(BandwidthEstimator estimator) {
        synchronized (mStateLock) {
            if (mState == State.ERROR || mState == State.END) {
                throw new IllegalStateException(
                        "Can't call setBandwidthEstimator in " + mState + " state.");
            }
        }

        mPlayer.setBandwidthEstimator(estimator);
    }

    private OnCompletionListener mOnCompletionListener;

    private OnBufferingUpdateListener mOnBufferingListener;

    private OnPreparedListener mOnPreparedListener;

    private OnInfoListener mOnInfoListener;

    private OnSeekCompleteListener mOnSeekCompleteListener;

    // TODO: Post events to this listener
    private OnRepresentationChangedListener mOnRepresentationChangedListener;

    private OnOutputControlEventListener mOnOutputControlEventListener;

    private OnErrorListener mOnErrorListener;

    private OnSubtitleDataListener mOnSubtitleDataListener;

    private OnVideoSizeChangedListener mOnVideoSizeChangedListener;

    private Player mPlayer;

    private State mState;

    private int mAudioSessionId;

    private HandlerThread mPlayerEventThread;

    private Handler mPlayerEventHandler;

    private final Handler mCallbackDispatcher;

    private final Object mStateLock = new Object();

    private final Object mListenerLock = new Object();

    private SurfaceHolder mSurfaceHolder;

    private boolean mKeepScreenOn = false;

    private WakeLock mWakeLock;

    private Context mContext;

    private AssetFileDescriptor mAssetFileDescriptor;

    private boolean mSeekFromCompleted;

    private static class CallbackDispatcher extends Handler {

        private final WeakReference<MediaPlayer> mMediaPlayer;

        public CallbackDispatcher(WeakReference<MediaPlayer> mediaPlayer, Looper looper) {
            super(looper);
            mMediaPlayer = mediaPlayer;
        }

        @Override
        public void handleMessage(Message msg) {
            MediaPlayer thiz = mMediaPlayer.get();

            if (thiz == null) {
                if (LOGS_ENABLED)
                    Log.e(TAG, "No MediaPlayer reference. Callback will be unhandled.");
                return;
            }

            synchronized (thiz.mListenerLock) {
                switch (msg.what) {
                    case Player.NOTIFY_PREPARED:
                        if (thiz.mOnPreparedListener != null) {
                            thiz.mOnPreparedListener.onPrepared(thiz);
                        }
                        break;
                    case Player.NOTIFY_PLAYBACK_COMPLETED:
                        if (thiz.mOnCompletionListener != null) {
                            thiz.mOnCompletionListener.onCompletion(thiz);
                        }
                        break;
                    case Player.NOTIFY_BUFFERING_START:
                        if (thiz.mOnInfoListener != null) {
                            thiz.mOnInfoListener.onInfo(thiz, BUFFERING_START, msg.arg1);
                        }
                        break;
                    case Player.NOTIFY_BUFFERING_END:
                        if (thiz.mOnInfoListener != null) {
                            thiz.mOnInfoListener.onInfo(thiz, BUFFERING_END, msg.arg1);
                        }
                        break;
                    case Player.NOTIFY_SEEK_COMPLETE:
                        if (thiz.mSeekFromCompleted) {
                            thiz.mSeekFromCompleted = false;
                            break;
                        }
                        if (thiz.mOnSeekCompleteListener != null) {
                            thiz.mOnSeekCompleteListener.onSeekComplete(thiz);
                        }
                        break;
                    case Player.NOTIFY_SUBTITLE_DATA:
                        SubtitleData sub = (SubtitleData)msg.obj;
                        if (thiz.mOnSubtitleDataListener != null) {
                            thiz.mOnSubtitleDataListener.onSubtitleData(thiz, sub);
                        } else {
                            if (LOGS_ENABLED) Log.v(TAG, sub.toString());
                        }
                        break;
                    case Player.NOTIFY_VIDEO_SIZE_CHANGED:
                        if (thiz.mOnVideoSizeChangedListener != null) {
                            thiz.mOnVideoSizeChangedListener.onVideoSizeChanged(thiz, msg.arg1,
                                    msg.arg2);
                        }
                        break;
                    case Player.NOTIFY_REPRESENTATION_CHANGED:
                        if (thiz.mOnRepresentationChangedListener != null) {
                            thiz.mOnRepresentationChangedListener.onRepresentationChanged(thiz,
                                    (Statistics)msg.obj);
                        }
                        break;
                    case Player.NOTIFY_ERROR:
                        boolean errorHandled = false;
                        if (thiz.mOnErrorListener != null) {
                            errorHandled = thiz.mOnErrorListener.onError(thiz, msg.arg1, 0);
                        }
                        if (!errorHandled && thiz.mOnCompletionListener != null) {
                            thiz.mOnCompletionListener.onCompletion(thiz);
                        }
                        break;
                    case Player.NOTIFY_OUTPUTCONTROL:
                        int type = msg.arg1;
                        switch (type) {
                            case OutputControllerUpdateListener.OUTPUT_CONTROLINFO:
                                if (thiz.mOnOutputControlEventListener != null) {
                                    thiz.mOnOutputControlEventListener.onOutputControlInfo(thiz,
                                            (OutputControlInfo)msg.obj);
                                }
                                break;
                            case OutputControllerUpdateListener.OUTPUT_BLOCKED:
                                if (thiz.mOnOutputControlEventListener != null) {
                                    thiz.mOnOutputControlEventListener.onOutputBlocked(thiz,
                                            (OutputBlockedInfo)msg.obj);
                                }
                                break;
                            default:
                                if (LOGS_ENABLED) Log.w(TAG, "Unknown output control callback");
                                break;
                        }
                        break;
                    case Player.NOTIFY_VIDEO_RENDERING_START:
                        if (thiz.mOnInfoListener != null) {
                            thiz.mOnInfoListener.onInfo(thiz, VIDEO_RENDERING_START, 0);
                        }
                        break;
                    case Player.NOTIFY_BUFFERING_UPDATE:
                        if (thiz.mOnBufferingListener != null) {
                            thiz.mOnBufferingListener.onBufferingUpdate(thiz, msg.arg1);
                        }
                        break;
                    default:
                        if (LOGS_ENABLED) Log.w(TAG, "Unknown callback");
                        break;
                }
            }
            super.handleMessage(msg);
        }
    }

    private static class PlayerEventHandler extends Handler {

        private final WeakReference<MediaPlayer> mMediaPlayer;

        public PlayerEventHandler(WeakReference<MediaPlayer> mediaPlayer, Looper looper) {
            super(looper);
            mMediaPlayer = mediaPlayer;
        }

        @Override
        public void handleMessage(Message msg) {
            MediaPlayer thiz = mMediaPlayer.get();

            if (thiz == null) {
                if (LOGS_ENABLED)
                    Log.e(TAG, "No MediaPlayer reference. Message will be unhandled.");
                return;
            }

            switch (msg.what) {
                case Player.NOTIFY_PREPARED:
                    synchronized (thiz.mStateLock) {
                        thiz.mState = State.PREPARED;
                    }
                    break;
                case Player.NOTIFY_PLAYBACK_COMPLETED:
                    synchronized (thiz.mStateLock) {
                        thiz.mState = State.COMPLETED;
                        thiz.updateKeepDeviceAlive();
                    }
                    break;
                case Player.NOTIFY_ERROR:
                    thiz.release(false);
                    synchronized (thiz.mStateLock) {
                        thiz.mState = State.ERROR;
                    }
                    break;
                case Player.NOTIFY_SEEK_COMPLETE:
                    synchronized (thiz.mStateLock) {
                        if (thiz.mState == State.PLAYING) {
                            thiz.mPlayer.resume();
                        }
                    }
                    break;
                case Player.NOTIFY_BUFFERING_UPDATE:
                    synchronized (thiz.mStateLock) {
                        if (thiz.mState == State.INITIALIZED) {
                            // Don't send any callbacks about buffering after stop.
                            return;
                        }
                    }
                    break;
                case Player.NOTIFY_OUTPUTCONTROL:
                    synchronized (thiz.mStateLock) {
                        if (msg.arg1 == OutputControllerUpdateListener.OUTPUT_BLOCKED &&
                                (thiz.mState == State.PREPARED ||
                                 thiz.mState == State.PREPARING)) {
                            // Don't send callbacks for OnOutputBlocked when in preparing or
                            // prepared state.
                            return;
                        }
                    }
            }

            if (thiz.mCallbackDispatcher != null) {
                thiz.mCallbackDispatcher.obtainMessage(msg.what, msg.arg1, msg.arg2, msg.obj)
                        .sendToTarget();
            }

            super.handleMessage(msg);
        }
    }

    /**
     * Creates a new MediaPlayer and puts it in IDLE state. Won't support
     * OutputControl.
     */
    public MediaPlayer() {
        this(null);
    }

    /**
     * Creates a new MediaPlayer with a context and puts it in IDLE state.
     *
     * @param context Context to use, only needed for OutputControl to function.
     */
    public MediaPlayer(Context context) {
        // Always print the library version for debug purpose.
        Log.d(TAG,"Library version: "+BuildConfig.LIBRARY_VERSION);

        if (LOGS_ENABLED) Log.d(TAG, "MediaPlayer(Context)");
        if (context != null) {
            mContext = context.getApplicationContext();
        }
        mPlayerEventThread = new HandlerThread("MediaPlayerEventThread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mPlayerEventThread.start();

        mPlayerEventHandler = new PlayerEventHandler(new WeakReference<>(this),
                mPlayerEventThread.getLooper());

        Looper looper = Looper.myLooper();
        if (looper != null) {
            mCallbackDispatcher = new CallbackDispatcher(new WeakReference<>(this),
                    looper);
        } else {
            looper = Looper.getMainLooper();
            if (looper != null) {
                mCallbackDispatcher = new CallbackDispatcher(new WeakReference<>(this),
                        looper);
            } else {
                mCallbackDispatcher = null;
            }
        }

        mPlayer = new Player(mPlayerEventHandler, mContext, 0);
        mAudioSessionId = mPlayer.getAudioSessionId();
        mState = State.IDLE;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        // Clean up....
        release(true);
    }

    /**
     * Get the current state.
     *
     * @return current state.
     */
    public State getState() {
        if (LOGS_ENABLED) Log.d(TAG, "getState()");
        synchronized (mStateLock) {
            return mState;
        }
    }

    /**
     * Sets the display for the MediaPlayer.
     *
     * @param surfaceHolder SurfaceHolder to use.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void setDisplay(SurfaceHolder surfaceHolder) throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "setDisplay()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Setting display not available from state END");
            } else if (mState == State.ERROR) {
                return;
            }
        }

        if (mSurfaceHolder != null) {
            mSurfaceHolder.setKeepScreenOn(false);
        }

        if (surfaceHolder == null) {
            mPlayer.setSurface(null);
        } else {
            mPlayer.setSurface(surfaceHolder.getSurface());
        }
        mSurfaceHolder = surfaceHolder;

        updateKeepDeviceAlive();
    }

    /**
     * Sets the data source to use.
     *
     * @param path file path or Uri to play.
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IOException if source could not be found.
     */
    public void setDataSource(String path) throws IllegalStateException, IOException {
        if (LOGS_ENABLED) Log.d(TAG, "setDataSource(path)" + path);
        if (path == null) {
            throw new IllegalArgumentException("Path can't be null");
        }
        synchronized (mStateLock) {
            if (mState == State.ERROR) {
                // Do nothing
            } else if (mState != State.IDLE) {
                throw new IllegalStateException("Must be idle when calling setDataSource");
            } else {
                mPlayer.setDataSource(path, 0, 0x7ffffffffffffffL);

                mState = State.INITIALIZED;
            }
        }
    }

    /**
     * Sets the data source to use.
     *
     * @param context the context to use.
     * @param uri Uri to the file.
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IOException if source could not be found.
     * @throws IllegalArgumentException if Context or Uri is null.
     */
    public void setDataSource(Context context, Uri uri) throws IllegalStateException, IOException {
        if (LOGS_ENABLED) Log.d(TAG, "setDataSource(context, uri)");

        if (context == null || uri == null) {
            throw new IllegalArgumentException("Context or Uri can't be null");
        }

        synchronized (mStateLock) {
            if (mState == State.ERROR) {
                // Do nothing
            } else if (mState != State.IDLE) {
                throw new IllegalStateException("Must be idle when calling setDataSource");
            } else {
                String scheme = uri.getScheme();
                if (scheme == null || scheme.equals("file")) {
                    setDataSource(uri.getPath());
                    return;
                }

                try {
                    ContentResolver resolver = context.getContentResolver();
                    mAssetFileDescriptor = resolver.openAssetFileDescriptor(uri, "r");
                    if (mAssetFileDescriptor == null) {
                        if (LOGS_ENABLED) Log.e(TAG, "Could not open FD");
                        throw new IOException("Failed to open Uri");
                    }
                    // Note: using getDeclaredLength so that our behavior is the
                    // same as previous versions when the content provider is
                    // returning a full file.
                    if (mAssetFileDescriptor.getDeclaredLength() < 0) {
                        setDataSource(mAssetFileDescriptor.getFileDescriptor());
                    } else {
                        setDataSource(mAssetFileDescriptor.getFileDescriptor(),
                                mAssetFileDescriptor.getStartOffset(),
                                mAssetFileDescriptor.getDeclaredLength());
                    }
                    return;
                } catch (SecurityException ex) {
                    if (LOGS_ENABLED) Log.e(TAG, "Caught SecurityException ", ex);
                } catch (IOException ex) {
                    if (LOGS_ENABLED) Log.e(TAG, "Caught IOException ", ex);
                }

                if (LOGS_ENABLED) Log.d(TAG, "Couldn't open file on client side," +
                        " trying server side");
                if (mAssetFileDescriptor != null) {
                    mAssetFileDescriptor.close();
                }

                setDataSource(uri.toString());
            }
        }
    }

    /**
     * Sets the data source to use.
     *
     * @param fd FileDescriptor of media to play. You are responsible to provide
     *            an open FileDescriptor for the duration of the MediaPlayer
     *            life cycle and also to close the FileDescriptor after it has
     *            been used.
     * @param offset offset in bytes to start of media.
     * @param length length of media. -1 indicates progressive download.
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IOException if source could not be found.
     * @throws IllegalArgumentException if the FileDescriptor is null.
     */
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IllegalStateException, IOException {
        if (LOGS_ENABLED) Log.d(TAG, "setDataSource(" + fd + ", " + offset + ", " + length + ")");

        if (fd == null) {
            throw new IllegalArgumentException("FileDescriptor can't be null");
        } else if (offset < 0) {
            throw new IllegalArgumentException("Content offset is negative");
        } else if (length < MIN_FILE_LENGTH) {
            throw new IllegalArgumentException("Content length is too short");
        } else if (offset > Long.MAX_VALUE - length) {
            throw new IllegalArgumentException("Content length not supported");
        }

        synchronized (mStateLock) {
            if (mState == State.ERROR) {
                // Do nothing
            } else if (mState != State.IDLE) {
                throw new IllegalStateException("Must be idle when calling setDataSource");
            } else {

                mPlayer.setDataSource(fd, offset, length);

                mState = State.INITIALIZED;
            }
        }
    }

    /**
     * Sets the data source.
     *
     * @param path path to file.
     * @param offset offset in bytes to media.
     * @param length length of media. -1 indicates progressive download.
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IOException if source could not be found.
     * @throws IllegalArgumentException if the path is null.
     */
    public void setDataSource(String path, long offset, long length)
            throws IllegalStateException, IOException {
        if (LOGS_ENABLED)
            Log.d(TAG, "setDataSource(" + path + ", " + offset + ", " + length + ")");

        if (path == null) {
            throw new IllegalArgumentException("Path can't be null");
        } else if (offset < 0) {
            throw new IllegalArgumentException("Content offset is negative");
        } else if (length != -1) {
            if (length < MIN_FILE_LENGTH) {
                throw new IllegalArgumentException("Content length is too short");
            } else if (offset > Long.MAX_VALUE - length) {
                throw new IllegalArgumentException("Content length not supported");
            }
        }

        synchronized (mStateLock) {
            if (mState == State.ERROR) {
                // Do nothing
            } else if (mState != State.IDLE) {
                throw new IllegalStateException("Must be idle when calling setDataSource");
            } else {

                mPlayer.setDataSource(path, offset, length);

                mState = State.INITIALIZED;
            }
        }
    }

    /**
     * Sets the data source.
     *
     * @param fd FileDescriptor of media to play. You are responsible to provide
     *            an open FileDescriptor for the duration of the MediaPlayer
     *            life cycle and also to close the FileDescriptor after it has
     *            been used.
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IOException if source could not be found.
     */
    public void setDataSource(FileDescriptor fd) throws IllegalStateException, IOException {
        if (LOGS_ENABLED) Log.d(TAG, "setDataSource(fd)" + fd);
        synchronized (mStateLock) {
            if (mState == State.ERROR) {
                // Do nothing
            } else if (mState != State.IDLE) {
                throw new IllegalStateException("Must be idle when calling setDataSource");
            } else {
                setDataSource(fd, 0, 0x7ffffffffffffffL);
            }
        }
    }

    /**
     * The content is scaled to the surface dimensions
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT =
            MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT;

    /**
     * The content is scaled, maintaining its aspect ratio, the whole surface
     * area is used, content may be cropped
     */
    public static final int VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING =
            MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;

    /**
     * Sets the video scaling mode.
     *
     * @param mode must be one of two supported modes.
     *            {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT} or
     *            {@link #VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING}
     * @throws IllegalStateException if it is called in an invalid state.
     * @throws IllegalArgumentException if the mode is not one of the above
     *             mentioned modes.
     */
    public void setVideoScalingMode(int mode) throws IllegalStateException,
            IllegalArgumentException {
        if (LOGS_ENABLED) Log.d(TAG, "setVideoScalingMode(" + mode + ")");
        synchronized (mStateLock) {
            if (mState == State.IDLE || mState == State.ERROR || mState == State.END) {
                moveToErrorStateAndSendInvalidStateCallback_l();
                return;
            }
        }
        if (!isVideoScalingModeSupported(mode)) {
            final String msg = "Scaling mode " + mode + " is not supported";
            throw new IllegalArgumentException(msg);
        }
        mPlayer.setVideoScalingMode(mode);
    }

    /*
     * Test whether a given video scaling mode is supported.
     */
    private static boolean isVideoScalingModeSupported(int mode) {
        return (mode == VIDEO_SCALING_MODE_SCALE_TO_FIT ||
                mode == VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
    }

    /**
     * Sets the volume for left and right channel.
     *
     * @param leftVolume volume for left channel. Valid range 0.0 - 1.0.
     * @param rightVolume volume for right channel. Valid range 0.0 - 1.0.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void setVolume(float leftVolume, float rightVolume)
            throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "setVolume(" + leftVolume + ", " + rightVolume + ")");
        synchronized (mStateLock) {
            if (mState == State.ERROR || mState == State.END) {
                // Ignore
            } else {
                mPlayer.setVolume(leftVolume, rightVolume);
            }
        }

    }

    /**
     * Initialize the player synchronously.
     *
     * @return true if preparation succeeded, false otherwise.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public boolean prepare() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "prepare()");
        synchronized (mStateLock) {
            if (mState != State.INITIALIZED) {
                throw new IllegalStateException("Must be initialized when calling prepare");
            }
        }

        synchronized (mStateLock) {
            mState = State.PREPARING;
        }
        if (mPlayer.prepare()) {
            synchronized (mStateLock) {
                mState = State.PREPARED;
            }
            mCallbackDispatcher.sendEmptyMessage(Player.NOTIFY_PREPARED);
        } else {
            synchronized (mStateLock) {
                release(false);
                mState = State.ERROR;
            }
        }

        synchronized (mStateLock) {
            return mState == State.PREPARED;
        }
    }

    /**
     * Initialize the player asynchronously. A callback is sent to
     * OnPrepareListener when completed.
     *
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void prepareAsync() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "prepareAsync()");
        synchronized (mStateLock) {
            if (mState != State.INITIALIZED) {
                throw new IllegalStateException("Must be initialized when calling prepare");
            }

            mPlayer.prepareAsync();

            mState = State.PREPARING;
        }
    }

    /**
     * Starts playback of the media and set the MediaPlayer state to PLAYING.
     *
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void play() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "play()");
        synchronized (mStateLock) {
            if (mState == State.PREPARED) {
                mPlayer.start();
                mState = State.PLAYING;
            } else if (mState == State.PAUSED) {
                mPlayer.resume();
                mState = State.PLAYING;
            } else if (mState == State.COMPLETED) {
                if (mPlayer.getCurrentPosition() < 0
                        || mPlayer.getCurrentPosition() >= mPlayer.getDurationMs()) {
                    seekTo(0); // Seek to start first
                    mSeekFromCompleted = true;
                }
                mState = State.PLAYING;
            } else if (mState == State.PLAYING) {
                // No-op
            } else if (mState == State.END) {
                throw new IllegalStateException("Can't play from END state.");
            } else {
                moveToErrorStateAndSendInvalidStateCallback_l();
                return;
            }

            updateKeepDeviceAlive();
        }
    }

    /**
     * Pause the playback and set the MediaPlayer state to PAUSED.
     *
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void pause() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "pause()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Can't call pause in END state");
            } else if (mState != State.PLAYING && mState != State.PAUSED &&
                    mState != State.COMPLETED) {
                moveToErrorStateAndSendInvalidStateCallback_l();
            } else {
                mPlayer.pause();
                mState = State.PAUSED;

                updateKeepDeviceAlive();
            }
        }
    }

    /**
     * Stops the playback and sets the MediaPlayer to INITIALIZED state.
     *
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void stop() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "stop()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Can't call stop in END state");
            } else if (mState != State.PREPARED && mState != State.PLAYING
                    && mState != State.PAUSED && mState != State.COMPLETED) {
                moveToErrorStateAndSendInvalidStateCallback_l();
            } else {
                mPlayer.stop();
                mState = State.INITIALIZED;

                updateKeepDeviceAlive();
            }
        }
    }

    /**
     * Seeks to specified time position. Until the seek has been completed
     * getCurrentPosition() will return the value of msec. When the seek has
     * been completed the actual time sought to will be returned by
     * getCurrentPosition(). To be notified upon seek completion the
     * OnSeekComplete Listener should be used.
     *
     * @param msec the offset in milliseconds from the start to seek to. Valid
     *            range is 0 - duration of content.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void seekTo(int msec) throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "seekTo(" + msec + ")");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Can't use seekTo in END state");
            } else if (mState != State.PREPARED && mState != State.PLAYING
                    && mState != State.PAUSED && mState != State.COMPLETED) {
                moveToErrorStateAndSendInvalidStateCallback_l();
            } else {
                int seekTimeMs = msec;
                if (seekTimeMs < 0) {
                    seekTimeMs = 0;
                }

                // TODO: Add check that we don't seek beyond duration?

                if (mState == State.PLAYING) {
                    mPlayer.pause();
                }
                mPlayer.seekTo(seekTimeMs);
            }
        }
    }

    /**
     * Get track info.
     *
     * @return TrackInfo array with information such as duration, mime type.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public TrackInfo[] getTrackInfo() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "getTrackInfo()");
        synchronized (mStateLock) {
            if (mState != State.PREPARED && mState != State.PLAYING && mState != State.PAUSED
                    && mState != State.COMPLETED) {
                throw new IllegalStateException(
                        "Must be prepared, playing, paused or completed when calling getTrackInfo");
            }
        }

        return mPlayer.getTrackInfo();
    }

    /**
     * Selects a specified track.
     *
     * @param index the index of the track which should be selected. Valid range
     *            0 - (number of tracks-1).
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void selectTrack(int index) throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "selectTrack(" + index + ")");
        synchronized (mStateLock) {
            if (mState != State.PREPARED && mState != State.PLAYING && mState != State.PAUSED
                    && mState != State.COMPLETED) {
                throw new IllegalStateException(
                        "Must be prepared, playing, paused or completed when calling selectTrack");
            }
        }

        mPlayer.selectTrack(true, index, null);
    }

    /**
     * Selects a specified track and also specifies what representations that
     * should be used. Note: selecting representations will only work if the
     * default representationSelector is used. If a custom
     * representationSelector is provided it is up to the selector to not select
     * unwanted representations.
     *
     * @param index the index of the track which should be selected. Valid range
     *            0 - (number of tracks-1).
     * @param representations Vector including the representation indexes to
     *            select.
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void selectTrack(int index, Vector<Integer> representations)
            throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "selectTrack(" + index + ", representations)");
        synchronized (mStateLock) {
            if (mState != State.PREPARED && mState != State.PLAYING &&
                    mState != State.PAUSED && mState != State.COMPLETED) {
                throw new IllegalStateException(
                        "Must be prepared, playing, paused or completed when calling selectTrack");
            }
        }
        mPlayer.selectTrack(true, index, representations);
    }

    /**
     * Deselects a specified track.
     *
     * @param index the index of the track which should be deselected
     * @throws IllegalStateException if it is called in an invalid state
     */
    public void deselectTrack(int index) throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "deselectTrack(" + index + ")");
        synchronized (mStateLock) {
            if (mState != State.PREPARED && mState != State.PLAYING && mState != State.PAUSED
                    && mState != State.COMPLETED) {
                throw new IllegalStateException(
                        "Must be prepared, playing or paused when calling deselectTrack");
            }
        }

        mPlayer.selectTrack(false, index, null);
    }

    /**
     * Resets the media player to IDLE state, prepare must be called again
     * before usage.
     */
    public void reset() {
        if (LOGS_ENABLED) Log.d(TAG, "reset()");
        synchronized (mStateLock) {
            if (mState != State.END) {
                if (mPlayer != null) {
                    mPlayer.release();
                }

                // Close AssetFileDescriptor if we have opened one.
                if (mAssetFileDescriptor != null) {
                    try {
                        mAssetFileDescriptor.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    mAssetFileDescriptor = null;
                }
                if (mCallbackDispatcher != null) {
                    mCallbackDispatcher.removeCallbacksAndMessages(null);
                }
                if (mPlayerEventThread != null) {
                    mPlayerEventThread.quit();
                }
                mPlayerEventThread = new HandlerThread("MediaPlayerEventThread",
                        Thread.MAX_PRIORITY);
                mPlayerEventThread.start();

                mPlayerEventHandler = new PlayerEventHandler(new WeakReference<>(this),
                        mPlayerEventThread.getLooper());
                mPlayer = new Player(mPlayerEventHandler, mContext, mAudioSessionId);
                mState = State.IDLE;
                if (mSurfaceHolder != null) {
                    mPlayer.setSurface(mSurfaceHolder.getSurface());
                }

                updateKeepDeviceAlive();
            } else {
                throw new IllegalStateException("Reset can not be called from state END");
            }
        }
    }

    /**
     * Gets the duration of the content.
     *
     * @return the duration in milliseconds, or -1 if duration is not known
     *         (e.g. for live streams).
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public int getDuration() throws IllegalStateException {
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("getDuration not available from END state.");
            } else if (mState == State.IDLE || mState == State.INITIALIZED ||
                    mState == State.ERROR || mState == State.PREPARING) {
                moveToErrorStateAndSendInvalidStateCallback_l();
                return -1;
            }
        }

        return mPlayer.getDurationMs();
    }

    /**
     * Gets the current playback position.
     *
     * @return the current playback position in milliseconds.
     */
    public int getCurrentPosition() throws IllegalStateException {
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Position not available from END state");
            } else if (mState == State.IDLE) {
                moveToErrorStateAndSendInvalidStateCallback_l();
                return 0;
            } else if (mState == State.ERROR) {
                return 0;
            }
        }
        return mPlayer.getCurrentPosition();
    }

    /**
     * Releases the player and the resources used by the player. When release is
     * called the listeners are removed and the player is destroyed. The player
     * cannot be used anymore after a call to release().
     */
    public void release() {
        if (LOGS_ENABLED) Log.d(TAG, "release()");
        release(true);
    }

    private void release(boolean resetListeners) {
        if (LOGS_ENABLED) Log.d(TAG, "release(" + resetListeners + ")");

        synchronized (mListenerLock) {
            if (resetListeners) {
                mOnBufferingListener = null;
                mOnCompletionListener = null;
                mOnErrorListener = null;
                mOnInfoListener = null;
                mOnOutputControlEventListener = null;
                mOnPreparedListener = null;
                mOnRepresentationChangedListener = null;
                mOnSeekCompleteListener = null;
                mOnSubtitleDataListener = null;
                mOnVideoSizeChangedListener = null;
            }
        }

        synchronized (mStateLock) {
            if (mState != State.ERROR && mState != State.END) {
                if (mPlayer != null) {
                    mPlayer.release();
                    mPlayer = null;
                }

                mState = State.END;

                updateKeepDeviceAlive();
                mPlayerEventThread.quitSafely();
                mPlayerEventThread = null;
                mPlayerEventHandler = null;

                // Close AssetFileDescriptor if we have opened one.
                if (mAssetFileDescriptor != null) {
                    try {
                        mAssetFileDescriptor.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                    mAssetFileDescriptor = null;
                }
            } else if (mState == State.ERROR) {
                mState = State.END;
            }
        }
    }

    /**
     * Sets the listener for onVideoSizeChanged. Called when the video size is
     * changed.
     *
     * @param listener the listener to be set.
     */
    public void setOnVideoSizeChangedListener(OnVideoSizeChangedListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnVideoSizeChangedListener()");
        synchronized (mListenerLock) {
            mOnVideoSizeChangedListener = listener;
        }
    }

    /**
     * Sets the listener for onError. Called when an error occurs in the
     * MediaPlayer.
     *
     * @param listener the listener to be set.
     */
    public void setOnErrorListener(OnErrorListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnErrorListener()");
        synchronized (mListenerLock) {
            mOnErrorListener = listener;
        }
    }

    /**
     * Sets the listener for OnCompletion. Called when finished playback of
     * media.
     *
     * @param listener the listener to be set.
     */
    public void setOnCompletionListener(OnCompletionListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnCompletionListener()");
        synchronized (mListenerLock) {
            mOnCompletionListener = listener;
        }
    }

    /**
     * Sets the listener for OnBufferingUpdate. Called when streaming media.
     *
     * @param listener the listener to be set.
     */
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnBufferingUpdateListener()");
        synchronized (mListenerLock) {
            mOnBufferingListener = listener;
        }
    }

    /**
     * Sets the listener for OnInfo. Called when preparing and playing media.
     *
     * @param listener the listener to be set.
     */
    public void setOnInfoListener(OnInfoListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnInfoListener()");
        synchronized (mListenerLock) {
            mOnInfoListener = listener;
        }
    }

    /**
     * Sets the listener for onPrepared. Called when prepare or prepareAsync is
     * finished.
     *
     * @param listener the listener to be set.
     */
    public void setOnPreparedListener(OnPreparedListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnPreparedListener()");
        synchronized (mListenerLock) {
            mOnPreparedListener = listener;
        }
    }

    /**
     * Sets the listener for onSeekComplete. Called when a seek operation is
     * completed.
     *
     * @param listener the listener to be set.
     */
    public void setOnSeekCompleteListener(OnSeekCompleteListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnSeekCompleteListener()");
        synchronized (mListenerLock) {
            mOnSeekCompleteListener = listener;
        }
    }

    /**
     * Sets the listener for onSubtitleData. Called when new subtitle data is
     * available.
     *
     * @param listener The listener to be set.
     */
    public void setOnSubtitleDataListener(OnSubtitleDataListener listener) {
        synchronized (mListenerLock) {
            mOnSubtitleDataListener = listener;
        }
    }

    /**
     * Get the AudioSessionID for this audio session. AudioSessionID can be used
     * with any kind of {@link android.media.audiofx.AudioEffect} in Android.
     *
     * @return the AudioSessionID. If the ID is 0 something went wrong in the
     *         creation of MediaPlayer.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public int getAudioSessionId() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "getAudioSessionId()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Audio session not available after release");
            }
        }
        return mAudioSessionId;
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId the audio session ID. The audio session ID is a system
     *            wide unique identifier for the audio stream played by this
     *            MediaPlayer instance. The primary use of the audio session ID
     *            is to associate audio effects to a particular instance of
     *            MediaPlayer: if an audio session ID is provided when creating
     *            an audio effect, this effect will be applied only to the audio
     *            content of media players within the same audio session and not
     *            to the output mix. When created, a MediaPlayer instance
     *            automatically generates its own audio session ID. However, it
     *            is possible to force this player to be part of an already
     *            existing audio session by calling this method. The MediaPlayer
     *            must be in IDLE state when calling this.
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if sessionId is invalid, e.g. negative.
     */
    public void setAudioSessionId(int sessionId) throws IllegalStateException,
            IllegalArgumentException {
        if (LOGS_ENABLED) Log.d(TAG, "setAudioSessionId(" + sessionId + ")");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Can't call setAudioSessionId in END state.");
            } else if (mState != State.IDLE) {
                moveToErrorStateAndSendInvalidStateCallback_l();
            } else {
                if (sessionId < 0) {
                    throw new IllegalArgumentException("Audio Session id can not be negative");
                }

                mPlayer.setAudioSessionId(sessionId);
                mAudioSessionId = sessionId;
            }
        }
    }

    /**
     * Gets the video height.
     *
     * @return height of the video, in pixels.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public int getVideoHeight() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "getVideoHeight()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Video height not available from state END");
            }
            if (mState == State.ERROR) {
                return 0;
            }
        }
        return mPlayer.getVideoHeight();
    }

    /**
     * Gets the video width.
     *
     * @return width of the video, in pixels.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public int getVideoWidth() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "getVideoWidth()");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("Video width not available from state END");
            }
            if (mState == State.ERROR) {
                return 0;
            }
        }
        return mPlayer.getVideoWidth();
    }

    /**
     * Keep the screen on while playing media.
     *
     * @param screenOn true if the screen is to be kept on, false otherwise.
     */
    public void setScreenOnWhilePlaying(boolean screenOn) {
        if (LOGS_ENABLED) Log.d(TAG, "setScreenOnWhilePlaying(" + screenOn + ")");
        mKeepScreenOn = screenOn;
        updateKeepDeviceAlive();
    }

    /**
     * Sets the wake mode.
     *
     * @param context Context to get the wakelock from. Cannot be null.
     * @param mode what wake mode to set. Wake mode should be one of the defined
     *            in {@link android.os.PowerManager}.
     */
    @SuppressLint("Wakelock")
    public void setWakeMode(Context context, int mode) {
        if (LOGS_ENABLED) Log.d(TAG, "setWakeMode(context, " + mode + ")");

        if (context == null) {
            throw new IllegalArgumentException("Context can't be null");
        }

        boolean retakeWakeLock = false;
        if (mWakeLock != null && mWakeLock.isHeld()) {
            // We need to release, mode can have changed.
            mWakeLock.release();
            retakeWakeLock = true;
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(mode, TAG);

        // Don't let the wake lock be reference counted, this means that one
        // single call to release will release it even if it was taken several
        // times. This to make sure we don't leak.
        mWakeLock.setReferenceCounted(false);

        if (retakeWakeLock) {
            mWakeLock.acquire();
        }
    }

    /**
     * Sets the playback speed of the media.
     *
     * @param speed the speed for playback. This is implementation dependent but
     *            most platforms should support values between 0.5 - 2.0.
     * @exception IllegalStateException if it is called in an invalid state.
     */
    public void setSpeed(float speed) {
        if (LOGS_ENABLED) Log.d(TAG, "setSpeed(" + speed + ")");
        synchronized (mStateLock) {
            if (mState == State.END) {
                throw new IllegalStateException("SetSpeed not available in END state.");
            } else if (mState != State.PREPARED && mState != State.PLAYING
                    && mState != State.PAUSED
                    && mState != State.COMPLETED) {
                moveToErrorStateAndSendInvalidStateCallback_l();
            } else {
                mPlayer.setSpeed(speed);
            }
        }
    }

    /**
     * Get statistics for the media. Only works with DASH.
     *
     * @return Statistics object with information.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public Statistics getStatistics() {
        if (LOGS_ENABLED) Log.d(TAG, "getStatistics()");
        synchronized (mStateLock) {
            if (mState != State.PREPARED && mState != State.PLAYING && mState != State.PAUSED
                    && mState != State.COMPLETED) {
                throw new IllegalStateException(
                        "Must be prepared, playing or paused when calling getStatistics");
            }
        }
        return mPlayer.getStatistics();
    }

    /**
     * Sets the listener for onRepresentationChanged. Called when representation
     * is changed during playback of DASH content.
     *
     * @param listener the listener to be set.
     */
    public void setOnRepresentationChangedListener(OnRepresentationChangedListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnRepresentationChangedListener()");
        synchronized (mListenerLock) {
            mOnRepresentationChangedListener = listener;
        }
    }

    /**
     * Sets the listener for onOutPutControlEvent. Called when OutputControl is
     * used.
     *
     * @param listener the listener to be set.
     */
    public void setOnOutputControlListener(OnOutputControlEventListener listener) {
        if (LOGS_ENABLED) Log.d(TAG, "setOnOutputControlListener()");
        synchronized (mListenerLock) {
            mOnOutputControlEventListener = listener;
        }
    }

    /**
     * Get MetaData for the media.
     *
     * @return MetaData for the media source.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public MetaData getMediaMetaData() throws IllegalStateException {
        if (LOGS_ENABLED) Log.d(TAG, "getMediaMetaData()");
        synchronized (mStateLock) {

            if (mState == State.ERROR || mState == State.IDLE
                    || mState == State.INITIALIZED || mState == State.PREPARING) {
                moveToErrorStateAndSendInvalidStateCallback_l();
                return null;
            } else if (mState == State.END) {
                throw new IllegalStateException("Can't call getMediaMetaData from state END");
            }
        }

        return mPlayer.getMediaMetaData();
    }

    /**
     * Sets the maximum buffer size allowed for network buffering. Typical usage
     * is to set a value based on the application heap allocation retrieved from
     * ActivityManager.getMemoryClass()
     *
     * @param sizeMb the buffer size in megabytes. Only positive values are
     *            allowed as zero or negative value would mean no buffering is
     *            possible
     * @throws IllegalArgumentException if sizeMb < 1.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void setMaxBufferSize(int sizeMb) {
        if (sizeMb < 1) {
            throw new IllegalArgumentException("Only positive values are allowed");
        }
        synchronized (mStateLock) {
            if (mState != State.INITIALIZED && mState != State.IDLE) {
                throw new IllegalStateException(
                        "Must be initialized or idle when calling setMaxBufferSize");
            }
        }
        long bufferSizeBytes = (long)sizeMb * 1024 * 1024;
        if (bufferSizeBytes >= Integer.MAX_VALUE) {
            mPlayer.setMaxBufferSize(Integer.MAX_VALUE);
        } else {
            mPlayer.setMaxBufferSize(sizeMb * 1024 * 1024);
        }
    }

    /**
     * Set a custom video configuration parameter that will be sent to
     * MediaCodec via the injected MediaFormat in the configure function.
     *
     * @param key   The parameter key.
     * @param value The parameter value.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    public void setCustomVideoConfigurationParameter(String key, int value) {
        if (LOGS_ENABLED)
            Log.d(TAG, "setCustomVideoConfigurationParameter(" + key + ":" + value + ")");

        synchronized (mStateLock) {
            if (mState != State.IDLE && mState != State.INITIALIZED) {
                throw new IllegalStateException(
                        "Custom MediaFormat Parameter must be set in IDLE or INITIALIZED state.");
            }
            mPlayer.setCustomVideoConfigurationParameter(key, value);
        }
    }

    /**
     * Get a custom video configuration parameter that was previously set.
     *
     * @param key The parameter key.
     * @return The set value or Integer.MIN_VALUE if the parameter have not been set.
     */
    public int getCustomVideoConfigurationParameter(String key) {
        return mPlayer.getCustomVideoConfigurationParameter(key);
    }

    @SuppressLint("Wakelock")
    private void updateKeepDeviceAlive() {
        synchronized (mStateLock) {
            if (LOGS_ENABLED) Log.d(TAG, "updateKeepDeviceAlive Player State: " + mState);

            boolean isPlaying = mState == State.PLAYING;

            if (mWakeLock != null) {
                if (isPlaying && !mWakeLock.isHeld()) {
                    if (LOGS_ENABLED) Log.d(TAG, "Acquire wake lock!");
                    mWakeLock.acquire();
                } else if (!isPlaying && mWakeLock.isHeld()) {
                    if (LOGS_ENABLED) Log.d(TAG, "Release wake lock!");
                    mWakeLock.release();
                }
            }

            if (mSurfaceHolder != null) {
                if (LOGS_ENABLED)
                    Log.d(TAG, "SetKeepScreenOn(" + (mKeepScreenOn && isPlaying) + ")");
                mSurfaceHolder.setKeepScreenOn(mKeepScreenOn && isPlaying);
            }
        }
    }

    private void moveToErrorStateAndSendInvalidStateCallback_l() {
        if (mState == State.END || mState == State.ERROR) {
            // In error and end state we have already released everything.
            // Just make sure the player is in ERROR state and then dispatch
            // directly to CallbackDispatcher
            mState = State.ERROR;

            mCallbackDispatcher.sendMessage(mCallbackDispatcher.obtainMessage(Player.NOTIFY_ERROR,
                    MediaError.INVALID_STATE, 0));
        } else {
            // Need to release resources and destroy the player, send the
            // callback with PlayerEventHandler and use the normal release
            // mechanism.
            mPlayerEventHandler.sendMessage(mPlayerEventHandler.obtainMessage(Player.NOTIFY_ERROR,
                    MediaError.INVALID_STATE, 0));
        }
    }
}
