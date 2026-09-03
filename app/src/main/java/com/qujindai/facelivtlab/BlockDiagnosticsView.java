package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** 18-block / 4-stage compact runtime diagnostic view. */
public final class BlockDiagnosticsView extends View {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint group = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ModelArchitectureSpec spec = ModelArchitectureSpec.forVariant(ModelVariant.S);
    private ModelDiagnostics data;
    private int selectedBlock = 0;

    public BlockDiagnosticsView(Context c) { super(c); init(); }
    public BlockDiagnosticsView(Context c, AttributeSet a) { super(c, a); init(); }
    public BlockDiagnosticsView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setWillNotDraw(false);
        setClickable(true);
        text.setColor(Color.WHITE);
        text.setTextSize(dp(11.5f));
        text.setFakeBoldText(true);
        dim.setColor(Color.rgb(183, 207, 203));
        dim.setTextSize(dp(9f));
        bar.setColor(Color.rgb(41, 198, 166));
        group.setStyle(Paint.Style.STROKE);
        group.setStrokeWidth(dp(1));
        group.setColor(Color.rgb(65, 84, 89));
    }

    public void clearForVariant(ModelArchitectureSpec spec) {
        if (spec != null) this.spec = ModelArchitectureSpec.forVariant(spec.variant);
        data = null;
        selectedBlock = 0;
        invalidate();
    }

    public void setData(ModelArchitectureSpec spec, ModelDiagnostics diagnostics) {
        if (spec != null) this.spec = ModelArchitectureSpec.forVariant(spec.variant);
        this.data = diagnostics;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(Math.round(dp(245)), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(10);
        canvas.drawText("模型内部显微镜 · " + spec.variant.storageKey + " · 18 Blocks / 4 Stages",
                pad, dp(18), text);
        if (data == null) {
            canvas.drawText("等待该模型诊断帧 · 普通识别路径只读取最终 512D embedding",
                    pad, dp(39), dim);
            drawStageSkeleton(canvas, dp(58));
            return;
        }

        float chartTop = dp(48);
        float chartBottom = dp(145);
        float left = pad;
        float right = getWidth() - pad;
        float gap = dp(2);
        float blockW = (right - left - gap * 17) / 18f;
        float maxDelta = 1e-6f;
        for (int i = 0; i < 18; i++) maxDelta = Math.max(maxDelta, data.blockStats[i][3]);

        int cursor = 0;
        for (int stage = 0; stage < 4; stage++) {
            int start = cursor;
            for (int local = 0; local < spec.depths[stage]; local++, cursor++) {
                float delta = Math.max(0f, data.blockStats[cursor][3]);
                float h = (chartBottom - chartTop) * Math.min(1f, delta / maxDelta);
                float x = left + cursor * (blockW + gap);
                RectF r = new RectF(x, chartBottom - h, x + blockW, chartBottom);
                bar.setAlpha(cursor == selectedBlock ? 255 : 165);
                canvas.drawRoundRect(r, dp(2), dp(2), bar);
                canvas.drawText(String.valueOf(cursor + 1), x, chartBottom + dp(12), dim);
            }
            float groupLeft = left + start * (blockW + gap) - dp(2);
            float groupRight = left + (cursor - 1) * (blockW + gap) + blockW + dp(2);
            canvas.drawRect(groupLeft, chartTop - dp(10), groupRight, chartBottom + dp(16), group);
            canvas.drawText("S" + (stage + 1), groupLeft + dp(2), chartTop - dp(1), dim);
        }

        float[] b = data.blockStats[Math.max(0, Math.min(17, selectedBlock))];
        canvas.drawText(String.format(Locale.US,
                "B%d · mean|x| %.4f · RMS %.4f · sparse %.1f%% · Δratio %.4f",
                selectedBlock + 1, b[0], b[1], b[2] * 100f, b[3]), pad, dp(178), dim);

        StringBuilder stages = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) stages.append("  |  ");
            stages.append(String.format(Locale.US, "S%d Δ%.3f", i + 1, data.stageStats[i][3]));
        }
        canvas.drawText(stages.toString(), pad, dp(198), dim);
        canvas.drawText(String.format(Locale.US,
                "1284D pre-head · mean|x| %.4f · RMS %.4f · sparse %.1f%% → 512D identity",
                data.preheadMeanAbs, data.preheadRms, data.preheadSparsity * 100f), pad, dp(217), dim);
        canvas.drawText("条高=block feature Δratio；它是内部变化量，不是识别置信度。", pad, dp(235), dim);
    }

    private void drawStageSkeleton(Canvas canvas, float top) {
        float pad = dp(10);
        float w = getWidth() - pad * 2;
        float x = pad;
        for (int i = 0; i < 4; i++) {
            float frac = spec.depths[i] / 18f;
            float stageW = w * frac - dp(3);
            RectF r = new RectF(x, top, x + stageW, top + dp(45));
            canvas.drawRoundRect(r, dp(4), dp(4), group);
            canvas.drawText("S" + (i + 1) + " · " + spec.depths[i] + "×" + spec.mixerTypes[i],
                    x + dp(3), top + dp(25), dim);
            x += w * frac;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && data != null) {
            float pad = dp(10);
            float usable = Math.max(1f, getWidth() - pad * 2);
            int index = (int) ((event.getX() - pad) / usable * 18f);
            selectedBlock = Math.max(0, Math.min(17, index));
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
