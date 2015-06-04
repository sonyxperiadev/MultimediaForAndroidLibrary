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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.util.Log;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.internal.Configuration;

public class MsDrmSession extends DrmSession {

    private static final boolean DEBUG_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MsDrmSession";

    private HashMap<String, MediaCrypto> mMediaCryptoMap;

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

        if (DEBUG_ENABLED) Log.d(TAG, "Close count = " + mOpenCount + " state = " + mState);

        if (mOpenCount == 0) {
            if(mMediaCryptoMap.size() != 0) {
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
        }
    }

    private synchronized void releaseAllMediaCryptos() {
        if (DEBUG_ENABLED) Log.d(TAG, "Open MediaCryptos: " + mMediaCryptoMap.size());

        for (Map.Entry<String, MediaCrypto> crypto : mMediaCryptoMap.entrySet()) {
            crypto.getValue().release();
        }
        mMediaCryptoMap.clear();
    }
}
