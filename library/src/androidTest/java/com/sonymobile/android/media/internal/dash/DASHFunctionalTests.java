/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.dash;

import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;
import junit.framework.TestCase;

import android.os.Looper;
import android.os.SystemClock;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.MediaInfo;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.MediaPlayer.Statistics;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.internal.mpegdash.DefaultDASHBandwidthEstimator;

public class DASHFunctionalTests extends TestCase {

    private int mOnSubtitleDataTrackIndex;

    private boolean mSubtitlesReceived;

    private boolean mBufferingDone;

    protected MediaPlayer mMediaPlayer;

    private boolean mStartCalled;

    private boolean mEndCalled;

    private boolean mDataTransferredCalled;

    private boolean mEstimatedCalled;

    private boolean mSelectRepresentationsCalled;

    private boolean mSelectDefaultRepresentationsCalled;

    private boolean mCalled;

    private Statistics mStatistics;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
    }

    public void testSubtitle() throws IllegalStateException, IOException {
        final Object subtitleLock = new Object();
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mMediaPlayer.selectTrack(2);

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        mOnSubtitleDataTrackIndex = -1;
        mSubtitlesReceived = false;

        mMediaPlayer.setOnSubtitleDataListener(new MediaPlayer.OnSubtitleDataListener() {

            public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
                mOnSubtitleDataTrackIndex = data.getTrackIndex();
                assertEquals("Tkhd box is missing in subtitle data", "tkhd",
                        new String(data.getData(), 4, 4));
                mSubtitlesReceived = true;
                synchronized (subtitleLock) {
                    subtitleLock.notifyAll();
                }
            }
        });

        mMediaPlayer.play();

        synchronized (bufferingDoneLock) {
            try {
                bufferingDoneLock.wait(20000);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Buffering was not finished", mBufferingDone);

        if (!mSubtitlesReceived) {
            synchronized (subtitleLock) {
                try {
                    subtitleLock.wait(20000);
                } catch (InterruptedException e) {
                }
            }
        }

        assertTrue("No subtitles received", mSubtitlesReceived);

        assertEquals("Got subtitles from wrong track", 2, mOnSubtitleDataTrackIndex);
    }

    public void testSubtitleImages() throws IOException {
        final Object subtitleLock = new Object();
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content15/Content15.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mMediaPlayer.selectTrack(2);

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        mOnSubtitleDataTrackIndex = -1;
        mSubtitlesReceived = false;

        mMediaPlayer.setOnSubtitleDataListener(new MediaPlayer.OnSubtitleDataListener() {

            public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
                mOnSubtitleDataTrackIndex = data.getTrackIndex();
                assertEquals("Tkhd box is missing in subtitle data", "tkhd",
                        new String(data.getData(), 4, 4));
                int tkhdLength = (data.getData()[0] & 0xFF) << 24
                        | (data.getData()[1] & 0xFF) << 16 | (data.getData()[2] & 0xFF) << 8
                        | (data.getData()[3] & 0xFF);
                assertEquals("Subs box is missing in subtitle data", "subs",
                        new String(data.getData(), tkhdLength + 4, 4));
                mSubtitlesReceived = true;
                synchronized (subtitleLock) {
                    subtitleLock.notifyAll();
                }
            }
        });

        mMediaPlayer.play();

        synchronized (bufferingDoneLock) {
            try {
                bufferingDoneLock.wait(20000);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Buffering was not finished", mBufferingDone);

        if (!mSubtitlesReceived) {
            synchronized (subtitleLock) {
                try {
                    subtitleLock.wait(20000);
                } catch (InterruptedException e) {
                }
            }
        }

        assertTrue("No subtitles received", mSubtitlesReceived);

        assertEquals("Got subtitles from wrong track", 2, mOnSubtitleDataTrackIndex);
    }

    public void testDeselectTrack() throws IllegalStateException, IOException {
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mMediaPlayer.selectTrack(2);

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        mMediaPlayer.play();

        synchronized (bufferingDoneLock) {
            try {
                bufferingDoneLock.wait(20000);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Buffering was not finished", mBufferingDone);

        mMediaPlayer.deselectTrack(2);
    }

    public void testGetMetaData() throws IllegalStateException, IOException {
        String mpd = "<?xml version=\"1.0\"?>\r\n"
                + "<MPD\r\n"
                + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n"
                + "  xmlns=\"urn:mpeg:dash:schema:mpd:2011\"\r\n"
                + "  xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\"\r\n"
                + "  type=\"static\"\r\n"
                + "  mediaPresentationDuration=\"PT4H30M\"\r\n"
                + "  minBufferTime=\"PT3.003000S\"\r\n"
                + "  maxSubsegmentDuration=\"PT3.003000S\"\r\n"
                + "  profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011,http://xmlns.sony.net/metadata/mpeg/dash/profile/senvu/2012\">\r\n"
                + "  <Period id=\"P1\" duration=\"PT4H30M\">\r\n"
                + "    <AdaptationSet  id=\"1\" group=\"2\" contentType=\"audio\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.5\" audioSamplingRate=\"48000\" lang=\"en\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\r\n"
                + "      <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\r\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\r\n"
                + "      <Representation id=\"1_1\" bandwidth=\"96000\">\r\n"
                + "        <BaseURL>Content1/Audio25.m4a</BaseURL>\r\n"
                + "      </Representation>\r\n"
                + "    </AdaptationSet>\r\n"
                + "    <AdaptationSet  id=\"2\" group=\"3\" contentType=\"text\" mimeType=\"application/mp4\" codecs=\"stpp\" par=\"16:9\" sar=\"1:1\" lang=\"en\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\r\n"
                + "      <Role schemeIdUri=\"http://xmlns.sony.net/metadata/mpeg/dash/role/2012\" value=\"forcedSubtitle\"/>\r\n"
                + "      <Representation id=\"2_1\" bandwidth=\"0\" width=\"854\" height=\"480\">\r\n"
                + "        <BaseURL>Content1/Subtitle71.m4t</BaseURL>\r\n"
                + "      </Representation>\r\n"
                + "    </AdaptationSet>\r\n"
                + "    <AdaptationSet  id=\"3\" group=\"1\" contentType=\"video\" mimeType=\"video/mp4\" codecs=\"avc1.4D401E\" par=\"16:9\" sar=\"1:1\" minBandwidth=\"4500000\" maxBandwidth=\"4500000\" minWidth=\"854\" maxWidth=\"854\" minHeight=\"480\" maxHeight=\"480\" frameRate=\"24000/1001\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\" maximumSAPPeriod=\"3.003000\">\r\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\r\n"
                + "      <Representation id=\"3_1\" bandwidth=\"4500000\" width=\"854\" height=\"480\" mediaStreamStructureId=\"1\">\r\n"
                + "        <BaseURL>Content1/Video35.m4v</BaseURL>\r\n"
                + "      </Representation>\r\n"
                + "    </AdaptationSet>\r\n"
                + "  </Period>\r\n"
                + "</MPD>";

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content1_GM8.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        MetaData metadata = mMediaPlayer.getMediaMetaData();

        assertNotNull("No metadata after prepare", metadata);

        assertEquals("Wrong mime type", "application/dash+xml",
                metadata.getString(MetaData.KEY_MIME_TYPE));

        assertEquals("MPD was wrong", mpd, metadata.getString(MetaData.KEY_MPD));
    }

    public void testCustomBandwidthEstimator() throws IllegalStateException, IOException {
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        mDataTransferredCalled = false;
        mStartCalled = false;
        mEndCalled = false;
        mEstimatedCalled = false;

        mMediaPlayer.setBandwidthEstimator(new BandwidthEstimator() {

            @Override
            public void onDataTransferred(long byteCount) {
                mDataTransferredCalled = true;

            }

            @Override
            public void onDataTransferStarted() {
                mStartCalled = true;
            }

            @Override
            public void onDataTransferEnded() {
                mEndCalled = true;
            }

            @Override
            public long getEstimatedBandwidth() {
                mEstimatedCalled = true;
                return 0;
            }
        });

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mMediaPlayer.play();

        if (!mBufferingDone) {
            synchronized (bufferingDoneLock) {
                try {
                    bufferingDoneLock.wait(20000);
                } catch (InterruptedException e) {
                }
            }
        }

        assertTrue("onDataTransferred never called", mDataTransferredCalled);
        assertTrue("onDataTransferStarted never called", mStartCalled);
        assertTrue("onDataTransferEnded never called", mEndCalled);
        assertTrue("getEstimatedBandwidth never called", mEstimatedCalled);

        mMediaPlayer.stop();
    }

    public void testCustomRepresentationSelector() throws IllegalStateException, IOException {
        final Object selectRepresentationsLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        BandwidthEstimator estimator = new DefaultDASHBandwidthEstimator();

        mMediaPlayer.setBandwidthEstimator(estimator);

        mSelectRepresentationsCalled = false;

        mSelectDefaultRepresentationsCalled = false;

        mMediaPlayer.setRepresentationSelector(new RepresentationSelector() {

            @Override
            public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo,
                    int[] selectedRepresentations) {
                mSelectDefaultRepresentationsCalled = true;
                selectedRepresentations[TrackType.AUDIO.ordinal()] = 0;
                selectedRepresentations[TrackType.SUBTITLE.ordinal()] = 0;
                selectedRepresentations[TrackType.VIDEO.ordinal()] = 0;
            }

            @Override
            public boolean selectRepresentations(long bandwidth, int[] selectedTracks,
                    int[] selectedRepresentations) {
                mSelectRepresentationsCalled = true;
                synchronized (selectRepresentationsLock) {
                    selectRepresentationsLock.notifyAll();
                }
                return true;
            }
        });

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mMediaPlayer.play();

        if (!mSelectRepresentationsCalled) {
            synchronized (selectRepresentationsLock) {
                try {
                    selectRepresentationsLock.wait(20000);
                } catch (InterruptedException e) {
                }
            }
        }

        assertTrue("selectDefaultRepresentations never called",
                mSelectDefaultRepresentationsCalled);
        assertTrue("selectRepresentations never called", mSelectRepresentationsCalled);

        mMediaPlayer.stop();
    }

    public void testSelectRepresentations() throws IllegalStateException, IOException {
        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        Vector<Integer> representations = new Vector<Integer>();
        representations.add(5);
        representations.add(6);

        mMediaPlayer.selectTrack(4, representations);

        mMediaPlayer.play();

        SystemClock.sleep(2000);

        mMediaPlayer.stop();
    }

    public void testSelectRepresentations_Invalid() throws IllegalStateException, IOException {
        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        Vector<Integer> representations = new Vector<Integer>();
        representations.add(-1);
        representations.add(16);

        mMediaPlayer.selectTrack(4, representations);

        mMediaPlayer.play();

        SystemClock.sleep(2000);

        mMediaPlayer.stop();
    }

    public void testGetStatistics() throws IllegalStateException, IOException {
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        mMediaPlayer.play();

        synchronized (bufferingDoneLock) {
            try {
                bufferingDoneLock.wait(20000);
            } catch (InterruptedException e) {
            }
        }

        Statistics statistics = mMediaPlayer.getStatistics();

        assertNotNull("getStatistics returned null", statistics);

        if (statistics.getLinkSpeed() <= 0) {
            fail("No linkspeed");
        }

        assertNotNull("No server IP", statistics.getServerIP());

        assertNotNull("No video URI", statistics.getVideoUri());
    }

    public void testRepresentationChangedListener() throws IllegalStateException, IOException {
        final Object bufferingDoneLock = new Object();

        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://vuabstest.dl.playstation.net/vuabstest/100/non-encrypted/SENVUABS_GM6/Content3/Content3.mpd");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        mCalled = false;
        mStatistics = null;

        mMediaPlayer
                .setOnRepresentationChangedListener(
                new MediaPlayer.OnRepresentationChangedListener() {

                    @Override
                    public void onRepresentationChanged(MediaPlayer mp, Statistics statistics) {
                        mCalled = true;
                        mStatistics = statistics;
                    }
                });

        mBufferingDone = false;

        mMediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {

            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {

                if (what == MediaInfo.BUFFERING_END) {
                    mBufferingDone = true;
                    synchronized (bufferingDoneLock) {
                        bufferingDoneLock.notifyAll();
                    }
                }
                return false;
            }
        });

        mMediaPlayer.play();

        synchronized (bufferingDoneLock) {
            try {
                bufferingDoneLock.wait(20000);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("onRepresentationChanged was never called", mCalled);

        assertNotNull("statistics was null", mStatistics);

        if (mStatistics.getLinkSpeed() <= 0) {
            fail("No linkspeed");
        }

        assertNotNull("No server IP", mStatistics.getServerIP());

        assertNotNull("No video URI", mStatistics.getVideoUri());
    }

    public void testPauseAndSeekAvailable() throws IOException {
        initMediaPlayer();

        mMediaPlayer
                .setDataSource("http://live.unified-streaming.com/loop/loop.isml/loop.mpd?format=mp4&session_id=50487");

        assertTrue("Prepare failed", mMediaPlayer.prepare());

        MetaData metadata = mMediaPlayer.getMediaMetaData();

        assertNotNull("No metadata found", metadata);

        assertTrue("Seek available was not found in metadata",
                metadata.containsKey(MetaData.KEY_SEEK_AVAILABLE));

        assertEquals("Seek available was true for live stream", 0,
                metadata.getInteger(MetaData.KEY_SEEK_AVAILABLE));

        assertTrue("Pause available was not found in metadata",
                metadata.containsKey(MetaData.KEY_PAUSE_AVAILABLE));

        assertEquals("Pause available was true for live stream", 0,
                metadata.getInteger(MetaData.KEY_PAUSE_AVAILABLE));
    }

    private void initMediaPlayer() {
        /*
         * MediaPlayer is created on new thread since callbacks are executed on
         * creating thread. If the media player is created on the same thread as
         * running the test case, the callbacks will not be executed until the
         * test method has been completed.
         */
        final CountDownLatch latch = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mMediaPlayer = new MediaPlayer();
                latch.countDown();
                Looper.loop();
            }
        }.start();
        try {
            assertTrue("Creating the media player timed out", latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail("Interrupted while waiting for creation of player");
        }
    }
}
