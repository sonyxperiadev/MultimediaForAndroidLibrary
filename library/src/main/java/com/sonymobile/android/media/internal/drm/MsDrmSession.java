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
package com.sonymobile.android.media.internal.drm;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.media.DeniedByServerException;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.MediaDrm.KeyRequest;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.Player;

public class MsDrmSession extends DrmSession {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MsDrmSession";

    private static final int OK = 0;

    private static final int MSG_REQUEST_LICENSE_KEY = 1;

    private static final String MSDRM_PARAMETER_HEADER = "Header";

    private static final String MSDRM_PARAMETER_ACTION = "Action";

    private static final String MSDRM_ACTION_GENERATE_LICENSE_CHALLENGE = "GenerateLicChallenge";

    private static final String MSDRM_ACTION_PROCESS_LICENSE_RESPONSE = "ProcessLicResponse";

    private static final String MSDRM_PARAMETER_DATA = "Data";

    private HashMap<String, MediaCrypto> mMediaCryptoMap;

    private String mMimeType;

    private int mKeyType;

    private Handler mCallbackHandler;

    private HashMap<String, String> mParams = new HashMap<>();

    private LicenseHandler mLicenseHandler;

    private HandlerThread mLicenseHandlerThread;

    public MsDrmSession(Map<UUID, byte[]> psshInfo) throws UnsupportedSchemeException,
            IllegalArgumentException {

        if (psshInfo == null) {
            throw new IllegalArgumentException("No valid initialization data");
        }

        mPsshInfo = psshInfo;
        mMediaDrm = new MediaDrm(DrmUUID.PLAY_READY);
        mOutputController = null;
        mMediaCryptoMap = new HashMap<>();
    }

    @Override
    public synchronized void open() throws DrmLicenseException {
        if (++mOpenCount == 1) {
            mState = STATE_OPENING;

            byte[] initData = mPsshInfo.get(DrmUUID.PLAY_READY);
            if (initData != null) {
                try {
                    mSessionId = mMediaDrm.openSession();
                    mState = STATE_OPENED;

                } catch (NotProvisionedException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                } catch (ResourceBusyException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                } catch (IllegalStateException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                }
            }
        }
    }

    public synchronized MediaCrypto getMediaCrypto(String key) throws IllegalStateException,
            MediaCryptoException {
        if (mState != STATE_OPENED && mState != STATE_OPENED_WITH_KEYS) {
            throw new IllegalStateException("Illegal state. Was a DRM session opened?");
        }

        MediaCrypto mediaCrypto = mMediaCryptoMap.get(key);
        if (mediaCrypto == null) {
            mediaCrypto = new MediaCrypto(DrmUUID.PLAY_READY, mSessionId);
            mMediaCryptoMap.put(key, mediaCrypto);
        }

        return mediaCrypto;
    }

    public synchronized void releaseMediaCrypto(String key) throws MediaCryptoException {
        MediaCrypto mediaCrypto = mMediaCryptoMap.get(key);
        if (mediaCrypto != null) {
            mMediaCryptoMap.remove(key);
            mediaCrypto.release();
        }
    }

    @Override
    public synchronized void close() {
        mOpenCount--;

        if (LOGS_ENABLED) Log.d(TAG, "Close count = " + mOpenCount + " state = " + mState);

        if (mOpenCount == 0) {
            if (!mMediaCryptoMap.isEmpty()) {
                releaseAllMediaCryptos();
            }
            if (mSessionId != null) {
                mMediaDrm.closeSession(mSessionId);
            }
            mMediaDrm = null;
            mState = STATE_CLOSED;
            if (mOutputController != null) {
                mOutputController.release();
            }

            if (mLicenseHandlerThread != null) {
                mLicenseHandlerThread.quit();
            }

            mLicenseHandlerThread = null;
            mLicenseHandler = null;
        }
    }

