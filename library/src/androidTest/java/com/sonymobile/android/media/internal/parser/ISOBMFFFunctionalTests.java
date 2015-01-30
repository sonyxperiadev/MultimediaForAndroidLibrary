/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.parser;

import junit.framework.TestCase;

import android.media.MediaFormat;

import com.sonymobile.android.media.internal.MediaParser;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Util;

public class ISOBMFFFunctionalTests extends TestCase {

    String sCorrectContentPath = Util.EXTERNAL_DIR + "/serge_and_eno_1.mp4";

    public void testGetDurationUs() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        long durationUs = mediaParser.getDurationUs();
        assertEquals(durationUs, 70720000);
    }

    public void testGetFormat() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        MediaFormat audioFormat = mediaParser.getFormat(TrackType.AUDIO);
        assertNotNull(audioFormat);
    }

    public void testGetTrackCount() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        int trackCount = mediaParser.getTrackCount();
        assertEquals(trackCount, 2);
    }

    public void testGetTrackMediaFormat() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        MediaFormat trackFormat = mediaParser.getTrackMediaFormat(0);
        assertNotNull(trackFormat);
    }

    public void testDequeueAccessUnit() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        AccessUnit accessUnit = mediaParser.dequeueAccessUnit(TrackType.AUDIO);
        assertNotNull(accessUnit);
    }

    public void testSeekTo() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        if (!mediaParser.parse()) {
            assertTrue(false);
        }
        mediaParser.seekTo(30000000);
        AccessUnit accessUnit = mediaParser.dequeueAccessUnit(TrackType.AUDIO);
        assertTrue(accessUnit.timeUs > 10000000);
    }
}
