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

import java.io.FileNotFoundException;
import java.io.IOException;

import android.os.Handler;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;

public class DASHBufferedDataSource extends BufferedDataSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DASHBufferedDataSource";

    /*
     * (non-Javadoc) Protected to force use of DataSource.create(....)
     */
    protected DASHBufferedDataSource(String uri, long offset, int length, int bufferSize,
            Handler notify, BandwidthEstimator bandwidthEstimator) throws FileNotFoundException,
            IOException {

        super(uri, offset, length, bufferSize, notify, bandwidthEstimator);

        if (LOGS_ENABLED)
            Log.v(TAG, "Created DASHBufferedDataSource");
    }

    @Override
    public int readAt(long offset, byte[] buffer, int size) throws IOException {
        if (LOGS_ENABLED) Log.d(TAG, "readAt " + offset + ", " + size + " bytes"
                + " mCurrentOffset: " + mCurrentOffset);

        checkConnectionAndStream();

        if (offset > mCurrentOffset) {
            skipBytes(offset - mCurrentOffset);
        } else if (mCurrentOffset != offset) {
            if (mLength != -1 && (offset < mOffset || mOffset + mLength < offset)) {
                if (LOGS_ENABLED) Log.e(TAG, "offset outside current range");
                return -1;
            }
            // Reconnect to the new offset
            if (LOGS_ENABLED) Log.d(TAG, "Read at reconnect now at " + offset);
            mCurrentOffset = offset;
            mOffset = offset;

            doCloseSync();
            openConnectionsAndStreams();
        }

        int totalRead = 0;
        do {
            int read = mBis.read(buffer, totalRead, size - totalRead);

            if (read == 0) {
                mBis.compact(-1);
            }

            if (read > -1) {
                mCurrentOffset += read;
                totalRead += read;
            } else if (read == -1 && mRangeExtended && mLength != -1 &&
                    mCurrentOffset < mOffset + mLength) {
                // EOS, but range was extended - so reconnect.
                if (LOGS_ENABLED) Log.d(TAG, "reconnect, EOS at " + mCurrentOffset);
                mOffset = mCurrentOffset;

                doCloseSync();
                openConnectionsAndStreams();
            } else {
                break;
            }

            if (totalRead < size) {
                try {
                    Thread.sleep(1); // Let the system take a breath....
                } catch (InterruptedException e) {
                }
            }
        } while (totalRead < size);

        if (totalRead < size) {
            throw new IOException("Not enough data read");
        }

        return totalRead;
    }
}
