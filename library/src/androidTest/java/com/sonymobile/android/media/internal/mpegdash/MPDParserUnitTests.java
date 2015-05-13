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

package com.sonymobile.android.media.internal.mpegdash;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;

import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.mpegdash.MPDParser.Representation;
import com.sonymobile.android.media.internal.mpegdash.MPDParser.SegmentBase;
import com.sonymobile.android.media.internal.mpegdash.MPDParser.SegmentTemplate;

public class MPDParserUnitTests extends TestCase {

    public void testDASHIF_SRMT_TestVector1() {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "  xmlns=\"urn:mpeg:dash:schema:mpd:2011\"\n"
                + "  xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\"\n"
                + "  type=\"static\"\n"
                + "  mediaPresentationDuration=\"PT654S\"\n"
                + "  minBufferTime=\"PT2S\"\n"
                + "  profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\">\n"
                + "\n"
                + "  <BaseURL>http://dash.edgesuite.net/dash264/TestCases/1a/netflix/</BaseURL>\n"
                + "  <Period>\n"
                + "    <!-- English Audio -->\n"
                + "    <AdaptationSet mimeType=\"audio/mp4\" codecs=\"mp4a.40.5\" lang=\"en\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <Representation id=\"1\" bandwidth=\"64000\">\n"
                + "        <BaseURL>ElephantsDream_AAC48K_064.mp4.dash</BaseURL>\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "    <!-- Video -->\n"
                + "    <AdaptationSet mimeType=\"video/mp4\" codecs=\"avc1.42401E\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <Representation id=\"2\" bandwidth=\"100000\" width=\"480\" height=\"360\">\n"
                + "        <BaseURL>ElephantsDream_H264BPL30_0100.264.dash</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation id=\"3\" bandwidth=\"175000\" width=\"480\" height=\"360\">\n"
                + "        <BaseURL>ElephantsDream_H264BPL30_0175.264.dash</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation id=\"4\" bandwidth=\"250000\" width=\"480\" height=\"360\">\n"
                + "        <BaseURL>ElephantsDream_H264BPL30_0250.264.dash</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation id=\"5\" bandwidth=\"500000\" width=\"480\" height=\"360\">\n"
                + "        <BaseURL>ElephantsDream_H264BPL30_0500.264.dash</BaseURL>\n"
                + "      </Representation>\n" + "    </AdaptationSet>\n" + "  </Period>\n"
                + "</MPD>\n\n";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 654000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 2000000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 480, representation.width);
        assertEquals("Wrong height", 360, representation.height);

        SegmentBase segmentBase = representation.segmentBase;

