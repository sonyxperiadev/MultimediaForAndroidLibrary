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

import android.util.Log;

public class Buffer {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "Buffer";

    private byte[] mByteBuffer;

    private int mCurrentReadPosition;

    private int mCurrentWritePosition;

    private int mMarkedPosition;

    private int mMarkReadLimit = -1;

    private boolean mClosed = false;

    public Buffer(int size) {
        mByteBuffer = new byte[size];
    }

    public synchronized void close() {
        mByteBuffer = null;
        mClosed = true;
    }

    public synchronized void mark(int readLimit) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't mark, buffer is closed!");
            return;
        }

        if (readLimit > mMarkReadLimit) {
            // Need to grow the buffer.
            // TODO: OutOfMemory possibility
            growBuffer(mByteBuffer.length + (readLimit - mMarkReadLimit));
        }

        mMarkedPosition = mCurrentReadPosition;
        mMarkReadLimit = readLimit;
        reArrangeBuffer(0);
    }

    public void mark(int readLimit, int keepBytes) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't mark, buffer is closed!");
            return;
        }

        if (readLimit > mMarkReadLimit) {
            // Need to grow the buffer.
            // TODO: OutOfMemory possibility
            growBuffer(mByteBuffer.length + (readLimit - mMarkReadLimit));
        }

        if (keepBytes > mCurrentReadPosition) {
            keepBytes = mCurrentReadPosition;
        }
        mMarkedPosition = mCurrentReadPosition;
        mMarkReadLimit = readLimit;
        reArrangeBuffer(keepBytes);
    }

    public synchronized void reset() {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't reset, buffer is closed!");
            return;
        }

        mCurrentReadPosition = mMarkedPosition;
    }

    public synchronized long skip(int byteCount) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't skip, buffer is closed!");
            return -1;
        }

        if (byteCount == 0) {
            return 0;
        }

        int bytesSkipped = 0;
        int bytesAvailble = mCurrentWritePosition - mCurrentReadPosition;

        if (bytesAvailble == 0) {
            return 0;
        }

        if (bytesAvailble > byteCount) {
            mCurrentReadPosition += byteCount;
            bytesSkipped = byteCount;
        } else {
            mCurrentReadPosition += bytesAvailble;
            bytesSkipped = bytesAvailble;
        }

        if (mCurrentReadPosition > mMarkReadLimit) {
            // Passed the read limit
            reArrangeBuffer(0);
        }

        return bytesSkipped;
    }

    public synchronized int available() throws IOException {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't check availble, buffer is closed!");
            return 0;
        }

        return mCurrentWritePosition - mCurrentReadPosition;
    }

    public synchronized int getBufferSize() {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't get buffer size, buffer is closed!");
            return 0;
        }

        return mByteBuffer.length;
    }

    public synchronized int get() {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't get, buffer is closed!");
            return -1;
        }

        byte data = 0;

        int bytesAvailble = mCurrentWritePosition - mCurrentReadPosition;

        if (bytesAvailble >= 1) {
            data = mByteBuffer[mCurrentReadPosition];
            mCurrentReadPosition++;
        }

        return data;
    }

    public synchronized int get(byte[] buffer, int byteOffset, int byteCount) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't get(array), buffer is closed!");
            return -1;
        }

        if (byteCount == 0) {
            return 0;
        }
        int bytesRead = 0;
        int bytesAvailble = mCurrentWritePosition - mCurrentReadPosition;
        if (bytesAvailble <= 0) {
            return 0;
        }

        if (bytesAvailble > byteCount) {
            // We got all data requested!
            System.arraycopy(mByteBuffer, mCurrentReadPosition, buffer, byteOffset, byteCount);
            bytesRead = byteCount;
        } else {
            // get what we got
            System.arraycopy(mByteBuffer, mCurrentReadPosition, buffer, byteOffset, bytesAvailble);
            bytesRead = bytesAvailble;
        }

        mCurrentReadPosition += bytesRead;

        // Buffer is unmarked, make sure to rearrange the buffer our self.
        if (mMarkReadLimit == -1 && mCurrentReadPosition > mByteBuffer.length / 4) {
            reArrangeBuffer(0);
        }

        return bytesRead;
    }

    public synchronized int put(byte[] buffer, int offset, int byteCount) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't put, buffer is closed!");
            return -1;
        }

        int bytesAvailble = mByteBuffer.length - mCurrentWritePosition;

        if (bytesAvailble == 0) {
            return 0;
        }

        int savedData = byteCount;

        if (bytesAvailble > byteCount) {
            // All data will fit.
            putData(buffer, offset, byteCount);

        } else {
            // Not all data will fit in one chunk.
            // Rearrange the buffer
            reArrangeBuffer(0);

            // Check how much space we got after rearrange.
            bytesAvailble = mByteBuffer.length - mCurrentWritePosition;
            if (bytesAvailble > byteCount) {
                // All data will fit.
                putData(buffer, offset, byteCount);
            } else {
                // Put what ever will fit
                putData(buffer, offset, bytesAvailble);
                savedData = bytesAvailble;
            }
        }

        // Buffer is unmarked, make sure to rearrange the buffer our self.
        if (mMarkReadLimit == -1 && mCurrentWritePosition > ((mByteBuffer.length / 4) * 3)) {
            reArrangeBuffer(0);
        }

        return savedData;
    }

    /**
     * Moves current read position back rewindBytes bytes. It is up the caller
     * to make sure we don't end up below our marked position or a subsequent
     * call to mark() might copy incorrect data.
     *
     * @param rewindBytes The number of bytes to move backwards in this buffer
     */
    protected synchronized boolean rewind(long rewindBytes) {
        if (mCurrentReadPosition >= rewindBytes) {
            mCurrentReadPosition -= rewindBytes;
            return true;
        }
        return false;
    }

    /**
     * Moves current read position forward fastForwardBytes bytes. This means
     * that the read position could be higher than write position, but any calls
     * to get() while this is the case will return 0, same as when we have no
     * data buffered.
     *
     * @param fastForwardBytes The number of bytes to move forward in this
     *            buffer
     */
    protected synchronized void fastForward(int fastForwardBytes) {
        mCurrentReadPosition += fastForwardBytes;
    }

    private void putData(byte[] buffer, int offset, int byteCount) {
        System.arraycopy(buffer, offset, mByteBuffer, mCurrentWritePosition, byteCount);
        mCurrentWritePosition += byteCount;
    }

    private void reArrangeBuffer(int keepBytes) {

        if (mCurrentReadPosition <= keepBytes || mCurrentReadPosition > mCurrentWritePosition) {
            return;
        } else if (mCurrentReadPosition > mMarkReadLimit) {
            // Either a non marked buffer or the read limit has passed.
            System.arraycopy(mByteBuffer, mCurrentReadPosition - keepBytes, mByteBuffer, 0,
                    mCurrentWritePosition - mCurrentReadPosition + keepBytes);

            mCurrentWritePosition -= (mCurrentReadPosition - keepBytes);
            mCurrentReadPosition = keepBytes;
            mMarkedPosition = 0;
        } else if (mMarkedPosition > 0) {
            System.arraycopy(mByteBuffer, mMarkedPosition - keepBytes, mByteBuffer, 0,
                    mCurrentWritePosition - mMarkedPosition + keepBytes);
            mCurrentWritePosition -= (mMarkedPosition - keepBytes);
            mCurrentReadPosition -= (mMarkedPosition - keepBytes);
            mMarkedPosition = 0;
        } else {
            // Can't move any data!
        }
    }

    // TODO: OutOfMemory possibility
    private void growBuffer(int newSize) {
        try {
            byte[] localBuffer = new byte[newSize];
            // Copy all we got!
            System.arraycopy(mByteBuffer, 0, localBuffer, 0, mByteBuffer.length);
            mByteBuffer = localBuffer;
        } catch (Throwable e) {
            if (LOGS_ENABLED) Log.e(TAG, "Error when growing buffer");
        }
    }
}
