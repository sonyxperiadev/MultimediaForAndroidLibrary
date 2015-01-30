/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnCompletionListener;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.TestContentProvider;
import com.sonymobile.android.media.TestHelper;
import com.sonymobile.android.media.Utils;

public class StateTest extends TestHelper {

    private static final boolean LOGS_ENABLED = false;

    private static final String TAG = "StateTest";

    // This should reflect no. of method strings in TestHelper.java
    private static final int NUMBER_OF_METHODS = 35;

    private MediaPlayer mMediaPlayer;

    private Looper sLooper = null;

    private ArrayList<Integer> mListOfMethods;

    private boolean mNoUncaughtException;

    private String mUncaughtExceptionMessage;

    private String mTestName;

    private String mMethodName;

    private boolean mCallback;

    private Context mContext;

    private TestContentProvider mTestContentProvider;

    public StateTest(TestContentProvider testContentProvider,
            Context context) {
        mTestContentProvider = testContentProvider;
        mContext = context;
        assertNotNull("Context is null", context);
        assertNotNull("No test content, Local",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_LOCAL));
        assertNotNull("No content uri, Local",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_LOCAL).getContentUri());
        assertNotNull("No test content, Offset",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_OFFSET));
        assertNotNull("No content uri, Offset",
                testContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL_WITH_OFFSET).getContentUri());
        assertNotNull("No test content, MediaStore",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_MEDIASTORE));
        assertNotNull("No content uri, MediaStore",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_MEDIASTORE)
                        .getContentUri());
        assertNotNull("No test content, Invalid",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_INVALID));
        assertNotNull("No content uri, Invalid",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_INVALID).getContentUri());
        assertTrue("Offset is less than 1",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_OFFSET)
                        .getOffset() > 0);
        assertTrue("Length is less than 1",
                testContentProvider.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_OFFSET)
                        .getLength() > 0);
    }

    public void testIDLEState() throws IOException {

        try {
            setupList();
            mTestName = "testIDLEState";
            int[] validMethods = {
                    METHOD_SET_DATA_SOURCE, METHOD_RELEASE, METHOD_RESET,
                    METHOD_GET_CURRENT_POSITION, METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH,
                    METHOD_GET_AUDIO_SESSION_ID, METHOD_SET_AUDIO_SESSION_ID, METHOD_SET_DISPLAY,
                    METHOD_SET_ON_BUFFERING_UPDATE_LISTENER, METHOD_SET_ON_COMPLETION_LISTENER,
                    METHOD_SET_ON_ERROR_LISTENER, METHOD_SET_ON_PREPARED_LISTENER,
                    METHOD_SET_ON_SEEK_COMPLETE_LISTENER, METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE,
                    METHOD_GET_STATE, METHOD_SET_DATA_SOURCE_OFFSET_LENGTH,
                    METHOD_SET_DATA_SOURCE_CONTEXT_URI, METHOD_SET_DATA_SOURCE_FD,
                    METHOD_SET_DATA_SOURCE_FD_OFFSET_LENGTH, METHOD_GET_DURATION,
                    METHOD_PAUSE, METHOD_SEEK_TO, METHOD_STOP, METHOD_PLAY,
                    METHOD_GET_MEDIA_METADATA, METHOD_SET_SPEED, METHOD_SET_VIDEO_SCALING_MODE
            };
            testValidMethods(validMethods, STATE_IDLE);
            testInvalidMethods(STATE_IDLE);
        } finally {
            shutDown();
        }
    }

    public void testINITIALIZEDState() throws IOException {
        try {
            setupList();
            mTestName = "testINITIALIZEDState";
            int[] validMethods = {
                    METHOD_PREPARE, METHOD_PREPARE_ASYNC, METHOD_RELEASE, METHOD_RESET,
                    METHOD_GET_CURRENT_POSITION, METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH,
                    METHOD_GET_AUDIO_SESSION_ID, METHOD_SET_DISPLAY, METHOD_SET_VIDEO_SCALING_MODE,
                    METHOD_SET_ON_BUFFERING_UPDATE_LISTENER, METHOD_SET_ON_COMPLETION_LISTENER,
                    METHOD_SET_ON_ERROR_LISTENER, METHOD_SET_ON_PREPARED_LISTENER,
                    METHOD_SET_ON_SEEK_COMPLETE_LISTENER, METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE,
                    METHOD_GET_STATE, METHOD_GET_DURATION, METHOD_PAUSE, METHOD_SEEK_TO,
                    METHOD_STOP, METHOD_PLAY, METHOD_GET_MEDIA_METADATA, METHOD_SET_SPEED,
                    METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_INITIALIZED);
            testInvalidMethods(STATE_INITIALIZED);
        } finally {
            shutDown();
        }
    }

    public void testPREPAREDState() throws IOException {
        try {
            setupList();
            mTestName = "testPREPAREDState";
            int[] validMethods = {
                    METHOD_PLAY, METHOD_SEEK_TO, METHOD_STOP, METHOD_RELEASE, METHOD_RESET,
                    METHOD_GET_CURRENT_POSITION, METHOD_GET_DURATION, METHOD_GET_VIDEO_HEIGHT,
                    METHOD_GET_VIDEO_WIDTH, METHOD_GET_AUDIO_SESSION_ID, METHOD_SET_DISPLAY,
                    METHOD_SET_VIDEO_SCALING_MODE, METHOD_SET_ON_BUFFERING_UPDATE_LISTENER,
                    METHOD_SET_ON_COMPLETION_LISTENER, METHOD_SET_ON_ERROR_LISTENER,
                    METHOD_SET_ON_PREPARED_LISTENER, METHOD_SET_ON_SEEK_COMPLETE_LISTENER,
                    METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE, METHOD_GET_TRACK_INFO,
                    METHOD_SELECT_TRACK, METHOD_DESELECT_TRACK, METHOD_GET_STATE, METHOD_SET_SPEED,
                    METHOD_GET_STATISTICS, METHOD_PAUSE, METHOD_GET_MEDIA_METADATA,
                    METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_PREPARED);
            testInvalidMethods(STATE_PREPARED);
        } finally {
            shutDown();
        }
    }

    public void testPLAYINGState() throws IOException {
        try {
            setupList();
            mTestName = "testPLAYINGState";
            int[] validMethods = {
                    METHOD_PAUSE, METHOD_SEEK_TO, METHOD_PLAY, METHOD_STOP, METHOD_RELEASE,
                    METHOD_RESET, METHOD_GET_CURRENT_POSITION, METHOD_GET_DURATION,
                    METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH, METHOD_GET_AUDIO_SESSION_ID,
                    METHOD_SET_DISPLAY, METHOD_SET_VIDEO_SCALING_MODE,
                    METHOD_SET_ON_BUFFERING_UPDATE_LISTENER, METHOD_SET_ON_COMPLETION_LISTENER,
                    METHOD_SET_ON_ERROR_LISTENER, METHOD_SET_ON_PREPARED_LISTENER,
                    METHOD_SET_ON_SEEK_COMPLETE_LISTENER, METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE,
                    METHOD_GET_TRACK_INFO, METHOD_SELECT_TRACK, METHOD_DESELECT_TRACK,
                    METHOD_GET_STATE, METHOD_SET_SPEED, METHOD_GET_STATISTICS,
                    METHOD_GET_MEDIA_METADATA, METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_PLAYING);
            testInvalidMethods(STATE_PLAYING);
        } finally {
            shutDown();
        }
    }

    public void testPAUSEDState() throws IOException {
        try {
            setupList();
            mTestName = "testPAUSEDState";
            int[] validMethods = {
                    METHOD_PAUSE, METHOD_PLAY, METHOD_SEEK_TO, METHOD_STOP, METHOD_RELEASE,
                    METHOD_RESET, METHOD_GET_CURRENT_POSITION, METHOD_GET_DURATION,
                    METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH, METHOD_GET_AUDIO_SESSION_ID,
                    METHOD_SET_DISPLAY, METHOD_SET_VIDEO_SCALING_MODE,
                    METHOD_SET_ON_BUFFERING_UPDATE_LISTENER, METHOD_SET_ON_COMPLETION_LISTENER,
                    METHOD_SET_ON_ERROR_LISTENER, METHOD_SET_ON_PREPARED_LISTENER,
                    METHOD_SET_ON_SEEK_COMPLETE_LISTENER, METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE,
                    METHOD_GET_TRACK_INFO, METHOD_SELECT_TRACK, METHOD_DESELECT_TRACK,
                    METHOD_GET_STATE, METHOD_SET_SPEED, METHOD_GET_STATISTICS,
                    METHOD_GET_MEDIA_METADATA, METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_PAUSED);
            testInvalidMethods(STATE_PAUSED);
        } finally {
            shutDown();
        }
    }

    public void testCOMPLETEDState() throws IOException {
        try {
            setupList();
            mTestName = "testCOMPLETEDState";
            int[] validMethods = {
                    METHOD_PLAY, METHOD_SEEK_TO, METHOD_STOP, METHOD_RELEASE, METHOD_RESET,
                    METHOD_GET_CURRENT_POSITION, METHOD_GET_DURATION, METHOD_GET_VIDEO_HEIGHT,
                    METHOD_GET_VIDEO_WIDTH, METHOD_GET_AUDIO_SESSION_ID, METHOD_SET_DISPLAY,
                    METHOD_SET_VIDEO_SCALING_MODE, METHOD_SET_ON_BUFFERING_UPDATE_LISTENER,
                    METHOD_SET_ON_COMPLETION_LISTENER, METHOD_SET_ON_ERROR_LISTENER,
                    METHOD_SET_ON_PREPARED_LISTENER, METHOD_SET_ON_SEEK_COMPLETE_LISTENER,
                    METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE, METHOD_GET_TRACK_INFO,
                    METHOD_SELECT_TRACK, METHOD_DESELECT_TRACK, METHOD_GET_STATE, METHOD_SET_SPEED,
                    METHOD_GET_STATISTICS, METHOD_PAUSE, METHOD_GET_MEDIA_METADATA,
                    METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_COMPLETED);
            testInvalidMethods(STATE_COMPLETED);
        } finally {
            shutDown();
        }
    }

    public void testENDState() throws IOException {
        try {
            setupList();
            mTestName = "testENDState";
            int[] validMethods = {
                    METHOD_RELEASE, METHOD_SET_ON_BUFFERING_UPDATE_LISTENER,
                    METHOD_SET_ON_COMPLETION_LISTENER, METHOD_SET_ON_ERROR_LISTENER,
                    METHOD_SET_ON_PREPARED_LISTENER, METHOD_SET_ON_SEEK_COMPLETE_LISTENER,
                    METHOD_SET_WAKE_MODE, METHOD_GET_STATE, METHOD_SET_VOLUME,
                    METHOD_SET_VIDEO_SCALING_MODE
            };
            testValidMethods(validMethods, STATE_END);
            testInvalidMethods(STATE_END);
        } finally {
            shutDown();
        }
    }

    public void testERRORState() throws IOException {
        try {
            setupList();
            mTestName = "testERRORState";
            int[] validMethods = {
                    METHOD_RELEASE, METHOD_SET_ON_BUFFERING_UPDATE_LISTENER,
                    METHOD_SET_ON_COMPLETION_LISTENER, METHOD_SET_ON_ERROR_LISTENER,
                    METHOD_SET_ON_PREPARED_LISTENER, METHOD_SET_ON_SEEK_COMPLETE_LISTENER,
                    METHOD_SET_WAKE_MODE, METHOD_GET_STATE, METHOD_RESET,
                    METHOD_GET_AUDIO_SESSION_ID, METHOD_GET_CURRENT_POSITION,
                    METHOD_GET_DURATION, METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH,
                    METHOD_PAUSE, METHOD_SEEK_TO, METHOD_SET_DATA_SOURCE,
                    METHOD_SET_DISPLAY, METHOD_STOP, METHOD_PLAY, METHOD_GET_MEDIA_METADATA,
                    METHOD_SET_VOLUME, METHOD_SET_SPEED, METHOD_SET_DATA_SOURCE_OFFSET_LENGTH,
                    METHOD_SET_DATA_SOURCE_CONTEXT_URI, METHOD_SET_DATA_SOURCE_FD,
                    METHOD_SET_DATA_SOURCE_FD_OFFSET_LENGTH, METHOD_SET_VIDEO_SCALING_MODE,
                    METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_ERROR);
            testInvalidMethods(STATE_ERROR);
        } finally {
            shutDown();
        }
    }

    public void testPREPARINGState() throws IOException {
        try {
            setupList();
            mTestName = "testPREPARINGState";
            int[] validMethods = {
                    METHOD_RELEASE, METHOD_RESET, METHOD_GET_CURRENT_POSITION,
                    METHOD_GET_VIDEO_HEIGHT, METHOD_GET_VIDEO_WIDTH, METHOD_GET_AUDIO_SESSION_ID,
                    METHOD_SET_DISPLAY, METHOD_SET_VIDEO_SCALING_MODE,
                    METHOD_SET_ON_BUFFERING_UPDATE_LISTENER, METHOD_SET_ON_COMPLETION_LISTENER,
                    METHOD_SET_ON_ERROR_LISTENER, METHOD_SET_ON_PREPARED_LISTENER,
                    METHOD_SET_ON_SEEK_COMPLETE_LISTENER, METHOD_SET_VOLUME, METHOD_SET_WAKE_MODE,
                    METHOD_GET_STATE, METHOD_GET_DURATION, METHOD_GET_MEDIA_METADATA, METHOD_PLAY,
                    METHOD_SEEK_TO, METHOD_PAUSE, METHOD_STOP, METHOD_SET_SPEED,
                    METHOD_SET_AUDIO_SESSION_ID
            };
            testValidMethods(validMethods, STATE_PREPARING);
            testInvalidMethods(STATE_PREPARING);
        } finally {
            shutDown();
        }
    }

    private void testValidMethods(int[] validMethods, int fromState) throws IOException {
        for (int i = 0; i < validMethods.length; i++) {
            try {
                testMethod(validMethods[i], fromState);
            } catch (IllegalStateException e) {
                fail("Recieved faulty IllegalStateException from STATE: "
                        + getString(fromState) + " by method: " + getString(validMethods[i]));
            }
        }
    }

    private void testMethod(int methodName, int fromState) throws IOException,
            IllegalStateException {
        mCallback = false;
        switch (fromState) {
            case STATE_IDLE: {
                initMediaPlayer();
                break;
            }
            case STATE_INITIALIZED: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                break;
            }
            case STATE_PREPARED: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                assertTrue("Prepare failed", mMediaPlayer.prepare());
                break;
            }
            case STATE_PLAYING: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                assertTrue("Prepare failed", mMediaPlayer.prepare());
                mMediaPlayer.play();
                break;
            }
            case STATE_PAUSED: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                assertTrue("Prepare failed", mMediaPlayer.prepare());
                mMediaPlayer.play();
                mMediaPlayer.pause();
                break;
            }
            case STATE_COMPLETED: {
                initMediaPlayer();
                mMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer arg0) {
                        mCallback = true;
                    }
                });
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                assertTrue("Prepare failed", mMediaPlayer.prepare());
                mMediaPlayer.seekTo(mMediaPlayer.getDuration());
                mMediaPlayer.play();
                int timeWaited = 0;
                while (!mCallback) {
                    SystemClock.sleep(50);
                    timeWaited += 50;
                    if (timeWaited > 20 * 1000) { // Time-out after 20 seconds
                        fail("Timed out waiting for onCompletion");
                    }
                }
                break;
            }
            case STATE_END: {
                initMediaPlayer();
                mMediaPlayer.release();
                break;
            }
            case STATE_ERROR: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_INVALID).getContentUri());
                assertTrue("Prepare did not fail", !mMediaPlayer.prepare());
                assertEquals("Did not go to ERROR state after calling prepare with invalid file",
                        MediaPlayer.State.ERROR, mMediaPlayer.getState());
                break;
            }
            case STATE_PREPARING: {
                initMediaPlayer();
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                mMediaPlayer.prepareAsync();
                assertEquals("MediaPlayer not in PREPARING state",
                        MediaPlayer.State.PREPARING, mMediaPlayer.getState());
                break;
            }
            default:
                fail("No such state");
        }
        if (LOGS_ENABLED)
            Utils.logd(TAG, mTestName, "Calling: " + getString(methodName));
        mMethodName = getString(methodName);
        switch (methodName) {
            case METHOD_SET_DATA_SOURCE:
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                break;
            case METHOD_RELEASE:
                mMediaPlayer.release();
                break;
            case METHOD_RESET:
                mMediaPlayer.reset();
                break;
            case METHOD_PREPARE:
                mMediaPlayer.prepare();
                break;
            case METHOD_PREPARE_ASYNC:
                mMediaPlayer.prepareAsync();
                break;
            case METHOD_PLAY:
                mMediaPlayer.play();
                break;
            case METHOD_SEEK_TO:
                mMediaPlayer.seekTo(0);
                break;
            case METHOD_PAUSE:
                mMediaPlayer.pause();
                break;
            case METHOD_STOP:
                mMediaPlayer.stop();
                break;
            case METHOD_GET_CURRENT_POSITION:
                mMediaPlayer.getCurrentPosition();
                break;
            case METHOD_GET_DURATION:
                mMediaPlayer.getDuration();
                break;
            case METHOD_GET_VIDEO_HEIGHT:
                mMediaPlayer.getVideoHeight();
                break;
            case METHOD_GET_VIDEO_WIDTH:
                mMediaPlayer.getVideoWidth();
                break;
            case METHOD_GET_AUDIO_SESSION_ID:
                mMediaPlayer.getAudioSessionId();
                break;
            case METHOD_SET_AUDIO_SESSION_ID:
                mMediaPlayer.setAudioSessionId(0);
                break;
            case METHOD_SET_DISPLAY:
                mMediaPlayer.setDisplay(null);
                break;
            case METHOD_SET_VIDEO_SCALING_MODE:
                mMediaPlayer.setVideoScalingMode(
                        MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                break;
            case METHOD_SET_ON_BUFFERING_UPDATE_LISTENER:
                mMediaPlayer.setOnBufferingUpdateListener(null);
                break;
            case METHOD_SET_ON_COMPLETION_LISTENER:
                mMediaPlayer.setOnCompletionListener(null);
                break;
            case METHOD_SET_ON_ERROR_LISTENER:
                mMediaPlayer.setOnErrorListener(null);
                break;
            case METHOD_SET_ON_PREPARED_LISTENER:
                mMediaPlayer.setOnPreparedListener(null);
                break;
            case METHOD_SET_ON_SEEK_COMPLETE_LISTENER:
                mMediaPlayer.setOnSeekCompleteListener(null);
                break;
            case METHOD_SET_VOLUME:
                mMediaPlayer.setVolume(0, 0);
                break;
            case METHOD_SET_WAKE_MODE:
                mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
                break;
            case METHOD_GET_TRACK_INFO:
                mMediaPlayer.getTrackInfo();
                break;
            case METHOD_SELECT_TRACK:
                mMediaPlayer.selectTrack(0);
                break;
            case METHOD_DESELECT_TRACK:
                mMediaPlayer.deselectTrack(0);
                break;
            case METHOD_GET_STATE:
                mMediaPlayer.getState();
                break;
            case METHOD_SET_SPEED:
                mMediaPlayer.setSpeed(2);
                break;
            case METHOD_GET_STATISTICS:
                mMediaPlayer.getStatistics();
                break;
            case METHOD_SET_DATA_SOURCE_OFFSET_LENGTH:
                mMediaPlayer.setDataSource(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri(), 0, Long.MAX_VALUE);
                break;
            case METHOD_SET_DATA_SOURCE_CONTEXT_URI:
                mMediaPlayer.setDataSource(mContext, Uri.parse(mTestContentProvider
                        .getTestItemById(TestContent.ID_TYPE_MEDIASTORE).getContentUri()));
                break;
            case METHOD_SET_DATA_SOURCE_FD:
                File fileFD = new File(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL).getContentUri());
                FileInputStream fisFD = new FileInputStream(fileFD);
                mMediaPlayer.setDataSource(fisFD.getFD());
                try {
                    fisFD.close();
                } catch (IOException e) {
                } // Ignoring
                break;
            case METHOD_SET_DATA_SOURCE_FD_OFFSET_LENGTH:
                File fileFDOffset = new File(mTestContentProvider.getTestItemById(
                        TestContent.ID_TYPE_LOCAL_WITH_OFFSET).getContentUri());
                FileInputStream fisFDOffset = new FileInputStream(fileFDOffset);
                mMediaPlayer.setDataSource(fisFDOffset.getFD(), mTestContentProvider
                        .getTestItemById(
                                TestContent.ID_TYPE_LOCAL_WITH_OFFSET).getOffset(),
                        mTestContentProvider.getTestItemById(
                                TestContent.ID_TYPE_LOCAL_WITH_OFFSET).getLength());
                try {
                    fisFDOffset.close();
                } catch (IOException e) {
                } // Ignoring
                break;
            case METHOD_GET_MEDIA_METADATA:
                mMediaPlayer.getMediaMetaData();
                break;
            default:
                fail("No such method");
        }
        mListOfMethods.remove(mListOfMethods.indexOf(methodName));
        shutDown();
    }

    private void testInvalidMethods(int fromState) throws IOException {
        int methodToTest;
        boolean continueTesting = true;
        if (LOGS_ENABLED)
            Utils.logd(TAG, mTestName, "Testing remaining, expecting IllegalStateExceptions");
        while (!mListOfMethods.isEmpty() && continueTesting) {
            methodToTest = mListOfMethods.get(0);
            try {
                testMethod(methodToTest, fromState);
                fail("Method did not throw IllegalStateException, from state: "
                        + getString(fromState) + " method: " + getString(methodToTest));
            } catch (IllegalStateException e) {
                if (LOGS_ENABLED) Utils.logd(TAG, mTestName, "catching IllegalStateException");
                continueTesting = true;
                mListOfMethods.remove(mListOfMethods.indexOf(methodToTest));
                shutDown();
            }
        }
        assertTrue("List of methods not empty, remaining: " + mListOfMethods.size(),
                mListOfMethods.isEmpty());
    }

    private void setupList() {
        mListOfMethods = new ArrayList<Integer>();
        for (int i = 1; i <= NUMBER_OF_METHODS; i++) {
            mListOfMethods.add(i);
        }
    }

    protected void initMediaPlayer() {
        /*
         * MediaPlayer is created on new thread since callbacks are executed on
         * creating thread. If the media player is created on the same thread as
         * running the test case, the callbacks will not be executed until the
         * test method has been completed.
         */
        setHandlerForUncaughtExceptions();
        final String testName = "initMediaPlayer";
        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread("testThread") {
            @Override
            public void run() {
                Looper.prepare();
                sLooper = Looper.myLooper();
                mMediaPlayer = new MediaPlayer();
                latch.countDown();
                Looper.loop();
            }
        };
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Utils.loge(TAG, testName, "Could not create MediaPlayer");
        }
    }

    protected void shutDown() {
        final String testName = "shutDown";
        try {
            if (mMediaPlayer != null && mMediaPlayer.getState() != MediaPlayer.State.END) {
                mMediaPlayer.release();
            }
            mMediaPlayer = null;
            if (sLooper != null) {
                sLooper.quit();
            }
        } catch (Exception e) {
            Utils.loge(TAG, testName, "Could not shutdown MediaPlayer");
        }
        assertTrue("UncaughtException: " + mUncaughtExceptionMessage
                + " when calling method: " + mMethodName, mNoUncaughtException);
    }

    private void setHandlerForUncaughtExceptions() {
        mNoUncaughtException = true;
        Thread.UncaughtExceptionHandler handler = new
                Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable ex) {
                        mNoUncaughtException = false;
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        ex.printStackTrace(pw);
                        mUncaughtExceptionMessage += sw.toString() + " ";
                        pw.close();
                    }
                };
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }
}
