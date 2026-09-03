package com.qujindai.facelivtlab;

/** Compact real activation statistics emitted by the R4 diagnostic FaceLiVT graph. */
public final class DeepModelStats {
    public static final int[] DEPTHS = {3, 3, 9, 3};
    public static final int BLOCK_COUNT = 18;
    public static final int STAGE_COUNT = 4;

    public static final class BlockStat {
        public final float meanAbs;
        public final float rms;
        public final float std;
        public final float nearZero;
        public final float relativeDelta;

        BlockStat(float[] row) {
            if (row == null || row.length != 5) throw new IllegalArgumentException("block stat must have 5 values");
            meanAbs = row[0];
            rms = row[1];
            std = row[2];
            nearZero = row[3];
            relativeDelta = row[4];
        }
    }

    public static final class StageStat {
        public final float meanAbs;
        public final float rms;
        public final float std;
        public final float nearZero;

        StageStat(float[] row) {
            if (row == null || row.length != 4) throw new IllegalArgumentException("stage stat must have 4 values");
            meanAbs = row[0];
            rms = row[1];
            std = row[2];
            nearZero = row[3];
        }
    }

    public final ModelVariant variant;
    public final int[] stageDepths;
    public final BlockStat[] blocks;
    public final StageStat[] stages;
    public final float preHeadMeanAbs;
    public final float preHeadRms;
    public final float preHeadStd;
    public final float preHeadNearZero;

    public DeepModelStats(ModelVariant variant, float[][] blockStats, float[][] stageStats, float[] preHeadStats) {
        this.variant = variant == null ? ModelVariant.S : variant;
        if (blockStats == null || blockStats.length != BLOCK_COUNT) {
            throw new IllegalArgumentException("block_stats must be 18x5");
        }
        if (stageStats == null || stageStats.length != STAGE_COUNT) {
            throw new IllegalArgumentException("stage_stats must be 4x4");
        }
        if (preHeadStats == null || preHeadStats.length != 4) {
            throw new IllegalArgumentException("prehead_stats must have 4 values");
        }
        stageDepths = DEPTHS.clone();
        blocks = new BlockStat[BLOCK_COUNT];
        for (int i = 0; i < BLOCK_COUNT; i++) blocks[i] = new BlockStat(blockStats[i].clone());
        stages = new StageStat[STAGE_COUNT];
        for (int i = 0; i < STAGE_COUNT; i++) stages[i] = new StageStat(stageStats[i].clone());
        preHeadMeanAbs = preHeadStats[0];
        preHeadRms = preHeadStats[1];
        preHeadStd = preHeadStats[2];
        preHeadNearZero = preHeadStats[3];
    }

    public static DeepModelStats empty(ModelVariant variant) {
        float[][] blocks = new float[BLOCK_COUNT][5];
        float[][] stages = new float[STAGE_COUNT][4];
        return new DeepModelStats(variant, blocks, stages, new float[4]);
    }

    public static int stageForBlock(int zeroBasedBlock) {
        if (zeroBasedBlock < 0 || zeroBasedBlock >= BLOCK_COUNT) {
            throw new IllegalArgumentException("block index must be 0..17");
        }
        int cursor = 0;
        for (int stage = 0; stage < DEPTHS.length; stage++) {
            cursor += DEPTHS[stage];
            if (zeroBasedBlock < cursor) return stage;
        }
        return DEPTHS.length - 1;
    }
}
