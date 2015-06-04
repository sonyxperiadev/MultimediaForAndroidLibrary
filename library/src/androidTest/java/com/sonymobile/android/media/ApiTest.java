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

package com.sonymobile.android.media;

import static com.sonymobile.android.media.Configuration.ALLOWED_TIME_DISCREPANCY_MS;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

import com.sonymobile.android.media.MediaInfo;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnBufferingUpdateListener;
import com.sonymobile.android.media.MediaPlayer.OnCompletionListener;
import com.sonymobile.android.media.MediaPlayer.OnInfoListener;
import com.sonymobile.android.media.MediaPlayer.OnPreparedListener;
import com.sonymobile.android.media.MediaPlayer.OnRepresentationChangedListener;
import com.sonymobile.android.media.MediaPlayer.OnSeekCompleteListener;
import com.sonymobile.android.media.MediaPlayer.OnSubtitleDataListener;
import com.sonymobile.android.media.MediaPlayer.OnVideoSizeChangedListener;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.MetaDataParser;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;

public class ApiTest {

    private static final String TAG = "ApiTest";

    private static MediaPlayer sMediaPlayer;

    private static Looper sLooper = null;

    private static boolean sBufferEnd = false;

    private static boolean sBufferStart = false;

    private static boolean sVideoRenderingStart = false;

    private static int sCallbackCounter;

    private static boolean sPrepareFinished;

    private static boolean sErrorSignalled;

    private static boolean sCompleted;

    private static int sCompletedCounter;

    private static long sBufferingUpdateReceivedAtTime;

    private static boolean sVideoSizeChanged;

    private static boolean sOnBufferUpdated;

    private static int sPercentageBuffered;

    private static boolean sNoUncaughtException;

    private static String sUncaughtExceptionMessage;

    private static int sCallbackValue;

    private static int sVideoWidth;

    private static int sVideoHeight;

    public static void createMediaPlayer() {

        MediaPlayer mp1, mp2;

        final MediaPlayer mp3;
        MediaPlayer mp4;
        setHandlerForUncaughtExceptions();

        mp1 = new MediaPlayer();
        mp2 = new MediaPlayer();
        mp3 = new MediaPlayer();
        mp4 = mp2;

        assertEquals("State not IDLE", mp1.getState(), MediaPlayer.State.IDLE);
        assertEquals("State not IDLE", mp2.getState(), MediaPlayer.State.IDLE);
        assertEquals("State not IDLE", mp3.getState(), MediaPlayer.State.IDLE);
        assertEquals("State not IDLE", mp4.getState(), MediaPlayer.State.IDLE);

        mp1.release();
        mp2.release();
        mp3.release();
        mp4.release();

        assertEquals("State not END", mp1.getState(), MediaPlayer.State.END);
        assertEquals("State not END", mp2.getState(), MediaPlayer.State.END);
        assertEquals("State not END", mp3.getState(), MediaPlayer.State.END);
        assertEquals("State not END", mp4.getState(), MediaPlayer.State.END);
        assertTrue("UncaughtException: " + sUncaughtExceptionMessage, sNoUncaughtException);

    }

