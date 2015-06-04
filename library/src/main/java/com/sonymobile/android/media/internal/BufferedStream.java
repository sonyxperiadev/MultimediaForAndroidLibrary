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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

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

public final class BufferedStream implements Closeable {

    public static final int MSG_RECONNECT = 1;

    public static final int MSG_SOCKET_TIMEOUT = 2;

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "BufferedStream";

    // TODO: Maybe should we wrap a BufferedInputStream since we do a lot of
    // small reads, however this could mess up bandwidth measure.
    private InputStream mInputStream;

    private final int mBufferSize;

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

    public synchronized int available() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }

        return mDataBuffer.available();
    }

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
            } catch (IOException e) {
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

    public synchronized void reset() throws IOException {
        if (mClosed) {
            throw streamIsClosed();
        }
    }

    public synchronized long skip(long byteCount) {
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
        return !mClosed && mDataBuffer.rewind(rewindBytes);
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
        return !mClosed && mDataBuffer.canDataFit(bytes);
    }

    protected synchronized boolean canRewind(long bytesToRewind) {
        return !mClosed && mDataBuffer.canRewind(bytesToRewind);
    }

    protected synchronized boolean canFastForward(long bytesToFastForward) {
        return !mClosed && mDataBuffer.canFastForward(bytesToFastForward);
    }

    protected synchronized void compact(int bytesToDiscard) {
        if (mClosed) {
            return;
        }

        mDataBuffer.compact(bytesToDiscard);
    }

    public synchronized void reconnect(InputStream in) {
        mInputStream = in;

        mDownloaderThread = new DownloaderThread();
        mDownloaderThread.start();

        if (mDataBuffer != null) {
            mDataBuffer.resetReconnect();
        }
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

    public synchronized boolean isAtEndOfStream() {
        return mDownloaderThread == null || mDownloaderThread.isAtEndOfStream();
    }

    public synchronized boolean isValidForReconnect() {
        return mDataBuffer != null && mDataBuffer.isValidForReconnect();
    }

    private class DownloaderThread extends Thread {

        private boolean mEos = false;

        public boolean isAtEndOfStream() {
            return mEos;
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
            while (!isClosed() && !mEos && mInputStream != null) {
                try {
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
                            int put = mDataBuffer.put(data, totalSaved, read - totalSaved);
                            totalSaved += put;
                            if (totalSaved < read) {
                                if (put == 0 && freeSpace() < (mBufferSize / 200) &&
                                        available() < mBufferSize / 10) {
                                    compact((mBufferSize / 10));
                                }
                                try {
                                    // Let the system take a breath.
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    } while (!isClosed() && totalSaved < read);
                } catch (SocketTimeoutException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "SocketTimeoutException during read!", e);
                    if (mInputStream != null) {
                        try {
                            mInputStream.close();
                        } catch (IOException e1) {
                        } finally {
                            mInputStream = null;
                        }
                    }
                    if (mCallback != null) {
                        mCallback.sendEmptyMessage(MSG_SOCKET_TIMEOUT);
                    } else {
                        mEos = true;
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
                } catch (RuntimeException e) {
                    if (LOGS_ENABLED) Log.e(TAG, "Exception during read!", e);
                    if (mInputStream != null) {
                        try {
                            mInputStream.close();
                        } catch (IOException e1) {
                        } finally {
                            mInputStream = null;
                        }
                    }
                    mEos = true;
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

            if (LOGS_ENABLED)
                Log.v(TAG, "DownloaderThread will now exit, stream should be closed by now.");
        }

        private boolean isClosed() {
            synchronized (BufferedStream.this) {
                return mClosed;
            }
        }
    }
}
