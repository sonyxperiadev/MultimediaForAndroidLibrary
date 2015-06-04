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

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;

import android.util.Log;

import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.DataSource;
import com.sonymobile.android.media.internal.ISOBMFFParser;

public class DASHISOParser extends ISOBMFFParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DASHISOParser";

    private static final int sBoxIdSidx = fourCC('s', 'i', 'd', 'x');

    private ArrayList<SubSegment> mSegmentIndex;

    private long mInitSize = -1;

    private long mSidxSize = -1;

    public static final int OK = 0;

    public static final int ERROR = -1;

    public static final int ERROR_IO = -2;

    public static final int ERROR_BUFFER_TO_SMALL = -3;

    private byte[] mSubs;

    private byte[] mTkhd;

    public DASHISOParser() {
        super(null);

        initParsing();
    }

    @Override
    public synchronized AccessUnit dequeueAccessUnit(TrackType type) {
        if (type == TrackType.SUBTITLE) {
            AccessUnit accessUnit = super.dequeueAccessUnit(type);

            if (accessUnit.status == AccessUnit.OK) {
                byte[] data = accessUnit.data;

                accessUnit.data = new byte[data.length + mTkhd.length
                        + (mSubs == null ? 0 : mSubs.length)];

                System.arraycopy(mTkhd, 0, accessUnit.data, 0, mTkhd.length);
                int offset = mTkhd.length;

                if (mSubs != null) {
                    System.arraycopy(mSubs, 0, accessUnit.data, offset, mSubs.length);
                    offset += mSubs.length;
                }

                System.arraycopy(data, 0, accessUnit.data, offset, data.length);

                accessUnit.size += offset;
            }

            return accessUnit;
        }

        return super.dequeueAccessUnit(type);
    }

    public int parseInit(DataSource source) {
        mDataSource = source;
        mCurrentOffset = 0;
        mFirstMoofOffset = -1;

        try {
            long sourceLength = mDataSource.length();

            while (mCurrentOffset < sourceLength) {
                BoxHeader header = getNextBoxHeader();
                if (header == null) {
                    break;
                } else if (header.boxType == sBoxIdSidx) {
                    mSidxSize = header.boxDataSize;
                    source.setRange(header.startOffset, header.boxHeaderSize + header.boxDataSize);
                    mSegmentIndex = new ArrayList<>();
                    return parseSegmentIndex(header);
                } else {
                    int err = parseBox12(header);
                    if (err != OK) {
                        return err;
                    }
                }
            }

            if (mInitSize == -1) {
                // Moov not found
                return ERROR_BUFFER_TO_SMALL;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Could not get length from DataSource", e);
            return ERROR_IO;
        }

        return OK;
    }

    @Override
    protected boolean parseTkhd(byte[] data) {
        int boxSize = data.length + 8;
        mTkhd = new byte[boxSize];
        mTkhd[0] = (byte)((boxSize & 0xFF000000) >> 24);
        mTkhd[1] = (byte)((boxSize & 0x00FF0000) >> 16);
        mTkhd[2] = (byte)((boxSize & 0x0000FF00) >> 8);
        mTkhd[3] = (byte)(boxSize & 0x000000FF);
        mTkhd[4] = 't';
        mTkhd[5] = 'k';
        mTkhd[6] = 'h';
        mTkhd[7] = 'd';
        System.arraycopy(data, 0, mTkhd, 8, data.length);
        return super.parseTkhd(data);
    }

    public int parseSidx(DataSource source) {
        mDataSource = source;
        long baseOffset = source.getCurrentOffset();
        mCurrentOffset = baseOffset;

        while (true) {
            BoxHeader header = getNextBoxHeader();
            if (header == null) {
                break;
            } else if (header.boxType == sBoxIdSidx) {
                mSidxSize = header.boxDataSize;
                source.setRange(header.startOffset, header.boxHeaderSize + header.boxDataSize);
                mSegmentIndex = new ArrayList<>();

                return parseSegmentIndex(header);
            } else {
                mCurrentOffset += header.boxDataSize;
                try {
                    if (mCurrentOffset > baseOffset + source.length()) {
                        break;
                    }
                } catch (IOException e) {
                    return ERROR_IO;
                }
            }
        }

        return OK;
    }

    public boolean parseMoof(DataSource source, long timeUs) {
        mDataSource = source;
        mCurrentOffset = source.getCurrentOffset();
        mFirstMoofOffset = 0;
        mSubs = null;
        BoxHeader header = null;
        do {
            if (header != null) {
                mCurrentOffset += header.boxDataSize;
            }
            header = getNextBoxHeader();
            if (header == null) {
                return false;
            }
        } while (header.boxType != BOX_ID_MOOF);
        super.parseBox(header);
        mCurrentTrack.mTimeTicks = timeUs * (long)mCurrentTrack.getTimeScale() / (long)1000000;
        return true;
    }

    private int parseBox12(BoxHeader nextHeader) {
        if (nextHeader.boxType == BOX_ID_MOOV) {
            try {
                mInitSize = mCurrentOffset + nextHeader.boxDataSize;

                if (mDataSource.length() < mCurrentOffset + nextHeader.boxDataSize) {
                    return ERROR_BUFFER_TO_SMALL;
                }
            } catch (IOException e) {
                return ERROR_IO;
            }
            return super.parseBox(nextHeader) ? OK : ERROR;
        } else {
            return super.parseBox(nextHeader) ? OK : ERROR;
        }
    }

    @Override
    protected boolean parseBox(BoxHeader header) {
        if (header == null) {
            return false;
        }
        if (header.boxType == BOX_ID_SUBS) {
            int boxSize = (int)(header.boxHeaderSize + header.boxDataSize);
            mSubs = new byte[(int)(header.boxHeaderSize + header.boxDataSize)];
            mSubs[0] = (byte)((boxSize & 0xFF000000) >> 24);
            mSubs[1] = (byte)((boxSize & 0x00FF0000) >> 16);
            mSubs[2] = (byte)((boxSize & 0x0000FF00) >> 8);
            mSubs[3] = (byte)(boxSize & 0x000000FF);
            mSubs[4] = (byte)((header.boxType & 0xFF000000) >> 24);
            mSubs[5] = (byte)((header.boxType & 0x00FF0000) >> 16);
            mSubs[6] = (byte)((header.boxType & 0x0000FF00) >> 8);
            mSubs[7] = (byte)(header.boxType & 0x000000FF);

            try {
                byte[] subsData = new byte[(int)header.boxDataSize];
                mDataSource.read(subsData);
                System.arraycopy(subsData, 0, mSubs, 8, subsData.length);

                mCurrentOffset = mCurrentOffset + header.boxDataSize;

                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return super.parseBox(header);
    }

    @Override
    protected boolean parseTfhd(BoxHeader header) {
        boolean parseOk = super.parseTfhd(header);
        // DASH only has one track per parser, so always set trackId to match
        // to ensure that parseTrun will parse properly
        mCurrentTrackId = mCurrentMoofTrackId;
        return parseOk;
    }

    private int parseSegmentIndex(BoxHeader header) {
        if (header == null || header.boxType != sBoxIdSidx) {
            return ERROR;
        }

        int versionFlags;
        try {
            versionFlags = mDataSource.readInt();
            int version = versionFlags >> 24;

            mDataSource.skipBytes(4); // reference_ID

            int timescale = mDataSource.readInt();

            long baseOffset = header.startOffset + header.boxHeaderSize + header.boxDataSize;

            long timeTicks, offset;
            if (version == 0) {
                timeTicks = mDataSource.readInt();
                offset = mDataSource.readInt();
            } else {
                timeTicks = mDataSource.readLong();
                offset = mDataSource.readLong();
            }

            offset += baseOffset;

            mDataSource.skipBytes(2); // reserved
            int referenceCount = mDataSource.readShort();

            ArrayList<Long> subSidxOffsets = null;
            for (int i = 0; i < referenceCount; i++) {
                int referenceSize = mDataSource.readInt();
                int referenceType = referenceSize >>> 31;
                referenceSize = referenceSize & 0x7fffffff;
                if (referenceType == 1) {
                    if (subSidxOffsets == null) {
                        subSidxOffsets = new ArrayList<>(referenceCount);
                    }

                    subSidxOffsets.add(offset);

                    mDataSource.skipBytes(8);

                    if (i == referenceCount - 1) {
                        mDataSource.setRange(header.startOffset, (offset - header.startOffset)
                                + referenceSize);
                        mSidxSize = offset + referenceSize;
                    }
                } else {
                    int duration = mDataSource.readInt();

                    SubSegment subsegment = new SubSegment();
                    subsegment.offset = offset;
                    subsegment.size = referenceSize;
                    subsegment.timeUs = timeTicks * (long)1000000 / (long)timescale;
                    subsegment.durationUs = duration * (long)1000000 / (long)timescale;

                    timeTicks += duration;
                    mSegmentIndex.add(subsegment);

                    mDataSource.skipBytes(4);
                }

                offset += referenceSize;
            }

            if (subSidxOffsets != null) {
                for (long subSidxOffset : subSidxOffsets) {
                    mCurrentOffset = subSidxOffset;

                    BoxHeader boxHeader = getNextBoxHeader();

                    int err = parseSegmentIndex(boxHeader);

                    if (err != OK) {
                        if (LOGS_ENABLED) Log.e(TAG, "failed to get next sidx at " + mCurrentOffset
                                + ", err: " + err);
                        return err;
                    }
                }
            }

        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException while parsing 'sidx' box", e);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'sidx' box", e);
        }
        mCurrentOffset += header.boxDataSize;

        return OK;
    }

    public ArrayList<SubSegment> getSegmentIndex() {
        return mSegmentIndex;
    }

    static class SubSegment {
        long offset;

        int size;

        long durationUs;

        long timeUs;
    }

    public long getInitSize() {
        return mInitSize;
    }

    public long getSidxSize() {
        return mSidxSize;
    }
}
