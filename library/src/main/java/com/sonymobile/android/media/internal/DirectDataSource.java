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

import java.io.EOFException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import android.util.Log;

public class DirectDataSource extends DataSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DirectDataSource";

    private static final int SIZE_INT = 4;

    private static final int SIZE_LONG = 8;

    private static final int SIZE_SHORT = 2;

    private FileInputStream mFis;

    private FileChannel mFileChannel;

    private RandomAccessFile mRandomAccessFile;

    private long mCurrentPosition;

    private long mStartOffset;

    private long mLength;

    public DirectDataSource(FileDescriptor fd, long offset, long length) {
        if (LOGS_ENABLED) Log.d(TAG, "Create DirectFDDataSource");

        try {
            setup(fd, offset, length);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IO Exception", e);
            throw new IllegalArgumentException("Unsupported FileDescriptor");
        }
    }

    public DirectDataSource(String uri) {
        try {
            if (uri.startsWith("/") || uri.startsWith("file")) {
                File file = new File(uri);
                long length = file.length();
                mRandomAccessFile = new RandomAccessFile(file, "r");

                setup(mRandomAccessFile.getFD(), 0, length);
            }

        } catch (FileNotFoundException e) {
            if (LOGS_ENABLED) Log.e(TAG, "File not found", e);
            throw new IllegalArgumentException("Unsupported uri");
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "IO Exception", e);
            throw new IllegalArgumentException("Unsupported uri");
        }
    }

    private void setup(FileDescriptor fd, long offset, long length) throws IOException {
        mFis = new FileInputStream(fd);
        mFileChannel = mFis.getChannel();
        mStartOffset = offset >= 0 ? offset : 0;
        mLength = length > 0 ? length : Long.MAX_VALUE;
        mFileChannel = mFileChannel.position(mStartOffset);
        mCurrentPosition = mStartOffset;
    }

    @Override
    public void close() throws IOException {
        mFileChannel.close();
        mFis.close();

        if (mRandomAccessFile != null) {
            mRandomAccessFile.close();
        }
    }

    @Override
    public int readAt(long offset, byte[] buffer, int size) throws IOException {
        if (offset + size > mLength) {
            throw new IOException("Offset larger than length");
        }
        if (size > buffer.length) {
            throw new IllegalArgumentException("Size is larger than buffer");
        }

        ByteBuffer bBuffer = ByteBuffer.wrap(buffer, 0, size);
        int read = mFileChannel.read(bBuffer, offset + mStartOffset);
        mCurrentPosition = offset + read;

        if (read < size) {
            throw new IOException("Not enough data read");
        }

        return read;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int bytesToRead = buffer.length;
        return readAt(mCurrentPosition, buffer, bytesToRead);
    }

    @Override
    public int readByte() throws IOException {
        byte[] data = new byte[1];
        readAt(mCurrentPosition, data, 1);
        return data[0];
    }

    @Override
    public short readShort() throws IOException, EOFException {
        byte[] shortBuffer = new byte[SIZE_SHORT];
        readAt(mCurrentPosition, shortBuffer, shortBuffer.length);
        return peekShort(shortBuffer, 0);
    }

    @Override
    public int readInt() throws IOException, EOFException {
        byte[] intBuffer = new byte[SIZE_INT];
        readAt(mCurrentPosition, intBuffer, intBuffer.length);
        return peekInt(intBuffer, 0);
    }

    @Override
    public long readLong() throws IOException, EOFException {
        byte[] longBuffer = new byte[SIZE_LONG];
        readAt(mCurrentPosition, longBuffer, longBuffer.length);
        return peekLong(longBuffer, 0);
    }

    @Override
    public long skipBytes(long count) throws IOException {
        mFileChannel = mFileChannel.position(mCurrentPosition + count);
        long skipped = mFileChannel.position() - mCurrentPosition;
        mCurrentPosition = mFileChannel.position();

        return skipped;
    }

    @Override
    public long length() throws IOException {
        return mFileChannel.size();
    }



    @Override
    public long getCurrentOffset() {
        return mCurrentPosition;
    }

    @Override
    public String getRemoteIP() {
        return null;
    }

    public void reset() {
        mCurrentPosition = mStartOffset;
    }

    public void seek(long offset) throws IOException {
        mFileChannel = mFileChannel.position(offset);
        mCurrentPosition = mFileChannel.position();
    }
}
