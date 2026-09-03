package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Compact architecture diagram for the currently observed FaceLiVTv2 variant. */
public final class ModelArchitectureView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ModelArchitectureSpec spec = ModelArchitectureSpec.forVariant(ModelVariant.S);

    public ModelArchitectureView(Context c) { super(c); init(); }
    public ModelArchitectureView(Context c, AttributeSet a) { super(c, a); init(); }
    public ModelArchitectureView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setWillNotDraw(false);
        text.setColor(Color.WHITE);
        text.setTextSize(dp(12));
        text.setFakeBoldText(true);
        dimText.setColor(Color.rgb(185, 210, 205));
        dimText.setTextSize(dp(9.5f));
        stagePaint.setColor(Color.rgb(37, 191, 164));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(1));
        border.setColor(Color.rgb(64, 85, 90));
    }

    public void setVariant(ModelArchitectureSpec spec) {
        if (spec == null) return;
        this.spec = ModelArchitectureSpec.forVariant(spec.variant);
        invalidate();
    }

    public void setVariant(ModelVariant variant) {
        setVariant(ModelArchitectureSpec.forVariant(variant));
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(Math.round(dp(155)), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(10);
        float y = dp(18);
        canvas.drawText(String.format(Locale.US,
                "%s · %.2fM params · %d Blocks · depths [3,3,9,3]",
                spec.variant.storageKey, spec.approxParamsM, spec.blockCount), pad, y, text);
        y += dp(16);
        canvas.drawText("同深度，主要靠通道宽度扩容 · 1284D pre-head → 512D identity", pad, y, dimText);

        float maxWidth = spec.widths[3];
        float top = y + dp(12);
        float rowH = dp(20);
        float left = pad;
        float available = Math.max(dp(120), getWidth() - pad * 2 - dp(86));
        for (int stage = 0; stage < 4; stage++) {
            float cy = top + stage * rowH;
            String label = String.format(Locale.US, "S%d  %dx %s  C%d",
                    stage + 1, spec.depths[stage], spec.mixerTypes[stage], spec.widths[stage]);
            canvas.drawText(label, left, cy + dp(11), dimText);
            float barLeft = left + dp(86);
            float barW = available * spec.widths[stage] / maxWidth;
            RectF r = new RectF(barLeft, cy + dp(2), barLeft + barW, cy + dp(14));
            stagePaint.setAlpha(145 + stage * 25);
            canvas.drawRoundRect(r, dp(3), dp(3), stagePaint);
            canvas.drawRoundRect(new RectF(barLeft, cy + dp(2), barLeft + available, cy + dp(14)),
                    dp(3), dp(3), border);
        }

        canvas.drawText("5 landmarks ≠ 5 enrollment samples ≠ 18 model blocks ≠ 512 embedding dims",
                pad, getHeight() - dp(8), dimText);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
