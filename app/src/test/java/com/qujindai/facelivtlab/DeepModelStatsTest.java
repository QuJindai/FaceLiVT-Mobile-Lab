package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DeepModelStatsTest {
    @Test public void facelivtTopologyHasEighteenBlocksInFourStages() {
        DeepModelStats stats = DeepModelStats.empty(ModelVariant.S);
        assertEquals(18, stats.blocks.length);
        assertArrayEquals(new int[]{3, 3, 9, 3}, stats.stageDepths);
        assertEquals(4, stats.stages.length);
    }

    @Test public void blockIndicesMapToExpectedStages() {
        assertEquals(0, DeepModelStats.stageForBlock(0));
        assertEquals(0, DeepModelStats.stageForBlock(2));
        assertEquals(1, DeepModelStats.stageForBlock(3));
        assertEquals(1, DeepModelStats.stageForBlock(5));
        assertEquals(2, DeepModelStats.stageForBlock(6));
        assertEquals(2, DeepModelStats.stageForBlock(14));
        assertEquals(3, DeepModelStats.stageForBlock(15));
        assertEquals(3, DeepModelStats.stageForBlock(17));
    }
}
