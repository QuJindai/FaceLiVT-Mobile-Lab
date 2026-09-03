package com.qujindai.facelivtlab;

/** Compact, shape-validated internal observability outputs from one FaceLiVTv2 forward pass. */
public final class ModelDiagnostics {
    public static final int BLOCK_COUNT = 18;
    public static final int STAGE_COUNT = 4;
    public static final int STAT_COUNT = 4;
    public static final int PREHEAD_DIM = 1284;
    public static final float SPARSITY_THRESHOLD = 0.05f;

    /** Columns: meanAbs, RMS, sparsity, deltaRatio. */
    public final float[][] blockStats;
    /** Columns: meanAbs, RMS, sparsity, deltaRatio. */
    public final float[][] stageStats;
    public final float[] prehead;
    public final float preheadMeanAbs;
    public final float preheadRms;
    public final float preheadSparsity;

    private ModelDiagnostics(float[][] blockStats, float[][] stageStats, float[] prehead,
                             float preheadMeanAbs, float preheadRms, float preheadSparsity) {
        this.blockStats = copy2d(blockStats);
        this.stageStats = copy2d(stageStats);
        this.prehead = prehead.clone();
        this.preheadMeanAbs = preheadMeanAbs;
        this.preheadRms = preheadRms;
        this.preheadSparsity = preheadSparsity;
    }

    public static ModelDiagnostics of(float[][] blockStats, float[][] stageStats, float[] prehead) {
        validate(blockStats, BLOCK_COUNT, STAT_COUNT, "block_stats");
        validate(stageStats, STAGE_COUNT, STAT_COUNT, "stage_stats");
        if (prehead == null || prehead.length != PREHEAD_DIM) {
            throw new IllegalArgumentException("prehead must have length " + PREHEAD_DIM);
        }
        double absSum = 0d;
        double squareSum = 0d;
        int sparse = 0;
        for (float value : prehead) {
            float abs = Math.abs(value);
            absSum += abs;
            squareSum += (double) value * value;
            if (abs < SPARSITY_THRESHOLD) sparse++;
        }
        float meanAbs = (float) (absSum / PREHEAD_DIM);
        float rms = (float) Math.sqrt(squareSum / PREHEAD_DIM);
        float sparsity = sparse / (float) PREHEAD_DIM;
        return new ModelDiagnostics(blockStats, stageStats, prehead, meanAbs, rms, sparsity);
    }

    public float[] block(int index) {
        if (index < 0 || index >= BLOCK_COUNT) throw new IllegalArgumentException("block index out of range");
        return blockStats[index].clone();
    }

    public float[] stage(int index) {
        if (index < 0 || index >= STAGE_COUNT) throw new IllegalArgumentException("stage index out of range");
        return stageStats[index].clone();
    }

    private static void validate(float[][] values, int rows, int cols, String label) {
        if (values == null || values.length != rows) {
            throw new IllegalArgumentException(label + " must be " + rows + "x" + cols);
        }
        for (float[] row : values) {
            if (row == null || row.length != cols) {
                throw new IllegalArgumentException(label + " must be " + rows + "x" + cols);
            }
        }
    }

    private static float[][] copy2d(float[][] source) {
        float[][] out = new float[source.length][];
        for (int i = 0; i < source.length; i++) out[i] = source[i].clone();
        return out;
    }
}
