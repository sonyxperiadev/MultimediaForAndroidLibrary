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

package com.sonymobile.android.media.internal;

import static com.sonymobile.android.media.internal.Util.MARLIN_SYSTEM_ID;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.util.Log;

import org.json.JSONException;

import com.sonymobile.android.media.AudioTrackRepresentation;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.VideoTrackRepresentation;
import com.sonymobile.android.media.internal.DataSource.DataAvailability;

public class ISOBMFFParser extends MediaParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "ISOBMFFParser";

    protected static final int BOX_ID_FTYP = fourCC('f', 't', 'y', 'p');

    protected static final int BOX_ID_UUID = fourCC('u', 'u', 'i', 'd');

    protected static final int BOX_ID_MOOV = fourCC('m', 'o', 'o', 'v');

    protected static final int BOX_ID_MVHD = fourCC('m', 'v', 'h', 'd');

    protected static final int BOX_ID_TRAK = fourCC('t', 'r', 'a', 'k');

    protected static final int BOX_ID_TKHD = fourCC('t', 'k', 'h', 'd');

    protected static final int BOX_ID_MDIA = fourCC('m', 'd', 'i', 'a');

    protected static final int BOX_ID_MDHD = fourCC('m', 'd', 'h', 'd');

    protected static final int BOX_ID_HDLR = fourCC('h', 'd', 'l', 'r');

    protected static final int BOX_ID_MINF = fourCC('m', 'i', 'n', 'f');

    protected static final int BOX_ID_STBL = fourCC('s', 't', 'b', 'l');

    protected static final int BOX_ID_STSD = fourCC('s', 't', 's', 'd');

    protected static final int BOX_ID_AVC1 = fourCC('a', 'v', 'c', '1');

    protected static final int BOX_ID_AVC3 = fourCC('a', 'v', 'c', '3');

    protected static final int BOX_ID_AVCC = fourCC('a', 'v', 'c', 'C');

    protected static final int BOX_ID_STTS = fourCC('s', 't', 't', 's');

    protected static final int BOX_ID_STSZ = fourCC('s', 't', 's', 'z');

    protected static final int BOX_ID_STCO = fourCC('s', 't', 'c', 'o');

    protected static final int BOX_ID_CO64 = fourCC('c', 'o', '6', '4');

    protected static final int BOX_ID_CTTS = fourCC('c', 't', 't', 's');

    protected static final int BOX_ID_STSC = fourCC('s', 't', 's', 'c');

    protected static final int BOX_ID_STSS = fourCC('s', 't', 's', 's');

    protected static final int BOX_ID_MP4V = fourCC('m', 'p', '4', 'v');

    protected static final int BOX_ID_MP4A = fourCC('m', 'p', '4', 'a');

    protected static final int BOX_ID_ESDS = fourCC('e', 's', 'd', 's');

    protected static final int BOX_ID_MDAT = fourCC('m', 'd', 'a', 't');

    protected static final int BOX_ID_MVEX = fourCC('m', 'v', 'e', 'x');

    protected static final int BOX_ID_TREX = fourCC('t', 'r', 'e', 'x');

    protected static final int BOX_ID_MEHD = fourCC('m', 'e', 'h', 'd');

    protected static final int BOX_ID_MOOF = fourCC('m', 'o', 'o', 'f');

    protected static final int BOX_ID_TFHD = fourCC('t', 'f', 'h', 'd');

    protected static final int BOX_ID_TRAF = fourCC('t', 'r', 'a', 'f');

    protected static final int BOX_ID_TRUN = fourCC('t', 'r', 'u', 'n');

    protected static final int BOX_ID_SBGP = fourCC('s', 'b', 'g', 'p');

    protected static final int BOX_ID_SGPD = fourCC('s', 'g', 'p', 'd');

    protected static final int BOX_ID_SUBS = fourCC('s', 'u', 'b', 's');

    protected static final int BOX_ID_SAIZ = fourCC('s', 'a', 'i', 'z');

    protected static final int BOX_ID_SAIO = fourCC('s', 'a', 'i', 'o');

    protected static final int BOX_ID_TFTD = fourCC('t', 'f', 'd', 't');

    protected static final int BOX_ID_SDTP = fourCC('s', 'd', 't', 'p');

    protected static final int BOX_ID_MFRA = fourCC('m', 'f', 'r', 'a');

    protected static final int BOX_ID_TFRA = fourCC('t', 'f', 'r', 'a');

    protected static final int BOX_ID_MFRO = fourCC('m', 'f', 'r', 'o');

    protected static final int BOX_ID_ENCV = fourCC('e', 'n', 'c', 'v');

    protected static final int BOX_ID_ENCA = fourCC('e', 'n', 'c', 'a');

    protected static final int BOX_ID_SINF = fourCC('s', 'i', 'n', 'f');

    protected static final int BOX_ID_FRMA = fourCC('f', 'r', 'm', 'a');

    protected static final int BOX_ID_SCHM = fourCC('s', 'c', 'h', 'm');

    protected static final int BOX_ID_SCHI = fourCC('s', 'c', 'h', 'i');

    protected static final int BOX_ID_EDTS = fourCC('e', 'd', 't', 's');

    protected static final int BOX_ID_ELST = fourCC('e', 'l', 's', 't');

    protected static final int BOX_ID_PSSH = fourCC('p', 's', 's', 'h');

    protected static final int BOX_ID_TENC = fourCC('t', 'e', 'n', 'c');

    protected static final int BOX_ID_STPP = fourCC('s', 't', 'p', 'p');

    protected static final int BOX_ID_SENC = fourCC('s', 'e', 'n', 'c');

    protected static final int BOX_ID_HVCC = fourCC('h', 'v', 'c', 'C');

    protected static final int BOX_ID_HVC1 = fourCC('h', 'v', 'c', '1');

    protected static final int BOX_ID_HEV1 = fourCC('h', 'e', 'v', '1');

    protected static final int BOX_ID_UDTA = fourCC('u', 'd', 't', 'a');

    protected static final int BOX_ID_META = fourCC('m', 'e', 't', 'a');

    protected static final int BOX_ID_SAMR = fourCC('s', 'a', 'm', 'r');

    protected static final int BOX_ID_SAWB = fourCC('s', 'a', 'w', 'b');

    protected static final int BOX_ID_SIDX = fourCC('s', 'i', 'd', 'x');

    protected static final int BOX_ID_S263 = fourCC('s', '2', '6', '3');

    protected static final int BOX_ID_H263 = fourCC('H', '2', '6', '3');

    protected static final int BOX_ID_H263_2 = fourCC('h', '2', '6', '3');

    protected static final int BOX_ID_DOTMP3 = fourCC('.', 'm', 'p', '3');

    protected static final int BOX_ID_ALAC = fourCC('a', 'l', 'a', 'c');

    protected static final int BOX_ID_PASP = fourCC('p', 'a', 's', 'p');

    protected static final int BOX_ID_FLGS = fourCC('f', 'l', 'g', 's');

    protected static final int BOX_ID_MFHD = fourCC('m', 'f', 'h', 'd');

    // iTunes metadata
    protected static final int BOX_ID_ILST = fourCC('i', 'l', 's', 't');

    protected static final int BOX_ID_ATNAM = fourCC((char)0xA9, 'n', 'a', 'm'); // title

    protected static final int BOX_ID_ATALB = fourCC((char)0xA9, 'a', 'l', 'b'); // album

    protected static final int BOX_ID_ATART = fourCC((char)0xA9, 'A', 'R', 'T'); // artist

    protected static final int BOX_ID_AART = fourCC('a', 'A', 'R', 'T'); // albumartist

    protected static final int BOX_ID_ATDAY = fourCC((char)0xA9, 'd', 'a', 'y'); // year

    protected static final int BOX_ID_TRKN = fourCC('t', 'r', 'k', 'n'); // tracknumber

    protected static final int BOX_ID_ATGEN = fourCC((char)0xA9, 'g', 'e', 'n'); // genre

    protected static final int BOX_ID_GNRE = fourCC('g', 'n', 'r', 'e'); // genre

    protected static final int BOX_ID_CPIL = fourCC('c', 'p', 'i', 'l'); // compilation

    protected static final int BOX_ID_ATWRT = fourCC((char)0xA9, 'w', 'r', 't'); // writer

    protected static final int BOX_ID_DISK = fourCC('d', 'i', 's', 'k'); // disknumber

    protected static final int BOX_ID_COVR = fourCC('c', 'o', 'v', 'r'); // cover art

    protected static final int BOX_ID_DATA = fourCC('d', 'a', 't', 'a');

    // 3GPP metadata
    protected static final int BOX_ID_TITL = fourCC('t', 'i', 't', 'l'); // title

    protected static final int BOX_ID_PERF = fourCC('p', 'e', 'r', 'f'); // artist

    protected static final int BOX_ID_AUTH = fourCC('a', 'u', 't', 'h'); // writer

    protected static final int BOX_ID_ALBM = fourCC('a', 'l', 'b', 'm'); // album
                                                                         // title
                                                                         // and
                                                                         // tracknumber

    protected static final int BOX_ID_YRRC = fourCC('y', 'r', 'r', 'c'); // year

    // ID3v2 metadata
    protected static final int BOX_ID_ID32 = fourCC('I', 'D', '3', '2');

    protected static final int ID3_KEY_COMPILATION = fourCC('T', 'C', 'M', 'P');

    protected static final int ID3_KEY_AUTHOR = fourCC('T', 'E', 'X', 'T');

    protected static final int ID3_KEY_COMPOSER = fourCC('T', 'C', 'O', 'M');

    protected static final int ID3_KEY_DISC_NUMBER = fourCC('T', 'P', 'O', 'S');

    protected static final int HANDLER_TYPE_VIDEO = fourCC('v', 'i', 'd', 'e');

    protected static final int HANDLER_TYPE_AUDIO = fourCC('s', 'o', 'u', 'n');

    protected static final int HANDLER_TYPE_SUBTITLE = fourCC('s', 'u', 'b', 't');

    protected static final int HANDLER_TYPE_HMMP_SUBTITLE = fourCC('G', 'R', 'A', 'P');

    protected static final int HANDLER_TYPE_OBJECT_DESCRIPTOR = fourCC('o', 'd', 's', 'm');

    protected static final int sNALHeaderSize = 4;

    protected static final int AVC_NAL_UNIT_TYPE_IDR_PICTURE = 5;

    protected static final int AVC_NAL_UNIT_TYPE_SPS = 7;

    protected static final int HEVC_NAL_UNIT_TYPE_IDR_PICTURE_W_RADL = 19;

    protected static final int HEVC_NAL_UNIT_TYPE_IDR_PICTURE_N_LP = 20;

    protected static final int HEVC_NAL_UNIT_TYPE_CRA_PICTURE = 21;

    protected static final String UUID_SOMD = "736F6D6489094E5DBE807CA58018263B";

    protected IsoTrack mCurrentAudioTrack;

    protected IsoTrack mCurrentVideoTrack;

    protected IsoTrack mCurrentSubtitleTrack;

    protected boolean mIsParsingTrack = false;

    protected boolean mIsFragmented = false;

    protected IsoTrack mCurrentTrack;

    protected long mFileTimescale = 1;

    protected Traf mCurrentTrackFragment;

    protected long mFirstMoofOffset = -1;

    protected long mCurrentMoofOffset = 0;

    protected long mPrevTrunDataSize = 0;

    protected ArrayList<IsoTrack> mMfraTracks;

    protected ArrayDeque<BoxHeader> mCurrentBoxSequence;

    protected boolean mParseODSMData = false;

    protected MediaFormat mCurrentMediaFormat;

    protected String mCurrentMetaDataKey = null;

    private long mMoofDataSize;

    protected int mCurrentMoofTrackId = -1;

    protected int mCurrentTrackId = -1;

    protected boolean mSkipInsertSamples = false;

    protected boolean mDesiredTrackIdFound = false;

    protected boolean mFoundMfra = false;

    protected boolean mMdatFound = false;

    protected boolean mParsedSencData = false;

    protected int mNALLengthSize;

    protected final ArrayList<IsoTrack> mTracks = new ArrayList<>(2);

    private static final int[] ISOBMFF_COMPATIBLE_BRANDS = {
            fourCC('i', 's', 'o', 'm'), fourCC('m', 'p', '4', '1'), fourCC('m', 'p', '4', '2'),
            fourCC('a', 'v', 'c', '1'), fourCC('3', 'g', 'p', '5'), fourCC('h', 'v', 'c', '1')
    };

    public ISOBMFFParser(DataSource source) {
        super(source);
        if (LOGS_ENABLED) Log.v(TAG, "create ISOBMFFParser from source");
    }

    protected static int fourCC(char c1, char c2, char c3, char c4) {
        return c1 << 24 | c2 << 16 | c3 << 8 | c4;
    }

    protected static String ccruof(long id) {
        return String.valueOf((char) ((id & 0x00000000FF000000) >> 24)) +
                (char) ((id & 0x0000000000FF0000) >> 16) +
                (char) ((id & 0x000000000000FF00) >> 8) +
                (char) (id & 0x00000000000000FF);
    }

    @Override
    public boolean parse() {
        if (LOGS_ENABLED) Log.v(TAG, "ISOBMFFParser parse");
        if (mIsParsed) {
            return mParseResult;
        }

        mIsParsed = true;

        boolean parseOK = true;

        initParsing();

        try {
            long sourceLength = mDataSource.length();
            BoxHeader nextHeader;
            mCurrentOffset = 0;
            while (!mInitDone && (mCurrentOffset < sourceLength || sourceLength == -1) &&
                    parseOK) {
                nextHeader = getNextBoxHeader();
                if (nextHeader == null) {
                    if (LOGS_ENABLED) Log.e(TAG, "Could not read next box header");
                    parseOK = false;
                } else {
                    parseOK = parseBox(nextHeader);
                }
            }

            if (!mInitDone && mMdatFound && mTracks.size() > 0 && !mIsFragmented) {
                mInitDone = true;
            }

            parseOK = mInitDone;

            if (parseOK && mIsFragmented && !mFoundMfra && sourceLength != -1) {
                long curOffset = mCurrentOffset;
                // read mfra at end of file
                mCurrentOffset = sourceLength - 16;
                nextHeader = getNextBoxHeader();
                if (nextHeader != null && nextHeader.boxType == BOX_ID_MFRO) {
                    byte[] buffer = new byte[4];
                    mDataSource.readAt(sourceLength - buffer.length, buffer,
                            buffer.length);
                    int mfraLength = ((buffer[0] & 0xFF) << 24 | (buffer[1] & 0xFF) << 16
                            | (buffer[2] & 0xFF) << 8 | buffer[3] & 0xFF);
                    mCurrentOffset = sourceLength - mfraLength;
                    nextHeader = getNextBoxHeader();
                    if (nextHeader.boxType == BOX_ID_MFRA) {
                        parseOK = parseBox(nextHeader);
                    } else {
                        if (LOGS_ENABLED) Log.w(TAG, "No mfra at end of file");
                    }
                } else {
                    if (LOGS_ENABLED) Log.w(TAG, "No mfro at end of file");
                }
                mCurrentOffset = curOffset;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing content", e);
            return false;
        }

        if (mCurrentVideoTrack != null) {
            mMetaDataValues.put(KEY_MIME_TYPE, MimeType.MP4_VIDEO);
        } else {
            mMetaDataValues.put(KEY_MIME_TYPE, MimeType.MP4_AUDIO);
        }

        mMetaDataValues.put(MetaData.KEY_PAUSE_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_SEEK_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_NUM_TRACKS, mTracks.size());

        if (parseOK) {
            updateAspectRatio();

            updateRotation();

            if (mCurrentAudioTrack != null) {
                mCurrentAudioTrack.buildSampleTable();
            }

            if (mCurrentVideoTrack != null) {
                mCurrentVideoTrack.buildSampleTable();
            }

            if (mCurrentSubtitleTrack != null) {
                mCurrentSubtitleTrack.buildSampleTable();
            }

            long firstOffset = 0;
            if (mIsFragmented) {
                firstOffset = mFirstMoofOffset;
            } else {
                if (mCurrentVideoTrack != null) {
                    firstOffset = mCurrentVideoTrack.getSampleTable().getOffset(0);
                }

                if (mCurrentAudioTrack != null) {
                    long audioOffset = mCurrentAudioTrack.getSampleTable().getOffset(0);
                    if (firstOffset == 0 || audioOffset < firstOffset) {
                        firstOffset = audioOffset;
                    }
                }
            }

            if (mDataSource.hasDataAvailable(firstOffset, 1) == DataAvailability.NOT_AVAILABLE) {
                try {
                    mDataSource.requestReadPosition(firstOffset);
                } catch (IOException e) {
                }
            }
        }

        mParseResult = parseOK;

        return parseOK;
    }

    @Override
    public int getTrackCount() {
        return mTracks.size();
    }

    @Override
    public MetaData getTrackMetaData(int index) {
        IsoTrack t = mTracks.get(index);
        if (t != null) {
            return t.getMetaData();
        }
        return null;
    }

    protected void updateAspectRatio() {
        if (mCurrentVideoTrack != null) {
            MediaFormat videoFormat = mCurrentVideoTrack.getMediaFormat();
            // pasp information has higher priority than sample aspect ratio
            if (videoFormat.containsKey(MetaData.KEY_PASP_HORIZONTAL_SPACING)
                    && videoFormat.containsKey(MetaData.KEY_PASP_VERTICAL_SPACING)) {
                int paspHSpacing = videoFormat.getInteger(MetaData.KEY_PASP_HORIZONTAL_SPACING);
                int paspVSpacing = videoFormat.getInteger(MetaData.KEY_PASP_VERTICAL_SPACING);

                int adjustedWidth = (int)Math.round(((double)videoFormat
                        .getInteger(MediaFormat.KEY_WIDTH) * paspHSpacing)
                        / paspVSpacing);

                addMetaDataValue(KEY_WIDTH, adjustedWidth);
                mCurrentVideoTrack.getMetaData().addValue(KEY_WIDTH, adjustedWidth);
            } else if (videoFormat.containsKey(MetaData.KEY_SAR_WIDTH)
                    && videoFormat.containsKey(MetaData.KEY_SAR_HEIGHT)) {
                int sarWidth = videoFormat.getInteger(MetaData.KEY_SAR_WIDTH);
                int sarHeight = videoFormat.getInteger(MetaData.KEY_SAR_HEIGHT);

                int adjustedWidth = (int)Math.round(((double)videoFormat
                        .getInteger(MediaFormat.KEY_WIDTH) * sarWidth)
                        / sarHeight);

                addMetaDataValue(KEY_WIDTH, adjustedWidth);
                mCurrentVideoTrack.getMetaData().addValue(KEY_WIDTH, adjustedWidth);
            }
        }
    }

    private void updateRotation() {
        if (getMetaData().containsKey(MetaData.KEY_ROTATION_DEGREES)) {
            int rotationAngle = getMetaData().getInteger(MetaData.KEY_ROTATION_DEGREES);
            for (IsoTrack track : mTracks) {
                if (track.getTrackType() == TrackType.VIDEO) {
                    track.getMediaFormat()
                            .setInteger(MetaData.KEY_ROTATION_DEGREES, rotationAngle);
                }
            }
        }
    }

    protected void initParsing() {
        mCurrentOffset = 0;
        mCurrentBoxSequence = new ArrayDeque<>(10);
    }

    @Override
    public synchronized AccessUnit dequeueAccessUnit(TrackType type) {
        IsoTrack currentTrack;

        if (type == TrackType.AUDIO && mCurrentAudioTrack != null) {
            currentTrack = mCurrentAudioTrack;
        } else if (type == TrackType.VIDEO && mCurrentVideoTrack != null) {
            currentTrack = mCurrentVideoTrack;
        } else if (type == TrackType.SUBTITLE && mCurrentSubtitleTrack != null) {
            currentTrack = mCurrentSubtitleTrack;
        } else {
            return null;
        }

        return currentTrack.dequeueAccessUnit(mIsFragmented);
    }

    protected BoxHeader getNextBoxHeader() {
        long startOffset = mCurrentOffset;
        byte[] buffer = new byte[8];
        try {
            if (mDataSource.readAt(mCurrentOffset, buffer, 8) != 8) {
                if (LOGS_ENABLED) Log.e(TAG, "could not read 8 bytes for header");
                return null;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error reading next header from source", e);
            return null;
        }

        mCurrentOffset += 8;

        if (LOGS_ENABLED)
            Log.v(TAG, "boxSize bytes = " + buffer[0] + " " + buffer[1] + " " + buffer[2] + " "
                    + buffer[3] + " ");

        long boxSize = ((long)buffer[0] & 0xFF) << 24 | ((long)buffer[1] & 0xFF) << 16
                | ((long)buffer[2] & 0xFF) << 8 | (long)buffer[3] & 0xFF;
        int boxType = (buffer[4] & 0xFF) << 24 | (buffer[5] & 0xFF) << 16
                | (buffer[6] & 0xFF) << 8 | buffer[7] & 0xFF;
        int boxHeaderSize = 8;

        if (boxSize == 1) {
            try {
                if (mDataSource.readAt(mCurrentOffset, buffer, 8) != 8) {
                    if (LOGS_ENABLED) Log.e(TAG, "could not read 8 bytes for extended size");
                    return null;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error while reading extended box size", e);
            }
            mCurrentOffset += 8;
            boxSize = ((long)(buffer[0] & 0xFF) << 56 | (long)(buffer[1] & 0xFF) << 48
                    | (long)(buffer[2] & 0xFF) << 40 | (long)(buffer[3] & 0xFF) << 32
                    | (long)(buffer[4] & 0xFF) << 24 | (long)(buffer[5] & 0xFF) << 16
                    | (long)(buffer[6] & 0xFF) << 8 | (long)buffer[7] & 0xFF);
            boxSize -= 8;
            boxHeaderSize += 8;
        }

        boxSize -= 8;

        BoxHeader boxHeader = new BoxHeader();
        boxHeader.boxHeaderSize = boxHeaderSize;
        boxHeader.boxDataSize = boxSize;
        boxHeader.boxType = boxType;
        boxHeader.startOffset = startOffset;

        if (LOGS_ENABLED)
            Log.v(TAG, "read box " + ccruof(boxHeader.boxType) + " with size " + boxSize
                    + " from offset " + startOffset);

        return boxHeader;
    }

    protected boolean parseBox(BoxHeader header) {
        if (header == null) {
            return false;
        }
        mCurrentBoxSequence.add(header);

        if (LOGS_ENABLED)
            Log.v(TAG, "parse box " + ccruof(header.boxType) + " with size " + header.boxDataSize);

        boolean parseOK = true;
        long boxEndOffset = mCurrentOffset + header.boxDataSize;
        if (header.boxType == BOX_ID_MOOV) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            // Merge tracks from moov and mfra
            if (mMfraTracks != null) {
                int numTracks = mTracks.size();
                int numMfraTracks = mMfraTracks.size();
                if (numMfraTracks > 0) {
                    for (int i = 0; i < numTracks; i++) {
                        IsoTrack track = mTracks.get(i);
                        for (int j = 0; j < numMfraTracks; j++) {
                            IsoTrack t = mMfraTracks.get(j);
                            if (t.getTrackId() == track.getTrackId()) {
                                track.setTfraList(t.getTfraList());
                                mMfraTracks.remove(j);
                                break;
                            }
                        }
                    }
                }
                mMfraTracks = null;
            }

            // Check for unsupported tracks
            int numTracks = mTracks.size();

            if (LOGS_ENABLED) Log.v(TAG, numTracks + " tracks, "
                    + "Video track " + getSelectedTrackIndex(TrackType.VIDEO)
                    + " Audio track " + getSelectedTrackIndex(TrackType.AUDIO)
                    + " Subtitle track " + getSelectedTrackIndex(TrackType.SUBTITLE));

            for (int i = 0; i < numTracks; i++) {
                IsoTrack track = mTracks.get(i);
                if (track.getMediaFormat() == null) {
                    if (LOGS_ENABLED)
                        Log.v(TAG, "Track " + i + " is unhandled, type " + track.getTrackType());
                    track.setTrackType(TrackType.UNKNOWN);
                } else {
                    if (LOGS_ENABLED)
                        Log.v(TAG, "Track " + i + " of type " + track.getTrackType() + " is OK");
                    track.setTrackIndex(i);
                }
            }

        } else if (header.boxType == BOX_ID_MVHD) {
            parseOK = parseMvhd(header);
        } else if (header.boxType == BOX_ID_TRAK) {
            mIsParsingTrack = true;
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            if (mParseODSMData) {
                if (!parseODSMData(mCurrentTrack)) {
                    if (LOGS_ENABLED)
                        Log.e(TAG, "Error while parsing ODSM track");
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
                mParseODSMData = false;
            }
            if (parseOK) {
                if (mCurrentTrack.getTrackType() == TrackType.AUDIO
                        && mCurrentAudioTrack == null) {
                    if (LOGS_ENABLED) Log.v(TAG,
                            "Setting audio track to " + mCurrentTrack.getTrackId());
                    mCurrentAudioTrack = mCurrentTrack;
                } else if (mCurrentTrack.getTrackType() == TrackType.VIDEO
                        && mCurrentVideoTrack == null) {
                    if (LOGS_ENABLED) Log.v(TAG,
                            "Setting video track to " + mCurrentTrack.getTrackId());
                    mCurrentVideoTrack = mCurrentTrack;
                }
            }
            mIsParsingTrack = false;
        } else if (header.boxType == BOX_ID_TKHD) {
            parseOK = readTkhd(header);
        } else if (header.boxType == BOX_ID_MDIA) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_MDHD) {
            parseOK = parseMdhd(header);
        } else if (header.boxType == BOX_ID_HDLR) {
            parseOK = parseHdlr(header);
        } else if (header.boxType == BOX_ID_MINF) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_STBL) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            if (parseOK) {
                IsoTrack currentTrack = mTracks.get(mTracks.size() - 1);
                SampleTable sampleTable = currentTrack.getSampleTable();
                sampleTable.setTimescale(currentTrack.getTimeScale());

                if (!sampleTable.calculateSampleCountAndDuration()) {
                    if (LOGS_ENABLED) Log.w(TAG,
                            "Error while calculating sample count and duration");
                }
                int sampleCount = sampleTable.getSampleCount();
                long trackDurationUs = currentTrack.getDurationUs();
                if (trackDurationUs > 0) {
                    float frameRate = (sampleCount * 1000000.0f / trackDurationUs);
                    mCurrentTrack.getMetaData().addValue(KEY_FRAME_RATE, frameRate);
                } else {
                    mCurrentTrack.getMetaData().addValue(KEY_FRAME_RATE, 0f);
                }
            }
        } else if (header.boxType == BOX_ID_STSD) {
            // skip 4 for version and flags
            // skip 4 for entry_count
            mCurrentOffset += 8;
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }

            if (mCurrentTrack.getMediaFormat() == null) {
                if (LOGS_ENABLED) Log.w(TAG, "Error parsing handler in 'stsd' box");
                mCurrentTrack.setTrackType(TrackType.UNKNOWN);
            }
        } else if (header.boxType == BOX_ID_AVC1 || header.boxType == BOX_ID_AVC3) {
            byte[] data = new byte[78];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error while parsing 'avc1' box", e);
                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.AVC);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.AVC);

            parseVisualSampleEntry(data);

            // TODO: Update this when we add support for nalSize other than 4
            mCurrentMediaFormat.setInteger("nal-size", 4);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);

        } else if (header.boxType == BOX_ID_AVCC) {
            parseOK = parseAvcc(header);
        } else if (header.boxType == BOX_ID_STTS) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'stts' box", e);
            }
            mCurrentTrack.getSampleTable().setSttsData(data);
        } else if (header.boxType == BOX_ID_STSZ) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'stsz' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setStszData(data);
        } else if (header.boxType == BOX_ID_CTTS) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'ctts' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setCttsData(data);
        } else if (header.boxType == BOX_ID_STSC) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'stsc' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setStscData(data);
        } else if (header.boxType == BOX_ID_STSS) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'stss' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setStssData(data);
        } else if (header.boxType == BOX_ID_STCO) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'stco' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setStcoData(data);
        } else if (header.boxType == BOX_ID_CO64) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'co64' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            mCurrentTrack.getSampleTable().setCo64Data(data);
        } else if (header.boxType == BOX_ID_MP4V) {
            byte[] data = new byte[78];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'mp4v' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.MPEG4_VISUAL);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.MPEG4_VISUAL);

            // mp4v is a type of VisualSampleEntry
            parseVisualSampleEntry(data);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_MP4A || header.boxType == BOX_ID_SAMR
                || header.boxType == BOX_ID_SAWB) {
            byte[] data = new byte[28];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'mp4a' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            parseAudioSampleEntry(data);

            String mime = null;
            if (header.boxType == BOX_ID_SAMR) {
                mime = MimeType.AMR_NB;
            } else if (header.boxType == BOX_ID_SAWB) {
                mime = MimeType.AMR_WB;
            }

            if (mime != null) {
                mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, mime);
                mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, mime);
            }

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_DOTMP3) {
            byte[] data = new byte[28];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing '.mp3' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            parseAudioSampleEntry(data);

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.MP3);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.MP3);

            // no need to parse subboxes for mp3 format
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_ESDS) {
            // skip 4 for version and flags
            mCurrentOffset += 4;
            byte[] data = new byte[(int)header.boxDataSize - 4];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'esds' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
            parseOK = parseESDS(data);
        } else if (header.boxType == BOX_ID_STPP) {
            mCurrentOffset += header.boxDataSize;

            mCurrentMediaFormat = new MediaFormat();

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.TTML);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.TTML);

            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_MVEX) {
            if (LOGS_ENABLED) Log.v(TAG, "found 'mvex', setting fragmented to true");
            mIsFragmented = true;
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_MEHD) {
            int versionFlags;
            try {
                versionFlags = mDataSource.readInt();
                int version = (versionFlags >> 24) & 0xFF;

                long durationTicks;
                if (version == 1) {
                    durationTicks = mDataSource.readLong();
                } else {
                    durationTicks = mDataSource.readInt();
                }
                addMetaDataValue(KEY_DURATION, durationTicks * 1000 / mFileTimescale);
            } catch (EOFException e) {
                if (LOGS_ENABLED) Log.e(TAG, "EOFException while parsing 'mvex' box", e);
                mCurrentBoxSequence.removeLast();

                return false;
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'mehd' box", e);
                mCurrentBoxSequence.removeLast();

                return false;
            }
        } else if (header.boxType == BOX_ID_TREX) {
            try {
                Trex newTrex = new Trex();
                mDataSource.skipBytes(4); // version and flags
                int trackId = mDataSource.readInt();
                mDataSource.skipBytes(4); // Skip Default Sample Description Index
                newTrex.defaultSampleDuration = mDataSource.readInt();
                newTrex.defaultSampleSize = mDataSource.readInt();
                mDataSource.skipBytes(4); // Skip Default Sample Flags

                IsoTrack track = null;
                int numTracks = mTracks.size();
                for (int i = 0; i < numTracks; i++) {
                    IsoTrack t = mTracks.get(i);
                    if (t.getTrackId() == trackId) {
                        track = t;
                        break;
                    }
                }

                if (track == null) {
                    track = createTrack();
                    track.setTrackId(trackId);
                    mTracks.add(track);
                }

                track.setTrex(newTrex);
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'trex' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }
        } else if (header.boxType == BOX_ID_MOOF) {
            if (mFirstMoofOffset == -1) {
                mIsFragmented = true;
                mInitDone = true;
                mFirstMoofOffset = header.startOffset;
                mCurrentBoxSequence.removeLast();
                return true;
            }
            mCurrentMoofOffset = header.startOffset;
            mMoofDataSize = 0;
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_TRAF) {
            mParsedSencData = false;
            mCurrentTrackFragment = new Traf();
            mPrevTrunDataSize = 0;
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_TFHD) {
            parseOK = parseTfhd(header);
        } else if (header.boxType == BOX_ID_TRUN) {
            parseOK = parseTrun(header);
        } else if (header.boxType == BOX_ID_MFRA) {
            if (!mFoundMfra) {
                mMfraTracks = new ArrayList<>(2);
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mFoundMfra = true;
            }
        } else if (header.boxType == BOX_ID_TFRA) {
            parseOK = parseTfra(header);
        } else if (header.boxType == BOX_ID_ENCA) {
            byte[] data = new byte[28];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'enca' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            parseAudioSampleEntry(data);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_ENCV) {
            byte[] data = new byte[78];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'encv' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            parseVisualSampleEntry(data);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_FRMA) {
            try {
                int dataFormat = mDataSource.readInt();
                if (dataFormat == BOX_ID_AVC1) {
                    mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.AVC);
                    mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.AVC);
                } else if (dataFormat == BOX_ID_HVC1) {
                    mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.HEVC);
                    mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.HEVC);
                } else if (dataFormat == BOX_ID_MP4V) {
                    mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.MPEG4_VISUAL);
                    mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.MPEG4_VISUAL);
                } else if (dataFormat == BOX_ID_S263 || dataFormat == BOX_ID_H263 ||
                        dataFormat == BOX_ID_H263_2) {
                    mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.H263);
                    mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.H263);
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception while parsing 'frma' box", e);
                mCurrentBoxSequence.removeLast();
                return false;
            }
        } else if (header.boxType == BOX_ID_SCHM) {
            parseOK = parseSchm(header);
        } else if (header.boxType == BOX_ID_SCHI) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_EDTS) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_ELST) {
            parseOK = parseElst(header);
        } else if (header.boxType == BOX_ID_PSSH) {
            parseOK = parsePsshData(header);
        } else if (header.boxType == BOX_ID_TENC) {
            try {
                // Skip version, flags and algorithm id
                mDataSource.skipBytes(7);
                int ivSize = mDataSource.readByte();
                byte[] kID = new byte[16];
                mDataSource.read(kID);
                mCurrentTrack.setDefaultEncryptionData(ivSize, kID);
                parseOK = true;
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'tenc' box", e);
                parseOK = false;
            }
        } else if (header.boxType == BOX_ID_SENC) {
            parseOK = parseSenc();
        } else if (header.boxType == BOX_ID_SINF) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_HVC1 || header.boxType == BOX_ID_HEV1) {
            byte[] data = new byte[78];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'hvc1' box", e);
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.HEVC);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.HEVC);

            parseVisualSampleEntry(data);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_HVCC) {
            byte[] data = new byte[(int)header.boxDataSize];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'hvcc' box", e);
                return false;
            }

            byte[] hvccData = parseHvcc(data);
            if (hvccData == null) {
                return false;
            }
            ByteBuffer csd0 = ByteBuffer.wrap(hvccData);
            mCurrentMediaFormat.setByteBuffer("csd-0", csd0);
            mCurrentMediaFormat.setInteger("nal-length-size", mNALLengthSize);
        } else if (header.boxType == BOX_ID_UDTA) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_META) {
            mCurrentOffset += 4; // skip version and flags
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_ILST) {
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
        } else if (header.boxType == BOX_ID_ATNAM) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_TITLE;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ATALB) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_ALBUM;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ATART) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_ARTIST;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_AART) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_ALBUM_ARTIST;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ATDAY) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_YEAR;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_TRKN) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_TRACK_NUMBER;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ATGEN || header.boxType == BOX_ID_GNRE) {
            mCurrentMetaDataKey = KEY_GENRE;
            if (boxIsUnder(BOX_ID_ILST)) {
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
            } else { // 3gpp metadata value
                try {
                    mDataSource.skipBytes(4); // skip version and flags
                    mDataSource.skipBytes(2); // skip language code
                    byte[] buffer = new byte[(int)(header.boxDataSize - 6)];
                    mDataSource.read(buffer);
                    String metaDataValue;
                    if ((0xFF & buffer[0]) == 0xFF && (0xFF & buffer[1]) == 0xFE
                            || (0xFF & buffer[0]) == 0xFE && (0xFF & buffer[1]) == 0xFF) {
                        metaDataValue = new String(buffer, 0, buffer.length - 2,
                                StandardCharsets.UTF_16);
                    } else {
                        metaDataValue = new String(buffer, 0, buffer.length - 1,
                                StandardCharsets.UTF_8);
                    }
                    addMetaDataValue(mCurrentMetaDataKey, metaDataValue);
                } catch (IOException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IOException parsing 'gnre' box", e);
                    parseOK = false;
                }
            }
            mCurrentMetaDataKey = null;
        } else if (header.boxType == BOX_ID_CPIL) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_COMPILATION;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ATWRT) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_WRITER;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_DISK) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_DISC_NUMBER;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_COVR) {
            if (boxIsUnder(BOX_ID_ILST)) {
                mCurrentMetaDataKey = KEY_ALBUM_ART;
                while (mCurrentOffset < boxEndOffset && parseOK) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    parseOK = parseBox(nextBoxHeader);
                }
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_DATA) {
            parseOK = parseDataBox(header);
        } else if (header.boxType == BOX_ID_ID32) {
            parseOK = parseID3(header);
        } else if (header.boxType == BOX_ID_TITL) {
            if (!mMetaDataValues.containsKey(KEY_TITLE)) {
                mCurrentMetaDataKey = KEY_TITLE;
                parseOK = parse3GPPMetaDataString(header);
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_PERF) {
            if (!mMetaDataValues.containsKey(KEY_ARTIST)) {
                mCurrentMetaDataKey = KEY_ARTIST;
                parseOK = parse3GPPMetaDataString(header);
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_AUTH) {
            if (!mMetaDataValues.containsKey(KEY_AUTHOR)) {
                mCurrentMetaDataKey = KEY_AUTHOR;
                parseOK = parse3GPPMetaDataString(header);
                mCurrentMetaDataKey = null;
            }
        } else if (header.boxType == BOX_ID_ALBM) {
            if (!mMetaDataValues.containsKey(KEY_ALBUM)) {
                try {
                    mDataSource.skipBytes(4); // skip version and flags
                    mDataSource.skipBytes(2); // skip language code
                    byte[] buffer = new byte[(int)(header.boxDataSize - 6)];
                    mDataSource.read(buffer);
                    String metaDataValue;
                    if ((0xFF & buffer[0]) == 0xFF && (0xFF & buffer[1]) == 0xFE) {
                        if (buffer[buffer.length - 3] == 0 && buffer[buffer.length - 2] == 0) {
                            if (!mMetaDataValues.containsKey(KEY_TRACK_NUMBER)) {
                                String trackNumber = Byte.toString(buffer[buffer.length - 1]);
                                addMetaDataValue(KEY_TRACK_NUMBER, trackNumber);
                            }
                            metaDataValue = new String(buffer, 0, buffer.length - 3,
                                    StandardCharsets.UTF_16);
                        } else {
                            metaDataValue = new String(buffer, StandardCharsets.UTF_16);
                        }
                    } else if ((0xFF & buffer[0]) == 0xFE && (0xFF & buffer[1]) == 0xFF) {
                        if (buffer[buffer.length - 3] == 0 && buffer[buffer.length - 2] == 0) {
                            if (!mMetaDataValues.containsKey(KEY_TRACK_NUMBER)) {
                                String trackNumber = Byte.toString(buffer[buffer.length - 1]);
                                addMetaDataValue(KEY_TRACK_NUMBER, trackNumber);
                            }
                            metaDataValue = new String(buffer, 0, buffer.length - 3,
                                    StandardCharsets.UTF_16);
                        } else {
                            metaDataValue = new String(buffer, 0, buffer.length - 2,
                                    StandardCharsets.UTF_16);
                        }
                    } else {
                        if (buffer[buffer.length - 2] == 0) {
                            if (!mMetaDataValues.containsKey(KEY_TRACK_NUMBER)) {
                                String trackNumber = Byte.toString(buffer[buffer.length - 1]);
                                addMetaDataValue(KEY_TRACK_NUMBER, trackNumber);
                            }
                            metaDataValue = new String(buffer, 0, buffer.length - 2,
                                    StandardCharsets.UTF_8);
                        } else {
                            metaDataValue = new String(buffer, 0, buffer.length - 1,
                                    StandardCharsets.UTF_8);
                        }
                    }
                    addMetaDataValue(KEY_ALBUM, metaDataValue);
                } catch (IOException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IOException parsing 'albm' box", e);
                    parseOK = false;
                }
            }
        } else if (header.boxType == BOX_ID_YRRC) {
            try {
                mDataSource.skipBytes(4); // skip version and flags
                if (header.boxDataSize > 6) {
                    // This should be a 16 bit int according to spec, but some
                    // files have this as a string
                    mDataSource.skipBytes(2); // skip language code
                    byte[] buffer = new byte[(int)(header.boxDataSize - 6)];
                    mDataSource.read(buffer);
                    String metaDataValue;
                    if ((0xFF & buffer[0]) == 0xFF && (0xFF & buffer[1]) == 0xFE
                            || (0xFF & buffer[0]) == 0xFE && (0xFF & buffer[1]) == 0xFF) {
                        metaDataValue = new String(buffer, 0, buffer.length - 2,
                                StandardCharsets.UTF_16);
                    } else {
                        metaDataValue = new String(buffer, 0, buffer.length - 1,
                                StandardCharsets.UTF_8);
                    }
                    addMetaDataValue(KEY_YEAR, metaDataValue);
                } else {
                    int year = mDataSource.readShort();
                    String metaDataValue = Integer.toString(year);
                    addMetaDataValue(KEY_YEAR, metaDataValue);
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException parsing 'yrrc' box", e);
                parseOK = false;
            }
        } else if (header.boxType == BOX_ID_SIDX) {
            parseOK = parseSidx(header);
        } else if (header.boxType == BOX_ID_S263 || header.boxType == BOX_ID_H263 ||
                header.boxType == BOX_ID_H263_2) {
            byte[] data = new byte[78];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error while parsing h263 visual entry box", e);
                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat = new MediaFormat();

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.H263);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.H263);

            parseVisualSampleEntry(data);

            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_ALAC) {
            byte[] data = new byte[28];
            try {
                if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
                mCurrentMediaFormat = new MediaFormat();

                parseAudioSampleEntry(data);

                // parse alac magic cookie
                int magicCookieSize = mDataSource.readInt();
                int magicCookieType = mDataSource.readInt();
                if (magicCookieType != BOX_ID_ALAC) {
                    if (LOGS_ENABLED) Log.e(TAG, "malformed alac magic cookie");
                    mCurrentBoxSequence.removeLast();
                    return false;
                }
                mDataSource.skipBytes(4); // version and flags
                byte[] magicCookie = new byte[magicCookieSize - 12];
                mDataSource.read(magicCookie);
                mCurrentMediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(magicCookie));
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'alac' box", e);

                mCurrentBoxSequence.removeLast();
                return false;
            }

            mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.ALAC);
            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.ALAC);

            mCurrentTrack.addSampleDescriptionEntry(mCurrentMediaFormat);
        } else if (header.boxType == BOX_ID_PASP) {
            parseOK = parsePasp(header);
        } else if (header.boxType == BOX_ID_UUID) {
            parseOK = parseUuid(header);
        } else if (header.boxType == BOX_ID_MDAT) {
            parseOK = parseMdat();
        } else {
            long skipSize = header.boxDataSize;
            try {
                while (skipSize > Integer.MAX_VALUE) {
                    mDataSource.skipBytes(Integer.MAX_VALUE);
                    skipSize -= Integer.MAX_VALUE;
                }
                if (skipSize > 0) {
                    mDataSource.skipBytes(skipSize);
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "could not skip box");

                mCurrentBoxSequence.removeLast();
                parseOK = false;
            }
        }
        mCurrentOffset = boxEndOffset;
        mCurrentBoxSequence.removeLast();
        return parseOK;
    }

    protected boolean parseMdat() {
        mMdatFound = true;
        return true;
    }

    protected boolean parseAvcc(BoxHeader header) {
        byte[] data = new byte[(int)header.boxDataSize];
        try {
            if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                return false;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error while parsing 'avcc' box", e);
            return false;
        }

        AvccData avccData = parseAvccData(data);
        if (avccData == null) {
            return false;
        }
        ByteBuffer csd0 = ByteBuffer.wrap(avccData.spsBuffer.array());
        ByteBuffer csd1 = ByteBuffer.wrap(avccData.ppsBuffer.array());
        mCurrentMediaFormat.setByteBuffer("csd-0", csd0);
        mCurrentMediaFormat.setByteBuffer("csd-1", csd1);
        mCurrentMediaFormat.setInteger("nal-length-size", mNALLengthSize);

        parseSPS(avccData.spsBuffer.array());
        return true;
    }

    protected boolean parseSchm(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            mDataSource.skipBytes(8); // scheme_type and scheme_version
            if ((versionFlags & 0x01) != 0) {
                // TODO read scheme_uri if we're interested
                // byte[] data = new byte[(int)header.boxDataSize - 12];
                // mDataSource.read(data);
                mDataSource.skipBytes(header.boxDataSize - 12);
            }
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'schm' box", e);

            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'schm' box", e);
            return false;
        }
        return true;
    }

    protected boolean parseSenc() {
        if (mCurrentMoofTrackId == mCurrentTrackId && !mSkipInsertSamples && !mParsedSencData) {
            mParsedSencData = true;
            try {
                int versionFlags = mDataSource.readInt();

                int sampleCount = mDataSource.readInt();

                ArrayList<CryptoInfo> cryptoInfos = new ArrayList<>(sampleCount);

                for (int i = 0; i < sampleCount; i++) {
                    CryptoInfo info = new CryptoInfo();
                    info.mode = MediaCodec.CRYPTO_MODE_AES_CTR;
                    info.iv = new byte[16];
                    if (mCurrentTrack.mDefaultIVSize == 16) {
                        mDataSource.read(info.iv);
                    } else {
                        // pad IV data to 128 bits
                        byte[] iv = new byte[8];
                        mDataSource.read(iv);
                        System.arraycopy(iv, 0, info.iv, 0, 8);
                    }
                    if ((versionFlags & 0x00000002) > 0) {
                        short subSampleCount = mDataSource.readShort();
                        info.numSubSamples = subSampleCount;
                        info.numBytesOfClearData = new int[subSampleCount];
                        info.numBytesOfEncryptedData = new int[subSampleCount];
                        for (int j = 0; j < subSampleCount; j++) {
                            info.numBytesOfClearData[j] = mDataSource.readShort();
                            info.numBytesOfEncryptedData[j] = mDataSource.readInt();
                        }
                    } else {
                        info.numSubSamples = 1;
                        info.numBytesOfClearData = new int[1];
                        info.numBytesOfClearData[0] = 0;
                        info.numBytesOfEncryptedData = new int[1];
                        info.numBytesOfEncryptedData[0] = -1;
                    }

                    if (info.numBytesOfClearData[0] == 0 && mCurrentTrack
                            .getTrackType() == TrackType.VIDEO) {
                        info.iv[15] = (byte)mNALLengthSize;
                    }

                    cryptoInfos.add(info);
                }

                mCurrentTrack.addCryptoInfos(cryptoInfos);
            } catch (EOFException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'senc' box", e);
                return false;
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'senc' box", e);
                return false;
            }
        }
        return true;
    }

    protected boolean boxIsUnder(int boxType) {
        for (BoxHeader header : mCurrentBoxSequence) {
            if (header.boxType == boxType) {
                return true;
            }
        }
        return false;
    }

    protected boolean parseID3(BoxHeader header) {
        long id3size = header.boxDataSize;
        long currentOffset = 0;

        try {
            mDataSource.skipBytes(6); // skip version, flags and language

            byte[] id3header = new byte[3];
            mDataSource.read(id3header);

            if (id3header[0] == 0x49 && // 'I'
                    id3header[1] == 0x44 && // 'D'
                    id3header[2] == 0x33 // '3'
            ) {
                mDataSource.skipBytes(2); // skip major version and revision

                int flags = mDataSource.readByte();
                mDataSource.skipBytes(4); // padding

                currentOffset += 16;

                if ((flags & (1 << 6)) > 0) {
                    // Extended ID3 header
                    int extendedHeaderSize = mDataSource.readInt();
                    mDataSource.skipBytes(extendedHeaderSize);

                    currentOffset += 4 + extendedHeaderSize;
                }

                while (currentOffset + 10 < id3size) {
                    int frameId = mDataSource.readInt();
                    int frameSize = mDataSource.readInt();

                    mDataSource.skipBytes(2); // skip flags

                    currentOffset += 10 + frameSize;

                    if (frameId == ID3_KEY_COMPILATION) {
                        if (!mMetaDataValues.containsKey(KEY_COMPILATION)) {
                            String id3String = readID3String(frameSize);
                            addMetaDataValue(KEY_COMPILATION, id3String);
                        } else {
                            mDataSource.skipBytes(frameSize);
                        }
                    } else if (frameId == ID3_KEY_AUTHOR) {
                        if (!mMetaDataValues.containsKey(KEY_AUTHOR)) {
                            String id3String = readID3String(frameSize);
                            addMetaDataValue(KEY_AUTHOR, id3String);
                        } else {
                            mDataSource.skipBytes(frameSize);
                        }
                    } else if (frameId == ID3_KEY_COMPOSER) {
                        if (!mMetaDataValues.containsKey(KEY_COMPOSER)) {
                            String id3String = readID3String(frameSize);
                            addMetaDataValue(KEY_COMPOSER, id3String);
                        } else {
                            mDataSource.skipBytes(frameSize);
                        }
                    } else if (frameId == ID3_KEY_DISC_NUMBER) {
                        if (!mMetaDataValues.containsKey(KEY_DISC_NUMBER)) {
                            String id3String = readID3String(frameSize);
                            addMetaDataValue(KEY_DISC_NUMBER, id3String);
                        } else {
                            mDataSource.skipBytes(frameSize);
                        }
                    } else {
                        mDataSource.skipBytes(frameSize);
                    }
                }

            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException parsing id3 box", e);
            return false;
        }

        return true;
    }

    private String readID3String(int frameSize) {
        String metadataString = null;
        try {
            int encoding = mDataSource.readByte();
            if (frameSize > 1) {
                if (encoding == 0) {
                    // ISO 8859-1
                    byte[] buffer = new byte[frameSize - 1];
                    mDataSource.read(buffer);
                    metadataString = new String(buffer, StandardCharsets.ISO_8859_1);
                } else if (encoding == 2) {
                    // UTF-16
                    int bom = mDataSource.readShort();
                    byte[] buffer = new byte[frameSize - 3];
                    short little_endian = (short)0xFFFE;
                    mDataSource.read(buffer);
                    if (bom == little_endian) {
                        metadataString = new String(buffer, StandardCharsets.UTF_16LE);
                    } else {
                        metadataString = new String(buffer, StandardCharsets.UTF_16BE);
                    }
                } else if (encoding == 3) {
                    // UTF-8
                    byte[] buffer = new byte[frameSize - 1];
                    mDataSource.read(buffer);
                    metadataString = new String(buffer, StandardCharsets.UTF_8);
                } else {
                    // UCS-2
                    int bom = mDataSource.readShort();
                    byte[] buffer = new byte[frameSize - 3];
                    mDataSource.read(buffer);
                    short little_endian = (short)0xFFFE;
                    if (bom == little_endian) {
                        for (int i = 0; i < buffer.length; i += 2) {
                            byte tempByte = buffer[i];
                            buffer[i] = buffer[i + 1];
                            buffer[i + 1] = tempByte;
                        }
                    }
                    metadataString = new String(buffer, "UCS-2");
                }
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOEception reading ID3 string", e);
        }
        return metadataString;
    }

    private boolean parse3GPPMetaDataString(BoxHeader header) {
        try {
            mDataSource.skipBytes(4); // skip version and flags
            mDataSource.skipBytes(2); // skip language code
            byte[] buffer = new byte[(int)(header.boxDataSize - 6)];
            mDataSource.read(buffer);
            String metaDataValue;
            if ((0xFF & buffer[0]) == 0xFF && (0xFF & buffer[1]) == 0xFE
                    || (0xFF & buffer[0]) == 0xFE && (0xFF & buffer[1]) == 0xFF) {
                metaDataValue = new String(buffer, 0, buffer.length - 2,
                        StandardCharsets.UTF_16);
            } else {
                metaDataValue = new String(buffer, 0, buffer.length - 1,
                        StandardCharsets.UTF_8);
            }
            addMetaDataValue(mCurrentMetaDataKey, metaDataValue);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException parsing 'titl' box", e);
            return false;
        }
        return true;
    }

    protected boolean parseODSMData(IsoTrack odsmTrack) {
        // empty implementation, interested subclasses should implement it
        return true;
    }

    protected boolean parsePsshData(BoxHeader header) {

        try {
            byte[] psshData = new byte[(int)header.boxDataSize];
            mDataSource.read(psshData);

            String systemIDString = Util.bytesToHex(psshData, 4, 16);
            byte[] systemId = new byte[16];
            System.arraycopy(psshData, 4, systemId, 0, 16);

            if (systemIDString.equals(MARLIN_SYSTEM_ID)) {
                int boxSize = (int)(header.boxHeaderSize + header.boxDataSize);
                byte[] psshBox = new byte[(int)(header.boxHeaderSize + header.boxDataSize) + 4];
                psshBox[0] = (byte)((boxSize & 0xFF000000) >> 24);
                psshBox[1] = (byte)((boxSize & 0x00FF0000) >> 16);
                psshBox[2] = (byte)((boxSize & 0x0000FF00) >> 8);
                psshBox[3] = (byte)(boxSize & 0x000000FF);
                psshBox[4] = (byte)((boxSize & 0xFF000000) >> 24);
                psshBox[5] = (byte)((boxSize & 0x00FF0000) >> 16);
                psshBox[6] = (byte)((boxSize & 0x0000FF00) >> 8);
                psshBox[7] = (byte)(boxSize & 0x000000FF);
                psshBox[8] = (byte)((header.boxType & 0xFF000000) >> 24);
                psshBox[9] = (byte)((header.boxType & 0x00FF0000) >> 16);
                psshBox[10] = (byte)((header.boxType & 0x0000FF00) >> 8);
                psshBox[11] = (byte)(header.boxType & 0x000000FF);

                System.arraycopy(psshData, 0, psshBox, 12, psshData.length);

                byte[][] kids = new byte[mTracks.size()][];

                for (int i = 0; i < mTracks.size(); i++) {
                    kids[i] = (mTracks.get(i)).mKID;
                }

                String psshJson = Util.getMarlinPSSHTable(psshBox, kids);

                for (int j = 0; j < mTracks.size(); j++) {
                    mTracks.get(j).getMediaFormat().setString(KEY_MARLIN_JSON, psshJson);
                }
            }

            byte[] rawPssh = new byte[psshData.length - 24];

            System.arraycopy(psshData, 24, rawPssh, 0, rawPssh.length);

            addMetaDataValue(KEY_DRM_UUID, systemId);

            addMetaDataValue(KEY_DRM_PSSH_DATA, rawPssh);

            return true;
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing pssh data", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing pssh data", e);
            return false;
        } catch (JSONException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing pssh data", e);
            return false;
        }
    }

    private boolean parseElst(BoxHeader header) {
        try {
            mDataSource.skipBytes(4); // version and flags
            int entryCount = mDataSource.readInt();
            for (int i = 0; i < entryCount; i++) {
                long segmentDurationTicks;
                long mediaTimeTicks;
                // if (((versionFlags >> 24) & 0xFF) == 1){
                // segmentDuration = mDataSource.readLong();
                // mediaTime = mDataSource.readLong();
                // } else {
                segmentDurationTicks = mDataSource.readInt();
                mediaTimeTicks = mDataSource.readInt();
                // }
                short mediaRateInteger = mDataSource.readShort();
                short mediaRateFraction = mDataSource.readShort();
                long segmentDurationUs = segmentDurationTicks * 1000000 / mFileTimescale;
                long mediaTimeUs = mediaTimeTicks * 1000000 / mFileTimescale;
                // TODO: Do something useful with this information
            }
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'elst' box", e);

            mCurrentBoxSequence.removeLast();
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'elst' box", e);

            mCurrentBoxSequence.removeLast();
            return false;
        }
        return true;
    }

    private boolean parseTfra(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            int version = (versionFlags >> 24) & 0xFF;

            int trackId = mDataSource.readInt();
            int lengths = mDataSource.readInt();
            int lengthSizeOfTrafNum = ((lengths >> 4) & 0x00000003) + 1;
            int lengthSizeOfTrunNum = ((lengths >> 2) & 0x00000003) + 1;
            int lengthSizeOfSampleNum = (lengths & 0x00000003) + 1;
            int numberOfEntry = mDataSource.readInt();
            ArrayList<Tfra> tfraList = new ArrayList<>(numberOfEntry);
            for (int i = 0; i < numberOfEntry; i++) {
                long timeTicks;
                long moofOffset;
                if (version == 1) {
                    timeTicks = mDataSource.readLong();
                    moofOffset = mDataSource.readLong();
                } else {
                    timeTicks = mDataSource.readUint();
                    moofOffset = mDataSource.readUint();
                }
                byte[] lengthsData = new byte[lengthSizeOfTrafNum + lengthSizeOfTrunNum
                        + lengthSizeOfSampleNum];
                mDataSource.read(lengthsData);
                long sampleNumber = 0;
                for (int j = lengthSizeOfTrafNum + lengthSizeOfTrunNum;
                        j < lengthsData.length; j++) {
                    sampleNumber = sampleNumber << 8 & (lengthsData[j] & 0xFF);
                }
                Tfra tfra = new Tfra();
                tfra.timeTicks = timeTicks;
                tfra.moofOffset = moofOffset;
                tfra.sampleNumber = sampleNumber;
                tfraList.add(tfra);
            }
            IsoTrack track = null;
            int numTracks = mTracks.size();
            for (int i = 0; i < numTracks; i++) {
                IsoTrack t = mTracks.get(i);
                if (t.getTrackId() == trackId) {
                    track = t;
                    break;
                }
            }
            if (track != null) {
                track.setTfraList(tfraList);
            } else {
                track = createTrack();
                track.setTrackId(trackId);
                track.setTfraList(tfraList);
                mMfraTracks.add(track);
            }

        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException while parsing 'tfra' box", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'tfra' box", e);
            return false;
        }
        return true;
    }

    protected boolean parseTrun(BoxHeader header) {
        if (mCurrentMoofTrackId != mCurrentTrackId || mSkipInsertSamples) {
            return true;
        }
        try {
            int versionFlags = mDataSource.readInt();
            int sampleCount = mDataSource.readInt();
            int dataOffset = 0;
            ArrayList<FragmentSample> fragmentSamples = new ArrayList<>(sampleCount);
            if ((versionFlags & 0x000001) != 0) {
                dataOffset = mDataSource.readInt();
            }
            if ((versionFlags & 0x000004) != 0) {
                mDataSource.skipBytes(4);
            }
            long sumSampleSizes = 0;
            for (int i = 0; i < sampleCount; i++) {
                FragmentSample sample = new FragmentSample();
                if ((versionFlags & 0x000100) != 0) {
                    sample.durationTicks = mDataSource.readInt();
                } else if (mCurrentTrackFragment.defaultSampleDuration != Integer.MIN_VALUE) {
                    sample.durationTicks = mCurrentTrackFragment.defaultSampleDuration;
                } else {
                    Trex trex = mCurrentTrack.getTrex();
                    if (trex != null) {
                        sample.durationTicks = trex.defaultSampleDuration;
                    } else {
                        if (LOGS_ENABLED)
                            Log.e(TAG,
                                    "no applicable values for fragment sample duration available");

                        mCurrentBoxSequence.removeLast();
                        return false;
                    }
                }
                if ((versionFlags & 0x000200) != 0) {
                    sample.size = mDataSource.readInt();
                } else if (mCurrentTrackFragment.defaultSampleSize != Integer.MIN_VALUE) {
                    sample.size = mCurrentTrackFragment.defaultSampleSize;
                } else {
                    Trex trex = mCurrentTrack.getTrex();
                    if (trex != null) {
                        sample.size = trex.defaultSampleSize;
                    } else {
                        if (LOGS_ENABLED)
                            Log.e(TAG, "no applicable values for fragment sample size available");

                        mCurrentBoxSequence.removeLast();
                        return false;
                    }
                }
                if ((versionFlags & 0x000400) != 0) {
                    mDataSource.skipBytes(4);
                }
                if ((versionFlags & 0x000800) != 0) {
                    sample.compositionTimeOffset = mDataSource.readInt();
                }
                if ((versionFlags & 0x000001) != 0) {
                    sample.dataOffset = mCurrentTrackFragment.baseDataOffset + dataOffset
                            + sumSampleSizes;
                } else {
                    sample.dataOffset = mCurrentTrackFragment.baseDataOffset
                            + mPrevTrunDataSize + sumSampleSizes;
                }
                sumSampleSizes += sample.size;
                fragmentSamples.add(sample);
            }
            mPrevTrunDataSize += sumSampleSizes;
            mMoofDataSize += sumSampleSizes;
            mCurrentTrack.addFragmentSamples(fragmentSamples);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'trun' box", e);
            mCurrentBoxSequence.removeLast();
            return false;
        }
        return true;
    }

    protected boolean parseTfhd(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            int trackId = mDataSource.readInt();
            mCurrentMoofTrackId = trackId;
            if (trackId == mCurrentTrackId || mCurrentTrackId == -1) {
                if (mSkipInsertSamples) {
                    mDesiredTrackIdFound = true;
                    return true;
                }
            } else {
                if (LOGS_ENABLED)
                    Log.i(TAG, "parse tfhd for track " + trackId + ", but we're looking for track "
                            + mCurrentTrackId);
                return true;
            }
            IsoTrack track = null;
            int numTracks = mTracks.size();
            for (int i = 0; i < numTracks; i++) {
                IsoTrack t = mTracks.get(i);
                if (t.getTrackId() == trackId) {
                    track = t;
                    break;
                }
            }
            if (track != null) {
                mCurrentTrack = track;
            } else {
                if (LOGS_ENABLED)
                    Log.e(TAG, "track id " + trackId
                            + " in current 'trun' box does not exist in list of tracks");

                mCurrentBoxSequence.removeLast();
                return false;
            }
            if ((versionFlags & 0x000001) != 0) {
                mCurrentTrackFragment.baseDataOffset = mDataSource.readLong();
            } else {
                mCurrentTrackFragment.baseDataOffset = mCurrentMoofOffset;
            }
            if ((versionFlags & 0x000002) != 0) {
                mDataSource.skipBytes(4);
            }
            if ((versionFlags & 0x000008) != 0) {
                mCurrentTrackFragment.defaultSampleDuration = mDataSource.readInt();
            }
            if ((versionFlags & 0x000010) != 0) {
                mCurrentTrackFragment.defaultSampleSize = mDataSource.readInt();
            }
            if ((versionFlags & 0x000020) != 0) {
                mDataSource.skipBytes(4);
            }

        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'tfhd' box", e);
            mCurrentBoxSequence.removeLast();
            return false;
        }
        return true;
    }

    private boolean parseHdlr(BoxHeader header) {
        byte[] data = new byte[(int)header.boxDataSize];
        try {
            if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                mCurrentBoxSequence.removeLast();
                return false;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'hdlr' box", e);
            return false;
        }
        // skip 4 for version and flags
        // skip 4 for pre_defined
        int handlerType = (data[8] & 0xFF) << 24 | (data[9] & 0xFF) << 16 | (data[10] & 0xFF) << 8
                | data[11] & 0xFF;
        TrackType trackType = TrackType.UNKNOWN;
        if (handlerType == HANDLER_TYPE_AUDIO) {
            trackType = TrackType.AUDIO;
        } else if (handlerType == HANDLER_TYPE_VIDEO) {
            trackType = TrackType.VIDEO;
        } else if (handlerType == HANDLER_TYPE_HMMP_SUBTITLE
                || handlerType == HANDLER_TYPE_SUBTITLE) {
            trackType = TrackType.SUBTITLE;
        } else {
            if (LOGS_ENABLED && mIsParsingTrack)
                Log.d(TAG, "Track " + mCurrentTrack.getTrackId() + " is unknown");
        }
        // skip 12 for reserved
        // skip remaining bytes for name
        if (mIsParsingTrack) {
            mCurrentTrack.setTrackType(trackType);
            if (handlerType == HANDLER_TYPE_OBJECT_DESCRIPTOR) {
                mParseODSMData = true;
            } else {
                if (LOGS_ENABLED)
                    Log.w(TAG, "unknown handler type 0x" + Util.bytesToHex(data, 8, 4));
            }
        } else {
            if (LOGS_ENABLED) Log.w(TAG, "Not parsing track, this is meta hdlr box");
        }
        return true;
    }

    private boolean parseMvhd(BoxHeader header) {
        int versionFlags;
        try {
            versionFlags = mDataSource.readInt();
            int version = versionFlags >> 24;
            // long creationTime = 0;
            // long modificationTime = 0;
            long durationTicks;
            if (version == 1) {
                // creationTime = mDataSource.readLong();
                // modificationTime = mDataSource.readLong();
                mDataSource.skipBytes(16);
                mFileTimescale = mDataSource.readInt();
                durationTicks = mDataSource.readLong();
            } else {
                // creationTime = mDataSource.readInt();
                // modificationTime = mDataSource.readInt();
                mDataSource.skipBytes(8);
                mFileTimescale = mDataSource.readInt();
                durationTicks = mDataSource.readInt();
            }
            addMetaDataValue(KEY_DURATION, durationTicks * 1000 / mFileTimescale);
            // Skip rest of box
            int skipCount = 4 + 2 + 2 + 8 + 36 + 24 + 4;
            mDataSource.skipBytes(skipCount);
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException while parsing 'mvhd' box", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'mvhd' box", e);
            return false;
        }
        return true;
    }

    private boolean parseMdhd(BoxHeader header) {
        byte[] data = new byte[(int)header.boxDataSize];
        try {
            if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                mCurrentBoxSequence.removeLast();
                return false;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'mdhd' box", e);
        }
        int version = data[0];
        int timescale;
        long durationTicks;
        short lang;
        String language;
        if (version == 1) {
            // skip 8 for creation_time
            // skip 8 for modification_time
            timescale = (data[20] & 0xFF) << 24 | (data[21] & 0xFF) << 16 | (data[22] & 0xFF) << 8
                    | data[23] & 0xFF;
            durationTicks = (long)(data[24] & 0xFF) << 56 | (long)(data[25] & 0xFF) << 48
                    | (long)(data[26] & 0xFF) << 40 | (long)(data[27] & 0xFF) << 32
                    | (data[28] & 0xFF) << 24 | (data[29] & 0xFF) << 16
                    | (data[30] & 0xFF) << 8 | data[31] & 0xFF;
            lang = (short)((data[32] & 0xFF) << 8 | data[33] & 0xFF);
        } else { // version 0
            // skip 4 for creation_time
            // skip 4 for modification_time
            timescale = (data[12] & 0xFF) << 24 | (data[13] & 0xFF) << 16 | (data[14] & 0xFF) << 8
                    | data[15] & 0xFF;
            durationTicks = (data[16] & 0xFF) << 24 | (data[17] & 0xFF) << 16
                    | (data[18] & 0xFF) << 8 | data[19] & 0xFF;
            lang = (short)((data[20] & 0xFF) << 8 | data[21] & 0xFF);
        }
        char c1 = (char)((lang >> 10) + 0x60);
        char c2 = (char)(((lang >> 5) & 31) + 0x60);
        char c3 = (char)((lang & 31) + 0x60);
        language = "" + c1 + c2 + c3;
        mCurrentTrack.setLanguage(language);
        mCurrentTrack.getMetaData().addValue(KEY_LANGUAGE, language);
        mCurrentTrack.setTimeScale(timescale);
        long trackDurationMs = durationTicks * 1000 / timescale;
        mCurrentTrack.getMetaData().addValue(KEY_DURATION, trackDurationMs);
        return true;
    }

    private boolean readTkhd(BoxHeader header) {
        byte[] data = new byte[(int)header.boxDataSize];
        try {
            if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                mCurrentBoxSequence.removeLast();
                return false;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'tkhd' box", e);
            mCurrentBoxSequence.removeLast();
            return false;
        }

        return parseTkhd(data);
    }

    protected boolean parseTkhd(byte[] data) {
        int trackId;
        // long duration = 0;
        int dynSize;

        if (data[0] == 1) { // version 1
            // skip 8 for creation_time
            // skip 8 for modification_time
            trackId = (data[20] & 0xFF) << 24 | (data[21] & 0xFF) << 16 | (data[22] & 0xFF) << 8
                    | data[23] & 0xFF;
            // skip 4 for reserved
            // duration = (data[28] & 0xFF) << 56 | (data[29] & 0xFF) << 48 |
            // (data[30] & 0xFF) << 40 | (data[31] & 0xFF) << 32 |
            // (data[32] & 0xFF) << 24 | (data[33] & 0xFF) << 16 |
            // (data[34] & 0xFF) << 8 | data[35] & 0xFF;
            dynSize = 36;
        } else { // version 0
            // skip 4 for creation_time
            // skip 4 for modification_time
            trackId = (data[12] & 0xFF) << 24 | (data[13] & 0xFF) << 16 | (data[14] & 0xFF) << 8
                    | data[15] & 0xFF;
            // skip 4 for reserved
            // duration = (data[20] & 0xFF) << 24 | (data[21] & 0xFF) << 16 |
            // (data[22] & 0xFF) << 8 | data[23] & 0xFF;
            dynSize = 24;
        }

        int rotationAngle = 0;
        // Check that we can read the rotation matrix
        if (data.length >= dynSize + 36) {
            int matrixOffset = dynSize + 16;
            int r00 = (data[matrixOffset] & 0xFF) << 24 |
                    (data[++matrixOffset] & 0xFF) << 16 |
                    (data[++matrixOffset] & 0xFF) << 8 |
                    data[++matrixOffset] & 0xFF;
            int r01 = (data[++matrixOffset] & 0xFF) << 24 |
                    (data[++matrixOffset] & 0xFF) << 16 |
                    (data[++matrixOffset] & 0xFF) << 8 |
                    data[++matrixOffset] & 0xFF;
            matrixOffset += 4; // Skip the transformation vector.
            int r10 = (data[++matrixOffset] & 0xFF) << 24 |
                    (data[++matrixOffset] & 0xFF) << 16 |
                    (data[++matrixOffset] & 0xFF) << 8 |
                    data[++matrixOffset] & 0xFF;
            int r11 = (data[++matrixOffset] & 0xFF) << 24 |
                    (data[++matrixOffset] & 0xFF) << 16 |
                    (data[++matrixOffset] & 0xFF) << 8 |
                    data[++matrixOffset] & 0xFF;

            int one = 0x10000;
            if (r00 == one && r01 == 0 && r10 == 0 && r11 == one) {
                rotationAngle = 0;
            } else if (r00 == 0 && r01 == one && r10 == -one && r11 == 0) {
                rotationAngle = 90;
            } else if (r00 == 0 && r01 == -one && r10 == one && r11 == 0) {
                rotationAngle = 270;
            } else if (r00 == -one && r01 == 0 && r10 == 0 && r11 == -one) {
                rotationAngle = 180;
            } else {
                rotationAngle = 0;
                if (LOGS_ENABLED) {
                    Log.w(TAG, "Only rotation of 0,90,180,270 degrees are supported.");
                }
            }
        } else if (LOGS_ENABLED) {
            Log.w(TAG, "Can't read the rotation matrix");
        }

        boolean foundTrack = false;
        for (IsoTrack track : mTracks) {
            if (track.getTrackId() == trackId) {
                mCurrentTrack = track;
                foundTrack = true;
            }
        }

        if (!foundTrack) {
            mCurrentTrack = createTrack();
            mCurrentTrack.setTrackId(trackId);
            mTracks.add(mCurrentTrack);
        }

        if (rotationAngle != 0) {
            addMetaDataValue(MetaData.KEY_ROTATION_DEGREES, rotationAngle);
        }

        return true;
    }

    protected void parseVisualSampleEntry(byte[] data) {
        /*
         * VisualSampleEntry extends SampleEntry in ISO/IEC 14496 part 12, see
         * section 8.5.2.2 for structure
         */
        mCurrentOffset += data.length;
        // SampleEntry
        // skip 6 for reserved
        // skip 2 for data_reference_index
        // VisualSampleEntry
        // skip 2 for pre_defined
        // skip 2 for reserved
        // skip 12 for pre_defined
        int width = ((data[24] & 0xFF) << 8 | data[25] & 0xFF) & 0x0000FFFF;
        int height = ((data[26] & 0xFF) << 8 | data[27] & 0xFF) & 0x0000FFFF;
        // skip 4 for horizresolution
        // skip 4 for vertresolution
        // skip 4 for reserved
        // skip 2 for frame_count
        // skip 32 for compressorname
        // skip 2 for depth
        // skip 2 for pre_defined
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_WIDTH, width);
        mCurrentTrack.getMetaData().addValue(KEY_WIDTH, width);
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_HEIGHT, height);
        mCurrentTrack.getMetaData().addValue(KEY_HEIGHT, height);
        String mime = mCurrentMediaFormat.getString(MediaFormat.KEY_MIME);
        int maxSize;
        // TODO: Move KEY_MAX_INPUT_SIZE setting from here, as we have not set mime
        // in case of protected files
        if (MimeType.AVC.equals(mime)) {
            maxSize = ((width + 15) / 16) * ((height + 15) / 16) * 192;
        } else {
            maxSize = width * height * 3 / 2;
        }
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxSize);
        addMetaDataValue(KEY_WIDTH, width);
        addMetaDataValue(KEY_HEIGHT, height);
    }

    protected void parseAudioSampleEntry(byte[] data) {
        mCurrentOffset += data.length;
        // skip 6 for reserved
        // skip 2 for data_reference_index
        // skip 8 for reserved
        int channelCount = ((data[16] & 0xFF) << 8 | data[17] & 0xFF) & 0x0000FFFF;
        // skip 2 for samplesize
        // skip 2 for pre_defined
        // skip 2 for reserved
        int sampleRate = ((data[24] & 0xFF) << 8 | data[25] & 0xFF) & 0x0000FFFF;
        // skip 2 for sampleRate lower
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
        mCurrentTrack.getMetaData().addValue(MetaData.KEY_CHANNEL_COUNT, channelCount);
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        int maxSize = 8192 * 4; // aac max size
        mCurrentMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxSize);
    }

    protected AvccData parseAvccData(byte[] buffer) {
        AvccData avccData = new AvccData();
        avccData.spsBuffer = ByteBuffer.allocate(1024);
        avccData.ppsBuffer = ByteBuffer.allocate(1024);
        int currentBufferOffset;
        if (buffer[0] != 1) { // configurationVersion
            return null;
        }

        mNALLengthSize = (buffer[4] & 3) + 1; // lengthSizeMinusOne;
        int numSPS = buffer[5] & 31; // numOfSequenceParameterSets
        currentBufferOffset = 6;
        for (int i = 0; i < numSPS; i++) {
            int spsLength = (((buffer[currentBufferOffset++] & 0xFF) << 8)
                    | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;
            byte[] spsUnit = new byte[spsLength + 4];
            spsUnit[0] = 0;
            spsUnit[1] = 0;
            spsUnit[2] = 0;
            spsUnit[3] = 1;
            System.arraycopy(buffer, currentBufferOffset, spsUnit, 4, spsLength);
            avccData.spsBuffer.put(spsUnit);
            currentBufferOffset += spsLength;
        }
        int numPPS = buffer[currentBufferOffset++]; // numOfPictureParameterSets
        for (int i = 0; i < numPPS; i++) {
            int ppsLength = ((buffer[currentBufferOffset++] & 0xFF) << 8
                    | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;
            byte[] ppsUnit = new byte[ppsLength + 4];
            ppsUnit[0] = 0;
            ppsUnit[1] = 0;
            ppsUnit[2] = 0;
            ppsUnit[3] = 1;
            System.arraycopy(buffer, currentBufferOffset, ppsUnit, 4, ppsLength);
            avccData.ppsBuffer.put(ppsUnit);
            currentBufferOffset += ppsLength;
        }
        return avccData;
    }

    protected byte[] parseHvcc(byte[] buffer) {
        byte[] hvccData = null;
        if (buffer[0] != 1) {
            if (LOGS_ENABLED) Log.e(TAG, "HVCC Version " + buffer[0] + " not supported");
            return null;
        }
        mNALLengthSize = (buffer[21] & 3) + 1; // lengthSizeMinusOne;
        int numArrays = buffer[22];
        int currentBufferOffset = 23;
        for (int i = 0; i < numArrays; i++) {
            currentBufferOffset++; // skip arrayedCompleteness, reserved,
                                   // nalUnitType
            int numNalUnits = ((buffer[currentBufferOffset++] & 0xFF) << 8
                    | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;
            for (int j = 0; j < numNalUnits; j++) {
                int nalUnitLength = ((buffer[currentBufferOffset++] & 0xFF) << 8
                        | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;
                if (hvccData == null) {
                    hvccData = new byte[nalUnitLength + 4];
                    hvccData[0] = 0x00;
                    hvccData[1] = 0x00;
                    hvccData[2] = 0x00;
                    hvccData[3] = 0x01;
                    if (buffer.length > currentBufferOffset + nalUnitLength) {
                        System.arraycopy(buffer, currentBufferOffset, hvccData, 4, nalUnitLength);
                    } else {
                        if (LOGS_ENABLED) {
                            Log.e(TAG, "Range of desired copy length exceeds that of the input " +
                                    "data buffer size");
                        }
                        return null;
                    }
                } else {
                    byte[] newArray = new byte[hvccData.length + nalUnitLength + 4];
                    System.arraycopy(hvccData, 0, newArray, 0,
                            hvccData.length);
                    newArray[hvccData.length] = 0x00;
                    newArray[hvccData.length + 1] = 0x00;
                    newArray[hvccData.length + 2] = 0x00;
                    newArray[hvccData.length + 3] = 0x01;
                    System.arraycopy(buffer, currentBufferOffset, newArray,
                            hvccData.length + 4, nalUnitLength);
                    hvccData = newArray;
                }
                currentBufferOffset += nalUnitLength;
            }
        }

        return hvccData;
    }

    private boolean parseESDS(byte[] esds) {
        if (esds[0] != 0x03) {
            if (LOGS_ENABLED) Log.e(TAG, "ESDS wrong tag, expected 0x03");
            return false;
        }

        int offset = 1;
        int dataSize = 0;
        boolean more;
        do {
            int x = esds[offset++];

            dataSize = (dataSize << 7) | (x & 0x7f);
            more = (x & 0x80) != 0;
        } while (more);

        if (offset + dataSize > esds.length) {
            return false;
        }

        offset += 2;

        boolean streamDependenceFlag = (esds[offset] & 0x80) > 0;
        boolean URL_Flag = (esds[offset] & 0x40) > 0;
        boolean OCRstreamFlag = (esds[offset] & 0x20) > 0;

        offset++;

        if (streamDependenceFlag) {
            offset += 2;
        }

        if (URL_Flag) {
            int URLlength = esds[offset++];

            offset += URLlength;
        }

        if (OCRstreamFlag) {
            offset += 2;
        }

        if (esds[offset++] != 0x04) {
            if (LOGS_ENABLED) Log.e(TAG, "ESDS wrong tag, expected 0x04");
            return false;
        }

        dataSize = 0;
        do {
            int x = esds[offset++];
            dataSize = (dataSize << 7) | (x & 0x7f);
            more = (x & 0x80) != 0;
        } while (more);

        if (offset + dataSize > esds.length) {
            if (LOGS_ENABLED) Log.e(TAG, "buffer too small");
            return false;
        }

        if (boxIsUnder(BOX_ID_MP4A) || boxIsUnder(BOX_ID_ENCA)) {
            if (esds[offset] == 0x6B) {
                mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.MPEG_AUDIO);
                mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.MPEG_AUDIO);
                return true;
            } else {
                mCurrentMediaFormat.setString(MediaFormat.KEY_MIME, MimeType.AAC);
                mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE, MimeType.AAC);
            }
        }

        offset += 13;

        if (esds[offset++] != 0x05) {
            if (LOGS_ENABLED) Log.e(TAG, "wrong tag expected 0x05");
            return false;
        }

        dataSize = 0;
        do {
            int x = esds[offset++];
            dataSize = (dataSize << 7) | (x & 0x7f);
            more = (x & 0x80) != 0;
        } while (more);

        if (offset + dataSize > esds.length) {
            return false;
        }

        mCurrentMediaFormat.setByteBuffer("csd-0",
                ByteBuffer.wrap(Arrays.copyOfRange(esds, offset, offset + dataSize)));
        return true;
    }

    private boolean parseSidx(BoxHeader header) {
        try {
            long boxEndOffset = mCurrentOffset + header.boxDataSize;
            int versionFlags = mDataSource.readInt();
            int version = (versionFlags >> 24) & 0xFF;
            int referenceId = mDataSource.readInt();
            IsoTrack sidxTrack = null;
            for (IsoTrack t : mTracks) {
                if (t.getTrackId() == referenceId) {
                    sidxTrack = t;
                    break;
                }
            }
            if (sidxTrack == null) {
                if (LOGS_ENABLED) Log.w(TAG, "we did not find a matching track for sidx box");
                // return true so parsing can continue
                return true;
            }

            int sidxTimescale = mDataSource.readInt();
            sidxTrack.setSidxTimescale(sidxTimescale);

            // the following 8-16 bytes are only relevant for sub-sidx boxes,
            // which we don't support
            if (version == 0) {
                mDataSource.skipBytes(8);
            } else {
                mDataSource.skipBytes(16);
            }
            mDataSource.skipBytes(2); // reserved
            short referenceCount = mDataSource.readShort();

            ArrayList<SidxEntry> sidxList = new ArrayList<>(referenceCount);
            long sidxTotalDurationUs = 0;
            long sidxTotalOffset = boxEndOffset;

            for (int i = 0; i < referenceCount; i++) {
                SidxEntry sidxEntry = new SidxEntry();

                int referencedSize = mDataSource.readInt();
                if (referencedSize < 0) {
                    // reference type 1 (sub-sidx box) not supported
                    // return true so parsing can continue
                    return true;
                }
                referencedSize &= 0x7FFFFFFF;

                sidxEntry.startOffset = sidxTotalOffset;
                sidxTotalOffset += referencedSize;

                long subsegmentDurationTicks = ((long)mDataSource.readByte() & 0xFF) << 24
                        | ((long)mDataSource.readByte() & 0xFF) << 16
                        | ((long)mDataSource.readByte() & 0xFF) << 8
                        | (long)mDataSource.readByte() & 0xFF;

                sidxEntry.startTimeUs = sidxTotalDurationUs;

                sidxTotalDurationUs += subsegmentDurationTicks * 1000000 / sidxTimescale;

                // Assume all segments start with SAP, so skip this info
                mDataSource.skipBytes(4);

                sidxList.add(sidxEntry);
            }

            sidxTrack.setSidxList(sidxList);

            long mediaDuration = 0;
            if (mMetaDataValues.containsKey(KEY_DURATION)) {
                mediaDuration = (long) mMetaDataValues.get(KEY_DURATION);
            }

            if (sidxTotalDurationUs > 0 && mediaDuration == 0) {
                addMetaDataValue(KEY_DURATION, sidxTotalDurationUs / 1000);
            }

        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing sidx box", e);
            mCurrentBoxSequence.removeLast();
            return false;
        }

        return true;
    }

    private boolean parseDataBox(BoxHeader header) {
        try {
            if (mCurrentMetaDataKey != null) {
                Object metaDataValue;
                if (mCurrentMetaDataKey == KEY_DISC_NUMBER) {
                    mDataSource.skipBytes(4); // skip type
                    mDataSource.skipBytes(4); // skip locale
                    mDataSource.skipBytes(2); // skip album id type
                                              // and album id len
                    int diskNumber = mDataSource.readShort();
                    mDataSource.skipBytes(2); // skip total number of disks
                    metaDataValue = Integer.toString(diskNumber);
                } else if (mCurrentMetaDataKey == KEY_ALBUM_ART) {
                    mDataSource.skipBytes(4); // skip type
                    mDataSource.skipBytes(4); // skip locale
                    byte[] albumart = new byte[(int)header.boxDataSize - 8];
                    mDataSource.read(albumart);
                    metaDataValue = albumart;
                } else {
                    byte[] typefield = new byte[4];
                    mDataSource.read(typefield);
                    int type = typefield[0];
                    int flag = (typefield[1] | typefield[2] | typefield[3]);

                    Charset encoding = StandardCharsets.UTF_8;
                    if (type == 2) {
                        encoding = StandardCharsets.UTF_16BE;
                    }
                    mDataSource.skipBytes(4); // skip locale for now
                    byte[] data = new byte[(int)header.boxDataSize - 8];
                    mDataSource.read(data);
                    if (mCurrentMetaDataKey == KEY_TRACK_NUMBER) {
                        int trackNumber = (data[2] << 8) & 0xFF | data[3];
                        int trackTotalNumber = (data[4] << 8) & 0xFF | data[5];
                        metaDataValue = trackNumber + "/" + trackTotalNumber;
                    } else if (mCurrentMetaDataKey == KEY_COMPILATION) {
                        metaDataValue = Byte.toString(data[0]);
                    } else if (mCurrentMetaDataKey == KEY_GENRE) {
                        if (type == 0 && flag == 1) {
                            metaDataValue = new String(data, encoding);
                        } else {
                            int genre = data[data.length - 1];
                            genre--;
                            if (genre < 0) {
                                genre = 255;
                            }
                            metaDataValue = Integer.toString(genre);
                        }
                    } else {
                        metaDataValue = new String(data, encoding);
                    }
                }
                mMetaDataValues.put(mCurrentMetaDataKey, metaDataValue);
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "could not read data", e);

            return false;
        }
        return true;
    }

    private boolean parsePasp(BoxHeader header) {
        try {
            int hSpacing = mDataSource.readInt();
            int vSpacing = mDataSource.readInt();
            mCurrentMediaFormat.setInteger(KEY_PASP_HORIZONTAL_SPACING, hSpacing);
            mCurrentMediaFormat.setInteger(KEY_PASP_VERTICAL_SPACING, vSpacing);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing pasp box", e);
            mCurrentBoxSequence.removeLast();
            return false;
        }
        return true;
    }

    protected boolean parseUuid(BoxHeader header) {
        byte[] userType = new byte[16];
        try {
            mDataSource.read(userType);
            mCurrentOffset += 16;
            String uuidUserType = Util.bytesToHex(userType);
            if (uuidUserType.equals(UUID_SOMD)) {
                return parseUuidSomd(header);
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'uuid' box", e);
            return false;
        }
        return true;
    }

    private boolean parseUuidSomd(BoxHeader header) {
        long boxEndOffset = mCurrentOffset + header.boxDataSize - 16;
        try {
            mDataSource.skipBytes(4); // version
            mCurrentOffset += 4;
            while (mCurrentOffset < boxEndOffset) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                if (nextBoxHeader.boxType == BOX_ID_FLGS) {
                    long flagsData = mDataSource.readLong();
                    long validData = mDataSource.readLong();
                    // Only last bit is currently used
                    if ((validData & 0x01) != 0 && (flagsData & 0x01) != 0) {
                        mCurrentVideoTrack.getMediaFormat().setInteger(
                                MetaData.KEY_IS_CAMERA_CONTENT, 1);
                    }
                    break;
                }
                mCurrentOffset += nextBoxHeader.boxDataSize;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing UUID_PROF box", e);
            return false;
        }
        return true;
    }

    protected static class BoxHeader {
        public long startOffset = 0;

        public long boxDataSize = 0;

        public int boxHeaderSize = 0;

        public int boxType = 0;
    }

    protected static class AvccData {
        ByteBuffer spsBuffer;

        ByteBuffer ppsBuffer;
    }

    protected static class SidxEntry {
        long startTimeUs;

        long startOffset;
    }

    public class IsoTrack {
        protected final MetaDataImpl mMetaData;

        protected final SampleTable mSampleTable;

        protected int mTimeScale = 0;

        protected int mTrackId = -1;

        protected int mTrackIndex = -1;

        protected MediaFormat mMediaFormat;

        protected TrackType mType;

        protected int mCurrentSampleIndex = 0;

        protected Trex mTrex;

        protected String mLanguage;

        protected ArrayList<Tfra> mTfraList;

        protected ArrayDeque<FragmentSample> mCurrentFragmentSampleQueue;

        protected ArrayDeque<CryptoInfo> mCurrentCryptoInfoQueue;

        public long mTimeTicks = 0;

        protected byte[] mKID;

        protected long mEditMediaTimeTicks = 0;

        protected int mDefaultIVSize = 0;

        protected byte[] mDefaultKID;

        protected int mCurrentSampleDescriptionIndex = -1;

        protected final ArrayList<MediaFormat> mSampleDescriptionList;

        protected long mNextMoofOffset = 0;

        protected long mLastTimestampUs = 0;

        protected long mSidxTimescale = 0;

        protected ArrayList<SidxEntry> mSidxList = null;

        public IsoTrack() {
            mMetaData = new MetaDataImpl();
            mSampleTable = new SampleTable();
            mCurrentFragmentSampleQueue = null;
            mSampleDescriptionList = new ArrayList<>(1);
        }

        public boolean buildSampleTable() {
            return mSampleTable.buildSampleTable();
        }

        public void releaseSampleTable() {
            mSampleTable.releaseSampleTable();
        }

        public void addSampleDescriptionEntry(MediaFormat mediaFormat) {
            if (mMediaFormat == null) {
                mMediaFormat = mediaFormat;
            }
            mSampleDescriptionList.add(mediaFormat);
        }

        public ArrayList<Tfra> getTfraList() {
            return mTfraList;
        }

        public void addFragmentSamples(ArrayList<FragmentSample> fragmentSamples) {
            int numNewSamples = fragmentSamples.size();
            if (mCurrentFragmentSampleQueue == null) {
                mCurrentFragmentSampleQueue = new ArrayDeque<>(numNewSamples);
            }
            for (int i = 0; i < numNewSamples; i++) {
                mCurrentFragmentSampleQueue.add(fragmentSamples.get(i));
            }
        }

        public void addCryptoInfos(ArrayList<CryptoInfo> cryptoInfos) {
            int numNewSamples = cryptoInfos.size();
            if (mCurrentCryptoInfoQueue == null) {
                mCurrentCryptoInfoQueue = new ArrayDeque<>(numNewSamples);
            }
            for (int i = 0; i < numNewSamples; i++) {
                mCurrentCryptoInfoQueue.add(cryptoInfos.get(i));
            }
        }

        public void setTfraList(ArrayList<Tfra> tfraEntryList) {
            mTfraList = tfraEntryList;
        }

        public AccessUnit dequeueAccessUnit(boolean readFragmented) {
            /*
             * if (LOGS_ENABLED) Log.v(TAG, "dequeueAccessUnit track " +
             * mTrackId + " sample " + mCurrentSampleIndex);
             */

            if (readFragmented && mCurrentSampleIndex >= mSampleTable.getSampleCount()) {
                return dequeueAccessUnitFragmented();
            }
            AccessUnit accessUnit = new AccessUnit();

            if (mCurrentSampleIndex >= mSampleTable.getSampleCount()) {
                accessUnit.status = AccessUnit.END_OF_STREAM;
                return accessUnit;
            }
            if (mCurrentSampleIndex >= mSampleTable.getSampleCount()) {
                accessUnit.status = AccessUnit.ERROR;
                return accessUnit;
            }
            int sampleDescriptionIndex =
                    mSampleTable.getSampleDescriptionIndex(mCurrentSampleIndex);
            if (mCurrentSampleDescriptionIndex == -1) {
                mCurrentSampleDescriptionIndex = sampleDescriptionIndex;
            } else if (sampleDescriptionIndex != mCurrentSampleDescriptionIndex) {
                mCurrentSampleDescriptionIndex = sampleDescriptionIndex;
                MediaFormat mediaFormat = mSampleDescriptionList
                        .get(mCurrentSampleDescriptionIndex);
                // TODO Send new format to codec
            }
            accessUnit.status = AccessUnit.OK;
            accessUnit.trackIndex = mTrackIndex;
            accessUnit.timeUs = mSampleTable.getTimestampUs(mCurrentSampleIndex)
                    - mEditMediaTimeTicks * 1000000 / mTimeScale;
            accessUnit.durationUs = mSampleTable.getDurationUs(mCurrentSampleIndex);
            long dataOffset = mSampleTable.getOffset(mCurrentSampleIndex);
            int dataSize = mSampleTable.getSize(mCurrentSampleIndex);
            if (accessUnit.data == null || accessUnit.data.length < dataSize) {
                accessUnit.data = null;
                accessUnit.data = new byte[dataSize];
            }
            accessUnit.size = dataSize;
            try {
                if (mDataSource.readAt(dataOffset, accessUnit.data, dataSize) != dataSize) {
                    accessUnit.status = AccessUnit.ERROR;
                    return accessUnit;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while reading accessunit from source");
                accessUnit.status = AccessUnit.ERROR;
                return accessUnit;
            }
            if (mMediaFormat.getString(MediaFormat.KEY_MIME).equals(MimeType.AVC) ||
                    mMediaFormat.getString(MediaFormat.KEY_MIME).equals(MimeType.HEVC)) {
                // add NAL Header
                int srcOffset = 0;
                int dstOffset = 0;
                int nalLengthSize = 4;
                // TODO: Support files with nalLengthSize other than 4
                while (srcOffset < dataSize) {
                    if ((srcOffset + nalLengthSize) > dataSize) {
                        accessUnit.status = AccessUnit.ERROR;
                        return accessUnit;
                    }
                    int nalLength = (accessUnit.data[srcOffset++] & 0xff) << 24
                            | (accessUnit.data[srcOffset++] & 0xff) << 16
                            | (accessUnit.data[srcOffset++] & 0xff) << 8
                            | (accessUnit.data[srcOffset++] & 0xff);
                    if (srcOffset + nalLength > dataSize) {
                        accessUnit.status = AccessUnit.ERROR;
                        return accessUnit;
                    }
                    accessUnit.data[dstOffset++] = 0;
                    accessUnit.data[dstOffset++] = 0;
                    accessUnit.data[dstOffset++] = 0;
                    accessUnit.data[dstOffset++] = 1;
                    srcOffset += nalLength;
                    dstOffset += nalLength;
                }
            }
            accessUnit.isSyncSample = mSampleTable.isSyncSample(mCurrentSampleIndex);

            mLastTimestampUs = accessUnit.timeUs;

            mCurrentSampleIndex++;
            return accessUnit;
        }

        protected AccessUnit dequeueAccessUnitFragmented() {
            // if (LOGS_ENABLED) Log.v(TAG, "dequeueAccessUnitFragmented track "
            // + mTrackId);

            AccessUnit accessUnit = new AccessUnit();

            // load moof box when necessary

            if (!fillFragmentQueue()) {
                accessUnit.status = AccessUnit.ERROR;
                return accessUnit;
            }

            if (mCurrentFragmentSampleQueue == null || mCurrentFragmentSampleQueue.isEmpty()) {
                if (LOGS_ENABLED) Log.i(TAG, "No more fragments in queue, end of stream");
                accessUnit.status = AccessUnit.END_OF_STREAM;
                return accessUnit;
            }
            FragmentSample sample = mCurrentFragmentSampleQueue.removeFirst();
            accessUnit.status = AccessUnit.OK;
            accessUnit.trackIndex = mTrackIndex;
            accessUnit.timeUs = (mTimeTicks
                    + sample.compositionTimeOffset - mEditMediaTimeTicks) * 1000000 / mTimeScale;
            accessUnit.timeUs += mSampleTable.getDurationUs();

            if (accessUnit.timeUs < 0) {
                if (LOGS_ENABLED) Log.w(TAG, "Negative sampletime!");
                accessUnit.timeUs = 0;
            }
            accessUnit.durationUs = (long)sample.durationTicks * 1000000L / mTimeScale;
            mTimeTicks += sample.durationTicks;
            long dataOffset = sample.dataOffset;
            int dataSize = sample.size;
            if (accessUnit.data == null || accessUnit.data.length < dataSize) {
                accessUnit.data = null;
                accessUnit.data = new byte[dataSize];
            }
            accessUnit.size = dataSize;
            try {
                if (mDataSource.readAt(dataOffset, accessUnit.data, dataSize) != dataSize) {
                    if (LOGS_ENABLED) Log.e(TAG, "could not read sample data");
                    accessUnit.status = AccessUnit.ERROR;
                    return accessUnit;
                }
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException while reading accessunit from source");
                accessUnit.status = AccessUnit.ERROR;
                return accessUnit;
            }

            if (mCurrentCryptoInfoQueue != null) {
                accessUnit.cryptoInfo = mCurrentCryptoInfoQueue.removeFirst();

                if (accessUnit.cryptoInfo != null && accessUnit.cryptoInfo.numSubSamples == 1 &&
                        accessUnit.cryptoInfo.numBytesOfEncryptedData[0] == -1) {
                    accessUnit.cryptoInfo.numBytesOfEncryptedData[0] = dataSize;
                }
            }

            boolean isAVC = mMediaFormat.getString(MediaFormat.KEY_MIME).equals(MimeType.AVC);
            boolean isHEVC = mMediaFormat.getString(MediaFormat.KEY_MIME).equals(MimeType.HEVC);

            // Add NAL header. If we have no clearbytes, we need to
            // let the platform set NAL header
            if ((isAVC || isHEVC)
                    && (accessUnit.cryptoInfo == null
                    || accessUnit.cryptoInfo.numBytesOfClearData[0] > 0)) {
                if (!addNALHeader(accessUnit, isAVC, isHEVC)) {
                    return AccessUnit.ACCESS_UNIT_ERROR;
                }
            } else if ((isAVC || isHEVC)
                    && accessUnit.cryptoInfo != null
                    && accessUnit.cryptoInfo.numBytesOfClearData[0] == 0) {
                // nalType is encrypted, so we assume it is as sync sample
                accessUnit.isSyncSample = true;
            }
            mLastTimestampUs = accessUnit.timeUs;

            return accessUnit;
        }

        public void setTrackType(TrackType trackType) {
            mType = trackType;
        }

        public TrackType getTrackType() {
            return mType;
        }

        public long getDurationUs() {
            if (mIsFragmented && mTfraList != null && mTimeScale > 0) {
                Tfra lastTfra = mTfraList.get(mTfraList.size() - 1);
                return lastTfra.timeTicks * 1000000 / mTimeScale;
            }
            return mSampleTable.getDurationUs();
        }

        public void setLanguage(String language) {
            mLanguage = language;
        }

        public String getLanguage() {
            return mLanguage;
        }

        public void setTrackId(int id) {
            mTrackId = id;
        }

        public int getTrackId() {
            return mTrackId;
        }

        public void setTrackIndex(int index) {
            mTrackIndex = index;
        }

        public int getTrackIndex() {
            return mTrackIndex;
        }

        public MetaDataImpl getMetaData() {
            return mMetaData;
        }

        public SampleTable getSampleTable() {
            return mSampleTable;
        }

        public void setTimeScale(int timeScale) {
            mTimeScale = timeScale;
        }

        public int getTimeScale() {
            return mTimeScale;
        }

        public void setMediaFormat(MediaFormat format) {
            mMediaFormat = format;
        }

        public MediaFormat getMediaFormat() {
            return mMediaFormat;
        }

        @Override
        public String toString() {
            return "TrackID = " + mTrackId + "," + "Track type = " + mType;
        }

        public long seekTo(long seekTimeUs, boolean isFragmented) {
            if (mCurrentCryptoInfoQueue != null) {
                mCurrentCryptoInfoQueue.clear();
            }
            if (!isFragmented
                    || (mSampleTable != null && seekTimeUs < mSampleTable.getDurationUs())) {
                if (mSampleTable == null) {
                    return 0;
                }
                mCurrentSampleIndex = mSampleTable.findSampleIndex(seekTimeUs);
                // Fix until findSampleIndex can not return negative values
                if (mCurrentSampleIndex < 0) {
                    mCurrentSampleIndex = 0;
                }
                if (isFragmented) {
                    // Need to reset next moof offset
                    Tfra tfra = mTfraList.get(0);
                    mNextMoofOffset = tfra.moofOffset;
                    if (mCurrentFragmentSampleQueue != null) {
                        mCurrentFragmentSampleQueue.clear();
                    }
                    mTimeTicks = 0;
                }
                return mSampleTable.getTimeOfSample(mCurrentSampleIndex);
            } else {
                if (mTfraList == null || mTfraList.isEmpty()) {
                    if (mSidxList != null) {
                        int sidxListLength = mSidxList.size();
                        for (int i = 0; i < sidxListLength; i++) {
                            SidxEntry sidxEntry = mSidxList.get(i);
                            SidxEntry nextEntry = null;
                            if (i + 1 < sidxListLength) {
                                nextEntry = mSidxList.get(i + 1);
                            }
                            if (sidxEntry.startTimeUs < seekTimeUs && (nextEntry == null ||
                                    nextEntry.startTimeUs > seekTimeUs)) {
                                mCurrentFragmentSampleQueue = null;
                                mNextMoofOffset = sidxEntry.startOffset;
                                mTimeTicks = sidxEntry.startTimeUs * mSidxTimescale / 1000000;
                                return sidxEntry.startTimeUs;
                            }
                        }
                    }

                    mCurrentFragmentSampleQueue = null;
                    mNextMoofOffset = 0;
                    mTimeTicks = 0;
                    return 0;
                }

                // need to set this so we actually get into
                // dequeueAccessUnitFragmented
                mCurrentSampleIndex = mSampleTable.getSampleCount();

                int numTfra = mTfraList.size();
                Tfra tfra = null;
                for (int i = 0; i < numTfra; i++) {
                    tfra = mTfraList.get(i);
                    long tfratimeUs = (tfra.timeTicks * 1000000 / mTimeScale);
                    if (tfratimeUs == seekTimeUs) {
                        break;
                    }
                    if (tfratimeUs > seekTimeUs) {
                        if (i > 0) {
                            tfra = mTfraList.get(i - 1);
                        }
                        break;
                    }
                }

                if (LOGS_ENABLED) {
                    Log.v(TAG, "Seek fragmented file track " + mType
                            + " to " + (tfra.timeTicks * 1000000 / mTimeScale)
                            + " at offset " + tfra.moofOffset);
                }

                if (mCurrentFragmentSampleQueue != null) {
                    mCurrentFragmentSampleQueue.clear();
                }

                long contentLength = 0;
                try {
                    contentLength = mDataSource.length();
                } catch (IOException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IOException when retrieving content length", e);
                }

                if (tfra.moofOffset > 0 && (tfra.moofOffset < contentLength ||
                        contentLength == -1)) {
                    mCurrentOffset = tfra.moofOffset;
                    BoxHeader header = getNextBoxHeader();
                    mCurrentTrackId = mTrackId;
                    if (header != null) {
                        if (header.boxType == BOX_ID_MOOF && parseBox(header)) {
                            // skip mdat to get next moof offset
                            mCurrentOffset =
                                    header.startOffset + header.boxHeaderSize + header.boxDataSize;
                            mNextMoofOffset = findNextMoofForTrack(mTrackId);
                        }
                    } else {
                        return 0;
                    }
                }

                mTimeTicks = tfra.timeTicks + mEditMediaTimeTicks
                        - (mSampleTable.getDurationUs() * mTimeScale / 1000000);

                if (mCurrentFragmentSampleQueue != null) {
                    long sampleNumber = tfra.sampleNumber;
                    if (mCurrentFragmentSampleQueue.size() >= sampleNumber) {
                        while (sampleNumber > 1) {
                            mCurrentFragmentSampleQueue.removeFirst();
                            sampleNumber--;
                        }
                        FragmentSample sample = mCurrentFragmentSampleQueue.peekFirst();
                        if (sample != null) {
                            mTimeTicks -= sample.compositionTimeOffset;
                        }
                    }

                    if (mType != TrackType.VIDEO) {
                        while (!mCurrentFragmentSampleQueue.isEmpty()) {
                            FragmentSample sample = mCurrentFragmentSampleQueue.peekFirst();
                            if (seekTimeUs * mTimeScale / 1000000L > mTimeTicks
                                    + sample.compositionTimeOffset
                                    - mEditMediaTimeTicks + sample.durationTicks) {
                                mTimeTicks += sample.durationTicks;
                                mCurrentFragmentSampleQueue.removeFirst();
                                if (mCurrentCryptoInfoQueue != null) {
                                    mCurrentCryptoInfoQueue.removeFirst();
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }

                return (tfra.timeTicks * 1000000 / mTimeScale);
            }
        }

        public void setTrex(Trex t) {
            mTrex = t;
        }

        public Trex getTrex() {
            return mTrex;
        }

        public void setDefaultEncryptionData(int defaultIVSize, byte[] kID) {
            mDefaultIVSize = defaultIVSize;
            mDefaultKID = new byte[kID.length];
            mDefaultKID = Arrays.copyOf(kID, kID.length);

            mKID = new byte[kID.length];
            mKID = Arrays.copyOf(kID, kID.length);

        }

        public void setEditMediaTimeTicks(long mediaTimeTicks) {
            mEditMediaTimeTicks = mediaTimeTicks;
        }

        public long getLastTimestampUs() {
            return mLastTimestampUs;
        }

        public boolean hasDataAvailable(boolean isFragmented) throws IOException {
            if (isFragmented && mCurrentSampleIndex >= mSampleTable.getSampleCount()) {
                if (!fillFragmentQueue()) {
                    return false;
                }

                if (mCurrentFragmentSampleQueue == null) {
                    return false;
                }

                if (mCurrentFragmentSampleQueue.isEmpty()) {
                    // End of stream, return true so other tracks can run to
                    // completion
                    return mNextMoofOffset < 0;
                }

                FragmentSample sample = mCurrentFragmentSampleQueue.peek();
                DataAvailability hasData = mDataSource
                        .hasDataAvailable(sample.dataOffset, sample.size);
                if (hasData == DataAvailability.NOT_AVAILABLE) {
                    mDataSource.seek(sample.dataOffset);
                }
                return hasData != DataAvailability.IN_FUTURE;
            } else {
                if (mCurrentSampleIndex >= mSampleTable.getSampleCount()) {
                    // End of stream, return true so other tracks can run to
                    // completion
                    return true;
                }
                DataAvailability hasData = mDataSource
                        .hasDataAvailable(mSampleTable.getOffset(mCurrentSampleIndex),
                                mSampleTable.getSize(mCurrentSampleIndex));
                if (hasData == DataAvailability.NOT_AVAILABLE) {
                    mDataSource.seek(mSampleTable.getOffset(mCurrentSampleIndex));
                }
                return hasData != DataAvailability.IN_FUTURE;
            }
        }

        private boolean fillFragmentQueue() {
            long contentLength = 0;
            try {
                contentLength = mDataSource.length();
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "IOException when retrieving content length", e);
            }

            while (mCurrentFragmentSampleQueue == null
                    || (mCurrentFragmentSampleQueue.isEmpty() && mNextMoofOffset > 0
                    && (mNextMoofOffset < contentLength || contentLength == -1))) {
                mCurrentTrackId = mTrackId;

                if (mNextMoofOffset == 0) {
                    // Find initial moof for this track
                    mCurrentOffset = mFirstMoofOffset;
                    mNextMoofOffset = findNextMoofForTrack(mTrackId);
                }

                if (mNextMoofOffset < 0) {
                    if (LOGS_ENABLED)
                        Log.i(TAG, "Could not find any more moof boxes for this track");
                    break;
                }

                mCurrentOffset = mNextMoofOffset;
                BoxHeader header = getNextBoxHeader();
                if (header == null || header.boxType != BOX_ID_MOOF) {
                    // We have read all moof boxes, no more data
                    if (LOGS_ENABLED) Log.i(TAG, "no more moof boxes, all available data queued");
                    break;
                }
                boolean parseOk = parseBox(header);
                if (!parseOk) {
                    if (LOGS_ENABLED) Log.e(TAG, "error parsing next 'moof' box");
                    return false;
                }

                mCurrentOffset = header.startOffset + header.boxHeaderSize + header.boxDataSize;
                mNextMoofOffset = findNextMoofForTrack(mTrackId);
            }
            return true;
        }

        public void setSidxTimescale(long timescale) {
            mSidxTimescale = timescale;
        }

        public long getSidxTimeScale() {
            return mSidxTimescale;
        }

        public void setSidxList(ArrayList<SidxEntry> list) {
            mSidxList = list;
        }
    }

    protected boolean addNALHeader(AccessUnit accessUnit, boolean isAVC, boolean isHEVC) {
        int dataSize = accessUnit.size;
        int srcOffset = 0;
        int dstOffset = 0;
        while (srcOffset < dataSize) {
            if ((srcOffset + mNALLengthSize) > dataSize) {
                if (LOGS_ENABLED) Log.e(TAG, "no room to add nal length");
                accessUnit.status = AccessUnit.ERROR;
                return false;
            }
            int nalLength = 0;

            if (mNALLengthSize == 1 || mNALLengthSize == 2) {
                if (mNALLengthSize == 1) {
                    nalLength = accessUnit.data[srcOffset];
                } else if (mNALLengthSize == 2) {
                    nalLength = ((accessUnit.data[srcOffset] & 0xff) << 8
                            | (accessUnit.data[srcOffset + 1] & 0xff));
                }
                byte[] tmpData = new byte[dataSize + (sNALHeaderSize -
                        mNALLengthSize)];
                if (srcOffset > 0) {
                    System.arraycopy(accessUnit.data, 0, tmpData, 0,
                            srcOffset);
                }
                System.arraycopy(accessUnit.data, srcOffset, tmpData,
                        (sNALHeaderSize - mNALLengthSize) + srcOffset,
                        accessUnit.data.length - srcOffset);
                accessUnit.data = tmpData;
                dataSize = accessUnit.data.length;
                accessUnit.size = dataSize;
            } else if (mNALLengthSize == 4) {
                nalLength = ((accessUnit.data[srcOffset] & 0xff) << 24
                        | (accessUnit.data[srcOffset + 1] & 0xff) << 16
                        | (accessUnit.data[srcOffset + 2] & 0xff) << 8
                        | (accessUnit.data[srcOffset + 3] & 0xff));
            } else {
                if (LOGS_ENABLED)
                    Log.e(TAG, "Unsupported nal length size" + mNALLengthSize);
                accessUnit.status = AccessUnit.ERROR;
                return false;
            }
            srcOffset += sNALHeaderSize;
            if (accessUnit.cryptoInfo != null) {
                accessUnit.cryptoInfo.numBytesOfClearData[0] +=
                        (sNALHeaderSize - mNALLengthSize);
            }
            if (srcOffset + nalLength > dataSize) {
                if (LOGS_ENABLED) Log.e(TAG, "Error writing nal length");
                accessUnit.status = AccessUnit.ERROR;
                return false;
            }
            accessUnit.data[dstOffset++] = 0;
            accessUnit.data[dstOffset++] = 0;
            accessUnit.data[dstOffset++] = 0;
            accessUnit.data[dstOffset++] = 1;

            if (isAVC && (accessUnit.data[srcOffset] & 0x1f)
                    == AVC_NAL_UNIT_TYPE_IDR_PICTURE) {
                accessUnit.isSyncSample = true;
            } else if (isHEVC) {
                int nalType = (accessUnit.data[srcOffset] & 0x7e) >> 1;

                if (nalType == HEVC_NAL_UNIT_TYPE_IDR_PICTURE_W_RADL
                        || nalType == HEVC_NAL_UNIT_TYPE_IDR_PICTURE_N_LP
                        || nalType == HEVC_NAL_UNIT_TYPE_CRA_PICTURE) {
                    accessUnit.isSyncSample = true;
                }
            }

            srcOffset += nalLength;
            dstOffset += nalLength;
        }
        return true;
    }

    static class Trex {
        public int defaultSampleDuration = 0;

        public int defaultSampleSize = 0;
    }

    public static class Traf {
        public long baseDataOffset = Integer.MIN_VALUE;

        public int defaultSampleDuration = Integer.MIN_VALUE;

        public int defaultSampleSize = Integer.MIN_VALUE;
    }

    public static class FragmentSample {
        public int durationTicks = 0;

        public int size = 0;

        public int compositionTimeOffset = 0;

        public long dataOffset = 0;

        public String toString() {
            return "offset = " + dataOffset + ", size = " + size + ", duration = " + durationTicks;
        }
    }

    static class Tfra {
        long timeTicks = 0;

        long moofOffset = 0;

        long sampleNumber = 0;
    }

    @Override
    public long getDurationUs() {
        long durationUs = 0;
        for (int i = 0; i < mTracks.size(); i++) {
            long trackDurationUs = mTracks.get(i).getDurationUs();
            if (trackDurationUs > durationUs) {
                durationUs = trackDurationUs;
            }
            long fileDurationUs = getLong(KEY_DURATION);
            fileDurationUs *= 1000;
            if (fileDurationUs > durationUs) {
                durationUs = fileDurationUs;
            }
        }
        return durationUs;
    }

    @Override
    public MediaFormat getFormat(TrackType type) {
        if (type == TrackType.AUDIO && mCurrentAudioTrack != null) {
            return mCurrentAudioTrack.getMediaFormat();
        } else if (type == TrackType.VIDEO && mCurrentVideoTrack != null) {
            return mCurrentVideoTrack.getMediaFormat();
        } else if (type == TrackType.SUBTITLE && mCurrentSubtitleTrack != null) {
            return mCurrentSubtitleTrack.getMediaFormat();
        }
        return null;
    }

    @Override
    public TrackInfo[] getTrackInfo() {
        int numTracks = mTracks.size();
        TrackInfo[] trackInfos = new TrackInfo[numTracks];

        for (int i = 0; i < numTracks; i++) {
            IsoTrack track = mTracks.get(i);
            TrackType trackType = track.getTrackType();
            if (trackType != TrackType.UNKNOWN) {
                MediaFormat mediaFormat = track.getMediaFormat();
                String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
                long durationUs = track.getDurationUs();
                String language = track.getLanguage();

                TrackRepresentation representation;

                if (trackType == TrackType.AUDIO) {
                    int channelCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    representation = new AudioTrackRepresentation(-1, channelCount, "", sampleRate);
                } else if (trackType == TrackType.VIDEO) {
                    int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    representation = new VideoTrackRepresentation(-1, width, height, -1f);
                } else { // SUBTITLE, UNKNOWN
                    representation = new TrackRepresentation(-1);
                }

                trackInfos[i] = new TrackInfo(trackType, mimeType, durationUs, language,
                        new TrackRepresentation[] { representation });
            } else {
                trackInfos[i] = new TrackInfo(TrackType.UNKNOWN, "", 0, "",
                        new TrackRepresentation[] {});
            }
        }

        return trackInfos;
    }

    @Override
    public synchronized void seekTo(long seekTimeUs) {
        // Seek video to nearest previous sync sample
        // Then use timestamp of that sample to seek audio

        long timeUs = seekTimeUs;
        if (mCurrentVideoTrack != null) {
            timeUs = mCurrentVideoTrack.seekTo(seekTimeUs, mIsFragmented);
        }

        if (timeUs >= 0) {
            if (mCurrentAudioTrack != null) {
                mCurrentAudioTrack.seekTo(timeUs, mIsFragmented);
            }
            if (mCurrentSubtitleTrack != null) {
                mCurrentSubtitleTrack.seekTo(timeUs, mIsFragmented);
            }
        }
    }

    @Override
    public TrackType selectTrack(boolean select, int index) {
        if (index < 0 || index > mTracks.size()) {
            if (LOGS_ENABLED) Log.e(TAG, "Invalid track: " + index);
            return TrackType.UNKNOWN;
        }

        IsoTrack track = mTracks.get(index);
        TrackType trackType = track.getTrackType();

        if (trackType == TrackType.AUDIO) {
            if (select) {
                if (mCurrentAudioTrack == track) {
                    if (LOGS_ENABLED) Log.w(TAG, "track " + index + " is already selected");
                    return TrackType.UNKNOWN;
                }

                long timeUs = 0;

                if (mCurrentAudioTrack != null) {
                    mCurrentAudioTrack.releaseSampleTable();
                    timeUs = mCurrentAudioTrack.getLastTimestampUs();
                } else if (mCurrentVideoTrack != null) {
                    timeUs = mCurrentVideoTrack.getLastTimestampUs();
                } else if (mCurrentSubtitleTrack != null) {
                    timeUs = mCurrentSubtitleTrack.getLastTimestampUs();
                }

                mCurrentAudioTrack = track;
                mCurrentAudioTrack.buildSampleTable();

                mCurrentAudioTrack.seekTo(timeUs, mIsFragmented);

                return TrackType.AUDIO;
            } else {
                if (LOGS_ENABLED) Log.w(TAG, "Audio tracks can't be deselected");
                return TrackType.UNKNOWN;
            }
        } else if (trackType == TrackType.SUBTITLE) {
            if (select) {
                if (mCurrentSubtitleTrack == track) {
                    if (LOGS_ENABLED) Log.w(TAG, "track " + index + " is already selected");
                    return TrackType.UNKNOWN;
                }

                long timeUs = 0;

                if (mCurrentSubtitleTrack != null) {
                    mCurrentSubtitleTrack.releaseSampleTable();
                    timeUs = mCurrentSubtitleTrack.getLastTimestampUs();
                } else if (mCurrentAudioTrack != null) {
                    timeUs = mCurrentAudioTrack.getLastTimestampUs();
                } else if (mCurrentVideoTrack != null) {
                    timeUs = mCurrentVideoTrack.getLastTimestampUs();
                }

                mCurrentSubtitleTrack = track;
                mCurrentSubtitleTrack.buildSampleTable();

                mCurrentSubtitleTrack.seekTo(timeUs, mIsFragmented);

                return TrackType.SUBTITLE;
            } else {
                if (mCurrentSubtitleTrack != track) {
                    if (LOGS_ENABLED) Log.w(TAG, "track " + index + " is not selected");
                    return TrackType.UNKNOWN;
                }

                mCurrentSubtitleTrack.releaseSampleTable();
                mCurrentSubtitleTrack = null;

                return TrackType.SUBTITLE;
            }
        } else if (trackType == TrackType.VIDEO) {
            if (LOGS_ENABLED) Log.w(TAG, "Video tracks can't be changed");
            return TrackType.UNKNOWN;
        }

        return TrackType.UNKNOWN;
    }

    @Override
    public int getSelectedTrackIndex(TrackType type) {
        if (type == TrackType.AUDIO && mCurrentAudioTrack != null) {
            return mCurrentAudioTrack.getTrackIndex();
        } else if (type == TrackType.VIDEO && mCurrentVideoTrack != null) {
            return mCurrentVideoTrack.getTrackIndex();
        } else if (type == TrackType.SUBTITLE && mCurrentSubtitleTrack != null) {
            return mCurrentSubtitleTrack.getTrackIndex();
        }
        return -1;
    }

    @Override
    public boolean canParse() {
        BoxHeader header = getNextBoxHeader();
        if (header == null || header.boxType != BOX_ID_FTYP) {
            if (LOGS_ENABLED)
                Log.w(TAG, "ftyp box not found at start of file, assume not ISO compatible for now");

            return false;
        }
        try {
            int majorBrand = mDataSource.readInt();
            mDataSource.skipBytes(4); // minor_version
            int numCompatibilityBrands = (int)((header.boxDataSize - 8) / 4);
            if (numCompatibilityBrands == 0) {
                for (int compatibleBrand : ISOBMFF_COMPATIBLE_BRANDS) {
                    if (compatibleBrand == majorBrand) {
                        return true;
                    }
                }
            }
            for (int i = 0; i < numCompatibilityBrands; i++) {
                int brand = mDataSource.readInt();
                for (int compatibleBrand : ISOBMFF_COMPATIBLE_BRANDS) {
                    if (compatibleBrand == brand) {
                        return true;
                    }
                }
            }
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException in canParse()", e);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException in canParse()", e);
        }
        return false;
    }

    private IsoTrack createTrack() {
        return new IsoTrack();
    }

    public long getMoofDataSize() {
        return mMoofDataSize;
    }

    private long findNextMoofForTrack(int trackId) {
        BoxHeader header;
        long moofOffset = Integer.MIN_VALUE;
        boolean parseOk;
        long sourceLength;
        try {
            sourceLength = mDataSource.length();
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "could not read length of data source", e);
            return Integer.MIN_VALUE;
        }
        mDesiredTrackIdFound = false;
        mSkipInsertSamples = true;
        do {
            mCurrentMoofTrackId = -1;
            header = getNextBoxHeader();
            if (header == null) {
                return -1;
            }
            parseOk = parseBox(header);
            if (header.boxType == BOX_ID_MOOF) {
                moofOffset = header.startOffset;
            }
        } while (!mDesiredTrackIdFound && parseOk && (mCurrentOffset < sourceLength ||
                sourceLength == -1));
        if (!parseOk || (mCurrentOffset >= sourceLength && sourceLength != -1)) {
            if (LOGS_ENABLED) Log.i(TAG, "Did not find any later moof for track " + trackId);
            moofOffset = Integer.MIN_VALUE;
        }
        mSkipInsertSamples = false;
        return moofOffset;
    }

    private static int parseUE(BitReader br) {
        int leadingZeroBits = 0;
        while (br.getBits(1) == 0) {
            leadingZeroBits++;
        }

        return (1 << leadingZeroBits) - 1 + br.getBits(leadingZeroBits);
    }

    protected void parseSPS(byte[] spsData) {
        BitReader br = new BitReader(spsData);
        br.skipBits(4 * 8); // NAL marker 00 00 00 01
        br.skipBits(3); // NAL Header - forbidden_zero_bit(1) + nal_ref_idc(2)

        int nal_unit_type = br.getBits(5);

        if (nal_unit_type != AVC_NAL_UNIT_TYPE_SPS) {
            return;
        }

        int profile_idc = br.getBits(8);
        br.skipBits(8); // constraint_set0_flag ... constraint_set5 _flag +
                        // reserved_zero_2bits
        br.getBits(8); // level_idc
        parseUE(br); // seq_parameter_set_id

        if (profile_idc == 100 || profile_idc == 110 ||
                profile_idc == 122 || profile_idc == 244 || profile_idc == 44 ||
                profile_idc == 83 || profile_idc == 86 || profile_idc == 118 ||
                profile_idc == 128 || profile_idc == 138) {
            int chroma_format_idc = parseUE(br);
            if (chroma_format_idc == 3) {
                br.getBits(1); // separate_colour_plane_flag
            }

            parseUE(br); // bit_depth_luma_minus8
            parseUE(br); // bit_depth_chroma_minus8
            br.getBits(1); // qpprime_y_zero_transform_bypass_flag

            int seq_scaling_matrix_present_flag = br.getBits(1);
            if (seq_scaling_matrix_present_flag == 1) {
                int nbrScaling = 8;
                if (chroma_format_idc == 3) {
                    nbrScaling = 12;
                }

                for (int i = 0; i < nbrScaling; ++i) {
                    br.getBits(1); // seq_scaling_list_present_flag[ i ]
                }
            }
        }

        parseUE(br); // log2_max_frame_num_minus4
        int pic_order_cnt_type = parseUE(br);
        if (pic_order_cnt_type == 0) {
            parseUE(br); // log2_max_pic_order_cnt_lsb_minus4
        } else if (pic_order_cnt_type == 1) {
            br.getBits(1); // delta_pic_order_always_zero_flag
            parseUE(br); // offset_for_non_ref_pic TODO: parseSE
            parseUE(br); // offset_for_top_to_bottom_field TODO: parseSE

            int num_ref_frames_in_pic_order_cnt_cycle = parseUE(br);
            for (int i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
                parseUE(br); // offset_for_ref_frame[ i ] TODO: parseSE
            }
        }

        parseUE(br); // max_num_ref_frames
        br.getBits(1); // gaps_in_frame_num_value_allowed_flag

        parseUE(br); // pic_width_in_mbs_minus1
        parseUE(br); // pic_height_in_map_units_minus1
        int frame_mbs_only_flag = br.getBits(1);
        if (frame_mbs_only_flag == 0) {
            br.getBits(1); // mb_adaptive_frame_field_flag
        }

        br.getBits(1); // direct_8x8_inference_flag
        int frame_cropping_flag = br.getBits(1);
        if (frame_cropping_flag == 1) {
            parseUE(br); // frame_crop_left_offset
            parseUE(br); // frame_crop_right_offset
            parseUE(br); // frame_crop_top_offset
            parseUE(br); // frame_crop_bottom_offset
        }

        int vui_parameters_present_flag = br.getBits(1);
        if (vui_parameters_present_flag == 1) {
            int aspect_ratio_info_present_flag = br.getBits(1);
            if (aspect_ratio_info_present_flag == 1) {
                int aspect_ratio_idc = br.getBits(8);

                int sar_width = 0;
                int sar_height = 0;
                if (aspect_ratio_idc > 0 && aspect_ratio_idc < 17) {
                    int[] width = {
                            1, 12, 10, 16, 40, 24, 20, 32, 80, 18, 15, 64, 160, 4, 3, 2
                    };
                    int[] height = {
                            1, 11, 11, 11, 33, 11, 11, 11, 33, 11, 11, 33, 99, 3, 2, 1
                    };

                    sar_width = width[aspect_ratio_idc - 1];
                    sar_height = height[aspect_ratio_idc - 1];
                } else if (aspect_ratio_idc == 255) {
                    sar_width = br.getBits(16);
                    sar_height = br.getBits(16);
                }

                if (mCurrentMediaFormat != null && sar_width > 0 && sar_height > 0) {
                    mCurrentMediaFormat.setInteger(KEY_SAR_WIDTH, sar_width);
                    mCurrentMediaFormat.setInteger(KEY_SAR_HEIGHT, sar_height);
                }
            }
        }
        // TODO: parse the rest of vui_parameters
    }

    @Override
    public synchronized boolean hasDataAvailable(TrackType type) throws IOException {
        boolean hasData = true;

        if (type == TrackType.AUDIO && mCurrentAudioTrack != null) {
            hasData = mCurrentAudioTrack.hasDataAvailable(mIsFragmented);
        } else if (type == TrackType.VIDEO && mCurrentVideoTrack != null) {
            hasData = mCurrentVideoTrack.hasDataAvailable(mIsFragmented);
        } else if (type == TrackType.SUBTITLE && mCurrentSubtitleTrack != null) {
            hasData = mCurrentSubtitleTrack.hasDataAvailable(mIsFragmented);
        }

        return hasData;
    }
}
