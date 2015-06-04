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

package com.sonymobile.android.media.internal;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.internal.drm.DrmUUID;

public class Util {

    private static final boolean LOGS_ENABLED = Configuration.DEBUG || false;

    private static final String TAG = "Util";

    public final static String PLAY_READY_SYSTEM_ID = "9A04F07998404286AB92E65BE0885F95";

    public final static String MARLIN_SYSTEM_ID = "69F908AF481646EA910CCD5DCCCB0A3A";

    public final static String MARLIN_SUBTITLE_CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";

    private final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public final static String EXTERNAL_DIR = Environment.getExternalStorageDirectory().getPath();

    // common keys
    private final static String INIT_DATA_KEY_TITLE = "title";

    private final static String INIT_DATA_KEY_PROPERTIES = "properties";

    private final static String INIT_DATA_KEY_PROP_NAME = "name";

    private final static String INIT_DATA_KEY_PROP_VERSION = "version";

    private final static String INIT_DATA_KEY_PROCESSTYPE = "process_type";

    private final static String INIT_DATA_KEY_DATATYPE = "data_type";

    // IPMP
    private final static String INIT_DATA_KEY_IPMP = "ipmp";

    private final static String INIT_DATA_KEY_SINF = "sinf";

    // CENC
    private final static String INIT_DATA_KEY_CENC = "cenc";

    private final static String INIT_DATA_KEY_PSSH = "pssh";

    private final static String INIT_DATA_KEY_KIDS = "kids";

    // Common data
    private final static String INIT_DATA_TITLE = "marlincdm_initData";

    private static final String CURRENT_VERSION = "1.0";

    private static final String PROCESS_TYPE_ANDROID = "android";

    private static final String DATA_TYPE_CENC = "cenc";

    private static final String DATA_TYPE_IPMP = "ipmp";

    private static final String PROPERTY_NAME_INIT_DATA = "getkeyRequest_initdata";

    // Playback speed
    public static final float DEFAULT_PLAYBACK_SPEED = 1.0f;

    public static final float MIN_PLAYBACK_SPEED = 0.5f;

    public static final float MAX_PLAYBACK_SPEED = 2.0f;

    public static final int DEFAULT_MESSAGE_DELAY = 10;

    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, 0, -1);
    }

    public static String bytesToHex(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            return null;
        }
        if (length < 0) {
            length = bytes.length;
        }
        if (offset + length > bytes.length) {
            if (LOGS_ENABLED)
                Log.e(TAG, "not enough bytes (" + bytes.length
                        + ") for desired parameters (offset = " + offset + ", length = " + length
                        + ")");
            return null;
        }
        char[] hexChars = new char[length * 2];

        for (int j = 0; j < length; j++) {
            int v = bytes[j + offset] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] uuidStringToByteArray(String uuidString) {
        byte[] signed = new BigInteger(uuidString, 16).toByteArray();

        if (signed.length == 16) {
            return signed;
        }

        byte[] unsigned = new byte[uuidString.length() / 2];
        //BigInteger returns a signed byte array so we need to get rid of the signing.
        System.arraycopy(signed, 1, unsigned, 0, unsigned.length);
        return unsigned;
    }

    public static String getMarlinPSSHTable(byte[] pssh, byte[][] kids) throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject property = new JSONObject();
        JSONObject cenc = new JSONObject();
        JSONArray kidsArray = new JSONArray();

        root.put(INIT_DATA_KEY_TITLE, INIT_DATA_TITLE);
        property.put(INIT_DATA_KEY_PROP_NAME, PROPERTY_NAME_INIT_DATA);
        property.put(INIT_DATA_KEY_PROP_VERSION, CURRENT_VERSION);
        property.put(INIT_DATA_KEY_PROCESSTYPE, PROCESS_TYPE_ANDROID);
        property.put(INIT_DATA_KEY_DATATYPE, DATA_TYPE_CENC);

        for (byte[] kid : kids) {
            kidsArray.put(bytesToHex(kid));
        }
        cenc.put(INIT_DATA_KEY_PSSH, bytesToHex(pssh));
        cenc.put(INIT_DATA_KEY_KIDS, kidsArray);

        property.put(INIT_DATA_KEY_CENC, cenc);
        root.put(INIT_DATA_KEY_PROPERTIES, property);

        return root.toString();
    }

    public static String getJSONIPMPData(byte[] sinfData) throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject property = new JSONObject();
        JSONObject sinfJson = new JSONObject();

        root.put(INIT_DATA_KEY_TITLE, INIT_DATA_TITLE);
        property.put(INIT_DATA_KEY_PROP_NAME, PROPERTY_NAME_INIT_DATA);
        property.put(INIT_DATA_KEY_PROP_VERSION, CURRENT_VERSION);
        property.put(INIT_DATA_KEY_PROCESSTYPE, PROCESS_TYPE_ANDROID);
        property.put(INIT_DATA_KEY_DATATYPE, DATA_TYPE_IPMP);
        sinfJson.put(INIT_DATA_KEY_SINF, bytesToHex(sinfData));
        property.put(INIT_DATA_KEY_IPMP, sinfJson);
        root.put(INIT_DATA_KEY_PROPERTIES, property);

        return root.toString();
    }

    public static MediaCrypto createMediaCrypto(MediaFormat format) throws MediaCryptoException {
        if (format.containsKey(MetaData.KEY_PLAYREADY_SESSIONID)) {
            ByteBuffer buffer = format.getByteBuffer(MetaData.KEY_PLAYREADY_SESSIONID);
            if (buffer != null) {
                return new MediaCrypto(DrmUUID.PLAY_READY, buffer.array());
            }
        } else if (format.containsKey(MetaData.KEY_MARLIN_JSON)) {
            byte[] marlinJson;
            try {
                marlinJson = format.getString(MetaData.KEY_MARLIN_JSON).getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                if (LOGS_ENABLED) Log.e(TAG, "Unsupported encoding", e);
                return null;
            }

            return new MediaCrypto(DrmUUID.MARLIN, marlinJson);
        }

        return null;
    }

}
