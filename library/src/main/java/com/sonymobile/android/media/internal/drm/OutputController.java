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
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteGroup;
import android.media.MediaRouter.RouteInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import com.sonymobile.android.media.MediaPlayer.OutputControlInfo;
import com.sonymobile.android.media.internal.Configuration;
import com.sonymobile.android.media.internal.MetaDataImpl;

public class OutputController {

    private static final boolean DEBUG_ENABLED = Configuration.DEBUG || false;

    private static final int MSG_UPDATE = 1;

    private static final int MSG_RELEASE = 2;

    private final static String TAG = "OutputController";

    private Context mContext;

    private OnOutputControllerUpdateListener mOutputControllerUpdateListener;

    private HashMap<String, String> mDrmInfo;

    private MediaRouter mMediaRouter;

    private final MediaRouter.Callback mMediaRouterCallback;

    private boolean mBlockWifDisplay;

    private boolean mBlockHDMI;

    private boolean mBlockAudioJack;

    private LicenseInfo mLicenseInfo;

    private Handler mEventHandler;

    @SuppressLint("HandlerLeak")
    private class EventHandler extends Handler {

        private MediaRouter mMediaRouter;

        private OutputController mOutputController;

        public EventHandler(OutputController outputController, MediaRouter mediaRouter) {
            mOutputController = outputController;
            mMediaRouter = mediaRouter;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    if (mMediaRouter != null) {
                        RouteInfo routeInfo = mMediaRouter
                                .getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

                        if (routeInfo == null) {
                            // No information about routing.
                            return;
                        }

                        final Display presentationDisplay = routeInfo.getPresentationDisplay();

                        mOutputController.checkVideoOutputRestriction(presentationDisplay);
                        mOutputController.checkAudioOutputRestriction(presentationDisplay);

                    }
                    break;

                case MSG_RELEASE:
                    mMediaRouter.removeCallback(mMediaRouterCallback);
                    // TODO: Keep track of state and set the mMediaRouter to
                    // null as well.
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
        mContext = context;
        mOutputControllerUpdateListener = listener;

        mMediaRouter = (MediaRouter)mContext.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        if (mMediaRouter == null) {
            throw new RuntimeException("Failed to create mediarouter");
        }

        mEventHandler = new EventHandler(this, mMediaRouter);
        mMediaRouterCallback = new MediaRouterCallback();

        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS);

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

    private void setRestrictions(LicenseInfo licenseInfo) {

        mBlockAudioJack = true;
        mBlockHDMI = true;
        mBlockWifDisplay = true;

        // Check if HEADPHONES is allowed.
        if (licenseInfo.getOplUncompressedDigitalAudio() <=
                OplValues.HEADPHONE_UNCOMPRESSED_AUDIO_LIMIT &&
                licenseInfo.getOplCompressedDigitalAudio() <=
                OplValues.HEADPHONE_COMPRESSED_AUDIO_LIMIT &&
                licenseInfo.getOplUncompressedDigitalVideo() <=
                OplValues.UNCOMPRESSED_DIGITAL_VIDEO_LIMIT) {
            mBlockAudioJack = false;
        }

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
            Log.d(TAG, "Restrictions set HDMI: " + mBlockHDMI + " WIFI: " + mBlockWifDisplay
                    + " AudioJack: " + mBlockAudioJack);
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
                    int opl_value = 0;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplCompressedDigitalAudioVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_UNCOMPRESSED_DIGITAL_AUDIO)) {
                    String value = entry.getValue();
                    int opl_value = 0;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplUncompressedDigitalAudioVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_COMPRESSED_DIGITAL_VIDEO)) {
                    String value = entry.getValue();
                    int opl_value = 0;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplCompressedDigitalVideoVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.OPL_UNCOMPRESSED_DIGITAL_VIDEO)) {
                    String value = entry.getValue();
                    int opl_value = 0;
                    try {
                        opl_value = Integer.parseInt(value, 10);
                        oplUncompressedDigitalVideoVal = opl_value;
                    } catch (NumberFormatException ne) {
                        if (DEBUG_ENABLED) Log.d(TAG, "Numberformat Exception");
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.PLAYENABLER_TYPE)) {
                    String value = entry.getValue();
                    if (value.equalsIgnoreCase("true")) {
                        playEnablerVal = true;
                    } else {
                        playEnablerVal = false;
                    }
                } else if (key.equalsIgnoreCase(LicenseInfo.VALID_RINGTONE_LICENSE)) {
                    String value = entry.getValue();
                    if (value.equalsIgnoreCase("true")) {
                        ringtoneAllowedVal = true;
                    } else {
                        ringtoneAllowedVal = false;
                    }
                }
            }
        }
        mLicenseInfo.setOplValues(oplCompressedDigitalAudioVal, oplUncompressedDigitalAudioVal,
                oplCompressedDigitalVideoVal, oplUncompressedDigitalVideoVal);
        mLicenseInfo.setPlayEnable(playEnablerVal);
        mLicenseInfo.setAllowRingtone(ringtoneAllowedVal);
    }

    @SuppressWarnings("deprecation")
    private boolean isRoutedToHeadphones(Display currentDisplay) {

        AudioManager audioManager = (AudioManager)mContext
                .getSystemService(Context.AUDIO_SERVICE);

        // isWiredHeadsetOn only gives information about connect and not
        // routing. That is why it is deprecated.
        boolean wiredHeadsetConnected = audioManager.isWiredHeadsetOn();

        if (currentDisplay == null) {
            // If we play on local screen this might be null. Just return the
            // headset connection status.
            return wiredHeadsetConnected;
        }

        boolean defaultDisplay = currentDisplay.getDisplayId() == Display.DEFAULT_DISPLAY;

        return (wiredHeadsetConnected && defaultDisplay);
    }

    private void checkAudioOutputRestriction(Display display) {
        OutputControlEvent event = new OutputControlEvent(mLicenseInfo);

        boolean routedToHeadPhones = isRoutedToHeadphones(display);

        if (mBlockAudioJack && routedToHeadPhones) {
            notifyListener(OutputControlEvent.OUTPUT_AUDIO_HEADPHONES_RESTRICTED, event, null);
        }
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

            if (mBlockWifDisplay && type == typeWfd) {
                notifyListener(OutputControlEvent.OUTPUT_EXTERNAL_WIFI_RESTRICTED, event, null);
            } else if (mBlockHDMI && type == typeHdmi) {
                notifyListener(OutputControlEvent.OUTPUT_EXTERNAL_HDMI_RESTRICTED, event, null);
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

    private synchronized void notifyListener(int action, OutputControlEvent event,
            RouteInfo route) {

        if (DEBUG_ENABLED) Log.d(TAG, "Notify Listeners Action: " + action);
        if (mOutputControllerUpdateListener == null) {
            return;
        }

        switch (action) {
            case OutputControlEvent.OUTPUT_AUDIO_HEADPHONES_RESTRICTED:
                mOutputControllerUpdateListener.onHeadphonesRestricted(event);
                break;
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

    private final class MediaRouterCallback extends MediaRouter.SimpleCallback {

        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            update();
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            update();
        }

        @Override
        public void onRouteGrouped(MediaRouter router, RouteInfo info,
                RouteGroup group, int index) {
            update();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            update();
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type,
                RouteInfo info) {
            update();
        }

        @Override
        public void onRouteUngrouped(MediaRouter router, RouteInfo info,
                RouteGroup group) {
            update();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, int type,
                RouteInfo info) {
            update();
        }
    }
}
