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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.particles);
        mGLView = (ParticlesSurfaceView)findViewById(R.id.particles_view);
        mFpsTextView = (TextView)findViewById(R.id.fps_text_view);
        mGLView.setFrameRenderedListener(new ParticlesSurfaceView.FrameRenderedListener() {
            @Override
            public void onFrameRendered(long computeTimeNs, long renderTimeNs) {
                mFrameCount.incrementAndGet();
            }
        });
        mFpsRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - mLastFpsUpdateTime;
                if (elapsedTime > 0) {
                    int frames = mFrameCount.getAndSet(0);
                    int fps = (int) (frames * 1000L / elapsedTime);
                    
                    SharedPreferences prefs = getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, MODE_PRIVATE);
                    if (prefs.getBoolean("show_fps", false)) {
                        mFpsTextView.setText(String.format(Locale.US, "FPS: %d", fps));
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
            editor.commit();
        }
        // Update display visibility based on preferences
        boolean showFps = prefs.getBoolean("show_fps", false);
        mFpsTextView.setVisibility(showFps ? View.VISIBLE : View.GONE);
        if (showFps) {
            mLastFpsUpdateTime = System.currentTimeMillis();
            mFrameCount.set(0);
            mHandler.postDelayed(mFpsRunnable, 200);
        }
        mGLView.onResume();
    }

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
                        // Re-evaluate display visibility after settings change
                        SharedPreferences prefs = getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, MODE_PRIVATE);
                        boolean showFps = prefs.getBoolean("show_fps", false);
                        mFpsTextView.setVisibility(showFps ? View.VISIBLE : View.GONE);
                        if (showFps) {
                            mLastFpsUpdateTime = System.currentTimeMillis();
                            mFrameCount.set(0);
                            mHandler.removeCallbacks(mFpsRunnable);
                            mHandler.postDelayed(mFpsRunnable, 200);
                        } else {
                            mHandler.removeCallbacks(mFpsRunnable);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // reset the dialog with its previous values.
                        mSettingsView.loadValues();
                        mGLView.onResume();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // reset the dialog with its previous values.
                        mSettingsView.loadValues();
                        mGLView.onResume();
                    }
                });
        return builder.create();
    }
}