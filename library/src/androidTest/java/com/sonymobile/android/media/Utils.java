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

import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import junit.framework.Assert;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

public class Utils {
    public static final String LOGTAG = "MediaPlayerLib_Test";

    private static final String classTAG = "Utils";

    private static String mSDCardPath = null;

    private final static String mContentPath = "MultimediaFrameworkTestContent/";

    private final static String mAppPath = "/storage/sdcard0/Android/data/com.sonyericsson.mediaframeworktest/files/";

    static {
        String sec_storage = System.getenv("SECONDARY_STORAGE");
        if (sec_storage != null) {
            String[] storages = sec_storage.split(":");
            for (String path : storages) {
                if (path != null) {
                    File extCard = new File(path + "/" + mContentPath);
                    if (extCard.exists() && extCard.isDirectory()) {
                        mSDCardPath = path + "/";
                        break;
                    }
                }
            }
        }

        if (mSDCardPath == null) {
            mSDCardPath = Environment.getExternalStorageDirectory().getPath() + "/";
        }
        Log.d(LOGTAG, "SD card path is " + mSDCardPath);

        // Make sure that the output path exists
        File outdir = new File(getOutputPath());
        if (!outdir.isDirectory()) {
            if (!outdir.mkdirs()) {
                Log.w(LOGTAG, "Failed to create output directory " + outdir.getAbsolutePath());
            }
        }

        // Log name of content "version"
        File contentDir = new File(getContentPath());
        String[] contentFiles = contentDir.list(new FilenameFilter() {

            public boolean accept(File dir, String filename) {
                return filename.startsWith("_____LAST UPDATE");
            }

        });
        if (contentFiles != null && contentFiles.length > 0) {
            for (String s : contentFiles) {
                Log.d(LOGTAG, "Found test content version: " + s);
            }
        } else {
            Log.d(LOGTAG, "Test content version NOT FOUND");
        }
    }

    public static String getMSDCardPath() {
        return mSDCardPath;
    }

    public static void loge(String msg) {
        Log.e(LOGTAG, logPrefix() + msg);
    }

    public static void logw(String msg) {
        Log.w(LOGTAG, logPrefix() + msg);
    }

    public static void logi(String msg) {
        Log.i(LOGTAG, logPrefix() + msg);
    }

    public static void logd(String msg) {
        Log.d(LOGTAG, logPrefix() + msg);
    }

    public static void logv(String msg) {
        Log.v(LOGTAG, logPrefix() + msg);
    }

    public static void loge(Object clazz, String methodName, String msg) {
        Log.e(LOGTAG, logPrefix(clazz, methodName) + msg);
    }

    public static void logw(Object clazz, String methodName, String msg) {
        Log.w(LOGTAG, logPrefix(clazz, methodName) + msg);
    }

    public static void logi(Object clazz, String methodName, String msg) {
        Log.i(LOGTAG, logPrefix(clazz, methodName) + msg);
    }

    public static void logd(Object clazz, String methodName, String msg) {
        Log.d(LOGTAG, logPrefix(clazz, methodName) + msg);
    }

    public static void logv(Object clazz, String methodName, String msg) {
        Log.v(LOGTAG, logPrefix(clazz, methodName) + msg);
    }

    public static void loge(String className, String methodName, String msg) {
        Log.e(LOGTAG, logPrefix(className, methodName) + msg);
    }

    public static void logw(String className, String methodName, String msg) {
        Log.w(LOGTAG, logPrefix(className, methodName) + msg);
    }

    public static void logi(String className, String methodName, String msg) {
        Log.i(LOGTAG, logPrefix(className, methodName) + msg);
    }

    public static void logd(String className, String methodName, String msg) {
        Log.d(LOGTAG, logPrefix(className, methodName) + msg);
    }

    public static void logv(String className, String methodName, String msg) {
        Log.v(LOGTAG, logPrefix(className, methodName) + msg);
    }

