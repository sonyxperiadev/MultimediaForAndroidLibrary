/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal;

import com.sonymobile.android.media.SurfaceViewStubActivity;
import com.sonymobile.android.media.TestContent;
import com.sonymobile.android.media.TestContentProvider;
import com.sonymobile.android.media.TestHelper;
import com.sonymobile.android.media.Utils;

import java.io.IOException;

import android.test.ActivityInstrumentationTestCase2;

public class ScenarioTestRunner extends
        ActivityInstrumentationTestCase2<SurfaceViewStubActivity> {

    private SurfaceViewStubActivity mSurfaceView;

    private TestContentProvider mTcp;

    public ScenarioTestRunner() throws Exception {
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

    public void testScenarioSeekToPlayLocal() throws IOException {
        ScenarioTest.testSeekToPlay(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioSeekToPlayDASH() throws IOException {
        ScenarioTest.testSeekToPlay(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlaySeekToLocal() throws IOException {
        ScenarioTest.testPlaySeekTo(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlaySeekToDASH() throws IOException {
        ScenarioTest.testPlaySeekTo(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlayPauseLocal() throws IOException {
        ScenarioTest.testPlayPause(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlayPauseDASH() throws IOException {
        ScenarioTest.testPlayPause(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioResetSetDataSourceLocal() throws IOException {
        ScenarioTest.testResetSetDataSource(
                mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioResetSetDataSourceDASH() throws IOException {
        ScenarioTest.testResetSetDataSource(
                mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioSelectTrackLocal() throws IOException {
        ScenarioTest.testSelectTrack(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());

    }

    public void testScenarioSelectTrackDASH() throws IOException {
        ScenarioTest.testSelectTrack(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioSetDataSourceLocal() throws IOException {
        ScenarioTest.testSetDataSource(
                mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioSetDataSourceDASH() throws IOException {
        ScenarioTest.testSetDataSource(
                mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlayStopPrepareLocal() throws IOException {
        ScenarioTest.testStopPrepare(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioPlayStopPrepareDASH() throws IOException {
        ScenarioTest.testStopPrepare(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder());
    }

    public void testScenarioStateFlow1Local() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE, TestHelper.METHOD_STOP,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_STOP
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioStateFlow1DASH() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE, TestHelper.METHOD_STOP,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_STOP
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioStateFlow2Local() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE, TestHelper.METHOD_STOP,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioStateFlow2DASH() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE, TestHelper.METHOD_STOP,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_STOP
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioStateFlow3Local() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_STOP, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE,
                TestHelper.METHOD_RESET
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioStateFlow3DASH() throws IOException {
        int[] methodCalls = {
                TestHelper.METHOD_SET_DATA_SOURCE, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_STOP, TestHelper.METHOD_PREPARE,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_RESET, TestHelper.METHOD_SET_DATA_SOURCE,
                TestHelper.METHOD_PREPARE, TestHelper.METHOD_PLAY, TestHelper.METHOD_PAUSE,
                TestHelper.METHOD_RESET
        };
        ScenarioTest.testStateChangingMethods(mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder(), methodCalls);
    }

    public void testScenarioLoadOnHandlerSeekToLocal() throws IOException {
        ScenarioTest.testHeavyLoadOnHandlerWithSeekTo(
                mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder(), 100);
    }

    public void testScenarioLoadOnHandlerSeekToDASH() throws IOException {
        ScenarioTest.testHeavyLoadOnHandlerWithSeekTo(
                mTcp.getTestItemById(TestContent.ID_TYPE_DASH),
                mSurfaceView.getSurfaceView().getHolder(), 100);
    }

    public void testRapidPlayRelease() throws IOException {
        ScenarioTest.rapidPlayRelease(mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL),
                mSurfaceView.getSurfaceView().getHolder());
    }
}
