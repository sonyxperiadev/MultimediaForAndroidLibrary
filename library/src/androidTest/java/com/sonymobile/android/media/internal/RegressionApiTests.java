/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnSeekCompleteListener;
import com.sonymobile.android.media.MediaPlayer.OnVideoSizeChangedListener;
import com.sonymobile.android.media.MediaPlayer.OnCompletionListener;
import com.sonymobile.android.media.MediaPlayer.State;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.Utils;

public class RegressionApiTests {

    private static final String TAG = "RegressionApiTester";

    private static boolean sNoUncaughtException;

    private static String sUncaughtExceptionMessage;

    private static MediaPlayer sMediaPlayer;

    private static Looper sLooper = null;

    private static boolean sVideoSizeChanged;

    private static boolean sCompleted = false;

    public static void videoSizeChanged(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("No maxIFrameInterval set", tc.getMaxIFrameInterval() > 0);

        final Object lock = new Object();

        try {
            initMediaPlayer();

            sVideoSizeChanged = false;
            sMediaPlayer.setOnVideoSizeChangedListener(new OnVideoSizeChangedListener() {

                @Override
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    synchronized (lock) {
                        sVideoSizeChanged = true;
                        lock.notifyAll();
                    }
                }
            });

            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            synchronized (lock) {
                if (!sVideoSizeChanged) {
                    try {
                        lock.wait(20000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            assertTrue("onVideoSizeChanged was not called.", sVideoSizeChanged);

            // Wait 1s to let any initial INFO_OUTPUT_FORMAT_CHANGED frame
            // dequeue properly.
            SystemClock.sleep(1000);

            sVideoSizeChanged = false;
            // Set null display during play should not trigger
            // onVideoSizeChanged
            sMediaPlayer.setDisplay(null);
            synchronized (lock) {
                if (!sVideoSizeChanged) {
                    try {
                        lock.wait(2000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            assertFalse("onVideoSizeChanged signalled after setDisplay(null)", sVideoSizeChanged);

            sVideoSizeChanged = false;
            // Set valid display during play should not trigger
            // onVideoSizeChanged
            sMediaPlayer.setDisplay(sh);
            synchronized (lock) {
                if (!sVideoSizeChanged) {
                    try {
                        lock.wait(2000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }

            assertFalse("onVideoSizeChanged signalled after setDisplay(sh)", sVideoSizeChanged);

        } finally {
            shutDown();
        }
    }

    public static void seekToNoMFRA(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Testcontent must have a valid duration", tc.getDuration() > 0);

        try {
            sCompleted = false;

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
            SystemClock.sleep(5000);

            int seekTime = sMediaPlayer.getDuration() / 4 * 3;
            sMediaPlayer.seekTo(seekTime);

            int timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onSeekCompleted");
                }
            }

            int pos = sMediaPlayer.getCurrentPosition();
            assertTrue("Wrong current position after seek, expected < 1000, got " + pos,
                    pos < 1000);

        } finally {
            shutDown();
        }
    }

    public static void setDisplayInCompletedState(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertTrue("Testcontent must have a valid duration", tc.getDuration() > 0);

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
            sMediaPlayer.setDisplay(sh);
            assertTrue("Prepare failed", sMediaPlayer.prepare());

            sMediaPlayer.play();
            int seekTime = sMediaPlayer.getDuration() - 500;
            sMediaPlayer.seekTo(seekTime);

            int timeout = 0;
            while (!sCompleted) {
                SystemClock.sleep(50);
                timeout += 50;
                if (timeout > 10000) {
                    fail("Timed out waiting for onCompletion");
                }
            }

            assertTrue("State is not COMPLETED", sMediaPlayer.getState() == State.COMPLETED);

            sCompleted = false;
            sMediaPlayer.setDisplay(null);

            SystemClock.sleep(1000);

            assertFalse("onCompletion was called in completed state", sCompleted);

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
        final String testName = "initMediaPlayer";
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
            Utils.loge(TAG, testName, "Could not create MediaPlayer");
        }
    }

    private static void shutDown() {
        final String testName = "shutDown";
        try {
            if (sMediaPlayer != null && sMediaPlayer.getState() != State.END) {
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

}
