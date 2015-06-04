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

import java.util.ArrayDeque;

import android.media.MediaFormat;

import com.sonymobile.android.media.internal.AccessUnit;

public class PacketSource {

    private final ArrayDeque<AccessUnit> mBuffer = new ArrayDeque<>();

    private long mNextTimeUs = -1;

    private boolean mClosed = false;

    private long mBufferDataSize;

    public synchronized void queueAccessUnit(AccessUnit accessUnit) {

        if (mClosed) {
            return;
        }

        mBuffer.add(accessUnit);
        if (accessUnit.data != null) {
            mBufferDataSize += accessUnit.data.length;
        }
    }

    public synchronized AccessUnit dequeueAccessUnit() {
        AccessUnit accessUnit = mBuffer.remove();
        if (accessUnit.data != null) {
            mBufferDataSize -= accessUnit.data.length;
        }
        return accessUnit;
    }

    public synchronized MediaFormat getFormat() {
        if (mBuffer.size() > 0) {
            return mBuffer.getFirst().format;
        }

        return null;
    }

    public synchronized boolean hasBufferAvailable() {
        return mBuffer.size() > 0;
    }

    public synchronized long getBufferDuration() {
        if (mBuffer.size() == 0) {
            return 0;
        }

        if (mBuffer.size() == 1) {
            return mBuffer.getLast().durationUs;
        }

        return mBuffer.getLast().timeUs - mBuffer.getFirst().timeUs;
    }

    public synchronized long getLastEnqueuedTimeUs() {
        if (mBuffer != null) {
            if (mBuffer.peekLast() != null) {
                return mBuffer.peekLast().timeUs;
            }
        }
        return -1;
    }

    public synchronized void clear() {
        mBuffer.clear();
        mBufferDataSize = 0;
    }

    public long getNextTimeUs() {
        return mNextTimeUs;
    }

    public void setNextTimeUs(long nextTimeUs) {
        mNextTimeUs = nextTimeUs;
    }

    public synchronized void setClosed(boolean closed) {
        mClosed = closed;
    }

    public synchronized long getBufferSize() {
        return mBufferDataSize;
    }
}
