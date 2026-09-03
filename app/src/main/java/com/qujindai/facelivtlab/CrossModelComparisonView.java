package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Cross-backbone enrollment relation microscope. */
public final class CrossModelComparisonView extends View {
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private CrossModelEnrollmentComparison data;

    public CrossModelComparisonView(Context c) { super(c); init(); }
    public CrossModelComparisonView(Context c, AttributeSet a) { super(c, a); init(); }
    public CrossModelComparisonView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setWillNotDraw(false);
        title.setColor(Color.WHITE);
        title.setTextSize(dp(12));
        title.setFakeBoldText(true);
        text.setColor(Color.rgb(188, 211, 207));
        text.setTextSize(dp(8.8f));
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(0.7f));
        grid.setColor(Color.rgb(68, 88, 92));
    }

    public void setData(CrossModelEnrollmentComparison data) {
        this.data = data;
        requestLayout();
        invalidate();
    }

    public void clear() {
        data = null;
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int n = matrixSize();
        float h = n > 0 ? 245 + n * 24 * 2 : 100;
        setMeasuredDimension(w, resolveSize(Math.round(dp(h)), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(10);
        canvas.drawText("XS / S / M 横向模型显微镜", pad, dp(18), title);
        canvas.drawText("5 samples → 5×5；矩阵阶数跟样本数走，不是模型层数。", pad, dp(37), text);
        canvas.drawText("三个 512D 空间彼此独立；只比较同一 sample-pair 的关系，不做跨模型 embedding cosine。",
                pad, dp(52), text);
        if (data == null || data.byVariant.isEmpty()) {
            canvas.drawText("等待一次完整三模型录入后生成横向比较。", pad, dp(77), text);
            return;
        }

        float y = dp(72);
        for (ModelVariant variant : ModelVariant.values()) {
            CrossModelEnrollmentComparison.VariantMetrics m = data.byVariant.get(variant);
            if (m == null) continue;
            ModelArchitectureSpec spec = ModelArchitectureSpec.forVariant(variant);
            String line = String.format(Locale.US,
                    "%s %.2fM | meanPair %.3f | minPair %.3f | Sstable %.3f | Cover %.3f | outlier S%d",
                    variant.storageKey, spec.approxParamsM, m.meanPair, m.minPair,
                    m.stability, m.coverage, m.outlierIndex + 1);
            canvas.drawText(line, pad, y, text);
            y += dp(17);
        }
        y += dp(5);
        y = drawDeltaMatrix(canvas, "ΔMatrix XS − S", data.deltaXsVsS, pad, y);
        y += dp(9);
        y = drawDeltaMatrix(canvas, "ΔMatrix M − S", data.deltaMVsS, pad, y);
        y += dp(16);
        canvas.drawText("PCA 提醒：XS/S/M 各自拟合 2D 坐标轴；绝对方向/位置不可横向比较，只看簇形、离群和相对结构。",
                pad, Math.min(getHeight() - dp(8), y), text);
    }

    private float drawDeltaMatrix(Canvas canvas, String label, float[][] matrix, float left, float top) {
        canvas.drawText(label, left, top, title);
        if (matrix == null || matrix.length == 0) {
            canvas.drawText("N/A", left + dp(120), top, text);
            return top + dp(18);
        }
        int n = matrix.length;
        float available = Math.max(dp(150), getWidth() - left * 2);
        float size = Math.min(dp(24), available / n);
        float maxAbs = 1e-6f;
        for (float[] row : matrix) for (float v : row) maxAbs = Math.max(maxAbs, Math.abs(v));
        float y0 = top + dp(8);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float value = matrix[i][j];
                float ratio = Math.min(1f, Math.abs(value) / maxAbs);
                if (value >= 0f) cell.setColor(Color.rgb(34, (int)(95 + 140 * ratio), (int)(100 + 120 * ratio)));
                else cell.setColor(Color.rgb((int)(110 + 130 * ratio), (int)(80 + 70 * (1f-ratio)), 70));
                float x = left + j * size;
                float y = y0 + i * size;
                RectF r = new RectF(x, y, x + size, y + size);
                canvas.drawRect(r, cell);
                canvas.drawRect(r, grid);
                if (size >= dp(20)) {
                    text.setColor(Color.WHITE);
                    canvas.drawText(String.format(Locale.US, "%+.2f", value), x + dp(2), y + size * 0.64f, text);
                    text.setColor(Color.rgb(188, 211, 207));
                }
            }
        }
        return y0 + n * size;
    }

    private int matrixSize() {
        if (data == null) return 0;
        if (data.deltaXsVsS != null && data.deltaXsVsS.length > 0) return data.deltaXsVsS.length;
        if (data.deltaMVsS != null) return data.deltaMVsS.length;
        return 0;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
