/*
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * NOTE: This file contains code from:
 *
 *     Memory.java
 *
 * taken from The Android Open Source Project
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
 *
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sonymobile.android.media.internal;

import java.io.Closeable;
import java.io.EOFException;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;

import android.os.Handler;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;

public abstract class DataSource implements Closeable {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DataSource";

    public static enum DataAvailability{
        AVAILABLE,
        IN_FUTURE,
        NOT_AVAILABLE
    }

    protected Handler mNotify;

    public static DataSource create(String uri, boolean isDash) throws IOException {
        return create(uri, -1, -1, -1, null, null, isDash);
    }

    public static DataSource create(String uri, BandwidthEstimator bandwidthEstimator,
            boolean isDash) throws IOException {
        return create(uri, -1, -1, -1, null, bandwidthEstimator, isDash);
    }

    public static DataSource create(String uri, int bufferSize,
            boolean isDash) throws IOException {
        return create(uri, -1, -1, bufferSize, null, null, isDash);
    }

    public static DataSource create(String uri, long offset, int length,
            boolean isDash) throws IOException {
        return create(uri, offset, length, -1, null, null, isDash);
    }

    public static DataSource create(String uri, long offset, int length,
            BandwidthEstimator bandwidthEstimator, boolean isDash) throws IOException {
        return create(uri, offset, length, -1, null, bandwidthEstimator, isDash);
    }

    /**
     * Create a new DataSource.
     *
     * @param uri The Uri to connect to.
     * @param offset The offset to start reading at or -1 for the beginning.
     *            Only supported for http.
     * @param length The number of bytes to request or -1 for all. Only
     *            supported for http.
     * @param bufferSize The size of the buffer in bytes, or -1 for default /
     *            automatic.
     * @param notify A handler to send source related messages to, e.g IO
     *            problems.
     * @param bandwidthEstimator The BandwidthEstimator to use.
     * @param isDash If a DASH related data source should be created.
     */
    public static DataSource create(String uri, long offset, int length,
            int bufferSize, Handler notify, BandwidthEstimator bandwidthEstimator,
            boolean isDash) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("Null uri is not allowed!");
        }

        try {
            if (uri.startsWith("http")) {
                if (isDash) {
                    return new DASHBufferedDataSource(uri, offset, length, bufferSize, null,
                            bandwidthEstimator);
                } else {
                    return new HttpBufferedDataSource(uri, offset, length, bufferSize, notify,
                            bandwidthEstimator);
                }
            } else if (uri.startsWith("/") || uri.startsWith("file")) {
                return new DirectDataSource(uri);
            }

        } catch (FileNotFoundException e) {
            if (LOGS_ENABLED) Log.e(TAG, "File not found!", e);
            throw new IOException("File not found");
        }

        throw new IllegalArgumentException("Create Failed! Unsupported uri: " + uri);
    }

    /**
     * Create a new DataSource.
     *
     * @param fd The FileDescriptor to use
     * @param offset The offset to start reading at or 0 for the beginning.
     * @param length The number of bytes to read or -1 for all.
     */
    public static DataSource create(FileDescriptor fd, long offset, long length) {
        if (fd == null) {
            throw new IllegalArgumentException("Null FileDescriptor is not allowed!");
        }
        return new DirectDataSource(fd, offset, length);
    }

    public static DataSource create(HttpURLConnection urlConnection, int bufferSize, Handler notify,
                                    BandwidthEstimator bandwidthEstimator) throws IOException {
        if (urlConnection == null) {
            throw new IllegalArgumentException("Null urlConnection is not allowed!");
        }

        return new HttpBufferedDataSource(urlConnection, bufferSize, notify, bandwidthEstimator);
    }

    protected BandwidthEstimator mBandwidthEstimator;

    public BandwidthEstimator getBandwidthEstimator() {
        return mBandwidthEstimator;
    }

    public void setRange(long offset, long length) {
        // Empty implementation, interested subclasses should override.
    }

    public abstract void reset();

    public abstract int readAt(long offset, byte[] buffer, int size) throws IOException;

    public abstract int read(byte[] buffer) throws IOException;

    public abstract int readByte() throws IOException;

    public abstract short readShort() throws IOException, EOFException;

    public abstract int readInt() throws IOException, EOFException;

    public abstract long readLong() throws IOException, EOFException;

    public abstract long skipBytes(long count) throws IOException;

    public abstract long length() throws IOException;

    public abstract long getCurrentOffset();

    public abstract String getRemoteIP();

    public long readUint() throws IOException, EOFException {
        return 0xFFFFFFFFL & readInt();
    }

    public DataAvailability hasDataAvailable(long offset, int size) {
        return DataAvailability.AVAILABLE;
    }

    public void requestReadPosition(long offset) throws IOException {
        // Empty implementation, interested subclasses should override.
    }

    public abstract void seek(long offset) throws IOException;

    protected long peekLong(byte[] src, int offset) {
        int h = ((src[offset++] & 0xff) << 24) | ((src[offset++] & 0xff) << 16)
                | ((src[offset++] & 0xff) << 8) | (src[offset++] & 0xff);
        int l = ((src[offset++] & 0xff) << 24) | ((src[offset++] & 0xff) << 16)
                | ((src[offset++] & 0xff) << 8) | (src[offset] & 0xff);
        return (((long)h) << 32L) | ((long)l) & 0xffffffffL;

    }

    protected int peekInt(byte[] src, int offset) {
        return (((src[offset++] & 0xff) << 24) | ((src[offset++] & 0xff) << 16)
                | ((src[offset++] & 0xff) << 8) | (src[offset] & 0xff));
    }

    protected short peekShort(byte[] src, int offset) {
        return (short)((src[offset] << 8) | (src[offset + 1] & 0xff));
    }

}
