package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;

/** Draws the detector box and the five labelled landmarks over the exact detector input image. */
public final class FaceOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF box;
    private final List<PointF> landmarks = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private int sourceW = 1;
    private int sourceH = 1;

    public FaceOverlayView(Context c) { super(c); init(); }
    public FaceOverlayView(Context c, AttributeSet a) { super(c, a); init(); }
    public FaceOverlayView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setWillNotDraw(false);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(2));
        boxPaint.setColor(Color.rgb(42,205,170));
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(Color.rgb(255,190,70));
        labelPaint.setColor(Color.rgb(255,220,135));
        labelPaint.setTextSize(dp(9.5f));
        labelPaint.setFakeBoldText(true);
        labelPaint.setShadowLayer(dp(1.5f), 0, 0, Color.BLACK);
    }

    public void setFace(Face face, int sourceW, int sourceH) {
        if (face == null) { clear(); return; }
        Rect r = face.getBoundingBox();
        this.box = new RectF(r.left, r.top, r.right, r.bottom);
        this.sourceW = Math.max(1, sourceW);
        this.sourceH = Math.max(1, sourceH);
        landmarks.clear();
        labels.clear();
        add(face, FaceLandmark.LEFT_EYE, "LE");
        add(face, FaceLandmark.RIGHT_EYE, "RE");
        add(face, FaceLandmark.NOSE_BASE, "N");
        add(face, FaceLandmark.MOUTH_LEFT, "ML");
        add(face, FaceLandmark.MOUTH_RIGHT, "MR");
        invalidate();
    }

    private void add(Face face, int type, String label) {
        FaceLandmark lm = face.getLandmark(type);
        if (lm != null) {
            landmarks.add(new PointF(lm.getPosition().x, lm.getPosition().y));
            labels.add(label);
        }
    }

    public void clear() {
        box = null;
        landmarks.clear();
        labels.clear();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (box == null) return;
        float scale = Math.min(getWidth() / (float) sourceW, getHeight() / (float) sourceH);
        float dx = (getWidth() - sourceW * scale) / 2f;
        float dy = (getHeight() - sourceH * scale) / 2f;
        RectF mapped = new RectF(dx + box.left*scale, dy + box.top*scale,
                dx + box.right*scale, dy + box.bottom*scale);
        canvas.drawRect(mapped, boxPaint);
        for (int i = 0; i < landmarks.size(); i++) {
            PointF p = landmarks.get(i);
            float x = dx + p.x * scale;
            float y = dy + p.y * scale;
            canvas.drawCircle(x, y, dp(3.5f), pointPaint);
            canvas.drawText(labels.get(i), x + dp(5), y - dp(4), labelPaint);
        }
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
