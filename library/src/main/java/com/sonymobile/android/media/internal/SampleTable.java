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
import java.util.ArrayList;

import android.util.Log;

class SampleTable {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "SampleTable";

    ByteBuffer mSttsData;

    ByteBuffer mCttsData;

    ByteBuffer mStscData;

    ByteBuffer mStszData;

    ByteBuffer mStcoData;

    ByteBuffer mStssData;

    ArrayList<Sample> mSamples;

    private int mSampleCount;

    private int mTimeScale;

    private boolean mUseLongChunkOffsets;

    private long mDurationUs = 0;

    public SampleTable() {

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

        mSamples = new ArrayList<Sample>();

        // stsz data
        mStszData.getInt(); // version and flags
        int sampleSize = mStszData.getInt(); // sample_size
        mSampleCount = mStszData.getInt(); // sample_count
        if (mSampleCount == 0) {
            return false;
        }

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

        for (int i = 1; i <= mSampleCount; i++) {

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

            Sample sample = new Sample();

            // Stsz data for sample
            int entrySize = sampleSize;
            if (sampleSize == 0) {
                entrySize = mStszData.getInt(); // entry_size
            }
            sample.setSampleSize(entrySize);

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
            sample.setSampleTimeToSampleUs(sttsCurrentSampleTimeToSample);
            sample.setSampleDurationUs((long)sttsCurrentSampleDelta * 1000000 / mTimeScale);
            sttsCurrentSampleTimeToSample += (long)sttsCurrentSampleDelta * 1000000 / mTimeScale;
            sttsSampleCounter++;

            // ctts data for sample
            if (mCttsData != null) {
                cttsCurrentEntrySampleCount++;
                sample.setSampleCttsUs((int)((long)cttsSampleOffset * 1000000 / mTimeScale));
            }

            sample.setSampleDescriptionIndex(stscSampleDescriptionIndex);
            sample.setSampleOffset(currentSampleOffset);

            currentSampleOffset += entrySize;
            stscSamplePerChunkCount++;

            // stss data
            if (mStssData != null) {
                if (i == stssSampleNumber) {
                    stssTableCount++;
                    sample.setIsSyncSample();
                    if (stssTableCount < stssEntryCount) {
                        stssSampleNumber = mStssData.getInt();
                    }
                }
            } else {
                sample.setIsSyncSample();
            }

            mSamples.add(sample);
        }
        mDurationUs = sttsCurrentSampleTimeToSample;

        mSttsData = null;
        mCttsData = null;
        mStscData = null;
        mStszData = null;
        mStcoData = null;
        mStssData = null;

        return true;
    }

    public int getSampleCount() {
        return mSampleCount;
    }

    public Sample getSample(int index) {
        if (index > mSampleCount) {
            return null;
        }
        return mSamples.get(index);
    }

    public void setTimescale(int timeScale) {
        mTimeScale = timeScale;
    }

    public int findSampleIndex(long seekTimeUs) {
        long sampleTimeUs = 0;
        int sampleCount = 0;
        int latestSyncSampleIndex = 0;
        for (int i = 0; i < mSampleCount; i++) {
            Sample sample = mSamples.get(i);
            if (sample.isSyncSample()) {
                sampleTimeUs = sample.getTimestampUs();
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
            return mSamples.get(sampleIndex).getTimestampUs();
        }
        return -1;
    }

    public long getDurationUs() {
        return mDurationUs;
    }

}

class Sample {

    private int mSampleSize;

    private int mSampleDescriptionIndex;

    private long mSampleOffset;

    private boolean mSampleIsSyncSample;

    private long mSampleTimestampUs;

    private long mSampleDurationUs;

    public Sample() {

    }

    public void setSampleCttsUs(int cttsUs) {
        mSampleTimestampUs += cttsUs;
    }

    public void setSampleSize(int entrySize) {
        mSampleSize = entrySize;
    }

    public void setIsSyncSample() {
        mSampleIsSyncSample = true;
    }

    public void setSampleOffset(long offset) {
        mSampleOffset = offset;
    }

    public void setSampleDescriptionIndex(int sampleDescriptionIndex) {
        mSampleDescriptionIndex = sampleDescriptionIndex;
    }

    public void setSampleTimeToSampleUs(long sttsUs) {
        mSampleTimestampUs = sttsUs;
    }

    public long getTimestampUs() {
        return mSampleTimestampUs;
    }

    public void setSampleDurationUs(long sttsDeltaUs) {
        mSampleDurationUs = sttsDeltaUs;
    }

    public long getDurationUs() {
        return mSampleDurationUs;
    }

    public long getOffset() {
        return mSampleOffset;
    }

    public int getSize() {
        return mSampleSize;
    }

    public int getSampleDescriptionIndex() {
        return mSampleDescriptionIndex;
    }

    public boolean isSyncSample() {
        return mSampleIsSyncSample;
    }

}
