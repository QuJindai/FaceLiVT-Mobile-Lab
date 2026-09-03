package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Compares scalar enrollment geometry across independent XS/S/M embedding spaces. */
public final class ModelComparisonStats {
    public static final class Row {
        public final ModelVariant variant;
        public final float parameterCountM;
        public final int blockCount;
        public final float stability;
        public final float coverage;
        public final float minPairCosine;
        public final float meanPairCosine;
        public final int outlierIndex;
        public final float outlierMeanCosine;

        Row(ModelVariant variant, EnrollmentSession.Summary summary) {
            ModelTopology topology = ModelTopology.forVariant(variant);
            this.variant = variant;
            this.parameterCountM = topology.parameterCountM;
            this.blockCount = topology.blockCount;
            this.stability = summary == null ? Float.NaN : summary.stability;
            this.coverage = summary == null ? Float.NaN : summary.coverage;
            this.minPairCosine = summary == null ? Float.NaN : summary.minPairCosine;
            this.meanPairCosine = summary == null ? Float.NaN : summary.meanPairCosine;
            this.outlierIndex = summary == null ? -1 : summary.outlierIndex;
            this.outlierMeanCosine = summary == null ? Float.NaN : summary.outlierMeanCosine;
        }
    }

    public final List<Row> rows;
    public final float[][] deltaXsMinusS;
    public final float[][] deltaMMinusS;

    private ModelComparisonStats(List<Row> rows, float[][] deltaXsMinusS, float[][] deltaMMinusS) {
        this.rows = rows;
        this.deltaXsMinusS = deltaXsMinusS;
        this.deltaMMinusS = deltaMMinusS;
    }

    public static ModelComparisonStats from(EnumMap<ModelVariant, EnrollmentSession.Summary> summaries) {
        EnumMap<ModelVariant, EnrollmentSession.Summary> safe = summaries == null
                ? new EnumMap<>(ModelVariant.class) : summaries;
        List<Row> rows = new ArrayList<>();
        for (ModelVariant variant : ModelVariant.values()) rows.add(new Row(variant, safe.get(variant)));
        EnrollmentSession.Summary xs = safe.get(ModelVariant.XS);
        EnrollmentSession.Summary s = safe.get(ModelVariant.S);
        EnrollmentSession.Summary m = safe.get(ModelVariant.M);
        return new ModelComparisonStats(rows,
                compatible(xs, s) ? delta(xs.similarityMatrix, s.similarityMatrix) : new float[0][0],
                compatible(m, s) ? delta(m.similarityMatrix, s.similarityMatrix) : new float[0][0]);
    }

    private static boolean compatible(EnrollmentSession.Summary a, EnrollmentSession.Summary b) {
        return a != null && b != null && a.similarityMatrix.length == b.similarityMatrix.length;
    }

    public static float[][] delta(float[][] a, float[][] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("matrix shapes must match");
        }
        float[][] out = new float[a.length][];
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null || b[i] == null || a[i].length != b[i].length) {
                throw new IllegalArgumentException("matrix row shapes must match");
            }
            out[i] = new float[a[i].length];
            for (int j = 0; j < a[i].length; j++) out[i][j] = a[i][j] - b[i][j];
        }
        return out;
    }
}
