package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Compact empirical threshold card inserted into the recognition microscope at runtime. */
public final class R31CalibrationPanel extends LinearLayout {
    private final TextView title;
    private final TextView body;
    private final Button apply;
    private ThresholdCalibrator.Result result;

    public R31CalibrationPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(12), dp(10), dp(12), dp(10));
        setBackgroundResource(R.drawable.r3_panel);

        title = new TextView(context);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setText("经验阈值标定");
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        body = new TextView(context);
        body.setTextColor(Color.rgb(210, 232, 227));
        body.setTextSize(11.5f);
        body.setPadding(0, dp(5), 0, dp(6));
        body.setText("等待本地身份参考样本。经验标定仅用于本机实验，不等同于生产 FAR 认证。");
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        apply = new Button(context);
        apply.setText("采用建议阈值");
        apply.setAllCaps(false);
        apply.setTextSize(13f);
        apply.setEnabled(false);
        apply.setGravity(Gravity.CENTER);
        addView(apply, new LayoutParams(LayoutParams.MATCH_PARENT, dp(46)));
    }

    public void setApplyListener(View.OnClickListener listener) {
        apply.setOnClickListener(listener);
    }

    public void setResult(ModelVariant variant, ThresholdCalibrator.Result result) {
        this.result = result;
        title.setText("经验阈值标定 · " + (variant == null ? "?" : variant.storageKey));
        if (result == null || !result.available) {
            body.setText(result == null
                    ? "暂无标定数据"
                    : String.format(Locale.US,
                    "身份 %d · genuine %d · impostor %d\n%s\n需要至少两个 R3.1 质量录入身份，才能评价 1:N 区分能力。",
                    result.identityCount, result.genuineCount, result.impostorCount, result.message));
            apply.setEnabled(false);
            return;
        }
        body.setText(String.format(Locale.US,
                "身份 %d · genuine %d · impostor %d\n建议 Tid %.3f · FAR %.1f%% · FRR %.1f%% · EER≈%.1f%%\n分离 gap %.3f · 小样本经验值，仅作工程标定",
                result.identityCount, result.genuineCount, result.impostorCount,
                result.suggestedThreshold, result.empiricalFar * 100f,
                result.empiricalFrr * 100f, result.eer * 100f, result.separation));
        apply.setEnabled(true);
    }

    public ThresholdCalibrator.Result currentResult() { return result; }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
