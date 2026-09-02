package com.qujindai.facelivtlab;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

public final class FrameDegrader {
    private FrameDegrader() {}

    public static Bitmap apply(Bitmap source, DegradationProfile profile) {
        int shortSide = Math.min(source.getWidth(), source.getHeight());
        int targetShort = Math.min(shortSide, profile.targetShortSide);
        Bitmap resized = source;
        if (targetShort < shortSide) {
            float scale = targetShort / (float) shortSide;
            int targetWidth = Math.max(1, Math.round(source.getWidth() * scale));
            int targetHeight = Math.max(1, Math.round(source.getHeight() * scale));
            resized = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        }

        if (profile.jpegQuality >= 100) return resized;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        resized.compress(Bitmap.CompressFormat.JPEG, profile.jpegQuality, out);
        byte[] bytes = out.toByteArray();
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return decoded != null ? decoded : resized;
    }
}
