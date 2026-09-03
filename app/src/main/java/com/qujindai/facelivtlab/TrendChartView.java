package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

public final class TrendChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] similarities = new float[0];
    private float[] qualities = new float[0];
    private float threshold = .45f;

    public TrendChartView(Context c) { super(c); init(); }
    public TrendChartView(Context c, AttributeSet a) { super(c, a); init(); }
    public TrendChartView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setMinimumHeight(Math.round(dp(160)));
        text.setTextSize(dp(10));
    }

    public void setSeries(float[] similarities, float[] qualities, float threshold) {
        this.similarities = similarities == null ? new float[0] : similarities.clone();
        this.qualities = qualities == null ? new float[0] : qualities.clone();
        this.threshold = threshold;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(18,24,27));
        float left=dp(28), right=getWidth()-dp(12), top=dp(18), bottom=getHeight()-dp(24);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(65,77,82));
        for (int i=0;i<=4;i++) {
            float y=top+(bottom-top)*i/4f;
            canvas.drawLine(left,y,right,y,paint);
        }
        float ty=bottom-Math.max(0f,Math.min(1f,threshold))*(bottom-top);
        paint.setColor(Color.rgb(255,180,80));
        paint.setStrokeWidth(dp(2));
        canvas.drawLine(left,ty,right,ty,paint);
        text.setColor(Color.rgb(255,205,130));
        canvas.drawText(String.format(Locale.US,"Tid %.2f",threshold), left+dp(3),ty-dp(3),text);
        drawSeries(canvas, similarities, Color.rgb(42,205,170), left,right,top,bottom);
        drawSeries(canvas, qualities, Color.rgb(110,160,255), left,right,top,bottom);
        text.setColor(Color.rgb(42,205,170));
        canvas.drawText("相似度", left, getHeight()-dp(7), text);
        text.setColor(Color.rgb(110,160,255));
        canvas.drawText("质量Q", left+dp(48), getHeight()-dp(7), text);
        if (similarities.length == 0) {
            text.setColor(Color.LTGRAY);
            canvas.drawText("连续检测后显示时序稳定性", left+dp(90), getHeight()-dp(7), text);
        }
    }

    private void drawSeries(Canvas canvas,float[] data,int color,float left,float right,float top,float bottom) {
        if (data.length < 1) return;
        Path path=new Path();
        for (int i=0;i<data.length;i++) {
            float x=data.length==1?(left+right)/2f:left+(right-left)*i/(data.length-1f);
            float v=Math.max(0f,Math.min(1f,data[i]));
            float y=bottom-v*(bottom-top);
            if(i==0) path.moveTo(x,y); else path.lineTo(x,y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(color);
        canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
