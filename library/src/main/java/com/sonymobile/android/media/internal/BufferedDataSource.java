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

import static com.sonymobile.android.media.internal.BufferedStream.MSG_SOCKET_TIMEOUT;
import static com.sonymobile.android.media.internal.MediaSource.SOURCE_ERROR;
import static com.sonymobile.android.media.internal.BufferedStream.MSG_RECONNECT;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.sonymobile.android.media.BandwidthEstimator;

public abstract class BufferedDataSource extends DataSource {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "BufferedDataSource";

    protected static final int INT = 4;

    protected static final int LONG = 8;

    protected static final int SHORT = 2;

    protected HttpURLConnection mHttpURLConnection;

    protected BufferedStream mBis;

    protected long mOffset = 0;

    protected long mCurrentOffset = 0;

    protected long mContentLength = 0;

    protected boolean mRangeExtended = false;

    protected String mUri = null;

    protected int mLength = -1;

    protected int mBufferSize = -1;

    protected String mServerIP = null;

    private Handler mReconnectHandler;

    private HandlerThread mReconnectThread;

    protected BufferedDataSource(String uri, long offset, int length, int bufferSize,
            Handler notify, BandwidthEstimator bandwidthEstimator) throws FileNotFoundException,
            IOException {

        if (LOGS_ENABLED)
            Log.v(TAG, "offset " + offset + " length " + length + " bufferSize " + bufferSize);

        mOffset = offset;
        if (mOffset == -1) {
            mOffset = 0;
        }
        mLength = length;
        mBufferSize = bufferSize;
        mUri = uri;
        mCurrentOffset = mOffset;
        mNotify = notify;
        mBandwidthEstimator = bandwidthEstimator;

        if (mNotify != null) {
            mReconnectThread = new HandlerThread("Reconnect thread");
            mReconnectThread.start();

            mReconnectHandler = new ReconnectHandler(mReconnectThread.getLooper());
        }

        openConnectionsAndStreams();
    }

    protected BufferedDataSource(HttpURLConnection urlConnection, int bufferSize, Handler notify,
                                 BandwidthEstimator bandwidthEstimator) throws IOException {

        mOffset = 0;
        mLength = -1;
        mBufferSize = bufferSize;
        mUri = urlConnection.getURL().toString();
        mCurrentOffset = 0;
        mNotify = notify;
        mBandwidthEstimator = bandwidthEstimator;

        if (mNotify != null) {
            mReconnectThread = new HandlerThread("Reconnect thread");
            mReconnectThread.start();

            mReconnectHandler = new ReconnectHandler(mReconnectThread.getLooper());
        }

        useConnectionsAndStreams(urlConnection);
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return readAt(mCurrentOffset, buffer, buffer.length);
    }

    @Override
    public short readShort() throws IOException, EOFException {
        byte[] shortBuffer = new byte[SHORT];
        int read = readAt(mCurrentOffset, shortBuffer, shortBuffer.length);
        if (read <= 0) {
            // Since we know that is a error it should fit fine in a short
            return (short)read;
        }
        return peekShort(shortBuffer, 0);
    }

    @Override
    public int readInt() throws IOException, EOFException {
        byte[] intBuffer = new byte[INT];
        int read = readAt(mCurrentOffset, intBuffer, intBuffer.length);
        if (read <= 0) {
            return read;
        }
        return peekInt(intBuffer, 0);
    }

    @Override
    public long readLong() throws IOException, EOFException {
        byte[] longBuffer = new byte[LONG];
        int read = readAt(mCurrentOffset, longBuffer, longBuffer.length);
        if (read <= 0) {
            return read;
        }
        return peekLong(longBuffer, 0);
    }

    @Override
    public int readByte() throws IOException {
        checkConnectionAndStream();

        int readByte = mBis.read();
        if (readByte == -1
                && mRangeExtended && mLength != -1 && mCurrentOffset < mOffset + mLength) {
            // EOS, but range was extended - so reconnect.
            if (LOGS_ENABLED) Log.d(TAG, "reconnect now because of read EOS at " + mCurrentOffset);
            mOffset = mCurrentOffset;

            doCloseSync();
            openConnectionsAndStreams();
            readByte = mBis.read();
        }
        mCurrentOffset++;

        return readByte;
    }

    @Override
    public long skipBytes(long count) throws IOException {
        checkConnectionAndStream();

        long totalSkipped = 0;
        do {
            long skipped = mBis.skip(count - totalSkipped);

            if (skipped == 0) {
                mBis.compact(-1);
            }

            if (skipped > -1) {
                mCurrentOffset += skipped;
                totalSkipped += skipped;
            } else if (mRangeExtended && mLength != -1 && mCurrentOffset < mOffset + mLength) {
                // EOS, but range was extended - so reconnect.
                if (LOGS_ENABLED) Log.d(TAG, "EOS when skipping  reconnect now at "
                        + (mCurrentOffset + count - totalSkipped));

                mCurrentOffset += count - totalSkipped;
                mOffset = mCurrentOffset;

                doCloseSync();
                openConnectionsAndStreams();
                totalSkipped = count;
            } else {
                // eos
                break;
            }

            if (totalSkipped < count) {
                try {
                    Thread.sleep(1); // Let the system take a breath....
                } catch (InterruptedException e) {
                }
            }
        } while (totalSkipped < count);

        return totalSkipped;
    }

