package com.nfaralli.particleflow;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.opengl.GLES31;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

public class ParticlesRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "ParticlesRenderer";

    private SharedPreferences mPrefs;
    private ParticlesSurfaceView mParticlesSurfaceView;

    private int mRenderProgram = 0;
    private int mComputeProgram = 0;
    
    // Render Uniforms (2D Optimized)
    private int uRScale, uROffset, uRPointSize;
    private float[] mScale = new float[2];
    private float[] mOffset = new float[2];

    // Compute Uniforms
    private int uCNumP, uCNumT, uCTouch, uCAtt, uCDrag, uCGradient;
    
    private int mWidth = 1;
    private int mHeight = 1;
    private int mPartCount;
    private int mParticleSize;
    private int mNumTouch;

    private int[] mSSBOs = new int[2];
    private int mCurrentBufferIndex = 0;
    
    private int mFbo = 0;
    private int mFboTex = 0;
    private int mRenderScale = 100;
    private int mScaledWidth = 1;
    private int mScaledHeight = 1;

    private int mGradientTex = 0;

    private float[] mTouchPos = new float[32]; // 16 vec2
    private final Object mTouchLock = new Object();

    private boolean mInitialized = false;
    private boolean mUseDoubleBuffer = false;

    // Vertex Shader: Ultra-Lean, hardware unpacking
    private final String mVertexShader =
        "#version 310 es\n" +
        "layout(location = 0) in vec4 aPosVel;\n" +
        "layout(location = 1) in uint aColor;\n" +
        "uniform vec2 uScale;\n" +
        "uniform vec2 uOffset;\n" +
        "uniform float uPointSize;\n" +
        "out lowp vec4 vColor;\n" +
        "void main() {\n" +
        "  gl_Position = vec4(aPosVel.xy * uScale + uOffset, 0.0, 1.0);\n" +
        "  gl_PointSize = uPointSize;\n" +
        "  vColor = unpackUnorm4x8(aColor);\n" +
        "}\n";

    private final String mFragmentShader =
        "#version 310 es\n" +
        "precision lowp float;\n" +
        "in lowp vec4 vColor;\n" +
        "out vec4 fragColor;\n" +
        "void main() { fragColor = vColor; }\n";

    // Compute Shader: Physics + Packed Color
    private final String mComputeShader =
        "#version 310 es\n" +
        "layout (local_size_x = 256) in;\n" +
        "precision highp float;\n" +
        "struct Particle { vec4 posVel; uint color; uint pad1; uint pad2; uint pad3; };\n" +
        "layout(std430, binding = 0) buffer In { Particle inP[]; };\n" +
        "layout(std430, binding = 1) buffer Out { Particle outP[]; };\n" +
        "\n" +
        "uniform int uNumP, uNumT;\n" +
        "uniform vec2 uT[16];\n" +
        "uniform float uAtt, uDrag;\n" +
        "uniform sampler2D uGradient;\n" +
        "\n" +
        "uint hash(uint x) { x = ((x >> 16) ^ x) * 0x45d9f3b1u; x = ((x >> 16) ^ x) * 0x45d9f3b1u; x = (x >> 16) ^ x; return x; }\n" +
        "\n" +
        "void main() {\n" +
        "  uint i = gl_GlobalInvocationID.x;\n" +
        "  if (i >= uint(uNumP)) return;\n" +
        "  \n" +
        "  vec4 data = inP[i].posVel;\n" +
        "  vec2 p = data.xy, v = data.zw; vec2 acc = vec2(0.0);\n" +
        "  for (int j = 0; j < uNumT; j++) {\n" +
        "    if (uT[j].x >= 0.0) {\n" +
        "      vec2 diff = uT[j] - p;\n" +
        "      float d2 = dot(diff, diff);\n" +
        "      if (d2 < 0.1) {\n" +
        "        float th = float(hash(i + uint(uNumT))) * 1.4629e-9;\n" +
        "        diff = vec2(cos(th), sin(th)); d2 = 1.0;\n" +
        "      }\n" +
        "      acc += (uAtt / d2) * diff;\n" +
        "    }\n" +
        "  }\n" +
        "  v = (v + acc) * uDrag; p += v;\n" +
        "  \n" +
        "  highp float sc = clamp(log(dot(v, v) + 1.0) / 4.5, 0.0, 1.0);\n" +
        "  vec4 c = texture(uGradient, vec2(sc, 0.5));\n" +
        "  uint packedColor = packUnorm4x8(c);\n" +
        "  \n" +
        "  outP[i].posVel = vec4(p, v);\n" +
        "  outP[i].color = packedColor;\n" +
        "}\n";

    public ParticlesRenderer(Context context, ParticlesSurfaceView view) {
        mPrefs = context.getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mParticlesSurfaceView = view;
        loadConfig();
    }

    private void loadConfig() {
        mPartCount = mPrefs.getInt("NumParticles", 1000000);
        mParticleSize = mPrefs.getInt("ParticleSize", 1);
        mNumTouch = mPrefs.getInt("NumAttPoints", 5);
        mUseDoubleBuffer = mPrefs.getBoolean("use_double_buffer", true);
        mRenderScale = mPrefs.getInt("RenderScale", 100);
        synchronized (mTouchLock) { for(int i=0; i<32; i++) mTouchPos[i] = -1.0f; }
    }

    public void onPrefsChanged() { loadConfig(); mInitialized = false; }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (mParticlesSurfaceView != null) {
            mParticlesSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
        
        GLES31.glDisable(GLES31.GL_DEPTH_TEST);
        GLES31.glDisable(GLES31.GL_STENCIL_TEST);
        GLES31.glDisable(GLES31.GL_BLEND);
        GLES31.glDisable(GLES31.GL_SCISSOR_TEST);

        mRenderProgram = createProgram(mVertexShader, mFragmentShader);
        uRScale = GLES31.glGetUniformLocation(mRenderProgram, "uScale");
        uROffset = GLES31.glGetUniformLocation(mRenderProgram, "uOffset");
        uRPointSize = GLES31.glGetUniformLocation(mRenderProgram, "uPointSize");

        mComputeProgram = createComputeProgram(mComputeShader);
        uCNumP = GLES31.glGetUniformLocation(mComputeProgram, "uNumP");
        uCNumT = GLES31.glGetUniformLocation(mComputeProgram, "uNumT");
        uCTouch = GLES31.glGetUniformLocation(mComputeProgram, "uT");
        uCAtt = GLES31.glGetUniformLocation(mComputeProgram, "uAtt");
        uCDrag = GLES31.glGetUniformLocation(mComputeProgram, "uDrag");
        uCGradient = GLES31.glGetUniformLocation(mComputeProgram, "uGradient");

        // Initialize Gradient LUT Texture
        int[] tex = new int[1];
        GLES31.glGenTextures(1, tex, 0);
        mGradientTex = tex[0];
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        updateGradient();
    }

    private void createFBO(int width, int height) {
        if (mFbo != 0) {
            GLES31.glDeleteFramebuffers(1, new int[]{mFbo}, 0);
            GLES31.glDeleteTextures(1, new int[]{mFboTex}, 0);
        }
        mScaledWidth = Math.max(1, (width * mRenderScale) / 100);
        mScaledHeight = Math.max(1, (height * mRenderScale) / 100);
        int[] fbos = new int[1], texs = new int[1];
        GLES31.glGenFramebuffers(1, fbos, 0); mFbo = fbos[0];
        GLES31.glGenTextures(1, texs, 0); mFboTex = texs[0];
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mFboTex);
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, mScaledWidth, mScaledHeight, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mFbo);
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, mFboTex, 0);
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);
    }

    public void updateGradient() {
        if (mGradientTex == 0) return;
        int width = 256;
        ByteBuffer bb = ByteBuffer.allocateDirect(width * 4).order(ByteOrder.nativeOrder());
        float[] hsvS = new float[3], hsvF = new float[3], tmp = new float[3];
        Color.colorToHSV(mPrefs.getInt("SlowColor", 0xFF0000FF), hsvS);
        Color.colorToHSV(mPrefs.getInt("FastColor", 0xFFFF0000), hsvF);
        float sh = hsvS[0]/360f, fh = hsvF[0]/360f;
        int dir = mPrefs.getInt("HueDirection", 0);
        if (sh < fh && dir == 0) sh += 1.0f; else if (sh > fh && dir == 1) fh += 1.0f;
        for (int i = 0; i < width; i++) {
            float t = i / (float)(width - 1);
            tmp[0] = (((1.0f - t) * sh + t * fh) % 1.0f) * 360f;
            tmp[1] = (1.0f - t) * hsvS[1] + t * hsvF[1];
            tmp[2] = (1.0f - t) * hsvS[2] + t * hsvF[2];
            int c = Color.HSVToColor(tmp);
            bb.put((byte) Color.red(c)); bb.put((byte) Color.green(c)); bb.put((byte) Color.blue(c)); bb.put((byte) 255);
        }
        bb.position(0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, width, 1, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, bb);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        mWidth = width; mHeight = height;
        GLES31.glViewport(0, 0, width, height);
        createFBO(width, height);
        mScale[0] = 2.0f / (float)width;
        mScale[1] = 2.0f / (float)height;
        mOffset[0] = -1.0f;
        mOffset[1] = -1.0f;
        mInitialized = false;
    }

    private void initBuffers() {
        if (mSSBOs[0] != 0) GLES31.glDeleteBuffers(2, mSSBOs, 0);
        GLES31.glGenBuffers(2, mSSBOs, 0);
        int structSize = 32;
        ByteBuffer bb = ByteBuffer.allocateDirect(mPartCount * structSize).order(ByteOrder.nativeOrder());
        Random r = new Random();
        float radius = (float) Math.sqrt(mWidth*mWidth + mHeight*mHeight) / 2.0f;
        for (int i = 0; i < mPartCount; i++) {
            float rad = radius * (float)Math.sqrt(r.nextFloat());
            float theta = r.nextFloat() * 6.2831853f;
            bb.putFloat(mWidth/2.0f + rad * (float)Math.cos(theta)); // pos.x
            bb.putFloat(mHeight/2.0f + rad * (float)Math.sin(theta)); // pos.y
            bb.putFloat(0f); bb.putFloat(0f); // vel.xy
            bb.putInt(0); bb.putInt(0); bb.putInt(0); bb.putInt(0); // color + pads
        }
        bb.position(0);
        for (int i = 0; i < 2; i++) {
            GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, mSSBOs[i]);
            GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, mPartCount * structSize, bb, GLES31.GL_DYNAMIC_DRAW);
        }
        resetAttractionPoints();
        mInitialized = true;
    }

    public void setTouch(int index, float x, float y) {
        if (index >= 16) return;
        synchronized (mTouchLock) {
            if (x < 0) { mTouchPos[2 * index] = -1.0f; mTouchPos[2 * index + 1] = -1.0f; }
            else { mTouchPos[2 * index] = x; mTouchPos[2 * index + 1] = mHeight - y; }
        }
    }

    public void syncTouch() {}

    public void resetAttractionPoints() {
        if (mWidth <= 1) return;
        float l = (mWidth < mHeight ? mWidth : mHeight) / 3.0f;
        setTouch(0, mWidth / 2.0f, mHeight / 2.0f + (mNumTouch == 1 ? 0 : l));
        for (int i = 1; i < mNumTouch; i++) {
            setTouch(i, (float)(mWidth/2f + l*Math.sin(i*6.28/mNumTouch)), (float)(mHeight/2f + l*Math.cos(i*6.28/mNumTouch)));
        }
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (!mInitialized) { if (mWidth > 1) initBuffers(); else return; }

        int inB = mSSBOs[mCurrentBufferIndex], outB = mSSBOs[1 - mCurrentBufferIndex];
        if (!mUseDoubleBuffer) inB = outB = mSSBOs[0];

        // 1. Compute Pass
        GLES31.glUseProgram(mComputeProgram);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, inB);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, outB);
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glUniform1i(uCGradient, 0);
        GLES31.glUniform1i(uCNumP, mPartCount); GLES31.glUniform1i(uCNumT, mNumTouch);
        synchronized (mTouchLock) { GLES31.glUniform2fv(uCTouch, 16, mTouchPos, 0); }
        GLES31.glUniform1f(uCAtt, (float)mPrefs.getInt("F01Attraction", 100));
        GLES31.glUniform1f(uCDrag, 1.0f - mPrefs.getInt("F01Drag", 4)/100f);
        GLES31.glDispatchCompute((mPartCount + 255) / 256, 1, 1);
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT | GLES31.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
        
        // 2. Render Pass
        if (mRenderScale < 100) {
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, mFbo);
            GLES31.glViewport(0, 0, mScaledWidth, mScaledHeight);
        } else {
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);
            GLES31.glViewport(0, 0, mWidth, mHeight);
        }

        int bg = mPrefs.getInt("BGColor", 0xFF000000);
        GLES31.glClearColor(Color.red(bg)/255f, Color.green(bg)/255f, Color.blue(bg)/255f, 1f);
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        GLES31.glUseProgram(mRenderProgram);
        GLES31.glUniform2fv(uRScale, 1, mScale, 0);
        GLES31.glUniform2fv(uROffset, 1, mOffset, 0);
        GLES31.glUniform1f(uRPointSize, (float)mParticleSize);
        
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, outB);
        GLES31.glEnableVertexAttribArray(0);
        GLES31.glVertexAttribPointer(0, 4, GLES31.GL_FLOAT, false, 32, 0);
        GLES31.glEnableVertexAttribArray(1);
        GLES31.glVertexAttribIPointer(1, 1, GLES31.GL_UNSIGNED_INT, 32, 16);
        GLES31.glDrawArrays(GLES31.GL_POINTS, 0, mPartCount);
        
        if (mRenderScale < 100) {
            GLES31.glBindFramebuffer(GLES31.GL_READ_FRAMEBUFFER, mFbo);
            GLES31.glBindFramebuffer(GLES31.GL_DRAW_FRAMEBUFFER, 0);
            GLES31.glBlitFramebuffer(0, 0, mScaledWidth, mScaledHeight, 0, 0, mWidth, mHeight, GLES31.GL_COLOR_BUFFER_BIT, GLES31.GL_LINEAR);
            GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);
        }

        if (mUseDoubleBuffer) mCurrentBufferIndex = 1 - mCurrentBufferIndex;
        if (mParticlesSurfaceView != null) mParticlesSurfaceView.notifyFrameRendered(0, 0);
    }

    private int createProgram(String v, String f) { int vs = loadShader(GLES31.GL_VERTEX_SHADER, v), fs = loadShader(GLES31.GL_FRAGMENT_SHADER, f); int p = GLES31.glCreateProgram(); GLES31.glAttachShader(p, vs); GLES31.glAttachShader(p, fs); GLES31.glLinkProgram(p); return p; }
    private int createComputeProgram(String c) { int cs = loadShader(GLES31.GL_COMPUTE_SHADER, c); int p = GLES31.glCreateProgram(); GLES31.glAttachShader(p, cs); GLES31.glLinkProgram(p); return p; }
    private int loadShader(int t, String c) {
        int s = GLES31.glCreateShader(t);
        GLES31.glShaderSource(s, c);
        GLES31.glCompileShader(s);
        int[] compiled = new int[1];
        GLES31.glGetShaderiv(s, GLES31.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) Log.e(TAG, "Shader error (" + t + "): " + GLES31.glGetShaderInfoLog(s));
        return s;
    }
}