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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.DASHTrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.Util;
import com.sonymobile.android.media.internal.streaming.common.ParseException;

public class MPDParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MPDParser";

    private static final int MAX_RETRIES = 5;

    private long mDurationUs = -1;

    private long mMinBufferTimeUs;

    private final ArrayList<Period> mPeriods = new ArrayList<>();

    private Period mCurrentPeriod;

    private AdaptationSet mCurrentAdaptationSet;

    private Representation mCurrentRepresentation;

    private SegmentBase mCurrentSegmentBase;

    private ContentProtection mContentProtection;

    private int mActivePeriod = 0;

    private String mBaseUri;

    private String mMPDFile = "";

    private boolean mIsDynamic = false;

    private long mMinUpdatePeriodUs;

    private byte[] mLastDigest;

    private int mUnchangedCounter = 0;

    public MPDParser(String baseUri) {
        mBaseUri = baseUri.substring(0, baseUri.lastIndexOf('/') + 1);
    }

    public String getMPDFile() {
        return mMPDFile;
    }

    public long getMinBufferTimeUs() {
        return mMinBufferTimeUs;
    }

    public Representation getRepresentation(TrackType type) {
        Period period = mPeriods.get(mActivePeriod);

        if (type != TrackType.UNKNOWN) {
            int selectedAdaptationSet = period.currentAdaptationSet[type.ordinal()];

            if (selectedAdaptationSet > -1) {
                AdaptationSet adaptationSet = period.adaptationSets
                        .get(selectedAdaptationSet);

                if (adaptationSet.activeRepresentation > -1) {
                    return adaptationSet.representations.get(adaptationSet.activeRepresentation);
                }
            }
        }

        return null;
    }

    public Period getActivePeriod() {
        return mPeriods.get(mActivePeriod);
    }

    public boolean parse(InputStream in) {
        byte[] buffer;
        try {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            buffer = new byte[1024];
            int read = in.read(buffer);
            while (read > 0) {
                digester.update(buffer, 0, read);
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }

            buffer = out.toByteArray();

            Charset charSet = Charset.forName("UTF-8");
            if ((buffer[0] == (byte) 0xff && buffer[1] == (byte) 0xfe) ||
                    (buffer[0] == (byte) 0xfe && buffer[1] == (byte) 0xff)) {
                charSet = Charset.forName("UTF-16");
            }

            mMPDFile = out.toString(charSet.name());
            mLastDigest = digester.digest();
            out.close();
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Could not download MPD", e);
            return false;
        } catch (NoSuchAlgorithmException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Unsupported digest method", e);
            return false;
        }

        return parseXML(new ByteArrayInputStream(buffer), false);
    }

    private boolean parseXML(InputStream in, boolean update) {
        XmlPullParser parser = Xml.newPullParser();
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if (parser.getName().equals("MPD")) {
                        handleMPD(parser);
                    } else if (parser.getName().equals("Period")) {
                        handlePeriod(parser, update);
                    } else if (parser.getName().equals("AdaptationSet")) {
                        handleAdaptationSet(parser);
                    } else if (parser.getName().equals("ContentComponent")) {
                        handleContentComponent(parser);
                    } else if (parser.getName().equals("ContentProtection")) {
                        handleContentProtection(parser);
                    } else if (parser.getName().equals("mspr:pro")) {
                        parser.next(); // Step to get to the data inside the tag.
                        handlePsshData(parser);
                    } else if (parser.getName().equals("SegmentTemplate")) {
                        handleSegmentTemplate(parser);
                    } else if (parser.getName().equals("SegmentBase")) {
                        handleSegmentBase(parser);
                    } else if (parser.getName().equals("S")) {
                        handleSegmentTimelineEntry(parser);
                    } else if (parser.getName().equals("Initialization")) {
                        handleInitialization(parser);
                    } else if (parser.getName().equals("Representation")) {
                        handleRepresentation(parser);
                    } else if (parser.getName().equals("AudioChannelConfiguration")) {
                        handleAudioChannelConfiguration(parser);
                    } else if (parser.getName().equals("Accessibility")) {
                        handleAccessibility(parser);
                    } else if (parser.getName().equals("Role")) {
                        handleRole(parser);
                    } else if (parser.getName().equals("Rating")) {
                        handleRating(parser);
                    } else if (parser.getName().equals("BaseURL")) {
                        parser.next();
                        handleBaseURL(parser);
                    }
                } else if (parser.getEventType() == XmlPullParser.END_TAG) {
                    if (parser.getName().equals("Period")) {
                        endPeriod(update);
                    } else if (parser.getName().equals("AdaptationSet")) {
                        endAdaptationSet();
                    } else if (parser.getName().equals("Representation")) {
                        endRepresentation();
                    } else if (parser.getName().equals("SegmentBase")) {
                        endSegmentBase();
                    }
                }
            }

            return mCurrentPeriod == null && mCurrentAdaptationSet == null
                    && mCurrentRepresentation == null;
        } catch (XmlPullParserException e) {
            if (LOGS_ENABLED) Log.e(TAG, "XmlPullParserException during parse", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException during parse", e);
            return false;
        } catch (ParseException e) {
            if (LOGS_ENABLED) Log.e(TAG, "ParseException during parse", e);
            return false;
        }
    }

    public boolean update(InputStream in) {
        byte[] buffer = new byte[1024];
        try {
            MessageDigest digester = MessageDigest.getInstance("MD5");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            int read = in.read(buffer);
            while (read > 0) {
                out.write(buffer, 0, read);
                digester.update(buffer, 0, read);
                read = in.read(buffer);
            }

            byte[] digest = digester.digest();

            if (MessageDigest.isEqual(digest, mLastDigest)) {
                mUnchangedCounter++;
                return false;
            }

            buffer = out.toByteArray();
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Could not download updated MPD", e);
            return false;
        } catch (NoSuchAlgorithmException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Unsupported digest method", e);
            return false;
        }

        mUnchangedCounter = 0;
        return parseXML(new ByteArrayInputStream(buffer), true);
    }

    private void updatePeriod(Period oldPeriod, Period newPeriod) {
        int noAdaptationSets = oldPeriod.adaptationSets.size();
        int i = 0;
        while (i < noAdaptationSets) {
            AdaptationSet oldAdaptationSet = oldPeriod.adaptationSets.get(i);
            boolean found = false;

            for (int j = 0; j < newPeriod.adaptationSets.size(); j++) {
                AdaptationSet newAdaptationSet = newPeriod.adaptationSets.get(j);

                if (oldAdaptationSet.mime.equals(newAdaptationSet.mime)) {
                    updateSegmentTimeline(oldAdaptationSet, newAdaptationSet);
                    newPeriod.adaptationSets.remove(j);
                    found = true;
                    break;
                }
            }

            if (!found) {
                oldPeriod.adaptationSets.remove(i);
                noAdaptationSets--;
                oldPeriod.currentAdaptationSet[TrackType.AUDIO.ordinal()] = -1;
                oldPeriod.currentAdaptationSet[TrackType.VIDEO.ordinal()] = -1;
                oldPeriod.currentAdaptationSet[TrackType.SUBTITLE.ordinal()] = -1;
            } else {
                i++;
            }
        }

        if (!newPeriod.adaptationSets.isEmpty()) {
            for (i = 0; i < newPeriod.adaptationSets.size(); i++) {
                oldPeriod.adaptationSets.add(newPeriod.adaptationSets.get(i));
            }

            oldPeriod.currentAdaptationSet[TrackType.AUDIO.ordinal()] = -1;
            oldPeriod.currentAdaptationSet[TrackType.VIDEO.ordinal()] = -1;
            oldPeriod.currentAdaptationSet[TrackType.SUBTITLE.ordinal()] = -1;
        }
    }

    private void updateSegmentTimeline(AdaptationSet oldAdaptationSet,
                                       AdaptationSet newAdaptationSet) {
        for (int i = 0; i < oldAdaptationSet.representations.size(); i++) {
            SegmentTemplate template = oldAdaptationSet.representations.get(i).segmentTemplate;

            if (template != null) {
                template.handled = false;
            }
        }

        for (int i = 0; i < oldAdaptationSet.representations.size(); i++) {
            Representation oldRepresentation = oldAdaptationSet.representations.get(i);

            if (oldRepresentation.segmentTemplate != null &&
                    oldRepresentation.segmentTemplate.handled) {
                continue;
            }

            for (int j = 0; j < newAdaptationSet.representations.size(); j++) {
                Representation newRepresentation = newAdaptationSet.representations.get(i);

                if (oldRepresentation.id.equals(newRepresentation.id)) {
                    if (newRepresentation.segmentTemplate != null) {
                        if (oldRepresentation.segmentTemplate == null) {
                            oldRepresentation.segmentTemplate = newRepresentation.segmentTemplate;
                        } else {
                            if (newRepresentation.segmentTemplate.segmentTimeline != null) {
                                oldRepresentation.segmentTemplate.segmentTimeline =
                                        newRepresentation.segmentTemplate.segmentTimeline;
                            }
                        }

                        oldRepresentation.segmentTemplate.handled = true;
                    }

                    newAdaptationSet.representations.remove(j);
                    break;
                }
            }
        }
    }

    private void handleMPD(XmlPullParser parser) {

        mDurationUs = parseISO8601Duration(parser.getAttributeValue(null,
                "mediaPresentationDuration"));

        String type = parser.getAttributeValue(null, "type");
        if (type.equalsIgnoreCase("dynamic")) {
            mIsDynamic = true;
        }

        mMinBufferTimeUs = parseISO8601Duration(parser.getAttributeValue(null, "minBufferTime"));

        mMinUpdatePeriodUs =
                parseISO8601Duration(parser.getAttributeValue(null, "minimumUpdatePeriod"));
    }

    private void handlePeriod(XmlPullParser parser, boolean update) {
        mCurrentPeriod = new Period();
        String duration = parser.getAttributeValue(null, "duration");

        mCurrentPeriod.id = parser.getAttributeValue(null, "id");
        if (mCurrentPeriod.id == null) {
            mCurrentPeriod.id = "";
        }

        if (duration != null) {
            mCurrentPeriod.durationUs = parseISO8601Duration(duration);
        } else {
            mCurrentPeriod.durationUs = mDurationUs;
        }

        String startTime = parser.getAttributeValue(null, "start");

        if (startTime != null) {
            mCurrentPeriod.startTimeUs = parseISO8601Duration(startTime);
        } else {
            if (mPeriods.size() > 0) {
                Period lastPeriod = mPeriods.get(mPeriods.size() - 1);
                mCurrentPeriod.startTimeUs = lastPeriod.startTimeUs + lastPeriod.durationUs;
            } else {
                mCurrentPeriod.startTimeUs = 0;
            }
        }

        mCurrentPeriod.currentAdaptationSet[TrackType.AUDIO.ordinal()] = -1;
        mCurrentPeriod.currentAdaptationSet[TrackType.VIDEO.ordinal()] = -1;
        mCurrentPeriod.currentAdaptationSet[TrackType.SUBTITLE.ordinal()] = -1;

        if (!update) {
            mPeriods.add(mCurrentPeriod);
        }
    }

    private void endPeriod(boolean update) {
        if (update) {
            boolean found = false;
            for (Period period : mPeriods) {
                if (period.id.equals(mCurrentPeriod.id)) {
                    found = true;
                    updatePeriod(period, mCurrentPeriod);
                    break;
                }
            }

            if (!found) {
                mPeriods.add(mCurrentPeriod);
            }
        }

        mCurrentPeriod = null;
    }

    private void handleAdaptationSet(XmlPullParser parser) {
        mCurrentAdaptationSet = new AdaptationSet();

        mCurrentAdaptationSet.mime = parser.getAttributeValue(null, "mimeType");

        String value = parser.getAttributeValue(null, "lang");
        if (value != null) {
            mCurrentAdaptationSet.language = value;
        } else {
            mCurrentAdaptationSet.language = "und";
        }

        value = parser.getAttributeValue(null, "contentType");
        if (value != null) {
            if (value.equals("audio")) {
                mCurrentAdaptationSet.type = TrackType.AUDIO;
            } else if (value.equals("video")) {
                mCurrentAdaptationSet.type = TrackType.VIDEO;
            } else if (value.equals("text") || value.equals("image")) {
                mCurrentAdaptationSet.type = TrackType.SUBTITLE;
            }
        }

        if (parser.getAttributeValue(null, "width") != null) {
            mCurrentAdaptationSet.width = Integer.parseInt(parser.getAttributeValue(null, "width"));
            mCurrentAdaptationSet.height = Integer
                    .parseInt(parser.getAttributeValue(null, "height"));
        }

        value = parser.getAttributeValue(null, "frameRate");
        if (value != null) {
            mCurrentAdaptationSet.frameRate = parseFrameRate(value);
        }

        value = parser.getAttributeValue(null, "audioSamplingRate");
        if (value != null) {
            mCurrentAdaptationSet.audioSamplingRate = Integer.parseInt(value);
        }
    }

    private void endAdaptationSet() {
        if (mCurrentAdaptationSet.type == TrackType.UNKNOWN) {
            if (mCurrentAdaptationSet.mime.contains("audio")) {
                mCurrentAdaptationSet.type = TrackType.AUDIO;
            } else if (mCurrentAdaptationSet.mime.contains("video")) {
                mCurrentAdaptationSet.type = TrackType.VIDEO;
            } else if (mCurrentAdaptationSet.mime.contains("text")
                    || mCurrentAdaptationSet.mime.contains("image")) {
                mCurrentAdaptationSet.type = TrackType.SUBTITLE;
            }
        }

        if (mCurrentAdaptationSet.type == TrackType.AUDIO
                && mCurrentPeriod.currentAdaptationSet[TrackType.AUDIO.ordinal()] == -1) {
            mCurrentPeriod.currentAdaptationSet[TrackType.AUDIO.ordinal()] =
                    mCurrentPeriod.adaptationSets.size();
        } else if (mCurrentAdaptationSet.type == TrackType.VIDEO
                && mCurrentPeriod.currentAdaptationSet[TrackType.VIDEO.ordinal()] == -1) {
            mCurrentPeriod.currentAdaptationSet[TrackType.VIDEO.ordinal()] =
                    mCurrentPeriod.adaptationSets.size();
        }

        for (Representation representation : mCurrentAdaptationSet.representations) {
            if (representation.segmentTemplate == null &&
                    representation.segmentBase == null) {
                throw new ParseException("No SegmentTemplate or BaseURL found!");
            }
        }

        mCurrentPeriod.adaptationSets.add(mCurrentAdaptationSet);

        mCurrentAdaptationSet = null;
    }

    private void handleContentComponent(XmlPullParser parser) {
        String value = parser.getAttributeValue(null, "contentType");
        if (value != null) {
            if (value.equals("audio")) {
                mCurrentAdaptationSet.type = TrackType.AUDIO;
            } else if (value.equals("video")) {
                mCurrentAdaptationSet.type = TrackType.VIDEO;
            } else if (value.equals("text") || value.equals("image")) {
                mCurrentAdaptationSet.type = TrackType.SUBTITLE;
            } else {
                mCurrentAdaptationSet.type = TrackType.UNKNOWN;
            }
        }
    }

    private void handleContentProtection(XmlPullParser parser) {
        String uuid = parser.getAttributeValue(null, "schemeIdUri");
        String compare = "urn:uuid:";
        // Multiple schemeIdUri tags can exist, check for correct one.
        if (uuid.startsWith(compare)) {
            mContentProtection = new ContentProtection();
            uuid = uuid.substring(compare.length(), uuid.length());
            uuid = uuid.replace("-", "");
            mContentProtection.uuid = Util.uuidStringToByteArray(uuid);
        }
    }

    private void handlePsshData(XmlPullParser parser) {
        if (mContentProtection != null && mContentProtection.psshData == null) {
            mContentProtection.psshData = Base64.decode(parser.getText(), Base64.DEFAULT);
        }
    }

    private void handleSegmentTemplate(XmlPullParser parser) {
        SegmentTemplate template = new SegmentTemplate();

        template.initialization = makeURL(parser.getAttributeValue(null, "initialization"));
        template.media = makeURL(parser.getAttributeValue(null, "media"));
        String startNumber = parser.getAttributeValue(null, "startNumber");
        if (startNumber != null) {
            template.startNumber = Integer.parseInt(startNumber);
        }

        String timescale = parser.getAttributeValue(null, "timescale");
        if (timescale != null) {
            template.timescale = Integer.parseInt(timescale);
        }

        String duration = parser.getAttributeValue(null, "duration");

        if (duration != null) {
            template.durationTicks = Integer.parseInt(duration);
        }

        if (mCurrentRepresentation != null) {
            mCurrentRepresentation.segmentTemplate = template;
        } else if (mCurrentAdaptationSet != null) {
            mCurrentAdaptationSet.segmentTemplate = template;
        }
    }

    private String makeURL(String url) {

        String fullURL = mBaseUri;

        if (mCurrentPeriod != null && mCurrentPeriod.baseURL != null) {
            if (mCurrentPeriod.baseURL.startsWith("http://")
                    || mCurrentPeriod.baseURL.startsWith("https://")) {
                fullURL = mCurrentPeriod.baseURL;
            } else {
                fullURL += mCurrentPeriod.baseURL;
            }
        }

        if (mCurrentRepresentation != null && mCurrentRepresentation.baseURL != null) {
            if (mCurrentPeriod.baseURL.startsWith("http://")
                    || mCurrentPeriod.baseURL.startsWith("https://")) {
                fullURL = mCurrentRepresentation.baseURL;
            } else {
                fullURL += mCurrentRepresentation.baseURL;
            }
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            fullURL = url;
        } else {
            fullURL += url;
        }

        return fullURL;
    }

    private void handleSegmentBase(XmlPullParser parser) {
        mCurrentSegmentBase = new SegmentBase();

        String baseURL = mCurrentRepresentation.baseURL;
        if (baseURL.startsWith("http://") || baseURL.startsWith("https://")) {
            mCurrentSegmentBase.url = mCurrentRepresentation.baseURL;
        } else {
            mCurrentSegmentBase.url = mBaseUri + mCurrentRepresentation.baseURL;
        }

        String[] indexRange = parser.getAttributeValue(null, "indexRange").split("-");
        mCurrentSegmentBase.sidxOffset = Long.parseLong(indexRange[0]);
        mCurrentSegmentBase.initSize = mCurrentSegmentBase.sidxOffset;
        mCurrentSegmentBase.sidxSize = Long.parseLong(indexRange[1]);

        mCurrentRepresentation.segmentBase = mCurrentSegmentBase;
    }

    private void endSegmentBase() {
        mCurrentSegmentBase = null;
    }

    private void handleInitialization(XmlPullParser parser) {
        if (mCurrentSegmentBase != null) {
            String[] range = parser.getAttributeValue(null, "range").split("-");
            mCurrentSegmentBase.initOffset = Long.parseLong(range[0]);
            mCurrentSegmentBase.initSize = Long.parseLong(range[1]);
        }

    }

    private void handleSegmentTimelineEntry(XmlPullParser parser) {
        SegmentTimelineEntry entry = new SegmentTimelineEntry();

        SegmentTemplate template;
        if (mCurrentRepresentation != null) {
            template = mCurrentRepresentation.segmentTemplate;
        } else {
            template = mCurrentAdaptationSet.segmentTemplate;
        }

        if (template.segmentTimeline == null) {
            template.segmentTimeline = new ArrayList<>();
        }

        String time = parser.getAttributeValue(null, "t");
        String duration = parser.getAttributeValue(null, "d");
        String repeat = parser.getAttributeValue(null, "r");

        if (time != null) {
            entry.timeTicks = Long.parseLong(time);
        } else {
            if (template.segmentTimeline.size() == 0) {
                entry.timeTicks = 0;
            } else {
                SegmentTimelineEntry lastEntry = template.segmentTimeline
                        .get(template.segmentTimeline.size() - 1);

                entry.timeTicks = lastEntry.timeTicks
                        + lastEntry.durationTicks * (lastEntry.repeat + 1);
            }
        }

        if (duration != null) {
            entry.durationTicks = Long.parseLong(duration);
        }

        if (repeat != null) {
            entry.repeat = Integer.parseInt(repeat);

            if (entry.repeat < 0) {
                long tmpRepeat = mCurrentPeriod.durationUs
                        / (entry.durationTicks * 1000000L / template.timescale);
                entry.repeat = (int)tmpRepeat - 1;
            }
        }

        template.segmentTimeline.add(entry);
    }

    private void handleRepresentation(XmlPullParser parser) {
        mCurrentRepresentation = new Representation();

        mCurrentRepresentation.id = parser.getAttributeValue(null, "id");
        mCurrentRepresentation.bandwidth = Integer.parseInt(parser.getAttributeValue(null,
                "bandwidth"));

        if (parser.getAttributeValue(null, "width") != null) {
            mCurrentRepresentation.width = Integer
                    .parseInt(parser.getAttributeValue(null, "width"));
            mCurrentRepresentation.height = Integer.parseInt(parser
                    .getAttributeValue(null, "height"));
        }

        String value = parser.getAttributeValue(null, "frameRate");
        if (value != null) {
            mCurrentRepresentation.frameRate = parseFrameRate(value);
        }

        value = parser.getAttributeValue(null, "audioSamplingRate");
        if (value != null) {
            mCurrentRepresentation.audioSamplingRate = Integer.parseInt(value);
        }

        if (mCurrentAdaptationSet.mime == null) {
            mCurrentAdaptationSet.mime = parser.getAttributeValue(null, "mimeType");
        }

        mCurrentAdaptationSet.representations.add(mCurrentRepresentation);
    }

    private void endRepresentation() {
        if (mCurrentRepresentation.segmentTemplate == null
                && mCurrentAdaptationSet.segmentTemplate != null) {
            mCurrentRepresentation.segmentTemplate = mCurrentAdaptationSet.segmentTemplate;
        }

        if (mCurrentRepresentation.segmentTemplate == null
                && mCurrentRepresentation.segmentBase == null
                && mCurrentRepresentation.baseURL != null
                && mCurrentRepresentation.baseURL.length() > 0) {
            SegmentBase segmentBase = new SegmentBase();

            String baseURL = mCurrentRepresentation.baseURL;
            if (baseURL.startsWith("http://") || baseURL.startsWith("https://")) {
                segmentBase.url = mCurrentRepresentation.baseURL;
            } else {
                segmentBase.url = mBaseUri + mCurrentRepresentation.baseURL;
            }

            segmentBase.initOffset = 0;
            segmentBase.initSize = 200;
            segmentBase.sidxOffset = -1;
            segmentBase.sidxSize = 200;

            mCurrentRepresentation.segmentBase = segmentBase;
        }

        mCurrentRepresentation = null;
    }

    private void handleAudioChannelConfiguration(XmlPullParser parser) {
        String value = parser.getAttributeValue(null, "value");
        if (mCurrentRepresentation != null) {
            mCurrentRepresentation.audioChannelConfiguration = value;
        } else {
            mCurrentAdaptationSet.audioChannelConfiguration = value;
        }
    }

    private void handleAccessibility(XmlPullParser parser) {
        mCurrentAdaptationSet.accessibility = parser.getAttributeValue(null, "value");
    }

    private void handleRole(XmlPullParser parser) {
        mCurrentAdaptationSet.role = parser.getAttributeValue(null, "value");
    }

    private void handleRating(XmlPullParser parser) {
        mCurrentAdaptationSet.rating = parser.getAttributeValue(null, "value");
    }

    private void handleBaseURL(XmlPullParser parser) {
        if (mCurrentRepresentation != null) {
            mCurrentRepresentation.baseURL = parser.getText();
        } else if (mCurrentPeriod != null) {
            mCurrentPeriod.baseURL = parser.getText();
        } else {
            mBaseUri = parser.getText();
        }
    }

    private static long parseISO8601Duration(String value) {
        if (value == null) {
            return -1;
        }

        if (value.charAt(0) != 'P') {
            return -1;
        }

        boolean time = false;
        int offset = 1;
        int start = 1;

        long durationUs = 0;

        while (offset < value.length()) {
            switch (value.charAt(offset)) {
                case 'Y':
                    durationUs += Long.parseLong(value.substring(start, offset)) * 365 * 24
                            * 60 * 60 * 1000000L;
                    start = offset + 1;
                    break;

                case 'M':
                    if (time) {
                        durationUs += Long.parseLong(value.substring(start, offset)) * 60
                                * 1000000L;
                    } else {
                        durationUs += Long.parseLong(value.substring(start, offset)) * 30 * 24 * 60
                                * 60 * 1000000L;
                    }
                    start = offset + 1;
                    break;

                case 'W':
                    durationUs += Long.parseLong(value.substring(start, offset)) * 7 * 24 * 60 * 60
                            * 1000000L;
                    start = offset + 1;
                    break;

                case 'D':
                    durationUs += Long.parseLong(value.substring(start, offset)) * 24 * 60 * 60
                            * 1000000L;
                    start = offset + 1;
                    break;

                case 'T':
                    time = true;
                    start = offset + 1;
                    break;

                case 'H':
                    durationUs += Long.parseLong(value.substring(start, offset)) * 60 * 60
                            * 1000000L;
                    start = offset + 1;
                    break;

                case 'S':
                    durationUs += new BigDecimal(value.substring(start, offset)).multiply(
                            new BigDecimal(1000000)).longValue();
                    break;

                default:
                    if ((value.charAt(offset) < '0' || value.charAt(offset) > '9')
                            && value.charAt(offset) != '.' && value.charAt(offset) != ',') {
                        if (LOGS_ENABLED) Log.e(TAG, "Illegal ISO 8601 char detected");
                        return -1;
                    }
                    break;
            }

            offset++;
        }

        return durationUs;
    }

    private static float parseFrameRate(String value) {
        try {
            int index = value.indexOf('/');
            if (index < 0) {
                return Float.parseFloat(value);
            }

            float numerator = Float.parseFloat(value.substring(0, index));
            float denominator = Float.parseFloat(value.substring(index + 1));

            if (denominator == 0) {
                if (LOGS_ENABLED) Log.e(TAG, "Illegal frame rate (division by zero)");
                return -1f;
            }

            return numerator / denominator;
        } catch (NumberFormatException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Frame rate could not be parsed");
            return -1f;
        }
    }

    private static int parseChannelConfiguration(String value) {
        try {
            int channelCount = Integer.parseInt(value);
            if (channelCount == 7) { // Front L/R/C, Middle L/R,
                                     // Rear L/R, LFE = 8 channels
                channelCount++;
            }

            return channelCount;
        } catch (NumberFormatException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Channel configuration could not be parsed");
            return -1;
        }
    }

    public long getDurationUs() {
        return mDurationUs;
    }

    public DASHTrackInfo[] getTrackInfo() {
        int numPeriods = mPeriods.size();

        int totalTracks = 0;
        for (int i = 0; i < numPeriods; i++) {
            totalTracks += mPeriods.get(i).adaptationSets.size();
        }

        DASHTrackInfo[] trackInfos = new DASHTrackInfo[totalTracks];
        int trackIndex = 0;

        for (int i = 0; i < numPeriods; i++) {
            Period period = mPeriods.get(i);

            int numAdaptationSets = period.adaptationSets.size();
            for (int j = 0; j < numAdaptationSets; j++) {
                AdaptationSet adaptationSet = period.adaptationSets.get(j);
                TrackType trackType = adaptationSet.type;

                int numRepresentations = adaptationSet.representations.size();
                TrackRepresentation[] trackRepresentations =
                        new TrackRepresentation[numRepresentations];

                for (int k = 0; k < numRepresentations; k++) {
                    Representation representation = adaptationSet.representations.get(k);
                    TrackRepresentation trackRepresentation = null;

                    if (trackType == TrackType.AUDIO) {
                        String channelConfiguration = representation.audioChannelConfiguration;
                        if (channelConfiguration == null || channelConfiguration.isEmpty()) {
                            channelConfiguration = adaptationSet.audioChannelConfiguration;
                        }

                        int channelCount = parseChannelConfiguration(channelConfiguration);

                        int samplingRate = representation.audioSamplingRate != 0
                                ? representation.audioSamplingRate
                                : adaptationSet.audioSamplingRate;
                        trackRepresentation = new AudioTrackRepresentation(
                                representation.bandwidth, channelCount, channelConfiguration,
                                samplingRate);
                    } else if (trackType == TrackType.VIDEO) {
                        int width = representation.width != 0 ? representation.width
                                : adaptationSet.width;
                        int height = representation.height != 0 ? representation.height
                                : adaptationSet.height;
                        float frameRate = representation.frameRate != 0 ? representation.frameRate
                                : adaptationSet.frameRate;
                        trackRepresentation = new VideoTrackRepresentation(
                                representation.bandwidth, width, height, frameRate);
                    } else if (trackType == TrackType.SUBTITLE) {
                        trackRepresentation = new TrackRepresentation(representation.bandwidth);
                    }

                    trackRepresentations[k] = trackRepresentation;
                }

                trackInfos[trackIndex] = new DASHTrackInfo(trackType, adaptationSet.mime,
                        period.durationUs, adaptationSet.language, trackRepresentations,
                        period.startTimeUs, adaptationSet.accessibility, adaptationSet.role,
                        adaptationSet.rating);
                trackIndex++;
            }
        }

        return trackInfos;
    }

    public void seekTo(long timeUs) {

        for (int i = 0; i < mPeriods.size(); i++) {
            Period period = mPeriods.get(i);

            mActivePeriod = i;
            if (timeUs >= period.startTimeUs && timeUs < period.startTimeUs + period.durationUs) {
                break;
            }
        }
    }

    public TrackType selectTrack(boolean select, int index) {
        Period period;
        int trackCount = 0;

        if (index < 0) {
            if (LOGS_ENABLED) Log.w(TAG, "Invalid track: " + index);
            return TrackType.UNKNOWN;
        }

        int numPeriods = mPeriods.size();
        for (int i = 0; i < numPeriods; i++) {
            period = mPeriods.get(i);
            int numAdaptationSets = period.adaptationSets.size();
            if (index < (trackCount + numAdaptationSets)) {
                AdaptationSet adaptationSet = period.adaptationSets.get(index - trackCount);

                if (select) {
                    if (period.currentAdaptationSet[adaptationSet.type
                            .ordinal()] == (index - trackCount)) {
                        if (LOGS_ENABLED) Log.w(TAG, "track " + index + " is already selected");

                        return TrackType.UNKNOWN;
                    }
                    period.currentAdaptationSet[adaptationSet.type.ordinal()] = index - trackCount;
                    if (LOGS_ENABLED) Log.v(TAG, "Selected track: " + index);
                } else {
                    if (period.currentAdaptationSet[adaptationSet.type
                            .ordinal()] != (index - trackCount)) {
                        if (LOGS_ENABLED) Log.w(TAG, "track " + index + " is not selected");

                        return TrackType.UNKNOWN;
                    }
                    period.currentAdaptationSet[adaptationSet.type.ordinal()] = -1;
                    if (LOGS_ENABLED) Log.v(TAG, "Deselected track: " + index);
                }

                if (mActivePeriod == i) {
                    return adaptationSet.type;
                } else {
                    return TrackType.UNKNOWN;
                }
            }

            trackCount += numAdaptationSets;
        }

        return TrackType.UNKNOWN;
    }

    public int getSelectedTrackIndex(TrackType type) {
        int trackCount = 0;
        int trackIndex = -1;

        for (int i = 0; i < mPeriods.size(); i++) {
            Period period = mPeriods.get(i);

            if (mActivePeriod == i) {
                trackIndex = period.currentAdaptationSet[type.ordinal()];
                break;
            }

            trackCount += period.adaptationSets.size();
        }

        if (trackIndex > -1) {
            return trackCount + trackIndex;
        }

        return -1;
    }

    public boolean hasNextPeriod() {
        return mActivePeriod + 1 < mPeriods.size();
    }

    public void nextPeriod() {
        if (mActivePeriod + 1 < mPeriods.size()) {
            mActivePeriod++;
        }
    }

    public long getPeriodTimeOffsetUs() {
        return mPeriods.get(mActivePeriod).startTimeUs;
    }

    public int[] getSelectedRepresentations() {
        int[] selectedRepresentations = new int[TrackType.UNKNOWN.ordinal()];
        Period period = mPeriods.get(mActivePeriod);

        for (int i = 0; i < TrackType.UNKNOWN.ordinal(); i++) {
            if (period.currentAdaptationSet[i] > -1) {
                selectedRepresentations[i] =
                        period.adaptationSets.get(
                                period.currentAdaptationSet[i]).activeRepresentation;
            } else {
                selectedRepresentations[i] = -1;
            }
        }
        return selectedRepresentations;
    }

    public void updateRepresentations(int[] selectedRepresentations) {
        Period period = mPeriods.get(mActivePeriod);
        for (int i = 0; i < TrackType.UNKNOWN.ordinal(); i++) {
            if (period.currentAdaptationSet[i] > -1) {
                AdaptationSet adaptationSet = period.adaptationSets
                        .get(period.currentAdaptationSet[i]);

                adaptationSet.activeRepresentation = selectedRepresentations[i];

                // Make sure that a representation is always selected
                if (adaptationSet.activeRepresentation < 0) {
                    adaptationSet.activeRepresentation = 0;
                }
                if (adaptationSet.activeRepresentation >= adaptationSet.representations.size()) {
                    adaptationSet.activeRepresentation = adaptationSet.representations.size() - 1;
                }
            }
        }
    }

    public void updateRepresentation(TrackType type, int representation) {
        Period period = mPeriods.get(mActivePeriod);
        int adaptationSetIndex = period.currentAdaptationSet[type.ordinal()];

        if (adaptationSetIndex > -1) {
            AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
            adaptationSet.activeRepresentation = representation;
        }
    }

    public int[] getSelectedTracks() {
        int trackCount = 0;
        for (int i = 0; i < mPeriods.size(); i++) {
            if (i < mActivePeriod) {
                trackCount += mPeriods.get(i).adaptationSets.size();
            } else if (i == mActivePeriod) {
                int[] selectedTracks = Arrays.copyOf(mPeriods.get(i).currentAdaptationSet,
                        TrackType.UNKNOWN.ordinal());
                for (int j = 0; j < selectedTracks.length; j++) {
                    if (selectedTracks[j] > -1) {
                        selectedTracks[j] += trackCount;
                    }
                }
                return selectedTracks;
            }
        }
        return null;
    }

    public ContentProtection getContentProtection() {
        return mContentProtection;
    }

    public void selectRepresentations(int trackIndex, Vector<Integer> representations) {
        Period period;
        int trackCount = 0;

        if (trackIndex < 0) {
            if (LOGS_ENABLED) Log.w(TAG, "Invalid track: " + trackIndex);
            return;
        }

        int numPeriods = mPeriods.size();
        for (int i = 0; i < numPeriods; i++) {

            period = mPeriods.get(i);
            int numAdaptationSets = period.adaptationSets.size();
            if (trackIndex < (trackCount + numAdaptationSets)) {
                AdaptationSet adaptationSet = period.adaptationSets.get(trackIndex - trackCount);

                int numRepresentations = adaptationSet.representations.size();
                for (int j = 0; j < numRepresentations; j++) {
                    adaptationSet.representations.get(j).selected = false;
                }

                int numSelectedRepresentations = representations.size();
                for (int j = 0; j < numSelectedRepresentations; j++) {
                    int representationIndex = representations.get(j);

                    if (representationIndex >= 0 && representationIndex < numRepresentations) {
                        adaptationSet.representations.get(representationIndex).selected = true;
                    } else if (LOGS_ENABLED) {
                        Log.w(TAG, "Invalid representation: " + representationIndex);
                    }
                }

                return;
            }

            trackCount += numAdaptationSets;
        }
    }

    public int getMaxVideoInputBufferSize() {
        int maxWidth = 0, maxHeight = 0;
        for (Period period : mPeriods) {
            for (AdaptationSet adaptationSet : period.adaptationSets) {
                if (adaptationSet.type == TrackType.VIDEO) {
                    for (Representation representation : adaptationSet.representations) {
                        if (representation.width > maxWidth) {
                            maxWidth = representation.width;
                        }

                        if (representation.height > maxHeight) {
                            maxHeight = representation.height;
                        }
                    }
                }
            }
        }

        return ((maxWidth + 15) / 16) * ((maxHeight + 15) / 16) * 192;
    }

    public long getMinUpdatePeriodUs() {
        return mMinUpdatePeriodUs;
    }

    public boolean isDynamicWaitingForUpdate() {
        return mIsDynamic && (mUnchangedCounter < MAX_RETRIES);
    }

    public static class SegmentBase {
        String url;

        public long initOffset = 0;

        long initSize;

        long sidxOffset;

        long sidxSize;
    }

    public static class SegmentTimelineEntry {
        public long timeTicks;

        public long durationTicks;

        public int repeat = 0;
    }

    public static class Representation {
        String id;

        int bandwidth;

        int width;

        int height;

        float frameRate;

        int audioSamplingRate;

        String audioChannelConfiguration;

        SegmentTemplate segmentTemplate;

        SegmentBase segmentBase;

        String baseURL;

        boolean selected = true;
    }

    public static class Period {

        String id;

        public String baseURL;

        long durationUs;

        long startTimeUs;

        final ArrayList<AdaptationSet> adaptationSets = new ArrayList<>();

        final int[] currentAdaptationSet = new int[TrackType.UNKNOWN.ordinal()];
    }

    public static class AdaptationSet {
        public int activeRepresentation = -1;

        public SegmentTemplate segmentTemplate;

        TrackType type = TrackType.UNKNOWN;

        String mime;

        String language;

        String accessibility;

        String role;

        String rating;

        int width;

        int height;

        float frameRate;

        int audioSamplingRate;

        String audioChannelConfiguration;

        final ArrayList<Representation> representations = new ArrayList<>();
    }

    public static class SegmentTemplate {
        String initialization;

        String media;

        int startNumber;

        public int timescale = 1;

        int durationTicks;

        public ArrayList<SegmentTimelineEntry> segmentTimeline;

        boolean handled;
    }

    public static class ContentProtection {
        byte[] uuid;

        byte[] psshData;
    }
}
