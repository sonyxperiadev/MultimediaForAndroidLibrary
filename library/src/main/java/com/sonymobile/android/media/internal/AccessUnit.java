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

import android.media.MediaCodec.CryptoInfo;
import android.media.MediaFormat;

public class AccessUnit {
    public static final AccessUnit ACCESS_UNIT_ERROR = new AccessUnit(AccessUnit.ERROR);

    public static final AccessUnit ACCESS_UNIT_END_OF_STREAM = new AccessUnit(
            AccessUnit.END_OF_STREAM);

    public static final AccessUnit ACCESS_UNIT_NO_DATA_AVAILABLE = new AccessUnit(
            AccessUnit.NO_DATA_AVAILABLE);

    public static final int OK = 0;

    public static final int ERROR = -2;

    public static final int END_OF_STREAM = -1;

    public static final int NO_DATA_AVAILABLE = -3;

    public static final int FORMAT_CHANGED = -4;

    public int status;

    public int size;

    public byte[] data;

    public long timeUs;

    public long durationUs;

    public boolean isSyncSample = false;

    public int trackIndex;

    public MediaFormat format;

    public CryptoInfo cryptoInfo;

    public AccessUnit() {
    }

    public AccessUnit(int status) {
        this.status = status;
    }
}
