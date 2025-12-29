package com.nfaralli.particleflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler; // Added import
import android.view.View;
import android.widget.TextView; // Added import
import android.widget.Toast;

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
    private TextView mFpsTextView; // Added
    private Handler mHandler = new Handler(); // Added
    private long mLastFpsUpdateTime = 0; // Added
    private int mFrameCount = 0; // Added
    private Runnable mFpsRunnable; // Added

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.particles);
        mGLView = (ParticlesSurfaceView)findViewById(R.id.particles_view);
        mFpsTextView = (TextView)findViewById(R.id.fps_text_view); // Added
        mGLView.setFrameRenderedListener(new ParticlesSurfaceView.FrameRenderedListener() { // Added
            @Override
            public void onFrameRendered() { // Added
                mFrameCount++; // Added
            } // Added
        }); // Added
        mFpsRunnable = new Runnable() { // Added
            @Override // Added
            public void run() { // Added
                long currentTime = System.currentTimeMillis(); // Added
                long elapsedTime = currentTime - mLastFpsUpdateTime; // Added
                if (elapsedTime > 0) { // Added
                    int fps = (int) (mFrameCount * 1000L / elapsedTime); // Added
                    mFpsTextView.setText("FPS: " + fps); // Added
                } // Added
                mFrameCount = 0; // Added
                mLastFpsUpdateTime = currentTime; // Added
                mHandler.postDelayed(this, 1000); // Schedule next update (Added)
            } // Added
        }; // Added
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
        mHandler.removeCallbacks(mFpsRunnable); // Added
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
        // Update FPS display visibility based on preference (Added)
        boolean showFps = prefs.getBoolean("show_fps", false); // Default to false (Added)
        mFpsTextView.setVisibility(showFps ? View.VISIBLE : View.GONE); // Added
        if (showFps) { // Added
            mLastFpsUpdateTime = System.currentTimeMillis(); // Reset FPS timer (Added)
            mFrameCount = 0; // Reset frame count (Added)
            mHandler.postDelayed(mFpsRunnable, 1000); // Start FPS updates (Added)
        } // Added
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
                        // Re-evaluate FPS display visibility after settings change (Added)
                        SharedPreferences prefs = getSharedPreferences(ParticlesSurfaceView.SHARED_PREFS_NAME, MODE_PRIVATE); // Added
                        boolean showFps = prefs.getBoolean("show_fps", false); // Added
                        mFpsTextView.setVisibility(showFps ? View.VISIBLE : View.GONE); // Added
                        if (showFps) { // Added
                            mLastFpsUpdateTime = System.currentTimeMillis(); // Reset FPS timer (Added)
                            mFrameCount = 0; // Reset frame count (Added)
                            mHandler.postDelayed(mFpsRunnable, 1000); // Start FPS updates (Added)
                        } else { // Added
                            mHandler.removeCallbacks(mFpsRunnable); // Stop FPS updates (Added)
                        } // Added
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
