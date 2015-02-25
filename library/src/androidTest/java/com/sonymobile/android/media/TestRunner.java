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

import com.sonymobile.android.media.SurfaceViewStubActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import junit.framework.Assert;

import android.annotation.TargetApi;
import android.os.Build;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

public class TestRunner extends
        ActivityInstrumentationTestCase2<SurfaceViewStubActivity> {

    private static final String TAG = "ApiTestRunner";

    private SurfaceViewStubActivity mSurfaceView;

    private TestContentProvider mTcp;

    public TestRunner() throws Exception {
        super(SurfaceViewStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTcp = new TestContentProvider(getActivity().getApplicationContext());
        mSurfaceView = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mTcp = null;
        mSurfaceView = null;
    }

    public void testCreateMediaPlayer() {
        ApiTest.createMediaPlayer();
    }

    public void testCreateMediaPlayerOnThread() {
        ApiTest.createMediaPlayerOnThread();
    }

    public void testSetDisplay() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDisplay(tc, mSurfaceView.getSurfaceView().getHolder(),
                mSurfaceView.getSurfaceView2().getHolder());
    }

    public void testSetVideoScalingMode() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setVideoScalingMode(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testGetState() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getState(tc);
    }

    public void testSetDataSource() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDataSource(tc);
    }

    public void testSetDataSource2() throws IOException { // (FileDescriptor fd)
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        File file = new File(tc.getContentUri());
        FileInputStream fis = new FileInputStream(file);
        ApiTest.setDataSource(fis.getFD());
        fis.close();
    }

    public void testSetDataSource3() throws IOException { // (Context, Uri)
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_MEDIASTORE);
        Assert.assertNotNull("Failed to get TestContent", tc);
        ApiTest.setDataSource(getActivity(), tc.getContentUri());
    }

    public void testSetDataSourceFdWithOffset() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_OFFSET);
        assertNotNull("No test content", tc);
        assertNotNull("No content uri", tc.getContentUri());
        File file = new File(tc.getContentUri());
        FileInputStream fis = new FileInputStream(file);
        ApiTest.setDataSourceFdWithOffset(tc, fis.getFD());
        fis.close();
    }

    public void testSetDataSource4() throws IOException { // (Context, Uri)
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDataSource(tc, 0, Long.MAX_VALUE);
    }

    public void testPrepare() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.prepare(tc);
    }

    public void testPrepareInvalid() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_INVALID);
        ApiTest.prepareInvalid(tc);
    }

    public void testPrepareAsync() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.prepareAsync(tc);
    }

    public void testPlay() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.play(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testPlayHEVC() throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_HEVC);
            ApiTest.play(tc, mSurfaceView.getSurfaceView().getHolder());
        } else {
            Log.i(TAG, "testPlayHEVC is not valid for API level below Lollipop");
        }
    }

    public void testGetCurrentPosition() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getCurrentPosition(tc);
    }

    public void testGetDuration() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getDuration(tc);
    }

    public void testPlayDASH() throws IOException { // (String path)
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.play(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testPause() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.pause(tc);
    }

    public void testSeekTo() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.seekTo(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testRelease() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.release(tc);
    }

    public void testReset() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.reset(tc);
    }

    public void testGetVideoWidth() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getVideoWidth(tc);
    }

    public void testGetVideoHeight() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getVideoHeight(tc);
    }

    public void testGetVideoWidthSampleAspectRatio() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_SAMPLE_ASPECT_RATIO);
        ApiTest.getVideoWidth(tc);
    }

    public void testGetVideoHeightSampleAspectRatio() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_SAMPLE_ASPECT_RATIO);
        ApiTest.getVideoHeight(tc);
    }

    public void testGetTrackInfo() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackInfo(tc);
    }

    public void testGetStatistics() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.getStatistics(tc);
    }

    public void testSetSpeed() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setSpeed(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnPreparedListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnPreparedListener(tc);
    }

    public void testSetOnInfoListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.setOnInfoListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnInfoListenerBackground() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnInfoListenerBackground(tc);
    }

    public void testSetOnErrorListener() throws IOException {
        // Invalid content (no file)
        TestContent invalid = mTcp.getTestItemById(TestContent.ID_TYPE_INVALID);
        ApiTest.setOnErrorListener(invalid);
    }

    public void testNoSetOnErrorListener() throws IOException {
        // Invalid content (no file)
        TestContent invalid = mTcp.getTestItemById(TestContent.ID_TYPE_INVALID);
        ApiTest.noSetErrorListener(invalid);
    }

    public void testSetOnCompletionListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnCompletionListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnSeekCompleteListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.setOnSeekCompleteListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnRepresentationChangedListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.setOnRepresentationChangedListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnSubtitleDataListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_SUBTITLE_TTML);
        ApiTest.setOnSubtitleDataListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetAudioSessionId() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.setAudioSessionId(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testGetAudioSessionId() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getAudioSessionId(tc, mSurfaceView.getSurfaceView().getHolder(),
                mSurfaceView.getSurfaceView2().getHolder(), getActivity());
    }

    public void testSelectTrack() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.selectTrack(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testDeselectTrack() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.deselectTrack(tc);
    }

    public void testSetOnBufferingUpdateListener() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_HTTP);
        ApiTest.setOnBufferingUpdateListener(tc);
    }

    public void testGetTrackCount() {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackCount(tc);
    }

    public void testGetMetaData() {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getMetaData(tc);
    }

    public void testGetMetaDataStrings() {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL_WITH_METADATA);
        ApiTest.getMetaDataStrings(tc);
    }

    public void testGetTrackMetaData() {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackMetaData(tc);
    }

    public void testReleaseMetaData() {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.releaseMetaData(tc);
    }

    public void testPauseAndSeekAvailableLocal() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.pauseAndSeekAvailable(tc);
    }

    public void testPauseAndSeekAvailableDASH() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_DASH);
        ApiTest.pauseAndSeekAvailable(tc);
    }

    public void testGetCurrentPositionInCompletedState() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_LOCAL);
        ApiTest.getCurrentPositionInCompletedState(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetOnInfoListenerHttp() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_HTTP);
        ApiTest.setOnInfoListener(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testStopReleaseResources() throws IOException {
        TestContent tc = mTcp.getTestItemById(TestContent.ID_TYPE_HTTP);
        ApiTest.stopReleaseResources(tc, mSurfaceView.getSurfaceView().getHolder());
    }

    public void testSetGetCustomVideoConfigurationParameter() throws IOException {
        ApiTest.setGetCustomVideoConfigurationParameter();
    }
}
