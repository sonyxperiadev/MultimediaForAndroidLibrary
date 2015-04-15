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

import com.sonymobile.android.media.annotations.Content;
import com.sonymobile.android.media.annotations.MetaInfo;
import com.sonymobile.android.media.annotations.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import junit.framework.Assert;

import android.annotation.TargetApi;
import android.os.Build;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

/**
 * How test content is selected:
 *
 * If the tests are started by MultipleContentTestRunner the Runner will create multiple test,
 * one for each content and inject the content via call to setTestContent.
 *
 * If the tests are started directly from this class, e.g. via a Gradle task the default content
 * will be used. The default content is selected by each test and is set via call to
 * setDefaultTestContent, if a content is already set calling setDefaultTestContent is a no-op.
 */
public class ApiTestCase extends
        ActivityInstrumentationTestCase2<SurfaceViewStubActivity> {

    private static final String TAG = "ApiTestCase";

    private SurfaceViewStubActivity mSurfaceView;

    private TestContentProvider mTcp;

    private TestContent mTestContent;

    public ApiTestCase() throws Exception {
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
        mTestContent = null;
    }

    public void setTestContent(TestContent content) {
        mTestContent = content;
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testCreateMediaPlayer() {
        ApiTest.createMediaPlayer();
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testCreateMediaPlayerOnThread() {
        ApiTest.createMediaPlayerOnThread();
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "maxIFrameInterval")
    public void testSetDisplay() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDisplay(mTestContent, mSurfaceView.getSurfaceView().getHolder(),
                mSurfaceView.getSurfaceView2().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Video")
    public void testSetVideoScalingMode() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setVideoScalingMode(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testGetState() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getState(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testSetDataSource() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDataSource(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testSetDataSource2() throws IOException { // (FileDescriptor fd)
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        assertNotNull("No test content", mTestContent);
        assertNotNull("No content uri", mTestContent.getContentUri());
        File file = new File(mTestContent.getContentUri());
        FileInputStream fis = new FileInputStream(file);
        ApiTest.setDataSource(fis.getFD());
        fis.close();
    }

    public void testSetDataSource3() throws IOException { // (Context, Uri)
        setDefaultTestContent(TestContent.ID_TYPE_MEDIASTORE);
        Assert.assertNotNull("Failed to get TestContent", mTestContent);
        ApiTest.setDataSource(getActivity(), mTestContent.getContentUri());
    }

    public void testSetDataSourceFdWithOffset() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_OFFSET);
        assertNotNull("No test content", mTestContent);
        assertNotNull("No content uri", mTestContent.getContentUri());
        File file = new File(mTestContent.getContentUri());
        FileInputStream fis = new FileInputStream(file);
        ApiTest.setDataSourceFdWithOffset(mTestContent, fis.getFD());
        fis.close();
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testSetDataSource4() throws IOException { // (Context, Uri)
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setDataSource(mTestContent, 0, Long.MAX_VALUE);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testPrepare() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.prepare(mTestContent);
    }

    public void testPrepareInvalid() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_INVALID);
        ApiTest.prepareInvalid(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testPrepareAsync() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.prepareAsync(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testPlay() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.play(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void testPlayHEVC() throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setDefaultTestContent(TestContent.ID_TYPE_LOCAL_HEVC);
            ApiTest.play(mTestContent, mSurfaceView.getSurfaceView().getHolder());
        } else {
            Log.i(TAG, "testPlayHEVC is not valid for API level below Lollipop");
        }
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Duration")
    public void testGetCurrentPosition() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getCurrentPosition(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Duration")
    public void testGetDuration() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getDuration(mTestContent);
    }

    @Protocol(types = "Dash")
    @Content(types = "Audio||Video")
    public void testPlayDASH() throws IOException { // (String path)
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.play(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testPause() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.pause(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "maxIFrameInterval")
    public void testSeekTo() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.seekTo(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testRelease() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.release(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testReset() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.reset(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Video")
    @MetaInfo(fields = "Width")
    public void testGetVideoWidth() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getVideoWidth(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Video")
    @MetaInfo(fields = "Height")
    public void testGetVideoHeight() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getVideoHeight(mTestContent);
    }

    public void testGetVideoWidthSampleAspectRatio() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_SAMPLE_ASPECT_RATIO);
        ApiTest.getVideoWidth(mTestContent);
    }

    public void testGetVideoHeightSampleAspectRatio() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_SAMPLE_ASPECT_RATIO);
        ApiTest.getVideoHeight(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "Width&&Height")
    public void testGetTrackInfo() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackInfo(mTestContent);
    }

    @Protocol(types = "Dash")
    @Content(types = "Audio||Video")
    public void testGetStatistics() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.getStatistics(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Duration")
    public void testSetSpeed() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setSpeed(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testSetOnPreparedListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnPreparedListener(mTestContent);
    }

    @Protocol(types = "Http||Dash")
    @Content(types = "Video")
    public void testSetOnInfoListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.setOnInfoListener(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Video")
    public void testSetOnInfoListenerBackground() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnInfoListenerBackground(mTestContent);
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

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testSetOnCompletionListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setOnCompletionListener(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "maxIFrameInterval")
    public void testSetOnSeekCompleteListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.setOnSeekCompleteListener(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Dash")
    @Content(types = "Video")
    public void testSetOnRepresentationChangedListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.setOnRepresentationChangedListener(mTestContent,
                mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "SubtitleTrack")
    public void testSetOnSubtitleDataListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_SUBTITLE_TTML);
        ApiTest.setOnSubtitleDataListener(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testSetAudioSessionId() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.setAudioSessionId(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Audio||Video")
    public void testGetAudioSessionId() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getAudioSessionId(mTestContent, mSurfaceView.getSurfaceView().getHolder(),
                mSurfaceView.getSurfaceView2().getHolder(), getActivity());
    }

    @Protocol(types = "Dash")
    @Content(types = "Video")
    public void testSelectTrack() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.selectTrack(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Dash")
    @Content(types = "Video")
    public void testDeselectTrack() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.deselectTrack(mTestContent);
    }

    @Protocol(types = "Http")
    @Content(types = "Audio||Video")
    public void testSetOnBufferingUpdateListener() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_HTTP);
        ApiTest.setOnBufferingUpdateListener(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "TrackCount")
    public void testGetTrackCount() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackCount(mTestContent);
    }

    @Protocol(types = "Local||Http")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Duration&&MimeType")
    public void testGetMetaData() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getMetaData(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Title&&Album&&Artist&&AlbumArtist&&Genre&&TrackNumber&&Compilation" +
            "&&Author&&Writer&&DiscNumber&&Year")
    public void testGetMetaDataStrings() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_METADATA);
        ApiTest.getMetaDataStrings(mTestContent);
    }

    public void testGetMetaDataSonyMobileFlags() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_SONY_MOBILE_FLAGS);
        ApiTest.getMetaDataSonyMobileFlags(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "AlbumArt")
    public void testGetMetaDataAlbumArt() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_ALBUMART);
        ApiTest.getMetaDataAlbumArt(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "TrackMimeTypeVideo&&TrackMimeTypeAudio")
    public void testGetTrackMetaData() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getTrackMetaData(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testReleaseMetaData() {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.releaseMetaData(mTestContent);
    }

    @Protocol(types = "Local||Http||Dash")
    @Content(types = "Invalid")
    public void testCreateMetaDataParserWithInvalidContent() {
        setDefaultTestContent(TestContent.ID_TYPE_INVALID);
        ApiTest.createMetaDataParserWithInvalidContent(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    public void testPauseAndSeekAvailableLocal() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.pauseAndSeekAvailable(mTestContent);
    }

    @Protocol(types = "Dash")
    @Content(types = "Audio||Video")
    public void testPauseAndSeekAvailableDASH() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_DASH);
        ApiTest.pauseAndSeekAvailable(mTestContent);
    }

    @Protocol(types = "Local")
    @Content(types = "Audio||Video")
    @MetaInfo(fields = "Duration")
    public void testGetCurrentPositionInCompletedState() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.getCurrentPositionInCompletedState(mTestContent,
                mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Http")
    @Content(types = "Audio||Video")
    public void testSetOnInfoListenerHttp() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_HTTP);
        ApiTest.setOnInfoListener(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Http")
    @Content(types = "Audio||Video")
    public void testStopReleaseResources() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_HTTP);
        ApiTest.stopReleaseResources(mTestContent, mSurfaceView.getSurfaceView().getHolder());
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    public void testSetGetCustomVideoConfigurationParameter() throws IOException {
        ApiTest.setGetCustomVideoConfigurationParameter();
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    public void testSeekToSwitchSurface() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL);
        ApiTest.seekToSwitchSurface(mTestContent, mSurfaceView.getSurfaceView().getHolder(),
                mSurfaceView.getSurfaceView2().getHolder());
    }

    @Protocol(types = "Local")
    @Content(types = "Video")
    @MetaInfo(fields = "Rotation")
    public void testRotation() throws IOException {
        setDefaultTestContent(TestContent.ID_TYPE_LOCAL_WITH_ROTATION);
        ApiTest.getVideoWidth(mTestContent);
        ApiTest.getVideoHeight(mTestContent);
        ApiTest.getMetaData(mTestContent);
        ApiTest.getPlayerMetaData(mTestContent);
    }

    /**
     * Calls to this function will update to use the Default Test Content if a content has not
     * already been set.
     *
     * @param defaultType The default test content type to use.
     */
    private void setDefaultTestContent(String defaultType) {
        if (mTestContent == null) {
            mTestContent = mTcp.getTestItemById(defaultType);
        }
    }
}