    @Override
    public void setRange(long offset, long length) {
        if (LOGS_ENABLED) Log.v(TAG, "setRange " + offset + " length " + length);
        mOffset = offset;
        mRangeExtended = true;

        if (length < Integer.MAX_VALUE) {
            mLength = (int)length;
        } else {
            mLength = Integer.MAX_VALUE;
        }
    }

    @Override
    public String getRemoteIP() {
        return mServerIP;
    }

    @Override
    public long length() throws IOException {
        return mContentLength;
    }

    @Override
    public long getCurrentOffset() {
        return mCurrentOffset;
    }


    @Override
    public void close() throws IOException {
        /*
         * Close the BufferStream and underlying streams asynchronously, due to
         * a bug in okHTTP: https://github.com/square/okhttp/pull/430
         */
        doCloseAsync();
        if (mReconnectThread != null) {
            mReconnectThread.quit();
            mReconnectThread = null;
        }
        mReconnectHandler = null;
    }

    @Override
    public void reset() {
        // Interested subclasses should override this.
    }

    @Override
    public DataAvailability hasDataAvailable(long offset, int size) {
        // Interested subclasses should override this.
        return DataAvailability.AVAILABLE;
    }

    @Override
    public void seek(long offset) throws IOException {
        checkConnectionAndStream();
        if (!mBis.isStreamClosed()) {
            mCurrentOffset = offset;
            mOffset = offset;

            doCloseSync();
            openConnectionsAndStreams();
        }
    }

    protected void openConnectionsAndStreams() throws FileNotFoundException, IOException {
        InputStream in = null;
        mRangeExtended = false;
        if (LOGS_ENABLED) Log.d(TAG, "openConnectionsAndStreams at " + mCurrentOffset);

        if (mUri.startsWith("http")) {
            int length = mLength;
            // Assume the range is meaningful and try not to overshoot that
            // range.
            if (mCurrentOffset < mOffset + mLength) {
                length -= (mCurrentOffset - mOffset);
            }
            mHttpURLConnection = openHttpConnection(mUri, mCurrentOffset, length);
            in = mHttpURLConnection.getInputStream();
        } else if (mUri.startsWith("/") || mUri.startsWith("file")) {
            File f = new File(mUri);
            in = new FileInputStream(f);
            mContentLength = f.length();
        }

        // Set bufferSize to the default size.
        int bufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;

        if (mBufferSize != -1) {
            // Size specified at create time.
            bufferSize = mBufferSize;
        }

        if (mLength != -1 && mLength < bufferSize) {
            // We got a length smaller than set buffer size.
            // Use length + 200 bytes as bufferSize
            bufferSize = mLength + 200;
        }
        mBufferSize = bufferSize;

        // TODO: We need to check if we run on a low memory device and adjust
        // the buffer size.
        if (in != null) {
            mBis = new BufferedStream(in, bufferSize, mBandwidthEstimator, mReconnectHandler);
        } else {
            throw new IOException("Unable to open data stream");
        }
    }

