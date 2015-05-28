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

package com.sonymobile.android.media.internal.streaming.mpegdash;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import junit.framework.TestCase;

import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Representation;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.SegmentBase;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.SegmentTemplate;

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

    public void testUnsupported_SegmentList() {
        String mpd = "<!--\n" +
                " MPD file Generated with GPAC version 0.5.1-DEV-rev4193 \n" +
                "-->\n" +
                "<MPD xmlns=\"urn:mpeg:DASH:schema:MPD:2011\" type=\"static\" minBufferTime=\"PT1.5S\" mediaPresentationDuration=\"PT0H10M0.00S\" profiles=\"urn:mpeg:dash:profile:isoff-main:2011\">\n" +
                "<ProgramInformation moreInformationURL=\"http://gpac.sourceforge.net\">\n" +
                "<Title>mp4-main-multi-mpd-AV-NBS.mpd generated by GPAC</Title>\n" +
                "<Copyright>TelecomParisTech(c)2012</Copyright>\n" +
                "</ProgramInformation>\n" +
                "<Period start=\"PT0S\" duration=\"PT0H10M0.00S\">\n" +
                "<AdaptationSet segmentAlignment=\"true\" maxWidth=\"1920\" maxHeight=\"1080\" maxFrameRate=\"25\" par=\"16:9\">\n" +
                "<ContentComponent id=\"1\" contentType=\"video\"/>\n" +
                "<Representation id=\"h264bl_low\" mimeType=\"video/mp4\" codecs=\"avc1.42c00d\" width=\"320\" height=\"180\" frameRate=\"25\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"50877\">\n" +
                "<SegmentList timescale=\"1000\" duration=\"10000\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-h264bl_low-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_low-60.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "<Representation id=\"h264bl_mid\" mimeType=\"video/mp4\" codecs=\"avc1.42c01e\" width=\"640\" height=\"360\" frameRate=\"25\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"194870\">\n" +
                "<SegmentList timescale=\"1000\" duration=\"10000\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-h264bl_mid-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_mid-60.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "<Representation id=\"h264bl_hd\" mimeType=\"video/mp4\" codecs=\"avc1.42c01f\" width=\"1280\" height=\"720\" frameRate=\"25\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"514828\">\n" +
                "<SegmentList timescale=\"1000\" duration=\"10000\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-h264bl_hd-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_hd-60.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "<Representation id=\"h264bl_full\" mimeType=\"video/mp4\" codecs=\"avc1.42c028\" width=\"1920\" height=\"1080\" frameRate=\"25\" sar=\"1:1\" startWithSAP=\"1\" bandwidth=\"770699\">\n" +
                "<SegmentList timescale=\"1000\" duration=\"10000\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-h264bl_full-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-h264bl_full-60.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "</AdaptationSet>\n" +
                "<AdaptationSet segmentAlignment=\"true\" lang=\"und\">\n" +
                "<ContentComponent id=\"1\" contentType=\"audio\" lang=\"und\"/>\n" +
                "<Representation id=\"aaclc_low\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.2\" audioSamplingRate=\"44100\" lang=\"und\" startWithSAP=\"1\" bandwidth=\"19079\">\n" +
                "<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"1\"/>\n" +
                "<SegmentList timescale=\"1000\" duration=\"9520\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-aaclc_low-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-60.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-61.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-62.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-63.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_low-64.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "<Representation id=\"aaclc_high\" mimeType=\"audio/mp4\" codecs=\"mp4a.40.2\" audioSamplingRate=\"44100\" lang=\"und\" startWithSAP=\"1\" bandwidth=\"66378\">\n" +
                "<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"1\"/>\n" +
                "<SegmentList timescale=\"1000\" duration=\"9520\">\n" +
                "<Initialization sourceURL=\"mp4-main-multi-aaclc_high-.mp4\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-1.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-2.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-3.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-4.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-5.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-6.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-7.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-8.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-9.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-10.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-11.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-12.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-13.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-14.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-15.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-16.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-17.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-18.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-19.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-20.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-21.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-22.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-23.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-24.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-25.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-26.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-27.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-28.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-29.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-30.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-31.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-32.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-33.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-34.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-35.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-36.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-37.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-38.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-39.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-40.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-41.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-42.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-43.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-44.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-45.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-46.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-47.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-48.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-49.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-50.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-51.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-52.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-53.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-54.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-55.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-56.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-57.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-58.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-59.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-60.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-61.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-62.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-63.m4s\"/>\n" +
                "<SegmentURL media=\"mp4-main-multi-aaclc_high-64.m4s\"/>\n" +
                "</SegmentList>\n" +
                "</Representation>\n" +
                "</AdaptationSet>\n" +
                "</Period>\n" +
                "</MPD>";

        MPDParser mpdParser = new MPDParser(
                "http://www.digitalprimates.net/dash/streams/gpac/mp4-main-multi-mpd-AV-NBS.mpd");

        assertFalse("Parsing of mpd did not fail as expected",
                mpdParser.parse(new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8))));
    }
}
