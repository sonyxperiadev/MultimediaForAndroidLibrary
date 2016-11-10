package com.vrviu.android.videoplayer;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import com.sonymobile.android.media.MediaPlayer;
import com.vrviu.dash.Orientation;

import java.io.IOException;

public class VideoPlayer implements VideoTexture.OnRenderVideoFrameListener {

    private static final String TAG = "VideoPlayer";

    private static VideoTexture mSprite;
    public static MediaPlayer mMediaPlayer;

    private OnRenderVideoListener mOnRenderVideoListener;
    private final Object mListenerLock = new Object();

    private static int mSurfaceTextureId;
    private int mErrorCode;
    private int mErrorCodeExtra;

    public native int InitNDK(Object obj);
    public native int InitApplication();
    public native void QuitApplication();
    public native void SetWindowSize(int iWidth,int iHeight,int iUnityTextureID,boolean bRockchip);
    public native void RenderScene(float [] fValue, int iTextureID,int iUnityTextureID);
    public native void SetManagerID(int iID);
    public native int GetManagerID();
    public native int InitExtTexture();
    public native int GetExtTexture();

    public native void SetUnityTextureID(int iTextureID);

    static
    {
        System.loadLibrary("OpenGLEngine");
    }



    /**
     * Interface definition of a callback to be invoked when a frame
     * of the video texture is available to be rendered.
     */
    public interface OnRenderVideoListener {

        /**
         * Called to indicate that a frame is available to be rendered.
         */
        void onRenderVideo();
    }

    /**
     * Sets the listener for OnCompletion. Called when updated render of video
     * media is available.
     *
     * @param listener the listener to be set.
     */
    public void setOnRenderVideoListener(OnRenderVideoListener listener) {
        synchronized (mListenerLock) {
            mOnRenderVideoListener = listener;
        }
    }

    public VideoPlayer()
    {
        int textureID = GetExtTexture();

        // initialize mSprite
        mSurfaceTextureId = textureID;
        mSprite = new VideoTexture(textureID);
        // set render frame listener
        mSprite.setOnRenderFrameListener(this);

        // create media player
        mMediaPlayer = new MediaPlayer();

        // set media player playback surface
        setVideoPlaybackSurface();
    }

    private void setVideoPlaybackSurface()
    {
        // set media player surface texture
        mMediaPlayer.mSurfaceTexture = mSprite.videoTexture;
        // create surface for video texture
        mMediaPlayer.mSurface = new Surface(mMediaPlayer.mSurfaceTexture);
        // set media player display to null so it will use the previously set surface to display the video
        mMediaPlayer.setDisplay(null);
    }

    public void storeDisplayTexture(int textureID)
    {
        mSprite.setUnityTextureID( textureID );
        fbo_width = mMediaPlayer.getVideoWidth();
        fbo_height = mMediaPlayer.getVideoHeight();
        mSprite.resizeFrameBufferObject(fbo_width, fbo_height);
        // set media player playback surface
        //setVideoPlaybackSurface();
    }

    public long getTimestamp()
    {
        long timestamp = 0L;

        if (mSprite!=null) {
            if (mSprite.videoTexture!=null) {
                Log.i(TAG, " >>> timestamp videoTexture getTimestamp()");
                timestamp = mSprite.videoTexture.getTimestamp();
            }
        }

        Log.i(TAG, " >>> timestamp videoTexture Timestamp=" + timestamp);
        int err = GLES20.glGetError();
        Log.i(TAG, " >>> timestamp videoTexture openglerror=" + err);
        return timestamp;
    }

    public void draw()
    {
        Log.i(TAG, " >>> draw()");
        if (mSprite!=null) {
            mSprite.draw();
        }
    }

    public int getDuration()
    {
        if (mMediaPlayer!=null) {
            return mMediaPlayer.getDuration();
        }
        return -1;
    }

    public int getError()
    {
        return mErrorCode;
    }

    public int getErrorExtra()
    {
        return mErrorCodeExtra;
    }

    public int getOpenGLTextureID()
    {
        int err = GLES20.glGetError();
        if (mSprite!=null) {
            Log.i(TAG, " >>> getOpenGLTextureID() m_SurfaceTextureID=" +  mSprite.m_SurfaceTextureID);
            return mSprite.m_SurfaceTextureID;
        }
        return 0;
    }

    public int getStateValue()
    {
        if (mMediaPlayer!=null) {
            return mMediaPlayer.getStateValue();
        }
        return 0;
    }

    public int getVideoHeight()
    {
        if (mMediaPlayer!=null) {
            int err = GLES20.glGetError();
            return mMediaPlayer.getVideoHeight();
        }
        return 0;
    }

