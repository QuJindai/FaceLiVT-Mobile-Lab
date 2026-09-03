package com.qujindai.facelivtlab;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;

/** Versioned compact metadata/embedding codec; thumbnail bytes live in app-private files. */
public final class EnrollmentHistoryCodec {
    private static final int MAGIC = 0x52354831; // R5H1
    private static final int VERSION = 1;
    private static final int MAX_VECTOR_DIM = 8192;
    private static final int MAX_POINT_FLOATS = 64;

    private EnrollmentHistoryCodec() {}

    public static String encode(EnrollmentHistoryRecord record) {
        if (record == null) throw new IllegalArgumentException("record required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(record.identity);
            out.writeInt(record.version);
            out.writeLong(record.timestampMs);
            out.writeUTF(record.profile);
            out.writeInt(record.effectiveSamplesBefore);
            out.writeInt(record.effectiveSamplesAfter);
            out.writeInt(record.frames.size());
            for (EnrollmentHistoryRecord.FrameRecord frame : record.frames) writeFrame(out, frame);
            out.writeInt(ModelVariant.values().length);
            for (ModelVariant variant : ModelVariant.values()) {
                out.writeInt(variant.ordinal());
                writeModel(out, record.models.get(variant));
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("history encoding failed", e);
        }
    }

    public static EnrollmentHistoryRecord decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) throw new IllegalArgumentException("history record required");
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IllegalArgumentException("unsupported history record");
            String identity = in.readUTF();
            int version = checked(in.readInt(), 1, 1_000_000, "version");
            long timestamp = in.readLong();
            String profile = in.readUTF();
            int before = checked(in.readInt(), 0, 1_000_000, "effective samples before");
            int after = checked(in.readInt(), 0, 1_000_000, "effective samples after");
            int frameCount = checked(in.readInt(), EnrollmentHistoryRecord.FRAME_COUNT,
                    EnrollmentHistoryRecord.FRAME_COUNT, "frame count");
            List<EnrollmentHistoryRecord.FrameRecord> frames = new ArrayList<>();
            for (int i = 0; i < frameCount; i++) frames.add(readFrame(in));

            int modelCount = checked(in.readInt(), ModelVariant.values().length,
                    ModelVariant.values().length, "model count");
            EnumMap<ModelVariant, EnrollmentHistoryRecord.ModelRecord> models = new EnumMap<>(ModelVariant.class);
            for (int i = 0; i < modelCount; i++) {
                int ordinal = checked(in.readInt(), 0, ModelVariant.values().length - 1, "model ordinal");
                ModelVariant variant = ModelVariant.values()[ordinal];
                if (models.containsKey(variant)) throw new IllegalArgumentException("duplicate model record");
                models.put(variant, readModel(in));
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing history bytes");
            return new EnrollmentHistoryRecord(identity, version, timestamp, profile, before, after, frames, models);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (EOFException e) {
            throw new IllegalArgumentException("truncated history record", e);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("invalid history record", e);
        }
    }

    private static void writeFrame(DataOutputStream out, EnrollmentHistoryRecord.FrameRecord frame) throws IOException {
        FaceQuality.Snapshot q = frame.quality;
        out.writeFloat(q.sharpness); out.writeFloat(q.brightness); out.writeFloat(q.contrast);
        out.writeFloat(q.pose); out.writeFloat(q.landmarks); out.writeFloat(q.size);
        out.writeFloat(q.composite); out.writeFloat(q.yaw); out.writeFloat(q.pitch);
        out.writeFloat(q.roll); out.writeFloat(q.faceAreaRatio);

        AlignmentGeometry g = frame.geometry;
        out.writeInt(g.landmarkCount);
        out.writeBoolean(g.usedFallback);
        out.writeFloat(g.eyeDistancePx); out.writeFloat(g.rollDeg); out.writeFloat(g.scale);
        out.writeFloat(g.translationPx); out.writeFloat(g.meanResidualPx); out.writeFloat(g.maxResidualPx);
        writeFloatArray(out, g.sourcePoints);
        writeFloatArray(out, g.targetPoints);
        writeFloatArray(out, g.transformedPoints);
    }

