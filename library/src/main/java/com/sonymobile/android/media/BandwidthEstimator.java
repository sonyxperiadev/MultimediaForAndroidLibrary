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

package com.sonymobile.android.media;

/**
 * Interface definition of a bandwidth estimator.
 */
public interface BandwidthEstimator {

    /**
     * Get the currently estimated bandwidth.
     *
     * @return the estimated bandwidth in bytes/s
     */

    public long getEstimatedBandwidth();

    /**
     * Called when a data transfer is started
     */
    public void onDataTransferStarted();

    /**
     * Called when a data transfer is stoppped
     */
    public void onDataTransferEnded();

    /**
     * Called to indicate that data have been transferred.
     *
     * @param byteCount The number of bytes transferred.
     */
    public void onDataTransferred(long byteCount);
}