    public int getVideoWidth()
    {
        if (mMediaPlayer!=null) {
            int err = GLES20.glGetError();
            if( err!=0 ) Log.i(TAG, " >>> getVideoWidth openglerror=" + err);
            return mMediaPlayer.getVideoWidth();
        }
        return 0;
    }

    public int getVideoTextureID()
    {
        if (mSprite!=null) {
            int err = GLES20.glGetError();
            if( err!=0 ) Log.i(TAG, " >>> getVideoTextureID pre! err=" + err);
            int ret = mSprite.m_SurfaceTextureID;
            Log.i(TAG, " >>> mSprite.m_SurfaceTextureID=" + mSprite.m_SurfaceTextureID);
            err = GLES20.glGetError();
            if( err!=0 ) Log.i(TAG, " >>> getVideoTextureID post! err=" + err);
            return ret;
        }
        return 0;
    }

    public boolean isUpdateFrame()
    {
        if (mSprite!=null) {
            return mSprite.isFrameAvailable();
        }

        return false;
    }

    public boolean prepare()
    {
        if (mMediaPlayer!=null) {
            int err = GLES20.glGetError();
            if( err!=0 ) Log.i(TAG, " >>> prepare pre! err=" + err);
            boolean ret = mMediaPlayer.prepare();
            err = GLES20.glGetError();
            if( err!=0 ) Log.i(TAG, " >>> prepare post! err=" + err);
            return ret;
        }
        int err = GLES20.glGetError();
        if( err!=0 ) Log.i(TAG, " >>> prepare pre! err=" + err);

        return false;
    }

    public void play()
    {
        if (mMediaPlayer!=null) {
            int err = GLES20.glGetError();
            Log.i(TAG, " >>> play pre! err=" + err);
            mMediaPlayer.play();
            err = GLES20.glGetError();
            Log.i(TAG, " >>> play post! err=" + err);
        }
    }

    public void pause()
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.pause();
        }
    }

    public void play(int iSeek)
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.seekTo(iSeek);
        }
    }

    public void release()
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.release();
        }
    }

    public void reset()
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.reset();
        }
    }

    public void seekTo(int iSeek)
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.seekTo(iSeek);
        }
    }

    public void setDataSource(String sDataSource)
    {
        if (mMediaPlayer!=null) {
            try {
                int err = GLES20.glGetError();
                Log.i(TAG, " >>> prepare setDataSource pre! err=" + err);
                mMediaPlayer.setDataSource(sDataSource);
                err = GLES20.glGetError();
                if( err!=0 ) Log.i(TAG, " >>> prepare setDataSource post! err=" + err);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setSpeed(float fSpeed)
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.setSpeed(fSpeed);
        }
    }

    public void setVolume(float fVolume)
    {
        setVolume(fVolume, fVolume);
    }

    public void setVolume(float fLVolume, float fRVolume)
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.setVolume(fLVolume, fRVolume);
        }
    }

    public void setOrientation(double yaw, double pitch, double roll)
    {
        if (mMediaPlayer!=null) {
            Orientation orientation = new Orientation();
            orientation.setYPR(yaw, pitch, roll);
            mMediaPlayer.setOrientation(orientation);
        }
    }
    public void stop()
    {
        if (mMediaPlayer!=null) {
            mMediaPlayer.stop();
        }
    }

    private static int fbo_width = -1;
    private static int fbo_height = -1;
    public static void updateVideoTexture()
    {
        try {
            if (mSprite!=null) {
                if (mSprite.videoTexture!=null) {
                    int err = GLES20.glGetError();
                    if( err!=0) Log.i(TAG, " >>> updateVideoTexture pre! err=" + err);
                    if( fbo_width!=mMediaPlayer.getVideoWidth() || fbo_height!=mMediaPlayer.getVideoHeight() ) {
                        fbo_width = mMediaPlayer.getVideoWidth();
                        fbo_height = mMediaPlayer.getVideoHeight();
                        mSprite.resizeFrameBufferObject(fbo_width, fbo_height);
                    }

                    mSprite.updateTextureImage();
                }
            }
        }
        catch (Exception e)
        {
            Log.i(TAG, " >>> updateVideoTexture Exception = " + e.toString());
        }
    }

    @Override
    public void onRenderVideoFrame()
    {
        if (mMediaPlayer!=null) {
            long timeMillis = System.currentTimeMillis();
            long angle = timeMillis*10/1000;
            Orientation orientation = new Orientation();
            orientation.setYaw((int) (angle%360)-180);

            mMediaPlayer.setOrientation(orientation);
        }

        // request render
        if (this.mOnRenderVideoListener != null) {
            Log.i(TAG, " >>> onRenderVideoFrame()");
            this.mOnRenderVideoListener.onRenderVideo();
        }

    }

}
