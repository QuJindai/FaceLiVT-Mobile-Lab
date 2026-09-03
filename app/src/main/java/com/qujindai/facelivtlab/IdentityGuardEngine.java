package com.qujindai.facelivtlab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure enrollment-page duplicate guard. It never compares embeddings across models;
 * it aggregates only model-local identity names and scalar confidence evidence.
 */
public final class IdentityGuardEngine {
    private static final int WINDOW = 5;
    private static final int EXISTING_CONFIRM_FRAMES = 3;

    public enum State { CLEAR, SUSPECTED, EXISTING }

    public static final class ModelEvidence {
        public final String top1Name;
        public final float top1Score;
        public final String top2Name;
        public final float top2Score;
        public final boolean marginAvailable;

        public ModelEvidence(String top1Name, float top1Score,
                             String top2Name, float top2Score,
                             boolean marginAvailable) {
            this.top1Name = safe(top1Name);
            this.top1Score = top1Score;
            this.top2Name = safe(top2Name);
            this.top2Score = top2Score;
            this.marginAvailable = marginAvailable;
        }

        public float margin() {
            return marginAvailable && Float.isFinite(top1Score) && Float.isFinite(top2Score)
                    ? top1Score - top2Score : Float.NaN;
        }
    }

    public static final class FrameInput {
        public final int librarySize;
        public final int trackingId;
        public final boolean validProbe;
        public final boolean fullFivePointGeometry;
        public final float tid;
        public final EnumMap<ModelVariant, Float> empiricalThresholds;
        public final EnumMap<ModelVariant, ModelEvidence> evidence;

        public FrameInput(int librarySize, int trackingId,
                          boolean validProbe, boolean fullFivePointGeometry,
                          float tid,
                          EnumMap<ModelVariant, Float> empiricalThresholds,
                          EnumMap<ModelVariant, ModelEvidence> evidence) {
            this.librarySize = Math.max(0, librarySize);
            this.trackingId = trackingId;
            this.validProbe = validProbe;
            this.fullFivePointGeometry = fullFivePointGeometry;
            this.tid = tid;
            this.empiricalThresholds = empiricalThresholds == null
                    ? new EnumMap<>(ModelVariant.class) : new EnumMap<>(empiricalThresholds);
            this.evidence = evidence == null
                    ? new EnumMap<>(ModelVariant.class) : new EnumMap<>(evidence);
        }
    }

    public static final class Snapshot {
        public final State state;
        public final String candidateIdentity;
        public final int cleanFrames;
        public final int confirmingFrames;
        public final String reason;
        public final EnumMap<ModelVariant, ModelEvidence> modelEvidence;
        public final long generation;

        Snapshot(State state, String candidateIdentity, int cleanFrames, int confirmingFrames,
                 String reason, EnumMap<ModelVariant, ModelEvidence> modelEvidence, long generation) {
            this.state = state;
            this.candidateIdentity = safe(candidateIdentity);
            this.cleanFrames = cleanFrames;
            this.confirmingFrames = confirmingFrames;
            this.reason = reason == null ? "" : reason;
            this.modelEvidence = modelEvidence == null
                    ? new EnumMap<>(ModelVariant.class) : new EnumMap<>(modelEvidence);
            this.generation = generation;
        }

        public boolean canCreateNew() {
            return state == State.CLEAR;
        }
    }

    private static final class FrameVerdict {
        final boolean clean;
        final String suspectCandidate;
        final String strongCandidate;
        final EnumMap<ModelVariant, ModelEvidence> evidence;

        FrameVerdict(boolean clean, String suspectCandidate, String strongCandidate,
                     EnumMap<ModelVariant, ModelEvidence> evidence) {
            this.clean = clean;
            this.suspectCandidate = safe(suspectCandidate);
            this.strongCandidate = safe(strongCandidate);
            this.evidence = new EnumMap<>(evidence);
        }
    }

    private final Deque<FrameVerdict> frames = new ArrayDeque<>();
    private long generation = 1L;
    private int trackingId = Integer.MIN_VALUE;
    private Snapshot last = waitingSnapshot("等待有效人脸进行身份防重确认");

    public synchronized long captureGeneration() {
        return generation;
    }

    public synchronized boolean isCurrent(long capturedGeneration) {
        return capturedGeneration == generation;
    }

    public synchronized Snapshot snapshot() {
        return copy(last);
    }

    public synchronized void reset() {
        frames.clear();
        trackingId = Integer.MIN_VALUE;
        generation++;
        last = waitingSnapshot("身份防重证据已重置");
    }

