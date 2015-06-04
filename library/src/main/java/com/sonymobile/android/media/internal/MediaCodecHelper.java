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

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

public class MediaCodecHelper {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "MediaCodecHelper";

    public static String findDecoder(String mimeType, boolean requireSecure,
            MediaCodecInfo[] codecInfo)
            throws IllegalArgumentException {

        if (codecInfo != null && codecInfo.length == 0) {
            throw new IllegalArgumentException();
        }

        String name = findDecoderKitKat(mimeType, requireSecure, codecInfo);

        if (name == null) {
            if (LOGS_ENABLED)
                Log.e(TAG, "No codec found for type: " + mimeType + ", secure: " + requireSecure);
            throw new IllegalArgumentException();
        }

        return name;
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String findDecoderKitKat(String mimeType, boolean requireSecure,
            MediaCodecInfo[] codecInfo) {
        int count = MediaCodecList.getCodecCount();

        for (int i = 0; i < count; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);

            if (!info.isEncoder()) {
                String[] supportedTypes = info.getSupportedTypes();

                for (String supportedType : supportedTypes) {
                    if (supportedType.equalsIgnoreCase(mimeType)) {
                        String name = info.getName();

                        if (requireSecure) {
                            name += ".secure";
                        }

                        if (codecInfo != null) {
                            codecInfo[0] = info;
                        }

                        return name;
                    }
                }
            }
        }

        return null;
    }
}
