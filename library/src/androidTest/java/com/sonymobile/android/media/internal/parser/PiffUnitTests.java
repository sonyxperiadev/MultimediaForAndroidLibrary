/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.parser;

import junit.framework.TestCase;

import com.sonymobile.android.media.internal.MediaParser;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.internal.PiffParser;
import com.sonymobile.android.media.internal.Util;

public class PiffUnitTests extends TestCase {

    public void testCreatePiff() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/SuperSpeedway_720_230.ismv");
        assertTrue(mediaParser instanceof PiffParser);
    }

    public void testCreateNotPiff() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR + "/MASN2215.MNV");
        assertFalse(mediaParser instanceof PiffParser);
    }

    public void testParsePiffv1_1() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/SuperSpeedway_720_230.ismv");
        assertTrue(mediaParser.parse());
    }

    public void testParseProtectedPiffv1_1() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/SuperSpeedway_720_230_encryption.ismv");
        assertTrue(mediaParser.parse());
    }

    public void testParsePiffv1_3() {
        // TODO: Make sure this is actually 1.3 content. It's from
        // SmoothStreaming test server, so should be OK, but need to make sure
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/The_Hobbit_Trailer_48fps_200.ismv");
        assertTrue(mediaParser.parse());
    }

    public void testParseProtectedPiffv1_3() {
        // TODO: Make sure this is actually 1.3 content. It's from
        // SmoothStreaming test server, so should be OK, but need to make sure
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/The_Hobbit_Trailer_48fps_200_protected.ismv");
        assertTrue(mediaParser.parse());
    }

    public void testRelease() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR
                + "/SuperSpeedway_720_230.ismv");
        mediaParser.release();
    }

}
