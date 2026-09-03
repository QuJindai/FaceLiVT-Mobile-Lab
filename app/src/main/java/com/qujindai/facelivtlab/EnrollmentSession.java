package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Collects one five-sample enrollment and exposes stability AND coverage as separate template qualities. */
public final class EnrollmentSession {
    public static final float MIN_AVERAGE_QUALITY = 0.55f;
    public static final float MIN_STABILITY = 0.70f;
    public static final float MIN_COVERAGE = 0.35f;
    public static final int MIN_SAMPLES = 5;
    private static final float NOVELTY_MAX_COSINE = 0.985f;
    private static final float NOVELTY_POSE_DELTA_DEG = 3.0f;

    private static final class Sample {
        final float[] embedding;
        final FaceQuality.Snapshot quality;
        Sample(float[] embedding, FaceQuality.Snapshot quality) {
            this.embedding = VectorMath.normalize(embedding);
            this.quality = quality;
        }
    }

    public static final class Summary {
        public final int sampleCount;
        public final float[] centroid;
        public final float averageQuality;
        public final float stability;
        public final float dispersion;
        public final float coverage;
        public final float embeddingCoverage;
        public final float poseCoverage;
        public final boolean allSamplesPassHardGate;
        public final float[][] similarityMatrix;
        public final float[][] projection;
        public final float[] sampleToCentroid;
        public final List<FaceQuality.Snapshot> qualities;

        Summary(int sampleCount, float[] centroid, float averageQuality,
                float stability, float dispersion, float coverage,
                float embeddingCoverage, float poseCoverage, boolean allSamplesPassHardGate,
                float[][] similarityMatrix, float[][] projection, float[] sampleToCentroid,
                List<FaceQuality.Snapshot> qualities) {
            this.sampleCount = sampleCount;
            this.centroid = centroid;
            this.averageQuality = averageQuality;
            this.stability = stability;
            this.dispersion = dispersion;
            this.coverage = coverage;
            this.embeddingCoverage = embeddingCoverage;
            this.poseCoverage = poseCoverage;
            this.allSamplesPassHardGate = allSamplesPassHardGate;
            this.similarityMatrix = similarityMatrix;
            this.projection = projection;
            this.sampleToCentroid = sampleToCentroid;
            this.qualities = qualities;
        }

        public boolean passesEnrollment() {
            return sampleCount >= MIN_SAMPLES &&
                    averageQuality >= MIN_AVERAGE_QUALITY &&
                    stability >= MIN_STABILITY &&
                    coverage >= MIN_COVERAGE &&
                    allSamplesPassHardGate;
        }
    }

    private final EnumMap<ModelVariant, List<Sample>> samples = new EnumMap<>(ModelVariant.class);

    public EnrollmentSession() {
        for (ModelVariant variant : ModelVariant.values()) samples.put(variant, new ArrayList<>());
    }

    public synchronized void add(ModelVariant variant, float[] embedding, FaceQuality.Snapshot quality) {
        if (variant == null || embedding == null || embedding.length == 0 || quality == null) {
            throw new IllegalArgumentException("variant, embedding and quality are required");
        }
        samples.get(variant).add(new Sample(embedding.clone(), quality));
    }

    public synchronized boolean isNovelCandidate(ModelVariant variant, float[] embedding, FaceQuality.Snapshot quality) {
        if (variant == null || embedding == null || embedding.length == 0 || quality == null) return false;
        List<Sample> list = samples.get(variant);
        if (list == null || list.isEmpty()) return true;
        float[] normalized = VectorMath.normalize(embedding);
        float maxCosine = -1f;
        float maxPoseDelta = 0f;
        for (Sample sample : list) {
            if (sample.embedding.length != normalized.length) continue;
            maxCosine = Math.max(maxCosine, VectorMath.cosine(sample.embedding, normalized));
            float yawDelta = Math.abs(sample.quality.yaw - quality.yaw);
            float pitchDelta = Math.abs(sample.quality.pitch - quality.pitch);
            float rollDelta = Math.abs(sample.quality.roll - quality.roll);
            maxPoseDelta = Math.max(maxPoseDelta, Math.max(yawDelta, Math.max(pitchDelta, rollDelta)));
        }
        return maxCosine < NOVELTY_MAX_COSINE || maxPoseDelta >= NOVELTY_POSE_DELTA_DEG;
    }