    public static void createMediaPlayerOnThread() {
        try {
            setHandlerForUncaughtExceptions();
            final CountDownLatch latch = new CountDownLatch(1);
            Thread t = new Thread("testThread") {
                @Override
                public void run() {
                    sMediaPlayer = new MediaPlayer();
                    latch.countDown();
                }
            };
            t.start();
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Could not create MediaPlayer");
            }
        } finally {
            shutDown();
        }
    }

    public static void getState(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            assertEquals("Wrong state after setup", MediaPlayer.State.IDLE,
                    sMediaPlayer.getState());
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertEquals("Wrong state after setDataSource()", MediaPlayer.State.INITIALIZED,
                    sMediaPlayer.getState());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertEquals("Wrong state after prepare()", MediaPlayer.State.PREPARED,
                    sMediaPlayer.getState());
            sMediaPlayer.play();
            assertEquals("Wrong state after play()", sMediaPlayer.getState(),
                    MediaPlayer.State.PLAYING);
            sMediaPlayer.pause();
            assertEquals("Wrong state after pause()", MediaPlayer.State.PAUSED,
                    sMediaPlayer.getState());
            sMediaPlayer.stop();
            assertEquals("Wrong state after stop()", MediaPlayer.State.INITIALIZED,
                    sMediaPlayer.getState());
            sMediaPlayer.release();
            assertEquals("Wrong state after release()", MediaPlayer.State.END,
                    sMediaPlayer.getState());
        } finally {
            shutDown();
        }

        // TODO: test COMPLETED, PREPARING and ERROR states aswell.
    }

    public static void setDisplay(TestContent tc, SurfaceHolder sh, SurfaceHolder sh2)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);

        try {
            initMediaPlayer();

            sErrorSignalled = false;
            sMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    sErrorSignalled = true;
                    return true;
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            sMediaPlayer.play();
            SystemClock.sleep(5000); // Play for ~5 sec

            assertFalse("Error signalled after setDisplay(1)", sErrorSignalled);

            sMediaPlayer.setDisplay(null);
            SystemClock.sleep(5000); // Play for ~5 sec

            assertFalse("Error signalled after setDisplay(null)", sErrorSignalled);

            sMediaPlayer.setDisplay(sh2);
            SystemClock.sleep(tc.getMaxIFrameInterval() + 5000);

            assertFalse("Error signalled after setDisplay(2)", sErrorSignalled);
        } finally {
            shutDown();
        }
    }

    public static void setVideoScalingMode(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setVideoScalingMode(
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
        } finally {
            shutDown();
        }
    }

    public static void setDataSource(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertEquals("State not INITIALIZED",
                    sMediaPlayer.getState(), MediaPlayer.State.INITIALIZED);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.reset();
            sMediaPlayer.setDataSource("");
        } finally {
            shutDown();
        }
    }

    public static void setDataSource(TestContent tc, long offset, long length) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri(), offset, length);
            assertEquals("State not INITIALIZED",
                    MediaPlayer.State.INITIALIZED, sMediaPlayer.getState());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
        } finally {
            shutDown();
        }
    }

    public static void setDataSource(Context context, String uri) throws IOException {
        assertNotNull("No context provided", context);
        assertNotNull("No content uri", uri);

        try {
            initMediaPlayer();

            sMediaPlayer.setDataSource(context, Uri.parse(uri));
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
        } finally {
            shutDown();
        }
    }

    public static void setDataSource(FileDescriptor fd) throws IOException {
        assertNotNull("No file descriptor provided", fd);

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(fd);
            assertEquals("State not INITIALIZED",
                    sMediaPlayer.getState(), MediaPlayer.State.INITIALIZED);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
        } finally {
            shutDown();
        }
    }

    public static void setDataSourceFdWithOffset(TestContent tc, FileDescriptor fd)
            throws IOException {
        assertNotNull("No test content", tc);
        assertTrue("Offset <= 0", tc.getOffset() > 0);
        assertTrue("Length <= 0", tc.getLength() > 0);
        assertNotNull("No file descriptor", fd);

        try {
            initMediaPlayer();
            long offset = tc.getOffset();
            long length = tc.getLength();
            sMediaPlayer.setDataSource(fd, offset, length);
            assertEquals("State not INITIALIZED after setDataSource",
                    sMediaPlayer.getState(), MediaPlayer.State.INITIALIZED);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();

        } finally {
            shutDown();
        }
    }

    public static void prepare(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uril", tc.getContentUri());

        try {
            sPrepareFinished = false;
            initMediaPlayer();
            sMediaPlayer.setOnPreparedListener(new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer arg0) {
                    sPrepareFinished = true;
                }
            });
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertEquals("State not initialized", sMediaPlayer.getState(),
                    MediaPlayer.State.INITIALIZED);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertEquals("State not PREPARED", sMediaPlayer.getState(), MediaPlayer.State.PREPARED);
            int timeWaited = 0;
            while (!sPrepareFinished) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 5 * 1000) {
                    fail("prepare did not notify OnPreparedListener");
                }
            }
        } finally {
            shutDown();
        }
    }

    public static void play(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            sMediaPlayer.play();
            SystemClock.sleep(5000); // Play for ~5 sec
            assertEquals("State not PLAYING", MediaPlayer.State.PLAYING, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void pause(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.pause();
            assertEquals("State not PAUSED", sMediaPlayer.getState(), MediaPlayer.State.PAUSED);
            sMediaPlayer.play();
            assertEquals("State not PLAYING", sMediaPlayer.getState(), MediaPlayer.State.PLAYING);
            sMediaPlayer.pause();
            sMediaPlayer.seekTo(sMediaPlayer.getDuration() / 2);
            assertEquals("State not PAUSED", sMediaPlayer.getState(), MediaPlayer.State.PAUSED);
            sMediaPlayer.stop();
            assertEquals("State not INITIALIZED",
                    sMediaPlayer.getState(), MediaPlayer.State.INITIALIZED);
        } finally {
            shutDown();
        }
    }

    public static void seekTo(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Testcontent must have a valid duration", tc.getDuration() > 0);
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);

        try {
            sCompleted = false;
            sCompletedCounter = 0;

            initMediaPlayer();

            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {

                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    sCompletedCounter++;
                    sCompleted = true;
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            int seekTime = sMediaPlayer.getDuration() / 4 * 3;
            sMediaPlayer.seekTo(seekTime); // Test seekTo in prepared state

            int timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onSeekCompleted");
                }
            }

            int current = sMediaPlayer.getCurrentPosition();
            assertEquals("Did not return seeked position after seek in prepared state", seekTime,
                    current);

            sCompleted = false;
            sMediaPlayer.play();

            int pos;
            do {
                SystemClock.sleep(50);
                pos = sMediaPlayer.getCurrentPosition();
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out during seek");
                }
            } while (pos == current);

            assertEquals("Wrong number of seekComplete callbacks sent", 1, sCompletedCounter);

            assertTrue("Seeked position differs from expected, current = " + pos +
                    " seekedTo = " + seekTime + " deltaAllowed = "
                    + tc.getMaxIFrameInterval(), pos > seekTime - tc.getMaxIFrameInterval()
                    && pos < seekTime);

            sCompleted = false;
            sMediaPlayer.seekTo(0); // Test seekTo in playing state

            timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onSeekCompleted");
                }
            }
            assertEquals("Wrong number of seekComplete callbacks sent", 2, sCompletedCounter);

            assertTrue("Wrong current position after seek",
                    sMediaPlayer.getCurrentPosition() < ALLOWED_TIME_DISCREPANCY_MS);

            pos = sMediaPlayer.getCurrentPosition();
            while (pos == 0) {
                SystemClock.sleep(50);
                pos = sMediaPlayer.getCurrentPosition();
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out during seek");
                }
            }

            assertTrue("Seek to 0 did not work", pos < ALLOWED_TIME_DISCREPANCY_MS);

            sMediaPlayer.pause();

            sCompleted = false;
            seekTime = sMediaPlayer.getDuration() / 4;
            sMediaPlayer.seekTo(seekTime);

            timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onSeekCompleted");
                }
            }
            assertEquals("Wrong number of seekComplete callbacks sent", 3, sCompletedCounter);

            timeout = 0;
            while ((seekTime - sMediaPlayer.getCurrentPosition()) > tc.getMaxIFrameInterval()) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 1000) {
                    fail("Timed out waiting for seek to reach seek point");
                }
            }

            assertEquals("Unexpected position after seek in paused state", seekTime,
                    sMediaPlayer.getCurrentPosition());
        } finally {
            shutDown();
        }
    }

    public static void getVideoWidth(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No valid width specified", tc.getWidth() > 0);

        sVideoWidth = 0;

        try {
            sVideoSizeChanged = false;

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener() {

                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    sVideoWidth = width;
                    sVideoSizeChanged = true;
                }
            });

            assertTrue("Prepare failed", sMediaPlayer.prepare());

            assertEquals("Video width not returned in prepared state", tc.getWidth(),
                    sMediaPlayer.getVideoWidth());

            sMediaPlayer.play();

            int timeout = 0;
            while (!sVideoSizeChanged) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onVideoSizeChanged");
                }
            }

            assertEquals("Incorrect video width in playing state", tc.getWidth(),
                    sMediaPlayer.getVideoWidth());

            assertEquals("Unexpected width in callback", tc.getWidth(), sVideoWidth);
        } finally {
            shutDown();
        }
    }

    public static void getVideoHeight(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No valid height specified", tc.getHeight() > 0);

        sVideoHeight = 0;

        try {
            sVideoSizeChanged = false;

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener() {

                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    sVideoHeight = height;
                    sVideoSizeChanged = true;
                }
            });

            assertTrue("Prepare failed", sMediaPlayer.prepare());

            assertEquals("Video height not returned in prepared state", tc.getHeight(),
                    sMediaPlayer.getVideoHeight());

            sMediaPlayer.play();

            int timeout = 0;
            while (!sVideoSizeChanged) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onVideoSizeChanged");
                }
            }

            assertEquals("Incorrect video height in playing state", tc.getHeight(),
                    sMediaPlayer.getVideoHeight());

            assertEquals("Unexpected height in callback", tc.getHeight(), sVideoHeight);
        } finally {
            shutDown();
        }
    }

    public static void release(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.release();
            // TODO: try to release from all the different states (PREPARING &
            // ERROR not tested)
            assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            shutDown();
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.release(); // INITIALIZED STATE
            assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            shutDown();

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.release(); // PREPARED STATE
            assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            shutDown();

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.release(); // PLAYING STATE
            assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            shutDown();

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.pause();
            sMediaPlayer.release(); // PAUSED STATE
            assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            shutDown();

            initMediaPlayer();
            sCompleted = false;
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0) {
                    sCompleted = true;
                }
            });
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.seekTo(sMediaPlayer.getDuration());
            sMediaPlayer.play();
            int timeWaited = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) { // Time-out after 20 seconds
                    fail("Timed out waiting for onSeekCompleted");
                }
            }
            if (sMediaPlayer.getState() != MediaPlayer.State.COMPLETED) {
                fail("Player not in COMPLETED state, please use testcontent with " +
                        "seekpoints closer to the end. Current testcontent: " + tc.getId());
            } else {
                sMediaPlayer.release(); // COMPLETED STATE
                assertEquals("State not END", sMediaPlayer.getState(), MediaPlayer.State.END);
            }
        } finally {
            shutDown();
        }
    }

    private static void waitForPrepareAsync() {
        int timeWaited = 0;
        while (sMediaPlayer.getState() != MediaPlayer.State.PREPARED) {
            SystemClock.sleep(100);
            timeWaited += 100;
            if (timeWaited > 20000) {
                fail("Timeout in waitForPrepareAsync");
            }
        }
    }

    public static void prepareAsync(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.prepareAsync();
            assertEquals("State not PREPARING", MediaPlayer.State.PREPARING,
                    sMediaPlayer.getState());
            waitForPrepareAsync();
            assertEquals("State not PREPARED", MediaPlayer.State.PREPARED, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void prepareInvalid(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            final Object lock = new Object();
            sPrepareFinished = false;

            initMediaPlayer();

            sMediaPlayer.setDataSource(tc.getContentUri());
            new Thread(new Runnable() {

                @Override
                public void run() {
                    sMediaPlayer.prepare();
                    synchronized (lock) {
                        sPrepareFinished = true;
                        lock.notifyAll();
                    }
                }
            }).start();

            synchronized (lock) {
                try {
                    lock.wait(20000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            assertTrue("Timed out waiting for prepare()", sPrepareFinished);

            assertEquals(
                    "MediaPlayer did not go to Error state after prepare with an invalid file",
                    sMediaPlayer.getState(), MediaPlayer.State.ERROR);
        } finally {
            shutDown();
        }
    }

    public static void getCurrentPosition(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Test content is too short (" + tc.getDuration() + ") " + tc.getContentUri(),
                tc.getDuration() >= 4000);

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertEquals("currentPosition not 0 in prepared state",
                    sMediaPlayer.getCurrentPosition(), 0);
            sMediaPlayer.play();
            int timeWaited = 0;
            while (sMediaPlayer.getCurrentPosition() == 0) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 5 * 1000) { // Time-out after 5 seconds
                    fail("Timed out waiting for MediaPlayer to start playing");
                }
            }
            SystemClock.sleep(2000); // play for approx 2s
            int current = sMediaPlayer.getCurrentPosition();
            assertTrue("Position after playing for 2 seconds is outside of valid delta",
                    current > 2000 - ALLOWED_TIME_DISCREPANCY_MS && current < 2000 +
                            ALLOWED_TIME_DISCREPANCY_MS);

            sCompleted = false;
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0) {
                    sCompleted = true;
                }
            });

            sMediaPlayer.seekTo(sMediaPlayer.getDuration());

            timeWaited = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) { // Time-out after 20 seconds
                    fail("Timed out waiting for onCompletion");
                }
            }

            int pos = sMediaPlayer.getCurrentPosition();
            for (int i = 0; i < 20; i++) {
                SystemClock.sleep(20);
                assertEquals("Time moving after completed",
                        pos, sMediaPlayer.getCurrentPosition());
            }
        } finally {
            shutDown();
        }
    }

    public static void getDuration(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No valid duration is provided", tc.getDuration() > 0);

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            int duration = tc.getDuration();
            int mpDuration = sMediaPlayer.getDuration();
            int delta = 999;
            if (duration + delta < mpDuration || duration - delta > mpDuration) {
                // Since we dont use milliseconds in the xmlfile the diff should
                // be max 999.
                fail("Difference greater than delta " + delta + ". Actual: " + mpDuration
                        + " Expected: " + duration);
            }
        } finally {
            shutDown();
        }
    }

    public static void reset(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.reset();
            // TODO: try to reset from all the different states (PREPARING &
            // ERROR not tested)
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.reset(); // INITIALIZED STATE
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);

            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.reset(); // PREPARED STATE
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);

            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.reset(); // PLAYING STATE
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);

            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.pause();
            sMediaPlayer.reset(); // PAUSED STATE
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);

            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.pause();
            sMediaPlayer.stop();
            sMediaPlayer.reset(); // STOPPED STATE
            assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);

            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sCompleted = false;
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer arg0) {
                    sCompleted = true;
                }
            });
            sMediaPlayer.seekTo(sMediaPlayer.getDuration() - 1);
            sMediaPlayer.play();
            int timeWaited = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) { // Time-out after 20 seconds
                    fail("Timed out waiting for onSeekCompleted");
                }
            }
            if (sMediaPlayer.getState() != MediaPlayer.State.COMPLETED) {
                fail("Player not in COMPLETED state, please use testcontent with " +
                        "seekpoints closer to the end. Current testcontent: " + tc.getId());
            } else {
                sMediaPlayer.reset(); // COMPLETED STATE
                assertEquals("State not IDLE", sMediaPlayer.getState(), MediaPlayer.State.IDLE);
            }
        } finally {
            shutDown();
        }
    }

    public static void getTrackInfo(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Width <= 0", tc.getWidth() > 0);
        assertTrue("Height <= 0", tc.getHeight() > 0);

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            TrackInfo[] ti = sMediaPlayer.getTrackInfo();
            assertTrue("TrackInfo length zero", ti.length != 0);
            boolean videoTrackFound = false;
            for (TrackInfo trackInfo : ti) {
                if (trackInfo.getTrackType() == TrackType.VIDEO) {
                    videoTrackFound = true;

                    TrackRepresentation[] tr = trackInfo.getRepresentations();
                    assertTrue("No video representation in video track", tr.length > 0);

                    assertEquals("Width not equal to expected width", tc.getWidth(),
                            ((VideoTrackRepresentation) tr[0]).getWidth());
                    assertEquals("Height not equal to expected height", tc.getHeight(),
                            ((VideoTrackRepresentation) tr[0]).getHeight());
                }
            }

            assertTrue("No video track found in test content", videoTrackFound);
        } finally {
            shutDown();
        }
    }

    public static void getStatistics(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sBufferEnd = false;

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setOnInfoListener(new OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {

                    if (what == MediaInfo.BUFFERING_END) {
                        sBufferEnd = true;
                    }
                    return true;
                }
            });
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            int sleepTime = 0;
            while (!sBufferEnd) {
                SystemClock.sleep(100); // wait for callback of BUFFERING_END to
                                        // get the stats
                sleepTime += 100;
                if (sleepTime > 20000) {
                    fail("Timeout in waiting for BUFFERING_END");
                }
            }
            Statistics stat = sMediaPlayer.getStatistics();
            assertNotNull("getStatistics() returned null obj", stat);
            assertTrue("getLinkSpeed() returned 0 linkspeed ", stat.getLinkSpeed() > 0);
            assertNotNull("getServerIP() returned null obj", stat.getServerIP());
            assertNotNull("getVideoUri() returned null obj", stat.getVideoUri());
        } finally {
            shutDown();
        }
    }

    public static void setSpeed(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Testcontent has too short duration", tc.getDuration() > 5000);

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.setSpeed(2.0f);
            sMediaPlayer.play();
            int timeOut = 0;
            while (sMediaPlayer.getCurrentPosition() == 0 && timeOut < 20000) {
                // wait for the playback to actually start
                SystemClock.sleep(25);
                timeOut += 25;
            }
            long startTime = System.currentTimeMillis();
            SystemClock.sleep(2000); // Play for 2s at 2x speed
            long endTime = System.currentTimeMillis();
            int pos = sMediaPlayer.getCurrentPosition();
            int diff = (int)((endTime - startTime) * 2);
            assertTrue(
                    "Position should be between " + (diff - ALLOWED_TIME_DISCREPANCY_MS) + " and" +
                            " " + (diff + ALLOWED_TIME_DISCREPANCY_MS) + ", pos = " + pos,
                    pos > (diff - ALLOWED_TIME_DISCREPANCY_MS) && pos < (diff +
                            ALLOWED_TIME_DISCREPANCY_MS));
            shutDown();

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.setSpeed(0.5f);
            sMediaPlayer.play();
            timeOut = 0;
            while (sMediaPlayer.getCurrentPosition() == 0 && timeOut < 20000) {
                SystemClock.sleep(25);
                timeOut += 25;
            }
            startTime = System.currentTimeMillis();
            SystemClock.sleep(4000); // Play for 4s at 0.5x speed
            endTime = System.currentTimeMillis();
            pos = sMediaPlayer.getCurrentPosition();
            diff = (int)((endTime - startTime) * 0.5);
            assertTrue(
                    "Position should be between " + (diff - ALLOWED_TIME_DISCREPANCY_MS) + " and" +
                            " " + (diff + ALLOWED_TIME_DISCREPANCY_MS) + ", pos = " + pos,
                    pos > (diff - ALLOWED_TIME_DISCREPANCY_MS) && pos < (diff +
                            ALLOWED_TIME_DISCREPANCY_MS));
            shutDown();

            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            try {
                sMediaPlayer.setSpeed(-2.0f);
                sMediaPlayer.play();
                fail("Should not be able to set and play an unsupported speed.");
            } catch (IllegalArgumentException e) {
                // expected for wrong parameters
            }
        } finally {
            shutDown();
        }
    }

    public static void setOnPreparedListener(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sPrepareFinished = false;
            sMediaPlayer.setDataSource(tc.getContentUri());

            OnPreparedListener ol = new OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer arg0) {
                    sPrepareFinished = true;
                }
            };
            sMediaPlayer.setOnPreparedListener(ol);
            sMediaPlayer.prepareAsync();
            int timeOut = 0;
            while (!sPrepareFinished) {
                SystemClock.sleep(100);
                timeOut += 100;
                if (timeOut > 20000) {
                    fail("Timeout when waiting for onPrepared");
                }
            }
            assertEquals("State not PREPARED",
                    sMediaPlayer.getState(), MediaPlayer.State.PREPARED);
            sMediaPlayer.play();
            assertEquals("State not PLAYING", sMediaPlayer.getState(), MediaPlayer.State.PLAYING);
        } finally {
            shutDown();
        }
    }

    public static void setOnInfoListener(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sBufferEnd = false;
            sBufferStart = false;
            sVideoRenderingStart = false;

            OnInfoListener oil = new OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer arg0, int what, int extra) {
                    if (what == MediaInfo.BUFFERING_START) {
                        sBufferStart = true;
                    } else if (what == MediaInfo.BUFFERING_END) {
                        assertTrue("BUFFERING_END callback before BUFFERING_START", sBufferStart);
                        sBufferEnd = true;
                    } else if (what == MediaInfo.VIDEO_RENDERING_START) {
                        sVideoRenderingStart = true;
                    }
                    return false;
                }
            };
            sMediaPlayer.setOnInfoListener(oil);
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare()); // Triggers
                                                                  // BUFFERING_START
            sMediaPlayer.play(); // Triggers BUFFERING_END
            int timeOut = 0;
            while (!sBufferEnd || !sBufferStart || !sVideoRenderingStart) {
                SystemClock.sleep(100);
                timeOut += 100;
                if (timeOut > 20000) {
                    break;
                }
            }
            assertTrue("Event MediaInfo.BUFFERING_START not recieved.", sBufferStart);
            assertTrue("Event MediaInfo.BUFFERING_DONE not recieved.", sBufferEnd);
            assertTrue("Event MediaInfo.VIDEO_RENDERING_START not recieved.", sVideoRenderingStart);
        } finally {
            shutDown();
        }
    }

    public static void setOnInfoListenerBackground(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sVideoRenderingStart = false;

            OnInfoListener oil = new OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer arg0, int what, int extra) {
                    if (what == MediaInfo.VIDEO_RENDERING_START) {
                        sVideoRenderingStart = true;
                    }
                    return false;
                }
            };
            sMediaPlayer.setOnInfoListener(oil);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();

            SystemClock.sleep(2000);
            assertFalse("Event VIDEO_RENDERING_START recieved for background playback.",
                    sVideoRenderingStart);
        } finally {
            shutDown();
        }
    }

    public static void setOnErrorListener(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sErrorSignalled = false;
            initMediaPlayer();
            sMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer arg0, int arg1, int extra) {
                    sErrorSignalled = true;
                    return true;
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.prepareAsync();
            int timeOut = 0;
            while (!sErrorSignalled) {
                SystemClock.sleep(100);
                timeOut += 100;
                if (timeOut > 10000) {
                    fail("Timeout when waiting for onError callback");
                    break;
                }
                if (sMediaPlayer.getState() == MediaPlayer.State.PREPARED) {
                    fail("State PREPARED. Use invalid testcontent.");
                }
            }
            assertEquals("MediaPlayer not in ERROR state",
                    MediaPlayer.State.ERROR, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void noSetErrorListener(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sCompleted = false;
            initMediaPlayer();
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    sCompleted = true;
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.prepareAsync();
            int timeOut = 0;
            while (!sCompleted) {
                SystemClock.sleep(100);
                timeOut += 100;
                if (timeOut > 10000) {
                    fail("Timeout waiting for onCompletion callback in error case");
                    break;
                }
                assertFalse("State should not be prepared",
                        sMediaPlayer.getState() == MediaPlayer.State.PREPARED);
            }
            assertEquals("MediaPlayer not in ERROR state",
                    MediaPlayer.State.ERROR, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void selectTrack(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            assertNotNull("Test content null", tc);
            assertNotNull("No content URI for testcontent", tc.getContentUri());
            // TODO: investigate exactly how selectTrack works and how this can
            // be tested.
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            TrackInfo[] tracks = sMediaPlayer.getTrackInfo();
            if (tracks.length < 0) {
                sMediaPlayer.selectTrack(0);
            }
            sMediaPlayer.play();
            Vector<Integer> content = new Vector<>();
            content.add(Integer.valueOf(0));
            content.add(Integer.valueOf(1));
            sMediaPlayer.selectTrack(0, content);
        } finally {
            shutDown();
        }
    }

    public static void deselectTrack(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            // TODO: investigate exactly how deselectTrack works and how this
            // can be tested.
            TrackInfo[] tracks = sMediaPlayer.getTrackInfo();
            if (tracks.length > 0) {
                sMediaPlayer.selectTrack(0);
                sMediaPlayer.deselectTrack(0);
            }
        } finally {
            shutDown();
        }
    }

    public static void setOnCompletionListener(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sCompleted = false;
            initMediaPlayer();
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    sCompleted = true;
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.prepareAsync();
            waitForPrepareAsync();
            sMediaPlayer.play();
            sMediaPlayer.seekTo(sMediaPlayer.getDuration());
            int timeOut = 0;
            while (!sCompleted) {
                SystemClock.sleep(100);
                timeOut += 100;
                if (timeOut > 20000) {
                    fail("Timeout waiting for OnCompletionListener");
                }
            }
            assertEquals("State not COMPLETED", MediaPlayer.State.COMPLETED,
                    sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void setOnSeekCompleteListener(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);

        try {
            final CountDownLatch signal = new CountDownLatch(1);
            initMediaPlayer();
            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    signal.countDown();
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            int duration = sMediaPlayer.getDuration();
            int timeout = 0;
            sMediaPlayer.play();
            sMediaPlayer.seekTo(duration / 2);
            try {
                signal.await(20 * 1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }// ignoring this
            assertEquals("onSeekComplete never received after seek", 0, signal.getCount());
            int pos = sMediaPlayer.getCurrentPosition();
            while (pos == duration / 2) {
                SystemClock.sleep(50);
                pos = sMediaPlayer.getCurrentPosition();
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out during seek");
                }
            }
            assertTrue(
                    "MediaPlayer getCurrentPosition"
                            + " not returning correct value after seek",
                    sMediaPlayer.getCurrentPosition() < duration / 2
                            && sMediaPlayer.getCurrentPosition() > (duration / 2)
                                    - tc.getMaxIFrameInterval());
        } finally {
            shutDown();
        }
    }

    public static void setOnSubtitleDataListener(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sCompleted = false;
            initMediaPlayer();
            sMediaPlayer.setOnSubtitleDataListener(new OnSubtitleDataListener() {
                @Override
                public void onSubtitleData(MediaPlayer arg0, SubtitleData sd) {
                    sCallbackValue = sd.getData().length;
                    sCompleted = true;
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sCompleted = false;
            sMediaPlayer.play();
            sMediaPlayer.selectTrack(tc.getSubtitleTrack());
            int timeWaited = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) {
                    fail("Timed out waiting for callback"); // Time-out after 20
                                                            // seconds
                }
            }
            assertEquals("Expected datalength" +
                    " not the same as returned datalength",
                    tc.getSubtitleDataLength(), sCallbackValue);

            sCompleted = false;
            sMediaPlayer.seekTo(0);
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) {
                    fail("Timed out waiting for callback"); // Time-out after 20
                                                            // seconds
                }
            }

        } finally {
            shutDown();
        }
    }

    public static void setAudioSessionId(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();
            sMediaPlayer.setAudioSessionId(5);
            assertEquals("Did not recieve the value that was set",
                    5, sMediaPlayer.getAudioSessionId());
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            while (sMediaPlayer.getCurrentPosition() == 0) {
                SystemClock.sleep(50);
            } // Waiting for audio track to be created
            assertEquals("Did not recieve the value that was set",
                    5, sMediaPlayer.getAudioSessionId());
        } finally {
            shutDown();
        }
    }

    public static void getAudioSessionId(final TestContent tc, SurfaceHolder sh,
            SurfaceHolder sh2, Context context)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer(context);
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            assertTrue("Audio Session Id should be > 0 after prepare",
                    sMediaPlayer.getAudioSessionId() > 0);

            sMediaPlayer.play();
            int timeToWait = 0;
            while (sMediaPlayer.getCurrentPosition() == 0) {
                SystemClock.sleep(100);
                if (timeToWait > 20 * 1000) { // Time-out after 20 seconds
                    fail("Timed out waiting for new AudioSessionId");
                }
                timeToWait += 100;
            }
            final int refVal = sMediaPlayer.getAudioSessionId();
            assertEquals("Returned value not the same as reference value", refVal,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.pause();
            assertEquals("Returned value not the same as reference value", refVal,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.stop();
            assertEquals("Returned value not the same as reference value", refVal,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.reset();
            assertEquals("Audiosession id is not the same after reset()", refVal,
                    sMediaPlayer.getAudioSessionId());
            shutDown();
            initMediaPlayer(context);
            sMediaPlayer.setDisplay(sh2);
            final int refVal2 = 4;
            sMediaPlayer.setAudioSessionId(refVal2);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertEquals("Returned value not the same as reference value", refVal2,
                    sMediaPlayer.getAudioSessionId());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertEquals("Returned value not the same as reference value", refVal2,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.play();
            timeToWait = 0;
            while (sMediaPlayer.getCurrentPosition() == 0) {
                SystemClock.sleep(100);
                timeToWait += 100;
                if (timeToWait > 20 * 1000) { // Timeout after 20 seconds
                    fail("Timemout waiting for audio track to be created");
                }
            }
            assertEquals("Returned value not the same as reference value", refVal2,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.pause();
            assertEquals("Returned value not the same as reference value", refVal2,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.stop();
            assertEquals("Returned value not the same as reference value", refVal2,
                    sMediaPlayer.getAudioSessionId());
            sMediaPlayer.reset();
            assertEquals("Audiosession id is not the same after reset()", refVal2,
                    sMediaPlayer.getAudioSessionId());
        } finally {
            shutDown();
        }
    }

    public static void setOnRepresentationChangedListener(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting MPEG-DASH content", TestContent.ID_TYPE_DASH, tc.getId());

        try {
            MockRepresentationSelector mockSelector = new MockRepresentationSelector();

            sCallbackCounter = 2;
            initMediaPlayer();
            sMediaPlayer.setOnRepresentationChangedListener(new OnRepresentationChangedListener() {
                @Override
                public void onRepresentationChanged(MediaPlayer mp, Statistics stats) {
                    sCallbackCounter--;
                }
            });
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setRepresentationSelector(mockSelector);
            mockSelector.setVideoRepresentation(0);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertTrue("Not enough video representations to test", multipleRepresentations());
            mockSelector.setVideoRepresentation(1);
            int timeToWait = 0;
            while (sCallbackCounter > 1) {
                SystemClock.sleep(50);
                timeToWait += 50;
                if (timeToWait > 20 * 1000) { // Waiting on callback for 20
                                              // seconds
                    fail("Timed out waiting for callback");
                }
            }
            mockSelector.setVideoRepresentation(0);
            timeToWait = 0;
            while (sCallbackCounter > 0) {
                SystemClock.sleep(50);
                timeToWait += 50;
                if (timeToWait > 20 * 1000) { // Waiting on callback for 20
                                              // seconds
                    break;
                }
            }
            assertEquals("Not enough callbacks occurred", 0, sCallbackCounter);
        } finally {
            shutDown();
        }
    }

    public static void setOnBufferingUpdateListener(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sOnBufferUpdated = false;
            initMediaPlayer();
            sMediaPlayer.setOnBufferingUpdateListener(new OnBufferingUpdateListener() {

                @Override
                public void onBufferingUpdate(MediaPlayer arg0, int arg1) {
                    sOnBufferUpdated = true;
                    sPercentageBuffered = arg1;
                }
            });
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();

            int timeOut = 0;
            while (!sOnBufferUpdated && timeOut < 30000) {
                SystemClock.sleep(50);
                timeOut += 50;
            }
            assertTrue("Timed out waiting for buffer updates", timeOut < 30000);
            assertTrue("Too large buffering percentage value " + sPercentageBuffered,
                    sPercentageBuffered <= 100);
            assertTrue("Negative buffering percentage value " + sPercentageBuffered,
                    sPercentageBuffered >= 0);

        } finally {
            shutDown();
        }
    }

    private static boolean multipleRepresentations() {
        TrackInfo[] tracks = sMediaPlayer.getTrackInfo();
        for (TrackInfo trackInfo : tracks) {
            if (trackInfo.getTrackType() == TrackType.VIDEO
                    && trackInfo.getRepresentations().length > 1) {
                return true;
            }
        }
        return false;
    }

    public static void getTrackCount(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            assertEquals("TrackCount differs from expected.",
                    tc.getTrackCount(), parser.getTrackCount());
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void getMetaData(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertNotNull("No mime type", tc.getMimeType());
        assertNotNull("No content type", tc.getContentType());
        assertTrue("Duration equal or less than zero", tc.getDuration() > 0);

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            MetaData metaData = parser.getMetaData();
            assertTrue("KEY_MIME_TYPE not found, should always exist",
                    metaData.containsKey(MetaData.KEY_MIME_TYPE));
            String mime = metaData.getString(MetaData.KEY_MIME_TYPE);
            assertEquals("Mimetype not equal to expected", tc.getMimeType(), mime);

            assertTrue("KEY_FILE_DURATION not found, should always exist",
                    metaData.containsKey(MetaData.KEY_DURATION));
            long duration = metaData.getLong(MetaData.KEY_DURATION);
            long delta = 999;
            assertTrue("Duration differs too much from expected.",
                    duration > tc.getDuration() - delta);
            assertTrue("Duration differs too much from expected.",
                    duration < tc.getDuration() + delta);

            if (metaData.containsKey(MetaData.KEY_ROTATION_DEGREES)) {
                assertEquals("Rotation not equal to expected", tc.getRotation(),
                        metaData.getInteger(MetaData.KEY_ROTATION_DEGREES));
            }
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void getMetaDataStrings(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            MetaData metaData = parser.getMetaData();

            String[] keys = {
                    MetaData.KEY_TITLE, MetaData.KEY_ALBUM, MetaData.KEY_ARTIST,
                    MetaData.KEY_ALBUM_ARTIST, MetaData.KEY_GENRE, MetaData.KEY_TRACK_NUMBER,
                    MetaData.KEY_COMPILATION, MetaData.KEY_AUTHOR, MetaData.KEY_WRITER,
                    MetaData.KEY_DISC_NUMBER, MetaData.KEY_YEAR
            };
            for (String key : keys) {
                assertTrue(key + " not found", metaData.containsKey(key));
                String metadataEntry = metaData.getString(key);
                String expectedEntry = (String) tc.getMetaDataValue(key);
                assertEquals("MetaData " + key + " did not match", expectedEntry, metadataEntry);
            }
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void getMetaDataAlbumArt(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            MetaData metaData = parser.getMetaData();

            String key = MetaData.KEY_ALBUM_ART;

            assertTrue(key + " not found", metaData.containsKey(key));
            byte[] metadataEntry = metaData.getByteBuffer(key);
            int expectedSize = tc.getAlbumArtSize();
            assertEquals("MetaData " + key + " did not match", expectedSize,
                    metadataEntry.length);
            Bitmap albumArtBitmap = BitmapFactory
                    .decodeByteArray(metadataEntry, 0, metadataEntry.length);
            assertNotNull("Could not create album art bitmap", albumArtBitmap);
            assertEquals("Album art width did not match", tc.getAlbumArtWidth(),
                    albumArtBitmap.getWidth());
            assertEquals("Album art height did not match", tc.getAlbumArtHeight(),
                    albumArtBitmap.getHeight());
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void getTrackMetaData(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertNotNull("No video mime type", tc.getTrackMimeTypeVideo());
        assertNotNull("No audio mime type", tc.getTrackMimeTypeAudio());
        assertTrue("Duration equal or less than zero", tc.getDuration() > 0);

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            int tracks = parser.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                MetaData trackMeta = parser.getTrackMetaData(i);
                assertTrue("No track mime found", trackMeta.containsKey(MetaData.KEY_MIME_TYPE));
                // Don't know if this is Audio track or Video track
                // so check for both.
                String mime = trackMeta.getString(MetaData.KEY_MIME_TYPE);
                assertTrue("TrackMimeType not equal to expected.",
                        mime.equals(tc.getTrackMimeTypeAudio()) ||
                                mime.equals(tc.getTrackMimeTypeVideo()));
            }
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void getPlayerMetaData(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertNotNull("No mime type", tc.getMimeType());
        assertTrue("Duration equal or less than zero", tc.getDuration() > 0);

        try {
            initMediaPlayer();

            sMediaPlayer
                    .setDataSource(tc.getContentUri());

            assertTrue("Prepare failed", sMediaPlayer.prepare());

            MetaData metaData = sMediaPlayer.getMediaMetaData();

            assertTrue("KEY_MIME_TYPE not found, should always exist",
                    metaData.containsKey(MetaData.KEY_MIME_TYPE));
            String mime = metaData.getString(MetaData.KEY_MIME_TYPE);
            assertEquals("Mimetype not equal to expected", tc.getMimeType(), mime);

            assertTrue("KEY_FILE_DURATION not found, should always exist",
                    metaData.containsKey(MetaData.KEY_DURATION));
            long duration = metaData.getLong(MetaData.KEY_DURATION);
            long delta = 999;
            assertTrue("Duration differs too much from expected.",
                    duration > tc.getDuration() - delta);
            assertTrue("Duration differs too much from expected.",
                    duration < tc.getDuration() + delta);


            if (metaData.containsKey(MetaData.KEY_ROTATION_DEGREES)) {
                assertEquals("Rotation not equal to expected", tc.getRotation(),
                        metaData.getInteger(MetaData.KEY_ROTATION_DEGREES));
            }
        } finally {
            shutDown();
        }
    }

    public static void getMetaDataSonyMobileFlags(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        MetaDataParser parser = null;
        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNotNull("Parserfactory returned null parser", parser);
            int tracks = parser.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                MetaData trackMeta = parser.getTrackMetaData(i);
                assertTrue("No track mime found", trackMeta.containsKey(MetaData.KEY_MIME_TYPE));
                // Don't know if this is Audio track or Video track
                // so check for both.
                String mime = trackMeta.getString(MetaData.KEY_MIME_TYPE);
                if (!mime.startsWith("video")) {
                    continue;
                }
                if (trackMeta.containsKey(MetaData.KEY_IS_CAMERA_CONTENT)) {
                    int cameraFlags = trackMeta.getInteger(MetaData.KEY_IS_CAMERA_CONTENT);
                    assertEquals("Camera content flag is not set",
                            tc.getMetaDataValue(MetaData.KEY_IS_CAMERA_CONTENT), cameraFlags);
                }
            }
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void releaseMetaData(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        MetaDataParser parser = MetaDataParserFactory.create(tc.getContentUri());
        assertNotNull("Parserfactory returned null parser", parser);
        parser.release();
    }

    public static void createMetaDataParserWithInvalidContent(TestContent tc) {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        MetaDataParser parser = null;

        try {
            parser = MetaDataParserFactory.create(tc.getContentUri());
            assertNull("Parserfactory did not return null parser", parser);
        } finally {
            if (parser != null) {
                parser.release();
            }
        }
    }

    public static void pauseAndSeekAvailable(TestContent tc) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            initMediaPlayer();

            sMediaPlayer
                    .setDataSource(tc.getContentUri());

            assertTrue("Prepare failed", sMediaPlayer.prepare());

            MetaData metadata = sMediaPlayer.getMediaMetaData();

            assertNotNull("No metadata found", metadata);

            assertTrue("Seek available was not found in metadata",
                    metadata.containsKey(MetaData.KEY_SEEK_AVAILABLE));

            assertEquals("Seek available was false", 1,
                    metadata.getInteger(MetaData.KEY_SEEK_AVAILABLE));

            assertTrue("Pause available was not found in metadata",
                    metadata.containsKey(MetaData.KEY_PAUSE_AVAILABLE));

            assertEquals("Pause available was false", 1,
                    metadata.getInteger(MetaData.KEY_PAUSE_AVAILABLE));
        } finally {
            shutDown();
        }
    }

    public static void getCurrentPositionInCompletedState(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());

        try {
            sCompleted = false;
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.seekTo(sMediaPlayer.getDuration());
            sMediaPlayer.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer arg0) {
                    sCompleted = true;
                }
            });

            int timeWaited = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) {
                    fail("Timed out waiting for callback"); // Time-out after 20
                    // seconds
                }
            }
            int duration = sMediaPlayer.getDuration();
            int currentPosition = sMediaPlayer.getCurrentPosition();
            assertEquals("CurrentPosition differentiate from duration in COMPLETED state: "
                    + (duration - currentPosition), duration, currentPosition);
        } finally {
            shutDown();
        }
    }

    public static void stopReleaseResources(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting http content", TestContent.ID_TYPE_HTTP, tc.getId());

        try {
            initMediaPlayer();
            sBufferingUpdateReceivedAtTime = 0;
            sMediaPlayer.setOnBufferingUpdateListener(new OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    sBufferingUpdateReceivedAtTime = SystemClock.uptimeMillis();
                }
            });

            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();

            int timeWaited = 0;
            while (sBufferingUpdateReceivedAtTime == 0) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) {
                    fail("Timed out waiting for callback"); // Time-out after 20
                    // seconds
                }
            }
            sMediaPlayer.stop();
            long stopTime = SystemClock.uptimeMillis();
            SystemClock.sleep(2000); // Wait 2 seconds

            // Check that no Buffering updates are sent after stop is called.
            // However due to thread scheduling we allow a overlap of 500ms
            assertTrue("Buffering updates received after stop",
                    stopTime > sBufferingUpdateReceivedAtTime - 500);

        } finally {
            shutDown();
        }

    }

    private static void initMediaPlayer() {
        initMediaPlayer(null);
    }

    private static void initMediaPlayer(final Context context) {
        /*
         * MediaPlayer is created on new thread since callbacks are executed on
         * creating thread. If the media player is created on the same thread as
         * running the test case, the callbacks will not be executed until the
         * test method has been completed.
         */
        setHandlerForUncaughtExceptions();
        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread("testThread") {
            @Override
            public void run() {
                Looper.prepare();
                sLooper = Looper.myLooper();
                sMediaPlayer = new MediaPlayer(context);
                latch.countDown();
                Looper.loop();
            }
        };
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Could not create MediaPlayer");
        }
    }

    public static void setGetCustomVideoConfigurationParameter() {
        try {
            initMediaPlayer();

            sMediaPlayer.setCustomVideoConfigurationParameter("key_one", 1);
            int setValue = sMediaPlayer.getCustomVideoConfigurationParameter("key_one");
            assertEquals("Fetched custom value was not same as set", 1, setValue);

            sMediaPlayer.setCustomVideoConfigurationParameter("key_one", 2);
            setValue = sMediaPlayer.getCustomVideoConfigurationParameter("key_one");
            assertEquals("Fetched custom value was not same as set", 2, setValue);

            sMediaPlayer.setCustomVideoConfigurationParameter("key_two", 3);
            setValue = sMediaPlayer.getCustomVideoConfigurationParameter("key_two");
            assertEquals("Fetched custom value was not same as set", 3, setValue);

        } finally {
            shutDown();
        }
    }

    public static void seekToSwitchSurface(TestContent tc, SurfaceHolder sh,
            SurfaceHolder sh2) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Testcontent must have a valid duration", tc.getDuration() > 0);

        try {
            sCompleted = false;
            sCompletedCounter = 0;

            initMediaPlayer();

            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {

                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    sCompleted = true;
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            sMediaPlayer.play();
            int timeout = 0;
            while (sMediaPlayer.getCurrentPosition() == 0) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    break;
                }
            }

            assertTrue("Failed to start playback ", sMediaPlayer.getCurrentPosition() != 0);

            int seekTime = sMediaPlayer.getDuration() / 4 * 3;
            sMediaPlayer.seekTo(seekTime);
            sMediaPlayer.setDisplay(sh2);

            timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    break;
                }
            }

            assertTrue("onSeekComplete not called after surface change", sCompleted);

        } finally {
            shutDown();
        }
    }

    private static void shutDown() {
        try {
            if (sMediaPlayer != null && sMediaPlayer.getState() != MediaPlayer.State.END) {
                sMediaPlayer.release();
            }
            sMediaPlayer = null;
            if (sLooper != null) {
                sLooper.quit();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not shutdown MediaPlayer");
        }
        assertTrue("UncaughtException: " + sUncaughtExceptionMessage, sNoUncaughtException);
    }

    private static void setHandlerForUncaughtExceptions() {
        sNoUncaughtException = true;
        Thread.UncaughtExceptionHandler handler = new
                Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable ex) {
                        sNoUncaughtException = false;
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        ex.printStackTrace(pw);
                        sUncaughtExceptionMessage += sw.toString() + " ";
                        pw.close();
                    }
                };
        Thread.setDefaultUncaughtExceptionHandler(handler);
    }

    private static class MockRepresentationSelector implements RepresentationSelector {

        private int mRepresentation;

        public MockRepresentationSelector() {
        }

        @Override
        public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo,
                int[] selectedRepresentations) {
            selectedRepresentations[TrackType.VIDEO.ordinal()] = mRepresentation;
        }

        @Override
        public boolean selectRepresentations(long bandwidth, int[] selectedTracks,
                int[] selectedRepresentations) {
            boolean changed = selectedRepresentations[TrackType.VIDEO.ordinal()] != mRepresentation;
            selectedRepresentations[TrackType.VIDEO.ordinal()] = mRepresentation;
            return changed;
        }

        public void setVideoRepresentation(int representation) {
            mRepresentation = representation;
        }
    }

}
