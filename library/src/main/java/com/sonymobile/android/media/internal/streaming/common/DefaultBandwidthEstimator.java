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

package com.sonymobile.android.media.internal.streaming.common;

import java.util.ArrayList;
import java.util.Iterator;

import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.internal.Configuration;

public class DefaultBandwidthEstimator implements BandwidthEstimator {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DefaultBandwidthEstimator";

    private final ArrayList<BandWidthMeasureItem> mBandWidthMeasure;

    private final Object mBandWidthMeasureLock = new Object();

    private long mOnDataTransferStartedTimeUs = -1;

    private long mAccumulatedTransferredData = -1;

    private double mEstimatedBandwidth = 0;

    private double mLatestBandwidthEntry;

    public DefaultBandwidthEstimator() {
        mBandWidthMeasure = new ArrayList<>();
    }

    @Override
    public void onDataTransferStarted() {
        mOnDataTransferStartedTimeUs = System.nanoTime() / 1000;
        mAccumulatedTransferredData = 0;
    }

    @Override
    public void onDataTransferEnded() {
        checkBandwidthDrop();
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

            int itemsToRemove = mBandWidthMeasure.size() - 5;
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

                totbandwidth += weighting * ((double)item.mBytes * 8E6 / item.mDownloadDurationUs);
                totWeighting += weighting;
            }
        }

        mEstimatedBandwidth = ((double)totbandwidth / (double)totWeighting);

        return (long)mEstimatedBandwidth;
    }


    private void checkBandwidthDrop() {

        double currentBps = mAccumulatedTransferredData * 8E6 /
                ((System.nanoTime() / 1000) - mOnDataTransferStartedTimeUs);

        double latestBandwidthEntry = mLatestBandwidthEntry;
        mLatestBandwidthEntry = currentBps;

        if ((mEstimatedBandwidth / 4) > currentBps) {

            if (LOGS_ENABLED) Log.i(TAG,
                    "Bandwidth dropped by over 75%, clearing earlier measurements");
            synchronized (mBandWidthMeasureLock) {
                while (mBandWidthMeasure.size() > 1) {
                    mBandWidthMeasure.remove(0);
                }
            }
        } else if ((mEstimatedBandwidth / 3) >
                ((currentBps + latestBandwidthEntry) / 2)) {
            if (LOGS_ENABLED) Log.i(TAG,
                    "Last two bandwidth measurements dropped by over 67%, clearing "
                            + "earlier measurements");
            synchronized (mBandWidthMeasureLock) {
                while (mBandWidthMeasure.size() > 1) {
                    mBandWidthMeasure.remove(0);
                }
            }
        }
    }
}
