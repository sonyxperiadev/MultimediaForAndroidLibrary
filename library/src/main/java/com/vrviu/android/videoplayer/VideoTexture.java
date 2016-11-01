package com.vrviu.android.videoplayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import java.util.Random;

public class VideoTexture implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "VideoTexture";

    private static final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec4 vTexCoordinate;" +
                    "uniform mat4 textureTransform;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main() {" +
                    "   v_TexCoordinate = (textureTransform * vTexCoordinate).xy;" +
                    "   gl_Position = vPosition;" +
                    "}";
    private static final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "uniform samplerExternalOES texture;" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main () {" +
                    "    vec4 color = texture2D(texture, v_TexCoordinate);" +
                    "    gl_FragColor = color;" +
                    "}";

    private float[] videoTextureTransform;

    int vertexShaderHandle;
    int fragmentShaderHandle;
    private int shaderProgram;
    private FloatBuffer vertexBuffer;
    private ShortBuffer drawListBuffer;
    private static Random random;

    private boolean m_bFrameAvailable = false;

    public SurfaceTexture videoTexture;

    // size to display onscreen
    private static float squareSize = 1.0f;
    private static float squareCoords[] = { -squareSize,  squareSize, 0.0f,   // top left
            -squareSize, -squareSize, 0.0f,   // bottom left
            squareSize, -squareSize, 0.0f,   // bottom right
            squareSize,  squareSize, 0.0f }; // top right
    private static short drawOrder[] = { 0, 1, 2, 0, 2, 3};  // order to draw vertices
    static final int COORDS_PER_VERTEX = 3;
    private final int TEXTURE_COORDINATE_DATA_SIZE = 4;

    // texture coordinates used to display the video texture
    private FloatBuffer textureBuffer;
    private float textureCoords[] = { 0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f };

    private OnRenderVideoFrameListener mOnRenderVideoFrameListener;
    private final Object mListenerLock = new Object();
    private static int m_FrameBufferObject[]=null;
	private static int m_FboWidth=0;
	private static int m_FboHeight=0;
    public int m_UnityTextureID;
    public int m_SurfaceTextureID;

    /**
     * Interface definition of a callback to be invoked when a frame
     * of the video texture is available to be rendered.
     */
    public interface OnRenderVideoFrameListener {

        /**
         * Called to indicate that a frame is available to be rendered.
         */
        void onRenderVideoFrame();
    }

    /**
     * Sets the listener for OnCompletion. Called when finished playback of
     * media.
     *
     * @param listener the listener to be set.
     */
    public void setOnRenderFrameListener(OnRenderVideoFrameListener listener) {
        synchronized (mListenerLock) {
            mOnRenderVideoFrameListener = listener;
        }
    }


    public VideoTexture(int textureID)
    {
        videoTextureTransform = new float[16];

        // set up vertex buffer
        setupVertexBuffer();

        // set up texture
        setupTexture(textureID);

        // set up shaders
        loadShaders();
        random = new Random();

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {
        //Log.i(TAG, " >>> VideoTexture onFrameAvailable!");
        synchronized (this)
        {
            //Log.i(TAG, " >>> VideoTexture set onFrameAvailable!");
            m_bFrameAvailable = true;

            // request render
            if (this.mOnRenderVideoFrameListener != null) {
                this.mOnRenderVideoFrameListener.onRenderVideoFrame();
            }

        }
    }

    public void setUnityTextureID( int textureId )
    {
        m_UnityTextureID = textureId;
    }

    private void loadShaders()
    {
        vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderHandle, vertexShaderCode);
        GLES20.glCompileShader(vertexShaderHandle);

        fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderHandle, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShaderHandle);

        shaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(shaderProgram, vertexShaderHandle);
        GLES20.glAttachShader(shaderProgram, fragmentShaderHandle);
        GLES20.glLinkProgram(shaderProgram);
    }

    private void setupVertexBuffer()
    {
        // Draw list buffer
        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder. length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        // Initialize the texture holder
        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());

        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }

    private void setupTexture(int textureID)
    {
        Log.i(TAG, " >>> setupTexture!!! textureID=" + textureID);
        m_SurfaceTextureID = textureID;

        ByteBuffer texturebb = ByteBuffer.allocateDirect(textureCoords.length * 4);
        texturebb.order(ByteOrder.nativeOrder());
        textureBuffer = texturebb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);

        // create surface texture
        videoTexture = new SurfaceTexture(m_SurfaceTextureID);
        videoTexture.setOnFrameAvailableListener(this);
    }

    public boolean isFrameAvailable()
    {
        return m_bFrameAvailable;
    }


    public void resizeFrameBufferObject( int width, int height )
    {
        if( m_FrameBufferObject!=null )
        {
            GLES20.glDeleteFramebuffers( m_FrameBufferObject.length, m_FrameBufferObject, 0 );
            m_FrameBufferObject = null;
        }
        m_FrameBufferObject = new int[1];

		m_FboWidth = width;
		m_FboHeight = height;
        GLES20.glGenFramebuffers( m_FrameBufferObject.length, m_FrameBufferObject, 0 );
        GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, m_FrameBufferObject[0] );
        GLES20.glFramebufferTexture2D( GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, m_UnityTextureID, 0 );
        GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, 0 );
    }

    public void updateTextureImage()
    {
        synchronized (this) {
            if (m_bFrameAvailable) {
				m_bFrameAvailable = false;
                Log.i(TAG, " >>> updateTextureImage videoTexture Timestamp=" + videoTexture.getTimestamp());
                boolean[] abValue = new boolean[1];
                GLES20.glGetBooleanv(GLES20.GL_DEPTH_TEST, abValue, 0);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_SurfaceTextureID );

				GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, m_FrameBufferObject[0] );
				GLES20.glDisable( GLES20.GL_DEPTH_TEST );
				GLES20.glDisable( GLES20.GL_SCISSOR_TEST );
				GLES20.glDisable( GLES20.GL_STENCIL_TEST );
				GLES20.glDisable( GLES20.GL_CULL_FACE );
				GLES20.glDisable( GLES20.GL_BLEND );
				GLES20.glViewport(0,0,m_FboWidth,m_FboHeight);

                videoTexture.updateTexImage();
                float[] mMat = new float[16];
                videoTexture.getTransformMatrix(mMat);

                if (abValue[0]) {
                    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                } else {

                }

                videoTexture.getTransformMatrix(videoTextureTransform);

                // add program to OpenGL ES Environment
                GLES20.glUseProgram(shaderProgram);

                // set Texture Handles and bind Texture
                int textureParamHandle = GLES20.glGetUniformLocation(shaderProgram, "texture");
                int textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoordinate");

                // get handle to vertex shader's vPosition member
                int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
                int textureTranformHandle = GLES20.glGetUniformLocation(shaderProgram, "textureTransform");

                // enable a handle to the triangle vertices
                GLES20.glEnableVertexAttribArray(positionHandle);

                // prepare the triangle coordinate data
                GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 4 * COORDS_PER_VERTEX, vertexBuffer);

                // bind the texture to this unit.
                GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_SurfaceTextureID );

                // set the active texture unit to texture unit 0.
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                // tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
                GLES20.glUniform1i(textureParamHandle, 0);

                // pass in the texture coordinate information
                GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
                GLES20.glVertexAttribPointer(textureCoordinateHandle, TEXTURE_COORDINATE_DATA_SIZE, GLES20.GL_FLOAT, false, 0, textureBuffer);

                // apply the projection and view transformation
                GLES20.glUniformMatrix4fv(textureTranformHandle, 1, false, videoTextureTransform, 0);

                // draw the triangles
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

                // disable Vertex Array
                GLES20.glDisableVertexAttribArray(positionHandle);
                GLES20.glDisableVertexAttribArray(textureCoordinateHandle);

                GLES20.glBindFramebuffer( GLES20.GL_FRAMEBUFFER, 0 );
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0 );
                abValue = null;

            }
        }
    }


    public boolean draw()
    {
        synchronized (this)
        {
            if (m_bFrameAvailable)
            {
                Log.i(TAG, " >>> m_bFrameAvailable!!!");
                Log.i(TAG, " >>> videoTexture Timestamp=" + videoTexture.getTimestamp());
                //videoTexture.updateTexImage();
                videoTexture.getTransformMatrix(videoTextureTransform);
                m_bFrameAvailable = false;

                Log.i(TAG, " >>> a");

                // add program to OpenGL ES Environment
                Log.i(TAG, " >>> c");
                GLES20.glUseProgram(shaderProgram);

                // set Texture Handles and bind Texture
                Log.i(TAG, " >>> d");
                int textureParamHandle = GLES20.glGetUniformLocation(shaderProgram, "texture");
                Log.i(TAG, " >>> e");
                int textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "vTexCoordinate");

                // get handle to vertex shader's vPosition member
                Log.i(TAG, " >>> f");
                int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition");
                Log.i(TAG, " >>> g");
                int textureTranformHandle = GLES20.glGetUniformLocation(shaderProgram, "textureTransform");

                // enable a handle to the triangle vertices
                Log.i(TAG, " >>> h");
                GLES20.glEnableVertexAttribArray(positionHandle);

                // prepare the triangle coordinate data
                Log.i(TAG, " >>> i");
                GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 4 * COORDS_PER_VERTEX, vertexBuffer);

                // bind the texture to this unit.
                Log.i(TAG, " >>> j");
                GLES20.glBindTexture( GLES11Ext.GL_TEXTURE_EXTERNAL_OES, m_SurfaceTextureID );

                // set the active texture unit to texture unit 0.
                Log.i(TAG, " >>> k");
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                // tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
                Log.i(TAG, " >>> l");
                GLES20.glUniform1i(textureParamHandle, 0);

                // pass in the texture coordinate information
                Log.i(TAG, " >>> m");
                GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
                Log.i(TAG, " >>> n");
                GLES20.glVertexAttribPointer(textureCoordinateHandle, TEXTURE_COORDINATE_DATA_SIZE, GLES20.GL_FLOAT, false, 0, textureBuffer);

                // apply the projection and view transformation
                Log.i(TAG, " >>> o");
                GLES20.glUniformMatrix4fv(textureTranformHandle, 1, false, videoTextureTransform, 0);

                // draw the triangles
                Log.i(TAG, " >>> p");
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

                // disable Vertex Array
                Log.i(TAG, " >>> q");
                GLES20.glDisableVertexAttribArray(positionHandle);
                Log.i(TAG, " >>> r");
                GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
                Log.i(TAG, " >>> s");

                return true;
            }
            else
            {
                Log.i(TAG, " >>> NO m_bFrameAvailable!!!");
                return false;
            }
        }

    }


}
