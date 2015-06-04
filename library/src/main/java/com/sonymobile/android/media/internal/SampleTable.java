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

import java.nio.ByteBuffer;

import android.util.Log;

class SampleTable {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SampleTable";

    private ByteBuffer mSttsData;

    private ByteBuffer mCttsData;

    private ByteBuffer mStscData;

    private ByteBuffer mStszData;

    private ByteBuffer mStcoData;

    private ByteBuffer mStssData;

    private int mSampleCount;

    private int mTimeScale;

    private boolean mUseLongChunkOffsets;

    private long mDurationUs = 0;

    private int[] mSampleSize;

    private int[] mSampleDescriptionIndex;

    private long[] mSampleOffset;

    private boolean[] mSampleIsSyncSample;

    private long[] mSampleTimestampUs;

    private long[] mSampleDurationUs;

    public SampleTable() {

    }

    public boolean isUsingLongChunkOffsets() {
        return mUseLongChunkOffsets;
    }

    public void setStcoData(byte[] data) {
        mStcoData = ByteBuffer.wrap(data);
        mUseLongChunkOffsets = false;
    }

    public void setCo64Data(byte[] data) {
        mStcoData = ByteBuffer.wrap(data);
        mUseLongChunkOffsets = true;
    }

    public void setSttsData(byte[] data) {
        mSttsData = ByteBuffer.wrap(data);
    }

    public void setStssData(byte[] data) {
        mStssData = ByteBuffer.wrap(data);
    }

    public void setStscData(byte[] data) {
        mStscData = ByteBuffer.wrap(data);
    }

    public void setCttsData(byte[] data) {
        mCttsData = ByteBuffer.wrap(data);
    }

    public void setStszData(byte[] data) {
        mStszData = ByteBuffer.wrap(data);
    }

    public long getTimestampUs(int i) {
        return mSampleTimestampUs[i];
    }

    public long getDurationUs(int i) {
        return mSampleDurationUs[i];
    }

    public long getOffset(int i) {
        return mSampleOffset[i];
    }

    public int getSize(int i) {
        return mSampleSize[i];
    }

    public int getSampleDescriptionIndex(int i) {
        return mSampleDescriptionIndex[i];
    }

    public boolean isSyncSample(int i) {
        return mSampleIsSyncSample[i];
    }

    ByteBuffer getStszData() {
        return mStszData;
    }

    ByteBuffer getStcoData() {
        return mStcoData;
    }

    public boolean calculateSampleCountAndDuration() {
        if (mStszData == null || mStszData.capacity() == 0 || mSttsData == null
                || mSttsData.capacity() == 0 ) {
            if (LOGS_ENABLED) Log.e(TAG, "unable to calculate sample count and duration");

            if (LOGS_ENABLED && mStszData == null) {
                Log.e(TAG, "missing mStszData");
            }
            if (LOGS_ENABLED && mSttsData == null) {
                Log.e(TAG, "missing mSttsData");
            }
            return false;
        }

        mStszData.rewind();
        mSttsData.rewind();

        // stsz data
        mStszData.getInt(); // version and flags
        mStszData.getInt(); // sample_size
        mSampleCount = mStszData.getInt(); // sample_count
        if (mSampleCount == 0) {
            return false;
        }

        // stts data
        mSttsData.getInt(); // version and flags
        int sttsEntryCount = mSttsData.getInt(); // entry_count

        long sttsCurrentSampleTimeToSample = 0;
        int sttsCurrentSampleCount;
        int sttsCurrentSampleDelta;
        for (int i = 0; i < sttsEntryCount; ++i) {
            sttsCurrentSampleCount = mSttsData.getInt();
            sttsCurrentSampleDelta = mSttsData.getInt();
            sttsCurrentSampleTimeToSample += sttsCurrentSampleCount *
                    ((long)sttsCurrentSampleDelta * 1000000 / mTimeScale);
        }

        mDurationUs = sttsCurrentSampleTimeToSample;
        return true;
    }

