/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.parser;

import junit.framework.TestCase;

import com.sonymobile.android.media.internal.MediaParser;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.internal.Util;
import com.sonymobile.android.media.internal.VUParser;

public class VideoUnlimitedUnitTests extends TestCase {

    String sCorrectContentPath = Util.EXTERNAL_DIR + "/MASN2001.mp4";

    public void testCreateFromURI() {
        System.out.println(sCorrectContentPath);
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        assertTrue(mediaParser instanceof VUParser);
    }

    public void testDontCreate() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/serge_and_eno_1.mp4");
        assertFalse(mediaParser instanceof VUParser);
    }

    public void testParse() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        assertTrue(mediaParser.parse());
    }

    public void testRelease() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(sCorrectContentPath);
        mediaParser.release();
    }

}
