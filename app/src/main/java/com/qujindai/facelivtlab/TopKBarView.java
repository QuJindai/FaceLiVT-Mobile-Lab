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

public final class TopKBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<FaceStore.Match> matches = new ArrayList<>();
    private float threshold = .45f;

    public TopKBarView(Context c) { super(c); init(); }
    public TopKBarView(Context c, AttributeSet a) { super(c, a); init(); }
    public TopKBarView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(150)));
        text.setTextSize(dp(12));
        text.setColor(Color.WHITE);
    }

    public void setResults(List<FaceStore.Match> values, float threshold) {
        this.matches = values == null ? new ArrayList<>() : new ArrayList<>(values);
        this.threshold = threshold;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        float left = dp(86);
        float right = getWidth() - dp(14);
        float barWidth = Math.max(dp(10), right - left);
        float thresholdX = left + Math.max(0f, Math.min(1f, threshold)) * barWidth;
        paint.setColor(Color.rgb(255,180,80));
        paint.setStrokeWidth(dp(2));
        canvas.drawLine(thresholdX, dp(14), thresholdX, getHeight()-dp(12), paint);
        text.setColor(Color.rgb(255,205,130));
        text.setTextSize(dp(10));
        canvas.drawText(String.format(Locale.US,"阈值 %.2f",threshold), Math.max(dp(4),thresholdX-dp(22)), dp(12), text);
        if (matches.isEmpty()) {
            text.setColor(Color.LTGRAY);
            text.setTextSize(dp(12));
            canvas.drawText("暂无 Top-K 匹配结果", dp(12), dp(40), text);
            return;
        }
        int count = Math.min(3, matches.size());
        float row = (getHeight()-dp(30)) / Math.max(3, count);
        for (int i=0;i<count;i++) {
            FaceStore.Match m = matches.get(i);
            float y = dp(26) + i*row;
            float score = Math.max(0f, Math.min(1f, m.similarity));
            text.setTextSize(dp(12));
            text.setColor(Color.WHITE);
            canvas.drawText((i+1)+"  "+shortName(m.name), dp(8), y+row*.55f, text);
            paint.setColor(Color.rgb(55,70,75));
            canvas.drawRoundRect(left,y+dp(8),right,y+row-dp(8),dp(5),dp(5),paint);
            paint.setColor(i==0 ? Color.rgb(42,205,170) : Color.rgb(84,132,145));
            canvas.drawRoundRect(left,y+dp(8),left+barWidth*score,y+row-dp(8),dp(5),dp(5),paint);
            text.setColor(Color.WHITE);
            canvas.drawText(String.format(Locale.US,"%.3f",m.similarity), left+dp(8), y+row*.55f, text);
        }
    }

    private String shortName(String value) {
        if (value == null) return "";
        return value.length() <= 8 ? value : value.substring(0,8);
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
