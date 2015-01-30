/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.parser;

import junit.framework.TestCase;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;
import com.sonymobile.android.media.internal.Util;

public class TrackInfoFunctionalTests extends TestCase {

    public void testGetTrackInfo() {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(Util.EXTERNAL_DIR + "/serge_and_eno_1.mp4");
            mp.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }

        TrackInfo[] tracks = mp.getTrackInfo();
        int trackCount = tracks.length;
        assertEquals(trackCount, 2);

        for (int i = 0; i < trackCount; i++) {
            TrackInfo trackInfo = tracks[i];
            TrackRepresentation[] representations = trackInfo.getRepresentations();

            if (trackInfo.getTrackType() == TrackType.AUDIO) {
                assertEquals(trackInfo.getMimeType(), "audio/mp4a-latm");
                assertEquals(trackInfo.getDurationUs(), 70635102L);
                assertEquals(trackInfo.getLanguage(), "und");

                assertEquals(representations.length, 1);

                AudioTrackRepresentation audioRepresentation =
                        (AudioTrackRepresentation)representations[0];
                assertEquals(audioRepresentation.getChannelCount(), 2);
                assertEquals(audioRepresentation.getSampleRate(), 44100);

            } else if (trackInfo.getTrackType() == TrackType.VIDEO) {
                assertEquals(trackInfo.getMimeType(), "video/mp4v-es");
                assertEquals(trackInfo.getDurationUs(), 70720000L);
                assertEquals(trackInfo.getLanguage(), "und");

                assertEquals(representations.length, 1);

                VideoTrackRepresentation videoRepresentation =
                        (VideoTrackRepresentation)representations[0];
                assertEquals(videoRepresentation.getWidth(), 854);
                assertEquals(videoRepresentation.getHeight(), 480);
            }
        }
    }

    public void testGetTrackInfoWithSubtitles() {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(Util.EXTERNAL_DIR + "/MASN2229.MNV");
            mp.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }

        TrackInfo[] tracks = mp.getTrackInfo();
        int trackCount = tracks.length;
        assertEquals(trackCount, 25);

        TrackInfo trackInfo = tracks[0];
        TrackRepresentation[] representations = trackInfo.getRepresentations();

        assertEquals(trackInfo.getTrackType(), TrackType.VIDEO);
        assertEquals(trackInfo.getMimeType(), "video/avc");
        assertEquals(trackInfo.getDurationUs(), 320320000L);
        assertEquals(trackInfo.getLanguage(), "eng");

        assertEquals(representations.length, 1);

        VideoTrackRepresentation videoRepresentation =
                (VideoTrackRepresentation)representations[0];
        assertEquals(videoRepresentation.getWidth(), 720);
        assertEquals(videoRepresentation.getHeight(), 480);

        trackInfo = tracks[2];
        representations = trackInfo.getRepresentations();

        assertEquals(trackInfo.getTrackType(), TrackType.AUDIO);
        assertEquals(trackInfo.getMimeType(), "audio/mp4a-latm");
        assertEquals(trackInfo.getDurationUs(), 320064000L);
        assertEquals(trackInfo.getLanguage(), "jpn");

        assertEquals(representations.length, 1);

        AudioTrackRepresentation audioRepresentation =
                (AudioTrackRepresentation)representations[0];
        assertEquals(audioRepresentation.getChannelCount(), 2);
        assertEquals(audioRepresentation.getSampleRate(), 48000);

        trackInfo = tracks[23];
        representations = trackInfo.getRepresentations();

        assertEquals(trackInfo.getTrackType(), TrackType.SUBTITLE);
        assertEquals(trackInfo.getMimeType(), "subtitle/grap-text");
        assertEquals(trackInfo.getDurationUs(), 320320000L);
        assertEquals(trackInfo.getLanguage(), "swe");

        assertEquals(representations.length, 1);
    }
}
