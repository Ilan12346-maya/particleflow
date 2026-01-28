package com.nfaralli.particleflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Super basic Activity:
 * Just create a GLSurfaceView and set it as the content view.
 * All the logic is in the GLSurfaceView, especially its renderer.
 */
public class MainActivity extends Activity {

    private ParticlesSurfaceView mGLView;
    private GearView mGearView;
    private SettingsView mSettingsView;
    private Dialog mSettingsDialog;
    private TextView mFpsTextView;
    private Handler mHandler = new Handler();
    private long mLastFpsUpdateTime = 0;
    private java.util.concurrent.atomic.AtomicInteger mFrameCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private Runnable mFpsRunnable;
    private boolean mShowFpsCached = false;
    private int mLastDisplayedFps = -1;
    private final StringBuilder mFpsStringBuilder = new StringBuilder(10);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.particles);
        mGLView = (ParticlesSurfaceView)findViewById(R.id.particles_view);
        mFpsTextView = (TextView)findViewById(R.id.fps_text_view);
        mGLView.setFrameRenderedListener(new ParticlesSurfaceView.FrameRenderedListener() {
            @Override
            public void onFrameRendered(long computeTimeNs, long renderTimeNs) {
                if (mShowFpsCached) {
                    mFrameCount.incrementAndGet();
                }
            }
        });
        mFpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mShowFpsCached) return;

                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - mLastFpsUpdateTime;
                if (elapsedTime > 0) {
                    int frames = mFrameCount.getAndSet(0);
                    int fps = (int) (frames * 1000L / elapsedTime);
                    
                    if (fps != mLastDisplayedFps) {
                        mLastDisplayedFps = fps;
                        mFpsStringBuilder.setLength(0);
                        mFpsStringBuilder.append(fps);
                        mFpsTextView.setText(mFpsStringBuilder);
                        mFpsTextView.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD));
                    }
                }
                mLastFpsUpdateTime = currentTime;
                mHandler.postDelayed(this, 200);
            }
        };
        mSettingsView = new SettingsView(this);
        mSettingsDialog = getSettingsDialog();
        mGearView = (GearView)findViewById(R.id.gear_view);
        mGearView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mGearView.isGearVisible()) {
                    mGLView.onPause();
                    mGearView.hideGear();
                    mSettingsDialog.show();
                } else {
                    mGearView.showGear();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLView.onPause();
        mGearView.hideGear();
        mHandler.removeCallbacks(mFpsRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs =
                getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("ShowSettingsHint", true)) {
            Toast.makeText(this, R.string.settings_hint, Toast.LENGTH_LONG).show();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("ShowSettingsHint", false);
            editor.apply();
        }
        
        mShowFpsCached = prefs.getBoolean("show_fps", false);
        mFpsTextView.setVisibility(mShowFpsCached ? View.VISIBLE : View.GONE);
        if (mShowFpsCached) {
            applyFpsSettings(prefs);
            mLastFpsUpdateTime = System.currentTimeMillis();
            mFrameCount.set(0);
            mLastDisplayedFps = -1;
            mHandler.removeCallbacks(mFpsRunnable);
            mHandler.postDelayed(mFpsRunnable, 200);
        }
        mGLView.onResume();
    }

    private void applyFpsSettings(SharedPreferences prefs) {
        if (!mShowFpsCached) return;
        mFpsTextView.setTextColor(prefs.getInt("fps_color", 0xFFFFFFFF));
        mFpsTextView.setTextSize(prefs.getInt("fps_font_size", 14));
        mFpsTextView.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD));
        
        int position = prefs.getInt("fps_position", 0);
        android.widget.RelativeLayout.LayoutParams params = 
            (android.widget.RelativeLayout.LayoutParams) mFpsTextView.getLayoutParams();
        
        // Reset all rules
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        params.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        params.removeRule(android.widget.RelativeLayout.LEFT_OF);
        
        switch (position) {
            case 0: // Upper Left
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
                break;
            case 1: // Upper Right
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
                break;
            case 2: // Lower Left
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
                break;
            case 3: // Lower Right
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
                break;
        }
        
        params.setMargins(0, 0, 0, 0);
        mFpsTextView.setLayoutParams(params);
    }

    @SuppressWarnings("deprecation")
    Dialog getSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings_title)
                .setIcon(R.drawable.gear_icon_00)
                .setView(mSettingsView)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mSettingsView.saveValues();
                        mGLView.onResume();
                        
                        SharedPreferences prefs = getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, MODE_PRIVATE);
                        mShowFpsCached = prefs.getBoolean("show_fps", false);
                        mFpsTextView.setVisibility(mShowFpsCached ? View.VISIBLE : View.GONE);
                        
                        mHandler.removeCallbacks(mFpsRunnable);
                        if (mShowFpsCached) {
                            applyFpsSettings(prefs);
                            mLastFpsUpdateTime = System.currentTimeMillis();
                            mFrameCount.set(0);
                            mLastDisplayedFps = -1;
                            mHandler.postDelayed(mFpsRunnable, 200);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mSettingsView.loadValues();
                        mGLView.onResume();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mSettingsView.loadValues();
                        mGLView.onResume();
                    }
                });
        return builder.create();
    }
}