    private static EnrollmentHistoryRecord.FrameRecord readFrame(DataInputStream in) throws IOException {
        FaceQuality.Snapshot q = new FaceQuality.Snapshot(
                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        int landmarks = checked(in.readInt(), 0, 5, "landmarks");
        boolean fallback = in.readBoolean();
        float eye = in.readFloat(), roll = in.readFloat(), scale = in.readFloat();
        float translation = in.readFloat(), mean = in.readFloat(), max = in.readFloat();
        float[] src = readFloatArray(in, MAX_POINT_FLOATS);
        float[] dst = readFloatArray(in, MAX_POINT_FLOATS);
        float[] transformed = readFloatArray(in, MAX_POINT_FLOATS);
        AlignmentGeometry g = AlignmentGeometry.restore(landmarks, src, dst, transformed,
                eye, roll, scale, translation, mean, max, fallback);
        return new EnrollmentHistoryRecord.FrameRecord(q, g);
    }

    private static void writeModel(DataOutputStream out, EnrollmentHistoryRecord.ModelRecord model) throws IOException {
        out.writeInt(model.embeddings.size());
        for (float[] embedding : model.embeddings) writeFloatArray(out, embedding);
        writeFloatArray(out, model.centroid);
        writeFloatArray(out, model.sampleToCentroid);
        out.writeFloat(model.averageQuality);
        out.writeFloat(model.stability);
        out.writeFloat(model.dispersion);
        out.writeFloat(model.embeddingCoverage);
        out.writeFloat(model.poseCoverage);
        out.writeFloat(model.coverage);
        out.writeFloat(model.minPairCosine);
        out.writeFloat(model.meanPairCosine);
        out.writeInt(model.outlierIndex);
        out.writeFloat(model.outlierMeanCosine);
        out.writeBoolean(model.allSamplesPassHardGate);
    }

    private static EnrollmentHistoryRecord.ModelRecord readModel(DataInputStream in) throws IOException {
        int count = checked(in.readInt(), EnrollmentHistoryRecord.FRAME_COUNT,
                EnrollmentHistoryRecord.FRAME_COUNT, "embedding count");
        List<float[]> embeddings = new ArrayList<>();
        for (int i = 0; i < count; i++) embeddings.add(readFloatArray(in, MAX_VECTOR_DIM));
        float[] centroid = readFloatArray(in, MAX_VECTOR_DIM);
        float[] scores = readFloatArray(in, EnrollmentHistoryRecord.FRAME_COUNT);
        if (scores.length != EnrollmentHistoryRecord.FRAME_COUNT) throw new IllegalArgumentException("invalid sample score count");
        float averageQuality = in.readFloat();
        float stability = in.readFloat();
        float dispersion = in.readFloat();
        float embeddingCoverage = in.readFloat();
        float poseCoverage = in.readFloat();
        float coverage = in.readFloat();
        float minPair = in.readFloat();
        float meanPair = in.readFloat();
        int outlierIndex = in.readInt();
        float outlierMean = in.readFloat();
        boolean hardGate = in.readBoolean();
        return new EnrollmentHistoryRecord.ModelRecord(embeddings, centroid, scores, averageQuality,
                stability, dispersion, embeddingCoverage, poseCoverage, coverage, minPair, meanPair,
                outlierIndex, outlierMean, hardGate);
    }

    private static void writeFloatArray(DataOutputStream out, float[] values) throws IOException {
        float[] safe = values == null ? new float[0] : values;
        out.writeInt(safe.length);
        for (float value : safe) out.writeFloat(value);
    }

    private static float[] readFloatArray(DataInputStream in, int max) throws IOException {
        int count = checked(in.readInt(), 0, max, "array size");
        float[] values = new float[count];
        for (int i = 0; i < count; i++) values[i] = in.readFloat();
        return values;
    }

    private static int checked(int value, int min, int max, String label) {
        if (value < min || value > max) throw new IllegalArgumentException("invalid " + label + ": " + value);
        return value;
    }
}
