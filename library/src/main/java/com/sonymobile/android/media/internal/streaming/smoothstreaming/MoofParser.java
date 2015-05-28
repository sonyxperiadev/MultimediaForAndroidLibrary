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

import java.io.IOException;
import java.util.ArrayList;

import android.media.MediaCodec;
import android.util.Log;

import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.AccessUnit;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.DataSource;
import com.sonymobile.android.media.internal.ISOBMFFParser;
import com.sonymobile.android.media.internal.MimeType;
import com.sonymobile.android.media.internal.Util;

public class MoofParser extends ISOBMFFParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MoofParser";

    private static final String UUID_SAMPLE_ENCRYPTION = "A2394F525A9B4F14A2446C427C648DF4";

    private final ArrayList<FragmentSample> mFragmentSamples = new ArrayList<>();

    private final ArrayList<MediaCodec.CryptoInfo> mCryptoInfos = new ArrayList<>();

    private final long mTimeScale;

    private long mTimeUs;

    private final String mMime;

    private byte[] mDefaultKID;

    public MoofParser(String mime, long timeScale, byte[] defaultKID) {
        super(null);
        initParsing();
        mMime = mime;
        mTimeScale = timeScale;
        if (defaultKID != null) {
            mDefaultKID = defaultKID.clone();
        }
    }

    public void setNALLengthSize(int NALLengthSize) {
        mNALLengthSize = NALLengthSize;
    }

    @Override
    public AccessUnit dequeueAccessUnit(TrackType type) {
        if (mFragmentSamples.isEmpty()) {
            return AccessUnit.ACCESS_UNIT_NO_DATA_AVAILABLE;
        }

        FragmentSample sample = mFragmentSamples.remove(0);

        byte[] buffer = new byte[sample.size];
        try {
            mDataSource.readAt(sample.dataOffset, buffer, buffer.length);
        } catch (IOException e) {
            return AccessUnit.ACCESS_UNIT_ERROR;
        }

        AccessUnit accessUnit = new AccessUnit(AccessUnit.OK);
        accessUnit.data = buffer;
        accessUnit.size = buffer.length;
        accessUnit.timeUs = mTimeUs + (sample.compositionTimeOffset * 1000000L / mTimeScale);

        if (!mCryptoInfos.isEmpty()) {
            accessUnit.cryptoInfo = mCryptoInfos.remove(0);
            if (accessUnit.cryptoInfo.numBytesOfEncryptedData[0] == -1) {
                accessUnit.cryptoInfo.numBytesOfEncryptedData[0] = accessUnit.size;
            }
        }

        if (type == TrackType.VIDEO && mMime.equals(MimeType.AVC) &&
                !addNALHeader(accessUnit, true, false)) {
            return AccessUnit.ACCESS_UNIT_ERROR;
        }

        mTimeUs += sample.durationTicks * 1000000L / mTimeScale;

        return accessUnit;
    }

    public boolean parseMoof(DataSource source, long timeUs) {
        mDataSource = source;
        mCurrentOffset = source.getCurrentOffset();
        mFirstMoofOffset = 0;
        BoxHeader header = getNextBoxHeader();
        if (header == null || header.boxType != BOX_ID_MOOF) {
            return false;
        }
        mTimeUs = timeUs;
        return super.parseBox(header);
    }

    @Override
    protected boolean parseTfhd(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            mCurrentMoofTrackId = mDataSource.readInt();

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
            return false;
        }
        return true;
    }

    protected boolean parseTrun(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            int sampleCount = mDataSource.readInt();
            int dataOffset = 0;
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
                    if (LOGS_ENABLED)
                        Log.e(TAG,
                                "No applicable values for fragment sample duration available");

                    return false;
                }
                if ((versionFlags & 0x000200) != 0) {
                    sample.size = mDataSource.readInt();
                } else if (mCurrentTrackFragment.defaultSampleSize != Integer.MIN_VALUE) {
                    sample.size = mCurrentTrackFragment.defaultSampleSize;
                } else {
                    if (LOGS_ENABLED)
                        Log.e(TAG, "No applicable values for fragment sample size available");

                    mCurrentBoxSequence.removeLast();
                    return false;
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
                mFragmentSamples.add(sample);
            }
            mPrevTrunDataSize += sumSampleSizes;
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'trun' box", e);
            return false;
        }
        return true;
    }

    @Override
    protected boolean parseUuid(BoxHeader header) {
        byte[] userType = new byte[16];
        try {
            mDataSource.read(userType);
            String uuidUserType = Util.bytesToHex(userType);
            if (uuidUserType.equals(UUID_SAMPLE_ENCRYPTION)) {
                // uuid in traf box
                int versionFlags = mDataSource.readInt();
                int ivSize;
                byte[] kID;
                if ((versionFlags & 0x1) != 0) {
                    mDataSource.skipBytes(3); // Skip Algorithm id
                    ivSize = mDataSource.readInt();
                    kID = new byte[16];
                    mDataSource.read(kID);
                } else {
                    // read default values from track encryption
                    ivSize = 8;
                    kID = mDefaultKID;
                }

                int sampleCount = mDataSource.readInt();

                if (sampleCount != mFragmentSamples.size()) {
                    if (LOGS_ENABLED) Log.e(TAG, "Have " + mFragmentSamples.size() +
                            " fragments but got " + sampleCount + " crypto samples");
                    return false;
                }

                for (int i = 0; i < sampleCount; i++) {
                    MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
                    info.mode = MediaCodec.CRYPTO_MODE_AES_CTR;
                    info.iv = new byte[16];
                    info.key = kID;
                    if (ivSize == 16) {
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

                    mCryptoInfos.add(info);
                }
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'uuid' box", e);
            return false;
        }

        return true;
    }
}
