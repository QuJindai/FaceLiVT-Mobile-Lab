package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionLoggerR4Test {
    @Test public void r4MicroscopeHeaderAndRowHaveSameColumns() {
        SessionLogger logger = new SessionLogger();
        float[] src = {10,20, 30,20, 20,30, 12,40, 28,40};
        float[] dst = src.clone();
        float[] affine = SimilarityTransform.fit(src, dst);
        AlignmentGeometry geometry = AlignmentGeometry.fromTransform(src, dst, affine);
        DeepModelStats deep = DeepModelStats.empty(ModelVariant.S);
        FaceQuality.Snapshot quality = FaceQuality.compose(1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f);

        logger.addMicroscope(1L, "480p", ModelVariant.S,
                1280, 720, 854, 480, 854, 480, 220, 220,
                quality, geometry, deep,
                "person", "other", .8f, .2f, .45f, true, 5,
                20, 1, 12, 1, 34, null);

        String[] lines = logger.toCsv().trim().split("\\n");
        assertEquals(2, lines.length);
        String[] header = lines[0].split(",", -1);
        String[] row = lines[1].split(",", -1);
        assertEquals(46, header.length);
        assertEquals(header.length, row.length);
        assertEquals("landmark_count", header[17]);
        assertEquals("5", row[17]);
        assertEquals("false", row[18]);
        assertEquals("stage1_rms", header[25]);
        assertEquals("prehead_rms", header[29]);
    }

    @Test public void compatibilityRowsStillKeepR4ColumnAlignment() {
        SessionLogger logger = new SessionLogger();
        logger.add(2L, "144p", ModelVariant.XS,
                1280, 720, 256, 144, 360, 360, 80, 80,
                "person", .7f, true, 5, 30, 8, 38, null);

        String[] lines = logger.toCsv().trim().split("\\n");
        assertEquals(lines[0].split(",", -1).length, lines[1].split(",", -1).length);
        assertTrue(lines[0].contains("align_mean_residual_px"));
        assertTrue(lines[0].contains("prehead_rms"));
    }
}
