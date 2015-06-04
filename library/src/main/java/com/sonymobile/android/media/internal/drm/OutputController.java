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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import com.sonymobile.android.media.MediaPlayer.OutputControlInfo;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MetaDataImpl;

public class OutputController {

    private static final boolean DEBUG_ENABLED = Configuration.DEBUG || false;

    private static final int MSG_UPDATE = 1;

    private static final int MSG_RELEASE = 2;

    private static final int HDCP_DELAY = 1000;

    private final static String TAG = "OutputController";

    private OnOutputControllerUpdateListener mOutputControllerUpdateListener;

    private HashMap<String, String> mDrmInfo;

    private DisplayManager mDisplayManager;

    private final DisplayListenerCallback mDisplayListenerCallback;

    private boolean mBlockWifDisplay;

    private boolean mBlockHDMI;

    private LicenseInfo mLicenseInfo;

    private Handler mEventHandler;

    @SuppressLint("HandlerLeak")
    private class EventHandler extends Handler {

        private final OutputController mOutputController;

        public EventHandler(OutputController outputController, DisplayManager displayManager) {
            mOutputController = outputController;
            mDisplayManager = displayManager;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    if (msg.arg1 == Display.DEFAULT_DISPLAY) {
                        //When coming from play and resume
                        //No new display is added, check restrictions for existing displays
                        Display[] displays = mDisplayManager.getDisplays();
                        for (Display display : displays) {
                            if (display.isValid()) {
                                mOutputController.checkVideoOutputRestriction(display);
                            }
                        }
                    } else {
                        //When coming from callback onDisplayAdded
                        //A new display is added, check restrictions on that one
                        final Display presentationDisplay = mDisplayManager.getDisplay(msg.arg1);
                        mOutputController.checkVideoOutputRestriction(presentationDisplay);
                    }
                    break;

                case MSG_RELEASE:
                    mDisplayManager.unregisterDisplayListener(mDisplayListenerCallback);
                    removeCallbacksAndMessages(null);
                    break;
                default:
                    if (DEBUG_ENABLED) Log.w(TAG, "Unknown message");
                    break;
            }

