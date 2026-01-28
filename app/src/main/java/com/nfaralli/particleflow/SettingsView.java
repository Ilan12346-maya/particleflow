package com.nfaralli.particleflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.TextView;

public class SettingsView extends FrameLayout {

    private ValidatedEditText mNumParticles;
    private ValidatedEditText mParticleSize;
    private ValidatedEditText mNumAttPoints;
    private ValidatedEditText mF01Attraction;
    private ValidatedEditText mF01Drag;
    private SeekBar mF01AttractionSeekBar;
    private SeekBar mF01DragSeekBar;
    private ColorView mBGColor;
    private ColorView mSlowPColor;
    private ColorView mFastPColor;
    private GradientView mBGGradientView;
    private GradientView mPartGradientView;
    private Spinner mHueDirection;
    private CheckBox mShowFpsCheckBox;
    private View mFpsControlsContainer;
    private ColorView mFpsColor;
    private SeekBar mFpsFontSize;
    private TextView mFpsFontSizeLabel;
    private Spinner mFpsPosition;

    private CheckBox mUseDoubleBufferCheckBox;
    private CheckBox mConstantSpeedCheckBox;
    private CheckBox mColorCorrectionCheckBox;
    private CheckBox mMotionBlurCheckBox;
    private CheckBox mAlphaBlendingCheckBox;
    private CheckBox mGlowModeCheckBox;
    
    private SeekBar mGlowIntensity;
    private TextView mGlowIntensityLabel;
    private SeekBar mBlurStrength;
    private TextView mBlurStrengthLabel;
    private SeekBar mWorkgroupSize;
    private TextView mWorkgroupSizeLabel;
    
    private SharedPreferences mPrefs;

    public SettingsView(Context context) {
        super(context);
        addView(inflate(getContext(), R.layout.settings, null));
        
        mNumParticles = (ValidatedEditText)findViewById(R.id.numParticles);
        mParticleSize = (ValidatedEditText)findViewById(R.id.particleSize);
        mNumAttPoints = (ValidatedEditText)findViewById(R.id.numAPoints);
        mBGColor = (ColorView)findViewById(R.id.bgColor);
        mSlowPColor = (ColorView)findViewById(R.id.slowColor);
        mFastPColor = (ColorView)findViewById(R.id.fastColor);
        mBGGradientView = (GradientView)findViewById(R.id.bgGradientView);
        mPartGradientView = (GradientView)findViewById(R.id.gradientView);
        mHueDirection = (Spinner)findViewById(R.id.hueDirection);
        mF01Attraction = (ValidatedEditText)findViewById(R.id.f01_attraction);
        mF01Drag = (ValidatedEditText)findViewById(R.id.f01_drag);
        
        mF01AttractionSeekBar = (SeekBar)findViewById(R.id.f01_attraction_seekbar);
        mF01DragSeekBar = (SeekBar)findViewById(R.id.f01_drag_seekbar);
        mF01AttractionSeekBar.setMax(1000);
        mF01DragSeekBar.setMax(100);

        mShowFpsCheckBox = (CheckBox)findViewById(R.id.showFps);
        mFpsControlsContainer = findViewById(R.id.fpsControlsContainer);
        mFpsColor = (ColorView)findViewById(R.id.fpsColor);
        mFpsFontSize = (SeekBar)findViewById(R.id.fpsFontSize);
        mFpsFontSizeLabel = (TextView)findViewById(R.id.fpsFontSizeLabel);
        mFpsPosition = (Spinner)findViewById(R.id.fpsPosition);

        mUseDoubleBufferCheckBox = (CheckBox)findViewById(R.id.useDoubleBuffer);
        mConstantSpeedCheckBox = (CheckBox) findViewById(R.id.constant_speed_checkbox);
        mColorCorrectionCheckBox = (CheckBox) findViewById(R.id.color_correction_checkbox);
        mMotionBlurCheckBox = (CheckBox) findViewById(R.id.motionBlur);
        mAlphaBlendingCheckBox = (CheckBox) findViewById(R.id.alphaBlending);
        mGlowModeCheckBox = (CheckBox) findViewById(R.id.glowMode);
        
        mGlowIntensity = (SeekBar) findViewById(R.id.glowIntensity);
        mGlowIntensityLabel = (TextView) findViewById(R.id.glowIntensityLabel);
        mBlurStrength = (SeekBar) findViewById(R.id.blurStrength);
        mBlurStrengthLabel = (TextView) findViewById(R.id.blurStrengthLabel);
        mWorkgroupSize = (SeekBar) findViewById(R.id.workgroupSize);
        mWorkgroupSizeLabel = (TextView) findViewById(R.id.workgroupSizeLabel);

        mNumParticles.setMinValue(1);
        mNumParticles.setMaxValue(ParticlesSurfaceView.MAX_NUM_PARTICLES);
        mParticleSize.setMinValue(1);
        mParticleSize.setMaxValue(100);
        mNumAttPoints.setMinValue(1);
        mNumAttPoints.setMaxValue(ParticlesSurfaceView.MAX_MAX_NUM_ATT_POINTS);
        mF01Attraction.setMinValue(0);
        mF01Attraction.setMaxValue(1000);
        mF01Drag.setMinValue(0);
        mF01Drag.setMaxValue(100);

        mPrefs = context.getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, Context.MODE_PRIVATE);

