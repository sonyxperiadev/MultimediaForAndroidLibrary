/*
 * Copyright (C) 2015 Sony Mobile Communications Inc.
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

package com.sonymobile.android.media.internal.streaming.smoothstreaming;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MimeType;
import com.sonymobile.android.media.internal.Util;
import com.sonymobile.android.media.internal.drm.MsDrmSession;
import com.sonymobile.android.media.internal.streaming.common.ParseException;

public class ManifestParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "ManifestParser";

    private long mDurationUs = -1;

    private final ArrayList<StreamIndex> mStreamIndexes = new ArrayList<>();

    private final int[] mActiveStreamIndexes = new int[TrackType.UNKNOWN.ordinal()];

    private StreamIndex mCurrentStreamIndex;

    private QualityLevel mCurrentQualityLevel;

    private final String mBaseUri;

    private long mTimeScale = 10000000;

    private Protection mProtection;

    public ManifestParser(String baseUri) {
        mBaseUri = baseUri.substring(0, baseUri.lastIndexOf('/') + 1);

        mActiveStreamIndexes[TrackType.AUDIO.ordinal()] = -1;
        mActiveStreamIndexes[TrackType.VIDEO.ordinal()] = -1;
        mActiveStreamIndexes[TrackType.SUBTITLE.ordinal()] = -1;
    }

    public QualityLevel getQualityLevel(TrackType type) {
        if (type != TrackType.UNKNOWN) {
            int selectedStreamIndex = mActiveStreamIndexes[type.ordinal()];

            if (selectedStreamIndex > -1) {
                StreamIndex streamIndex = mStreamIndexes.get(selectedStreamIndex);

                if (streamIndex.activeQualityLevel > -1) {
                    return streamIndex.qualityLevels.get(streamIndex.activeQualityLevel);
                }
            }
        }

        return null;
    }

    public boolean parse(InputStream in) {
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("SmoothStreamingMedia")) {
                        handleSmoothStreamingMedia(parser);
                    } else if (parser.getName().equals("StreamIndex")) {
                        handleStreamIndex(parser);
                    } else if (parser.getName().equals("QualityLevel")) {
                        handleQualityLevel(parser);
                    } else if (parser.getName().equals("c")) {
                        handleFragmentEntry(parser);
                    } else if (parser.getName().equals("ProtectionHeader")) {
                        handleProtectionHeader(parser);
                    }
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("StreamIndex")) {
                        endStreamIndex(parser);
                    } else if (parser.getName().equals("QualityLevel")) {
                        endQualityLevel(parser);
                    }
                }
            }

            return mCurrentStreamIndex == null && mCurrentQualityLevel == null;
        } catch (XmlPullParserException e) {
            if (LOGS_ENABLED) Log.e(TAG, "XmlPullParserException during parse", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException during parse", e);
            return false;
        } catch (ParseException e) {
            if (LOGS_ENABLED) Log.e(TAG, "ParseException", e);
            return false;
        } catch (NumberFormatException e) {
            if (LOGS_ENABLED) Log.e(TAG, "NumberFormatException during parse", e);
            return false;
        }
    }

    private void handleSmoothStreamingMedia(XmlPullParser parser) {
        String value = parser.getAttributeValue(null, "TimeScale");

        if (value != null) {
            mTimeScale = Long.parseLong(value);
        }

        mDurationUs = Long.parseLong(parser.getAttributeValue(null, "Duration"))
                * 1000000L / mTimeScale;


    }

    private void handleStreamIndex(XmlPullParser parser) {
        mCurrentStreamIndex = new StreamIndex();

        String value = parser.getAttributeValue(null, "Type");
        if (value != null) {
            if (value.equals("audio")) {
                mCurrentStreamIndex.type = TrackType.AUDIO;
            } else if (value.equals("video")) {
                mCurrentStreamIndex.type = TrackType.VIDEO;
            } else if (value.equals("text")) {
                mCurrentStreamIndex.type = TrackType.SUBTITLE;
            }
        }

        mCurrentStreamIndex.url = mBaseUri + parser.getAttributeValue(null, "Url");

        mCurrentStreamIndex.fragments = new ArrayList<>();

        value =  parser.getAttributeValue(null, "TimeScale");

        if (value != null) {
            mCurrentStreamIndex.timeScale = Long.parseLong(value);
        } else {
            mCurrentStreamIndex.timeScale = mTimeScale;
        }

        value = parser.getAttributeValue(null, "Language");

        if (value != null) {
            mCurrentStreamIndex.language = value;
        }
    }

    private void endStreamIndex(XmlPullParser parser) {
        if ((mCurrentStreamIndex.type == TrackType.AUDIO ||
                mCurrentStreamIndex.type == TrackType.VIDEO) &&
                mActiveStreamIndexes[mCurrentStreamIndex.type.ordinal()] == -1) {

            mActiveStreamIndexes[mCurrentStreamIndex.type.ordinal()] = mStreamIndexes.size();
        }

        if (!mCurrentStreamIndex.qualityLevels.isEmpty()) {
            mStreamIndexes.add(mCurrentStreamIndex);
        } else {
            if (LOGS_ENABLED) Log.w(TAG, "Unsupported StreamIndex skipped");
        }

        mCurrentStreamIndex = null;
    }

    private void handleQualityLevel(XmlPullParser parser) {
        String value;
        switch (mCurrentStreamIndex.type) {
            case AUDIO:
                mCurrentQualityLevel = new AudioQualityLevel();
                value = parser.getAttributeValue(null, "SamplingRate");
                if (value == null) {
                    throw new ParseException("Missing sample rate");
                }
                ((AudioQualityLevel) mCurrentQualityLevel).sampleRate =
                        Integer.parseInt(value);

                value = parser.getAttributeValue(null, "Channels");
                if (value == null) {
                    throw new ParseException("Missing channel count");
                }
                ((AudioQualityLevel) mCurrentQualityLevel).channels =
                        Integer.parseInt(value);
                break;
            case VIDEO:
                mCurrentQualityLevel = new VideoQualityLevel();
                value = parser.getAttributeValue(null, "MaxWidth");
                if (value == null) {
                    throw new ParseException("Missing MaxWidth");
                }
                ((VideoQualityLevel)mCurrentQualityLevel).width =
                        Integer.parseInt(value);

                value = parser.getAttributeValue(null, "MaxHeight");
                if (value == null) {
                    throw new ParseException("Missing MaxHeight");
                }
                ((VideoQualityLevel) mCurrentQualityLevel).height =
                        Integer.parseInt(value);

                value = parser.getAttributeValue(null, "NALUnitLengthField");
                if (value != null) {
                    ((VideoQualityLevel)mCurrentQualityLevel).NALLengthSize =
                            Integer.parseInt(value);
                }
                break;
            default:
                mCurrentQualityLevel = new QualityLevel();
                break;
        }
        mCurrentQualityLevel.streamIndex = mCurrentStreamIndex;

        mCurrentQualityLevel.bitrate = Integer.parseInt(parser.getAttributeValue(null, "Bitrate"));
        mCurrentQualityLevel.fourCC = parser.getAttributeValue(null, "FourCC");
        mCurrentQualityLevel.mime = fourCC2Mime(mCurrentQualityLevel.fourCC);
        mCurrentQualityLevel.cpd = parser.getAttributeValue(null, "CodecPrivateData");

        if (mCurrentQualityLevel.cpd.isEmpty() &&
                mCurrentQualityLevel.fourCC.equalsIgnoreCase("AACL")) {
            mCurrentQualityLevel.cpd =
                    Util.bytesToHex(
                            makeAACCSD(((AudioQualityLevel) mCurrentQualityLevel).sampleRate,
                                ((AudioQualityLevel) mCurrentQualityLevel).channels));
        }

        if (mCurrentQualityLevel.mime != null) {
            mCurrentStreamIndex.qualityLevels.add(mCurrentQualityLevel);
        } else {
            if (LOGS_ENABLED) Log.w(TAG, "Unsupported QualityLevel skipped");
        }
    }

    private static byte[] makeAACCSD(int sampleRate, int channels) {
        final int[] samplingFreq = new int[]{
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        byte[] csd = new byte[2];

        byte profile = 2; //AAC LC
        int samplingFreqIndex = -1;
        for (int i = 0; i < samplingFreq.length; i++) {
            if (samplingFreq[i] == sampleRate) {
                samplingFreqIndex = i;
                break;
            }
        }

        if (samplingFreqIndex == -1) {
            if (LOGS_ENABLED) Log.w(TAG, "Sample rate not defined");
        }

        csd[0] = (byte) ((profile << 3) & 0xF8 | (samplingFreqIndex >> 1) & 0x07);
        csd[1] = (byte) ((samplingFreqIndex << 7) & 0x80 | (channels << 3) & 0x78);

        return csd;
    }

    private static String fourCC2Mime(String fourCC) {
        if (fourCC.equalsIgnoreCase("H264") || fourCC.equalsIgnoreCase("AVC1")) {
            return MimeType.AVC;
        } else if (fourCC.equalsIgnoreCase("AACL")) {
            return MimeType.AAC;
        } else if (fourCC.equalsIgnoreCase("WVC1")) {
            return MimeType.VC1;
        } else if (fourCC.equalsIgnoreCase("WMAP")) {
            return MimeType.WMA;
        } else if (fourCC.equalsIgnoreCase("TTML")) {
            return MimeType.TTML;
        }

        return null;
    }

    private void endQualityLevel(XmlPullParser parser) {
        mCurrentQualityLevel = null;
    }

    private void handleFragmentEntry(XmlPullParser parser) {
        FragmentEntry entry = new FragmentEntry();
        entry.durationTicks = Long.parseLong(parser.getAttributeValue(null, "d"));

        String value = parser.getAttributeValue(null, "t");

        if (value != null) {
            entry.timeTicks = Long.parseLong(value);
        } else {
            if (mCurrentStreamIndex.fragments.isEmpty()) {
                entry.timeTicks = 0;
            } else {
                FragmentEntry lastEntry =
                        mCurrentStreamIndex.fragments.get(mCurrentStreamIndex.fragments.size() - 1);

                entry.timeTicks =
                        lastEntry.timeTicks + lastEntry.repeat * lastEntry.durationTicks;
            }
        }

        value = parser.getAttributeValue(null, "r");

        if (value != null) {
            entry.repeat = Integer.parseInt(value);
        }

        mCurrentStreamIndex.fragments.add(entry);
    }

    private void handleProtectionHeader(XmlPullParser parser) {

        mProtection = new Protection();

        String systemID = parser.getAttributeValue(null, "SystemID");

        systemID = systemID.replace("-", "");

        mProtection.uuid = Util.uuidStringToByteArray(systemID);

        try {
            parser.next();
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mProtection.content = Base64.decode(parser.getText(), Base64.DEFAULT);

        String playReadyHeader = MsDrmSession.getPlayReadyHeader(mProtection.content);

        XmlPullParser protectionParser = Xml.newPullParser();
        try {
            protectionParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            protectionParser.setInput(new ByteArrayInputStream(
                    playReadyHeader.getBytes(StandardCharsets.UTF_8)), null);
            while (protectionParser.next() != XmlPullParser.END_DOCUMENT) {
                if (protectionParser.getEventType() == XmlPullParser.START_TAG) {
                    if (protectionParser.getName().equals("KID")) {
                        protectionParser.next();
                        mProtection.kID = Base64.decode(protectionParser.getText(), Base64.DEFAULT);
                    }
                }
            }
        } catch (XmlPullParserException e) {
            if (LOGS_ENABLED)
                Log.e(TAG, "XmlPullParserException during parse of ProtectionHeader data", e);
        } catch (IOException e) {
            if (LOGS_ENABLED)
                Log.e(TAG, "IOException during parse of ProtectionHeader data", e);
        }

    }

    public long getDurationUs() {
        return mDurationUs;
    }

    public TrackType selectTrack(boolean select, int index) {
        if (index < 0 || index > mStreamIndexes.size()) {
            if (LOGS_ENABLED) Log.w(TAG, "Invalid track: " + index);
            return TrackType.UNKNOWN;
        }

        StreamIndex streamIndex = mStreamIndexes.get(index);

        if (select) {
            if (mActiveStreamIndexes[streamIndex.type.ordinal()] == index) {
                if (LOGS_ENABLED) Log.w(TAG, "Track " + index + " is already selected");
                return TrackType.UNKNOWN;
            }

            mActiveStreamIndexes[streamIndex.type.ordinal()] = index;
            if (LOGS_ENABLED) Log.v(TAG, "Selected track: " + index);
        } else {
            if (mActiveStreamIndexes[streamIndex.type.ordinal()] != index) {
                if (LOGS_ENABLED) Log.w(TAG, "Track " + index + " is not selected");
                return TrackType.UNKNOWN;
            }

            mActiveStreamIndexes[streamIndex.type.ordinal()] = -1;
            if (LOGS_ENABLED) Log.v(TAG, "Deselected track: " + index);
        }

        return streamIndex.type;
    }

    public int getSelectedTrackIndex(TrackType type) {
        return mActiveStreamIndexes[type.ordinal()];
    }

    public int[] getSelectedTracks() {
        return Arrays.copyOf(mActiveStreamIndexes, mActiveStreamIndexes.length);
    }

    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        selectTrack(true, trackIndex);
    }

    public int getMaxVideoInputBufferSize() {
        int maxWidth = 0, maxHeight = 0;
        boolean isAVC = false;
        for (StreamIndex streamIndex : mStreamIndexes) {
            if (streamIndex.type == TrackType.VIDEO) {
                for (QualityLevel qualityLevel : streamIndex.qualityLevels) {
                    if (((VideoQualityLevel)qualityLevel).width > maxWidth) {
                        maxWidth = ((VideoQualityLevel)qualityLevel).width;
                    }

                    if (((VideoQualityLevel)qualityLevel).height > maxHeight) {
                        maxHeight = ((VideoQualityLevel)qualityLevel).height;
                    }

                    if (((VideoQualityLevel)qualityLevel).fourCC.equalsIgnoreCase("H264") ||
                            ((VideoQualityLevel)qualityLevel).fourCC.equalsIgnoreCase("AVC1")) {
                        isAVC = true;
                    }
                }
            }
        }

        if (isAVC) {
            return ((maxWidth + 15) / 16) * ((maxHeight + 15) / 16) * 192;
        }

        return maxWidth * maxHeight * 3 / 2;
    }

    public int[] getSelectedQualityLevels() {
        int[] selectedQualityLevels = new int[TrackType.UNKNOWN.ordinal()];

        for (int i = 0; i < TrackType.UNKNOWN.ordinal(); i++) {
            if (mActiveStreamIndexes[i] > -1) {
                selectedQualityLevels[i] =
                        mStreamIndexes.get(
                                mActiveStreamIndexes[i]).activeQualityLevel;
            } else {
                selectedQualityLevels[i] = -1;
            }
        }
        return selectedQualityLevels;
    }

    public void updateQualityLevels(int[] selectedQualityLevels) {
        for (int i = 0; i < TrackType.UNKNOWN.ordinal(); i++) {
            if (mActiveStreamIndexes[i] > -1) {
                StreamIndex streamIndex = mStreamIndexes
                        .get(mActiveStreamIndexes[i]);

                streamIndex.activeQualityLevel = selectedQualityLevels[i];

                // Make sure that a quality level is always selected
                if (streamIndex.activeQualityLevel < 0) {
                    streamIndex.activeQualityLevel = 0;
                }
                if (streamIndex.activeQualityLevel >= streamIndex.qualityLevels.size()) {
                    streamIndex.activeQualityLevel = streamIndex.qualityLevels.size() - 1;
                }
            }
        }
    }

    public void updateQualityLevel(TrackType type, int qualityLevel) {
        StreamIndex streamIndex = mStreamIndexes.get(mActiveStreamIndexes[type.ordinal()]);

        streamIndex.activeQualityLevel = qualityLevel;

        // Make sure that a quality level is always selected
        if (streamIndex.activeQualityLevel < 0) {
            streamIndex.activeQualityLevel = 0;
        }
        if (streamIndex.activeQualityLevel >= streamIndex.qualityLevels.size()) {
            streamIndex.activeQualityLevel = streamIndex.qualityLevels.size() - 1;
        }
    }

    public TrackInfo[] getTrackInfo() {
        TrackInfo[] trackInfo = new TrackInfo[mStreamIndexes.size()];

        for (int i = 0; i < mStreamIndexes.size(); i++) {
            StreamIndex streamIndex = mStreamIndexes.get(i);

            String mime = null;
            TrackRepresentation[] representations =
                    new TrackRepresentation[streamIndex.qualityLevels.size()];
            for (int j = 0; j < streamIndex.qualityLevels.size(); j++) {
                QualityLevel qualityLevel = streamIndex.qualityLevels.get(j);
                if (j == 0) {
                    mime = qualityLevel.mime;
                }

                if (streamIndex.type == TrackType.AUDIO) {
                    AudioQualityLevel audio = (AudioQualityLevel)qualityLevel;
                    representations[j] = new AudioTrackRepresentation(qualityLevel.bitrate,
                            audio.channels, null, audio.sampleRate);
                } else if (streamIndex.type == TrackType.VIDEO) {
                    VideoQualityLevel video = (VideoQualityLevel)qualityLevel;
                    representations[j] = new VideoTrackRepresentation(qualityLevel.bitrate,
                            video.width, video.height, -1.0f);
                } else {
                    representations[j] = new TrackRepresentation(qualityLevel.bitrate);
                }

                representations[j] = new TrackRepresentation(qualityLevel.bitrate);
            }

            trackInfo[i] = new TrackInfo(streamIndex.type, mime, mDurationUs,
                    streamIndex.language, representations);
        }

        return trackInfo;
    }

    public Protection getProtection() {
        return mProtection;
    }

    public static class FragmentEntry {
        public long timeTicks;

        public long durationTicks;

        public int repeat = 1;
    }

    public static class QualityLevel {
        StreamIndex streamIndex;

        int bitrate;

        String fourCC;

        String mime;

        String cpd;
    }

    public static class AudioQualityLevel extends QualityLevel {
        int channels;

        int sampleRate;
    }

    public static class VideoQualityLevel extends QualityLevel {
        int width;

        int height;

        int NALLengthSize = 4;
    }

    public static class StreamIndex {
        TrackType type = TrackType.UNKNOWN;

        String url;

        int activeQualityLevel;

        final ArrayList<QualityLevel> qualityLevels = new ArrayList<>();

        ArrayList<FragmentEntry> fragments;

        long timeScale;

        String language = "und";
    }

    public static class Protection {
        byte[] uuid;

        byte[] content;

        byte[] kID;
    }
}
