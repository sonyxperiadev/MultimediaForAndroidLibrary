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
 * Interface definition of a meta data representation.
 */
public interface MetaData {

    /**
     * Key for mime type.
     */
    public static final String KEY_MIME_TYPE = "mime";

    /**
     * Key for avc configurations.
     */
    public static final String KEY_AVCC = "avcC";

    /**
     * Key for an array with different languages. Corresponds with
     * {@link #KEY_HMMP_TITLE}.
     */
    public static final String KEY_HMMP_TITLE_LANGUAGES = "hmmpTitleLangs";

    /**
     * Key for an array with titles in different languages. Corresponds with
     * {@link #KEY_HMMP_TITLE_LANGUAGES}.
     */
    public static final String KEY_HMMP_TITLE = "hmmpTitle";

    /**
     * Key for an array with different languages. Corresponds with
     * {@link #KEY_HMMP_ICON}.
     */
    public static final String KEY_HMMP_ICON_LANGUAGES = "hmmpIconLangs";

    /**
     * Key for an array with thumbnails. Corresponds with
     * {@link #KEY_HMMP_ICON_LANGUAGES}.
     */
    public static final String KEY_HMMP_ICON = "hmmpIcon";

    /**
     * Key for a hmmp pixel aspect ratio.
     */
    public static final String KEY_HMMP_PIXEL_ASPECT_RATIO = "hmmpPxar";

    /**
     * Key for ipmp data.
     */
    public static final String KEY_IPMP_DATA = "ipmp";

    /**
     * Key for pssh data.
     */
    public static final String KEY_DRM_PSSH_DATA = "pssh";

    /**
     * Key for DRM uuid.
     */
    public static final String KEY_DRM_UUID = "uudm";

    /**
     * Key for width.
     */
    public static final String KEY_WIDTH = "widt";

    /**
     * Key for height.
     */
    public static final String KEY_HEIGHT = "heig";

    /**
     * Key for frame rate of media.
     */
    public static final String KEY_FRAME_RATE = "frmR";

    /**
     * Key for number of channels.
     */
    public static final String KEY_CHANNEL_COUNT = "#chn";

    /**
     * Key for language.
     */
    public static final String KEY_LANGUAGE = "lang";

    /**
     * Key for duration of content.
     */
    public static final String KEY_DURATION = "dura";

    /**
     * Key for title of media.
     */
    public static final String KEY_TITLE = "titl";

    /**
     * Key for album art.
     */
    public static final String KEY_ALBUM = "albu";

    /**
     * Key for artist.
     */
    public static final String KEY_ARTIST = "arti";

    /**
     * Key for album artist.
     */
    public static final String KEY_ALBUM_ARTIST = "aart";

    /**
     * Key for genre.
     */
    public static final String KEY_GENRE = "genr";

    /**
     * Key for year released.
     */
    public static final String KEY_YEAR = "year";

    /**
     * Key for track number.
     */
    public static final String KEY_TRACK_NUMBER = "cdtr";

    /**
     * Key for compilation.
     */
    public static final String KEY_COMPILATION = "cpil";

    /**
     * Key for author.
     */
    public static final String KEY_AUTHOR = "auth";

    /**
     * Key for composer.
     */
    public static final String KEY_COMPOSER = "comp";

    /**
     * Key for writer.
     */
    public static final String KEY_WRITER = "writ";

    /**
     * Key for disc number.
     */
    public static final String KEY_DISC_NUMBER = "dnum";

    /**
     * Key for album art
     */
    public static final String KEY_ALBUM_ART = "albA";

    /**
     * Key for mpd file.
     */
    public static final String KEY_MPD = "mpd";

    /**
     * Key for number of tracks.
     */
    public static final String KEY_NUM_TRACKS = "num_tracks";

    /**
     * Key for JSON data intended for Marlin DRM.
     */
    /*package*/ static final String KEY_MARLIN_JSON = "json_drm";

    /**
     * Key for playready session ID.
     */
    public static final String KEY_PLAYREADY_SESSIONID = "playready_sessionid";

    /**
     * Key for pause available.
     */
    public static final String KEY_PAUSE_AVAILABLE = "pause_available";

    /**
     * Key for seek available.
     */
    public static final String KEY_SEEK_AVAILABLE = "seek_available";

    /**
     * Key for Sample Aspect Ratio Width.
     */
    /*package*/ static final String KEY_SAR_WIDTH = "sar_width";

    /**
     * Key for Sample Aspect Ratio Height.
     */
    /*package*/ static final String KEY_SAR_HEIGHT = "sar_height";

    /**
     * Key for Pixel Aspect horizontal spacing
     */
    public static final String KEY_PASP_HORIZONTAL_SPACING = "paspHSpacing";

    /**
     * Key for Pixel Aspect vertical spacing
     */
    public static final String KEY_PASP_VERTICAL_SPACING = "paspVSpacing";

    /**
     * Key for rotation degrees.
     */
    public static final String KEY_ROTATION_DEGREES = "rotation-degrees";

    /**
     * Key for Sony Mobile camera content flag
     */
    public static final String KEY_IS_CAMERA_CONTENT = "camc";

    /**
     * Gets an integer value from the meta data.
     *
     * @param key of the integer value that is wanted. Keys are specified in
     *            this MetaData interface.
     * @return int corresponding to the key wanted. If not found
     *         Integer.MIN_VALUE is returned.
     */
    public int getInteger(String key);

    /**
     * Gets a long value from the meta data.
     *
     * @param key of the long value that is wanted. Keys are specified in this
     *            MetaData interface.
     * @return long corresponding to the key wanted. If not found Long.MIN_VALUE
     *         is returned.
     */
    public long getLong(String key);

    /**
     * Gets a long float from the meta data.
     *
     * @param key of the float value that is wanted. Keys are specified in this
     *            MetaData interface.
     * @return float corresponding to the key wanted. If not found
     *         Float.MIN_VALUE is returned.
     */
    public float getFloat(String key);

    /**
     * Gets a String value from the meta data.
     *
     * @param key of the String value that is wanted. Keys are specified in this
     *            MetaData interface.
     * @return String corresponding to the key wanted. If not found null is
     *         returned.
     */
    public String getString(String key);

    /**
     * Gets a String value from the meta data.
     *
     * @param key1 of the String value that is wanted. Keys are specified in
     *            this MetaData interface.
     * @param key2 extra parameter for a more specified call.
     * @return String corresponding to the key wanted. If not found null is
     *         returned.
     */
    public String getString(String key1, String key2);

    /**
     * Gets a byte[] from the meta data.
     *
     * @param key of the byte[] that is wanted. Keys are specified in this
     *            MetaData interface.
     * @return byte[] corresponding to the key wanted. If not found null is
     *         returned.
     */
    public byte[] getByteBuffer(String key);

    /**
     * Gets a byte[] from the meta data.
     *
     * @param key1 of the byte[] that is wanted. Keys are specified in this
     *            MetaData interface.
     * @param key2 extra parameter for a more specified call.
     * @return byte[] corresponding to the key wanted. If not found null is
     *         returned.
     */
    public byte[] getByteBuffer(String key1, String key2);

    /**
     * Gets a String[] from the meta data.
     *
     * @param key of the String[] that is wanted. Keys are specified in this
     *            MetaData interface.
     * @return String[] corresponding to the key wanted. If not found null is
     *         returned.
     */
    public String[] getStringArray(String key);

    /**
     * Finds out if the meta data contains a key/value pair.
     *
     * @param key The key asked for.
     * @return True if found, false if not found.
     */
    public boolean containsKey(String key);

}
