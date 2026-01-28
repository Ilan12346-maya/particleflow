package com.nfaralli.particleflow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class ColorView extends FrameLayout {

    public interface OnValueChangedListener {
        void onValueChanged(int color);
    }

    private View mColorPreview;
    private Button mPickButton;
    private OnValueChangedListener mOnValueChangedListener;
    private int mCurrentColor = Color.BLACK;

    public ColorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        addView(inflate(getContext(), R.layout.color, null));
        mColorPreview = findViewById(R.id.colorPreview);
        mPickButton = (Button)findViewById(R.id.pickColorBtn);

        OnClickListener openPicker = new OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorPicker();
            }
        };
        mPickButton.setOnClickListener(openPicker);
        mColorPreview.setOnClickListener(openPicker);

        setColor(0xFF000000);
    }

    private void showColorPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Pick Color");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(16 * getContext().getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final float[] hsv = new float[3];
        Color.colorToHSV(mCurrentColor, hsv);

        final View dialogPreview = new View(getContext());
        dialogPreview.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int)(50 * getContext().getResources().getDisplayMetrics().density)));
        dialogPreview.setBackgroundColor(mCurrentColor);
        layout.addView(dialogPreview);

        final ColorPickerSquare svSquare = new ColorPickerSquare(getContext());
        svSquare.setHSV(hsv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int)(180 * getContext().getResources().getDisplayMetrics().density));
        lp.topMargin = padding / 2;
        svSquare.setLayoutParams(lp);
        layout.addView(svSquare);

        final SeekBar hueBar = new SeekBar(getContext());
        hueBar.setMax(360);
        hueBar.setProgress((int)hsv[0]);
        layout.addView(new TextView(getContext()) {{ setText("Hue"); setPadding(0, padding / 2, 0, 0); }});
        layout.addView(hueBar);

        // RGB Input Fields in Dialog
        LinearLayout rgbLayout = new LinearLayout(getContext());
        rgbLayout.setOrientation(LinearLayout.HORIZONTAL);
        rgbLayout.setPadding(0, padding / 2, 0, 0);
        
        final ValidatedEditText rEdit = createRgbEdit("R:", rgbLayout);
        final ValidatedEditText gEdit = createRgbEdit("G:", rgbLayout);
        final ValidatedEditText bEdit = createRgbEdit("B:", rgbLayout);
        layout.addView(rgbLayout);

        rEdit.setText(String.valueOf(Color.red(mCurrentColor)));
        gEdit.setText(String.valueOf(Color.green(mCurrentColor)));
        bEdit.setText(String.valueOf(Color.blue(mCurrentColor)));

        // Helper to update all dialog UI components
        final Runnable updateUIFromHSV = new Runnable() {
            @Override
            public void run() {
                int c = Color.HSVToColor(hsv);
                dialogPreview.setBackgroundColor(c);
                rEdit.setText(String.valueOf(Color.red(c)));
                gEdit.setText(String.valueOf(Color.green(c)));
                bEdit.setText(String.valueOf(Color.blue(c)));
            }
        };

        final ValidatedEditText.OnTextChangedListener rgbListener = new ValidatedEditText.OnTextChangedListener() {
            @Override
            public void onTextChanged(String str) {
                try {
                    int r = Integer.parseInt(rEdit.getText().toString());
                    int g = Integer.parseInt(gEdit.getText().toString());
                    int b = Integer.parseInt(bEdit.getText().toString());
                    int c = Color.rgb(r, g, b);
                    Color.colorToHSV(c, hsv);
                    
                    dialogPreview.setBackgroundColor(c);
                    svSquare.setHSV(hsv);
                    hueBar.setProgress((int)hsv[0]);
                } catch (Exception ignored) {}
            }
        };

        rEdit.setOnTextChangedListener(rgbListener);
        gEdit.setOnTextChangedListener(rgbListener);
        bEdit.setOnTextChangedListener(rgbListener);

        svSquare.setListener(new ColorPickerSquare.OnColorChangedListener() {
            @Override
            public void onColorChanged(float[] newHsv) {
                hsv[1] = newHsv[1];
                hsv[2] = newHsv[2];
                // Silence listeners while updating to avoid loops
                rEdit.setOnTextChangedListener(null);
                gEdit.setOnTextChangedListener(null);
                bEdit.setOnTextChangedListener(null);
                updateUIFromHSV.run();
                rEdit.setOnTextChangedListener(rgbListener);
                gEdit.setOnTextChangedListener(rgbListener);
                bEdit.setOnTextChangedListener(rgbListener);
            }
        });

        hueBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                hsv[0] = progress;
                svSquare.updateHue(progress);
                rEdit.setOnTextChangedListener(null);
                gEdit.setOnTextChangedListener(null);
                bEdit.setOnTextChangedListener(null);
                updateUIFromHSV.run();
                rEdit.setOnTextChangedListener(rgbListener);
                gEdit.setOnTextChangedListener(rgbListener);
                bEdit.setOnTextChangedListener(rgbListener);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        builder.setView(layout);
        builder.setPositiveButton("Select", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setColor(Color.HSVToColor(hsv));
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private ValidatedEditText createRgbEdit(String label, LinearLayout parent) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setPadding(8, 0, 4, 0);
        parent.addView(tv);
        
        ValidatedEditText et = new ValidatedEditText(getContext(), null);
        et.setMinValue(0);
        et.setMaxValue(255);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        parent.addView(et);
        return et;
    }

    public void setOnValueChangedListener(OnValueChangedListener listener) {
        mOnValueChangedListener = listener;
    }

    public void onValueChanged() {
        if (mOnValueChangedListener != null) {
            mOnValueChangedListener.onValueChanged(mCurrentColor);
        }
    }

    public int getColor() {
        return mCurrentColor;
    }

    public void setColor(int color) {
        mCurrentColor = color;
        if (mColorPreview != null) mColorPreview.setBackgroundColor(color);
        onValueChanged();
    }

    private static class ColorPickerSquare extends View {
        public interface OnColorChangedListener { void onColorChanged(float[] hsv); }
        private Paint paint, borderPaint, pickerPaint;
        private float[] hsv;
        private OnColorChangedListener listener;

        public ColorPickerSquare(Context context) {
            super(context);
            paint = new Paint();
            borderPaint = new Paint();
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(4);
            borderPaint.setColor(Color.LTGRAY);
            pickerPaint = new Paint();
            pickerPaint.setStyle(Paint.Style.STROKE);
            pickerPaint.setStrokeWidth(6);
            pickerPaint.setColor(Color.WHITE);
            pickerPaint.setAntiAlias(true);
        }

        public void setHSV(float[] hsv) { this.hsv = hsv; invalidate(); }
        public void setListener(OnColorChangedListener l) { this.listener = l; }
        public void updateHue(float hue) { hsv[0] = hue; invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) return;

            Shader satShader = new LinearGradient(0, 0, width, 0, Color.WHITE, Color.HSVToColor(new float[]{hsv[0], 1f, 1f}), Shader.TileMode.CLAMP);
            Shader valShader = new LinearGradient(0, 0, 0, height, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
            
            paint.setShader(satShader);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(valShader);
            canvas.drawRect(0, 0, width, height, paint);
            canvas.drawRect(0, 0, width, height, borderPaint);

            float x = hsv[1] * width;
            float y = (1f - hsv[2]) * height;
            canvas.drawCircle(x, y, 20, pickerPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                float y = Math.max(0, Math.min(event.getY(), getHeight()));
                hsv[1] = x / getWidth();
                hsv[2] = 1f - (y / getHeight());
                if (listener != null) listener.onColorChanged(hsv);
                invalidate();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}