    private HttpURLConnection openHttpConnection(String http, long offset, int length)
            throws IOException {
        try {
            URL url = new URL(http);
            HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
            httpConnection.setConnectTimeout(5000); // 5s timeout
            httpConnection.setReadTimeout(5000); // 5s timeout
            httpConnection.setRequestProperty("Accept-Encoding", "identity");

            if (offset > 0 || length != -1) {
                String requestRange = "bytes=" + offset + "-";
                if (length != -1) {
                    requestRange += "" + (offset + (length - 1));
                }
                httpConnection.setRequestProperty("Range", requestRange);
            }

            httpConnection.connect();

            InetAddress address = InetAddress.getByName(url.getHost());
            mServerIP = address.getHostAddress();

            int responseCode = httpConnection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK
                    && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                if (LOGS_ENABLED) Log.e(TAG, "Server responded with " + responseCode);
                throw new IOException("Not OK from server");
            }

            if (mLength != -1 || offset == 0) {
                // Unless a finite range is set, full length is defined by first
                // connect.
                try {
                    mContentLength =
                            Long.parseLong(httpConnection.getHeaderField("Content-Length"));
                } catch (NumberFormatException e) {
                    mContentLength = -1;
                    if (LOGS_ENABLED) Log.e(TAG, "Failed to Parse header field");
                }
            }

            return httpConnection;

        } catch (MalformedURLException e) {
            throw new IOException("Not an HTTP Url!");
        } catch (ProtocolException e) {
            throw new IOException("Unsupported response from server!");
        }
    }

    protected void useConnectionsAndStreams(HttpURLConnection urlConnection) throws IOException {
        InputStream in = null;
        mRangeExtended = false;
        if (LOGS_ENABLED) Log.d(TAG, "useConnectionsAndStreams at " + mCurrentOffset);

        mHttpURLConnection = useHttpConnection(urlConnection);
        in = mHttpURLConnection.getInputStream();

        // Set bufferSize to the default size.
        int bufferSize = Configuration.DEFAULT_HTTP_BUFFER_SIZE;

        if (mBufferSize != -1) {
            // Size specified at create time.
            bufferSize = mBufferSize;
        }

        mBufferSize = bufferSize;

        // TODO: We need to check if we run on a low memory device and adjust
        // the buffer size.
        if (in != null) {
            mBis = new BufferedStream(in, bufferSize, mBandwidthEstimator, mReconnectHandler);
        } else {
            throw new IOException("Unable to open data stream");
        }
    }

    private HttpURLConnection useHttpConnection(HttpURLConnection httpConnection)
            throws IOException {
        try {
            InetAddress address = InetAddress.getByName(httpConnection.getURL().getHost());
            mServerIP = address.getHostAddress();

            int responseCode = httpConnection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK
                    && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                if (LOGS_ENABLED) Log.e(TAG, "Server responded with " + responseCode);
                throw new IOException("Not OK from server");
            }

            try {
                mContentLength =
                        Long.parseLong(httpConnection.getHeaderField("Content-Length"));
            } catch (NumberFormatException e) {
                mContentLength = -1;
                if (LOGS_ENABLED) Log.e(TAG, "Failed to Parse header field");
            }

            return httpConnection;

        } catch (MalformedURLException e) {
            throw new IOException("Not an HTTP Url!");
        } catch (ProtocolException e) {
            throw new IOException("Unsupported response from server!");
        }
    }

    protected void doReconnect() throws IOException {

        InputStream in = null;
        mRangeExtended = false;
        if (LOGS_ENABLED) Log.d(TAG, "Reconnect at " + mOffset);

        if (mUri.startsWith("http")) {
            mHttpURLConnection = openHttpConnection(mUri, mOffset + mBis.getTotalBytesLoaded(),
                    mLength);
            in = mHttpURLConnection.getInputStream();
        } else if (mUri.startsWith("/") || mUri.startsWith("file")) {
            File f = new File(mUri);
            in = new FileInputStream(f);
            mContentLength = f.length();
        }

        if (in != null && mBis != null) {
            mOffset += mBis.getTotalBytesLoaded();
            mBis.reconnect(in);
        } else {
            throw new IOException("Unable to open data stream");
        }
    }

    protected void checkConnectionAndStream() throws IOException {
        if (mHttpURLConnection == null) {
            if(mNotify != null){
                mNotify.sendEmptyMessage(SOURCE_ERROR);
            }
            throw new IOException("No HTTP connection availble");
        }

        if (mBis == null) {
            if(mNotify != null){
                mNotify.sendEmptyMessage(SOURCE_ERROR);
            }
            throw new IOException("No data stream availble");
        }
    }

    /*
     * DO NOT USE!!!!! Must be closed asynchronously due to bug in okHTTP:
     * https://github.com/square/okhttp/pull/430
     */
    protected void doClose() {
        throw new RuntimeException("You should not use this function!!");
        // doCloseSilently(mBis);
        // mBis = null;
        //
        // if (mHttpURLConnection != null) {
        // mHttpURLConnection.disconnect();
        // mHttpURLConnection = null;
        // }
    }

    protected void doCloseSync() {
        doCloseSilently(mBis);
        mBis = null;

        if (mHttpURLConnection != null) {
            mHttpURLConnection.disconnect();
            mHttpURLConnection = null;
        }
    }

    /**
     * Closes the BufferStream and underlying streams asynchronously. This is
     * due to a bug in okHTTP: https://github.com/square/okhttp/pull/430
     */
    protected void doCloseAsync() {
        final Thread closer = new Thread() {
            @Override
            public void run() {
                doCloseSilently(mBis);
                mBis = null;

                if (mHttpURLConnection != null) {
                    try {
                        mHttpURLConnection.disconnect();
                    } catch (Exception e) {
                        if (LOGS_ENABLED) {
                            Log.e(TAG, "Exception from mHttpURLConnection.disconnect()", e);
                        }
                    }
                    mHttpURLConnection = null;
                }
            }
        };
        closer.start();
    }

    protected void doCloseSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public abstract int readAt(long offset, byte[] buffer, int size) throws IOException;

    class ReconnectHandler extends Handler {

        public ReconnectHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RECONNECT:
                    try {
                        if (mBis != null) {
                            if (mBis.isValidForReconnect()) {
                                doReconnect();
                            } else {
                                mBis.close();
                                mNotify.sendEmptyMessage(SOURCE_ERROR);
                            }
                        }
                    } catch (IOException e) {
                        mReconnectHandler.sendEmptyMessageAtTime(MSG_RECONNECT,
                                SystemClock.uptimeMillis() + 1000);
                    }
                    break;
                case MSG_SOCKET_TIMEOUT:
                    try {
                        if (mBis != null) {
                            mBis.close();
                        }
                    } catch (IOException e) {
                        // Ignored.
                    } finally {
                        mNotify.sendEmptyMessage(SOURCE_ERROR);
                    }
                    break;
            }
        }
    }
}