    private synchronized void releaseAllMediaCryptos() {
        if (LOGS_ENABLED) Log.d(TAG, "Open MediaCryptos: " + mMediaCryptoMap.size());

        for (Map.Entry<String, MediaCrypto> crypto : mMediaCryptoMap.entrySet()) {
            crypto.getValue().release();
        }
        mMediaCryptoMap.clear();
    }

    @Override
    public synchronized void requestKey(final String mime, final int keyType,
            final Handler callbackHandler) {
        mMimeType = mime;
        mKeyType = keyType;
        mCallbackHandler = callbackHandler;

        mLicenseHandlerThread = new HandlerThread("DRM-License-Thread");
        mLicenseHandlerThread.start();

        mLicenseHandler = new LicenseHandler(new WeakReference<MsDrmSession>(this),
                mLicenseHandlerThread.getLooper());

        mLicenseHandler.sendEmptyMessage(MSG_REQUEST_LICENSE_KEY);
    }

    @Override
    public synchronized void restoreKey() throws DrmLicenseException {
        try {
            byte[] initData = mPsshInfo.get(DrmUUID.PLAY_READY);
            mMediaDrm.restoreKeys(mSessionId, initData);
            mDrmInfo = mMediaDrm.queryKeyStatus(mSessionId);
            String validLicense = mDrmInfo.get("valid_license");
            if (!validLicense.equals("true")) {
                mState = STATE_ERROR;
                if (mDrmInfo.containsKey("license_start_time")) {
                    throw new DrmLicenseException(MediaError.DRM_LICENSE_FUTURE);
                } else {
                    throw new DrmLicenseException(MediaError.DRM_NO_LICENSE);
                }
            }
            mState = STATE_OPENED_WITH_KEYS;

        } catch (RuntimeException e) { //MediaDrmStateException, API level 21
            throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
        }
    }

    private synchronized void onRequestKey() {
        try {
            KeyRequest keyRequest = mMediaDrm.getKeyRequest(mSessionId, mPsshInfo.get(DrmUUID
                            .PLAY_READY), mMimeType, mKeyType, null);

            byte[] response = getKeyResponseFromServer(keyRequest.getDefaultUrl(),
                    keyRequest.getData());

            provideKeyResponse(response);

        } catch (NotProvisionedException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Failed to get a key request!", e);
            mState = STATE_ERROR;
            sendRequestDoneCallback(MediaError.DRM_UNKNOWN, false);

        } catch (IllegalArgumentException e) {
            // Some devices implement the DRM handling according to:
            // https://github.com/sonyxperiadev/DrmLicenseService
            // If the normal API fails use this flow instead.

            specialRequestKey();
            return;
        }
    }

    private synchronized void specialRequestKey() {
        // Some devices implement the DRM handling according to:
        // https://github.com/sonyxperiadev/DrmLicenseService
        // Request key and supply key response according to this.

        KeyRequest keyRequest = getLicenseChallenge();

        if (keyRequest != null) {

            byte[] response = getKeyResponseFromServer(keyRequest.getDefaultUrl(),
                    keyRequest.getData());

            specialProvideKeyResponse(response);

        } else {
            if (LOGS_ENABLED) {
                Log.e(TAG, "No Key request!");
            }
            mState = STATE_ERROR;
            sendRequestDoneCallback(MediaError.DRM_UNKNOWN, false);
        }
    }

    private synchronized void provideKeyResponse(byte[] response) {
        int errorCode = OK;
        if (response != null) {
            try {
                mMediaDrm.provideKeyResponse(mSessionId, response);
                queryKeyStatus();
                mState = STATE_OPENED_WITH_KEYS;

            } catch (NotProvisionedException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "NotProvisionedException when providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = MediaError.DRM_UNKNOWN;

            } catch (DeniedByServerException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "DeniedByServerException when providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = MediaError.DRM_UNKNOWN;

            } catch (DrmLicenseException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "DrmLicenseException when providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = e.getErrorCode();
            }
        } else {
            errorCode = MediaError.DRM_UNKNOWN;
        }

        sendRequestDoneCallback(errorCode, errorCode == OK);
    }

