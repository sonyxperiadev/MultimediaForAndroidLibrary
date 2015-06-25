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

package com.sonymobile.android.media.testmediaplayer;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.sonymobile.android.media.MediaError;
import com.sonymobile.android.media.MediaInfo;
import com.sonymobile.android.media.MediaPlayer;
import com.sonymobile.android.media.MediaPlayer.OnBufferingUpdateListener;
import com.sonymobile.android.media.MediaPlayer.OnCompletionListener;
import com.sonymobile.android.media.MediaPlayer.OnErrorListener;
import com.sonymobile.android.media.MediaPlayer.OnOutputControlEventListener;
import com.sonymobile.android.media.MediaPlayer.OnSeekCompleteListener;
import com.sonymobile.android.media.MediaPlayer.OnSubtitleDataListener;
import com.sonymobile.android.media.MediaPlayer.OutputBlockedInfo;
import com.sonymobile.android.media.MediaPlayer.OutputControlInfo;
import com.sonymobile.android.media.MetaData;
import com.sonymobile.android.media.testmediaplayer.R;
import com.sonymobile.android.media.SubtitleData;
import com.sonymobile.android.media.TrackInfo;
import com.sonymobile.android.media.TrackInfo.TrackType;
import com.sonymobile.android.media.testmediaplayer.GUI.AudioSubtitleTrackDialog;
import com.sonymobile.android.media.testmediaplayer.GUI.SeekbarUpdater;
import com.sonymobile.android.media.testmediaplayer.GUI.TimeTracker;
import com.sonymobile.android.media.testmediaplayer.filebrowsing.MediaBrowser;
import com.sonymobile.android.media.testmediaplayer.subtitles.SubtitleRenderer;

