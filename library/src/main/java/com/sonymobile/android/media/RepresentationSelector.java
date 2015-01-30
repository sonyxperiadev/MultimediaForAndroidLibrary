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
 * Interface definition for selecting representations when streaming content.
 */
public interface RepresentationSelector {

    /**
     * Used to select which representation to use when starting a new streaming
     * session. NOTE: This method may be invoked in PREPARING state which means
     * that some methods in the MediaPlayer API is not available.
     *
     * @param selectedRepresentations Update the array to set which
     *            representation to fetch. The selectedRepresentations array is
     *            index according to {@link TrackInfo.TrackType}.
     * @param selectedTracks Current selected tracks
     * @param trackInfo The track information for all tracks.
     * @param selectedRepresentations out: The selected representations
     */
    public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo,
            int[] selectedRepresentations);

    /**
     * Used to select which representation to use for downloading new segments.
     *
     * @param selectedRepresentations The representation that will be fetched
     *            for each track type. Update the array to change which
     *            representation to fetch. The selectedRepresentations array is
     *            index according to {@link TrackInfo.TrackType}.
     * @param bandwidth Current estimated bandwidth
     * @param selectedTracks Current selected tracks
     * @param selectedRepresentations in: Current selected representations, out:
     *            New selected representations
     * @return true if the representation array was updated.
     */
    public boolean selectRepresentations(long bandwidth, int[] selectedTracks,
            int[] selectedRepresentations);

}
