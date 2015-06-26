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

import java.util.Map;
import java.util.UUID;

import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.Handler;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.internal.Configuration;

public class MarlinDrmSession extends DrmSession {

    private static final boolean DEBUG_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MarlinDrmSession";

    private MediaCrypto mMediaCrypto;

    public MarlinDrmSession(Map<UUID, byte[]> init) throws UnsupportedSchemeException,
            IllegalArgumentException {

        if (init == null) {
            throw new IllegalArgumentException("No valid initialization data");
        }

        mMarlinInitData = init;
        mMediaDrm = new MediaDrm(DrmUUID.MARLIN);
        mOutputController = null;
    }

    @Override
    public synchronized void open() throws DrmLicenseException {
        byte[] initData;

        mState = STATE_OPENING;
        if (mMarlinInitData != null) {
            initData = mMarlinInitData.get(DrmUUID.MARLIN);
            if (initData != null) {
                try {
                    mSessionId = mMediaDrm.openSession();
                    mState = STATE_OPENED;
                    mMediaDrm.restoreKeys(mSessionId, initData);
                    mDrmInfo = mMediaDrm.queryKeyStatus(mSessionId);
                    if (mDrmInfo != null) {
                        String validLicense = mDrmInfo.get("license.status");
                        if (!validLicense.equals("valid")) {
                            mState = STATE_ERROR;
                            if (validLicense.equals("expired")) {
                                throw new DrmLicenseException(MediaError.DRM_LICENSE_EXPIRED);
                            } else {
                                throw new DrmLicenseException(MediaError.DRM_NO_LICENSE);
                            }
                        }
                    }
                    mMediaCrypto = new MediaCrypto(DrmUUID.MARLIN, initData);
                    mState = STATE_OPENED_WITH_KEYS;
                } catch (NotProvisionedException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                } catch (MediaCryptoException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                } catch (ResourceBusyException e) {
                    mState = STATE_ERROR;
                    throw new DrmLicenseException(MediaError.DRM_UNKNOWN);
                }
            }
        }
    }

    public synchronized MediaCrypto getMediaCrypto(String key) throws IllegalStateException{
        if (mState != STATE_OPENED && mState != STATE_OPENED_WITH_KEYS) {
            throw new IllegalStateException("Illegal state. Was a DRM session opened?");
        }
        return mMediaCrypto;
    }

    public synchronized void releaseMediaCrypto(String key) throws IllegalStateException{
        if (mState != STATE_OPENED && mState != STATE_OPENED_WITH_KEYS) {
            throw new IllegalStateException("Illegal state. Was a DRM session opened?");
        }
        mMediaCrypto.release();
    }

    public synchronized MediaDrm getMediaDrm() throws IllegalStateException{
        if (mState != STATE_OPENED && mState != STATE_OPENED_WITH_KEYS) {
            throw new IllegalStateException("Illegal state. Was a DRM session opened?");
        }
        return mMediaDrm;
    }

    @Override
    public synchronized void close() {
        if (mMediaCrypto != null) {
            mMediaCrypto.release();
        }
        mMediaCrypto = null;
        if (mSessionId != null) {
            mMediaDrm.closeSession(mSessionId);
        }
        mMediaDrm = null;
        mState = STATE_CLOSED;
        if (mOutputController != null) {
            mOutputController.release();
        }
    }

    @Override
    public void requestKey(String mime, int keyType, Handler callbackHandler) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void restoreKey() throws DrmLicenseException {
        throw new RuntimeException("Not supported");
    }
}
