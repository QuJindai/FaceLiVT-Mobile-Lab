package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Small-sample empirical threshold calibration. This is an engineering diagnostic, not biometric certification. */
public final class ThresholdCalibrator {
    private ThresholdCalibrator() {}

    public static final class Result {
        public final boolean available;
        public final float suggestedThreshold;
        public final float empiricalFar;
        public final float empiricalFrr;
        public final float eer;
        public final float separation;
        public final int genuineCount;
        public final int impostorCount;
        public final int identityCount;
        public final String message;

        Result(boolean available, float suggestedThreshold, float empiricalFar, float empiricalFrr,
               float eer, float separation, int genuineCount, int impostorCount,
               int identityCount, String message) {
            this.available = available;
            this.suggestedThreshold = suggestedThreshold;
            this.empiricalFar = empiricalFar;
            this.empiricalFrr = empiricalFrr;
            this.eer = eer;
            this.separation = separation;
            this.genuineCount = genuineCount;
            this.impostorCount = impostorCount;
            this.identityCount = identityCount;
            this.message = message;
        }
    }

    public static Result calibrate(int identityCount, float[] genuineScores, float[] impostorScores) {
        float[] genuine = finiteCopy(genuineScores);
        float[] impostor = finiteCopy(impostorScores);
        if (identityCount < 2) {
            return unavailable(identityCount, genuine.length, impostor.length,
                    "至少 2 个身份才能估计 impostor 分布；当前只能做 1:1 稳定性观察");
        }
        if (genuine.length < 2 || impostor.length < 1) {
            return unavailable(identityCount, genuine.length, impostor.length,
                    "样本不足：需要 genuine 与 impostor 分数后才能给出经验阈值");
        }

        Arrays.sort(genuine);
        Arrays.sort(impostor);
        float minGenuine = genuine[0];
        float maxImpostor = impostor[impostor.length - 1];
        float separation = minGenuine - maxImpostor;

        if (maxImpostor < minGenuine) {
            float threshold = (maxImpostor + minGenuine) * 0.5f;
            return new Result(true, threshold, 0f, 0f, 0f, separation,
                    genuine.length, impostor.length, identityCount,
                    String.format(Locale.US, "经验分布已分离 · 建议阈值 %.3f", threshold));
        }

        List<Float> candidates = new ArrayList<>();
        float[] all = new float[genuine.length + impostor.length];
        System.arraycopy(genuine, 0, all, 0, genuine.length);
        System.arraycopy(impostor, 0, all, genuine.length, impostor.length);
        Arrays.sort(all);
        candidates.add(all[0] - 0.001f);
        for (int i = 0; i < all.length - 1; i++) {
            candidates.add((all[i] + all[i + 1]) * 0.5f);
        }
        candidates.add(all[all.length - 1] + 0.001f);

        float bestT = candidates.get(0);
        float bestFar = 1f, bestFrr = 1f;
        float bestBalance = Float.POSITIVE_INFINITY;
        float bestTotal = Float.POSITIVE_INFINITY;
        for (float t : candidates) {
            float far = far(impostor, t);
            float frr = frr(genuine, t);
            float balance = Math.abs(far - frr);
            float total = far + frr;
            if (balance < bestBalance - 1e-7f ||
                    (Math.abs(balance - bestBalance) <= 1e-7f && total < bestTotal)) {
                bestBalance = balance;
                bestTotal = total;
                bestT = t;
                bestFar = far;
                bestFrr = frr;
            }
        }
        float eer = (bestFar + bestFrr) * 0.5f;
        return new Result(true, bestT, bestFar, bestFrr, eer, separation,
                genuine.length, impostor.length, identityCount,
                String.format(Locale.US, "经验阈值 %.3f · FAR %.1f%% · FRR %.1f%% · EER≈%.1f%%",
                        bestT, bestFar * 100f, bestFrr * 100f, eer * 100f));
    }

    private static Result unavailable(int ids, int genuine, int impostor, String message) {
        return new Result(false, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                genuine, impostor, ids, message);
    }

    private static float far(float[] impostor, float threshold) {
        int falseAccept = 0;
        for (float score : impostor) if (score >= threshold) falseAccept++;
        return impostor.length == 0 ? Float.NaN : falseAccept / (float) impostor.length;
    }

    private static float frr(float[] genuine, float threshold) {
        int falseReject = 0;
        for (float score : genuine) if (score < threshold) falseReject++;
        return genuine.length == 0 ? Float.NaN : falseReject / (float) genuine.length;
    }

    private static float[] finiteCopy(float[] input) {
        if (input == null) return new float[0];
        int count = 0;
        for (float v : input) if (Float.isFinite(v)) count++;
        float[] out = new float[count];
        int i = 0;
        for (float v : input) if (Float.isFinite(v)) out[i++] = v;
        return out;
    }
}