        assertNotNull("No segmentBase", segmentBase);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1a/netflix/ElephantsDream_H264BPL30_0100.264.dash",
                segmentBase.url);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector2() {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" maxSubsegmentDuration=\"PT5.0S\" mediaPresentationDuration=\"PT9M57S\" minBufferTime=\"PT5.0S\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011,http://xmlns.sony.net/metadata/mpeg/dash/profile/senvu/2012\" type=\"static\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\">\n"
                + "  <Period duration=\"PT9M57S\" id=\"P1\">\n"
                + "    <!-- Adaptation Set for main audio -->\n"
                + "    <AdaptationSet audioSamplingRate=\"48000\" codecs=\"mp4a.40.5\" contentType=\"audio\" group=\"2\" id=\"2\" lang=\"en\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\n"
                + "      <Representation bandwidth=\"64000\" id=\"2_1\">\n"
                + "        <BaseURL>DASH_vodaudio_Track5.m4a</BaseURL>\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "    <!-- Adaptation Set for video -->\n"
                + "    <AdaptationSet codecs=\"avc1.4D401E\" contentType=\"video\" frameRate=\"24000/1001\" group=\"1\" id=\"1\" maxBandwidth=\"1609728\" maxHeight=\"480\" maxWidth=\"854\" maximumSAPPeriod=\"5.0\" mimeType=\"video/mp4\" minBandwidth=\"452608\" minHeight=\"480\" minWidth=\"854\" par=\"16:9\" sar=\"1:1\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\n"
                + "      <Representation bandwidth=\"1005568\" height=\"480\" id=\"1_1\" mediaStreamStructureId=\"1\" width=\"854\">\n"
                + "        <BaseURL>DASH_vodvideo_Track2.m4v</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation bandwidth=\"1609728\" height=\"480\" id=\"1_2\" mediaStreamStructureId=\"1\" width=\"854\">\n"
                + "        <BaseURL>DASH_vodvideo_Track1.m4v</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation bandwidth=\"704512\" height=\"480\" id=\"1_3\" mediaStreamStructureId=\"1\" width=\"854\">\n"
                + "        <BaseURL>DASH_vodvideo_Track3.m4v</BaseURL>\n"
                + "      </Representation>\n"
                + "      <Representation bandwidth=\"452608\" height=\"480\" id=\"1_4\" mediaStreamStructureId=\"1\" width=\"854\">\n"
                + "        <BaseURL>DASH_vodvideo_Track4.m4v</BaseURL>\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "  </Period>\n"
                + "</MPD>\n";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1a/sony/SNE_DASH_SD_CASE1A_REVISED.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 597000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 5000000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 3, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 854, representation.width);
        assertEquals("Wrong height", 480, representation.height);

        SegmentBase segmentBase = representation.segmentBase;

        assertNotNull("No segmentBase", segmentBase);

        assertEquals("Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1a/sony/DASH_vodvideo_Track4.m4v",
                segmentBase.url);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector3() {
        String mpd = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- MPD file Generated with GPAC version 0.5.1-DEV-rev4862M  on 2013-10-24T08:21:31Z-->\n"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" minBufferTime=\"PT5S\" type=\"static\" mediaPresentationDuration=\"PT0H9M54.00S\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011, http://dashif.org/guidelines/dash264\">\n"
                + "  <ProgramInformation moreInformationURL=\"http://gpac.sourceforge.net\">\n"
                + "    <Title>/home/elkhatib/Documents/dash264/TestCases/1a/qualcomm/1/MultiRate.mpd generated by GPAC</Title>\n"
                + "  </ProgramInformation>\n"
                + "  <Period id=\"\" duration=\"PT0H9M54.00S\">\n"
                + "    <AdaptationSet segmentAlignment=\"true\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"24\" par=\"16:9\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <Representation id=\"1\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"1200000\">\n"
                + "        <BaseURL>BBB_720_1M_video_init.mp4</BaseURL>\n"
                + "        <SegmentBase indexRangeExact=\"true\" indexRange=\"885-4480\" />\n"
                + "      </Representation>\n"
                + "      <Representation id=\"2\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"2200000\">\n"
                + "        <BaseURL>BBB_720_2M_video_init.mp4</BaseURL>\n"
                + "        <SegmentBase indexRangeExact=\"true\" indexRange=\"885-4480\" />\n"
                + "      </Representation>\n"
                + "      <Representation id=\"3\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"4100000\">\n"
                + "        <BaseURL>BBB_720_4M_video_init.mp4</BaseURL>\n"
                + "        <SegmentBase indexRangeExact=\"true\" indexRange=\"885-4480\" />\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "    <AdaptationSet segmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "      <Representation id=\"4\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.29\" audioSamplingRate=\"48000\" startWithSAP=\"1\" bandwidth=\"33206\">\n"
                + "        <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\" />\n"
                + "        <BaseURL>BBB_32k_init.mp4</BaseURL>\n"
                + "        <SegmentBase indexRangeExact=\"true\" indexRange=\"820-4487\" />\n"
                + "      </Representation>\n" + "    </AdaptationSet>\n" + "  </Period>\n"
                + "</MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1a/qualcomm/1/MultiRate.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 594000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 5000000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 1280, representation.width);
        assertEquals("Wrong height", 720, representation.height);

        SegmentBase segmentBase = representation.segmentBase;

        assertNotNull("No segmentBase", segmentBase);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1a/qualcomm/1/BBB_720_1M_video_init.mp4",
                segmentBase.url);

        assertEquals("Wrong sidx offset", 885, segmentBase.sidxOffset);
        assertEquals("Wrong sidx size", 4480, segmentBase.sidxSize);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector4() {
        String mpd = "<?xml version=\"1.0\"?>\n"
                + "<!-- MPD file Generated with GPAC version 0.5.1-DEV-rev4862M  on 2013-10-24T08:31:50Z-->\n"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" minBufferTime=\"PT1.500000S\" type=\"static\" mediaPresentationDuration=\"PT0H10M54.00S\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\">\n"
                + " <ProgramInformation moreInformationURL=\"http://gpac.sourceforge.net\">\n"
                + "  <Title>/home/elkhatib/Documents/dash264/TestCases/1a/qualcomm/2/MultiRate.mpd generated by GPAC</Title>\n"
                + " </ProgramInformation>\n"
                + "\n"
                + " <Period id=\"\" duration=\"PT0H10M54.00S\">\n"
                + "  <AdaptationSet segmentAlignment=\"true\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"24\" par=\"16:9\" subsegmentStartsWithSAP=\"1\">\n"
                + "   <Representation id=\"1\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"927346\">\n"
                + "    <BaseURL>ED_720_1M_MPEG2_video_init.mp4</BaseURL>\n"
                + "    <SegmentBase indexRangeExact=\"true\" indexRange=\"885-2488\"/>\n"
                + "   </Representation>\n"
                + "   <Representation id=\"2\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"1865574\">\n"
                + "    <BaseURL>ED_720_2M_MPEG2_video_init.mp4</BaseURL>\n"
                + "    <SegmentBase indexRangeExact=\"true\" indexRange=\"885-2488\"/>\n"
                + "   </Representation>\n"
                + "   <Representation id=\"3\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"3750026\">\n"
                + "    <BaseURL>ED_720_4M_MPEG2_video_init.mp4</BaseURL>\n"
                + "    <SegmentBase indexRangeExact=\"true\" indexRange=\"885-2488\"/>\n"
                + "   </Representation>\n"
                + "  </AdaptationSet>\n"
                + "  <AdaptationSet segmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n"
                + "   <Representation id=\"4\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.29\" audioSamplingRate=\"48000\" startWithSAP=\"1\" bandwidth=\"32937\">\n"
                + "    <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n"
                + "    <BaseURL>ED_MPEG2_32k_init.mp4</BaseURL>\n"
                + "    <SegmentBase indexRangeExact=\"true\" indexRange=\"820-2435\"/>\n"
                + "   </Representation>\n" + "  </AdaptationSet>\n" + " </Period>\n" + "</MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1a/qualcomm/2/MultiRate.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 654000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 1500000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 1280, representation.width);
        assertEquals("Wrong height", 720, representation.height);

        SegmentBase segmentBase = representation.segmentBase;

        assertNotNull("No segmentBase", segmentBase);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1a/qualcomm/2/ED_720_1M_MPEG2_video_init.mp4",
                segmentBase.url);

        assertEquals("Wrong sidx offset", 885, segmentBase.sidxOffset);
        assertEquals("Wrong sidx size", 2488, segmentBase.sidxSize);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector5() {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\" type=\"static\" mediaPresentationDuration=\"PT25.959S\" availabilityStartTime=\"2012-12-17T15:56:16Z\" maxSegmentDuration=\"PT2.080S\" minBufferTime=\"PT2.001S\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\"><Period id=\"0\"><AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\" maxWidth=\"640\" maxHeight=\"360\" maxFrameRate=\"30000/1001\" par=\"16:9\"><SegmentTemplate presentationTimeOffset=\"9498\" timescale=\"90000\" initialization=\"seg_$RepresentationID$_init.m4s\" media=\"seg_$RepresentationID$_n$Number$.m4s\" duration=\"180000\" startNumber=\"0\"/><Representation id=\"b2000k_v\" width=\"640\" height=\"360\" frameRate=\"30000/1001\" sar=\"1:1\" scanType=\"progressive\" bandwidth=\"2000000\" codecs=\"avc1.4D401E\"/><Representation id=\"b1000k_v\" width=\"640\" height=\"360\" frameRate=\"30000/1001\" sar=\"1:1\" scanType=\"progressive\" bandwidth=\"1000000\" codecs=\"avc1.4D401E\"/></AdaptationSet><AdaptationSet mimeType=\"audio/mp4\" lang=\"en\" segmentAlignment=\"true\" startWithSAP=\"1\"><SegmentTemplate presentationTimeOffset=\"2533\" timescale=\"24000\" initialization=\"seg_$RepresentationID$_init.m4s\" media=\"seg_$RepresentationID$_n$Number$.m4s\" duration=\"48000\" startNumber=\"0\"/><Representation id=\"a\" audioSamplingRate=\"24000\" bandwidth=\"64000\" codecs=\"mp4a.40.5\"><AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/></Representation></AdaptationSet></Period></MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1b/envivio/manifest.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 25959000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 2001000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 640, representation.width);
        assertEquals("Wrong height", 360, representation.height);

        SegmentTemplate segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1b/envivio/seg_$RepresentationID$_n$Number$.m4s",
                segmentTemplate.media);

        assertEquals("Wrong start number", 0, segmentTemplate.startNumber);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector6() {
        String mpd = "<?xml version=\"1.0\"?>\n"
                + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "  xmlns=\"urn:mpeg:dash:schema:mpd:2011\"\n"
                + "  type=\"static\"\n"
                + "  minBufferTime=\"PT2S\"\n"
                + "  profiles=\"urn:mpeg:dash:profile:isoff-live:2011\"\n"
                + "  mediaPresentationDuration=\"PT188S\">\n"
                + "  <Period>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"25\" par=\"16:9\">\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23821645\" media=\"video_$Number$_$Bandwidth$bps.mp4\" initialization=\"video_$Bandwidth$bps.mp4\">\n"
                + "      </SegmentTemplate>\n"
                + "      <Representation id=\"v0\" codecs=\"avc3.4d401f\" width=\"1280\" height=\"720\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"4000000\"/>\n"
                + "      <Representation id=\"v1\" codecs=\"avc3.4d401f\" width=\"1280\" height=\"720\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"2500000\"/>\n"
                + "    </AdaptationSet>\n"
                + "    <AdaptationSet mimeType=\"audio/mp4\" codecs=\"mp4a.40.5\" audioSamplingRate=\"48000\" lang=\"fr\" segmentAlignment=\"true\" startWithSAP=\"1\">\n"
                + "        <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n"
                + "        <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\"/>\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23821645\" media=\"audio_$Number$_$Bandwidth$bps_Input_2.mp4\" initialization=\"audio_$Bandwidth$bps_Input_2.mp4\">\n"
                + "      </SegmentTemplate>\n"
                + "      <Representation id=\"a2\" bandwidth=\"96000\"/>\n"
                + "    </AdaptationSet>\n" + "  </Period>\n" + "</MPD>\n";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1b/envivio/manifest.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 188000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 2000000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 1280, representation.width);
        assertEquals("Wrong height", 720, representation.height);

        SegmentTemplate segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1b/envivio/video_$Number$_$Bandwidth$bps.mp4",
                segmentTemplate.media);

        assertEquals("Wrong start number", 23821645, segmentTemplate.startNumber);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector8() {
        String mpd = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- MPD file Generated with GPAC version 0.5.1-DEV-rev4736M  on 2013-09-19T15:26:43Z-->\n"
                + "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" minBufferTime=\"PT1.500000S\" type=\"static\" mediaPresentationDuration=\"PT0H9M54.00S\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\">\n"
                + "  <ProgramInformation moreInformationURL=\"http://gpac.sourceforge.net\">\n"
                + "    <Title>/home/elkhatib/Documents/dash264/TestCases/1b/qualcomm/1_BBB_2Sec_MainProf/MultiRate.mpd generated by GPAC</Title>\n"
                + "  </ProgramInformation>\n"
                + "  <Period id=\"\" duration=\"PT0H9M54.00S\">\n"
                + "    <AdaptationSet segmentAlignment=\"true\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"24\" par=\"16:9\">\n"
                + "      <Representation id=\"1\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"980104\">\n"
                + "        <SegmentTemplate timescale=\"12288\" duration=\"24576\" media=\"BBB_720_1M_video_$Number$.mp4\" startNumber=\"1\" initialization=\"BBB_720_1M_video_init.mp4\" />\n"
                + "      </Representation>\n"
                + "      <Representation id=\"2\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"1950145\">\n"
                + "        <SegmentTemplate timescale=\"12288\" duration=\"24576\" media=\"BBB_720_2M_video_$Number$.mp4\" startNumber=\"1\" initialization=\"BBB_720_2M_video_init.mp4\" />\n"
                + "      </Representation>\n"
                + "      <Representation id=\"3\" mimeType=\"video/mp4\" codecs=\"avc1.4d401f\" width=\"1280\" height=\"720\" frameRate=\"24\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"3893089\">\n"
                + "        <SegmentTemplate timescale=\"12288\" duration=\"24576\" media=\"BBB_720_4M_video_$Number$.mp4\" startNumber=\"1\" initialization=\"BBB_720_4M_video_init.mp4\" />\n"
                + "      </Representation>\n"
                + "    </AdaptationSet>\n"
                + "    <AdaptationSet segmentAlignment=\"true\">\n"
                + "      <Representation id=\"4\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.29\" audioSamplingRate=\"48000\" startWithSAP=\"1\" bandwidth=\"33434\">\n"
                + "        <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\" />\n"
                + "        <SegmentTemplate timescale=\"48000\" duration=\"94175\" media=\"BBB_32k_$Number$.mp4\" startNumber=\"1\" initialization=\"BBB_32k_init.mp4\" />\n"
                + "      </Representation>\n" + "    </AdaptationSet>\n" + "  </Period>\n"
                + "</MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1b/qualcomm/1/MultiRate.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 594000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 1500000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 1280, representation.width);
        assertEquals("Wrong height", 720, representation.height);

        SegmentTemplate segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1b/qualcomm/1/BBB_720_1M_video_$Number$.mp4",
                segmentTemplate.media);

        assertEquals("Wrong start number", 1, segmentTemplate.startNumber);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_SRMT_TestVector10() {
        String mpd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"urn:mpeg:dash:schema:mpd:2011\" xsi:schemaLocation=\"urn:mpeg:dash:schema:mpd:2011 DASH-MPD.xsd\" type=\"static\" mediaPresentationDuration=\"PT25.959S\" availabilityStartTime=\"2012-12-17T15:56:16Z\" maxSegmentDuration=\"PT2.080S\" minBufferTime=\"PT2.001S\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\"><Period id=\"0\"><AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\" maxWidth=\"640\" maxHeight=\"360\" maxFrameRate=\"30000/1001\" par=\"16:9\"><SegmentTemplate presentationTimeOffset=\"9498\" timescale=\"90000\" initialization=\"seg_$RepresentationID$_init.m4s\" media=\"seg_$RepresentationID$_t$Time$.m4s\"><SegmentTimeline><S t=\"12012\" d=\"180180\" r=\"11\"/><S t=\"2174172\" d=\"174174\"/></SegmentTimeline></SegmentTemplate><Representation id=\"b2000k_v\" width=\"640\" height=\"360\" frameRate=\"30000/1001\" sar=\"1:1\" scanType=\"progressive\" bandwidth=\"2000000\" codecs=\"avc1.4D401E\"/><Representation id=\"b1000k_v\" width=\"640\" height=\"360\" frameRate=\"30000/1001\" sar=\"1:1\" scanType=\"progressive\" bandwidth=\"1000000\" codecs=\"avc1.4D401E\"/></AdaptationSet><AdaptationSet mimeType=\"audio/mp4\" lang=\"en\" segmentAlignment=\"true\" startWithSAP=\"1\"><SegmentTemplate presentationTimeOffset=\"2533\" timescale=\"24000\" initialization=\"seg_$RepresentationID$_init.m4s\" media=\"seg_$RepresentationID$_t$Time$.m4s\"><SegmentTimeline><S t=\"2533\" d=\"48128\"/><S t=\"50661\" d=\"47104\"/><S t=\"97765\" d=\"48128\" r=\"9\"/></SegmentTimeline></SegmentTemplate><Representation id=\"a\" audioSamplingRate=\"24000\" bandwidth=\"64000\" codecs=\"mp4a.40.5\"><AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/></Representation></AdaptationSet></Period></MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/1c/envivio/manifest.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 25959000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 2001000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 640, representation.width);
        assertEquals("Wrong height", 360, representation.height);

        SegmentTemplate segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1c/envivio/seg_$RepresentationID$_t$Time$.m4s",
                segmentTemplate.media);

        assertNotNull("No segment timeline found", segmentTemplate.segmentTimeline);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }

    public void testDASHIF_MP_TestVector1() {
        String mpd = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<MPD xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" minBufferTime=\"PT2S\" profiles=\"urn:mpeg:dash:profile:isoff-live:2011\" mediaPresentationDuration=\"PT368S\">\n"
                + "  <Period id=\"0\" duration=\"PT188S\">\n"
                + "    <BaseURL>http://dash.edgesuite.net/dash264/TestCases/1b/thomson-networks/1/</BaseURL>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"25\" par=\"16:9\">\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23821645\" media=\"video_$Number$_$Bandwidth$bps.mp4\" initialization=\"video_$Bandwidth$bps.mp4\"></SegmentTemplate>\n"
                + "      <Representation id=\"v0\" codecs=\"avc3.4d401f\" width=\"1280\" height=\"720\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"4000000\" />\n"
                + "      <Representation id=\"v1\" codecs=\"avc3.4d401f\" width=\"1280\" height=\"720\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"2500000\" />\n"
                + "    </AdaptationSet>\n"
                + "    <AdaptationSet mimeType=\"audio/mp4\" codecs=\"mp4a.40.5\" audioSamplingRate=\"48000\" lang=\"fr\" segmentAlignment=\"true\" startWithSAP=\"1\">\n"
                + "      <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\" />\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\" />\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23821645\" media=\"audio_$Number$_$Bandwidth$bps_Input_2.mp4\" initialization=\"audio_$Bandwidth$bps_Input_2.mp4\"></SegmentTemplate>\n"
                + "      <Representation id=\"a2\" bandwidth=\"96000\" />\n"
                + "    </AdaptationSet>\n"
                + "  </Period>\n"
                + "  <Period id=\"1\" duration=\"PT180S\">\n"
                + "    <BaseURL>http://dash.edgesuite.net/dash264/TestCases/2b/thomson-networks/1/</BaseURL>\n"
                + "    <AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\" maxWidth=\"1280\" maxHeight=\"720\" maxFrameRate=\"25\" par=\"16:9\">\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23601896\" media=\"video_$Number$_$Bandwidth$bps.mp4\" initialization=\"video_$Bandwidth$bps.mp4\"></SegmentTemplate>\n"
                + "      <Representation id=\"v0\" codecs=\"avc3.4d401f\" width=\"1280\" height=\"720\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"3000000\" />\n"
                + "      <Representation id=\"v1\" codecs=\"avc3.4d401e\" width=\"720\" height=\"480\" scanType=\"progressive\" frameRate=\"25\" sar=\"32:27\" bandwidth=\"1500000\" />\n"
                + "      <Representation id=\"v2\" codecs=\"avc3.4d401e\" width=\"640\" height=\"360\" scanType=\"progressive\" frameRate=\"25\" sar=\"1:1\" bandwidth=\"900000\" />\n"
                + "      <Representation id=\"v3\" codecs=\"avc3.4d400d\" width=\"320\" height=\"240\" scanType=\"progressive\" frameRate=\"25\" sar=\"4:3\" bandwidth=\"500000\" />\n"
                + "    </AdaptationSet>\n"
                + "    <AdaptationSet mimeType=\"audio/mp4\" codecs=\"mp4a.40.5\" audioSamplingRate=\"48000\" lang=\"fr\" segmentAlignment=\"true\" startWithSAP=\"1\">\n"
                + "      <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\" />\n"
                + "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"main\" />\n"
                + "      <SegmentTemplate duration=\"2\" startNumber=\"23601896\" media=\"audio_$Number$_$Bandwidth$bps_Input_4.mp4\" initialization=\"audio_$Bandwidth$bps_Input_4.mp4\"></SegmentTemplate>\n"
                + "      <Representation id=\"a4\" bandwidth=\"96000\" />\n"
                + "    </AdaptationSet>\n" + "  </Period>\n" + "</MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://dash.edgesuite.net/dash264/TestCases/5a/1/manifest.mpd");

        assertTrue("Parsing of mpd failed",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));

        assertEquals("Wrong duration", 368000000, mpdParser.getDurationUs());
        assertEquals("Wrong min buffer time", 2000000, mpdParser.getMinBufferTimeUs());

        mpdParser.updateRepresentations(new int[] {0, 0, -1});

        Representation representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 1280, representation.width);
        assertEquals("Wrong height", 720, representation.height);

        SegmentTemplate segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals("Wrong start number", 23821645, segmentTemplate.startNumber);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/1b/thomson-networks/1/video_$Number$_$Bandwidth$bps.mp4",
                segmentTemplate.media);

        assertTrue("Second period not found", mpdParser.hasNextPeriod());

        mpdParser.nextPeriod();

        mpdParser.updateRepresentations(new int[] {0, 3, -1});

        representation = mpdParser.getRepresentation(TrackType.VIDEO);

        assertNotNull("No video representation", representation);

        assertEquals("Wrong width", 320, representation.width);
        assertEquals("Wrong height", 240, representation.height);

        segmentTemplate = representation.segmentTemplate;

        assertNotNull("No segmentTemplate", segmentTemplate);

        assertEquals("Wrong start number", 23601896, segmentTemplate.startNumber);

        assertEquals(
                "Wrong video uri",
                "http://dash.edgesuite.net/dash264/TestCases/2b/thomson-networks/1/video_$Number$_$Bandwidth$bps.mp4",
                segmentTemplate.media);

        assertFalse("Unexpected period found", mpdParser.hasNextPeriod());
    }
}
