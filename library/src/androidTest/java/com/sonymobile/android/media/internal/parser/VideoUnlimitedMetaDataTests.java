/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 * All rights, including trade secret rights, reserved.
 */

package com.sonymobile.android.media.internal.parser;

import junit.framework.TestCase;

import com.sonymobile.android.media.internal.MediaParser;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.MetaDataParserFactory;
import com.sonymobile.android.media.internal.Util;

public class VideoUnlimitedMetaDataTests extends TestCase {

    public void testParseMetaDataTitle() {
        String expectedTitle = "Title MTD_002";
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR + "/MASN2215.MNV");
        assertTrue(mediaParser.parse());
        MetaData metadata = mediaParser.getMetaData();
        String[] titleLangs = metadata.getStringArray(MetaData.KEY_HMMP_TITLE_LANGUAGES);
        assertNotNull(titleLangs);
        assertEquals(titleLangs.length, 2);
        String expectedLanguage = "eng";
        int languageIndex = 0;
        for (; languageIndex < titleLangs.length; languageIndex++) {
            if (expectedLanguage.equalsIgnoreCase(titleLangs[languageIndex])) {
                break;
            }
        }
        assertFalse(languageIndex >= titleLangs.length);
        String retrievedTitle = metadata.getString(MetaData.KEY_HMMP_TITLE, expectedLanguage);
        assertEquals(expectedTitle, retrievedTitle);
    }

    public void testParseMetaDataThumbnail() {
        MediaParser mediaParser = (MediaParser)MetaDataParserFactory.create(Util.EXTERNAL_DIR + "/MASN2217.MNV");
        assertTrue(mediaParser.parse());

        MetaData metadata = mediaParser.getMetaData();
        String[] iconLangs = metadata.getStringArray(MetaData.KEY_HMMP_ICON_LANGUAGES);
        assertNotNull(iconLangs);
        assertEquals(iconLangs.length, 1);
        String expectedLanguage = "und";
        int languageIndex = 0;
        for (; languageIndex < iconLangs.length; languageIndex++) {
            if (expectedLanguage.equalsIgnoreCase(iconLangs[languageIndex])) {
                break;
            }
        }
        assertFalse(languageIndex >= iconLangs.length);
        byte[] iconData = metadata.getByteBuffer(MetaData.KEY_HMMP_ICON, expectedLanguage);
        assertNotNull(iconData);
        assertEquals(iconData.length, 36581);
        String s = "89504E470D0A1A0A0000000D49484452000000A0000000780802000000FAC06E380000000173524"
                + "74200AECE1CE9000000097048597300000B1300000B1301009A9C180000000774494D4507D70C0B";
        int len = s.length();
        byte[] iconStart = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            iconStart[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4) + Character.digit(
                    s.charAt(i + 1), 16));
        }
        for (int i = 0; i < iconStart.length; i++) {
            assertEquals(iconStart[i], iconData[i]);
        }
    }
}
