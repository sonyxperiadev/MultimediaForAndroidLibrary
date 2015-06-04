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

import android.content.Context;
import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;

public abstract class DrmSession {

    public static class DrmLicenseException extends Exception {
        private final int mErrorCode;

        public DrmLicenseException(int errorCode) {
            mErrorCode = errorCode;
        }

        public int getErrorCode() {
            return mErrorCode;
        }
    }

    /**
     * The error state. {@link #getError()} can be used to retrieve the cause.
     */
    public static final int STATE_ERROR = 0;

    /**
     * The session is closed.
     */
    public static final int STATE_CLOSED = 1;

    /**
     * The session is being opened (i.e. {@link #open(Map, String)} has been
     * called, but the session is not yet open).
     */
    public static final int STATE_OPENING = 2;

    /**
     * The session is open, but does not yet have the keys required for
     * decryption.
     */
    public static final int STATE_OPENED = 3;

    /**
     * The session is open and has the keys required for decryption.
     */
    public static final int STATE_OPENED_WITH_KEYS = 4;

    protected int mOpenCount;

    protected int mState;

    protected MediaDrm mMediaDrm;

    protected OutputController mOutputController;

    protected byte[] mSessionId = null;

    protected Map<UUID, byte[]> mPsshInfo;

    protected Map<UUID, byte[]> mMarlinInitData;

    protected HashMap<String, String> mDrmInfo;

    /**
     * Open a drm session. This call should be called first time a drm session
     * is created and pssh information is given by DrmSessionFactory.create() or
     * DrmSessionFactory.create(UUID uuid, Map<UUID, byte[]> map).
     */
    public abstract void open() throws DrmLicenseException;

    /**
     * Close a session.
     */
    public abstract void close();

    /**
     * Get a MediaCrypto for the current DRM session.
     *
     * @return MediaCrypto
     * @throws MediaCryptoException
     * @throws IllegalStateException
     */
    public abstract MediaCrypto getMediaCrypto(String key) throws IllegalStateException,
            MediaCryptoException;

    /**
     * Release a MediaCrypto for the current DRM session.
     *
     * @return MediaCrypto
     * @throws MediaCryptoException
     * @throws IllegalStateException
     */
    public abstract void releaseMediaCrypto(String key) throws MediaCryptoException;

    /**
     * Get the MediaDrm used for the current DRM session.
     *
     * @return MediaDrm
     */
    public MediaDrm getMediaDrm() {
        return mMediaDrm;
    }

    /**
     * Creates an OutputController to protect content being rendered on non
     * secure device.
     *
     * @param context Context used to register for callbacks regarding routing
     *            changes.
     */
    public synchronized void initOutputController(Context context,
            OnOutputControllerUpdateListener listener) {
        if (mDrmInfo != null) {
            mOutputController = new OutputController(context, mDrmInfo, listener);
        } else {
            mOutputController = null;
        }
    }

    /**
     * Get the OutputController used for the current DRM session.
     *
     * @return A OutputController
     */
    public synchronized OutputController getOutputController() {
        return mOutputController;
    }
}
