/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import com.sonymobile.android.media.SurfaceViewStubActivity;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.TestContentProvider;
import com.sonymobile.android.media.Utils;

import java.io.IOException;

import android.test.ActivityInstrumentationTestCase2;

public class RegressionTestRunner extends
        ActivityInstrumentationTestCase2<SurfaceViewStubActivity> {

    private SurfaceViewStubActivity mSurfaceView;

    private TestContentProvider mTcp;

    public RegressionTestRunner() throws Exception {
        super(SurfaceViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTcp = new TestContentProvider(getActivity().getApplicationContext());
        Utils.logd(this, "setUp", "Start test");
        mSurfaceView = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mTcp = null;
        mSurfaceView = null;
        Utils.logd(this, "tearDown", "End test");
    }

    public void testWifiLostDuringPlaybackDASH() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        RegressionScenarioTests.testWifiLostDuringPlaybackDASH(tc, mSurfaceView.getSurfaceView()
                .getHolder(), mSurfaceView.getApplicationContext());
    }

    public void testLocalContentWithGRAPSubtitles() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_SUBTITLE_GRAP);
        RegressionScenarioTests.testGRAPSubtitles(tc, mSurfaceView.getSurfaceView()
                .getHolder());
    }

    public void testLocalContentPauseSleepPlay() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        RegressionScenarioTests.testPauseSleepPlay(tc, mSurfaceView.getSurfaceView()
                .getHolder());
    }

    public void testGetLinkSpeedReturnsZeroAtStart() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        RegressionScenarioTests.testGetLinkSpeedReturnsZeroAtStart(tc, mSurfaceView
                .getSurfaceView().getHolder(), mSurfaceView.getApplicationContext());
    }

    public void testGetDurationOnSpecificContent() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        RegressionScenarioTests.testGetDurationSpecificContent(tc, mSurfaceView
                .getSurfaceView().getHolder());
    }

    public void testVideoSizeChanged() throws IOException{
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        RegressionApiTests.videoSizeChanged(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSeekToNoMFRA() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_NO_MFRA);
        RegressionApiTests.seekToNoMFRA(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetDisplayInCompletedState() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        RegressionApiTests
                .setDisplayInCompletedState(tc, mSurfaceView.getSurfaceView().getHolder());
    }
}
