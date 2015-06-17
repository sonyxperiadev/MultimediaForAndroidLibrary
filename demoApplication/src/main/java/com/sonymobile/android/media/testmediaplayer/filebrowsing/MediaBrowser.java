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

package com.sonymobile.android.media.testmediaplayer.filebrowsing;

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.testmediaplayer.R;
import com.sonymobile.android.media.testmediaplayer.MainActivity;
import com.sonymobile.android.media.testmediaplayer.PlayerConfiguration;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Audio.Media;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class MediaBrowser {

    private static final boolean LOGS_ENABLED = PlayerConfiguration.DEBUG || false;

    private File[] mFiles;

    private final ListView mListView;

    private String mCurrentPath;

    private File mCurrentFile;

    private static final String TAG = "DEMOAPPLICATION_BROWSER";

    private static final String LOCAL_FILES = "Local files";

    private static final String[] SUPPORTED_FILE_EXTENSIONS = {
            "MP4",
            "MNV",
            "ISMV",
            "ISMA",
            "M4V",
            "M4A",
            "3GP",
            "3GPP",
            "M4B",
            "3G2",
            "3GA"
    };

    private static final String[] SUPPORTED_MIMETYPES = {
            "video/mp4",
            "video/3gpp",
            "video/ismv",
            "audio/isma",
            "video/vnd.sony.mnv",
            "audio/mp4"
    };

    private final ExpandableListView mExpandableListView;

    private List<String> mListDataHeader;

    private HashMap<String, List<MediaSource>> mListDataChild;

    // HashMap< Section header , HashMap< Child display name , Link >>
    private HashMap<String, HashMap<String, String>> mAllSources;

    private final Context mContext;

    private final MediaPlayer mMediaPlayer;

    private final MainActivity mMainActivity;

    private final DrawerLayout mDrawerLayout;

    private ArrayAdapter mAdapter;

    private String mStartPath;

    private final LinearLayout mDebugLayout;

    private TextView mDebugTitle;

    private boolean mListAdapterNotSet;

    public MediaBrowser(Context cont, ExpandableListView elv, ListView listv,
            MediaPlayer mp, MainActivity ma, DrawerLayout dl, LinearLayout debugLayout) {
        mExpandableListView = elv;
        mListView = listv;
        mContext = cont;
        mMediaPlayer = mp;
        mMainActivity = ma;
        mDrawerLayout = dl;
        mDebugLayout = debugLayout;
        init();
    }

    private void init() {
        mListDataHeader = new ArrayList<>();
        mListDataChild = new HashMap<>();
        mAllSources = new HashMap<>();
        readFromMediaStoreVideo();
        readFromMediaStoreAudio();
        readFileFromPath(null);
        readFileFromPath(Environment.getExternalStorageDirectory()
                + "/demoapplication_links.txt");
        prepareListData();

        mDebugTitle = (TextView)mDebugLayout.findViewById(R.id.activity_main_debug_media_title);

        ExpandableListViewAdapter listAdapter =
                new ExpandableListViewAdapter(mContext, mListDataHeader, mListDataChild);
        mExpandableListView.setAdapter(listAdapter);
        mExpandableListView.setOnChildClickListener(new OnChildClickListener() {

            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                    int childPosition, long id) {
                LinearLayout layout = (LinearLayout)v;
                TextView tv = (TextView)layout.findViewById(R.id.expand_list_item);
                mDebugTitle.setText(mListDataHeader.get(groupPosition) + " / " + tv.getText());
                if (groupPosition == 0 && mListDataHeader.get(groupPosition).equals(LOCAL_FILES)) {
                    startMediaPlayer(
                            mAllSources.get(mListDataHeader.get(groupPosition)).get(tv.getText()),
                            true);
                } else {
                    startMediaPlayer(
                            mAllSources.get(mListDataHeader.get(groupPosition)).get(tv.getText()),
                            false);

                }
                return false;
            }

        });
    }

    private void readFromMediaStoreVideo() {

        String columns[] = {
                MediaStore.Video.VideoColumns.TITLE, MediaStore.Video.VideoColumns._ID,
                MediaStore.Video.VideoColumns.MIME_TYPE, MediaStore.Video.VideoColumns.DATA
        };
        Cursor cursor = MediaStore.Video.query(mMainActivity.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns);
        ArrayList<MediaSource> children = new ArrayList<>();
        String[] tmpStrArr;
        String tmpStr;
        if (cursor.moveToFirst()) {
            mListDataHeader.add(LOCAL_FILES);
            mAllSources.put(LOCAL_FILES, new HashMap<String, String>());
            do {
                tmpStr = cursor.getString(2);
                for (String s : SUPPORTED_MIMETYPES) {
                    if (s.equals(tmpStr)) {
                        String titleWithFiletype = cursor.getString(3).substring(
                                cursor.getString(3).lastIndexOf("/") + 1);
                        children.add(new MediaSource(titleWithFiletype,
                                cursor.getString(3)));
                        mAllSources.get(LOCAL_FILES).put(titleWithFiletype, cursor.getString(3));
                    }
                }
            } while (cursor.moveToNext());
            mListDataChild.put(LOCAL_FILES, children);
        }
        cursor.close();
    }

    private void readFromMediaStoreAudio() {

        String columns[] = {
                MediaStore.Audio.AudioColumns.TITLE, MediaStore.Audio.AudioColumns._ID,
                MediaStore.Audio.AudioColumns.MIME_TYPE, MediaStore.Audio.AudioColumns.DATA
        };
        Cursor cursor = mMainActivity.getContentResolver().query(MediaStore.Audio.Media
                .EXTERNAL_CONTENT_URI, columns, null, null, null);
        ArrayList<MediaSource> children = new ArrayList<MediaSource>();
        String[] tmpStrArr;
        String tmpStr;
        if (cursor.moveToFirst()) {
            do {
                tmpStr = cursor.getString(2);
                for (String s : SUPPORTED_MIMETYPES) {
                    if (s.equals(tmpStr)) {
                        String titleWithFiletype = cursor.getString(3).substring(
                                cursor.getString(3).lastIndexOf("/") + 1);
                        children.add(new MediaSource(titleWithFiletype,
                                cursor.getString(3)));
                        mAllSources.get(LOCAL_FILES).put(titleWithFiletype, cursor.getString(3));
                    }
                }
            } while (cursor.moveToNext());
            ArrayList<MediaSource> localList =
                    (ArrayList<MediaSource>)mListDataChild.get(LOCAL_FILES);
            if (localList != null) {
                children.addAll(localList);
            }
            mListDataChild.put(LOCAL_FILES, children);
        }
        cursor.close();
    }

    private void prepareListData() {

        mCurrentPath = "/sdcard1/";
        mStartPath = "/sdcard1/";
        File file = new File("/sdcard1/");
        File[] mTempFiles = file.listFiles();
        if (mTempFiles == null) {
            if (LOGS_ENABLED) Log.d(TAG, "mTempFiles is null");
        }
        if (!file.exists() || mTempFiles == null) {
            file = new File(Environment.getExternalStorageDirectory().toString());
            mCurrentPath = Environment.getExternalStorageDirectory().toString();
            mStartPath = Environment.getExternalStorageDirectory().toString();
        }
        mFiles = file.listFiles();
        Arrays.sort(mFiles, new FileComparator());
        ArrayList<String> nameList = new ArrayList<String>();
        for (File tempFile : mFiles) {
            if (isFileExtensionSupported(tempFile.getName())) {
                nameList.add(tempFile.getName());
            } else if (tempFile.isDirectory()) {
                nameList.add(tempFile.getName() + "/");
            }
        }

        String[] fileNames = new String[nameList.size()];
        for (int i = 0; i < nameList.size(); i++) {
            fileNames[i] = nameList.get(i);
        }
        ArrayAdapter adapter = new ArrayAdapter<>(mContext, R.layout.ondevice_list_item,
                fileNames);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                TextView fileNameView = (TextView)arg1;
                String theFileName = (String)fileNameView.getText();
                mDebugTitle.setText(theFileName);
                File theFile = new File(mCurrentPath + "/" + theFileName);
                if (theFile.isDirectory()) {
                    navigate(theFile);
                } else {
                    mMainActivity.setMediaBrowserState(mListView.getAdapter(), mCurrentPath);
                    startMediaPlayer(theFile.getPath(), false);
                }
            }
        });
    }

    private void startMediaPlayer(String path, boolean useUri) {
        if (mMediaPlayer.getState() != MediaPlayer.State.IDLE) {
            mMainActivity.reset();
        }
        try {
            if (LOGS_ENABLED) Log.d(TAG, "Setting datasource to: " + path);
            if (useUri) {
                // Method not used until fixed in framework
                // mMediaPlayer.setDataSource(mContext,
                // MediaStore.Video.Media.getContentUri(path));
                mMediaPlayer.setDataSource(path);
            } else {
                mMediaPlayer.setDataSource(path);
            }
            mMediaPlayer.prepareAsync();
            mDrawerLayout.closeDrawer(Gravity.START);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void navigate(File file) {
        mCurrentFile = file;
        mCurrentPath = file.getPath();
        mFiles = file.listFiles();
        Arrays.sort(mFiles, new FileComparator());
        ArrayList<String> nameList = new ArrayList<String>();
        for (File tempFile : mFiles) {
            if (isFileExtensionSupported(tempFile.getName())) {
                nameList.add(tempFile.getName());
            } else if (tempFile.isDirectory()) {
                nameList.add(tempFile.getName() + "/");
            }
        }
        String[] fileNames = new String[nameList.size()];
        for (int i = 0; i < nameList.size(); i++) {
            fileNames[i] = nameList.get(i);
        }
        ArrayAdapter adapter = new ArrayAdapter<>(mContext, R.layout.ondevice_list_item,
                fileNames);
        mListView.setAdapter(adapter);
    }


    public void onBackPressed() {
        if (LOGS_ENABLED)
            Log.d("FileBrowsingActivity", "onBackPressed, currentPath: " + mCurrentPath);
        if (!mCurrentPath.equals(mStartPath)
                && !mCurrentPath.equals("/")) {
            navigate(mCurrentFile.getParentFile());
        }
    }

    public void onSwapSourceClicked(Boolean onDevice) {
        if (onDevice) {
            mListView.setVisibility(View.GONE);
            mExpandableListView.setVisibility(View.VISIBLE);
            mMainActivity.findViewById(R.id.file_browsing_back).setVisibility(View.GONE);
        } else {
            mExpandableListView.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
            mMainActivity.findViewById(R.id.file_browsing_back).setVisibility(View.VISIBLE);
        }
    }

    private void readFileFromPath(String path) {
        Log.d(TAG, "externalStorage: " + Environment.getExternalStorageDirectory());
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        if(path == null){
            try {
                is = mContext.getResources().openRawResource(R.raw.sourcefile);
                isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                br = new BufferedReader(isr);
                readData(br);
            } finally {
                close(br);
                close(isr);
                close(is);
            }
        } else {
            File file = new File(path);
            if (file.exists()) {
                try {
                    is = new FileInputStream(file);
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    readData(br);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    close(br);
                    close(isr);
                    close(is);
                }
            }
        }
    }

    private void readData(BufferedReader br) {
        String line;
        try {
            while ((line = br.readLine()) != null) {
                if (line.length() > 2) {
                    String[] infoParameters = line.split(";");
                    if (infoParameters.length < 3) {
                        continue;
                    }
                    if (mListDataHeader.contains(infoParameters[0])) {
                        mListDataChild.get(infoParameters[0])
                                .add(new MediaSource(infoParameters[1], infoParameters[2]));
                    } else {
                        mListDataHeader.add(infoParameters[0]);
                        List<MediaSource> children = new ArrayList<>();
                        children.add(new MediaSource(infoParameters[1], infoParameters[2]));
                        mListDataChild.put(infoParameters[0], children);
                    }
                    if (mAllSources.containsKey(infoParameters[0])) {
                        mAllSources.get(infoParameters[0])
                                .put(infoParameters[1], infoParameters[2]);
                    } else {
                        HashMap<String, String> child = new HashMap<>();
                        child.put(infoParameters[1], infoParameters[2]);
                        mAllSources.put(infoParameters[0], child);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void restoreBrowser(ListAdapter adapter, String path) {
        if (!mListAdapterNotSet) {
            mListAdapterNotSet = true;
            mListView.setAdapter(adapter);
            mCurrentPath = path;
            mCurrentFile = new File(path);
        }
    }

    private static boolean isFileExtensionSupported(String extension) {
        for (String s : SUPPORTED_FILE_EXTENSIONS) {
            if (extension.toUpperCase().endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    private class FileComparator implements Comparator<File> {

        @Override
        public int compare(File file, File file2) {
            if (file.isDirectory()) {
                if (!file2.isDirectory()) {
                    return -1;
                }
            } else if (file2.isDirectory()) {
                return 1;
            }
            return file.compareTo(file2);
        }
    }
}