    /**
     * Method to create log prefix strings. It creates the prefix from the stack
     * trace of the current thread. Warning!! this method is dependent of call
     * chain Must be called from top level method in this class
     *
     * @return prefix string for log print outs
     */
    private static String logPrefix() {
        StackTraceElement element = Thread.currentThread().getStackTrace()[4];
        String classNameLong = element.getClassName();
        String className = classNameLong.substring(classNameLong.lastIndexOf(".") + 1);
        String methodName = element.getMethodName();
        int lineNr = element.getLineNumber();
        return "[" + className + " - " + methodName + " :" + lineNr + "] ";
    }

    /**
     * Method to create log prefix strings.
     *
     * @param clazz
     * @param methodName
     * @return prefix string for log print outs
     */
    private static String logPrefix(Object clazz, String methodName) {
        String className = clazz.getClass().getSimpleName();
        return logPrefix(className, methodName);
    }

    /**
     * Method to create log prefix strings.
     *
     * @param className
     * @param methodName
     * @return prefix string for log print outs
     */
    private static String logPrefix(String className, String methodName) {
        return "[" + className + " - " + methodName + "] ";
    }

    /**
     * Returns the path to the content
     */
    public static String getContentPath() {
        return mSDCardPath + mContentPath;
    }

    /**
     * Returns the path where created files (recording etc.) should be stored
     */
    public static String getOutputPath() {
        return mAppPath;
    }

    /**
     * Returns a list of Uris matching the specific track title.
     */

