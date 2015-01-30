/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.parser;

import junit.framework.TestCase;

import com.sonymobile.android.media.internal.MediaParser;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.internal.ISOBMFFParser;
import com.sonymobile.android.media.internal.Util;

public class ISOBMFFUnitTests extends TestCase {

    String sCorrectContentPath = Util.EXTERNAL_DIR + "/serge_and_eno_1.mp4";

    public void testCreateFromURI() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        assertTrue(mediaParser instanceof ISOBMFFParser);
    }

    public void testDontCreate() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/WEBM_540x360_486Kbps_23Fps_elephants-dream.webm");
        assertFalse(mediaParser instanceof ISOBMFFParser);
    }

    public void testParse() {
        System.out.println(sCorrectContentPath);
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        assertTrue(mediaParser.parse());
    }

    public void testRelease() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        mediaParser.release();
    }

}
