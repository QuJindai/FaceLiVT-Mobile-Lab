package com.qujindai.facelivtlab;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** App-private immutable learning history: compact metadata + five aligned thumbnails. */
public final class EnrollmentHistoryStore {
    private static final String ROOT = "enrollment_history";
    private static final String RECORD = "record.txt";
    private final File root;

    public EnrollmentHistoryStore(Context context) {
        this(context.getFilesDir());
    }

    /** Testable constructor: {@code filesDir/enrollment_history} becomes the private history root. */
    EnrollmentHistoryStore(File filesDir) {
        if (filesDir == null) throw new IllegalArgumentException("filesDir required");
        root = new File(filesDir, ROOT);
    }

    public synchronized int nextVersion(String identity) {
        List<Integer> versions = versions(identity);
        return versions.isEmpty() ? 1 : versions.get(versions.size() - 1) + 1;
    }

    public synchronized void saveVersion(EnrollmentHistoryRecord record, List<byte[]> fiveFrames) {
        if (record == null) throw new IllegalArgumentException("record required");
        if (fiveFrames == null || fiveFrames.size() != EnrollmentHistoryRecord.FRAME_COUNT) {
            throw new IllegalArgumentException("exactly five thumbnail payloads required");
        }
        for (byte[] bytes : fiveFrames) {
            if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("thumbnail cannot be empty");
        }

        File identityDir = identityDir(record.identity);
        File target = new File(identityDir, "v" + record.version);
        if (target.exists()) throw new IllegalStateException("history version already exists");
        if (!identityDir.exists() && !identityDir.mkdirs()) throw new IllegalStateException("cannot create identity history directory");
        File temp = new File(identityDir, ".v" + record.version + ".tmp-" + UUID.randomUUID());
        if (!temp.mkdirs()) throw new IllegalStateException("cannot create temporary history directory");

        try {
            write(new File(temp, RECORD), EnrollmentHistoryCodec.encode(record).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < fiveFrames.size(); i++) write(new File(temp, "s" + (i + 1) + ".webp"), fiveFrames.get(i));
            if (!temp.renameTo(target)) throw new IOException("cannot publish immutable history version");
        } catch (IOException | RuntimeException e) {
            deleteRecursive(temp);
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            throw new IllegalStateException("history persistence failed", e);
        }
    }

    public synchronized EnrollmentHistoryRecord latest(String identity) {
        List<Integer> list = versions(identity);
        return list.isEmpty() ? null : loadVersion(identity, list.get(list.size() - 1));
    }

    public synchronized List<Integer> versions(String identity) {
        File dir = identityDir(identity);
        File[] children = dir.listFiles();
        if (children == null) return new ArrayList<>();
        List<Integer> out = new ArrayList<>();
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().matches("v[1-9][0-9]*")) continue;
            try {
                int version = Integer.parseInt(child.getName().substring(1));
                EnrollmentHistoryRecord record = readRecord(child);
                if (record != null && safe(identity).equals(record.identity) && record.version == version) out.add(version);
            } catch (RuntimeException ignored) {
                // Corrupt/incomplete directories are never surfaced as valid history versions.
            }
        }
        Collections.sort(out);
        return out;
    }

    public synchronized EnrollmentHistoryRecord loadVersion(String identity, int version) {
        if (version <= 0) return null;
        File dir = new File(identityDir(identity), "v" + version);
        if (!dir.isDirectory()) return null;
        try {
            EnrollmentHistoryRecord record = readRecord(dir);
            return record != null && safe(identity).equals(record.identity) && record.version == version ? record : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public synchronized List<byte[]> loadFiveFrames(String identity, int version) {
        File dir = new File(identityDir(identity), "v" + version);
        if (!dir.isDirectory() || loadVersion(identity, version) == null) return new ArrayList<>();
        List<byte[]> out = new ArrayList<>();
        try {
            for (int i = 1; i <= EnrollmentHistoryRecord.FRAME_COUNT; i++) {
                File file = new File(dir, "s" + i + ".webp");
                if (!file.isFile()) return new ArrayList<>();
                out.add(read(file));
            }
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public synchronized boolean hasHistory(String identity) {
        return !versions(identity).isEmpty();
    }

    public synchronized void deleteVersion(String identity, int version) {
        if (version <= 0) return;
        deleteRecursive(new File(identityDir(identity), "v" + version));
        File dir = identityDir(identity);
        File[] remaining = dir.listFiles();
        if (remaining != null && remaining.length == 0) dir.delete();
    }

    public synchronized void deleteIdentity(String identity) {
        deleteRecursive(identityDir(identity));
    }

    private EnrollmentHistoryRecord readRecord(File versionDir) {
        File file = new File(versionDir, RECORD);
        if (!file.isFile()) return null;
        try {
            return EnrollmentHistoryCodec.decode(new String(read(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private File identityDir(String identity) {
        return new File(root, "i_" + sha256(safe(identity)));
    }

    private static String safe(String identity) {
        return identity == null ? "" : identity.trim();
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }

    private static byte[] read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }
}