        setupListeners();
        loadValues();
    }

    private void setupListeners() {
        // --- Sync Listeners for Attraction ---
        mF01AttractionSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) mF01Attraction.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mF01Attraction.setOnTextChangedListener(new ValidatedEditText.OnTextChangedListener() {
            @Override
            public void onTextChanged(String str) {
                try {
                    int val = Integer.parseInt(str);
                    if (val != mF01AttractionSeekBar.getProgress()) {
                        mF01AttractionSeekBar.setProgress(val);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // --- Sync Listeners for Drag ---
        mF01DragSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) mF01Drag.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        mF01Drag.setOnTextChangedListener(new ValidatedEditText.OnTextChangedListener() {
            @Override
            public void onTextChanged(String str) {
                try {
                    int val = Integer.parseInt(str);
                    if (val != mF01DragSeekBar.getProgress()) {
                        mF01DragSeekBar.setProgress(val);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        mBGColor.setOnValueChangedListener(new ColorView.OnValueChangedListener() {
            @Override
            public void onValueChanged(int color) {
                mBGGradientView.setLeftColor(color);
                mBGGradientView.setRightColor(color);
                mBGGradientView.invalidate();
            }
        });
        mSlowPColor.setOnValueChangedListener(new ColorView.OnValueChangedListener() {
            @Override
            public void onValueChanged(int color) {
                mPartGradientView.setLeftColor(color);
                mPartGradientView.invalidate();
            }
        });
        mFastPColor.setOnValueChangedListener(new ColorView.OnValueChangedListener() {
            @Override
            public void onValueChanged(int color) {
                mPartGradientView.setRightColor(color);
                mPartGradientView.invalidate();
            }
        });
        
        mShowFpsCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVisibility();
            }
        });

        mFpsFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mFpsFontSizeLabel.setText("FPS Font Size: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        mHueDirection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mPartGradientView.setHueDirection(position == 0);
                mPartGradientView.invalidate();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        mMotionBlurCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVisibility();
            }
        });

        mGlowModeCheckBox.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                updateVisibility();
            }
        });

        mGlowIntensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mGlowIntensityLabel.setText("Glow Intensity: " + String.format("%.1f", (progress + 1) / 10.0f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mBlurStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBlurStrengthLabel.setText("Blur Strength: " + (progress / 100.0f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mWorkgroupSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mWorkgroupSizeLabel.setText("Workgroup Size: " + ((progress + 1) * 32));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.resetButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadDefaultValues();
            }
        });
    }

    private void updateVisibility() {
        mFpsControlsContainer.setVisibility(mShowFpsCheckBox.isChecked() ? View.VISIBLE : View.GONE);

        int blurVis = mMotionBlurCheckBox.isChecked() ? View.VISIBLE : View.GONE;
        mBlurStrength.setVisibility(blurVis);
        mBlurStrengthLabel.setVisibility(blurVis);

        int glowVis = mGlowModeCheckBox.isChecked() ? View.VISIBLE : View.GONE;
        mGlowIntensity.setVisibility(glowVis);
        mGlowIntensityLabel.setVisibility(glowVis);
    }

    public void loadValues() {
        mNumParticles.setText(String.valueOf(mPrefs.getInt("NumParticles", ParticlesSurfaceView.DEFAULT_NUM_PARTICLES)));
        mParticleSize.setText(String.valueOf(mPrefs.getInt("ParticleSize", ParticlesSurfaceView.DEFAULT_PARTICLE_SIZE)));
        mNumAttPoints.setText(String.valueOf(mPrefs.getInt("NumAttPoints", ParticlesSurfaceView.DEFAULT_MAX_NUM_ATT_POINTS)));
        mBGColor.setColor(mPrefs.getInt("BGColor", ParticlesSurfaceView.DEFAULT_BG_COLOR));
        mSlowPColor.setColor(mPrefs.getInt("SlowColor", ParticlesSurfaceView.DEFAULT_SLOW_COLOR));
        mFastPColor.setColor(mPrefs.getInt("FastColor", ParticlesSurfaceView.DEFAULT_FAST_COLOR));
        mHueDirection.setSelection(mPrefs.getInt("HueDirection", ParticlesSurfaceView.DEFAULT_HUE_DIRECTION));
        int f01Att = mPrefs.getInt("F01Attraction", ParticlesSurfaceView.DEFAULT_F01_ATTRACTION_COEF);
        mF01Attraction.setText(String.valueOf(f01Att));
        mF01AttractionSeekBar.setProgress(f01Att);
        int f01Drag = mPrefs.getInt("F01Drag", ParticlesSurfaceView.DEFAULT_F01_DRAG_COEF);
        mF01Drag.setText(String.valueOf(f01Drag));
        mF01DragSeekBar.setProgress(f01Drag);
        mShowFpsCheckBox.setChecked(mPrefs.getBoolean("show_fps", false));
        mFpsColor.setColor(mPrefs.getInt("fps_color", 0xFFFFFFFF));
        int fpsFs = mPrefs.getInt("fps_font_size", 14);
        mFpsFontSize.setProgress(fpsFs);
        mFpsFontSizeLabel.setText("FPS Font Size: " + fpsFs);
        mFpsPosition.setSelection(mPrefs.getInt("fps_position", 0));

        mUseDoubleBufferCheckBox.setChecked(mPrefs.getBoolean("use_double_buffer", false));
        mConstantSpeedCheckBox.setChecked(mPrefs.getBoolean("constant_speed", false));
        mColorCorrectionCheckBox.setChecked(mPrefs.getBoolean("color_correction", false));
        mMotionBlurCheckBox.setChecked(mPrefs.getBoolean("motion_blur", false));
        mAlphaBlendingCheckBox.setChecked(mPrefs.getBoolean("alpha_blending", false));
        mGlowModeCheckBox.setChecked(mPrefs.getBoolean("glow_mode", false));
        
        float gInt = mPrefs.getFloat("glow_intensity", 1.0f);
        mGlowIntensity.setProgress((int)(gInt * 10) - 1);
        mGlowIntensityLabel.setText("Glow Intensity: " + gInt);
        
        float bStr = mPrefs.getFloat("blur_strength", 1.0f);
        mBlurStrength.setProgress((int)(bStr * 100));
        mBlurStrengthLabel.setText("Trail Factor: " + bStr);

        int wgSize = mPrefs.getInt("WorkgroupSize", 256);
        mWorkgroupSize.setProgress((wgSize / 32) - 1);
        mWorkgroupSizeLabel.setText("Workgroup Size: " + wgSize);
        
        updateVisibility();
    }

    public void loadDefaultValues() {
        mNumParticles.setText(String.valueOf(ParticlesSurfaceView.DEFAULT_NUM_PARTICLES));
        mParticleSize.setText(String.valueOf(ParticlesSurfaceView.DEFAULT_PARTICLE_SIZE));
        mNumAttPoints.setText(String.valueOf(ParticlesSurfaceView.DEFAULT_MAX_NUM_ATT_POINTS));
        mBGColor.setColor(ParticlesSurfaceView.DEFAULT_BG_COLOR);
        mSlowPColor.setColor(ParticlesSurfaceView.DEFAULT_SLOW_COLOR);
        mFastPColor.setColor(ParticlesSurfaceView.DEFAULT_FAST_COLOR);
        mHueDirection.setSelection(ParticlesSurfaceView.DEFAULT_HUE_DIRECTION);
        mF01Attraction.setText(String.valueOf(ParticlesSurfaceView.DEFAULT_F01_ATTRACTION_COEF));
        mF01AttractionSeekBar.setProgress(ParticlesSurfaceView.DEFAULT_F01_ATTRACTION_COEF);
        mF01Drag.setText("4");
        mF01DragSeekBar.setProgress(4);
        mShowFpsCheckBox.setChecked(false);
        mFpsColor.setColor(0xFFFFFFFF);
        mFpsFontSize.setProgress(14);
        mFpsFontSizeLabel.setText("FPS Font Size: 14");
        mFpsPosition.setSelection(0);
        mShowFpsCheckBox.setChecked(false);
        mUseDoubleBufferCheckBox.setChecked(false);
        mConstantSpeedCheckBox.setChecked(false);
        mColorCorrectionCheckBox.setChecked(false);
        mMotionBlurCheckBox.setChecked(false);
        mAlphaBlendingCheckBox.setChecked(false);
        mGlowModeCheckBox.setChecked(false);
        mGlowIntensity.setProgress(9); // 1.0
        mGlowIntensityLabel.setText("Glow Intensity: 1.0");
        mBlurStrength.setProgress(100);
        mBlurStrengthLabel.setText("Trail Factor: 1.0");
        mWorkgroupSize.setProgress(7); // 256
        updateVisibility();
    }

    public void saveValues() {
        View focusedChild = getFocusedChild();
        if (focusedChild!= null) focusedChild.clearFocus();
        
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt("NumParticles", Integer.parseInt(mNumParticles.getText().toString()));
        editor.putInt("ParticleSize", Integer.parseInt(mParticleSize.getText().toString()));
        editor.putInt("NumAttPoints", Integer.parseInt(mNumAttPoints.getText().toString()));
        editor.putInt("BGColor", mBGColor.getColor());
        editor.putInt("SlowColor", mSlowPColor.getColor());
        editor.putInt("FastColor", mFastPColor.getColor());
        editor.putInt("HueDirection", mHueDirection.getSelectedItemPosition());
        editor.putInt("F01Attraction", Integer.parseInt(mF01Attraction.getText().toString()));
        editor.putInt("F01Drag", Integer.parseInt(mF01Drag.getText().toString()));
        editor.putBoolean("show_fps", mShowFpsCheckBox.isChecked());
        editor.putInt("fps_color", mFpsColor.getColor());
        editor.putInt("fps_font_size", mFpsFontSize.getProgress());
        editor.putInt("fps_position", mFpsPosition.getSelectedItemPosition());
        editor.putBoolean("use_double_buffer", mUseDoubleBufferCheckBox.isChecked());
        editor.putBoolean("constant_speed", mConstantSpeedCheckBox.isChecked());
        editor.putBoolean("color_correction", mColorCorrectionCheckBox.isChecked());
        editor.putBoolean("motion_blur", mMotionBlurCheckBox.isChecked());
        editor.putBoolean("alpha_blending", mAlphaBlendingCheckBox.isChecked());
        editor.putBoolean("glow_mode", mGlowModeCheckBox.isChecked());
        editor.putFloat("glow_intensity", (mGlowIntensity.getProgress() + 1) / 10.0f);
        editor.putFloat("blur_strength", mBlurStrength.getProgress() / 100.0f);
        editor.putInt("WorkgroupSize", (mWorkgroupSize.getProgress() + 1) * 32);
        editor.apply();
    }
}