    public synchronized Snapshot push(FrameInput input) {
        if (input == null) {
            last = waitingSnapshot("等待有效身份防重输入");
            return copy(last);
        }

        if (trackingId != input.trackingId) {
            frames.clear();
            trackingId = input.trackingId;
            generation++;
        }

        if (!input.validProbe) {
            frames.clear();
            last = new Snapshot(State.SUSPECTED, "", 0, 0,
                    "Probe 质量不足，禁止新建并等待下一张有效人脸",
                    input.evidence, generation);
            return copy(last);
        }

        if (input.librarySize == 0) {
            frames.clear();
            frames.addLast(new FrameVerdict(true, "", "", input.evidence));
            last = new Snapshot(State.CLEAR, "", 1, 0,
                    "身份库为空 · 首张有效 Probe 已通过防重门，可新建身份",
                    input.evidence, generation);
            return copy(last);
        }

        FrameVerdict verdict = evaluate(input);
        frames.addLast(verdict);
        while (frames.size() > WINDOW) frames.removeFirst();

        int cleanFrames = consecutiveCleanTail();
        String strongTailCandidate = consecutiveStrongCandidateTail();
        int confirmingFrames = strongTailCandidate.isEmpty() ? 0 : consecutiveStrongTail(strongTailCandidate);
        String suspectCandidate = chooseWindowSuspectCandidate(verdict.suspectCandidate);

        if (!strongTailCandidate.isEmpty() && confirmingFrames >= EXISTING_CONFIRM_FRAMES) {
            last = new Snapshot(State.EXISTING, strongTailCandidate, cleanFrames, confirmingFrames,
                    "多模型 + 连续帧一致：已确认历史身份 " + strongTailCandidate + "，禁止另存为新人",
                    input.evidence, generation);
        } else if (cleanFrames >= WINDOW) {
            last = new Snapshot(State.CLEAR, "", cleanFrames, 0,
                    "连续 5 张有效 Probe 均低于疑似重复阈值，可新建身份",
                    input.evidence, generation);
        } else {
            String reason;
            if (!verdict.strongCandidate.isEmpty() && !input.fullFivePointGeometry) {
                reason = "身份分数较高，但本帧缺少完整 5 点几何，只能保持疑似状态";
            } else if (!suspectCandidate.isEmpty()) {
                reason = "疑似已有身份 " + suspectCandidate + " · 继续采样确认或人工选择已有身份";
            } else {
                reason = "正在排除重复身份 · 清洁证据 " + cleanFrames + "/5，期间禁止新建";
            }
            last = new Snapshot(State.SUSPECTED, suspectCandidate, cleanFrames, confirmingFrames,
                    reason, input.evidence, generation);
        }
        return copy(last);
    }

    private FrameVerdict evaluate(FrameInput input) {
        Map<String, Integer> suspectVotes = new HashMap<>();
        Map<String, Float> suspectBest = new HashMap<>();
        Map<String, Integer> strongVotes = new HashMap<>();

        boolean anySuspect = false;
        for (ModelVariant variant : ModelVariant.values()) {
            ModelEvidence e = input.evidence.get(variant);
            if (e == null || e.top1Name.isEmpty()) continue;
            Float empirical = input.empiricalThresholds.get(variant);
            IdentityGuardPolicy.Thresholds thresholds = IdentityGuardPolicy.thresholds(input.tid, empirical);
            if (IdentityGuardPolicy.isSuspect(e.top1Score, thresholds)) {
                anySuspect = true;
                suspectVotes.put(e.top1Name, suspectVotes.getOrDefault(e.top1Name, 0) + 1);
                Float best = suspectBest.get(e.top1Name);
                if (best == null || e.top1Score > best) suspectBest.put(e.top1Name, e.top1Score);
            }
            if (input.fullFivePointGeometry && IdentityGuardPolicy.isStrongVote(
                    input.librarySize, e.top1Score, e.marginAvailable, e.margin(), thresholds)) {
                strongVotes.put(e.top1Name, strongVotes.getOrDefault(e.top1Name, 0) + 1);
            }
        }

        String suspect = bestIdentity(suspectVotes, suspectBest);
        int requiredStrong = input.librarySize <= 1 ? 3 : 2;
        String strong = uniqueStrongIdentity(strongVotes, requiredStrong);
        return new FrameVerdict(!anySuspect, suspect, strong, input.evidence);
    }

    private static String uniqueStrongIdentity(Map<String, Integer> votes, int required) {
        String winner = "";
        int winnerVotes = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            int value = entry.getValue();
            if (value < required) continue;
            if (value > winnerVotes) {
                winner = entry.getKey();
                winnerVotes = value;
                tie = false;
            } else if (value == winnerVotes && !entry.getKey().equals(winner)) {
                tie = true;
            }
        }
        return tie ? "" : winner;
    }

    private static String bestIdentity(Map<String, Integer> votes, Map<String, Float> bestScores) {
        String winner = "";
        int count = -1;
        float score = Float.NEGATIVE_INFINITY;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            float candidateScore = bestScores.getOrDefault(entry.getKey(), Float.NEGATIVE_INFINITY);
            if (entry.getValue() > count || (entry.getValue() == count && candidateScore > score)) {
                winner = entry.getKey();
                count = entry.getValue();
                score = candidateScore;
            }
        }
        return winner;
    }

    private int consecutiveCleanTail() {
        int count = 0;
        List<FrameVerdict> list = new ArrayList<>(frames);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!list.get(i).clean) break;
            count++;
        }
        return count;
    }

    private String consecutiveStrongCandidateTail() {
        if (frames.isEmpty()) return "";
        FrameVerdict lastFrame = frames.peekLast();
        return lastFrame == null ? "" : lastFrame.strongCandidate;
    }

    private int consecutiveStrongTail(String candidate) {
        int count = 0;
        List<FrameVerdict> list = new ArrayList<>(frames);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!candidate.equals(list.get(i).strongCandidate)) break;
            count++;
        }
        return count;
    }

    private String chooseWindowSuspectCandidate(String latest) {
        if (latest != null && !latest.isEmpty()) return latest;
        Map<String, Integer> votes = new HashMap<>();
        Map<String, Float> placeholder = new HashMap<>();
        for (FrameVerdict frame : frames) {
            if (frame.suspectCandidate.isEmpty()) continue;
            votes.put(frame.suspectCandidate, votes.getOrDefault(frame.suspectCandidate, 0) + 1);
            placeholder.put(frame.suspectCandidate, (float) votes.get(frame.suspectCandidate));
        }
        return bestIdentity(votes, placeholder);
    }

    private Snapshot waitingSnapshot(String reason) {
        return new Snapshot(State.SUSPECTED, "", 0, 0, reason,
                new EnumMap<>(ModelVariant.class), generation);
    }

    private static Snapshot copy(Snapshot source) {
        return new Snapshot(source.state, source.candidateIdentity, source.cleanFrames,
                source.confirmingFrames, source.reason, source.modelEvidence, source.generation);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
