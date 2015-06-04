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

public class OutputControlEvent {
    public final static int NOT_SET = -1;

    public final static int OUTPUT_AUDIO_HEADPHONES_RESTRICTED = 1;

    public final static int OUTPUT_EXTERNAL_WIFI_RESTRICTED = 2;

    public final static int OUTPUT_EXTERNAL_HDMI_RESTRICTED = 3;

    private final LicenseInfo mLicenseInfo;

    public OutputControlEvent(LicenseInfo licenseInfo){
        mLicenseInfo = licenseInfo;
    }

    protected LicenseInfo getLicenseInfo() {
        return mLicenseInfo;
    }

    public String toString() {
        return mLicenseInfo.toString();
    }
}
