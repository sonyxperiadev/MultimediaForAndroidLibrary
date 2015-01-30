/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.dash;

import junit.framework.TestCase;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.DASHTrackInfo;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;


public class DASHTrackInfoFunctionalTests extends TestCase {

    final String contentPath = "http://www.gte.nu/users/streaming/dash/envivio/av/Manifest.mpd";

    public void testGetTrackInfo() {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(contentPath);
            mp.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }

        TrackInfo[] tracks = mp.getTrackInfo();
        int trackCount = tracks.length;
        assertEquals(trackCount, 2);

        for (int i = 0; i < trackCount; i++) {
            DASHTrackInfo track = (DASHTrackInfo)tracks[i];
            TrackRepresentation[] representations = track.getRepresentations();

            if (track.getTrackType() == TrackType.AUDIO) {
                assertEquals(track.getMimeType(), "audio/mp4");
                assertEquals(track.getDurationUs(), 260266000L);
                assertEquals(track.getLanguage(), "en");
                assertEquals(track.getStartTimeUs(), 0L);

                assertEquals(representations.length, 1);

                AudioTrackRepresentation audioRepresentation =
                        (AudioTrackRepresentation)representations[0];
                assertEquals(audioRepresentation.getBitrate(), 56000);
                assertEquals(audioRepresentation.getChannelCount(), 2);
                assertEquals(audioRepresentation.getChannelConfiguration(), "2");
                assertEquals(audioRepresentation.getSampleRate(), 48000);

            } else if (track.getTrackType() == TrackType.VIDEO) {
                assertEquals(track.getMimeType(), "video/mp4");
                assertEquals(track.getDurationUs(), 260266000L);
                assertEquals(track.getLanguage(), "und");
                assertEquals(track.getStartTimeUs(), 0L);

                assertEquals(representations.length, 5);

                VideoTrackRepresentation videoRepresentation =
                        (VideoTrackRepresentation)representations[0];
                assertEquals(3000000, videoRepresentation.getBitrate());
                assertEquals(1280, videoRepresentation.getWidth());
                assertEquals(720, videoRepresentation.getHeight());
                assertEquals(25, videoRepresentation.getFrameRate(), 0);

                videoRepresentation =
                        (VideoTrackRepresentation)representations[4];
                assertEquals(349952, videoRepresentation.getBitrate());
                assertEquals(320, videoRepresentation.getWidth());
                assertEquals(180, videoRepresentation.getHeight());
                assertEquals(25, videoRepresentation.getFrameRate(), 0);
            }
        }
    }
}