    @SuppressWarnings("unused")
    public boolean buildSampleTable() {
        if (mStszData == null || mStszData.capacity() == 0 || mSttsData == null
                || mSttsData.capacity() == 0 || mStscData == null || mStscData.capacity() == 0
                || mStcoData == null || mStcoData.capacity() == 0) {
            if (LOGS_ENABLED) Log.e(TAG, "unable to build sample table");

            if (LOGS_ENABLED && mStszData == null) {
                Log.e(TAG, "missing mStszData");
            }
            if (LOGS_ENABLED && mSttsData == null) {
                Log.e(TAG, "missing mSttsData");
            }
            if (LOGS_ENABLED && mStscData == null) {
                Log.e(TAG, "missing mStscData");
            }
            if (LOGS_ENABLED && mStcoData == null) {
                Log.e(TAG, "missing mStcoData");
            }
            return false;
        }

        mStszData.rewind();
        mSttsData.rewind();
        mStscData.rewind();
        mStcoData.rewind();

        if (mStssData != null) {
            mStssData.rewind();
        }

        if (mCttsData != null) {
            mCttsData.rewind();
        }

        // stsz data
        mStszData.getInt(); // version and flags
        int sampleSize = mStszData.getInt(); // sample_size
        mSampleCount = mStszData.getInt(); // sample_count
        if (mSampleCount == 0) {
            return false;
        }
        mSampleSize = new int[mSampleCount];
        mSampleDescriptionIndex = new int[mSampleCount];
        mSampleOffset = new long[mSampleCount];
        mSampleIsSyncSample = new boolean[mSampleCount];
        mSampleTimestampUs = new long[mSampleCount];
        mSampleDurationUs = new long[mSampleCount];

        // stts data
        mSttsData.getInt(); // version and flags
        int sttsEntryCount = mSttsData.getInt(); // entry_count
        int sttsCurrentEntry = 1;
        int sttsCurrentSampleCount = mSttsData.getInt();
        int sttsSampleCounter = 1;
        int sttsCurrentSampleDelta = mSttsData.getInt();
        long sttsCurrentSampleTimeToSample = 0;

        // ctss data
        int cttsSampleCount = 0;
        int cttsSampleOffset = 0;
        int cttsCurrentEntrySampleCount = 1;
        if (mCttsData != null) {
            mCttsData.getInt(); // version and flags
            mCttsData.getInt(); // entry_count
            cttsSampleCount = mCttsData.getInt(); // sample_count
            cttsSampleOffset = mCttsData.getInt(); // sample_offset
        }

        // stco data
        mStcoData.getInt(); // version and flags
        int stcoEntryCount = mStcoData.getInt(); // entry_count
        long stcoChunkOffset = mUseLongChunkOffsets ? mStcoData.getLong()
                : 0xFFFFFFFFL & mStcoData.getInt(); // chunk_offset

        // stsc data
        mStscData.getInt(); // version and flags
        int stscEntryCount = mStscData.getInt(); // entry_count
        mStscData.getInt(); // first_chunk
        int stscSamplesPerChunk = mStscData.getInt(); // samples_per_chunk
        int stscSampleDescriptionIndex = mStscData.getInt(); // sample_description_index
        int stscNextFirstChunk = stcoEntryCount + 1;
        if (stscEntryCount > 1) {
            stscNextFirstChunk = mStscData.getInt();
        }

        int chunkCount = 1;
        int stscCurrentEntryNumber = 1;
        int stscSamplePerChunkCount = 1;
        long currentSampleOffset = stcoChunkOffset;

        // stss data
        int stssEntryCount = 0;
        int stssSampleNumber = 0;
        int stssTableCount = 0;
        if (mStssData != null) {
            mStssData.getInt(); // version and flags
            stssEntryCount = mStssData.getInt(); // entry_count
            stssSampleNumber = mStssData.getInt(); // sample_number;
        }

        for (int i = 0; i < mSampleCount; i++) {
            // Chunk data for sample
            if (stscSamplePerChunkCount > stscSamplesPerChunk) {
                chunkCount++;
                stscSamplePerChunkCount = 1;
                // STCO should be interpreted as an unsigned int.
                currentSampleOffset = mUseLongChunkOffsets ? mStcoData.getLong()
                        : 0xFFFFFFFFL & mStcoData.getInt();
            }

            if (chunkCount == stscNextFirstChunk) {
                stscSamplesPerChunk = mStscData.getInt();
                stscSampleDescriptionIndex = mStscData.getInt();
                stscCurrentEntryNumber++;
                if (stscCurrentEntryNumber < stscEntryCount) {
                    stscNextFirstChunk = mStscData.getInt();
                } else {
                    stscNextFirstChunk = Integer.MAX_VALUE;
                }
            }

            if (mCttsData != null) {
                if (cttsCurrentEntrySampleCount > cttsSampleCount) {
                    cttsCurrentEntrySampleCount = 1;
                    cttsSampleCount = mCttsData.getInt();
                    cttsSampleOffset = mCttsData.getInt();
                }
            }

            // Stsz data for sample
            int entrySize = sampleSize;
            if (sampleSize == 0) {
                entrySize = mStszData.getInt(); // entry_size
            }
            mSampleSize[i] = entrySize;

            // stts data for sample
            if (sttsSampleCounter > sttsCurrentSampleCount) {
                sttsCurrentSampleCount = mSttsData.getInt();
                sttsCurrentSampleDelta = mSttsData.getInt();
                sttsSampleCounter = 1;
                sttsCurrentEntry++;

                if (sttsCurrentEntry > sttsEntryCount) {
                    return false;
                }
            }
            mSampleTimestampUs[i] = sttsCurrentSampleTimeToSample;
            mSampleDurationUs[i] = (long)sttsCurrentSampleDelta * 1000000 / mTimeScale;
            sttsCurrentSampleTimeToSample += (long)sttsCurrentSampleDelta * 1000000 / mTimeScale;
            sttsSampleCounter++;

            // ctts data for sample
            if (mCttsData != null) {
                cttsCurrentEntrySampleCount++;
                mSampleTimestampUs[i] += (int)((long)cttsSampleOffset * 1000000 / mTimeScale);
            }

            mSampleDescriptionIndex[i] = stscSampleDescriptionIndex;
            mSampleOffset[i] = currentSampleOffset;

            currentSampleOffset += entrySize;
            stscSamplePerChunkCount++;

            // stss data
            if (mStssData != null) {
                if (i+1 == stssSampleNumber) {
                    stssTableCount++;
                    mSampleIsSyncSample[i] = true;
                    if (stssTableCount < stssEntryCount) {
                        stssSampleNumber = mStssData.getInt();
                    }
                }
            } else {
                mSampleIsSyncSample[i] = true;
            }
        }
        mDurationUs = sttsCurrentSampleTimeToSample;
        return true;
    }

    public void releaseSampleTable() {
        mSampleSize = null;
        mSampleDescriptionIndex = null;
        mSampleOffset = null;
        mSampleIsSyncSample = null;
        mSampleTimestampUs = null;
        mSampleDurationUs = null;
    }

    public int getSampleCount() {
        return mSampleCount;
    }


    public void setTimescale(int timeScale) {
        mTimeScale = timeScale;
    }

    public int findSampleIndex(long seekTimeUs) {
        long sampleTimeUs;
        int sampleCount = 0;
        int latestSyncSampleIndex = 0;
        for (int i = 0; i < mSampleCount; i++) {
            if (mSampleIsSyncSample[i]) {
                sampleTimeUs = mSampleTimestampUs[i];
                if (sampleTimeUs >= seekTimeUs) {
                    break;
                }
                latestSyncSampleIndex = sampleCount;
            }
            sampleCount++;
        }
        return latestSyncSampleIndex;
    }

    public long getTimeOfSample(int sampleIndex) {
        if (sampleIndex < mSampleCount) {
            return mSampleTimestampUs[sampleIndex];
        }
        return -1;
    }

    public long getDurationUs() {
        return mDurationUs;
    }

}
