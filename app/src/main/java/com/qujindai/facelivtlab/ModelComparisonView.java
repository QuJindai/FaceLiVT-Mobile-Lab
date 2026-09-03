package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact comparable scalar statistics for XS/S/M; never cross-compares raw embedding coordinates. */
public final class ModelComparisonView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<ModelComparisonStats.Row> rows = new ArrayList<>();

    public ModelComparisonView(Context c) { super(c); init(); }
    public ModelComparisonView(Context c, AttributeSet a) { super(c, a); init(); }
    public ModelComparisonView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(145)));
        text.setTextSize(dp(10));
        text.setColor(Color.LTGRAY);
        line.setColor(Color.rgb(55,70,75));
    }

    public void setRows(List<ModelComparisonStats.Row> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        text.setColor(Color.WHITE);
        text.setTextSize(dp(11));
        canvas.drawText("XS / S / M 横向模型显微镜 · 标量可比，512D坐标不可跨模型直接比", dp(10), dp(19), text);
        text.setTextSize(dp(9));
        text.setColor(Color.LTGRAY);
        canvas.drawText("模型 | 参数 | 深度 | Sstable | Coverage | min pair | mean pair | outlier", dp(10), dp(37), text);
        if (rows.isEmpty()) {
            canvas.drawText("完成三模型同批录入后显示", dp(10), dp(59), text);
            return;
        }
        float y = dp(59);
        for (ModelComparisonStats.Row row : rows) {
            text.setColor(row.variant == ModelVariant.S ? Color.rgb(47,205,176) : Color.WHITE);
            String outlier = row.outlierIndex < 0 ? "N/A" : "S" + (row.outlierIndex + 1);
            canvas.drawText(String.format(Locale.US,
                    "%s | %.2fM | 18B | %.3f | %.3f | %s | %s | %s",
                    row.variant.storageKey, row.parameterCountM,
                    row.stability, row.coverage, f(row.minPairCosine), f(row.meanPairCosine), outlier),
                    dp(10), y, text);
            canvas.drawLine(dp(10), y + dp(7), getWidth() - dp(10), y + dp(7), line);
            y += dp(25);
        }
    }

    private static String f(float v) {
        return Float.isFinite(v) ? String.format(Locale.US, "%.3f", v) : "N/A";
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
