package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fixed-axis reference-cluster projection with a live probe trail. Final recognition still uses 512-D cosine. */
public final class ProbeEmbeddingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[][] references = new float[0][0];
    private int sampleCount;
    private float[] probe;
    private List<float[]> trail = new ArrayList<>();
    private float[] variance = new float[]{0f, 0f};

    public ProbeEmbeddingView(Context c) { super(c); init(); }
    public ProbeEmbeddingView(Context c, AttributeSet a) { super(c, a); init(); }
    public ProbeEmbeddingView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(175)));
        text.setTextSize(dp(10));
        text.setColor(Color.LTGRAY);
    }

    public void clearData() {
        references = new float[0][0];
        sampleCount = 0;
        probe = null;
        trail = new ArrayList<>();
        variance = new float[]{0f, 0f};
        invalidate();
    }

    public void setData(float[][] references, int sampleCount, float[] probe,
                        List<float[]> trail, float[] explainedVariance) {
        this.references = copy2d(references);
        this.sampleCount = Math.max(0, sampleCount);
        this.probe = probe == null ? null : probe.clone();
        this.trail = new ArrayList<>();
        if (trail != null) for (float[] p : trail) if (p != null && p.length >= 2) this.trail.add(p.clone());
        this.variance = explainedVariance == null ? new float[]{0f,0f} : explainedVariance.clone();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        float cx = getWidth()/2f, cy = getHeight()/2f;
        float rx = Math.max(dp(1), getWidth()*.39f), ry = Math.max(dp(1), getHeight()*.31f);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(68,82,86));
        canvas.drawLine(dp(18),cy,getWidth()-dp(18),cy,paint);
        canvas.drawLine(cx,dp(24),cx,getHeight()-dp(27),paint);
        text.setColor(Color.rgb(150,175,180));
        canvas.drawText(String.format(Locale.US,"PC1 %.0f%% · PC2 %.0f%%", value(variance,0)*100f, value(variance,1)*100f), dp(8), dp(15), text);

        if (references.length == 0) {
            text.setColor(Color.LTGRAY);
            canvas.drawText("R3.1 重新录入后显示模板簇与实时 Probe", dp(12), dp(40), text);
            return;
        }
        int limit = Math.min(sampleCount, references.length);
        for (int i=0;i<limit;i++) {
            float x=x(references[i],cx,rx), y=y(references[i],cy,ry);
            paint.setColor(Color.rgb(45,202,176));
            canvas.drawCircle(x,y,dp(5),paint);
            text.setColor(Color.WHITE);
            canvas.drawText("S"+(i+1),x+dp(6),y-dp(5),text);
        }
        if (references.length>limit) {
            float[] c=references[references.length-1];
            float x=x(c,cx,rx), y=y(c,cy,ry);
            paint.setColor(Color.rgb(255,185,65));
            paint.setStrokeWidth(dp(3));
            canvas.drawLine(x-dp(7),y,x+dp(7),y,paint);
            canvas.drawLine(x,y-dp(7),x,y+dp(7),paint);
        }

        if (trail.size()>1) {
            Path path=new Path();
            for(int i=0;i<trail.size();i++) {
                float px=x(trail.get(i),cx,rx), py=y(trail.get(i),cy,ry);
                if(i==0) path.moveTo(px,py); else path.lineTo(px,py);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(112,158,255));
            canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if(probe!=null && probe.length>=2) {
            float px=x(probe,cx,rx), py=y(probe,cy,ry);
            paint.setColor(Color.rgb(255,92,150));
            canvas.drawCircle(px,py,dp(7),paint);
            text.setColor(Color.rgb(255,180,210));
            canvas.drawText("Probe",px+dp(8),py-dp(7),text);
        }
        text.setColor(Color.rgb(150,175,180));
        canvas.drawText("2D仅用于观察 · 最终判定仍使用512D cosine",dp(8),getHeight()-dp(7),text);
    }

    private float x(float[] p,float cx,float r){return cx+clamp(value(p,0),-1.25f,1.25f)*r;}
    private float y(float[] p,float cy,float r){return cy-clamp(value(p,1),-1.25f,1.25f)*r;}
    private static float value(float[] a,int i){return a!=null&&a.length>i&&Float.isFinite(a[i])?a[i]:0f;}
    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static float[][] copy2d(float[][] src){
        if(src==null)return new float[0][0];
        float[][] out=new float[src.length][];
        for(int i=0;i<src.length;i++)out[i]=src[i]==null?new float[0]:src[i].clone();
        return out;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
