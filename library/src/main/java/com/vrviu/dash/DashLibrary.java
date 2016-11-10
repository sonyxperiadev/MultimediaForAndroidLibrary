/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.vrviu.dash;

import java.util.ArrayList;
import com.vrviu.dash.Orientation;
import com.sonymobile.android.media.internal.streaming.mpegdash.MPDParser.Representation;

class VRVIURepresentation extends Representation {
    public Orientation orientation;

    public VRVIURepresentation() 
    {
        orientation = new Orientation();
    }
}
