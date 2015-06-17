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

import static com.sonymobile.android.media.internal.MediaSource.SOURCE_BUFFERING_UPDATE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;

import android.os.Handler;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;

public class HttpBufferedDataSource extends BufferedDataSource {

    /**
     * Buffer handling Logic:
     *
     * This class act as the DataSource for HTTP progressive download.
     * The logic for buffer handling / readAt function work like this:
     *
     * If the free space in the underlying BufferedStream is less than 0.5% of the buffer size
     * the underlying buffer is compacted and up to 5% of the buffer data is removed. This data
     * should already have been consumed.
     *
     * If the requested read position is the same as current read position the requested readAt
     * operation is performed directly.
     *
     * If the requested read position is before current read position the stream is backed to
     * the requested position only if data for the requested position exists.
     *
     * If the requested read position is after current read position the stream is forwarded to
     * the requested position if the data has already been downloaded.
     * If data has not been downloaded, there is enough room in the underlying buffer to
     * download the amount of data and the data to wait for is less than 1/3 of the buffer size
     * the readAt operation is blocked until the data has been downloaded. If a blocking read is
     * not desired it is up to the caller to make sure this doesn't happen by checking data
     * availability.
     *
     * In all other cases where the data is not available the HTTP connection is reestablished
     * at the requested read position.
     *
     */

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "HttpBufferedDataSource";

    /*
     * (non-Javadoc) Protected to force use of DataSource.create(....)
     */
    protected HttpBufferedDataSource(String uri, long offset, int length, int bufferSize,
            Handler notify, BandwidthEstimator bandwidthEstimator) throws FileNotFoundException,
            IOException {

        super(uri, offset, length, bufferSize, notify, bandwidthEstimator);

        if (LOGS_ENABLED)
            Log.v(TAG, "Created HttpBufferedDataSource");
    }

    public HttpBufferedDataSource(HttpURLConnection urlConnection, int bufferSize, Handler notify,
                                  BandwidthEstimator bandwidthEstimator) throws IOException {
        super(urlConnection, bufferSize, notify, bandwidthEstimator);
    }

    @Override
    public int readAt(long offset, byte[] buffer, int size) throws IOException {
        if (LOGS_ENABLED) Log.d(TAG, "readAt " + offset + ", " + size + " bytes"
                + " mCurrentOffset: " + mCurrentOffset);

        checkConnectionAndStream();

        if (mBis.freeSpace() < (mBufferSize / 200)) {
            // Less than 0.5% buffer left to fill
            if (mBis.available() < mBufferSize / 10) {
                // Less than 10% available to read, compact and remove 10%.
                mBis.compact((mBufferSize / 10));
            }
        }

        if (mCurrentOffset > offset && mBis.canRewind(mCurrentOffset - offset)) {
            mBis.rewind(mCurrentOffset - offset);
        } else if (mCurrentOffset < offset) {
            if (mBis.canFastForward(offset - mCurrentOffset)) {
                mBis.fastForward(offset - mCurrentOffset);
            } else {
                if (!mBis.canDataFit((offset - mCurrentOffset) + size)) {
                    mBis.compact((mBufferSize / 10));
                }

                if (mBis.canDataFit((offset - mCurrentOffset) + size) &&
                        offset - mCurrentOffset < mBufferSize / 3) {
                    // Data will fit in the buffer and we need to wait for a buffer smaller than
                    // 1/3 of the length.
                    Object waiterLock = new Object();
                    while (!mBis.canFastForward(offset - mCurrentOffset)) {
                        synchronized (waiterLock) {
                            try {
                                waiterLock.wait(50);
                                if (mBis.isStreamClosed()) {
                                    return -1;
                                }
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                    mBis.fastForward(offset - mCurrentOffset);
                } else {
                    mCurrentOffset = offset;
                    mOffset = offset;
                    doCloseSync();
                    openConnectionsAndStreams();
                }
            }
        } else if (mCurrentOffset != offset) {
            mCurrentOffset = offset;
            mOffset = offset;

            doCloseSync();
            openConnectionsAndStreams();
        }

        int totalRead = 0;
        mCurrentOffset = offset;
        do {
            int read = mBis.read(buffer, totalRead, size - totalRead);
            if (read > -1) {
                mCurrentOffset += read;
                totalRead += read;
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

    protected void openConnectionsAndStreams()
            throws FileNotFoundException, IOException {
        super.openConnectionsAndStreams();

        sendMessage(SOURCE_BUFFERING_UPDATE);
    }

    @Override
    public DataAvailability hasDataAvailable(long offset, int size) {
        if (mBis == null) {
            return DataAvailability.NOT_AVAILABLE;
        }
        if (mBis.isStreamClosed()) {
            return DataAvailability.IN_FUTURE;
        }
        DataAvailability toReturn = DataAvailability.IN_FUTURE;
        if (mCurrentOffset == offset) {
            toReturn = DataAvailability.AVAILABLE;
        } else if (mCurrentOffset > offset) {
            if (mBis.canRewind(mCurrentOffset - offset)) {
                toReturn = DataAvailability.AVAILABLE;
            } else {
                toReturn = DataAvailability.NOT_AVAILABLE;
            }
        } else {
            if (mBis.canFastForward(offset - mCurrentOffset)) {
                toReturn = DataAvailability.AVAILABLE;
            } else if ((offset - mCurrentOffset) > mBufferSize / 3) {
                toReturn = DataAvailability.NOT_AVAILABLE;
            }
        }
        return toReturn;
    }

    @Override
    public void requestReadPosition(long offset) throws IOException {
        if (LOGS_ENABLED)
            Log.d(TAG, "Request reconnect now at " + offset);

        mCurrentOffset = offset;
        mOffset = offset;

        doCloseSync();
        openConnectionsAndStreams();
    }

    public int getBuffering() {
        int percentage = 0;
        try {
            if (mBis == null) {
                return 0;
            }
            int numBytesAvailable = mBis.available();
            percentage = (int)(100 * (double)(mCurrentOffset + numBytesAvailable)
                    / mContentLength);
            if (percentage > 100) {
                percentage = 100;
            } else if (percentage < 0) {
                percentage = 0;
            }
        } catch (IOException e) {
        }
        return percentage;
    }

    public int getBufferedSize() {
        try {
            return mBis.available();
        } catch (IOException e) {
        }
        return 0;
    }

    private void sendMessage(int what) {
        if (mNotify != null) {
            mNotify.sendEmptyMessage(what);
        }
    }

    public boolean isAtEndOfStream() {
        return mBis == null || mBis.isAtEndOfStream();
    }
}