    private synchronized void specialProvideKeyResponse(byte[] response) {
        // Some devices implement the DRM handling according to:
        // https://github.com/sonyxperiadev/DrmLicenseService
        // Request key and supply key response according to this.

        int errorCode = OK;
        if (response != null) {
            try {
                byte[] psshData = mPsshInfo.get(DrmUUID.PLAY_READY);
                mParams.put(MSDRM_PARAMETER_ACTION, MSDRM_ACTION_PROCESS_LICENSE_RESPONSE);
                mParams.put(MSDRM_PARAMETER_DATA, new String(response, StandardCharsets.UTF_8));
                mMediaDrm.getKeyRequest(mSessionId, psshData, null, mKeyType, mParams);

                queryKeyStatus();
                mState = STATE_OPENED_WITH_KEYS;

            } catch (NotProvisionedException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "NotProvisionedException while providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = MediaError.DRM_UNKNOWN;

            } catch (DrmLicenseException e) {
                if (LOGS_ENABLED) {
                    Log.e(TAG, "DrmLicenseException while providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = e.getErrorCode();

            } catch (RuntimeException e) { // MediaDrmStateException API level 21
                if (LOGS_ENABLED) {
                    Log.e(TAG, "RuntimeException while providing key response", e);
                }
                mState = STATE_ERROR;
                errorCode = MediaError.DRM_UNKNOWN;
            }

        } else {
            mState = STATE_ERROR;
            errorCode = MediaError.DRM_UNKNOWN;
        }

        sendRequestDoneCallback(errorCode, errorCode == OK);
    }

    private synchronized void sendRequestDoneCallback(int errorCode, boolean success) {
        if (mCallbackHandler != null) {
            mCallbackHandler.obtainMessage(Player.MSG_DRM_NOTIFY, errorCode, 0, success)
                    .sendToTarget();
        }
        mCallbackHandler = null;
    }

    private synchronized KeyRequest getLicenseChallenge() {

        KeyRequest keyRequest = null;
        try {
            if (mSessionId == null) {
                mSessionId = mMediaDrm.openSession();
            }
            byte[] psshData = mPsshInfo.get(DrmUUID.PLAY_READY);
            mParams.put(MSDRM_PARAMETER_HEADER, getPlayReadyHeader(psshData));
            mParams.put(MSDRM_PARAMETER_ACTION, MSDRM_ACTION_GENERATE_LICENSE_CHALLENGE);
            keyRequest = mMediaDrm.getKeyRequest(mSessionId, psshData, null,
                    mKeyType, mParams);

        } catch (NotProvisionedException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Failed to get a key request!", e);
        } catch (IllegalStateException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Failed to get a key request!", e);
        } catch (ResourceBusyException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Failed to get a key request!", e);
        }
        return keyRequest;
    }

    private void queryKeyStatus() throws DrmLicenseException {
        mDrmInfo = mMediaDrm.queryKeyStatus(mSessionId);
        String validLicense = mDrmInfo.get("valid_license");
        if (!validLicense.equals("true")) {
            mState = STATE_ERROR;
            if (mDrmInfo.containsKey("license_start_time")) {
                throw new DrmLicenseException(MediaError.DRM_LICENSE_FUTURE);
            } else {
                throw new DrmLicenseException(MediaError.DRM_NO_LICENSE);
            }
        }
    }

    private static int uint16FromBufferLE(byte[] buffer, int offset) {
        int result = (buffer[offset + 1] & 0xFF) << 8 | (buffer[offset] & 0xFF);
        return result;
    }

    public static String getPlayReadyHeader(byte[] playReadyObjects) {
        String result = null;
        if (playReadyObjects != null) {
            long recordOffset = 0;
            int playReadyObjectSize = 0;
            int numberOfRecords = 0;
            int DRM_PLAYREADY_RECORD_VALUE_OFFSET = 4;
            int DRM_PLAYREADY_OBJECTS_HEADER_RECORD_OFFSET = 6;
            int DRM_PLAYREADY_RECORD_LENGTH_OFFSET = 2;
            int DRM_PLAYREADY_RECORD_MIN_LENGTH = 4;

            playReadyObjectSize = playReadyObjects.length;
            recordOffset = DRM_PLAYREADY_OBJECTS_HEADER_RECORD_OFFSET; // offset to first record

            // Read a PlayReady Record
            while (recordOffset <= playReadyObjectSize - DRM_PLAYREADY_RECORD_VALUE_OFFSET) {
                int recordType = uint16FromBufferLE(playReadyObjects, (int)recordOffset);
                int recordValueSize = uint16FromBufferLE(playReadyObjects,
                        (int)recordOffset + DRM_PLAYREADY_RECORD_LENGTH_OFFSET);

                if (recordValueSize < DRM_PLAYREADY_RECORD_MIN_LENGTH) {
                    if (LOGS_ENABLED) Log.e(TAG, "Playready record to small");
                    break;
                }

                long recordValueOffset = recordOffset + DRM_PLAYREADY_RECORD_VALUE_OFFSET;

                if (recordType == 1
                        && recordValueOffset + recordValueSize <= playReadyObjects.length) {
                    // header found
                    try {
                        result = new String(playReadyObjects, (int)recordValueOffset,
                                recordValueSize, "UTF-16LE");
                    } catch (UnsupportedEncodingException ex) {
                        result = null;
                    }

                    break;
                } else {
                    // Step to next record
                    if (--numberOfRecords == 0) {
                        if (LOGS_ENABLED) Log.d(TAG, "No more playready records");
                        break;
                    }
                    recordOffset = recordOffset + DRM_PLAYREADY_RECORD_VALUE_OFFSET
                            + recordValueSize;
                }
            }
        }
        return result;
    }


    private byte[] getKeyResponseFromServer(String http, byte[] drmRequest) {
        ByteArrayOutputStream bos = null;
        InputStream in = null;
        HttpURLConnection httpConnection = null;

        try {
            URL url = new URL(http);
            httpConnection = (HttpURLConnection)url.openConnection();
            httpConnection.setConnectTimeout(5000); // 5s timeout
            httpConnection.setRequestMethod("POST");
            httpConnection.setDoOutput(drmRequest != null);
            httpConnection.setDoInput(true);
            httpConnection.addRequestProperty("Accept-Encoding", "identity");
            httpConnection.addRequestProperty("Content-Type", "text/xml");
            httpConnection.addRequestProperty("SOAPAction",
                    "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");

            if (drmRequest != null) {
                OutputStream out = new BufferedOutputStream(httpConnection.getOutputStream());
                out.write(drmRequest);
                out.close();
            }

            if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                bos = new ByteArrayOutputStream();
                in = httpConnection.getInputStream();

                byte[] data = new byte[1024];
                int read;
                do {
                    read = in.read(data);
                    if (read > 0) {
                        bos.write(data, 0, read);
                    }

                } while (read > 0);

                return bos.toByteArray();
            }
        } catch (MalformedURLException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Exception during key server request", e);
        } catch (ProtocolException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Exception during key server request", e);
        } catch (IOException e) {
            if (LOGS_ENABLED) Log.e(TAG, "Exception during key server request", e);
        } finally {
            closeSilently(bos);
            closeSilently(in);
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }

        return null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (mLicenseHandlerThread != null) {
            mLicenseHandlerThread.quit();
        }
    }

    private void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
            }
        }
    }

    private static class LicenseHandler extends Handler {

        private WeakReference<MsDrmSession> mSession;

        public LicenseHandler(WeakReference<MsDrmSession> session, Looper looper) {
            super(looper);
            mSession = session;
        }

        @Override
        public void handleMessage(Message msg) {
            MsDrmSession thiz = mSession.get();
            switch (msg.what) {
                case MSG_REQUEST_LICENSE_KEY: {
                    thiz.onRequestKey();
                    break;
                }
                default:
                    break;
            }

        }
    }
}
