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
import java.util.Map.Entry;
import java.util.UUID;

import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;

public class DrmSessionFactory {

    /**
     * Create a DrmSession base on MediaExtractor.getPsshInfo() if the UUID is unknown.
     * The map parameter is also used by OutputController to get the license information.
     *
     * @param psshInfo PSSH information given by a MediaExtractor.
     * @return DrmSession
     * @throws IllegalArgumentException
     * @throws UnsupportedSchemeException
     */
    public static DrmSession create(Map<UUID, byte[]> psshInfo) throws IllegalArgumentException,
            UnsupportedSchemeException {

        DrmSession drmSession = null;
        if (psshInfo == null || psshInfo.isEmpty()) {
            throw new IllegalArgumentException("Invalid PSSH information");
        }
        for (Entry<UUID, byte[]> uuidEntry : psshInfo.entrySet()) {
            drmSession = create(uuidEntry.getKey(), psshInfo);
            if (drmSession != null) {
                break;
            }
        }
        return drmSession;
    }

    /**
     * Create a DrmSession base on the UUID if it is already known. The map
     * parameter is kept because OutputController needs it to extract
     * information about the license.
     *
     * @param psshInfo PSSH information given by a MediaExtractor.
     * @return DrmSession
     * @throws IllegalArgumentException
     * @throws UnsupportedSchemeException
     */
    public static DrmSession create(UUID uuid, Map<UUID, byte[]> psshInfo)
            throws UnsupportedSchemeException {
        DrmSession drmSession = null;
        if (uuid != null) {
            if (MediaDrm.isCryptoSchemeSupported(uuid)) {
                if (uuid.equals(DrmUUID.PLAY_READY)) {
                    drmSession = new MsDrmSession(psshInfo);
                } else if (uuid.equals(DrmUUID.MARLIN)) {
                    drmSession = new MarlinDrmSession(psshInfo);
                }
            } else {
                throw new UnsupportedSchemeException("Unsupported DRM scheme");
            }
        }
        return drmSession;
    }
}
