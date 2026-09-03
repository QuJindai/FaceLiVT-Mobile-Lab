package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public final class SimilarityMatrixView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[][] matrix = new float[0][0];

    public SimilarityMatrixView(Context c) { super(c); init(); }
    public SimilarityMatrixView(Context c, AttributeSet a) { super(c, a); init(); }
    public SimilarityMatrixView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(160)));
        text.setTextSize(dp(11));
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setMatrix(float[][] values) {
        matrix = values == null ? new float[0][0] : values;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18, 24, 27));
        int n = matrix.length;
        if (n == 0) {
            text.setColor(Color.LTGRAY);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("完成 5 帧录入后显示样本相似度矩阵", dp(12), dp(28), text);
            text.setTextAlign(Paint.Align.CENTER);
            return;
        }
        float pad = dp(28);
        float cell = Math.min((getWidth() - pad - dp(8)) / n, (getHeight() - pad - dp(8)) / n);
        for (int i = 0; i < n; i++) {
            text.setColor(Color.LTGRAY);
            canvas.drawText("S" + (i + 1), pad - dp(13), pad + (i + .68f) * cell, text);
            canvas.drawText("S" + (i + 1), pad + (i + .5f) * cell, pad - dp(8), text);
            for (int j = 0; j < n; j++) {
                float value = j < matrix[i].length ? matrix[i][j] : 0f;
                float t = Math.max(0f, Math.min(1f, (value - .50f) / .50f));
                fill.setColor(Color.rgb((int)(35 + 25 * t), (int)(62 + 145 * t), (int)(68 + 120 * t)));
                float left = pad + j * cell;
                float top = pad + i * cell;
                canvas.drawRect(left + 1, top + 1, left + cell - 1, top + cell - 1, fill);
                text.setColor(t > .55f ? Color.BLACK : Color.WHITE);
                canvas.drawText(String.format(Locale.US, "%.2f", value), left + cell / 2f, top + cell * .62f, text);
            }
        }
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
