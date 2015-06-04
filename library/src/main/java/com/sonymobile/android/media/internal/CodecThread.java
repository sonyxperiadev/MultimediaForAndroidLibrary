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

import java.util.ArrayDeque;

import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CryptoException;

import com.sonymobile.android.media.MediaError;

public abstract class CodecThread implements Codec {

    protected static final int MSG_SET_SOURCE = 1;

    protected static final int MSG_SET_SURFACE = 6;

    protected static final int MSG_SETUP = 2;

    protected static final int MSG_DEQUEUE_INPUT_BUFFER = 3;

    protected static final int MSG_DEQUEUE_OUTPUT_BUFFER = 4;

    protected static final int MSG_RENDER = 5;

    protected static final int MSG_RENDER_ONE_FRAME = 6;

    protected static final int MSG_START = 7;

    protected static final int MSG_PAUSE = 8;

    protected static final int MSG_FLUSH = 9;

    protected static final int MSG_STOP = 10;

    protected static final int MSG_SET_VIDEO_SCALING_MODE = 11;

    protected static final int MSG_SET_SPEED = 12;

    private final ArrayDeque<Frame> mDecodedFrames;

    private final Object mDecodedFramesLock = new Object();

    private final ArrayDeque<Frame> mFramePool;

    private final Object mFramePoolLock = new Object();

    protected CodecThread() {
        mDecodedFrames = new ArrayDeque<>();
        mFramePool = new ArrayDeque<>(5);
    }

    protected void addDecodedFrame(Frame frame) {
        synchronized (mDecodedFramesLock) {
            mDecodedFrames.add(frame);
        }
    }

    protected Frame peekDecodedFrame() {
        synchronized (mDecodedFramesLock) {
            return mDecodedFrames.peek();
        }
    }

    protected Frame removeFirstDecodedFrame() {
        synchronized (mDecodedFramesLock) {
            if (mDecodedFrames.size() == 0) {
                return null;
            }

            return mDecodedFrames.removeFirst();
        }
    }

    protected void clearDecodedFrames() {
        synchronized (mDecodedFramesLock) {
            synchronized (mFramePoolLock) {
                while (mDecodedFrames.size() > 0) {
                    Frame frame = mDecodedFrames.removeFirst();
                    mFramePool.add(frame);
                }
            }
        }
    }

    protected int decodedFrameCount() {
        synchronized (mDecodedFramesLock) {
            return mDecodedFrames.size();
        }
    }

    protected void addFrameToPool(Frame frame) {
        if (frame != null) {
            synchronized (mFramePoolLock) {
                mFramePool.add(frame);
            }
        }
    }

    protected Frame removeFrameFromPool() {
        synchronized (mFramePoolLock) {
            Frame temp = null;
            if (mFramePool.size() > 0) {
                temp = mFramePool.removeFirst();
            }
            if (temp == null) {
                // Create an empty frame.
                temp = new Frame(new BufferInfo(), -1);
            }
            return temp;
        }
    }

    protected int framePoolCount() {
        synchronized (mFramePoolLock) {
            return mFramePool.size();
        }
    }

    protected int getMediaDrmErrorCode(int cryptoExceptionErrorCode) {
        int error = MediaError.DRM_UNKNOWN;
        switch (cryptoExceptionErrorCode) {
            case CryptoException.ERROR_KEY_EXPIRED:
                error = MediaError.DRM_LICENSE_EXPIRED;
                break;
            case CryptoException.ERROR_NO_KEY:
                error = MediaError.DRM_NO_LICENSE;
                break;
            case CryptoException.ERROR_INSUFFICIENT_OUTPUT_PROTECTION:
                error = MediaError.DRM_INSUFFICIENT_OUTPUT_PROTECTION;
                break;
            default:
                break;
        }

        return error;
    }

    protected static class Frame {
        byte[] byteBuffer;

        final BufferInfo info;

        int bufferIndex;

        Frame(BufferInfo info, int bufferIndex) {
            this.info = info;
            this.bufferIndex = bufferIndex;
            byteBuffer = null;
        }
    }

}
