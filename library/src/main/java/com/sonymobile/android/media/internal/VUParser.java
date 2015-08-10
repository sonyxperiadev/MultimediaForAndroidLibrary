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

import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.util.Log;

import org.json.JSONException;

import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.TrackInfo.TrackType;

public class VUParser extends ISOBMFFParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "VUParser";

    private static final int FTYP_BRAND_MGSV = fourCC('M', 'G', 'S', 'V');

    private static final int FTYP_BRAND_MSNV = fourCC('M', 'S', 'N', 'V');

    private static final int BOX_ID_MTDT = fourCC('M', 'T', 'D', 'T');

    private static final int BOX_ID_MTSM = fourCC('M', 'T', 'S', 'M');

    private static final int BOX_ID_MTHD = fourCC('M', 'T', 'H', 'D');

    private static final int BOX_ID_MDST = fourCC('M', 'D', 'S', 'T');

    private static final int BOX_ID_STGS = fourCC('S', 'T', 'G', 'S');

    private static final String UUID_PROF = "50524F4621D24FCEBB88695CFAC9C740";

    private static final String UUID_MTSD = "4D54534421D24FCEBB88695CFAC9C740";

    private static final String UUID_USMT = "55534D5421D24FCEBB88695CFAC9C740";

    private long mMtsdOffset;

    private ArrayList<MtsmEntry> mMtsmList;

    private MtsmEntry mCurrentMtsmEntry;

    private ArrayList<IconInfo> mIconList;

    private ArrayList<String> mHmmpTitles;

    protected boolean mIsMarlinProtected = false;

    private boolean mNeedsMTSD = false;

    private HashMap<String, byte[]> mIconMap;

    public VUParser(DataSource source) {
        super(source);
        if (LOGS_ENABLED) Log.v(TAG, "create VUParser from source");
    }

    @Override
    public boolean parse() {
        if (mIsParsed) {
            return mParseResult;
        }

        boolean parseOK = super.parse();

        mMetaDataValues.put(KEY_MIME_TYPE, MimeType.MNV);
        mMetaDataValues.put(MetaData.KEY_PAUSE_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_SEEK_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_NUM_TRACKS, mTracks.size());

        updateAspectRatio();

        saveVUThumbnails();
        return parseOK;
    }

    @Override
    protected void updateAspectRatio() {
        if (mCurrentVideoTrack != null) {
            if (containsKey(MetaData.KEY_HMMP_PIXEL_ASPECT_RATIO)) {
                int pixelAspectRatio = getInteger(MetaData.KEY_HMMP_PIXEL_ASPECT_RATIO);
                int sarWidth = (pixelAspectRatio >> 16) & 0xFFFF;
                int sarHeight = pixelAspectRatio & 0xFFFF;

                addMetaDataValue(
                        KEY_WIDTH,
                        (mCurrentVideoTrack.getMediaFormat().
                                getInteger(MediaFormat.KEY_WIDTH) * sarWidth) / sarHeight);
            } else {
                super.updateAspectRatio();
            }
        }
    }

    @Override
    protected boolean parseBox(BoxHeader header) {
        if (header == null) {
            return false;
        }
        mCurrentBoxSequence.add(header);
        long boxEndOffset = mCurrentOffset + header.boxDataSize;
        boolean parseOK = true;
        if (header.boxType == BOX_ID_FTYP) {

            try {
                int majorBrand = mDataSource.readInt();
                if (majorBrand == FTYP_BRAND_MGSV) {
                    mIsMarlinProtected = true;
                }
            } catch (EOFException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception parsing 'ftyp' box", e);
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception parsing 'ftyp' box", e);
            }
        } else if (header.boxType == BOX_ID_MTDT) {
            parseOK = parseMtdt(header);
        } else if (header.boxType == BOX_ID_MTSM) {
            if (mMtsmList == null) {
                mMtsmList = new ArrayList<>();
            }
            mCurrentMtsmEntry = new MtsmEntry();
            while (mCurrentOffset < boxEndOffset && parseOK) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                parseOK = parseBox(nextBoxHeader);
            }
            mMtsmList.add(mCurrentMtsmEntry);
        } else if (header.boxType == BOX_ID_MTHD) {
            try {
                mDataSource.skipBytes(12);
                mCurrentMtsmEntry.id = mDataSource.readInt();
                mDataSource.skipBytes(8);
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception reading 'MTHD' box", e);
            }
        } else if (header.boxType == BOX_ID_MDST) {
            try {
                mDataSource.skipBytes(4);
                int metadataSampleCount = mDataSource.readInt();
                mCurrentMtsmEntry.mMdstList = new ArrayList<>(metadataSampleCount);
                for (int i = 0; i < metadataSampleCount; i++) {
                    mDataSource.skipBytes(4); // metadataSampleDescriptionIndex
                    MdstEntry mdstEntry = new MdstEntry();
                    mdstEntry.metadataSampleOffset = mDataSource.readInt();
                    mdstEntry.metadataSampleSize = mDataSource.readInt();
                    mDataSource.skipBytes(8);
                    mCurrentMtsmEntry.mMdstList.add(mdstEntry);
                }
                mNeedsMTSD = true;
            } catch (EOFException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception reading from 'MDST' box", e);
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Exception reading from 'MDST' box", e);
            }
        } else if (header.boxType == BOX_ID_STGS) {
            MediaFormat mediaFormat = MediaFormat
                    .createSubtitleFormat("subtitle/grap-text", mCurrentTrack.getLanguage());
            mCurrentTrack.addSampleDescriptionEntry(mediaFormat);

            mCurrentTrack.getMetaData().addValue(KEY_MIME_TYPE,
                    mediaFormat.getString(MediaFormat.KEY_MIME));
        } else {
            parseOK = super.parseBox(header);
        }
        mCurrentOffset = boxEndOffset;
        mCurrentBoxSequence.removeLast();
        return parseOK;
    }

    @Override
    protected boolean parseUuid(BoxHeader header) {
        byte[] userType = new byte[16];
        try {
            mDataSource.read(userType);
            mCurrentOffset += 16;
            String uuidUserType = Util.bytesToHex(userType);
            if (uuidUserType.equals(UUID_PROF)) {
                return parseUuidPROF(header);
            } else if (uuidUserType.equals(UUID_MTSD)) {
                // MTSD box
                mMtsdOffset = header.startOffset;
                mNeedsMTSD = false;
            } else if (uuidUserType.equals(UUID_USMT)) {
                long boxEndOffset = mCurrentOffset + header.boxDataSize - 16;
                // USMT box
                while (mCurrentOffset < boxEndOffset) {
                    BoxHeader nextBoxHeader = getNextBoxHeader();
                    boolean result = parseBox(nextBoxHeader);
                    if (!result) {
                        return false;
                    }
                }
            } else {
                mCurrentOffset -= 16;
                return super.parseBox(header);
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'uuid' box", e);
            return false;
        }

        return true;
    }

    @Override
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

        if (mIsMarlinProtected) {
            ByteBuffer buffer = parseAvccForMarlin(data);
            if (buffer == null) {
                return false;
            }
            mCurrentMediaFormat.setByteBuffer("csd-0", buffer);

            parseSPS(buffer.array());
        } else {
            AvccData avccData = parseAvccData(data);
            if (avccData == null) {
                return false;
            }
            ByteBuffer csd0 = ByteBuffer.wrap(avccData.spsBuffer.array());
            ByteBuffer csd1 = ByteBuffer.wrap(avccData.ppsBuffer.array());
            mCurrentMediaFormat.setByteBuffer("csd-0", csd0);
            mCurrentMediaFormat.setByteBuffer("csd-1", csd1);

            parseSPS(avccData.spsBuffer.array());
        }

        return true;
    }

    @Override
    protected boolean parseMdat() {
        if (mTracks.size() > 0 && !mIsFragmented && !mNeedsMTSD) {
            mInitDone = true;
        } else if (mIsFragmented && mFirstMoofOffset != -1) {
            mInitDone = true;
        } else {
            mMdatFound = true;
        }

        return true;
    }

    protected boolean parseODSMData(IsoTrack odsmTrack) {
        int kObjectSize = 11;
        SampleTable sampleTable = odsmTrack.getSampleTable();
        if (sampleTable.getSampleCount() > 1) {
            // TODO: Should multiple entries be supported?
            return false;
        }
        ArrayList<SinfData> sinfList = new ArrayList<>(2);

        ByteBuffer stszData = sampleTable.getStszData();
        stszData.rewind();
        stszData.getInt(); // version and flags

        int dataSize  = stszData.getInt();
        if (dataSize == 0) {
            stszData.getInt(); // sample_count
            dataSize  = stszData.getInt();
        }

        byte[] data = new byte[dataSize];
        try {
            ByteBuffer stcoData = sampleTable.getStcoData();
            stcoData.rewind();

            stcoData.getInt(); // version and flags
            stcoData.getInt(); // entry_count

            long sampleOffset;
            if (sampleTable.isUsingLongChunkOffsets()) {
                sampleOffset = stcoData.getLong();
            } else {
                sampleOffset = 0xFFFFFFFFL & stcoData.getInt();
            }

            mDataSource.readAt(sampleOffset, data, dataSize);
            ByteBuffer dataBuffer = ByteBuffer.wrap(data);
            byte updateTag = dataBuffer.get();
            if (updateTag != 1) {
                return false;
            }
            int size = 0;
            int sizePart;
            do {
                sizePart = (dataBuffer.get() & 0xFF);
                size = ((size << 7) & 0xFFFFFF80) | (sizePart & 0x7F);
            } while (sizePart > 128);
            while (size >= kObjectSize) {
                byte descriptorTag = dataBuffer.get();
                if (descriptorTag != 17) {
                    // not mp4 descriptor
                    return false;
                }
                dataBuffer.get(); // ODLength
                dataBuffer.getShort(); // 10 bit ObjectDescriptorID, 1 bit
                                       // URL_FLAG and 5 bit reserved

                byte esTag = dataBuffer.get();
                if (esTag != 0x0F) {
                    return false;
                }
                dataBuffer.get(); // ES Length
                short esTrackReferenceIndex = dataBuffer.getShort();
                byte ipmpDescriptorPointer = dataBuffer.get();
                if (ipmpDescriptorPointer != 0x0A) {
                    // unexpected pointer
                    return false;
                }
                dataBuffer.get(); // ipmpLength
                byte ipmpDescriptorId = dataBuffer.get();
                SinfData sinfData = new SinfData();
                sinfData.esIdReference = esTrackReferenceIndex;
                sinfData.ipmpDescriptorId = ipmpDescriptorId;
                sinfList.add(sinfData);
                size -= kObjectSize;
            }
            dataBuffer.get(); // IPMP Descriptor Update Tag
            int sinfCount = sinfList.size();
            size = 0;
            do {
                sizePart = (dataBuffer.get() & 0xFF);
                size = ((size << 7) & 0xFFFFFF80) | (sizePart & 0x7F);
            } while (sizePart > 128);
            while (size > 0) {
                dataBuffer.get(); // IPMP Descriptor Tag
                int ipmpByteCount = 1;
                int ipmpLength = 0;
                do {
                    sizePart = (dataBuffer.get() & 0xFF);
                    ipmpByteCount++;
                    ipmpLength = ((ipmpLength << 7) & 0xFFFFFF80) | (sizePart & 0x7F);
                } while (sizePart > 128);
                ipmpByteCount += ipmpLength;
                byte ipmpDescriptorId = dataBuffer.get();
                dataBuffer.getShort(); // IPMPS Type
                byte[] ipmpData = new byte[ipmpLength - 3];
                dataBuffer.get(ipmpData);
                SinfData sinfData;
                for (int i = 0; i < sinfCount; i++) {
                    sinfData = sinfList.get(i);
                    if (sinfData.ipmpDescriptorId == ipmpDescriptorId) {
                        sinfData.ipmpData = ipmpData;
                        break;
                    }
                }
                size -= ipmpByteCount;
            }
            int ipmpDataLength = 0;
            for (int i = 0; i < sinfCount; i++) {
                SinfData sinfData = sinfList.get(i);
                ipmpDataLength += sinfData.ipmpData.length;
            }

            int ipmpMetaDataLength = 16 // MARLIN_SYSTEM_ID
                    + 4 // size of all SINF data
                    + 4 // size of SINF box id
                    + 4 * sinfCount // trackIndex * sinfCount
                    + 4 * sinfCount // ipmpLength * sinfCount
                    + ipmpDataLength; // size of ipmpData
            byte[] ipmpMetaData = new byte[ipmpMetaDataLength];
            int offset = 16;

            for (int i = 0; i < offset; i++) {
                int hexVal = Integer
                        .parseInt(Util.MARLIN_SYSTEM_ID.substring(i * 2, i * 2 + 2), 16);
                ipmpMetaData[i] = (byte)hexVal;
            }
            ipmpMetaData[offset++] = (byte)((ipmpDataLength >> 24) & 0xFF);
            ipmpMetaData[offset++] = (byte)((ipmpDataLength >> 16) & 0xFF);
            ipmpMetaData[offset++] = (byte)((ipmpDataLength >> 8) & 0xFF);
            ipmpMetaData[offset++] = (byte)(ipmpDataLength & 0xFF);
            ipmpMetaData[offset++] = 0x73; // S
            ipmpMetaData[offset++] = 0x69; // I
            ipmpMetaData[offset++] = 0x6E; // N
            ipmpMetaData[offset++] = 0x66; // F

            int numTracks = mTracks.size();
            for (int i = 0; i < numTracks; i++) {
                IsoTrack track = (IsoTrack)mTracks.get(i);
                for (int j = 0; j < sinfCount; j++) {
                    SinfData sinfData = sinfList.get(j);
                    if (sinfData.esIdReference == track.getTrackId()) {
                        track.getMetaData().addValue(MetaData.KEY_IPMP_DATA, sinfData.ipmpData);
                        // track index
                        ipmpMetaData[offset++] = (byte)((i >> 24) & 0xFF);
                        ipmpMetaData[offset++] = (byte)((i >> 16) & 0xFF);
                        ipmpMetaData[offset++] = (byte)((i >> 8) & 0xFF);
                        ipmpMetaData[offset++] = (byte)(i & 0xFF);

                        // sinf data length
                        ipmpMetaData[offset++] = (byte)((sinfData.ipmpData.length >> 24) & 0xFF);
                        ipmpMetaData[offset++] = (byte)((sinfData.ipmpData.length >> 16) & 0xFF);
                        ipmpMetaData[offset++] = (byte)((sinfData.ipmpData.length >> 8) & 0xFF);
                        ipmpMetaData[offset++] = (byte)(sinfData.ipmpData.length & 0xFF);

                        System.arraycopy(sinfData.ipmpData, 0, ipmpMetaData, offset,
                                sinfData.ipmpData.length);

                        byte[] tempData = new byte[4 + sinfData.ipmpData.length];
                        tempData[0] = (byte)((sinfData.ipmpData.length >> 24) & 0xFF);
                        tempData[1] = (byte)((sinfData.ipmpData.length >> 16) & 0xFF);
                        tempData[2] = (byte)((sinfData.ipmpData.length >> 8) & 0xFF);
                        tempData[3] = (byte)(sinfData.ipmpData.length & 0xFF);
                        System.arraycopy(sinfData.ipmpData, 0, tempData, 4,
                                sinfData.ipmpData.length);

                        // Create JSON for this track
                        String jsonData;
                        try {
                            jsonData = Util.getJSONIPMPData(tempData);
                        } catch (JSONException e) {
                            if (LOGS_ENABLED)
                                Log.e(TAG, "Exception when creating JSON object" + e);
                            return false;
                        }
                        track.getMediaFormat().setString(KEY_DRM_UUID,
                                Util.MARLIN_SYSTEM_ID);
                        track.getMediaFormat()
                                .setString(KEY_MARLIN_JSON, jsonData);

                        offset += sinfData.ipmpData.length;
                        break;
                    }
                }
            }

            addMetaDataValue(KEY_IPMP_DATA, ipmpMetaData);

            mCurrentTrack.getMetaData()
                    .addValue(KEY_MIME_TYPE, MimeType.OCTET_STREAM);

        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException when parsing ODSM data");
        }
        return true;
    }

    static class SinfData {
        int esIdReference = 0;

        int ipmpDescriptorId = 0;

        byte[] ipmpData;
    }

    private boolean parseUuidPROF(BoxHeader header) {
        long boxEndOffset = mCurrentOffset + header.boxDataSize - 16;
        try {
            mDataSource.skipBytes(4); // version and flags
            mDataSource.skipBytes(4); // profile_entry_count, not necessary
            mCurrentOffset += 8;
            while (mCurrentOffset < boxEndOffset) {
                BoxHeader nextBoxHeader = getNextBoxHeader();
                if (nextBoxHeader.boxType == fourCC('V', 'P', 'R', 'F')) {
                    mDataSource.skipBytes(10 * 4); // skippable data
                    int pixelAspectRatio = mDataSource.readInt();
                    addMetaDataValue(KEY_HMMP_PIXEL_ASPECT_RATIO, pixelAspectRatio);
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

    private static class MtsmEntry {
        int id = -1;

        ArrayList<MdstEntry> mMdstList;
    }

    private static class MdstEntry {
        public int metadataSampleOffset = -1;

        public int metadataSampleSize = -1;
    }

    private boolean parseMtdt(BoxHeader header) {
        // if mCurrentBoxSequence contains trak, then add metadata to current
        // track
        // else metadata is for file
        // we're currently not interested in anything on track level
        try {
            int numberOfUnits = mDataSource.readShort();
            mHmmpTitles = new ArrayList<>(1);
            ArrayDeque<String> titleLanguages = new ArrayDeque<>(1);
            ArrayDeque<String> iconLanguages = new ArrayDeque<>(1);
            for (int i = 0; i < numberOfUnits; i++) {
                short dataUnitSize = mDataSource.readShort();
                int dataTypeID = mDataSource.readInt();
                short language = mDataSource.readShort();
                char l1 = Character.toChars(((language >> 10) & 0x1F) + 96)[0];
                char l2 = Character.toChars(((language >> 5) & 0x1F) + 96)[0];
                char l3 = Character.toChars(((language) & 0x1F) + 96)[0];
                String languageString = "" + l1 + l2 + l3;
                short encodingType = mDataSource.readShort();
                if (encodingType == 0x01) {
                    byte[] metadata = new byte[dataUnitSize - 10];
                    mDataSource.read(metadata);
                    String metadataString = new String(metadata, "UTF-16BE").trim();
                    if ((dataTypeID & 0xFFFF) == 0x01) {
                        mHmmpTitles.add(metadataString);
                        titleLanguages.add(languageString);
                    }
                } else if (encodingType == 0x101) {
                    if (dataTypeID == 0xA04) {
                        if (mIconList == null) {
                            mIconList = new ArrayList<>();
                        }
                        mDataSource.skipBytes(4); // selectionFlags
                        mDataSource.skipBytes(4); // reserved
                        int artworkCount = mDataSource.readInt();
                        for (int j = 0; j < artworkCount; j++) {
                            IconInfo iconInfo = new IconInfo();
                            iconInfo.mtsmId = mDataSource.readInt();
                            iconInfo.mdstIndex = mDataSource.readInt();
                            iconInfo.languageIndex = iconLanguages.size();
                            mDataSource.skipBytes(4);
                            mIconList.add(iconInfo);
                        }
                        iconLanguages.add(languageString);
                    }
                }
            }
            addMetaDataValue(KEY_HMMP_TITLE_LANGUAGES, titleLanguages.toArray());
            addMetaDataValue(KEY_HMMP_ICON_LANGUAGES, iconLanguages.toArray());
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Exception while reading from 'MTDT' box", e);
            return false;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Exception while reading from 'MTDT' box", e);
            return false;
        }
        return true;
    }

    private static class IconInfo {
        public int mtsmId = -1;

        public int mdstIndex = -1;

        public int languageIndex = -1;
    }

    @Override
    public boolean canParse() {
        BoxHeader header = getNextBoxHeader();
        if (header == null || header.boxType != BOX_ID_FTYP) {
            return false;
        }
        try {
            boolean isMajorBrandVU = false;
            int majorBrand = mDataSource.readInt();
            if (majorBrand == FTYP_BRAND_MGSV || majorBrand == FTYP_BRAND_MSNV) {
                isMajorBrandVU = true;
            }
            mDataSource.skipBytes(4); // minorVersion
            if (isMajorBrandVU) {
                return true;
            }
            int numCompatibilityBrands = (int)((header.boxDataSize - 8) / 4);
            for (int i = 0; i < numCompatibilityBrands; i++) {
                int brand = mDataSource.readInt();
                if (brand == FTYP_BRAND_MGSV || brand == FTYP_BRAND_MSNV) {
                    return true;
                }
            }
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException while identifying VU file", e);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while identifying VU file", e);
        }
        return false;
    }

    @Override
    public String getString(String key1, String key2) {
        if (key1 == KEY_HMMP_TITLE) {
            String[] titleLanguages = getStringArray(KEY_HMMP_TITLE_LANGUAGES);
            for (int i = 0; i < titleLanguages.length; i++) {
                if (titleLanguages[i].equals(key2)) {
                    return mHmmpTitles.get(i);
                }
            }
            return null;
        }
        return super.getString(key1, key2);
    }

    @Override
    public byte[] getByteBuffer(String key1, String key2) {
        if (key1.equals(KEY_HMMP_ICON)) {
            byte[] iconData = mIconMap.get(key2);
            if (iconData != null) {
                return iconData;
            } else {
                return null;
            }
        }
        return super.getByteBuffer(key1, key2);
    }

    protected static ByteBuffer parseAvccForMarlin(byte[] buffer) {
        int currentBufferOffset;
        if (buffer[0] != 1) { // configurationVersion
            return null;
        }

        int numSPS = buffer[5] & 31; // numOfSequenceParameterSets
        currentBufferOffset = 6;
        byte[] csdArray = new byte[1024];
        int csdArrayOffset = 0;
        for (int i = 0; i < numSPS; i++) {
            int spsLength = ((buffer[currentBufferOffset++] & 0xFF) << 8
                    | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;

            csdArray[csdArrayOffset++] = 0;
            csdArray[csdArrayOffset++] = 0;
            csdArray[csdArrayOffset++] = (byte)((spsLength >> 8) & 0xFF);
            csdArray[csdArrayOffset++] = (byte)(spsLength & 0xFF);
            for (int j = 0; j < spsLength; j++) {
                csdArray[csdArrayOffset++] = buffer[currentBufferOffset + j];
            }
            currentBufferOffset += spsLength;
        }
        int numPPS = buffer[currentBufferOffset++]; // numOfPictureParameterSets
        for (int i = 0; i < numPPS; i++) {
            int ppsLength = ((buffer[currentBufferOffset++] & 0xFF) << 8
                    | buffer[currentBufferOffset++] & 0xFF) & 0x0000FFFF;
            csdArray[csdArrayOffset++] = 0;
            csdArray[csdArrayOffset++] = 0;
            csdArray[csdArrayOffset++] = (byte)((ppsLength >> 8) & 0xFF);
            csdArray[csdArrayOffset++] = (byte)(ppsLength & 0xFF);
            for (int j = 0; j < ppsLength; j++) {
                csdArray[csdArrayOffset++] = buffer[currentBufferOffset + j];
            }
            currentBufferOffset += ppsLength;
        }
        ByteBuffer csdData = ByteBuffer.wrap(csdArray);
        csdData.limit(csdArrayOffset);
        return csdData;
    }

    protected void saveVUThumbnails() {
        if (mIconList == null) {
            return;
        }
        mIconMap = new HashMap<>();
        String[] iconLanguages = getStringArray(KEY_HMMP_ICON_LANGUAGES);
        int iconCount = mIconList.size();
        for (int i = 0; i < iconCount; i++) {
            IconInfo iconInfo = mIconList.get(i);
            if (mIconMap.containsKey(iconLanguages[iconInfo.languageIndex])) {
                continue;
            }
            int mtsmCount = mMtsmList.size();
            MtsmEntry mtsmEntry = null;
            for (int j = 0; j < mtsmCount; j++) {
                MtsmEntry tmpEntry = mMtsmList.get(j);
                if (tmpEntry.id == iconInfo.mtsmId) {
                    mtsmEntry = tmpEntry;
                    break;
                }
            }
            if (mtsmEntry == null) {
                if (LOGS_ENABLED) Log.e(TAG, "mtsmEntry is null");
                continue;
            }
            int mdstCount = mtsmEntry.mMdstList.size();
            MdstEntry mdstEntry = null;

            if (iconInfo.mdstIndex > 0 && iconInfo.mdstIndex <= mdstCount) {
                mdstEntry = mtsmEntry.mMdstList.get(iconInfo.mdstIndex - 1);
            }

            if (mdstEntry == null) {
                if (LOGS_ENABLED) Log.e(TAG, "mdstEntry is null");
                continue;
            }
            // read icon from file
            byte[] data = new byte[mdstEntry.metadataSampleSize];
            try {
                mDataSource.readAt(mdstEntry.metadataSampleOffset + mMtsdOffset, data,
                        data.length);
            } catch (IOException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Error reading icon data from file", e);
                return;
            }
            mIconMap.put(iconLanguages[iconInfo.languageIndex], data);
        }
    }

    public class VUIsoTrack extends ISOBMFFParser.IsoTrack {

        @Override
        public MediaFormat getMediaFormat() {
            if (mMediaFormat != null && mIsMarlinProtected) {
                mMediaFormat.setInteger("is-marlin-protected", 1);
            }
            return mMediaFormat;
        }

        public AccessUnit dequeueAccessUnit(boolean readFragmented) {
            // if (LOGS_ENABLED) Log.v(TAG, "dequeueAccessUnit track " +
            // mTrackId + " sample " + mCurrentSampleIndex);

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
            accessUnit.timeUs =
                    mSampleTable.getTimestampUs(mCurrentSampleIndex) + mEditMediaTimeTicks;
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
            if (mMediaFormat.getString(MediaFormat.KEY_MIME).equals(MimeType.AVC)
                    && !mIsMarlinProtected) {
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

            /*
             * final char[] hexArray = "0123456789ABCDEF".toCharArray(); char[]
             * hexChars = new char[buffer.length * 2]; for ( int j = 0; j <
             * buffer.length; j++ ) { int v = buffer[j] & 0xFF; hexChars[j * 2]
             * = hexArray[v >>> 4]; hexChars[j * 2 + 1] = hexArray[v & 0x0F]; }
             * String s = new String(hexChars); Log.e(TAG, "buffer = "+s);
             */

            CryptoInfo cryptoInfo = null;
            if (mIsMarlinProtected) {
                if (mType == TrackType.SUBTITLE) {
                    accessUnit.format = mMediaFormat;
                } else {
                    cryptoInfo = new CryptoInfo();
                    int[] numClearBytes = new int[1];
                    numClearBytes[0] = 0;
                    int[] numEncryptedBytes = new int[1];
                    numEncryptedBytes[0] = accessUnit.size;
                    int numSubSamples = 1;
                    cryptoInfo.set(numSubSamples, numClearBytes,
                            numEncryptedBytes, null/*key*/, null/*ivData*/,
                            MediaCodec.CRYPTO_MODE_AES_CTR);
                }
            }
            accessUnit.cryptoInfo = cryptoInfo;

            mLastTimestampUs = accessUnit.timeUs;

            mCurrentSampleIndex++;
            return accessUnit;
        }
    }

    @Override
    public IsoTrack createTrack() {
        return new VUIsoTrack();
    }
}
