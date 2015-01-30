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
 * Interface definition of a meta data parser.
 */
public interface MetaDataParser {

    /**
     * Get the number of media tracks for the file.
     *
     * @return Number of tracks.
     */
    public int getTrackCount();

    /**
     * Get the meta data for this file.
     *
     * @return Meta data for the file.
     */
    public MetaData getMetaData();

    /**
     * Get the meta data for a certain track of a media file.
     *
     * @param index of the track.
     * @return Meta data for the track. If no track is found for the index
     *         return null.
     */
    public MetaData getTrackMetaData(int index);

    /**
     * Release this meta data parser and close all resources that belongs to it.
     */
    public void release();

}