    private static List<Uri> getUriList(String trackTitle,
            String trackColumnId,
            String titleColumnName,
            Uri uri,
            String sortOrder,
            Context context) {

        ArrayList<Uri> list = new ArrayList<Uri>();
        int trackId = -1;

        String[] columns = {
                trackColumnId
        };

        ContentResolver resolver = context.getContentResolver();

        String query = titleColumnName + "=" + DatabaseUtils.sqlEscapeString(trackTitle);

        Cursor cursor = resolver.query(uri, columns,
                query, null, sortOrder);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                do {
                    trackId = cursor.getInt(cursor.getColumnIndex(trackColumnId));
                    Uri newUri = Uri.withAppendedPath(uri,
                            String.valueOf(trackId));

                    list.add(newUri);
                } while (cursor.moveToNext());
            } finally {
                cursor.close();
            }
        }

        Assert.assertTrue("Could not find track in database: " + trackTitle, trackId != -1);
        return list;

    }

    /**
     * Returns a list of audio Uris matching the specific track title.
     */

    public static List<Uri> getAudioUriList(String trackTitle, Context context) {

        return getUriList(trackTitle,
                BaseColumns._ID,
                MediaColumns.TITLE,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                AudioColumns.TRACK,
                context);
    }

    /**
     * Returns a list of video Uris matching the specific track title.
     */

    public static List<Uri> getVideoUriList(String trackTitle, Context context) {

        return getUriList(trackTitle,
                BaseColumns._ID,
                MediaColumns.TITLE,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaColumns.TITLE,
                context);
    }

    /**
     * Returns a list of image Uris matching the specific track title.
     */

    public static List<Uri> getImageUriList(String trackTitle, Context context) {

        return getUriList(trackTitle,
                BaseColumns._ID,
                MediaColumns.TITLE,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaColumns.TITLE,
                context);
    }

    /**
     * Returns the Uri for a specific track.
     */
    public static Uri getAudioUri(String track, Context context) {
        int trackId = -1;

        String[] columns = {
                BaseColumns._ID
        };

        ContentResolver resolver = context.getContentResolver();

        String query = MediaColumns.TITLE + "=" + DatabaseUtils.sqlEscapeString(track);

        Cursor mCursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                query, null, AudioColumns.TRACK);
        if (mCursor != null) {
            try {
                if (mCursor.moveToFirst()) {
                    trackId = mCursor.getInt(mCursor.getColumnIndex(BaseColumns._ID));
                }
            } finally {
                mCursor.close();
            }
        }
        Assert.assertTrue("Could not find track in database. ", trackId != -1);

        return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String
                .valueOf(trackId));
    }

    public static Uri getVideoUri(String video, Context context) {
        int trackId = -1;

        String[] columns = {
                BaseColumns._ID
        };

        ContentResolver resolver = context.getContentResolver();

        String query = MediaColumns.TITLE + "=" + DatabaseUtils.sqlEscapeString(video);

        Cursor mCursor = resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns,
                query, null, MediaColumns.TITLE);
        if (mCursor != null) {
            try {
                if (mCursor.moveToFirst()) {
                    trackId = mCursor.getInt(mCursor.getColumnIndex(BaseColumns._ID));
                }
            } finally {
                mCursor.close();
            }
        }
        Assert.assertTrue("Could not find track in database. ", trackId != -1);
        return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String
                .valueOf(trackId));
    }

    public static Uri getImageUri(String image, Context context) {
        int imageId = -1;

        String[] columns = {
                BaseColumns._ID
        };

        ContentResolver resolver = context.getContentResolver();

        String query = MediaColumns.TITLE + "=" + DatabaseUtils.sqlEscapeString(image);

        Cursor mCursor = resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns,
                query, null, MediaColumns.TITLE);
        if (mCursor != null) {
            try {
                if (mCursor.moveToFirst()) {
                    imageId = mCursor.getInt(mCursor.getColumnIndex(BaseColumns._ID));
                }
            } finally {
                mCursor.close();
            }
        }
        Assert.assertTrue("Could not find image in database. ", imageId != -1);
        return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String
                .valueOf(imageId));
    }

    /*
     * Returns the number of entries matching the Id for the URI set as in
     * parameter
     */
    public static int getTableCount(String what, Context context, Uri uri, String columnName) {
        int size = -1;

        String[] columns = {
                columnName
        };

        ContentResolver resolver = context.getContentResolver();

        String query = columnName + "=" + DatabaseUtils.sqlEscapeString(what);

        Cursor mCursor = resolver.query(uri, columns,
                query, null, null);
        if (mCursor != null) {
            try {
                size = mCursor.getCount();
            } finally {
                mCursor.close();
            }
        }
        return size;
    }

    /*
     * The method returns the number of entries matching the id from the audio
     * table
     */
    public static int getAudioCount(String id, Context context) {
        return getTableCount(id, context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                BaseColumns._ID);
    }

    /*
     * The method returns the number of entries matching the id from the video
     * table
     */
    public static int getVideoCount(String id, Context context) {
        return getTableCount(id, context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                BaseColumns._ID);
    }

    /*
     * The method returns the number of entries matching the id from the image
     * table
     */
    public static int getImageCount(String id, Context context) {
        return getTableCount(id, context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                BaseColumns._ID);
    }

    /*
     * The method returns the number of entries matching the data (path) from
     * the audio table
     */
    public static int getAudioPathCount(String path, Context context) {
        return getTableCount(path, context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaColumns.DATA);
    }

    /*
     * The method returns the number of entries matching the data (path) from
     * the video table
     */
    public static int getVideoPathCount(String path, Context context) {
        return getTableCount(path, context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaColumns.DATA);
    }

    /*
     * The method returns the number of entries matching the data (path) from
     * the image table
     */
    public static int getImagePathCount(String path, Context context) {
        return getTableCount(path, context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaColumns.DATA);
    }

    public static String printCpuInfo() {
        String cm = "dumpsys cpuinfo";
        String cpuinfo = null;
        int ch;
        try {
            Process p = Runtime.getRuntime().exec(cm);
            InputStream in = p.getInputStream();
            StringBuffer sb = new StringBuffer(512);
            while ((ch = in.read()) != -1) {
                sb.append((char)ch);
            }
            cpuinfo = sb.toString();
        } catch (IOException e) {
            logv(classTAG, "printCpuInfo", "Exception: " + e.toString());
        }
        return cpuinfo;
    }

    private static final int[] STREAM_TYPE = {
            AudioManager.STREAM_VOICE_CALL, // 0
            AudioManager.STREAM_SYSTEM, // 1
            AudioManager.STREAM_RING, // 2
            AudioManager.STREAM_MUSIC, // 3
            AudioManager.STREAM_ALARM, // 4
            AudioManager.STREAM_NOTIFICATION, // 5
    // AudioManager.STREAM_BLUETOOTH_SCO,// 6
    };

    public static int[] getStreamTypes() {
        return STREAM_TYPE.clone();
    }

    /**
     * Calls String.format(Locale.US, ...).
     *
     * @see String#format(java.util.Locale, String, Object...)
     */
    public static String format(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }

    /**
     * Asserts that expected - delta <= actual <= expected + delta.
     *
     * @param expected interval midpoint
     * @param delta absolute delta, must be >= 0
     * @param actual the value to be checked
     * @param message if not null, it is prepended to the standard failure
     *            message
     */
    public static void assertIntervalAbs(String message,
            int expected, int delta, int actual) {
        if (delta < 0) {
            throw new IllegalArgumentException("delta must be >= 0");
        }

        // Check that calculations would not silently overflow.
        int lowerbound = expected - delta;
        int upperbound = expected + delta;
        if (lowerbound > expected || upperbound < expected) {
            String msg = format("%d +/- %d overflows to [%d, %d]",
                    expected, delta, lowerbound, upperbound);
            throw new IllegalArgumentException(msg);
        }

        long offBy = 0;
        if (actual < lowerbound) {
            // offBy should be < 0 when below lower bound
            offBy = actual - lowerbound;
        } else if (actual > upperbound) {
            offBy = actual - upperbound;
        }

        if (offBy != 0) {
            String failmsg = format(
                    "expected:<%d +/- %d> but was<%d>, off by %+d",
                    expected, delta, actual, offBy);
            if (message != null) {
                failmsg = message + failmsg;
            }
            Assert.fail(failmsg);
        }
    }

    /**
     * Equivalent to assertIntervalAbs(null, expected, delta, actual)
     */
    public static void assertIntervalAbs(int expected, int delta, int actual) {
        assertIntervalAbs(null, expected, delta, actual);
    }

    /**
     * Utility method to check if the file path exists on the SD card.
     *
     * @param path absolute path of a local file.
     */
    public static void assertFilePathExists(String path) {
        File file = null;
        if (path.startsWith(mSDCardPath)) {
            file = new File(path);
            if (!file.exists()) {
                Assert.fail("Not found on sdcard: " + path);
            }
        }
    }

    /**
     * Recursively delete a file or directory including subdirectories. No error
     * is reported to caller if the file or directory does not exist.
     *
     * @param path file or directory to be deleted
     * @return true if all (existing) files and directories were successfully
     *         deleted
     * @see #recursiveDelete(java.io.File)
     */
    public static boolean recursiveDelete(String path) {
        return recursiveDelete(new File(path));
    }

    /**
     * Recursively delete a file or directory including subdirectories. No error
     * is reported to caller if the file or directory does not exist.
     *
     * @param file file or directory to be deleted
     * @return true if all (existing) files and directories were successfully
     *         deleted
     * @see #recursiveDelete(String)
     */
    public static boolean recursiveDelete(File file) {
        if (!file.exists()) {
            logv("Ignoring non existing path " + file.getPath());

            return true;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();

            if (files != null) {
                for (File child : files) {
                    recursiveDelete(child);
                }
            }
        }

        // if file was a directory and any child failed to be deleted
        // the parent (file) will also fail deletion and we'll return the
        // correct value here.
        // So we don't need to check the return values from the recursion.
        return file.delete();
    }

    /**
     * Delete a file or directory including subdirectories. No error is reported
     * to caller if the file or directory does not exist.
     *
     * @param path file or directory to be deleted
     * @see #delete(java.io.File)
     */
    public static boolean deleteAndIgnore(String path) {
        return deleteAndIgnore(new File(path));
    }

    /**
     * Delete a file or directory including subdirectories. No error is reported
     * to caller if the file or directory does not exist.
     *
     * @param path file or directory to be deleted
     * @see #delete(String)
     */
    public static boolean deleteAndIgnore(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * Utility method to close a stream while suppressing the IOException
     *
     * @param closeable the stream to close
     */
    public static void closeCloseable(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Do nothing
            }
        }
    }
}
