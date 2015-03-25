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

import java.io.IOException;
import java.io.InputStream;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;

/*
 * TODO:
 *
 * 1: Make the buffer smart and resize the buffer depending on how much data is produced / consumed and only see the incoming size as a maximum.
 * 2: Implement high / low threshold callbacks to pause / resume data production.
 */

public class BufferedStream extends InputStream {

    public static final int MSG_RECONNECT = 1;

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "BufferedStream";

    private static final int LOW_THRESHOLD_PERCENT = 15;

    private static final int HIGH_THRESHOLD_PERCENT = 85;

    // TODO: Maybe should we wrap a BufferedInputStream since we do a lot of
    // small reads, however this could mess up bandwidth measure.
    private InputStream mInputStream;

    private int mBufferSize;

    private Buffer mDataBuffer;

    private DownloaderThread mDownloaderThread;

    private BandwidthEstimator mBandwidthEstimator;

    private boolean mClosed = false;

    private long mTotalBytesLoaded = 0;

    private Handler mCallback;

    public BufferedStream(InputStream in, int bufferSize) {
        this(in, bufferSize, null, null);
    }

    public BufferedStream(InputStream in, int bufferSize, BandwidthEstimator estimator,
            Handler handler) {
        super();
        mInputStream = in;
        mBufferSize = bufferSize;

        mBandwidthEstimator = estimator;

        mDataBuffer = new Buffer(mBufferSize);

        mDownloaderThread = new DownloaderThread();
        mDownloaderThread.start();

        mCallback = handler;
    }