public class MainActivity extends Activity implements SurfaceHolder.Callback,
        DialogInterface.OnClickListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnPreparedListener, DrawerLayout.DrawerListener,
        OnSystemUiVisibilityChangeListener, OnCompletionListener, OnTouchListener,
        OnSeekCompleteListener, OnErrorListener, OnSubtitleDataListener,
        OnOutputControlEventListener, OnBufferingUpdateListener {

    private static final boolean LOGS_ENABLED = PlayerConfiguration.DEBUG || false;

    private SurfaceView mPreview;

    private SurfaceHolder mHolder;

    private MediaPlayer mMediaPlayer;

    private static final String TAG = "DEMOAPPLACTION_MAIN";

    private static final int mUiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN;

    private RelativeLayout mBottomControls;

    private RelativeLayout mUpperControls;

    private Animation mSlideInAnimation;

    private Handler mHandler;

    private View mDecorView;

    private Runnable mFadeOutRunnable;

    private Runnable mHideNavigationRunnable;

    private boolean mIsAudio;

    private SeekBar mSeekbar;

    private ImageView mPlayPauseButton;

    private SeekbarUpdater mSeekBarUpdater;

    private int mSubPos;

    private int mAudioPos;

    private AudioSubtitleTrackDialog mTrackDialog;

    private String[] mSubtitles = new String[] {
            "No subtitle"
    };

    private HashMap<Integer, Integer> mSubtitleTracker;

    private String[] mAudioTracks = new String[] {
            "No audio"
    };

    private HashMap<Integer, Integer> mAudioTracker;

    private TimeTracker mTimeTracker;

    private LinearLayout mTimeLayout;

    private TextView mDurationView;

    private TextView mTimeView;

    private TextView mSubtitleView;

    private DrawerLayout mDrawerLayout;

    private MediaBrowser mFileBrowser;

    private Boolean mIsFileBrowsing;

    private boolean mIsBuffering = false;

    private boolean mIsPrepared = false;

    private final long[] mDebugArray = {
            0, 0, 0
    };

    private boolean mDebugView = false;

    private boolean mSeekLock = false;

    private SubtitleRenderer mSubtitleRenderer;

    private boolean mOngoingAnimation;

    private ListAdapter mMediaBrowserAdapter;

    private String mMediaBrowserPath;

    private TextView mTimeSeekView;

    private RelativeLayout.LayoutParams mSubtitleLayoutAboveTimeView;

    private RelativeLayout.LayoutParams mSubtitleLayoutBottom;

    private float mPlaybackSpeed = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.new_activity_main_drawerlayout);
        init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (LOGS_ENABLED) Log.d(TAG, "onDestroy");
        mSeekBarUpdater.deactivate();
        mTimeTracker.stopUpdating();
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mTrackDialog.getDialog() != null) {
            mTrackDialog.getDialog().dismiss();
        }
    }

    @Override
    public void onPause() {
        if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
            onPlayPauseClicked(null);
        }
        mSeekBarUpdater.deactivate();
        mTimeTracker.stopUpdating();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (LOGS_ENABLED) Log.d(TAG, "onResume, MediaPlayer.State: " + mMediaPlayer.getState());
    }

    // GUI handling
    private void fadeOut() {
        mHandler.postDelayed(mFadeOutRunnable, 2500);
        mHandler.postDelayed(mHideNavigationRunnable, 3500);
    }

    // GUI button
    public void onPlayPauseClicked(View view) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        } else {
            MediaPlayer.State mState = mMediaPlayer.getState();
            if (mState == MediaPlayer.State.PLAYING
                    || mState == MediaPlayer.State.PAUSED
                    || mState == MediaPlayer.State.PREPARED
                    || mState == MediaPlayer.State.COMPLETED) {
                if (mPlayPauseButton.getTag() == null) {
                    mPlayPauseButton.setTag(true);
                    mPlayPauseButton.setImageResource(R.drawable.media_player_pause_button);
                    if (mState == MediaPlayer.State.PREPARED) {
                        mHandler.post(mHideNavigationRunnable);
                    } else {
                        fadeOut();
                    }
                    mMediaPlayer.play();
                    mTimeTracker.startUpdating();
                    mSeekBarUpdater.activate();
                } else {
                    if ((Boolean)(mPlayPauseButton.getTag()) == true) {
                        mPlayPauseButton.setTag(false);
                        mPlayPauseButton.setImageResource(R.drawable.media_player_play_button);
                        if (LOGS_ENABLED) Log.d(TAG, "onPauseClicked");
                        mHandler.removeCallbacks(mFadeOutRunnable);
                        mHandler.removeCallbacks(mHideNavigationRunnable);
                        if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                            mMediaPlayer.pause();
                            mSeekBarUpdater.deactivate();
                            mTimeTracker.stopUpdating();
                            mSubtitleRenderer.pause();
                        }
                    } else {
                        mPlayPauseButton.setTag(true);
                        mPlayPauseButton.setImageResource(R.drawable.media_player_pause_button);
                        if (mMediaPlayer.getState() == MediaPlayer.State.PREPARED
                                || mMediaPlayer.getState() == MediaPlayer.State.PAUSED
                                || mMediaPlayer.getState() == MediaPlayer.State.COMPLETED) {
                            if (mState == MediaPlayer.State.PREPARED) {
                                mHandler.post(mHideNavigationRunnable);
                            } else {
                                fadeOut();
                            }
                            mMediaPlayer.play();
                            if (!mSeekLock) {
                                mSeekBarUpdater.activate();
                            }
                            mTimeTracker.startUpdating();
                        }
                    }
                }
            }
        }
    }

    // GUI button
    public void onSpeedUpClicked(View view) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        } else {
            mHandler.removeCallbacks(mFadeOutRunnable);
            mHandler.removeCallbacks(mHideNavigationRunnable);
            if (mPlaybackSpeed == 1.0f) {
                mPlaybackSpeed = 2.0f;
            } else if (mPlaybackSpeed == 0.5f) {
                mPlaybackSpeed = 1.0f;
            }
            if (LOGS_ENABLED) Log.d(TAG, "Setting speed to: " + mPlaybackSpeed);
            mMediaPlayer.setSpeed(mPlaybackSpeed);
            fadeOut();
        }
    }

    // GUI button
    public void onSlowDownClicked(View view) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        } else {
            mHandler.removeCallbacks(mFadeOutRunnable);
            mHandler.removeCallbacks(mHideNavigationRunnable);
            if (mPlaybackSpeed == 2.0f) {
                mPlaybackSpeed = 1.0f;
            } else if (mPlaybackSpeed == 1.0f) {
                mPlaybackSpeed = 0.5f;
            }
            if (LOGS_ENABLED) Log.d(TAG, "Setting speed to: " + mPlaybackSpeed);
            mMediaPlayer.setSpeed(mPlaybackSpeed);
            fadeOut();
        }
    }

    // GUI button
    public void onSubtitleClicked(View view) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        } else {
            if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                onPlayPauseClicked(null);
            }
            mSubtitleTracker.clear();
            ArrayList<String> tracks = new ArrayList<>();
            tracks.add("No subtitle");
            TrackInfo[] mTrackInfo = mMediaPlayer.getTrackInfo();
            for (int i = 0; i < mTrackInfo.length; i++) {
                if (mTrackInfo[i].getTrackType() == TrackType.SUBTITLE) {
                    mSubtitleTracker.put(tracks.size(), i);
                    tracks.add(mTrackInfo[i].getLanguage());
                }
            }
            if (LOGS_ENABLED) Log.d(TAG, "Number of subtitle tracks: " + tracks.size());
            if (tracks.size() > 1) {
                mSubtitles = new String[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) {
                    mSubtitles[i] = tracks.get(i);
                }

            } else {
                mSubtitles = new String[1];
                mSubtitles[0] = "No subtitle";
            }
            mTrackDialog
                    .createDialog(AudioSubtitleTrackDialog.CHOOSE_SUBTITLE_TRACK, mSubtitles,
                            mSubPos,
                            this);
            mIsAudio = false;
        }
    }

    // GUI button
    public void onAudioClicked(View view) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        } else {
            if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                onPlayPauseClicked(null);
            }
            mAudioTracker.clear();
            ArrayList<String> tracks = new ArrayList<>();
            TrackInfo[] mTrackInfo = mMediaPlayer.getTrackInfo();
            for (int i = 0; i < mTrackInfo.length; i++) {
                if (mTrackInfo[i].getTrackType() == TrackType.AUDIO) {
                    mAudioTracker.put(tracks.size(), i);
                    tracks.add(mTrackInfo[i].getLanguage());
                }
            }
            if (LOGS_ENABLED) Log.d(TAG, "Number of audio tracks: " + tracks.size());
            if (tracks.size() > 0) {
                mAudioTracks = new String[tracks.size()];
                for (int i = 0; i < tracks.size(); i++) {
                    mAudioTracks[i] = tracks.get(i);
                }
            } else {
                mAudioTracks = new String[1];
                mAudioTracks[0] = "No audio";
            }
            mTrackDialog.createDialog(AudioSubtitleTrackDialog.CHOOSE_AUDIO_TRACK, mAudioTracks,
                    mAudioPos, this);
            mIsAudio = true;
        }
    }

    // Initiating objects
    public void init() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer(getApplicationContext());
            mMediaPlayer.setScreenOnWhilePlaying(true);
        }
        mHandler = new Handler();
        mDecorView = getWindow().getDecorView();
        mSeekbar = (SeekBar)findViewById(R.id.activity_main_seekbar);
        mSeekbar.setMax(1000);
        mPlayPauseButton = (ImageView)findViewById(R.id.activity_main_playpause_button);
        mSeekBarUpdater = new SeekbarUpdater(mSeekbar, mMediaPlayer);
        RelativeLayout topLayout = (RelativeLayout)findViewById(R.id.activity_main_mainlayout);
        mTrackDialog = new AudioSubtitleTrackDialog(this);
        mAudioPos = mSubPos = 0;
        mTimeView = (TextView)findViewById(R.id.activity_main_time);
        mDurationView = (TextView)findViewById(R.id.activity_main_duration);
        mTimeLayout = (LinearLayout)findViewById(R.id.activity_main_timelayout);
        mTimeTracker = new TimeTracker(mMediaPlayer, mTimeView);
        mSubtitleView = (TextView)findViewById(R.id.activity_main_subtitleView);
        mSubtitleRenderer = new SubtitleRenderer(mSubtitleView, Looper.myLooper(), mMediaPlayer);
        mSubtitleLayoutAboveTimeView = (RelativeLayout.LayoutParams)mSubtitleView.getLayoutParams();
        mSubtitleLayoutBottom = (RelativeLayout.LayoutParams)
                findViewById(R.id.activity_main_subtitleView_params).getLayoutParams();
        mSubtitleView.setLayoutParams(mSubtitleLayoutBottom);
        mTimeSeekView = (TextView)findViewById(R.id.activity_main_time_seek);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        RelativeLayout browsingL = (RelativeLayout)findViewById(R.id.activity_main_browsing_layout);
        ExpandableListView elv = (ExpandableListView)browsingL
                .findViewById(R.id.file_browsing_exp_list_view);
        ListView lv = (ListView)browsingL.findViewById(R.id.file_browsing_listview);
        RelativeLayout debugLayout = (RelativeLayout)findViewById(R.id.activity_main_debug_view);
        LinearLayout debugLinearLayout = (LinearLayout)debugLayout
                .findViewById(R.id.activity_main_debug_linear);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mDrawerLayout.setScrimColor(Color.TRANSPARENT);
        mDrawerLayout.setDrawerListener(this);
        mFileBrowser = new MediaBrowser(this, elv, lv, mMediaPlayer, this,
                mDrawerLayout, debugLinearLayout);
        mIsFileBrowsing = false;
        mPreview = (SurfaceView)findViewById(R.id.mSurfaceView);
        mPreview.setOnTouchListener(this);
        topLayout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mPreview.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        mSubtitleTracker = new HashMap<>();
        mAudioTracker = new HashMap<>();
        findViewById(R.id.activity_main_loading_panel).bringToFront();
        findViewById(R.id.activity_main_browsing_layout).bringToFront();
        mUpperControls = (RelativeLayout)findViewById(R.id.UpperButtonLayout);
        mBottomControls = (RelativeLayout)findViewById(R.id.ButtonLayout);
        mSlideInAnimation = AnimationUtils.loadAnimation(this, R.anim.in_top);
        mSlideInAnimation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationEnd(Animation animation) {
                mUpperControls.setVisibility(View.VISIBLE);
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        mBottomControls.setVisibility(View.INVISIBLE);
                    }
                };
                mHandler.postDelayed(r, 3500);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationStart(Animation animation) {
                mBottomControls.setVisibility(View.INVISIBLE);
            }
        });
        mHolder = mPreview.getHolder();
        mHolder.addCallback(this);
        mFadeOutRunnable = new Runnable() {
            @Override
            public void run() {
                mSeekbar.setEnabled(false);
                Animation fadeoutBottom = AnimationUtils.loadAnimation(getApplicationContext(),
                        R.anim.out_bottom);
                fadeoutBottom.setAnimationListener(new AnimationListener() {

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (mOngoingAnimation) {
                            mOngoingAnimation = false;
                            mBottomControls.setVisibility(View.INVISIBLE);
                            mUpperControls.setVisibility(View.INVISIBLE);
                            mSeekbar.setVisibility(View.GONE);
                            mTimeLayout.setVisibility(View.GONE);
                            mSubtitleView.setLayoutParams(mSubtitleLayoutBottom);
                        }
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }

                    @Override
                    public void onAnimationStart(Animation animation) {
                        mOngoingAnimation = true;
                    }
                });
                Animation fadeoutTop = AnimationUtils.loadAnimation(getApplicationContext(),
                        R.anim.out_top);
                mBottomControls.startAnimation(fadeoutBottom);
                mUpperControls.startAnimation(fadeoutTop);
                mSeekbar.startAnimation(fadeoutBottom);
                mTimeLayout.startAnimation(fadeoutBottom);
            }
        };
        mHideNavigationRunnable = new Runnable() {
            @Override
            public void run() {
                mPreview.setSystemUiVisibility(mUiOptions);
            }
        };

        mDecorView.setOnSystemUiVisibilityChangeListener(this);
        mSeekbar.setOnSeekBarChangeListener(new OwnOnSeekBarChangeListener(mMediaPlayer));
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnSeekCompleteListener(this);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnSubtitleDataListener(this);

        DrawerLayout.LayoutParams params = (DrawerLayout.LayoutParams)browsingL.getLayoutParams();
        Resources resources = getResources();
        int top = 0;
        int bottom = 0;
        RelativeLayout.LayoutParams tempParams;
        int resourceId = resources.getIdentifier("status_bar_height",
                "dimen", "android");
        if (resourceId > 0) {
            top = resources.getDimensionPixelSize(resourceId);
        }
        resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            bottom = resources.getDimensionPixelSize(resourceId);
        }
        if (navigationBarAtBottom()) {
            tempParams = (RelativeLayout.LayoutParams)mBottomControls
                    .getLayoutParams();
            tempParams.bottomMargin += bottom;
            mBottomControls.setLayoutParams(tempParams);
            tempParams = (RelativeLayout.LayoutParams)mSeekbar.getLayoutParams();
            tempParams.bottomMargin += bottom;
            mSeekbar.setLayoutParams(tempParams);
            params.setMargins(0, top, 0, bottom);
        } else {
            tempParams = (RelativeLayout.LayoutParams)mBottomControls
                    .getLayoutParams();
            tempParams.rightMargin += bottom;
            mBottomControls.setLayoutParams(tempParams);
            params.setMargins(0, top, 0, 0);
        }
        browsingL.setLayoutParams(params);
        mDrawerLayout.openDrawer(Gravity.START);

        mMediaPlayer.setOnOutputControlListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (LOGS_ENABLED) Log.d(TAG, "surfaceCreated");
        mHolder = holder;
        if (mMediaPlayer != null) {
            mMediaPlayer.setDisplay(mHolder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (LOGS_ENABLED) Log.d(TAG, "surface Destroyed");
        if (mMediaPlayer != null) {
            mSeekBarUpdater.deactivate();
            mTimeTracker.stopUpdating();
        }
    }

    private class OwnOnSeekBarChangeListener implements OnSeekBarChangeListener {
        private int progressFromUser = 0;

        private final MediaPlayer mCurrentMediaPlayer;

        public OwnOnSeekBarChangeListener(MediaPlayer mMediaPlayer) {
            mCurrentMediaPlayer = mMediaPlayer;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            seekBar.setProgress(progress);
            if (fromUser) {
                progressFromUser = progress;
                int time = (int)(mCurrentMediaPlayer.getDuration() * ((double)progress / 1000));
                setTimeOnView(mTimeSeekView, time);
                setTimeOnView(mTimeView, time);
                mHandler.removeCallbacks(mFadeOutRunnable);
                mHandler.removeCallbacks(mHideNavigationRunnable);
                if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                    onPlayPauseClicked(null);
                }
                mCurrentMediaPlayer
                        .seekTo((int)((mCurrentMediaPlayer.getDuration()
                        * ((double)progressFromUser / 1000))));
                mTimeSeekView.setVisibility(View.VISIBLE);
                mSubtitleRenderer.pause();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            if (mOngoingAnimation) {
                cancelFullscreen();
            } else {
                if (LOGS_ENABLED) Log.d(TAG, "onStartTrackingTouch ");
                mHandler.removeCallbacks(mFadeOutRunnable);
                mHandler.removeCallbacks(mHideNavigationRunnable);
                if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                    onPlayPauseClicked(null);
                }
                mTimeSeekView.setVisibility(View.VISIBLE);
                mSeekLock = true;
                mSubtitleRenderer.pause();
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            if (LOGS_ENABLED) Log.d(TAG, "onStopTrackingTouch, progress: "
                    + seekBar.getProgress() + " mp.currentpos: "
                    + mMediaPlayer.getCurrentPosition());
            if (LOGS_ENABLED)
                Log.d(TAG, "seekTo: "
                        + (mCurrentMediaPlayer.getDuration() * ((double)seekBar
                                .getProgress() / 1000)));
            mSubtitleRenderer.clearText();
            mCurrentMediaPlayer
                    .seekTo((int)((mCurrentMediaPlayer.getDuration()
                    * ((double)progressFromUser / 1000))));
            mHandler.postDelayed(mHideNavigationRunnable, 3500);
            mTimeSeekView.setVisibility(View.GONE);
            mSeekLock = false;
        }
    }

    public void mediaplayerPrepared() {
        mSeekbar.setVisibility(View.VISIBLE);
        mTimeLayout.setVisibility(View.VISIBLE);
        mUpperControls.setVisibility(View.VISIBLE);
        mBottomControls.setVisibility(View.VISIBLE);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (LOGS_ENABLED) Log.d(TAG, "Dialog in activity, which:" + which);
        dialog.dismiss();
        if (mIsAudio) {
            mMediaPlayer.selectTrack(mAudioTracker.get(which));
            mAudioPos = which;
            Toast.makeText(getApplicationContext(), "Audio selected: " + mAudioTracks[which],
                    Toast.LENGTH_LONG).show();
        } else {
            if (which > 0) {
                mMediaPlayer.selectTrack(mSubtitleTracker.get(which));
            } else {
                if (mSubPos != 0) {
                    mMediaPlayer.deselectTrack(mSubtitleTracker.get(mSubPos));
                }
            }
            mSubPos = which;
            Toast.makeText(getApplicationContext(), "Subtitle selected: " + mSubtitles[which],
                    Toast.LENGTH_LONG).show();
        }
        onPlayPauseClicked(null);
    }

    public void updateTimeView(String time) {
        mTimeView.setText(time);
    }

    public void onSwapSourceClicked(View view) {
        mFileBrowser.onSwapSourceClicked(mIsFileBrowsing);
        mIsFileBrowsing = !mIsFileBrowsing;
    }

    public void onBackPathClicked(View view) {
        mFileBrowser.onBackPressed();
    }

    public void toggleLoading(Boolean loading) {
        if (loading) {
            findViewById(R.id.activity_main_loading_panel).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.activity_main_loading_panel).setVisibility(View.GONE);
        }
    }

    public void reset() {
        mSeekBarUpdater.deactivate();
        mTimeTracker.stopUpdating();
        mMediaPlayer.reset();
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        mHolder.setFormat(PixelFormat.OPAQUE);
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setDisplay(mHolder);
        mBottomControls.setVisibility(View.INVISIBLE);
        mSeekbar.setVisibility(View.GONE);
        mTimeLayout.setVisibility(View.GONE);
        mDecorView.setOnSystemUiVisibilityChangeListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnSeekCompleteListener(this);
        mMediaPlayer.setOnSubtitleDataListener(this);
        mSeekbar.setOnSeekBarChangeListener(new OwnOnSeekBarChangeListener(mMediaPlayer));
        mSeekBarUpdater = new SeekbarUpdater(mSeekbar, mMediaPlayer);
        mTimeTracker.setMediaPlayer(mMediaPlayer);
        mSubtitleRenderer.stopRendering();
        RelativeLayout browsingL = (RelativeLayout)findViewById(R.id.activity_main_browsing_layout);
        RelativeLayout debugLayout = (RelativeLayout)findViewById(R.id.activity_main_debug_view);
        LinearLayout debugLinearLayout = (LinearLayout)debugLayout
                .findViewById(R.id.activity_main_debug_linear);
        mFileBrowser = new MediaBrowser(this, (ExpandableListView)browsingL
                .findViewById(R.id.file_browsing_exp_list_view),
                (ListView)browsingL.findViewById(R.id.file_browsing_listview),
                mMediaPlayer, this, mDrawerLayout, debugLinearLayout);
        mAudioPos = 0;
        mSubPos = 0;
        mMediaPlayer.setOnOutputControlListener(this);
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaInfo.BUFFERING_START:
                mIsBuffering = true;
                toggleLoading(true);
                if (LOGS_ENABLED) Log.d(TAG, "OnBufferingStart");
                return true;
            case MediaInfo.BUFFERING_END:
                mIsBuffering = false;
                toggleLoading(false);
                if (LOGS_ENABLED) Log.d(TAG, "OnBufferingEnd");
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        Log.i(TAG, "onBufferingUpdate: " + percent);
    }

    @Override
    public void onPrepared(MediaPlayer arg0) {
        if (LOGS_ENABLED) Log.d(TAG, "onPreparedListener, State: " + mMediaPlayer.getState());
        setTimeOnView(mDurationView, mMediaPlayer.getDuration());
        mDrawerLayout.closeDrawer(Gravity.START);
        mPlayPauseButton.setTag(false);
        onPlayPauseClicked(null);
        mIsPrepared = true;
        MetaData metadata = mMediaPlayer.getMediaMetaData();
        if (metadata != null) {
            int videoHeight = mMediaPlayer.getVideoHeight();
            if (videoHeight == 0) {
                // audio only
                if (metadata.containsKey(MetaData.KEY_ALBUM_ART)) {
                    byte[] imageData = metadata.getByteBuffer(MetaData.KEY_ALBUM_ART);
                    Canvas canvas = mHolder.lockCanvas();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    int newHeight = canvas.getHeight();
                    int newWidth = bitmap.getWidth() * newHeight / bitmap.getHeight();
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
                    canvas.drawBitmap(bitmap, (canvas.getWidth() - bitmap.getWidth()) / 2f, 0,
                            new Paint());
                    mHolder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    @Override
    public void onDrawerClosed(View arg0) {
        MediaPlayer.State mState = mMediaPlayer.getState();
        if (mState == MediaPlayer.State.PAUSED && mState != MediaPlayer.State.INITIALIZED
                && mState != MediaPlayer.State.IDLE) {
            onPlayPauseClicked(null);
        }
        mSeekbar.setClickable(true);
    }

    @Override
    public void onDrawerOpened(View arg0) {
        if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
            onPlayPauseClicked(null);
        }
        mSeekbar.setClickable(false);
    }

    @Override
    public void onDrawerSlide(View arg0, float arg1) {
        findViewById(R.id.activity_main_browsing_layout).bringToFront();
    }

    @Override
    public void onDrawerStateChanged(int stateChanged) {
        if (stateChanged == DrawerLayout.STATE_DRAGGING && mMediaBrowserAdapter != null) {
            mFileBrowser.restoreBrowser(mMediaBrowserAdapter, mMediaBrowserPath);
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (mIsBuffering && !mIsPrepared) {
            mHandler.postDelayed(mHideNavigationRunnable, 3500);
        } else {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                if (LOGS_ENABLED) Log.d(TAG, "visibility&Fullscreen == 0");
                mSubtitleView.setLayoutParams(mSubtitleLayoutAboveTimeView);
                mBottomControls.setVisibility(View.VISIBLE);
                mUpperControls.setVisibility(View.VISIBLE);
                mSeekbar.setVisibility(View.VISIBLE);
                mTimeLayout.setVisibility(View.VISIBLE);
                mSeekbar.setEnabled(true);
                if (mMediaPlayer.getState() == MediaPlayer.State.PLAYING) {
                    mHandler.postDelayed(mFadeOutRunnable, 2500);
                    mHandler.postDelayed(mHideNavigationRunnable, 3500);
                }
            } else {
                if (LOGS_ENABLED) Log.d(TAG, "visibility&Fullscreen != 0");
            }
        }
    }

    @Override
    public void onCompletion(MediaPlayer arg0) {
        if (LOGS_ENABLED) Log.d(TAG, "OnCompletion!");
        mSeekBarUpdater.deactivate();
        mTimeTracker.stopUpdating();
        mHandler.removeCallbacks(mFadeOutRunnable);
        mHandler.removeCallbacks(mHideNavigationRunnable);
        setTimeOnView(mTimeView, mMediaPlayer.getDuration());
        mDrawerLayout.openDrawer(Gravity.START);
        mPlayPauseButton.setTag(false);
        mPlayPauseButton.setImageResource(R.drawable.media_player_play_button);
        mPreview.setSystemUiVisibility(0);
        mBottomControls.setVisibility(View.VISIBLE);
        mSeekbar.setVisibility(View.VISIBLE);
        mTimeLayout.setVisibility(View.VISIBLE);
        mSeekbar.setEnabled(true);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mOngoingAnimation) {
            cancelFullscreen();
        }
        if (LOGS_ENABLED) Log.d(TAG, "onTouch, mPreview");
        if (System.currentTimeMillis() - mDebugArray[0] > 1000) {
            mDebugArray[0] = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - mDebugArray[1] > 1000) {
            mDebugArray[1] = System.currentTimeMillis();
        } else {
            if (!mDebugView) {
                findViewById(R.id.activity_main_debug_view).setVisibility(View.VISIBLE);
                mDebugView = true;
            } else {
                findViewById(R.id.activity_main_debug_view).setVisibility(View.GONE);
                mDebugView = false;
            }
        }
        return false;
    }

    private void cancelFullscreen() {
        if (LOGS_ENABLED) Log.d(TAG, "cancelFullscreen");
        mBottomControls.clearAnimation();
        mUpperControls.clearAnimation();
        mSeekbar.clearAnimation();
        mTimeLayout.clearAnimation();
        mOngoingAnimation = false;
        mSeekbar.setEnabled(true);
        mHandler.removeCallbacks(mFadeOutRunnable);
        mHandler.removeCallbacks(mHideNavigationRunnable);
        fadeOut();
    }

    @Override
    public void onSeekComplete(MediaPlayer arg0) {
        if (LOGS_ENABLED) Log.d(TAG, "SeekComplete");
        mSeekBarUpdater.activate();
        setTimeOnView(mTimeView, mMediaPlayer.getCurrentPosition());

        if(!mSeekLock){
            onPlayPauseClicked(null);
        }
    }

    public boolean navigationBarAtBottom() {
        return (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK)
                >= Configuration.SCREENLAYOUT_SIZE_LARGE
                && getResources().getString(R.string.screen_type).equals("tablet");
    }

    public void setMediaBrowserState(ListAdapter adapter, String path) {
        mMediaBrowserAdapter = adapter;
        mMediaBrowserPath = path;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mSubtitleView.setText("Error " + errorCodeToString(what));
        mSubtitleView.setLayoutParams(mSubtitleLayoutBottom);
        mIsBuffering = false;
        toggleLoading(false);
        mIsPrepared = false;
        mSeekBarUpdater.deactivate();
        mTimeTracker.stopUpdating();
        return true;
    }

    private static String errorCodeToString(int errorCode) {
        switch (errorCode) {
            case MediaError.UNKNOWN:
                return "UNKNOWN";
            case MediaError.INVALID_STATE:
                return "INVALID_STATE";
            case MediaError.SERVER_DIED:
                return "SERVER_DIED";
            case MediaError.NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                return "NOT_VALID_FOR_PROGRESSIVE_PLAYBACK";
            case MediaError.IO:
                return "IO";
            case MediaError.MALFORMED:
                return "MALFORMED";
            case MediaError.UNSUPPORTED:
                return "UNSUPPORTED";
            case MediaError.TIMED_OUT:
                return "TIMED_OUT";
            case MediaError.DRM_UNKNOWN:
                return "DRM_UNKNOWN";
            case MediaError.DRM_NO_LICENSE:
                return "DRM_NO_LICENSE";
            case MediaError.DRM_LICENSE_EXPIRED:
                return "DRM_LICENSE_EXPIRED";
            case MediaError.DRM_SESSION_NOT_OPENED:
                return "DRM_SESSION_NOT_OPENED";
            case MediaError.DRM_LICENSE_FUTURE:
                return "DRM_LICENSE_FUTURE";
            case MediaError.DRM_INSUFFICIENT_OUTPUT_PROTECTION:
                return "DRM_INSUFFICIENT_OUTPUT_PROTECTION";
            default:
                return "not defined: " + errorCode;
        }
    }

    @Override
    public void onSubtitleData(MediaPlayer mp, SubtitleData data) {
        if (LOGS_ENABLED) Log.d(TAG, "onSubtitleData");
        mSubtitleRenderer.parseSubtitle(new ByteArrayInputStream(data.getData()));
        mSubtitleRenderer.startRendering(mMediaPlayer.getCurrentPosition());
    }

    private void setTimeOnView(TextView tv, int time) {
        tv.setText(String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(time), TimeUnit.MILLISECONDS.toMinutes(time)
                        - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                TimeUnit.MILLISECONDS.toSeconds(time)
                        - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
    }

    @Override
    public void onOutputControlInfo(MediaPlayer mp, OutputControlInfo info) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onOutputBlocked(MediaPlayer mp, OutputBlockedInfo info) {
        // TODO Auto-generated method stub
    }

}
