package com.qujindai.facelivtlab;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SessionLogger {
    private static final int MAX_ROWS = 10000;
    private static final String HEADER = "timestamp_ms,profile,model,source,simulated,assist,face_px,top1,similarity,accepted,fused_frames,detect_ms,infer_ms,total_ms,battery_c,thermal_status,thermal_label";
    private final List<String> rows = new ArrayList<>();

    public synchronized void add(long timestampMs, String profile, ModelVariant variant,
                                 int sourceW, int sourceH, int simW, int simH, int assistW, int assistH,
                                 int faceW, int faceH, String top1, float similarity, boolean accepted,
                                 int fusedFrames, long detectMs, long inferMs, long totalMs,
                                 ThermalProbe.Snapshot thermal) {
        if (rows.size() >= MAX_ROWS) rows.remove(0);
        String battery = thermal == null || Float.isNaN(thermal.batteryC)
                ? "" : String.format(Locale.US, "%.1f", thermal.batteryC);
        int thermalStatus = thermal == null ? -1 : thermal.thermalStatus;
        String thermalLabel = thermal == null ? "N/A" : thermal.thermalLabel;
        rows.add(timestampMs + "," +
                SessionCsv.escape(profile) + "," + variant.storageKey + "," +
                sourceW + "x" + sourceH + "," + simW + "x" + simH + "," + assistW + "x" + assistH + "," +
                faceW + "x" + faceH + "," + SessionCsv.escape(top1) + "," +
                String.format(Locale.US, "%.6f", similarity) + "," + accepted + "," + fusedFrames + "," +
                detectMs + "," + inferMs + "," + totalMs + "," + battery + "," + thermalStatus + "," + SessionCsv.escape(thermalLabel));
    }

    public synchronized int size() { return rows.size(); }

    public synchronized String toCsv() {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        for (String row : rows) out.append(row).append('\n');
        return out.toString();
    }

    public File exportCsv(Context context) throws Exception {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create export directory");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "facelivt-r2-session-" + stamp + ".csv");
        byte[] bytes = toCsv().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); }
        return file;
    }
}