            super.handleMessage(msg);
        }
    }

    public OutputController(Context context, HashMap<String, String> drmInfo,
            OnOutputControllerUpdateListener listener) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        mOutputControllerUpdateListener = listener;

        mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        if (mDisplayManager == null) {
            throw new RuntimeException("Failed to create displaymanager");
        }
        mEventHandler = new EventHandler(this, mDisplayManager);
        mDisplayListenerCallback = new DisplayListenerCallback();
        mDisplayManager.registerDisplayListener(mDisplayListenerCallback, mEventHandler);

        // Initialize license info and presentation
        mLicenseInfo = new LicenseInfo();
        mDrmInfo = drmInfo;

        initLicenseInfo(drmInfo); // Make sure the license is updated
        setRestrictions(mLicenseInfo);

        OutputControlInfo outputControlInfo = new OutputControlInfo();
        outputControlInfo.info = new MetaDataImpl();
        Iterator<Entry<String, String>> iterator = mDrmInfo.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            ((MetaDataImpl)outputControlInfo.info).addValue(entry.getKey(),
                    entry.getValue());
        }

        if (mOutputControllerUpdateListener != null) {
            mOutputControllerUpdateListener.onOutputControlInfo(outputControlInfo);
        }
    }

    /**
     * Release this control and stop listening for routing changes.
     */
    public void release() {
        mEventHandler.obtainMessage(MSG_RELEASE).sendToTarget();
    }

    /**
     * Updates the OutputController on the present DRM session.
     */
    public void update() {
        mEventHandler.obtainMessage(MSG_UPDATE).sendToTarget();
    }

    /**
     * Updates the OutputController on the present display status.
     * Used by the nested class DisplayListenerCallback.
     * @param displayId Id of the display that needs update.
     */
    private void updateDisplay(int displayId) {
        Message msg = mEventHandler.obtainMessage(MSG_UPDATE, displayId, 0);
        //Delay message to ensure that hdcp flag is set.
        mEventHandler.sendMessageAtTime(msg, SystemClock.uptimeMillis() + HDCP_DELAY);
    }

    private void setRestrictions(LicenseInfo licenseInfo) {

        mBlockHDMI = true;
        mBlockWifDisplay = true;

        // Check if HDMI (MHL) is allowed.
        if (licenseInfo.getOplUncompressedDigitalAudio() <=
                OplValues.HDMI_UNCOMPRESSED_AUDIO_LIMIT &&
                licenseInfo.getOplCompressedDigitalAudio() <=
                OplValues.HDMI_COMPRESSED_AUDIO_LIMIT &&
                licenseInfo.getOplUncompressedDigitalVideo() <=
                OplValues.UNCOMPRESSED_DIGITAL_VIDEO_LIMIT) {
            mBlockHDMI = false;
        }

        // Check if WIFIDISPLAY (MIRRORING) is allowed.
        if (licenseInfo.getPlayEnable()) {
            mBlockWifDisplay = false;
        }

        if (DEBUG_ENABLED)
            Log.d(TAG, "Restrictions set HDMI: " + mBlockHDMI + " WIFI: " + mBlockWifDisplay);
    }

    private void initLicenseInfo(HashMap<String, String> drmInfo) {

        int oplUncompressedDigitalAudioVal = 0;

        int oplCompressedDigitalAudioVal = 0;

        int oplUncompressedDigitalVideoVal = 0;

        int oplCompressedDigitalVideoVal = 0;

        boolean playEnablerVal = true;

        boolean ringtoneAllowedVal = true;

        if (mDrmInfo != null) {
            for (Map.Entry<String, String> entry : drmInfo.entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase(LicenseInfo.OPL_COMPRESSED_DIGITAL_AUDIO)) {
                    String value = entry.getValue();
                    int opl_value;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplCompressedDigitalAudioVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_UNCOMPRESSED_DIGITAL_AUDIO)) {
                    String value = entry.getValue();
                    int opl_value;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplUncompressedDigitalAudioVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_COMPRESSED_DIGITAL_VIDEO)) {
                    String value = entry.getValue();
                    int opl_value;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplCompressedDigitalVideoVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_UNCOMPRESSED_DIGITAL_VIDEO)) {
                    String value = entry.getValue();
                    int opl_value;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplUncompressedDigitalVideoVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.PLAYENABLER_TYPE)) {
                    String value = entry.getValue();
                    playEnablerVal = value.equalsIgnoreCase("true");
                } else if (key.equalsIgnoreCase(LicenseInfo.VALID_RINGTONE_LICENSE)) {
                    String value = entry.getValue();
                    ringtoneAllowedVal = value.equalsIgnoreCase("true");
                }
            }
        }
        mLicenseInfo.setOplValues(oplCompressedDigitalAudioVal, oplUncompressedDigitalAudioVal,
                oplCompressedDigitalVideoVal, oplUncompressedDigitalVideoVal);
        mLicenseInfo.setPlayEnable(playEnablerVal);
        mLicenseInfo.setAllowRingtone(ringtoneAllowedVal);
    }

    private void checkVideoOutputRestriction(Display display) {

        if (display == null || (!mBlockWifDisplay && !mBlockHDMI)) {
            return;
        }

        try {
            int typeHdmi = Display.class.getDeclaredField("TYPE_HDMI").getInt(null);
            int typeWfd = Display.class.getDeclaredField("TYPE_WIFI").getInt(null);

            Method getTypeMethod = Display.class.getMethod("getType", (Class<?>[])null);

            int type = (Integer)getTypeMethod.invoke(display, (Object[])null);

            OutputControlEvent event = new OutputControlEvent(mLicenseInfo);
            int hdcpEnabled = display.getFlags() & Display.FLAG_SUPPORTS_PROTECTED_BUFFERS;

            if ((mBlockWifDisplay || hdcpEnabled == 0) && type == typeWfd) {
                notifyListener(OutputControlEvent.OUTPUT_EXTERNAL_WIFI_RESTRICTED, event);
            } else if ((mBlockHDMI || hdcpEnabled == 0) && type == typeHdmi) {
                notifyListener(OutputControlEvent.OUTPUT_EXTERNAL_HDMI_RESTRICTED, event);
            }

        } catch (IllegalAccessException e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        } catch (IllegalArgumentException e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        } catch (NoSuchFieldException e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        } catch (NoSuchMethodException e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        } catch (InvocationTargetException e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        } catch (Exception e) {
            if (DEBUG_ENABLED) Log.e(TAG, "Failed to call hidden api", e);
        }
    }

    private synchronized void notifyListener(int action, OutputControlEvent event) {

        if (DEBUG_ENABLED) Log.d(TAG, "Notify Listeners Action: " + action);
        if (mOutputControllerUpdateListener == null) {
            return;
        }

        switch (action) {
            case OutputControlEvent.OUTPUT_EXTERNAL_HDMI_RESTRICTED:
                mOutputControllerUpdateListener.onExternalHDMIRestricted(event);
                break;
            case OutputControlEvent.OUTPUT_EXTERNAL_WIFI_RESTRICTED:
                mOutputControllerUpdateListener.onExternalWifiRestricted(event);
                break;
            default:
                break;
        }

    }

    private final class DisplayListenerCallback implements DisplayManager.DisplayListener {

        @Override
        public void onDisplayAdded(int id) {
            updateDisplay(id);
        }

        @Override
        public void onDisplayRemoved(int id) {
            //Ignore if displays are removed
        }

        @Override
        public void onDisplayChanged(int id) {
            //Ignore display change callbacks
        }
    }
}
