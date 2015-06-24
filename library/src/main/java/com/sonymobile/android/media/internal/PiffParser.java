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
import java.util.ArrayList;

import android.media.MediaCodec;
import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.TrackInfo.TrackType;

public class PiffParser extends ISOBMFFParser {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "PiffParser";

    private static final int FTYP_BRAND_PIFF = fourCC('p', 'i', 'f', 'f');

    private static final int SCHEME_TYPE_CENC = fourCC('c', 'e', 'n', 'c');

    private static final int SCHEME_TYPE_PIFF = FTYP_BRAND_PIFF;

    private static final int BOX_ID_AVCN = fourCC('a', 'v', 'c', 'n');

    protected static final int HANDLER_TYPE_CFF_SUBTITLE = fourCC('s', 'u', 'b', 't');

    private static final String UUID_PSSH = "D08A4F1810F34A82B6C832D8ABA183D3";

    private static final String UUID_SAMPLE_ENCRYPTION = "A2394F525A9B4F14A2446C427C648DF4";

    private static final String UUID_TRACK_ENCRYPTION = "8974DBCE7BE74C5184F97148F9882554";

    private static final int UUID_SIZE = 16;

    public PiffParser(DataSource source) {
        super(source);
        if (LOGS_ENABLED) Log.v(TAG, "create PiffParser from source");
    }

    @Override
    public boolean parse() {
        if (mIsParsed) {
            return mParseResult;
        }

        boolean parseOK = super.parse();

        if (mCurrentVideoTrack != null) {
            mMetaDataValues.put(KEY_MIME_TYPE, MimeType.PIFF);
        } else {
            mMetaDataValues.put(KEY_MIME_TYPE, MimeType.PIFF_AUDIO);
        }
        mMetaDataValues.put(MetaData.KEY_PAUSE_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_SEEK_AVAILABLE, 1);
        mMetaDataValues.put(MetaData.KEY_NUM_TRACKS, mTracks.size());

        updateAspectRatio();

        return parseOK;
    }

    @Override
    protected boolean parseBox(BoxHeader header) {
        if (header == null) {
            return false;
        }
        mCurrentBoxSequence.add(header);
        long boxEndOffset = mCurrentOffset + header.boxDataSize;
        boolean parseOK;
        if (header.boxType == BOX_ID_AVCN) {
            return parseAvcn(header);
        } else {
            parseOK = super.parseBox(header);
        }
        mCurrentOffset = boxEndOffset;
        mCurrentBoxSequence.removeLast();
        return parseOK;
    }

    @Override
    protected boolean parseUuid(BoxHeader header) {
        byte[] userType = new byte[UUID_SIZE];
        try {
            mDataSource.read(userType);
            String uuidUserType = Util.bytesToHex(userType);
            if (uuidUserType.equals(UUID_PSSH)) {
                // uuid in moov box (pssh data)
                return parsePsshDataFromUuid(header);
            } else if (uuidUserType.equals(UUID_SAMPLE_ENCRYPTION)) {
                // uuid in traf box
                if (mCurrentMoofTrackId == mCurrentTrackId && !mSkipInsertSamples
                        && !mParsedSencData) {
                    mParsedSencData = true;
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
                        ivSize = mCurrentTrack.mDefaultIVSize;
                        kID = mCurrentTrack.mDefaultKID;
                    }
                    try {
                        int sampleCount = mDataSource.readInt();

                        ArrayList<CryptoInfo> cryptoInfos =
                                new ArrayList<>(sampleCount);

                        for (int i = 0; i < sampleCount; i++) {
                            CryptoInfo info = new CryptoInfo();
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

                            if (info.numBytesOfClearData[0] == 0 &&
                                    mCurrentTrack.getTrackType() == TrackType.VIDEO) {
                                info.iv[15] = (byte)mNALLengthSize;
                            }

                            cryptoInfos.add(info);
                        }
                        mCurrentTrack.addCryptoInfos(cryptoInfos);

                    } catch (EOFException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'senc' uuid box", e);

                        mCurrentBoxSequence.removeLast();
                        return false;
                    } catch (IOException e) {
                        if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'senc' uuid box", e);

                        mCurrentBoxSequence.removeLast();
                        return false;
                    }
                }
            } else if (uuidUserType.equals(UUID_TRACK_ENCRYPTION)) {
                // uuid in schi box
                mDataSource.skipBytes(7); // version, flags and algorithm id
                int defaultIVSize = mDataSource.readByte();
                byte[] defaultKID = new byte[16];
                mDataSource.read(defaultKID);
                mCurrentTrack.setDefaultEncryptionData(defaultIVSize,
                        defaultKID);
            } else {
                mCurrentOffset -= UUID_SIZE;
                return super.parseBox(header);
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error parsing 'uuid' box", e);
            return false;
        }

        return true;
    }

