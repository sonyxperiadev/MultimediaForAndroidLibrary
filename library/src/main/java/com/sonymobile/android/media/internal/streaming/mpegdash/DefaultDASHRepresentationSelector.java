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

package com.sonymobile.android.media.internal.streaming.mpegdash;

import java.util.ArrayList;

import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.TrackRepresentation;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.AdaptationSet;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Period;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Representation;

import android.util.Log;

public class DefaultDASHRepresentationSelector implements RepresentationSelector {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "DefaultDASHRepresentationSelector";

    private final MPDParser mMPDParser;

    private final int mMaxBufferSize;

    private int mSwitchUpCounter = 0;

    private int mSwitchUpRepresentation = -1;

    private int mSwitchDownCounter = 0;

    private int mSwitchDownRepresentation = -1;

    public DefaultDASHRepresentationSelector(MPDParser parser, int maxBufferSize) {
        mMPDParser = parser;
        mMaxBufferSize = maxBufferSize;
    }

    @Override
    public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo,
            int[] selectedRepresentations) {
        // Select the audio representation with the highest quality
        if (selectedTracks[TrackType.AUDIO.ordinal()] >= 0) {
            long audioBitrate = 0;
            int audioRepresentation = 0;
            TrackRepresentation[] representations =
                    trackInfo[selectedTracks[TrackType.AUDIO.ordinal()]].getRepresentations();
            for (int i = 0; representations != null && i < representations.length; i++) {
                if (representations[i].getBitrate() > audioBitrate) {
                    audioBitrate = representations[i].getBitrate();
                    audioRepresentation = i;
                }
            }
            selectedRepresentations[TrackType.AUDIO.ordinal()] = audioRepresentation;
        } else {
            selectedRepresentations[TrackType.AUDIO.ordinal()] = -1;
        }

        // Select the first subtitle representation
        if (selectedTracks[TrackType.SUBTITLE.ordinal()] >= 0) {
            selectedRepresentations[TrackType.SUBTITLE.ordinal()] = 0;
        } else {
            selectedRepresentations[TrackType.SUBTITLE.ordinal()] = -1;
        }

        // Select the video representation with the lowest quality
        if (selectedTracks[TrackType.VIDEO.ordinal()] >= 0) {
            int worstBitrate = -1;
            int videoRepresentation = 0;
            TrackRepresentation[] representations =
                    trackInfo[selectedTracks[TrackType.VIDEO.ordinal()]].getRepresentations();
            for (int i = 0; representations != null && i < representations.length; i++) {
                if (worstBitrate == -1 || representations[i].getBitrate() < worstBitrate) {
                    worstBitrate = representations[i].getBitrate();
                    videoRepresentation = i;
                }
            }
            selectedRepresentations[TrackType.VIDEO.ordinal()] = videoRepresentation;
        } else {
            selectedRepresentations[TrackType.VIDEO.ordinal()] = -1;
        }
    }

    @Override
    public boolean selectRepresentations(long bandwidth, int[] selectedTracks,
            int[] selectedRepresentations) {
        boolean representationsChanged = false;
        Period period = mMPDParser.getActivePeriod();

        long audioBandwidth = 0;
        if (period.currentAdaptationSet[TrackType.AUDIO.ordinal()] > -1) {
            AdaptationSet audioAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.AUDIO.ordinal()]);

            int audioRepresentation = selectedRepresentations[TrackType.AUDIO.ordinal()];
            if (audioRepresentation == -1) {
                for (int i = 0; i < audioAdaptationSet.representations.size(); i++) {
                    Representation representation = audioAdaptationSet.representations.get(i);
                    if (representation.bandwidth > audioBandwidth) {
                        audioBandwidth = representation.bandwidth;
                        audioRepresentation = i;
                    }
                }
                selectedRepresentations[TrackType.AUDIO.ordinal()] = audioRepresentation;
                representationsChanged = true;
            } else {
                audioBandwidth = audioAdaptationSet.representations
                        .get(audioRepresentation).bandwidth;
            }
        }

        long subtitleBandwidth = 0;
        if (period.currentAdaptationSet[TrackType.SUBTITLE.ordinal()] > -1) {
            AdaptationSet subtitleAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.SUBTITLE.ordinal()]);

            int subtitleRepresentation = selectedRepresentations[TrackType.SUBTITLE.ordinal()];
            if (subtitleRepresentation == -1) {
                subtitleRepresentation = 0;
                selectedRepresentations[TrackType.SUBTITLE.ordinal()] = 0;
                representationsChanged = true;
            }

            subtitleBandwidth = subtitleAdaptationSet.representations
                    .get(subtitleRepresentation).bandwidth;
        }

        long availableBandwidth = 0;
        if (bandwidth > 0) {
            availableBandwidth = bandwidth - audioBandwidth - subtitleBandwidth;
        }

        if (mMaxBufferSize > 0) {
            long minBufferTimeSeconds = mMPDParser.getMinBufferTimeUs() / 1000000;
            long availableBuffer = mMaxBufferSize
                    - (int)((audioBandwidth + subtitleBandwidth) * minBufferTimeSeconds / 8);
            long availableBandwidthFromBuffer = availableBuffer * 8 / minBufferTimeSeconds;
            if (availableBandwidthFromBuffer < availableBandwidth) {
                if (LOGS_ENABLED)
                    Log.d(TAG, "available bandwidth capped to " + availableBandwidthFromBuffer
                            + " due to max buffer size");
                availableBandwidth = availableBandwidthFromBuffer;
            }
        }

        if (period.currentAdaptationSet[TrackType.VIDEO.ordinal()] > -1) {
            AdaptationSet videoAdaptationSet = period.adaptationSets
                    .get(period.currentAdaptationSet[TrackType.VIDEO.ordinal()]);

            int videoRepresentations = videoAdaptationSet.representations.size();
            ArrayList<Integer> sortedRepresentations = new ArrayList<>(videoRepresentations);
            for (int i = 0; i < videoRepresentations; i++) {
                if (!videoAdaptationSet.representations.get(i).selected) {
                    continue;
                }
                if (sortedRepresentations.size() == 0) {
                    sortedRepresentations.add(i);
                } else {
                    int representationBandwidth =
                            videoAdaptationSet.representations.get(i).bandwidth;
                    boolean inserted = false;
                    for (int j = 0; j < sortedRepresentations.size(); j++) {
                        int currentRepresentation = sortedRepresentations.get(j);
                        if (representationBandwidth < videoAdaptationSet.
                                representations.get(currentRepresentation).bandwidth) {
                            sortedRepresentations.add(j, i);
                            inserted = true;
                            break;
                        }
                    }
                    if (!inserted) {
                        sortedRepresentations.add(i);
                    }
                }
            }

            int currentVideoRepresentation = selectedRepresentations[TrackType.VIDEO.ordinal()];
            int currentSelectedBandwidth = 0;
            if (currentVideoRepresentation > -1) {
                currentSelectedBandwidth = videoAdaptationSet.representations
                        .get(currentVideoRepresentation).bandwidth;
            }

            int videoRepresentation = -1;
            for (int i = sortedRepresentations.size() - 1; i >= 0; i--) {
                Representation representation =
                        videoAdaptationSet.representations.get(sortedRepresentations.get(i));

                if (availableBandwidth > representation.bandwidth) {
                    if (representation.bandwidth > currentSelectedBandwidth
                            && availableBandwidth < (float)representation.bandwidth * 1.3) {
                        if (availableBandwidth < (float)representation.bandwidth * 1.1) {
                            mSwitchUpCounter = 1;
                            mSwitchUpRepresentation = sortedRepresentations.get(i);
                            continue;
                        }

                        if (mSwitchUpRepresentation == sortedRepresentations.get(i)) {
                            if (mSwitchUpCounter < 3) {
                                mSwitchUpCounter++;
                                continue;
                            }
                        } else {
                            mSwitchUpRepresentation = -1;
                            mSwitchUpCounter = 0;
                            continue;
                        }
                    }

                    mSwitchDownCounter = 0;
                    mSwitchDownRepresentation = -1;
                    videoRepresentation = sortedRepresentations.get(i);
                    break;
                } else if (availableBandwidth > representation.bandwidth * 0.9 &&
                        sortedRepresentations.get(i) == currentVideoRepresentation) {
                    if (mSwitchDownRepresentation == sortedRepresentations.get(i)) {
                        if (mSwitchDownCounter < 3) {
                            mSwitchDownCounter++;
                            videoRepresentation = sortedRepresentations.get(i);
                            break;
                        }
                    } else {
                        mSwitchDownRepresentation = sortedRepresentations.get(i);
                        mSwitchDownCounter = 1;
                        videoRepresentation = sortedRepresentations.get(i);
                        break;
                    }
                }
            }

            if (videoRepresentation == -1) {
                videoRepresentation = sortedRepresentations.get(0);
            }

            if (videoRepresentation != currentVideoRepresentation) {
                selectedRepresentations[TrackType.VIDEO.ordinal()] = videoRepresentation;
                representationsChanged = true;
                mSwitchUpRepresentation = -1;
                mSwitchUpCounter = 0;
            }
        }

        return representationsChanged;
    }
}
