package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Zero-centered scalar delta matrix; valid because each cell is a cosine relation between the same sample indices. */
public final class MatrixDeltaView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[][] matrix = new float[0][0];
    private String label = "ΔM";

    public MatrixDeltaView(Context c) { super(c); init(); }
    public MatrixDeltaView(Context c, AttributeSet a) { super(c, a); init(); }
    public MatrixDeltaView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(175)));
        text.setTextSize(dp(10));
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setDelta(String label, float[][] values) {
        this.label = label == null ? "ΔM" : label;
        this.matrix = values == null ? new float[0][0] : values;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        int n = matrix.length;
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(Color.LTGRAY);
        canvas.drawText(label + " · 正值=前者该样本关系更高", dp(10), dp(18), text);
        if (n == 0) {
            canvas.drawText("完成三模型同批录入后显示", dp(10), dp(38), text);
            return;
        }
        text.setTextAlign(Paint.Align.CENTER);
        float padLeft = dp(28);
        float padTop = dp(38);
        float cell = Math.min((getWidth() - padLeft - dp(8)) / n,
                (getHeight() - padTop - dp(8)) / n);
        float maxAbs = 0.01f;
        for (float[] row : matrix) for (float v : row) maxAbs = Math.max(maxAbs, Math.abs(v));
        for (int i = 0; i < n; i++) {
            text.setColor(Color.LTGRAY);
            canvas.drawText("S" + (i + 1), padLeft - dp(13), padTop + (i + .65f) * cell, text);
            canvas.drawText("S" + (i + 1), padLeft + (i + .5f) * cell, padTop - dp(7), text);
            for (int j = 0; j < matrix[i].length; j++) {
                float v = matrix[i][j];
                float mag = Math.min(1f, Math.abs(v) / maxAbs);
                if (v >= 0f) {
                    fill.setColor(Color.rgb((int)(36 + 20 * mag), (int)(64 + 135 * mag), (int)(70 + 105 * mag)));
                } else {
                    fill.setColor(Color.rgb((int)(70 + 140 * mag), (int)(58 + 70 * mag), (int)(48 + 35 * mag)));
                }
                float left = padLeft + j * cell;
                float top = padTop + i * cell;
                canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, fill);
                text.setColor(Color.WHITE);
                canvas.drawText(String.format(Locale.US, "%+.2f", v), left + cell / 2f, top + cell * .62f, text);
            }
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
