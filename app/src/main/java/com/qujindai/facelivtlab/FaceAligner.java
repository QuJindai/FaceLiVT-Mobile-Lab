package com.qujindai.facelivtlab;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

public final class FaceAligner {
    private static final int SIZE = 112;
    private static final float[] ARC_FACE_5 = {
            38.2946f, 51.6963f,
            73.5318f, 51.5014f,
            56.0252f, 71.7366f,
            41.5493f, 92.3655f,
            70.7299f, 92.2041f
    };

    public static final class Result {
        public final Bitmap aligned;
        public final AlignmentGeometry geometry;

        Result(Bitmap aligned, AlignmentGeometry geometry) {
            this.aligned = aligned;
            this.geometry = geometry;
        }
    }

    private FaceAligner() {}

    public static Bitmap align(Bitmap source, Face face) {
        return alignWithGeometry(source, face).aligned;
    }

    public static Result alignWithGeometry(Bitmap source, Face face) {
        FaceLandmark le = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark re = face.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark nose = face.getLandmark(FaceLandmark.NOSE_BASE);
        FaceLandmark ml = face.getLandmark(FaceLandmark.MOUTH_LEFT);
        FaceLandmark mr = face.getLandmark(FaceLandmark.MOUTH_RIGHT);
        int landmarkCount = count(le, re, nose, ml, mr);

        if (landmarkCount == 5) {
            float[] src = {
                    le.getPosition().x, le.getPosition().y,
                    re.getPosition().x, re.getPosition().y,
                    nose.getPosition().x, nose.getPosition().y,
                    ml.getPosition().x, ml.getPosition().y,
                    mr.getPosition().x, mr.getPosition().y
            };
            try {
                float[] affine = SimilarityTransform.fit(src, ARC_FACE_5);
                AlignmentGeometry geometry = AlignmentGeometry.fromTransform(src, ARC_FACE_5, affine);
                Matrix matrix = new Matrix();
                matrix.setValues(new float[]{
                        affine[0], affine[1], affine[2],
                        affine[3], affine[4], affine[5],
                        0f, 0f, 1f
                });
                Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
                canvas.drawBitmap(source, matrix, paint);
                return new Result(out, geometry);
            } catch (IllegalArgumentException ignored) {
                // Degenerate landmark geometry is observable as fallback instead of a fake residual.
            }
        }
        return new Result(cropFallback(source, face.getBoundingBox()), AlignmentGeometry.fallback(landmarkCount));
    }

    private static int count(FaceLandmark... landmarks) {
        int n = 0;
        for (FaceLandmark landmark : landmarks) if (landmark != null) n++;
        return n;
    }

    private static Bitmap cropFallback(Bitmap source, Rect box) {
        float cx = box.exactCenterX();
        float cy = box.exactCenterY();
        float side = Math.max(box.width(), box.height()) * 1.35f;
        RectF wanted = new RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
        int left = Math.max(0, Math.round(wanted.left));
        int top = Math.max(0, Math.round(wanted.top));
        int right = Math.min(source.getWidth(), Math.round(wanted.right));
        int bottom = Math.min(source.getHeight(), Math.round(wanted.bottom));
        if (right <= left || bottom <= top) {
            return Bitmap.createScaledBitmap(source, SIZE, SIZE, true);
        }
        Bitmap crop = Bitmap.createBitmap(source, left, top, right - left, bottom - top);
        return Bitmap.createScaledBitmap(crop, SIZE, SIZE, true);
    }
}
