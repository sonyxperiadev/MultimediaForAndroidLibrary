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

import com.sonymobile.android.media.MediaPlayer.OutputControlInfo;

public interface OnOutputControllerUpdateListener {

    public void onExternalWifiRestricted(OutputControlEvent outputControlEvent);

    public void onExternalHDMIRestricted(OutputControlEvent outputControlEvent);

    public void onHeadphonesRestricted(OutputControlEvent outputControlEvent);

    public void onOutputControlInfo(OutputControlInfo info);
}
