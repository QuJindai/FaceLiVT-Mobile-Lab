package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Five-landmark geometry and similarity-transform diagnostics. */
public final class AlignmentGeometryView extends View {
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private FaceAlignmentDiagnostics data;

    public AlignmentGeometryView(Context c) { super(c); init(); }
    public AlignmentGeometryView(Context c, AttributeSet a) { super(c, a); init(); }
    public AlignmentGeometryView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setWillNotDraw(false);
        title.setColor(Color.WHITE);
        title.setTextSize(dp(12));
        title.setFakeBoldText(true);
        text.setColor(Color.rgb(190, 216, 210));
        text.setTextSize(dp(10f));
    }

    public void setData(FaceAlignmentDiagnostics data) {
        this.data = data;
        invalidate();
    }

    public void clear() {
        data = null;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(Math.round(dp(125)), heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float x = dp(10);
        canvas.drawText("5点几何显微镜 · LE / RE / N / ML / MR", x, dp(18), title);
        canvas.drawText("p' = sRp + t · 五点用于把脸摆正，不直接承担身份分类", x, dp(37), text);
        if (data == null) {
            canvas.drawText("等待人脸关键点与 112×112 对齐结果", x, dp(62), text);
            return;
        }
        canvas.drawText("完整度 " + data.landmarkCount + "/5 · 方法 " + data.methodLabel(), x, dp(58), text);
        if (data.usedFallback) {
            canvas.drawText("关键点不足或退化：使用 fallback crop；scale / rotation / residual 不伪造。",
                    x, dp(80), text);
        } else {
            canvas.drawText(String.format(Locale.US,
                    "眼间距 %.1f px · scale %.4f · rotation %.2f°",
                    data.eyeDistancePx, data.scale, data.rotationDeg), x, dp(80), text);
            canvas.drawText(String.format(Locale.US,
                    "Ealign=mean||p'i-p̂i|| = %.3f px · max residual %.3f px",
                    data.meanResidualPx, data.maxResidualPx), x, dp(102), text);
        }
        canvas.drawText("几何异常先于 512D：残差升高时，先查检测/关键点/对齐，再归因模型。",
                x, dp(120), text);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
}
