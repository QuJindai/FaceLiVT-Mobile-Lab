package com.qujindai.facelivtlab;

import java.util.EnumMap;

/**
 * Re-checks identity while a five-frame capture is already running so a face swap
 * after the initial Guard decision cannot silently corrupt a new or append session.
 */
public final class EnrollmentIdentityLock {
    private EnrollmentIdentityLock() {}

    public static final class Result {
        public final boolean allowed;
        public final String candidateIdentity;
        public final int targetVotes;
        public final String reason;

        Result(boolean allowed, String candidateIdentity, int targetVotes, String reason) {
            this.allowed = allowed;
            this.candidateIdentity = candidateIdentity == null ? "" : candidateIdentity;
            this.targetVotes = targetVotes;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static Result forNewIdentity(float tid,
                                        EnumMap<ModelVariant, Float> empiricalThresholds,
                                        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> evidence) {
        EnumMap<ModelVariant, Float> empirical = empiricalThresholds == null
                ? new EnumMap<>(ModelVariant.class) : empiricalThresholds;
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> map = evidence == null
                ? new EnumMap<>(ModelVariant.class) : evidence;
        String candidate = "";
        float best = Float.NEGATIVE_INFINITY;
        for (ModelVariant variant : ModelVariant.values()) {
            IdentityGuardEngine.ModelEvidence e = map.get(variant);
            if (e == null || e.top1Name.isEmpty() || !Float.isFinite(e.top1Score)) continue;
            IdentityGuardPolicy.Thresholds thresholds = IdentityGuardPolicy.thresholds(tid, empirical.get(variant));
            if (IdentityGuardPolicy.isSuspect(e.top1Score, thresholds) && e.top1Score > best) {
                best = e.top1Score;
                candidate = e.top1Name;
            }
        }
        if (!candidate.isEmpty()) {
            return new Result(false, candidate, 0,
                    "录入中二次防重命中已有身份 " + candidate + "，本轮新身份采样已停止");
        }
        return new Result(true, "", 0, "录入中二次防重 PASS");
    }

    public static Result forAppend(String targetIdentity, int librarySize, float tid,
                                   EnumMap<ModelVariant, Float> empiricalThresholds,
                                   EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> evidence) {
        String target = targetIdentity == null ? "" : targetIdentity.trim();
        if (target.isEmpty()) return new Result(false, "", 0, "追加学习缺少目标身份");
        EnumMap<ModelVariant, Float> empirical = empiricalThresholds == null
                ? new EnumMap<>(ModelVariant.class) : empiricalThresholds;
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> map = evidence == null
                ? new EnumMap<>(ModelVariant.class) : evidence;

        int votes = 0;
        String competing = "";
        float competingBest = Float.NEGATIVE_INFINITY;
        for (ModelVariant variant : ModelVariant.values()) {
            IdentityGuardEngine.ModelEvidence e = map.get(variant);
            if (e == null || e.top1Name.isEmpty() || !Float.isFinite(e.top1Score)) continue;
            IdentityGuardPolicy.Thresholds thresholds = IdentityGuardPolicy.thresholds(tid, empirical.get(variant));
            if (target.equals(e.top1Name) && IdentityGuardPolicy.isSuspect(e.top1Score, thresholds)) {
                votes++;
            } else if (!target.equals(e.top1Name) && IdentityGuardPolicy.isSuspect(e.top1Score, thresholds)
                    && e.top1Score > competingBest) {
                competingBest = e.top1Score;
                competing = e.top1Name;
            }
        }
        int required = Math.max(1, librarySize) <= 1 ? 3 : 2;
        if (votes >= required) {
            return new Result(true, target, votes,
                    "追加身份锁 PASS · " + votes + "/3 模型仍确认 " + target);
        }
        String suffix = competing.isEmpty() ? "" : " · 当前更像 " + competing;
        return new Result(false, competing, votes,
                "追加身份锁 FAIL · 仅 " + votes + "/3 模型确认 " + target + suffix);
    }
}