    @Override
    protected boolean parseSchm(BoxHeader header) {
        try {
            int versionFlags = mDataSource.readInt();
            int schemeType = mDataSource.readInt();
            if (schemeType != SCHEME_TYPE_PIFF && schemeType != SCHEME_TYPE_CENC) {
                if (LOGS_ENABLED) Log.e(TAG, "Scheme is not of type 'piff' or 'cenc'");
                return false;
            } else {
                mDataSource.skipBytes(4); // Skip schemaVersion
                if ((versionFlags & 0x01) != 0) {
                    // TODO read scheme_uri if we're interested
                    // byte[] data = new byte[(int)header.boxDataSize - 12];
                    // mDataSource.read(data);
                    mDataSource.skipBytes(header.boxDataSize - 12);
                }
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

    private boolean parseAvcn(BoxHeader header) {
        byte[] data = new byte[(int)header.boxDataSize];
        try {
            if (mDataSource.readAt(mCurrentOffset, data, data.length) != data.length) {
                mCurrentBoxSequence.removeLast();
                return false;
            }
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while parsing 'avcc' box", e);
        }
        AvccData avccData = parseAvccData(data);
        if (avccData == null) {
            mCurrentBoxSequence.removeLast();
            return false;
        }
        MediaFormat trackFormat = mCurrentTrack.getMediaFormat();
        ByteBuffer csd0 = ByteBuffer.wrap(avccData.spsBuffer.array());
        ByteBuffer csd1 = ByteBuffer.wrap(avccData.ppsBuffer.array());
        trackFormat.setByteBuffer("csd-0", csd0);
        trackFormat.setByteBuffer("csd-1", csd1);
        // TODO: Make sure new format is sent to codec

        parseSPS(avccData.spsBuffer.array());
        return true;
    }

    protected boolean parsePsshDataFromUuid(BoxHeader header) {
        try {
            mDataSource.skipBytes(4);
            byte[] systemId = new byte[16];
            mDataSource.read(systemId);
            addMetaDataValue(KEY_DRM_UUID, systemId);
            int psshDataSize = mDataSource.readInt();
            byte[] psshData = new byte[psshDataSize];
            mDataSource.read(psshData);
            mMetaDataValues.put(KEY_DRM_PSSH_DATA, psshData);
            return true;
        } catch (IOException e) {
            if (LOGS_ENABLED)
                Log.e(TAG, "Exception when reading pssh from UUID box" + e);
            return false;
        }
    }

    @Override
    public boolean canParse() {
        BoxHeader header = getNextBoxHeader();
        if (header == null || header.boxType != BOX_ID_FTYP) {
            return false;
        }
        try {
            boolean isMajorBrandPiff = false;
            int majorBrand = mDataSource.readInt();
            if (majorBrand == FTYP_BRAND_PIFF) {
                isMajorBrandPiff = true;
            }
            mDataSource.skipBytes(4); // minor version
            if (isMajorBrandPiff) {
                return true;
            }
            int numCompatibilityBrands = (int)((header.boxDataSize - 8) / 4);
            for (int i = 0; i < numCompatibilityBrands; i++) {
                int brand = mDataSource.readInt();
                if (brand == FTYP_BRAND_PIFF) {
                    return true;
                }
            }
        } catch (EOFException e) {
            if (LOGS_ENABLED) Log.e(TAG, "EOFException while checking file", e);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IOException while checking file", e);
        }
        return false;
    }

}
