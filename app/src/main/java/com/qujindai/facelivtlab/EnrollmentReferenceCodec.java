package com.qujindai.facelivtlab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Compact local codec for enrollment reference embeddings used only by the on-device microscope. */
public final class EnrollmentReferenceCodec {
    private static final int MAGIC = 0x46333152; // F31R
    private static final int VERSION = 1;

    private EnrollmentReferenceCodec() {}

    public static final class Record {
        public final List<float[]> embeddings;
        public final float[] genuineScores;

        public Record(List<float[]> embeddings, float[] genuineScores) {
            this.embeddings = new ArrayList<>();
            if (embeddings != null) {
                for (float[] embedding : embeddings) {
                    if (embedding == null) throw new IllegalArgumentException("embedding cannot be null");
                    this.embeddings.add(embedding.clone());
                }
            }
            this.genuineScores = genuineScores == null ? new float[0] : genuineScores.clone();
        }
    }

    public static String encode(Record record) {
        if (record == null) throw new IllegalArgumentException("record required");
        int bytes = 16 + record.genuineScores.length * 4;
        for (float[] embedding : record.embeddings) bytes += 4 + embedding.length * 4;
        ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.putInt(VERSION);
        buffer.putInt(record.embeddings.size());
        for (float[] embedding : record.embeddings) {
            buffer.putInt(embedding.length);
            for (float v : embedding) buffer.putFloat(v);
        }
        buffer.putInt(record.genuineScores.length);
        for (float v : record.genuineScores) buffer.putFloat(v);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public static Record decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return new Record(new ArrayList<>(), new float[0]);
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).order(ByteOrder.LITTLE_ENDIAN);
            if (buffer.getInt() != MAGIC || buffer.getInt() != VERSION) {
                throw new IllegalArgumentException("unsupported enrollment reference record");
            }
            int count = checkedCount(buffer.getInt(), 128);
            List<float[]> embeddings = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int dim = checkedCount(buffer.getInt(), 8192);
                if (buffer.remaining() < dim * 4) throw new IllegalArgumentException("truncated embedding record");
                float[] embedding = new float[dim];
                for (int j = 0; j < dim; j++) embedding[j] = buffer.getFloat();
                embeddings.add(embedding);
            }
            int scores = checkedCount(buffer.getInt(), 4096);
            if (buffer.remaining() < scores * 4) throw new IllegalArgumentException("truncated score record");
            float[] genuine = new float[scores];
            for (int i = 0; i < scores; i++) genuine[i] = buffer.getFloat();
            return new Record(embeddings, genuine);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) throw e;
            throw new IllegalArgumentException("invalid enrollment reference record", e);
        }
    }

    private static int checkedCount(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("invalid record count");
        return value;
    }
}
