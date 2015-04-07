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

package com.sonymobile.android.media.internal.mpegdash;

import java.util.ArrayList;
import java.util.Iterator;

import com.sonymobile.android.media.BandwidthEstimator;

public class DefaultDASHBandwidthEstimator implements BandwidthEstimator {

    private ArrayList<BandWidthMeasureItem> mBandWidthMeasure;

    private Object mBandWidthMeasureLock = new Object();

    private long mOnDataTransferStartedTimeUs = -1;

    private long mAccumulatedTransferredData = -1;

    public DefaultDASHBandwidthEstimator() {
        mBandWidthMeasure = new ArrayList<DefaultDASHBandwidthEstimator.BandWidthMeasureItem>();
    }

    @Override
    public void onDataTransferStarted() {
        mOnDataTransferStartedTimeUs = System.nanoTime() / 1000;
        mAccumulatedTransferredData = 0;
    }

    @Override
    public void onDataTransferEnded() {
        if (mOnDataTransferStartedTimeUs > 0 && mAccumulatedTransferredData > 0) {
            long endTimeUs = System.nanoTime() / 1000;
            addBandWidthMeasure(endTimeUs - mOnDataTransferStartedTimeUs,
                    mAccumulatedTransferredData);
        }
        mOnDataTransferStartedTimeUs = -1;
        mAccumulatedTransferredData = 0;
    }

    @Override
    public void onDataTransferred(long byteCount) {
        mAccumulatedTransferredData += byteCount;
    }

    void addBandWidthMeasure(long durationUs, long bytes) {
        synchronized (mBandWidthMeasureLock) {
            mBandWidthMeasure.add(new BandWidthMeasureItem(durationUs, bytes, System
                    .currentTimeMillis()));

            int itemsToRemove = mBandWidthMeasure.size() - 20;
            if (itemsToRemove > 0) {
                Iterator<BandWidthMeasureItem> iter = mBandWidthMeasure.iterator();
                int j = 0;
                while (j < itemsToRemove && iter.hasNext()) {
                    iter.next();
                    iter.remove();
                    j++;
                }
            }
        }
    }

    private static class BandWidthMeasureItem {

        private BandWidthMeasureItem(long durationUs, long bytes, long wallTimeMs) {
            mWallTimeMs = wallTimeMs;
            mDownloadDurationUs = durationUs;
            mBytes = bytes;
        }

        private long mWallTimeMs = 0;

        private long mDownloadDurationUs = 0;

        private long mBytes = 0;
    }

    @Override
    public long getEstimatedBandwidth() {
        // TODO: Check this algorithm. It has just been ported from our native
        // implementation.
        long totWeighting = 0;
        long totbandwidth = 0;
        synchronized (mBandWidthMeasureLock) {
            int length = mBandWidthMeasure.size();
            if (length == 0) {
                // No data yet!
                return 0;
            }

            for (int i = 0; i < length; i++) {
                BandWidthMeasureItem item = mBandWidthMeasure.get(i);
                long weighting = (item.mWallTimeMs - mBandWidthMeasure.get(0).mWallTimeMs) / 20000;
                if (weighting == 0) {
                    weighting = 1;
                } else if (weighting > 20) {
                    weighting = 20;
                }
                totbandwidth += weighting * (double) item.mBytes * 8E6 / item.mDownloadDurationUs;
                totWeighting += weighting;
            }
        }

        double bandwidthBps = ((double)totbandwidth / (double)totWeighting) * 0.95;

        return (long)bandwidthBps;
    }

}