    public synchronized int size(ModelVariant variant) { return samples.get(variant).size(); }

    public synchronized int sampleCount() {
        int max = 0;
        for (List<Sample> values : samples.values()) max = Math.max(max, values.size());
        return max;
    }

    public synchronized void clear() {
        for (List<Sample> values : samples.values()) values.clear();
    }

    public synchronized List<float[]> embeddings(ModelVariant variant) {
        List<float[]> out = new ArrayList<>();
        List<Sample> list = samples.get(variant);
        if (list == null) return out;
        for (Sample sample : list) out.add(sample.embedding.clone());
        return out;
    }

    public synchronized Summary summary(ModelVariant variant) {
        List<Sample> list = samples.get(variant);
        if (list == null || list.isEmpty()) {
            return new Summary(0, new float[0], 0f, 0f, 1f,
                    0f, 0f, 0f, false,
                    new float[0][0], new float[0][0], new float[0], new ArrayList<>());
        }

        int dimensions = list.get(0).embedding.length;
        float[] weighted = new float[dimensions];
        double totalWeight = 0.0;
        double qualitySum = 0.0;
        List<FaceQuality.Snapshot> qualityList = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        boolean hardPass = true;

        float minYaw = Float.POSITIVE_INFINITY, maxYaw = Float.NEGATIVE_INFINITY;
        float minPitch = Float.POSITIVE_INFINITY, maxPitch = Float.NEGATIVE_INFINITY;
        for (Sample sample : list) {
            if (sample.embedding.length != dimensions) {
                throw new IllegalStateException("embedding dimension changed within session");
            }
            double alpha = Math.max(sample.quality.composite, 0.05f);
            totalWeight += alpha;
            qualitySum += sample.quality.composite;
            hardPass &= sample.quality.passesEnrollmentGate();
            qualityList.add(sample.quality);
            vectors.add(sample.embedding.clone());
            minYaw = Math.min(minYaw, sample.quality.yaw);
            maxYaw = Math.max(maxYaw, sample.quality.yaw);
            minPitch = Math.min(minPitch, sample.quality.pitch);
            maxPitch = Math.max(maxPitch, sample.quality.pitch);
            for (int i = 0; i < dimensions; i++) weighted[i] += sample.embedding[i] * alpha;
        }
        if (totalWeight > 0.0) {
            for (int i = 0; i < weighted.length; i++) weighted[i] /= (float) totalWeight;
        }
        float[] centroid = VectorMath.normalize(weighted);

        int n = list.size();
        float[][] matrix = new float[n][n];
        float[] toCentroid = new float[n];
        double stabilitySum = 0.0;
        double pairDistanceSum = 0.0;
        int pairCount = 0;
        for (int i = 0; i < n; i++) {
            toCentroid[i] = VectorMath.cosine(list.get(i).embedding, centroid);
            stabilitySum += toCentroid[i];
            for (int j = 0; j < n; j++) {
                float cosine = VectorMath.cosine(list.get(i).embedding, list.get(j).embedding);
                matrix[i][j] = cosine;
                if (j > i) {
                    pairDistanceSum += Math.max(0f, 1f - cosine);
                    pairCount++;
                }
            }
        }
        float stability = (float) (stabilitySum / n);
        float dispersion = 1f - stability;
        float averageQuality = (float) (qualitySum / n);

        float meanPairDistance = pairCount == 0 ? 0f : (float) (pairDistanceSum / pairCount);
        float embeddingCoverage = clamp01(meanPairDistance / 0.06f);
        float yawCoverage = clamp01((maxYaw - minYaw) / 12f);
        float pitchCoverage = clamp01((maxPitch - minPitch) / 10f);
        float poseCoverage = (yawCoverage + pitchCoverage) * 0.5f;
        // Geometric mean requires both identity-space variation and real pose coverage.
        float coverage = (float) Math.sqrt(Math.max(0f, embeddingCoverage * poseCoverage));

        List<float[]> projectionInput = new ArrayList<>(vectors);
        projectionInput.add(centroid.clone());
        float[][] projection = EmbeddingProjector.project(projectionInput);

        return new Summary(n, centroid, averageQuality, stability, dispersion,
                coverage, embeddingCoverage, poseCoverage, hardPass,
                matrix, projection, toCentroid, qualityList);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
