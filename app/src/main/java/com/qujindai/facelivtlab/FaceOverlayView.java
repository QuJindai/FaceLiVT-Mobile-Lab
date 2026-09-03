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

/** Draws the detector box and the five landmarks over the exact detector input image. */
public final class FaceOverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF box;
    private final List<PointF> landmarks = new ArrayList<>();
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
    }

    public void setFace(Face face, int sourceW, int sourceH) {
        if (face == null) { clear(); return; }
        Rect r = face.getBoundingBox();
        this.box = new RectF(r.left, r.top, r.right, r.bottom);
        this.sourceW = Math.max(1, sourceW);
        this.sourceH = Math.max(1, sourceH);
        landmarks.clear();
        add(face, FaceLandmark.LEFT_EYE);
        add(face, FaceLandmark.RIGHT_EYE);
        add(face, FaceLandmark.NOSE_BASE);
        add(face, FaceLandmark.MOUTH_LEFT);
        add(face, FaceLandmark.MOUTH_RIGHT);
        invalidate();
    }

    private void add(Face face, int type) {
        FaceLandmark lm = face.getLandmark(type);
        if (lm != null) landmarks.add(new PointF(lm.getPosition().x, lm.getPosition().y));
    }

    public void clear() {
        box = null;
        landmarks.clear();
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
        for (PointF p : landmarks) {
            canvas.drawCircle(dx+p.x*scale, dy+p.y*scale, dp(3.5f), pointPaint);
        }
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
