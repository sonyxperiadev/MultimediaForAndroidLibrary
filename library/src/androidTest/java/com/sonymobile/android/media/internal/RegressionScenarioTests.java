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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnErrorListener;
import com.sonymobile.android.media.MediaPlayer.OnSubtitleDataListener;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.Utils;

public class RegressionScenarioTests {

    private static final String TAG = "RegressionScenarioTester";

    private static MediaPlayer sMediaPlayer;

    private static Looper sLooper = null;

    private static boolean sNoUncaughtException;

    private static String sUncaughtExceptionMessage;

    private static boolean sErrorCallbackReceived;

    private static boolean sSubtitleDataCallback;

    private static SubtitleData sSubtitleData;

    public static void testWifiLostDuringPlaybackDASH(TestContent tc, SurfaceHolder sh,
            Context context) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting MPEG-DASH content", TestContent.ID_TYPE_DASH, tc.getId());
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        assertTrue("Wifi not enabled", wifiManager.isWifiEnabled());
        ConnectivityManager connManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        assertTrue("Wifi not connected", mWifi.isConnected());

        try {
            sErrorCallbackReceived = false;
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setOnErrorListener(new OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    sErrorCallbackReceived = true;
                    return true;
                }
            });
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            SystemClock.sleep(5000);
            wifiManager.setWifiEnabled(false);
            int timeWaited = 0;
            while (!sErrorCallbackReceived) {
                SystemClock.sleep(100);
                timeWaited += 100;
                if (timeWaited > 15 * 1000) { // Time-out after 15 seconds
                    fail("Timed out waiting for OnError-callback");
                }
            }
        } finally {
            wifiManager.setWifiEnabled(true);
            int timeWaited = 0;
            // Checks wifi enabled
            while (!wifiManager.isWifiEnabled()) {
                SystemClock.sleep(100);
                timeWaited += 100;
                if (timeWaited > 25 * 1000) {
                    // Even though the test may have passed if we don't
                    // reconnect to the wifi other remaining tests might suffer
                    // from this wifi disabling. Instead of checking for wifi
                    // enabled on other tests we can check that it reconnects
                    // here. Mabye we in the future we should check on tests
                    // that they have wifi enabled on streaming content?
                    fail("Timed out waiting for wifi to reconnect");
                }
            }
            connManager = (ConnectivityManager)context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            timeWaited = 0;
            // Checks wifi connected
            while (!mWifi.isConnected()) {
                SystemClock.sleep(1000);
                timeWaited += 1000;
                mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (timeWaited > 20 * 1000) {
                    // Same as above
                    fail("Timed out waiting for wifi to reconnect");
                }
            }

            shutDown();
        }
    }

    public static void testGRAPSubtitles(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertNotNull("No subtitle track chosen", tc.getSubtitleTrack());
        assertTrue("No subtitle length interval", tc.getSubtitleLengthInterval() > 0);
        assertEquals("Expecting GRAP-subtitles content",
                TestContent.ID_TYPE_LOCAL_WITH_SUBTITLE_GRAP, tc.getId());

        try {
            int timeOut = tc.getSubtitleLengthInterval();
            sSubtitleDataCallback = false;
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            sMediaPlayer.setOnSubtitleDataListener(new OnSubtitleDataListener() {

                @Override
                public void onSubtitleData(MediaPlayer arg0, SubtitleData sd) {
                    sSubtitleData = sd;
                    sSubtitleDataCallback = true;
                }
            });

            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.selectTrack(tc.getSubtitleTrack());
            sMediaPlayer.play();

            int timeWaited = 0;
            while (!sSubtitleDataCallback) {
                SystemClock.sleep(100);
                timeWaited += 100;
                assertTrue("Timed out waiting for callback", timeWaited < timeOut);
            }

            int currentPosition = sMediaPlayer.getCurrentPosition();
            assertTrue(
                    "Subtitle callback not synchronized, "
                            + "Subtitle start-time (Ms): "
                            + (sSubtitleData.getStartTimeUs() / 1000)
                            + " MediaPlayer.currentPosition() (Ms): "
                            + currentPosition, (sSubtitleData.getStartTimeUs() / 1000)
                            - currentPosition <= 100
                            && (sSubtitleData.getStartTimeUs() / 1000) - currentPosition >= -100);

            int duration = sMediaPlayer.getDuration();
            // Seek forward
            sMediaPlayer.seekTo(duration / 2);
            timeWaited = 0;
            int pos = sMediaPlayer.getCurrentPosition();
            while (pos == duration / 2 || pos == 0) {
                SystemClock.sleep(10);
                timeWaited += 10;
                assertTrue("Timed out waiting for playback to start after seek",
                        timeWaited < 10 * 1000);
                pos = sMediaPlayer.getCurrentPosition();
            }
            sSubtitleDataCallback = false;
            timeWaited = 0;
            while (!sSubtitleDataCallback) {
                SystemClock.sleep(100);
                timeWaited += 100;
                assertTrue("Timed out waiting for callback", timeWaited < timeOut);
            }

            currentPosition = sMediaPlayer.getCurrentPosition();
            assertTrue(
                    "Subtitle callback not synchronized, "
                            + "Subtitle start-time (Ms): "
                            + (sSubtitleData.getStartTimeUs() / 1000)
                            + " MediaPlayer.currentPosition() (Ms): "
                            + currentPosition, (sSubtitleData.getStartTimeUs() / 1000)
                            - currentPosition <= 100
                            && (sSubtitleData.getStartTimeUs() / 1000) - currentPosition >= -100);

            // Seek backwards
            sMediaPlayer.seekTo(duration / 4);
            timeWaited = 0;
            pos = sMediaPlayer.getCurrentPosition();
            int posBeforeSeekStarted = pos;
            while (pos == duration / 4 || pos == posBeforeSeekStarted) {
                SystemClock.sleep(10);
                timeWaited += 10;
                assertTrue("Timed out waiting for playback to start after seek",
                        timeWaited < 10 * 1000);
                pos = sMediaPlayer.getCurrentPosition();
            }
            sSubtitleDataCallback = false;
            timeWaited = 0;
            while (!sSubtitleDataCallback) {
                SystemClock.sleep(100);
                timeWaited += 100;
                assertTrue("Timed out waiting for callback", timeWaited < timeOut);
            }

            currentPosition = sMediaPlayer.getCurrentPosition();
            assertTrue(
                    "Subtitle callback not synchronized, "
                            + "Subtitle start-time (Ms): "
                            + (sSubtitleData.getStartTimeUs() / 1000)
                            + " MediaPlayer.currentPosition() (Ms): "
                            + currentPosition, (sSubtitleData.getStartTimeUs() / 1000)
                            - currentPosition <= 100
                            && (sSubtitleData.getStartTimeUs() / 1000) - currentPosition >= -100);

        } finally {
            shutDown();
        }
    }

    public static void testPauseSleepPlay(TestContent tc, SurfaceHolder sh) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting local content", TestContent.ID_TYPE_LOCAL, tc.getId());

        try {
            initMediaPlayer();
            Random rand = new Random();
            int timeWaited = 0;
            int currentPositionBeforeSleep, discrepancy;
            sMediaPlayer.setDisplay(sh);
            // The iteration is due to a return value outside of MediaPlayer.
            // Which may or may not return the wanted value therefore we iterate
            // to trigger it.
            for (int i = 0; i < 10; i++) {
                sMediaPlayer.setDataSource(tc.getContentUri());
                assertTrue("Prepare failed", sMediaPlayer.prepare());
                sMediaPlayer.play();
                while (sMediaPlayer.getCurrentPosition() == 0) {
                    SystemClock.sleep(10);
                    timeWaited += 10;
                    assertTrue("Timed out waiting for MediaPlayer to start", timeWaited < 5 * 1000);
                }
                SystemClock.sleep(2000 + rand.nextInt(2000));
                currentPositionBeforeSleep = sMediaPlayer.getCurrentPosition();
                sMediaPlayer.pause();
                SystemClock.sleep(3000 + rand.nextInt(2000));
                sMediaPlayer.play();
                timeWaited = 0;
                while (sMediaPlayer.getCurrentPosition() == currentPositionBeforeSleep) {
                    SystemClock.sleep(5);
                    timeWaited += 5;
                    assertTrue("Timed out waiting for MediaPlayer to start", timeWaited < 5 * 1000);
                }
                discrepancy = sMediaPlayer.getCurrentPosition() - currentPositionBeforeSleep;
                assertTrue("Difference in time after pause, discrepancy: " + discrepancy,
                        discrepancy < 100 && discrepancy > -100);

                currentPositionBeforeSleep = sMediaPlayer.getCurrentPosition();
                sMediaPlayer.pause();
                SystemClock.sleep(3000 + rand.nextInt(2000));
                sMediaPlayer.play();
                timeWaited = 0;
                while (sMediaPlayer.getCurrentPosition() == currentPositionBeforeSleep) {
                    SystemClock.sleep(5);
                    timeWaited += 5;
                    assertTrue("Timed out waiting for MediaPlayer to start", timeWaited < 5 * 1000);
                }
                discrepancy = sMediaPlayer.getCurrentPosition() - currentPositionBeforeSleep;
                assertTrue("Difference in time after pause, discrepancy: " + discrepancy,
                        discrepancy < 200 && discrepancy > -200);

                sMediaPlayer.reset();
            }

        } finally {
            shutDown();
        }
    }

    public static void testGetLinkSpeedReturnsZeroAtStart(TestContent tc, SurfaceHolder sh,
            Context context) throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting DASH content", TestContent.ID_TYPE_DASH, tc.getId());
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        assertTrue("Wifi not enabled", wifiManager.isWifiEnabled());
        ConnectivityManager connManager = (ConnectivityManager)context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        assertTrue("Wifi not connected", mWifi.isConnected());
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            int timeWaited = 0;
            while (sMediaPlayer.getStatistics() == null) {
                SystemClock.sleep(10);
                timeWaited += 10;
                assertTrue("Timed out waiting for getStatistics to not return null",
                        timeWaited < 10 * 1000);
            }
            int linkSpeed = sMediaPlayer.getStatistics().getLinkSpeed();
            assertEquals("LinkSpeed not 0 when bandwith is not yet estimated", 0, linkSpeed);
        } finally {
            shutDown();
        }
    }

    // Yes, this is test for specific content
    public static void testGetDurationSpecificContent(TestContent tc, SurfaceHolder sh)
            throws IOException {
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        assertEquals("Expecting local content", TestContent.ID_TYPE_LOCAL, tc.getId());
        int durationMs = 320384;
        try {
            initMediaPlayer();
            sMediaPlayer.setDisplay(sh);
            sMediaPlayer.setDataSource(tc.getContentUri());
            assertTrue("Prepare failed", sMediaPlayer.prepare());
            sMediaPlayer.play();
            int timeWaited = 0;
            while(sMediaPlayer.getCurrentPosition() == 0){
                SystemClock.sleep(10);
                timeWaited += 10;
                assertTrue("Timed out waiting for sources to be created", timeWaited < 10 * 1000);
            }
            assertEquals("Duration does not match", durationMs,
                    sMediaPlayer.getDuration());
        } finally {
            shutDown();
        }
    }

    protected static void initMediaPlayer() {
        initMediaPlayer(null);
    }

    protected static void initMediaPlayer(final Context context) {
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
