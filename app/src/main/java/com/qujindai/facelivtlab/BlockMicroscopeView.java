package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** Compact real-activation view for the 18 FaceLiVTv2 blocks and four stages. */
public final class BlockMicroscopeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ModelTopology topology = ModelTopology.forVariant(ModelVariant.S);
    private DeepModelStats stats;
    private int selectedBlock = -1;
    private float tilesLeft;
    private float tilesTop;
    private float tileWidth;
    private float tileHeight;

    public BlockMicroscopeView(Context c) { super(c); init(); }
    public BlockMicroscopeView(Context c, AttributeSet a) { super(c, a); init(); }
    public BlockMicroscopeView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(255)));
        setClickable(true);
        text.setColor(Color.LTGRAY);
        text.setTextSize(dp(10));
    }

    public void setStats(ModelTopology topology, DeepModelStats stats) {
        this.topology = topology == null ? ModelTopology.forVariant(ModelVariant.S) : topology;
        this.stats = stats;
        if (selectedBlock >= this.topology.blockCount) selectedBlock = -1;
        invalidate();
    }

    public void clearStats(ModelTopology topology) {
        setStats(topology, null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        text.setColor(Color.WHITE);
        text.setTextSize(dp(11));
        canvas.drawText(topology.compactHeader(), dp(10), dp(18), text);
        text.setTextSize(dp(9));
        canvas.drawText("同深度不同宽度 · Block统计来自真实中间张量，不保留原始Feature Map", dp(10), dp(34), text);

        tilesLeft = dp(10);
        tilesTop = dp(56);
        tileWidth = Math.max(dp(11), (getWidth() - dp(20)) / 18f);
        tileHeight = dp(56);
        float maxDelta = 0.01f;
        float maxRms = 0.01f;
        if (stats != null) {
            for (DeepModelStats.BlockStat b : stats.blocks) {
                maxDelta = Math.max(maxDelta, b.relativeDelta);
                maxRms = Math.max(maxRms, b.rms);
            }
        }

        for (int i = 0; i < 18; i++) {
            int stage = DeepModelStats.stageForBlock(i);
            float x = tilesLeft + i * tileWidth;
            DeepModelStats.BlockStat b = stats == null ? null : stats.blocks[i];
            float delta = b == null ? 0f : Math.min(1f, b.relativeDelta / maxDelta);
            float rms = b == null ? 0f : Math.min(1f, b.rms / maxRms);
            int base = stage < 2 ? 38 : 52;
            paint.setColor(Color.rgb((int)(base + 22 * rms), (int)(64 + 125 * delta), (int)(72 + 100 * rms)));
            canvas.drawRect(x + 1, tilesTop, x + tileWidth - 1, tilesTop + tileHeight, paint);
            if (i == selectedBlock) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.rgb(255,190,70));
                canvas.drawRect(x + 1, tilesTop, x + tileWidth - 1, tilesTop + tileHeight, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            text.setColor(Color.WHITE);
            text.setTextSize(dp(7.5f));
            canvas.drawText(String.format(Locale.US, "%02d", i + 1), x + dp(2), tilesTop + dp(12), text);
            if (b != null) {
                canvas.drawText(String.format(Locale.US, "Δ%.2f", b.relativeDelta), x + dp(1), tilesTop + dp(29), text);
                canvas.drawText(String.format(Locale.US, "R%.2f", b.rms), x + dp(1), tilesTop + dp(43), text);
            }
        }

        text.setTextSize(dp(9));
        int cursor = 0;
        float stageY = tilesTop + tileHeight + dp(18);
        for (int stage = 0; stage < 4; stage++) {
            int depth = topology.stageDepths[stage];
            float x = tilesLeft + cursor * tileWidth;
            float w = depth * tileWidth;
            paint.setColor(Color.rgb(75,88,92));
            canvas.drawRect(x, stageY - dp(10), x + w - dp(2), stageY - dp(8), paint);
            text.setColor(Color.LTGRAY);
            canvas.drawText("S" + (stage + 1) + " " + topology.stageTypes[stage] + " · C" + topology.widths[stage], x, stageY + dp(3), text);
            cursor += depth;
        }

        float detailY = stageY + dp(28);
        if (stats == null) {
            text.setColor(Color.LTGRAY);
            canvas.drawText("等待当前焦点模型的深层诊断帧…", dp(10), detailY, text);
            return;
        }

        StringBuilder stages = new StringBuilder("Stage RMS: ");
        for (int i = 0; i < stats.stages.length; i++) {
            if (i > 0) stages.append(" | ");
            stages.append("S").append(i + 1).append(' ').append(String.format(Locale.US, "%.3f", stats.stages[i].rms));
        }
        text.setColor(Color.rgb(188,211,207));
        canvas.drawText(stages.toString(), dp(10), detailY, text);
        canvas.drawText(String.format(Locale.US,
                "PreHead 1284D: mean|x| %.3f · RMS %.3f · std %.3f · near0 %.2f%% → 512D",
                stats.preHeadMeanAbs, stats.preHeadRms, stats.preHeadStd, stats.preHeadNearZero * 100f),
                dp(10), detailY + dp(16), text);

        if (selectedBlock >= 0 && selectedBlock < stats.blocks.length) {
            DeepModelStats.BlockStat b = stats.blocks[selectedBlock];
            canvas.drawText(String.format(Locale.US,
                    "B%02d: mean|x| %.4f · RMS %.4f · std %.4f · near0 %.2f%% · relativeΔ %.4f",
                    selectedBlock + 1, b.meanAbs, b.rms, b.std, b.nearZero * 100f, b.relativeDelta),
                    dp(10), detailY + dp(34), text);
        } else {
            canvas.drawText("点选任一 Block 查看 mean|x| / RMS / std / near0 / relativeΔ", dp(10), detailY + dp(34), text);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP && event.getY() >= tilesTop && event.getY() <= tilesTop + tileHeight) {
            int index = (int)((event.getX() - tilesLeft) / Math.max(1f, tileWidth));
            if (index >= 0 && index < 18) {
                selectedBlock = index;
                invalidate();
                performClick();
                return true;
            }
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
