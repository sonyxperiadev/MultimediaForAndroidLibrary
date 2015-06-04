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

import com.sonymobile.android.media.testmediaplayer.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class OnResumePlaybackDialog {

    private final Context mContext;
    private AlertDialog mAlertDialog;

    public OnResumePlaybackDialog(Context cont) {
        mContext = cont;
    }

    public void createDialog(DialogInterface.OnClickListener listener) {

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.resume_playing_title).setMessage(R.string.resume_playing_text)
                .setPositiveButton(R.string.yes, listener).setNegativeButton(R.string.no, listener);
        mAlertDialog = builder.create();
        mAlertDialog.show();
    }

    public AlertDialog getDialog() {
        return mAlertDialog;
    }
}
