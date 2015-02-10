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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.os.Handler;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;
import com.sonymobile.android.media.internal.BufferedStream.ThresholdListener;

public class HttpBufferedDataSource extends BufferedDataSource implements ThresholdListener {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "HttpBufferedDataSource";

    private BufferingUpdateThread mBufferingThread;

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

    @Override
    public synchronized int readAt(long offset, byte[] buffer, int size) throws IOException {
        if (LOGS_ENABLED) Log.d(TAG, "readAt " + offset + ", " + size + " bytes"
                + " mCurrentOffset: " + mCurrentOffset);

        if (mConnectError != STATUS_OK) {
            return mConnectError;
        }

        checkConnectionAndStream();

        if (mCurrentOffset > offset && mMarkedOffset <= offset && mMarkedOffset != -1) {
            // Backing within the marked data.
            if (LOGS_ENABLED) Log.d(TAG, "Backing to " + offset + " within the marked range");

            if (!mBis.rewind(mCurrentOffset - offset)) {
                if (LOGS_ENABLED) Log.e(TAG, "could not rewind buffer");
                return -1;
            }
            mCurrentOffset = offset;
        } else if (offset > mCurrentOffset && offset < (mMarkedOffset + (mReadLimit * 2))) {
            // offset is inside current buffer, so we can wait until we reach
            // the data
            if (LOGS_ENABLED) Log.d(TAG, "requested offset inside current buffer, wait for data");
        } else if (mCurrentOffset != offset) {
            if (mLength != -1 && (offset < mOffset || mOffset + mLength < offset)) {
                if (LOGS_ENABLED) Log.e(TAG, "offset outside current range");
                return -1;
            }
            // Reconnect to the new offset
            if (LOGS_ENABLED)
                Log.d(TAG, "Read at reconnect now at " + offset + " using range "
                        + "( " + mCurrentOffset + " + " + mReadLimit +
                        " * 2 ), markedOffset: "
                        + mMarkedOffset);
            mMarkedOffset = -1;
            mCurrentOffset = offset;
            mOffset = offset;

            doCloseSync();
            openConnectionsAndStreams();
        }

        if (mMarkedOffset == -1 || (mCurrentOffset + size) > (mMarkedOffset + mReadLimit)) {
            // We have passed the mark read limit and need to reset!!
            if (LOGS_ENABLED)
                Log.d(TAG, "We have passed the read limit: " + mReadLimit +
                        " mCurrentOffset: " + mCurrentOffset);

            int keepBytes = mReadLimit / 4;
            mBis.mark(mReadLimit, keepBytes);
            if (mMarkedOffset == -1) {
                mMarkedOffset = mOffset;
            } else {
                if (keepBytes > mCurrentOffset) {
                    mMarkedOffset = mOffset;
                } else if (keepBytes > mCurrentOffset - mMarkedOffset) {
                    mMarkedOffset = mCurrentOffset - (mCurrentOffset - mMarkedOffset) + mOffset;
                } else {
                    mMarkedOffset = mCurrentOffset - keepBytes + mOffset;
                }
            }
        }

        int totalRead = 0;
        int offsetDiff = (int)(offset - mCurrentOffset);
        if (offsetDiff > 0) {
            mBis.fastforward(offsetDiff);
        }
        if (LOGS_ENABLED)
            Log.d(TAG, "mCurrentOffset: " + mCurrentOffset + ", offsetDiff: " + offsetDiff);
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
                if (mMarkedOffset != mCurrentOffset
                        && ((mCurrentOffset + size - totalRead) > (mMarkedOffset + mReadLimit))) {
                    if (LOGS_ENABLED)
                        Log.d(TAG, "We passed the read limit again " + mReadLimit
                                + " mCurrentOffset: " + mCurrentOffset);
                    mBis.mark(mReadLimit);
                    mMarkedOffset = mCurrentOffset;
                }
                try {
                    Thread.sleep(1); // Let the system take a breath....
                } catch (InterruptedException e) {
                }
            }
        } while (totalRead < size);

        return totalRead;
    }

    protected synchronized void openConnectionsAndStreams()
            throws FileNotFoundException, IOException {
        super.openConnectionsAndStreams();

        if (mBis != null) {
            mBis.setThresholdListener(this);
            mBis.mark(mReadLimit);
            mMarkedOffset = -1;

            // start thread for buffering callbacks
            if (mBufferingThread != null && !mBufferingThread.isAlive()) {
                mBufferingThread = null;
            }
            if (mBufferingThread == null) {
                mBufferingThread = new BufferingUpdateThread();
                mBufferingThread.start();
            }
        }
    }

    @Override
    protected void doReconnect() throws IOException {
        super.doReconnect();

        // start thread for buffering callbacks.
        // It's safe to do so here since super.doReconnect will throw an IO if
        // we fail to reconnect.
        if (mBufferingThread != null && !mBufferingThread.isAlive()) {
            mBufferingThread = null;
        }
        if (mBufferingThread == null) {
            mBufferingThread = new BufferingUpdateThread();
            mBufferingThread.start();
        }
    }

    protected void doCloseAsync() {
        super.doCloseAsync();

        if (mBufferingThread != null && mBufferingThread.isAlive()) {
            mBufferingThread.interrupt();
        }
    }

    @Override
    public synchronized boolean hasDataAvailable(long offset, int size) {
        if (mCurrentOffset > offset && mMarkedOffset <= offset && mMarkedOffset != -1) {
            // can back to offset in current buffer
            return true;
        }
        if (offset >= mCurrentOffset && offset < (mMarkedOffset + (mReadLimit * 2))) {
            // will reach offset in current buffer
            return true;
        }
        return false;
    }

    @Override
    public synchronized void requestReadPosition(long offset) throws IOException {
        if (offset < mMarkedOffset
                || (offset > mCurrentOffset && offset > (mMarkedOffset + (mReadLimit * 2)))) {
            if (LOGS_ENABLED)
                Log.d(TAG, "Request reconnect now at " + offset + " using range "
                        + "( " + mCurrentOffset + " + " + mReadLimit + " * 2 ), mMarkedOffset: "
                        + mMarkedOffset);

            mMarkedOffset = -1;
            mCurrentOffset = offset;
            mOffset = offset;

            doCloseSync();
            openConnectionsAndStreams();
        }
    }

    @Override
    public void onLowThreshold() {
        // Running low start thread for buffering callbacks
        if (mBufferingThread != null && !mBufferingThread.isAlive()) {
            mBufferingThread = null;
        }
        if (mBufferingThread == null) {
            mBufferingThread = new BufferingUpdateThread();
            mBufferingThread.start();
        }
    }

    @Override
    public void onHighThreshold() {
        // Not used.
    }

    private class BufferingUpdateThread extends Thread {

        public void run() {
            if (mNotify != null) {
                int percentage = 0;
                while (mBis != null && percentage < 100 && percentage >= 0
                        && !isInterrupted()) {
                    try {
                        int numBytesAvailable = mBis.available();
                        percentage = (int)(100 * (double)(mCurrentOffset + numBytesAvailable)
                                / mContentLength);
                        if (percentage > 100) {
                            percentage = 100;
                        } else if (percentage < 0) {
                            percentage = 0;
                        }
                        mNotify.obtainMessage(SOURCE_BUFFERING_UPDATE, percentage, 0)
                                .sendToTarget();
                        sleep(1000);
                    } catch (IOException e) {
                        break;
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }
}
