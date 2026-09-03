package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Collects one five-sample enrollment and exposes the template microscope. */
public final class EnrollmentSession {
    public static final float MIN_AVERAGE_QUALITY = 0.55f;
    public static final float MIN_STABILITY = 0.70f;
    public static final int MIN_SAMPLES = 5;

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
        public final float[][] similarityMatrix;
        public final float[][] projection;
        public final float[] sampleToCentroid;
        public final List<FaceQuality.Snapshot> qualities;

        Summary(int sampleCount, float[] centroid, float averageQuality,
                float stability, float dispersion, float[][] similarityMatrix,
                float[][] projection, float[] sampleToCentroid,
                List<FaceQuality.Snapshot> qualities) {
            this.sampleCount = sampleCount;
            this.centroid = centroid;
            this.averageQuality = averageQuality;
            this.stability = stability;
            this.dispersion = dispersion;
            this.similarityMatrix = similarityMatrix;
            this.projection = projection;
            this.sampleToCentroid = sampleToCentroid;
            this.qualities = qualities;
        }

        public boolean passesEnrollment() {
            return sampleCount >= MIN_SAMPLES &&
                    averageQuality >= MIN_AVERAGE_QUALITY &&
                    stability >= MIN_STABILITY;
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

    public synchronized int size(ModelVariant variant) { return samples.get(variant).size(); }

    public synchronized int sampleCount() {
        int max = 0;
        for (List<Sample> values : samples.values()) max = Math.max(max, values.size());
        return max;
    }

    public synchronized void clear() {
        for (List<Sample> values : samples.values()) values.clear();
    }

    public synchronized Summary summary(ModelVariant variant) {
        List<Sample> list = samples.get(variant);
        if (list == null || list.isEmpty()) {
            return new Summary(0, new float[0], 0f, 0f, 1f,
                    new float[0][0], new float[0][0], new float[0], new ArrayList<>());
        }

        int dimensions = list.get(0).embedding.length;
        float[] weighted = new float[dimensions];
        double totalWeight = 0.0;
        double qualitySum = 0.0;
        List<FaceQuality.Snapshot> qualityList = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();

        for (Sample sample : list) {
            if (sample.embedding.length != dimensions) {
                throw new IllegalStateException("embedding dimension changed within session");
            }
            double alpha = Math.max(sample.quality.composite, 0.05f);
            totalWeight += alpha;
            qualitySum += sample.quality.composite;
            qualityList.add(sample.quality);
            vectors.add(sample.embedding.clone());
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
        for (int i = 0; i < n; i++) {
            toCentroid[i] = VectorMath.cosine(list.get(i).embedding, centroid);
            stabilitySum += toCentroid[i];
            for (int j = 0; j < n; j++) {
                matrix[i][j] = VectorMath.cosine(list.get(i).embedding, list.get(j).embedding);
            }
        }
        float stability = (float) (stabilitySum / n);
        float dispersion = 1f - stability;
        float averageQuality = (float) (qualitySum / n);

        List<float[]> projectionInput = new ArrayList<>(vectors);
        projectionInput.add(centroid.clone());
        float[][] projection = EmbeddingProjector.project(projectionInput);

        return new Summary(n, centroid, averageQuality, stability, dispersion,
                matrix, projection, toCentroid, qualityList);
    }
}
