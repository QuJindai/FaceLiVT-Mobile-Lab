package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Immutable metadata for one successful five-frame R5 learning version. */
public final class EnrollmentHistoryRecord {
    public static final int FRAME_COUNT = 5;

    public final String identity;
    public final int version;
    public final long timestampMs;
    public final String profile;
    public final int effectiveSamplesBefore;
    public final int effectiveSamplesAfter;
    public final List<FrameRecord> frames;
    public final EnumMap<ModelVariant, ModelRecord> models;

    public EnrollmentHistoryRecord(String identity, int version, long timestampMs, String profile,
                                   int effectiveSamplesBefore, int effectiveSamplesAfter,
                                   List<FrameRecord> frames,
                                   EnumMap<ModelVariant, ModelRecord> models) {
        if (identity == null || identity.trim().isEmpty()) throw new IllegalArgumentException("identity required");
        if (version <= 0) throw new IllegalArgumentException("version must be positive");
        if (frames == null || frames.size() != FRAME_COUNT) throw new IllegalArgumentException("exactly five frames required");
        if (models == null) throw new IllegalArgumentException("model evidence required");
        for (ModelVariant variant : ModelVariant.values()) {
            if (!models.containsKey(variant) || models.get(variant) == null) {
                throw new IllegalArgumentException("missing model evidence for " + variant.storageKey);
            }
        }
        this.identity = identity.trim();
        this.version = version;
        this.timestampMs = timestampMs;
        this.profile = profile == null ? "" : profile;
        this.effectiveSamplesBefore = Math.max(0, effectiveSamplesBefore);
        this.effectiveSamplesAfter = Math.max(0, effectiveSamplesAfter);
        this.frames = new ArrayList<>(FRAME_COUNT);
        for (FrameRecord frame : frames) {
            if (frame == null) throw new IllegalArgumentException("frame cannot be null");
            this.frames.add(frame.copy());
        }
        this.models = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) this.models.put(variant, models.get(variant).copy());
    }

    public static EnrollmentHistoryRecord fromSession(String identity, int version, long timestampMs,
                                                      String profile, int effectiveSamplesBefore,
                                                      int effectiveSamplesAfter, EnrollmentSession session,
                                                      List<AlignmentGeometry> geometries) {
        if (session == null || geometries == null || geometries.size() != FRAME_COUNT) {
            throw new IllegalArgumentException("five-frame session and geometry evidence required");
        }
        EnrollmentSession.Summary qualitySource = session.summary(ModelVariant.S);
        if (qualitySource.sampleCount != FRAME_COUNT || qualitySource.qualities.size() != FRAME_COUNT) {
            throw new IllegalArgumentException("successful five-frame enrollment session required");
        }
        List<FrameRecord> frames = new ArrayList<>();
        for (int i = 0; i < FRAME_COUNT; i++) frames.add(new FrameRecord(qualitySource.qualities.get(i), geometries.get(i)));

        EnumMap<ModelVariant, ModelRecord> models = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary s = session.summary(variant);
            if (s.sampleCount != FRAME_COUNT) throw new IllegalArgumentException("missing five samples for " + variant.storageKey);
            models.put(variant, new ModelRecord(session.embeddings(variant), s.centroid, s.sampleToCentroid,
                    s.averageQuality, s.stability, s.dispersion, s.embeddingCoverage, s.poseCoverage,
                    s.coverage, s.minPairCosine, s.meanPairCosine, s.outlierIndex,
                    s.outlierMeanCosine, s.allSamplesPassHardGate));
        }
        return new EnrollmentHistoryRecord(identity, version, timestampMs, profile,
                effectiveSamplesBefore, effectiveSamplesAfter, frames, models);
    }

    public EnrollmentSession toEnrollmentSession() {
        EnrollmentSession session = new EnrollmentSession();
        for (ModelVariant variant : ModelVariant.values()) {
            ModelRecord model = models.get(variant);
            int count = Math.min(FRAME_COUNT, model.embeddings.size());
            for (int i = 0; i < count; i++) session.add(variant, model.embeddings.get(i), frames.get(i).quality);
        }
        return session;
    }

    public static final class FrameRecord {
        public final FaceQuality.Snapshot quality;
        public final AlignmentGeometry geometry;

        public FrameRecord(FaceQuality.Snapshot quality, AlignmentGeometry geometry) {
            if (quality == null || geometry == null) throw new IllegalArgumentException("quality and geometry required");
            this.quality = copyQuality(quality);
            this.geometry = copyGeometry(geometry);
        }

        FrameRecord copy() { return new FrameRecord(quality, geometry); }
    }

    public static final class ModelRecord {
        public final List<float[]> embeddings;
        public final float[] centroid;
        public final float[] sampleToCentroid;
        public final float averageQuality;
        public final float stability;
        public final float dispersion;
        public final float embeddingCoverage;
        public final float poseCoverage;
        public final float coverage;
        public final float minPairCosine;
        public final float meanPairCosine;
        public final int outlierIndex;
        public final float outlierMeanCosine;
        public final boolean allSamplesPassHardGate;

        public ModelRecord(List<float[]> embeddings, float[] centroid, float[] sampleToCentroid,
                           float averageQuality, float stability, float dispersion,
                           float embeddingCoverage, float poseCoverage, float coverage,
                           float minPairCosine, float meanPairCosine,
                           int outlierIndex, float outlierMeanCosine,
                           boolean allSamplesPassHardGate) {
            if (embeddings == null || embeddings.size() != FRAME_COUNT) throw new IllegalArgumentException("five embeddings required");
            if (centroid == null || centroid.length == 0) throw new IllegalArgumentException("centroid required");
            if (sampleToCentroid == null || sampleToCentroid.length != FRAME_COUNT) throw new IllegalArgumentException("five sample scores required");
            this.embeddings = new ArrayList<>(FRAME_COUNT);
            for (float[] embedding : embeddings) {
                if (embedding == null || embedding.length == 0) throw new IllegalArgumentException("embedding required");
                this.embeddings.add(embedding.clone());
            }
            this.centroid = centroid.clone();
            this.sampleToCentroid = sampleToCentroid.clone();
            this.averageQuality = averageQuality;
            this.stability = stability;
            this.dispersion = dispersion;
            this.embeddingCoverage = embeddingCoverage;
            this.poseCoverage = poseCoverage;
            this.coverage = coverage;
            this.minPairCosine = minPairCosine;
            this.meanPairCosine = meanPairCosine;
            this.outlierIndex = outlierIndex;
            this.outlierMeanCosine = outlierMeanCosine;
            this.allSamplesPassHardGate = allSamplesPassHardGate;
        }

        ModelRecord copy() {
            return new ModelRecord(embeddings, centroid, sampleToCentroid, averageQuality, stability,
                    dispersion, embeddingCoverage, poseCoverage, coverage, minPairCosine,
                    meanPairCosine, outlierIndex, outlierMeanCosine, allSamplesPassHardGate);
        }
    }

    private static FaceQuality.Snapshot copyQuality(FaceQuality.Snapshot q) {
        return new FaceQuality.Snapshot(q.sharpness, q.brightness, q.contrast, q.pose,
                q.landmarks, q.size, q.composite, q.yaw, q.pitch, q.roll, q.faceAreaRatio);
    }

    private static AlignmentGeometry copyGeometry(AlignmentGeometry g) {
        return AlignmentGeometry.restore(g.landmarkCount, g.sourcePoints, g.targetPoints,
                g.transformedPoints, g.eyeDistancePx, g.rollDeg, g.scale, g.translationPx,
                g.meanResidualPx, g.maxResidualPx, g.usedFallback);
    }
}
