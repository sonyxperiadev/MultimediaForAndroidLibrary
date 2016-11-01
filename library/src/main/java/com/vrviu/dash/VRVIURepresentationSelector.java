/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vrviu.dash;

import android.util.Log;

import java.util.ArrayList;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.RepresentationSelector;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.AdaptationSet;
import com.sonymobile.android.media.internal.streaming.mpegdash.DefaultDASHRepresentationSelector;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Period;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 *
 * @author the_g
 */
//public class VRVIURepresentationSelector extends DefaultDASHRepresentationSelector {
public class VRVIURepresentationSelector implements RepresentationSelector {

    private Orientation mOrientation;
    private final DefaultDASHRepresentationSelector mDASHRepresentationSelector;
    private final Pattern mPattern;
    static final String TAG ="VRVIURepresentationSelector";
    
//    public VRVIURepresentationSelector() {
//        
//    }
    
    public VRVIURepresentationSelector(
            MPDParser parser, 
            int maxBufferSize )
    {
        //super(parser, maxBufferSize);
        mDASHRepresentationSelector = new DefaultDASHRepresentationSelector(parser, maxBufferSize);        
        mOrientation = new Orientation();
        mOrientation.setYPR(45,0,0);
        String pattern="yaw= *(-?[\\d]{1,3}) pitch= *(-?[\\d]{1,3}) roll= *(-?[\\d]{1,3})";
        mPattern = Pattern.compile(pattern);
    }
    
    public void setOrientation( Orientation orientation )
    {
        mOrientation = orientation;
    }

    @Override
    public boolean selectRepresentations(
            long bandwidth, 
            int[] selectedTracks, 
            int[] selectedRepresentations) {
        
        Period period = mDASHRepresentationSelector.mMPDParser.getActivePeriod();
        int count = 0;
        int closestAdaptationSet = -1;
        double closestDistance = Double.MAX_VALUE;

        boolean bResult = false;

        Orientation selectedOrientation = new Orientation();

        for( int i=0; i<period.adaptationSets.size(); i++ )
        {
            AdaptationSet adaptationSet = period.adaptationSets.get(i);
            if( adaptationSet.type==TrackType.VIDEO )
            {
                if( adaptationSet.viewpoint!=null )
                {
                    //Log.i( TAG, "adaptationSet:[" + i + "]" + adaptationSet.viewpoint );
                    Matcher m = mPattern.matcher(adaptationSet.viewpoint);
                    if (m.find()) {
                        try {
                            double yaw = Double.parseDouble(m.group(1));
                            double pitch = Double.parseDouble(m.group(2));
                            double roll = Double.parseDouble(m.group(3));

                            Boolean mono = (roll == -1);

                            if(mono)
                                roll = 0;

                            Orientation viewpointOrientation = new Orientation();
                            viewpointOrientation.setYPR(yaw,pitch,roll);

                            double viewVector[] = mOrientation.getViewVector();
                            double viewpointVector[] = viewpointOrientation.getViewVector();

                            double viewDelta[] = {viewVector[0] - viewpointVector[0], viewVector[1] - viewpointVector[1], viewVector[2] - viewpointVector[2]};
                            double distance = viewDelta[0]*viewDelta[0] + viewDelta[1]*viewDelta[1] + viewDelta[2]*viewDelta[2];

                            if(!mono) {
                                double iPVector[] = mOrientation.getInterpupillaryVector();
                                double viewpointIPVector[] = viewpointOrientation.getInterpupillaryVector();

                                double ipViewDelta[] = {iPVector[0] - viewpointIPVector[0], iPVector[1] - viewpointIPVector[1], iPVector[2] - viewpointIPVector[2]};
                                distance += ipViewDelta[0]*ipViewDelta[0] + ipViewDelta[1]*ipViewDelta[1] + ipViewDelta[2]*ipViewDelta[2];

                            };

                            if (distance < closestDistance) {
                                closestDistance = distance;
                                closestAdaptationSet = i;
                                selectedOrientation.setYPR(yaw, pitch, roll);
                            }
                        }
                        catch(NumberFormatException e)
                        {
                        }
                    }
                }
                else
                {
                    Log.i( TAG, "No adaptation viewpoint defined" );
                }
                count++;
            }
        }
        if( closestAdaptationSet>-1 )
        {
            AdaptationSet adaptationSet = period.adaptationSets.get(closestAdaptationSet);
            Log.v( TAG, "Selected adaptation set "+closestAdaptationSet+" orientation="+selectedOrientation.toString()+" target orientation:"+mOrientation.toString() );
            period.currentAdaptationSet[TrackType.VIDEO.ordinal()] = closestAdaptationSet;
        }
        bResult = mDASHRepresentationSelector.selectRepresentations(bandwidth, selectedTracks, selectedRepresentations);

        return bResult;
    }   

    @Override
    public void selectDefaultRepresentations(int[] selectedTracks, TrackInfo[] trackInfo, int[] selectedRepresentations) {
        mDASHRepresentationSelector.selectDefaultRepresentations( selectedTracks, trackInfo, selectedRepresentations );
    }
}
