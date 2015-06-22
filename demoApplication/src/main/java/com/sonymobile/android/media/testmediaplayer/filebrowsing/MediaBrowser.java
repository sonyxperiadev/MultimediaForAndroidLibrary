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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
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

import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.testmediaplayer.MainActivity;
import com.sonymobile.android.media.testmediaplayer.PlayerConfiguration;
import com.sonymobile.android.media.testmediaplayer.R;

public class MediaBrowser {

    private static final boolean LOGS_ENABLED = PlayerConfiguration.DEBUG || false;

    private File[] mFiles;

    private final ListView mListView;

    private String mCurrentPath;

    private File mCurrentFile;

    private static final String TAG = "DEMOAPPLICATION_BROWSER";


    private final ExpandableListView mExpandableListView;

    private List<String> mListDataHeader;

    private HashMap<String, List<MediaSource>> mListDataChild;

    private final Context mContext;

    private final MediaPlayer mMediaPlayer;

    private final MainActivity mMainActivity;

    private final DrawerLayout mDrawerLayout;


    private final LinearLayout mDebugLayout;

    private TextView mDebugTitle;

    private boolean mListAdapterNotSet;

    private ArrayList<String> mVolumes;

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
        readFromMediaStoreVideo();
        readFromMediaStoreAudio();
        readInternalSourceFile();
        readExternalSourceFile();
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
                startMediaPlayer(
                        mListDataChild.get(
                                mListDataHeader.get(groupPosition)).get(childPosition).getSource(),
                        mListDataHeader.get(groupPosition).startsWith("MediaStore"));
                return true;
            }

        });
    }

    private void readFromMediaStoreVideo() {

        String columns[] = {
                MediaStore.Video.VideoColumns._ID, MediaStore.Video.VideoColumns.DATA
        };
        Cursor cursor = MediaStore.Video.query(mMainActivity.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, columns);
        if (cursor.moveToFirst()) {
            mListDataHeader.add("MediaStore Video");
            ArrayList<MediaSource> children = new ArrayList<>();
            do {
                String titleWithFiletype = cursor.getString(1).substring(
                        cursor.getString(1).lastIndexOf("/") + 1);
                children.add(new MediaSource(titleWithFiletype,
                        ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(0)).toString()));
            } while (cursor.moveToNext());
            mListDataChild.put("MediaStore Video", children);
        }
        cursor.close();
    }

    private void readFromMediaStoreAudio() {

        String columns[] = {
                MediaStore.Audio.AudioColumns._ID, MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.MIME_TYPE
        };
        Cursor cursor = mMainActivity.getContentResolver().query(MediaStore.Audio.Media
                .EXTERNAL_CONTENT_URI, columns, null, null, null);
        if (cursor.moveToFirst()) {
            mListDataHeader.add("MediaStore Audio");
            ArrayList<MediaSource> children = new ArrayList<>();
            do {
                String titleWithFiletype = cursor.getString(1).substring(
                        cursor.getString(1).lastIndexOf("/") + 1);

                children.add(new MediaSource(titleWithFiletype,
                        ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(0)).toString()));
            } while (cursor.moveToNext());
            mListDataChild.put("MediaStore Audio", children);
        }
        cursor.close();
    }

    private void prepareListData() {

        StorageManager sman = (StorageManager)mContext.getSystemService(Context.STORAGE_SERVICE);

        mVolumes = new ArrayList<>();
        try {
            Method getVolumePaths = StorageManager.class.getMethod("getVolumePaths", null);

            String[] paths = (String[])getVolumePaths.invoke(sman);

            for (String path : paths) {
                File f = new File(path);
                if (f.canRead() && f.exists() && f.isDirectory() && f.listFiles().length > 0) {
                    mVolumes.add(path + "/");
                }
            }
        } catch (NoSuchMethodException e) {
        } catch (InvocationTargetException e) {
        } catch (IllegalAccessException e) {
        }

        if (mVolumes.isEmpty()) {
            mVolumes.add(Environment.getExternalStorageDirectory().getPath());
        }

        navigateToVolumes();

        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                TextView fileNameView = (TextView)arg1;
                String theFileName = (String)fileNameView.getText();
                mDebugTitle.setText(theFileName);
                File theFile = new File(mCurrentPath + theFileName);
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
                mMediaPlayer.setDataSource(mContext, Uri.parse(path));
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

    private void navigateToVolumes() {
        if (mVolumes.size() == 1) {
            navigate(new File(mVolumes.get(0)));
            return;
        }

        mCurrentPath = "";

        String[] fileNames = new String[mVolumes.size()];
        for (int i = 0; i < mVolumes.size(); i++) {
            fileNames[i] = mVolumes.get(i);
        }
        ArrayAdapter adapter = new ArrayAdapter<>(mContext, R.layout.ondevice_list_item,
                fileNames);
        mListView.setAdapter(adapter);
    }

    protected void navigate(File file) {
        mCurrentFile = file;
        mCurrentPath = file.getPath() + "/";
        mFiles = file.listFiles();
        Arrays.sort(mFiles, new FileComparator());
        ArrayList<String> nameList = new ArrayList<>();
        for (File tempFile : mFiles) {
            if (tempFile.isDirectory()) {
                if (tempFile.listFiles().length > 0) {
                    nameList.add(tempFile.getName() + "/");
                }
            } else {
                nameList.add(tempFile.getName());
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
            Log.d(TAG, "onBackPressed, currentPath: " + mCurrentPath);
        if (mVolumes.contains(mCurrentPath)) {
            navigateToVolumes();
        } else if (!mCurrentPath.isEmpty()) {
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

    private void readInternalSourceFile() {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
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
    }

    private void readExternalSourceFile() {
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        File file = new File(Environment.getExternalStorageDirectory()
                + "/demoapplication_links.txt");
        if (file.exists()) {
            try {
                is = new FileInputStream(file);
                isr = new InputStreamReader(is);
                br = new BufferedReader(isr);
                readData(br);
            } catch (FileNotFoundException e) {
            } finally {
                close(br);
                close(isr);
                close(is);
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