    @Override
    public synchronized int available() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        return mDataBuffer.available();
    }

    @Override
    public synchronized void close() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        mClosed = true; // Mark as closed so we will exit ASAP

        if (mDownloaderThread != null) {
            try {
                // Wait for the thread to exit.
                mDownloaderThread.join(500);
            } catch (InterruptedException e1) {
            }
        }

        if (mInputStream != null) {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (Exception e) {
            }
        }

        if (mDataBuffer != null) {
            mDataBuffer.close();
        }

        mDownloaderThread = null;
        mDataBuffer = null;
        mCallback = null;
        mBandwidthEstimator = null;
    }

    @Override
    public synchronized void mark(int readlimit) {

    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized int read() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        int data = mDataBuffer.get();
        if (data == 0 && (mDownloaderThread == null || mDownloaderThread.isAtEndOfStream())) {
            data = -1;
        }

        return data;
    }

    @Override
    public synchronized int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        int read = mDataBuffer.get(buffer, byteOffset, byteCount);
        if (read == 0 && (mDownloaderThread == null || mDownloaderThread.isAtEndOfStream())) {
            return -1;
        }

        return read;
    }

    @Override
    public synchronized int read(byte[] buffer) throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        int read = mDataBuffer.get(buffer, 0, buffer.length);
        if (read == 0 && (mDownloaderThread == null || mDownloaderThread.isAtEndOfStream())) {
            return -1;
        }

        return read;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }
    }

    @Override
    public synchronized long skip(long byteCount) throws IOException {
        if (mClosed) {
            return -1;
        }

        long skipped = mDataBuffer.skip((int)byteCount);
        if (skipped == 0 && (mDownloaderThread == null || mDownloaderThread.isAtEndOfStream())) {
            skipped = -1;
        }

        return skipped;
    }

    protected synchronized boolean rewind(long rewindBytes) {
        if (mClosed) {
            return false;
        }

        return mDataBuffer.rewind(rewindBytes);
    }

    protected synchronized void fastForward(long fastForwardBytes) {
        if (mClosed) {
            return;
        }

        mDataBuffer.fastForward(fastForwardBytes);
    }

    protected synchronized int freeSpace() {
        if (mClosed) {
            return -1;
        }

        return mDataBuffer.freeSpace();
    }

    protected synchronized boolean canDataFit(long bytes) {
        if (mClosed) {
            return false;
        }

        return mDataBuffer.canDataFit(bytes);
    }

    protected synchronized boolean canRewind(long bytesToRewind) {
        if (mClosed) {
            return false;
        }

        return mDataBuffer.canRewind(bytesToRewind);
    }

    protected synchronized boolean canFastForward(long bytesToFastForward) {
        if (mClosed) {
            return false;
        }

        return mDataBuffer.canFastForward(bytesToFastForward);
    }

    protected synchronized void compact(int bytesToDiscard) {
        if (mClosed) {
            return;
        }

        mDataBuffer.compact(bytesToDiscard);
    }

    public void reconnect(InputStream in) {
        mInputStream = in;

        mDownloaderThread = new DownloaderThread();
        mDownloaderThread.start();
    }

    public long getTotalBytesLoaded() {
        return mTotalBytesLoaded;
    }

    private IOException streamIsClosed() {
        return new IOException("Stream is closed");
    }

    public synchronized boolean isStreamClosed() {
        return mClosed;
    }

    private class DownloaderThread extends Thread {

        private boolean mEos = false;

        private boolean mPaused = false;

        private boolean mInPauseState = false;

        private Object mPausedLock = new Object();

        private boolean mHasPassedLowThreshold = false;

        public boolean isAtEndOfStream() {
            return mEos;
        }

        public void pauseProduction() {
            // We are closed or at end of stream, can't pause.
            if (mClosed || mEos) {
                return;
            }

            mPaused = true;
            while (!mInPauseState) {
                synchronized (mPausedLock) {
                    try {
                        mPausedLock.wait(100);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public void resumeProduction() {
            mPaused = false;
        }

        public long skipData(long byteCount) throws IOException {
            if (mClosed || mEos) {
                return -1;
            }

            return mInputStream.skip(byteCount);
        }

        @Override
        public void run() {
            // TODO: What is the best read size ??
            byte[] data = new byte[1024];

            mTotalBytesLoaded = 0;

            if (mBandwidthEstimator != null) {
                mBandwidthEstimator.onDataTransferStarted();
            }

            if (LOGS_ENABLED) Log.v(TAG, "DownloaderThread will now start.");
            while (!mClosed && !mEos && mInputStream != null) {
                try {
                    if (!mPaused) {
                        mInPauseState = false;
                        int read = mInputStream.read(data);

                        if (read == -1 || mClosed) {
                            mEos = true;
                            break;
                        }

                        // TODO: Do not use outer class member. Should be passed
                        // to the DownloadThread instead.
                        if (mBandwidthEstimator != null) {
                            // TODO call async ?
                            mBandwidthEstimator.onDataTransferred(read);
                        }

                        mTotalBytesLoaded += read;

                        int totalSaved = 0;
                        do {
                            if (mDataBuffer != null) {
                                totalSaved += mDataBuffer.put(data, totalSaved, read - totalSaved);
                                if (totalSaved < read) {
                                    try {
                                        // Let the system take a breath.
                                        Thread.sleep(1);
                                    } catch (Exception e) {
                                    }
                                }
                            }
                        } while (!mClosed && totalSaved < read);
                    } else {
                        synchronized (mPausedLock) {
                            try {
                                mInPauseState = true;
                                mPausedLock.wait(100);
                            } catch (Exception e) {
                            }
                        }
                    }
                } catch (IOException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "IOException during read!", e);
                    if (mInputStream != null) {
                        try {
                            mInputStream.close();
                        } catch (IOException e1) {
                        } finally {
                            mInputStream = null;
                        }
                    }
                    if (mCallback != null) {
                        mCallback.sendEmptyMessageAtTime(MSG_RECONNECT,
                                SystemClock.uptimeMillis() + 1000);
                    } else {
                        mEos = true;
                    }
                }
            }

            if (mBandwidthEstimator != null) {
                mBandwidthEstimator.onDataTransferEnded();
            }

            if (!mClosed && mInputStream != null) {
                try {
                    mInputStream.close();
                    mInputStream = null;
                } catch (Exception e) {
                }
            }

            data = null;

            if (LOGS_ENABLED)
                Log.v(TAG, "DownloaderThread will now exit, stream should be closed by now.");
        }
    }
}
