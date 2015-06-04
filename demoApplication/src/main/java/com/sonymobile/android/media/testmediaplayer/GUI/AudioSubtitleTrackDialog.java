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

package com.sonymobile.android.media.testmediaplayer.GUI;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class AudioSubtitleTrackDialog {
    public static final String CHOOSE_SUBTITLE_TRACK = "choose subtitle track";
    public static final String CHOOSE_AUDIO_TRACK = "choose audio track";

    private final Context mContext;
    private AlertDialog levelDialog;

    public AudioSubtitleTrackDialog(Context cont){
        mContext = cont;
    }

    // Creating and Building the Dialog
    public void createDialog(String choice, String[] trackInfo, int pos,
            DialogInterface.OnClickListener listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        if(choice.equals(CHOOSE_AUDIO_TRACK)){
            builder.setTitle("Select Audio Track");
        }else{
            builder.setTitle("Select Subtitle Track");
        }
        builder.setSingleChoiceItems(trackInfo, pos, listener);
        levelDialog = builder.create();
        levelDialog.show();
    }

    public AlertDialog getDialog() {
        return levelDialog;
    }
}
