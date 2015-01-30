/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnSeekCompleteListener;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.TestHelper;
import com.sonymobile.android.media.Utils;

public class ScenarioTest extends TestHelper {

    private static final String TAG = "ScenarioTester";

    private static final boolean LOGS_ENABLED = false;

    private static MediaPlayer sMediaPlayer;

    private static Looper sLooper = null;

    private static boolean sCallbackOccurred;

    private static String sUncaughtExceptionMessage;

    private static boolean sNoUncaughtException;

    private static String sContentPath;

    private static int sDuration;

    private static int sSeekCompleteCounter;

    public static void testSeekToPlay(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);
        try {
            sCallbackOccurred = false;
            initMediaPlayer();
            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer arg0) {
                    sCallbackOccurred = true;
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            int duration = sMediaPlayer.getDuration();
            sMediaPlayer.seekTo(duration / 2);
            sMediaPlayer.play();
            int timeWaited = 0;
            while (!sCallbackOccurred) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) { // Waiting on callback for 20
                                              // seconds
                    fail("Timed out waiting for callback");
                }
            }
            assertEquals("Incorrect state of MediaPlayer",
                    MediaPlayer.State.PLAYING, sMediaPlayer.getState());

            assertTrue("Expected MediaPlayers position to be at, or smaller and close to " +
                    "seeked value. Seeked to: " + duration / 2 + ", retrieved: "
                    + sMediaPlayer.getCurrentPosition(),
                    sMediaPlayer.getCurrentPosition()
                    > (duration / 2) - tc.getMaxIFrameInterval());

            assertTrue("Expected MediaPlayers position to be at, or smaller and close to " +
                    "seeked value. Seeked to: " + duration / 2 + ", retrieved: "
                    + sMediaPlayer.getCurrentPosition(),
                    sMediaPlayer.getCurrentPosition() <= duration / 2);
        } finally {
            shutDown();
        }
    }

    public static void testPlaySeekTo(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);
        try {
            sCallbackOccurred = false;
            initMediaPlayer();
            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer arg0) {
                    sCallbackOccurred = true;
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            int duration = sMediaPlayer.getDuration();
            sMediaPlayer.play();
            sMediaPlayer.seekTo(duration / 2);
            int timeWaited = 0;
            while (!sCallbackOccurred) {
                SystemClock.sleep(50);
                timeWaited += 50;
                if (timeWaited > 20 * 1000) { // Waiting on callback for 20
                                              // seconds
                    fail("Timed out waiting for callback");
                }
            }
            assertEquals("Incorrect state of MediaPlayer",
                    MediaPlayer.State.PLAYING, sMediaPlayer.getState());

            assertTrue("Expected MediaPlayers position to be at, or smaller and close to " +
                    "seeked value. Seeked to: " + duration / 2 + ", retrieved: "
                    + sMediaPlayer.getCurrentPosition(),
                    sMediaPlayer.getCurrentPosition()
                    > (duration / 2) - tc.getMaxIFrameInterval());

            assertTrue("Expected MediaPlayers position to be at, or smaller and close to " +
                    "seeked value. Seeked to: " + duration / 2 + ", retrieved: "
                    + sMediaPlayer.getCurrentPosition(),
                    sMediaPlayer.getCurrentPosition() <= duration / 2);
        } finally {
            shutDown();
        }

    }

    public static void testPlayPause(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            for (int i = 0; i < 5; i++) {
                sMediaPlayer.play();
                sMediaPlayer.pause();
            }
            SystemClock.sleep(1000); // Wait to see that MediaPlayer handles
            // scenario correctly
            assertEquals("Incorrect state of MediaPlayer",
                    MediaPlayer.State.PAUSED, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void testResetSetDataSource(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.reset();
            sMediaPlayer.setDataSource(tc.getContentUri());
            SystemClock.sleep(1000); // Wait to see that MediaPlayer handles
            // scenario correctly
            assertEquals(MediaPlayer.State.INITIALIZED, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void testStopPrepare(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            sMediaPlayer.stop();
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            assertEquals("Incorrect state of MediaPlayer",
                    MediaPlayer.State.PREPARED, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void testSelectTrack(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            for (int i = 0; i < 5; i++) {
                sMediaPlayer.selectTrack(i % 2);
            }
            SystemClock.sleep(1000); // Waiting to see if MediaPlayer handles
            // scenario correctly
            for (int i = 0; i < 5; i++) {
                sMediaPlayer.selectTrack(0);
            }
            SystemClock.sleep(1000); // Waiting to see if MediaPlayer handles
            // scenario correctly
        } finally {
            shutDown();
        }
    }

    public static void testSetDataSource(final TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        try {
            initMediaPlayer();
            int i = 0;
            try {
                while (i < 5) {
                    sMediaPlayer.setDataSource(tc.getContentUri());
                    i++;
                }
            } catch (IllegalStateException e) {
                assertEquals("Exception did not occur correctly", 1, i);
            }
            assertEquals("Incorrect state of MediaPlayer",
                    MediaPlayer.State.INITIALIZED, sMediaPlayer.getState());
        } finally {
            shutDown();
        }
    }

    public static void testStateChangingMethods(TestContent tc, SurfaceHolder sh, int[] methodNames)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No methods to test", methodNames.length > 0);
        try {
            sContentPath = tc.getContentUri();
            initMediaPlayer();
            for (int i = 0; i < methodNames.length; i++) {
                callMethod(methodNames[i]);
            }
        } finally {
            shutDown();
        }
    }

    public static void testHeavyLoadOnHandlerWithSeekTo(TestContent tc, SurfaceHolder sh,
            int load) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No methods to test", load > 1);
        try{
            Random rand = new Random();
            sSeekCompleteCounter = 0;
            initMediaPlayer();
            sMediaPlayer.setOnSeekCompleteListener(new OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer arg0) {
                    sSeekCompleteCounter++;
                }
            });
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sDuration = sMediaPlayer.getDuration();
            sMediaPlayer.play();
            for (int i = 0; i < load - 1; i++) {
                sMediaPlayer.seekTo(1 + rand.nextInt(sDuration - 1));
            }
            int timeWaited = 0;
            while (timeWaited < 10 * 1000 && sSeekCompleteCounter < load - 1) {
                SystemClock.sleep(50);
                timeWaited += 50;
            }
            timeWaited = 0;
            sMediaPlayer.seekTo(0);
            while (timeWaited < 10 * 1000 && sMediaPlayer.getCurrentPosition() != 0) {
                // Await the seekTo(0)
                SystemClock.sleep(10);
                timeWaited += 10;
            }
            timeWaited = 0;
            while (timeWaited < 10 * 1000 && sMediaPlayer.getCurrentPosition() == 0) {
                // Await the MediaPlayer to play from 0
                SystemClock.sleep(10);
                timeWaited += 10;
            }
            assertEquals("Not enough callbacks", load, sSeekCompleteCounter);

        }finally{
            shutDown();
        }
    }

    private static void callMethod(int methodName) throws IOException {
        if (LOGS_ENABLED) Utils.logd(TAG, getString(methodName), "");
        switch (methodName) {
            case METHOD_SET_DATA_SOURCE:
                sMediaPlayer.setDataSource(sContentPath);
                break;
            case METHOD_RESET:
                sMediaPlayer.reset();
                break;
            case METHOD_PREPARE:
                sMediaPlayer.prepare();
                break;
            case METHOD_PLAY:
                sMediaPlayer.play();
                break;
            case METHOD_PAUSE:
                sMediaPlayer.pause();
                break;
            case METHOD_STOP:
                sMediaPlayer.stop();
                break;
            default:
                fail("No such method");
        }
    }

    public static void rapidPlayRelease(final TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting local content", TestContent.ID_TYPE_LOCAL, tc.getId());

        try {
            for (int i = 0; i < 15; i++) {
                initMediaPlayer();
                sMediaPlayer.setDisplay(sh);
                sMediaPlayer.setDataSource(tc.getContentUri());
                assertTrue("Prepare failed", sMediaPlayer.prepare());
                sMediaPlayer.play();
                int timeToWait = 0;
                while (sMediaPlayer.getCurrentPosition() == 0) {
                    SystemClock.sleep(100);
                    if (timeToWait > 20 * 1000) { // Time-out after 20 seconds
                        fail("Timed out waiting for playback to start");
                    }
                    timeToWait += 100;
                }
                sMediaPlayer.pause();
                sMediaPlayer.stop();
                shutDown();
            }
        } finally {
            shutDown();
        }
    }

    protected static void initMediaPlayer() {
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
                sMediaPlayer = new MediaPlayer();
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

    protected static void shutDown() {
        final String testName = "shutDown";
        try {
            if (sMediaPlayer != null && sMediaPlayer.getState() != MediaPlayer.State.END) {
                sMediaPlayer.release();
            }
            sMediaPlayer = null;
            if (sLooper != null) {
                sLooper.quit();
            }
        } catch (Exception e) {
            Utils.loge(TAG, testName, "Could not shutdown MediaPlayer");
        }

        assertTrue("UncaughtException: " + sUncaughtExceptionMessage, sNoUncaughtException);
    }

    private static void setHandlerForUncaughtExceptions() {
        sNoUncaughtException = true;
        Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
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
}
