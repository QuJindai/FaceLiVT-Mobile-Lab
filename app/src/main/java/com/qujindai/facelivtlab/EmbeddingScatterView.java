package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public final class EmbeddingScatterView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[][] points = new float[0][0];
    private int sampleCount = 0;

    public EmbeddingScatterView(Context c) { super(c); init(); }
    public EmbeddingScatterView(Context c, AttributeSet a) { super(c, a); init(); }
    public EmbeddingScatterView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(170)));
        text.setTextSize(dp(11));
        text.setColor(Color.LTGRAY);
    }

    public void setProjection(float[][] projection, int sampleCount) {
        this.points = projection == null ? new float[0][0] : projection;
        this.sampleCount = Math.max(0, sampleCount);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(70,82,86));
        canvas.drawLine(dp(20), cy, getWidth()-dp(20), cy, paint);
        canvas.drawLine(cx, dp(20), cx, getHeight()-dp(20), paint);
        if (points.length == 0) {
            canvas.drawText("完成录入后显示 512D embedding 的 2D 投影", dp(12), dp(28), text);
            return;
        }
        float rx = Math.max(dp(1), getWidth() * .40f);
        float ry = Math.max(dp(1), getHeight() * .34f);
        int sampleLimit = Math.min(sampleCount, points.length);
        for (int i = 0; i < sampleLimit; i++) {
            float x = cx + points[i][0] * rx;
            float y = cy - points[i][1] * ry;
            paint.setColor(Color.rgb(47, 205, 176));
            canvas.drawCircle(x, y, dp(6), paint);
            text.setColor(Color.WHITE);
            canvas.drawText("S" + (i+1), x + dp(7), y - dp(7), text);
        }
        if (points.length > sampleLimit) {
            float[] c = points[points.length - 1];
            float x = cx + c[0] * rx;
            float y = cy - c[1] * ry;
            paint.setColor(Color.rgb(255, 190, 70));
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(x-dp(8), y, x+dp(8), y, paint);
            canvas.drawLine(x, y-dp(8), x, y+dp(8), paint);
            text.setColor(Color.rgb(255,220,140));
            canvas.drawText("模板中心 c", x + dp(10), y + dp(4), text);
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
