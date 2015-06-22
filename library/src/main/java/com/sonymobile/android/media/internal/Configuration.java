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

import com.sonymobile.android.media.BuildConfig;

public class Configuration {

    public static final boolean DEBUG = BuildConfig.LOGS_ENABLED;

    public static final int SUBTITLE_PRETRIGGER_TIME_MS = 30;

    public static final boolean DO_COMPENSATE_AUDIO_TIMESTAMP_LATENCY = false;

    public static final int DEFAULT_HTTP_BUFFER_SIZE = 50 * 1024 * 1024;

    public static final int HTTP_MIN_BUFFERING_DURATION_US = 2000000;

    public static final boolean ENABLE_PLATFORM_PARSER = true;
}
