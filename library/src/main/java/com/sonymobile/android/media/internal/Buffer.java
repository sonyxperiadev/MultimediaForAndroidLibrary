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

import android.util.Log;

public class Buffer {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "Buffer";

    private byte[] mByteBuffer;

    private int mCurrentReadPosition;

    private int mCurrentWritePosition;

    private int mReadPositionDuringReconnect;

    private boolean mClosed = false;

    public Buffer(int size) {
        mByteBuffer = new byte[size];
    }

    public synchronized void close() {
        mByteBuffer = null;
        mClosed = true;
    }

    public synchronized long skip(int byteCount) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't skip, buffer is closed!");
            return -1;
        }

        if (byteCount == 0) {
            return 0;
        }

        int bytesSkipped;
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

        return bytesSkipped;
    }

    public synchronized int available() {
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
        int bytesRead;
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

        return bytesRead;
    }

    public synchronized int put(byte[] buffer, int offset, int byteCount) {
        if (mClosed) {
            if (LOGS_ENABLED) Log.e(TAG, "Can't put, buffer is closed!");
            return -1;
        }

        int bytesAvailable = mByteBuffer.length - mCurrentWritePosition;

        if (bytesAvailable == 0) {
            return 0;
        }

        int savedData = byteCount;

        if (bytesAvailable > byteCount) {
            // All data will fit.
            putData(buffer, offset, byteCount);

        } else {
            // Not all data will fit in one chunk.
            // Put what ever will fit
            putData(buffer, offset, bytesAvailable);
            savedData = bytesAvailable;
        }

        return savedData;
    }

    public synchronized boolean isValidForReconnect() {
        boolean isValid = mReadPositionDuringReconnect == 0
                || mReadPositionDuringReconnect == mCurrentReadPosition
                || mCurrentWritePosition - mCurrentReadPosition > 0;
        mReadPositionDuringReconnect = mCurrentReadPosition;
        return isValid;
    }

    public synchronized void resetReconnect() {
        mReadPositionDuringReconnect = 0;
    }

    protected synchronized int freeSpace() {
        return mByteBuffer.length - mCurrentWritePosition;
    }

    protected synchronized boolean canRewind(long bytesToRewind) {
        return mCurrentReadPosition >= bytesToRewind;
    }

    protected synchronized boolean canFastForward(long bytesToFastForward) {
        return mCurrentReadPosition + bytesToFastForward < mCurrentWritePosition;
    }

    protected synchronized boolean canDataFit(long bytes) {
        return mByteBuffer.length > mCurrentWritePosition + bytes;
    }

    protected synchronized void compact(int bytesToDiscard) {
        int discardPosition;
        if (bytesToDiscard == -1) {
            discardPosition = mCurrentReadPosition;
        } else {
            discardPosition = bytesToDiscard <= mCurrentReadPosition ? bytesToDiscard :
                    mCurrentReadPosition;
        }

        System.arraycopy(mByteBuffer, discardPosition, mByteBuffer, 0,
                mCurrentWritePosition - discardPosition);

        mCurrentReadPosition -= discardPosition;
        mCurrentWritePosition -= discardPosition;
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
    protected synchronized void fastForward(long fastForwardBytes) {
        mCurrentReadPosition += fastForwardBytes;
    }

    private void putData(byte[] buffer, int offset, int byteCount) {
        System.arraycopy(buffer, offset, mByteBuffer, mCurrentWritePosition, byteCount);
        mCurrentWritePosition += byteCount;
    }
}
