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

    private final float[] mScaleVec = new float[2];
    private final float[] mOffsetVec = new float[] {-1.0f, -1.0f};

    private SharedPreferences mPrefs;
    private ParticlesSurfaceView mParticlesSurfaceView;

    private int mRenderProgram = 0;
    private int mComputeProgram = 0;

    // Render Uniforms
    private int uRScale, uROffset, uRPointSize, uRTimeScale, uRBlurStrength, uRGlowStrength, uRGradient, uRMode;
    // Compute Uniforms
    private int uCNumP, uCNumT, uCTouch, uCAtt, uCDrag, uCReset, uCRes;

    private int mWidth = 1;
    private int mHeight = 1;
    private int mPartCount;
    private int mParticleSize;
    private int mNumTouch;

    // --- Cached Preferences ---
    private boolean mConstantSpeed = false;
    private boolean mColorCorrection = false;
    private boolean mAlphaBlending = false;
    private boolean mGlowMode = false;
    private boolean mMotionBlur = false;
    private float mBlurStrength = 1.0f;
    private float mGlowIntensity = 1.0f;
    private int mF01Attraction = 100;
    private float mF01Drag = 0.96f;
    private int mBGColorValue = 0xFF000000;

    private int[] mSSBOs = new int[4]; // 0,1: Pos Ping-Pong (vec2 FP32); 2,3: Vel Ping-Pong (uint FP16)
    private int mCurrentBufferIndex = 0;

    private int mGradientTex = 0;

    private float[] mTouchPos = new float[32]; // 16 vec2
    private float[] mActiveTouchPos = new float[32];
    private int mActiveTouchCount = 0;
    private final Object mTouchLock = new Object();

    private boolean mInitialized = false;
    private boolean mNeedsReset = false;
    private boolean mUseDoubleBuffer = false;

    private long mLastFrameTimeNs = 0;

    // ================= SHADERS =================

    private final String mVertexShader =
        "#version 310 es\n" +
        "precision highp float;\n" +
        "layout(std430, binding = 0) readonly buffer PosB { vec2 pB[]; };\n" +
        "layout(std430, binding = 1) readonly buffer VelB { uint vB[]; };\n" +
        "uniform vec2 uScale, uOffset;\n" +
        "uniform mediump float uPointSize, uTimeScale, uBlurStrength, uGlowStrength;\n" +
        "uniform int uMode;\n" +
        "uniform mediump sampler2D uGradient;\n" +
        "out lowp vec4 vColor;\n" +
        "void main() {\n" +
        "  uint idx;\n" +
        "  if (uMode == 1) idx = uint(gl_VertexID / 3);\n" +
        "  else if (uMode == 2) idx = uint(gl_VertexID >> 1);\n" +
        "  else idx = uint(gl_VertexID);\n" +
        "  vec2 pos = pB[idx];\n" +
        "  vec2 vel = unpackHalf2x16(vB[idx]);\n" +
        "  mediump vec2 v = vel / uTimeScale;\n" +
        "  if (uMode == 1) {\n" +
        "    mediump float speed = length(v);\n" +
        "    mediump vec2 dir = (speed > 0.0001) ? normalize(vel) : vec2(1.0, 0.0);\n" +
        "    mediump vec2 ortho = vec2(-dir.y, dir.x) * (uPointSize * 0.5);\n" +
        "    mediump vec2 backEdge = pos - dir * (uPointSize * 0.5);\n" +
        "    int vID = gl_VertexID % 3;\n" +
        "    if (vID == 0) pos = backEdge - vel * uBlurStrength;\n" + // Tapered Tip
        "    else if (vID == 1) pos = backEdge + ortho;\n" + // Back Corner 1
        "    else pos = backEdge - ortho;\n" + // Back Corner 2
        "  } else if (uMode == 2 && (gl_VertexID & 1) != 0) {\n" +
        "    pos -= vel * uBlurStrength;\n" +
        "  }\n" +
        "  gl_Position = vec4(pos * uScale + uOffset, 0.0, 1.0);\n" +
        "  gl_PointSize = (uMode == 0) ? uPointSize : 1.0;\n" +
        "  mediump float d2 = dot(v, v);\n" +
        "  mediump float sc = clamp(log2(d2 + 1.0) * 0.15, 0.0, 1.0);\n" +
        "  vColor = texture(uGradient, vec2(sc, 0.5));\n" +
        "  vColor.rgb *= uGlowStrength;\n" +
        "  if (uMode == 1) {\n" +
        "    if ((gl_VertexID % 3) == 0) vColor.a = 0.0;\n" +
        "  } else if (uMode == 2 && (gl_VertexID & 1) != 0) {\n" +
        "    vColor.a = 0.0;\n" +
        "  }\n" +
        "}\n";

    private final String mFragmentShader =
        "#version 310 es\n" +
        "precision lowp float;\n" +
        "in lowp vec4 vColor;\n" +
        "out vec4 fragColor;\n" +
        "void main() { fragColor = vColor; }\n";

    private int mWorkgroupSize = 256;

    private String getComputeShaderCode() {
        return "#version 310 es\n" +
        "layout (local_size_x = " + mWorkgroupSize + ") in;\n" +
        "precision highp float;\n" +
        "layout(std430, binding = 0) readonly restrict buffer InPos { vec4 inP[]; };\n" +
        "layout(std430, binding = 1) readonly restrict buffer InVel { uvec2 inV[]; };\n" +
        "layout(std430, binding = 2) writeonly restrict buffer OutPos { vec4 outP[]; };\n" +
        "layout(std430, binding = 3) writeonly restrict buffer OutVel { uvec2 outV[]; };\n" +
        "uniform int uNumP, uNumT;\n" +
        "uniform bool uReset;\n" +
        "uniform vec2 uRes;\n" +
        "uniform vec2 uT[16];\n" +
        "uniform float uAtt, uDrag;\n" +
        "shared vec2 sharedT[16];\n" +
        "uint hash(uint x) { x = ((x >> 16) ^ x) * 0x45d9f3b1u; x = ((x >> 16) ^ x) * 0x45d9f3b1u; x = (x >> 16) ^ x; return x; }\n" +
        "void main() {\n" +
        "  uint i = gl_GlobalInvocationID.x;\n" +
        "  if (i >= uint(uNumP >> 1)) return;\n" +
        "  if (uReset) {\n" +
        "    uint idx = (i << 1);\n" +
        "    uint h1 = hash(idx), h2 = hash(idx + 1337u);\n" +
        "    vec2 r1 = vec2(float(h1 & 0xFFFFu), float(h1 >> 16)) * 1.5258e-5;\n" +
        "    vec2 r2 = vec2(float(h2 & 0xFFFFu), float(h2 >> 16)) * 1.5258e-5;\n" +
        "    vec2 rad = sqrt(vec2(r1.x, r2.x)) * min(uRes.x, uRes.y) * 0.45;\n" +
        "    vec2 th = vec2(r1.y, r2.y) * 6.2831853;\n" +
        "    outP[i] = uRes.xyxy * 0.5 + vec4(cos(th.x), sin(th.x), cos(th.y), sin(th.y)) * rad.xxyy;\n" +
        "    outV[i] = uvec2(packHalf2x16(vec2(0.0)), packHalf2x16(vec2(0.0)));\n" +
        "    return;\n" +
        "  }\n" +
        "  uint localId = gl_LocalInvocationIndex;\n" +
        "  if (localId < 16u) { sharedT[localId] = uT[localId]; }\n" +
        "  barrier();\n" +
        "  vec4 p = inP[i];\n" +
        "  vec2 v1 = unpackHalf2x16(inV[i].x), v2 = unpackHalf2x16(inV[i].y);\n" +
        "  vec2 acc1 = vec2(0.0), acc2 = vec2(0.0);\n" +
        "  for (int j = 0; j < uNumT; j++) {\n" +
        "    vec2 t = sharedT[j];\n" +
        "    vec4 d = t.xyxy - p;\n" +
        "    vec2 distSq = vec2(dot(d.xy, d.xy), dot(d.zw, d.zw));\n" +
        "    vec2 near = step(distSq, vec2(0.1));\n" +
        "    uint h = hash((i << 1) + uint(j));\n" +
        "    vec2 rnd = vec2(float(h & 0xFFFFu), float(h >> 16)) * 9.587e-5; // normalized to ~6.28\n" +
        "    vec4 noise = vec4(cos(rnd.x), sin(rnd.x), cos(rnd.y), sin(rnd.y));\n" +
        "    vec2 invDist = 1.0 / max(distSq, vec2(1.0));\n" +
        "    acc1 += uAtt * mix(d.xy * invDist.x, noise.xy, near.x);\n" +
        "    acc2 += uAtt * mix(d.zw * invDist.y, noise.zw, near.y);\n" +
        "  }\n" +
        "  v1 = (v1 + acc1) * uDrag; v2 = (v2 + acc2) * uDrag;\n" +
        "  p += vec4(v1, v2);\n" +
        "  outP[i] = p; outV[i] = uvec2(packHalf2x16(v1), packHalf2x16(v2));\n" +
        "}\n";
    }

    public ParticlesRenderer(Context context, ParticlesSurfaceView view) {
        mPrefs = context.getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        mParticlesSurfaceView = view;
        loadConfig();
    }

    private void loadConfig() {
        mPartCount = mPrefs.getInt("NumParticles", 1000000);
        if (mPartCount % 2 != 0) mPartCount++;
        mParticleSize = mPrefs.getInt("ParticleSize", 1);
        mNumTouch = mPrefs.getInt("NumAttPoints", 5);
        mUseDoubleBuffer = mPrefs.getBoolean("use_double_buffer", false);
        mWorkgroupSize = mPrefs.getInt("WorkgroupSize", 256);

        // --- Cache SharedPreferences ---
        mConstantSpeed = mPrefs.getBoolean("constant_speed", false);
        mColorCorrection = mPrefs.getBoolean("color_correction", false);
        mAlphaBlending = mPrefs.getBoolean("alpha_blending", false);
        mGlowMode = mPrefs.getBoolean("glow_mode", false);
        mMotionBlur = mPrefs.getBoolean("motion_blur", false);
        mBlurStrength = mPrefs.getFloat("blur_strength", 1.0f);
        mGlowIntensity = mPrefs.getFloat("glow_intensity", 1.0f);
        mF01Attraction = mPrefs.getInt("F01Attraction", 100);
        mF01Drag = 1.0f - mPrefs.getInt("F01Drag", 4) / 100f;
        mBGColorValue = mPrefs.getInt("BGColor", 0xFF000000);

        synchronized (mTouchLock) {
            for (int i = 0; i < 32; i++) mTouchPos[i] = -1.0f;
            updateActiveTouchList();
        }
    }

    public void onPrefsChanged() {
        int oldPartCount = mPartCount;
        loadConfig();
        if (mPartCount != oldPartCount) mNeedsReset = true;
        mInitialized = false;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        if (mParticlesSurfaceView != null) mParticlesSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        mRenderProgram = createProgram(mVertexShader, mFragmentShader);
        uRScale = GLES31.glGetUniformLocation(mRenderProgram, "uScale");
        uROffset = GLES31.glGetUniformLocation(mRenderProgram, "uOffset");
        uRPointSize = GLES31.glGetUniformLocation(mRenderProgram, "uPointSize");
        uRTimeScale = GLES31.glGetUniformLocation(mRenderProgram, "uTimeScale");
        uRBlurStrength = GLES31.glGetUniformLocation(mRenderProgram, "uBlurStrength");
        uRGlowStrength = GLES31.glGetUniformLocation(mRenderProgram, "uGlowStrength");
        uRGradient = GLES31.glGetUniformLocation(mRenderProgram, "uGradient");
        uRMode = GLES31.glGetUniformLocation(mRenderProgram, "uMode");

        mComputeProgram = createComputeProgram(getComputeShaderCode());
        uCNumP = GLES31.glGetUniformLocation(mComputeProgram, "uNumP");
        uCNumT = GLES31.glGetUniformLocation(mComputeProgram, "uNumT");
        uCTouch = GLES31.glGetUniformLocation(mComputeProgram, "uT");
        uCAtt = GLES31.glGetUniformLocation(mComputeProgram, "uAtt");
        uCDrag = GLES31.glGetUniformLocation(mComputeProgram, "uDrag");
        uCReset = GLES31.glGetUniformLocation(mComputeProgram, "uReset");
        uCRes = GLES31.glGetUniformLocation(mComputeProgram, "uRes");

        int[] tex = new int[1]; GLES31.glGenTextures(1, tex, 0); mGradientTex = tex[0];
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);
        updateGradient();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (!mInitialized) { if (mWidth > 1) { initBuffers(mNeedsReset || !mInitialized); mNeedsReset = false; } else return; }
        long now = System.nanoTime(); if (mLastFrameTimeNs == 0) mLastFrameTimeNs = now;
        float deltaTime = (now - mLastFrameTimeNs) * 1e-9f; mLastFrameTimeNs = now;

        int inPosB, outPosB, inVelB, outVelB;
        if (mUseDoubleBuffer) {
            inPosB = mSSBOs[mCurrentBufferIndex]; outPosB = mSSBOs[1 - mCurrentBufferIndex];
            inVelB = mSSBOs[mCurrentBufferIndex + 2]; outVelB = mSSBOs[1 - mCurrentBufferIndex + 2];
        } else { inPosB = outPosB = mSSBOs[0]; inVelB = outVelB = mSSBOs[2]; }

        float fpsFactor = 1.1f;
        if (mConstantSpeed) {
            if (deltaTime <= 0.0001f) deltaTime = 1.0f/120.0f;
            fpsFactor = (deltaTime * 120.0f) * 1.1f;
            if (fpsFactor > 12.0f) fpsFactor = 12.0f;
        }

        GLES31.glUseProgram(mComputeProgram);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, inPosB);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, inVelB);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, outPosB);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, outVelB);
        GLES31.glUniform1i(uCNumP, mPartCount);

        synchronized (mTouchLock) {
            GLES31.glUniform1i(uCNumT, mActiveTouchCount);
            GLES31.glUniform2fv(uCTouch, 16, mActiveTouchPos, 0);
        }

        GLES31.glUniform1f(uCAtt, (float)mF01Attraction * fpsFactor);
        GLES31.glUniform1f(uCDrag, mF01Drag);
        GLES31.glUniform2f(uCRes, (float)mWidth, (float)mHeight);

        GLES31.glDispatchCompute((mPartCount / 2 + mWorkgroupSize - 1) / mWorkgroupSize, 1, 1);
        GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);

        GLES31.glClearColor(Color.red(mBGColorValue)/255f, Color.green(mBGColorValue)/255f, Color.blue(mBGColorValue)/255f, 1f);
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        if (mAlphaBlending || mGlowMode) {
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES31.GL_SRC_ALPHA, mGlowMode ? GLES31.GL_ONE : GLES31.GL_ONE_MINUS_SRC_ALPHA);
        } else GLES31.glDisable(GLES31.GL_BLEND);

        GLES31.glUseProgram(mRenderProgram);
        GLES31.glUniform2fv(uRScale, 1, mScaleVec, 0);
        GLES31.glUniform2fv(uROffset, 1, mOffsetVec, 0);
        GLES31.glUniform1f(uRPointSize, (float)mParticleSize);
        GLES31.glUniform1f(uRTimeScale, mColorCorrection ? fpsFactor : 1.0f);
        GLES31.glUniform1f(uRBlurStrength, mMotionBlur ? mBlurStrength : 0.0f);
        GLES31.glUniform1f(uRGlowStrength, mGlowMode ? mGlowIntensity : 1.0f);
        GLES31.glUniform1i(uRMode, (mMotionBlur && mParticleSize > 1) ? 1 : 0);

        GLES31.glActiveTexture(GLES31.GL_TEXTURE0); GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glUniform1i(uRGradient, 0);

        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 0, outPosB);
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 1, outVelB);

        if (mMotionBlur) {
            if (mParticleSize > 1) {
                // Pass 1: Draw Triangle Trail behind the particle
                GLES31.glUniform1i(uRMode, 1);
                GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, mPartCount * 3);
                // Pass 2: Draw Square Head (Particle itself)
                GLES31.glUniform1i(uRMode, 0);
                GLES31.glDrawArrays(GLES31.GL_POINTS, 0, mPartCount);
            } else {
                // For size 1, a simple line is sufficient
                GLES31.glUniform1i(uRMode, 2);
                GLES31.glDrawArrays(GLES31.GL_LINES, 0, mPartCount * 2);
            }
        } else {
            GLES31.glUniform1i(uRMode, 0);
            GLES31.glDrawArrays(GLES31.GL_POINTS, 0, mPartCount);
        }

        if (mUseDoubleBuffer) mCurrentBufferIndex = 1 - mCurrentBufferIndex;
        if (mParticlesSurfaceView != null) mParticlesSurfaceView.notifyFrameRendered(0, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        mWidth = width; mHeight = height; GLES31.glViewport(0, 0, width, height);
        mScaleVec[0] = 2.0f / (float)width;
        mScaleVec[1] = 2.0f / (float)height;
        mInitialized = false;
    }

    private void initBuffers(boolean resetParticles) {
        if (mSSBOs[0] != 0 && resetParticles) { GLES31.glDeleteBuffers(4, mSSBOs, 0); mSSBOs[0] = 0; }
        if (mSSBOs[0] == 0) {
            GLES31.glGenBuffers(4, mSSBOs, 0);
            for (int i = 0; i < 4; i++) {
                GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, mSSBOs[i]);
                GLES31.glBufferData(GLES31.GL_SHADER_STORAGE_BUFFER, mPartCount * (i < 2 ? 4 : 2) * 2, null, GLES31.GL_DYNAMIC_DRAW);
            }
            resetParticles = true;
        }
        if (resetParticles) {
            GLES31.glUseProgram(mComputeProgram); GLES31.glUniform1i(uCReset, 1);
            GLES31.glUniform2f(uCRes, (float)mWidth, (float)mHeight); GLES31.glUniform1i(uCNumP, mPartCount);
            for (int i = 0; i < 2; i++) {
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 2, mSSBOs[i]);
                GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, 3, mSSBOs[i + 2]);
                GLES31.glDispatchCompute((mPartCount / 2 + mWorkgroupSize - 1) / mWorkgroupSize, 1, 1);
            }
            GLES31.glUniform1i(uCReset, 0); GLES31.glMemoryBarrier(GLES31.GL_SHADER_STORAGE_BARRIER_BIT);
        }
        resetAttractionPoints(); mInitialized = true;
    }

    public void setTouch(int index, float x, float y) {
        if (index >= 16) return;
        synchronized (mTouchLock) {
            if (x < 0) { mTouchPos[2 * index] = -1f; mTouchPos[2 * index + 1] = -1f; } else { mTouchPos[2 * index] = x; mTouchPos[2 * index + 1] = mHeight - y; }
            updateActiveTouchList();
        }
    }

    private void updateActiveTouchList() {
        mActiveTouchCount = 0;
        for (int i = 0; i < 16; i++) {
            if (mTouchPos[2 * i] >= 0.0f) {
                mActiveTouchPos[2 * mActiveTouchCount] = mTouchPos[2 * i];
                mActiveTouchPos[2 * mActiveTouchCount + 1] = mTouchPos[2 * i + 1];
                mActiveTouchCount++;
            }
        }
    }

    public void syncTouch() {}

    public void resetAttractionPoints() {
        if (mWidth <= 1) return;
        float l = Math.min(mWidth, mHeight) / 3f;
        setTouch(0, mWidth / 2f, mHeight / 2f + (mNumTouch == 1 ? 0 : l));
        for (int i = 1; i < mNumTouch; i++) {
            setTouch(i, (float) (mWidth / 2f + l * Math.sin(i * 6.28 / mNumTouch)), (float) (mHeight / 2f + l * Math.cos(i * 6.28 / mNumTouch)));
        }
    }

    private int createProgram(String v, String f) {
        int vs = loadShader(GLES31.GL_VERTEX_SHADER, v); int fs = loadShader(GLES31.GL_FRAGMENT_SHADER, f);
        int p = GLES31.glCreateProgram(); GLES31.glAttachShader(p, vs); GLES31.glAttachShader(p, fs);
        GLES31.glLinkProgram(p); return p;
    }

    private int createComputeProgram(String c) {
        int cs = loadShader(GLES31.GL_COMPUTE_SHADER, c);
        int p = GLES31.glCreateProgram(); GLES31.glAttachShader(p, cs); GLES31.glLinkProgram(p);
        return p;
    }

    private int loadShader(int type, String code) {
        int s = GLES31.glCreateShader(type);
        GLES31.glShaderSource(s, code); GLES31.glCompileShader(s);
        int[] compiled = new int[1]; GLES31.glGetShaderiv(s, GLES31.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) Log.e(TAG, "Shader error: " + GLES31.glGetShaderInfoLog(s));
        return s;
    }

    public void updateGradient() {
        if (mGradientTex == 0) return;
        int width = 256;
        ByteBuffer bb = ByteBuffer.allocateDirect(width * 4).order(ByteOrder.nativeOrder());
        float[] hsvSlow = new float[3], hsvFast = new float[3];
        
        // Always read fresh values from prefs to avoid sync issues
        int slowColor = mPrefs.getInt("SlowColor", 0xFF0000FF);
        int fastColor = mPrefs.getInt("FastColor", 0xFFFF0000);
        int hueDir = mPrefs.getInt("HueDirection", 0);

        Color.colorToHSV(slowColor, hsvSlow);
        Color.colorToHSV(fastColor, hsvFast);
        float sh = hsvSlow[0] / 360f, fh = hsvFast[0] / 360f;
        if (sh < fh && hueDir == 0) sh += 1f; else if (sh > fh && hueDir == 1) fh += 1f;
        float[] temp = new float[3];
        for (int i = 0; i < width; i++) {
            float t = i / (float) (width - 1);
            temp[0] = (((1f - t) * sh + t * fh) % 1f) * 360f;
            temp[1] = (1f - t) * hsvSlow[1] + t * hsvFast[1];
            temp[2] = (1f - t) * hsvSlow[2] + t * hsvFast[2];
            int c = Color.HSVToColor(temp);
            bb.put((byte) Color.red(c)); bb.put((byte) Color.green(c)); bb.put((byte) Color.blue(c)); bb.put((byte) 255);
        }
        bb.position(0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, mGradientTex);
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, width, 1, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, bb);
    }
}
