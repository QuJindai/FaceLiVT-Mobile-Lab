package com.qujindai.facelivtlab;

/** Least-squares 2D similarity transform: u=a*x-b*y+tx, v=b*x+a*y+ty. */
public final class SimilarityTransform {
    private SimilarityTransform() {}

    public static float[] fit(float[] src, float[] dst) {
        if (src == null || dst == null || src.length != dst.length || src.length < 4 || (src.length & 1) != 0) {
            throw new IllegalArgumentException("src/dst must contain matching 2D points");
        }
        int n = src.length / 2;
        double mx = 0, my = 0, mu = 0, mv = 0;
        for (int i = 0; i < src.length; i += 2) {
            mx += src[i]; my += src[i + 1];
            mu += dst[i]; mv += dst[i + 1];
        }
        mx /= n; my /= n; mu /= n; mv /= n;

        double denom = 0, aNum = 0, bNum = 0;
        for (int i = 0; i < src.length; i += 2) {
            double x = src[i] - mx, y = src[i + 1] - my;
            double u = dst[i] - mu, v = dst[i + 1] - mv;
            denom += x * x + y * y;
            aNum += x * u + y * v;
            bNum += x * v - y * u;
        }
        if (denom < 1e-9) throw new IllegalArgumentException("degenerate source points");
        double a = aNum / denom;
        double b = bNum / denom;
        double tx = mu - a * mx + b * my;
        double ty = mv - b * mx - a * my;
        return new float[]{(float) a, (float) -b, (float) tx, (float) b, (float) a, (float) ty};
    }
}
