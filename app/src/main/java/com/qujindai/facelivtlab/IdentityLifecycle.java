package com.qujindai.facelivtlab;

/** Single destructive lifecycle boundary so delete/re-enroll cannot leave hidden identity remnants. */
public final class IdentityLifecycle {
    private final FaceStore faceStore;
    private final EnrollmentArchiveStore archiveStore;
    private final EnrollmentHistoryStore historyStore;

    public IdentityLifecycle(FaceStore faceStore, EnrollmentArchiveStore archiveStore,
                             EnrollmentHistoryStore historyStore) {
        if (faceStore == null || archiveStore == null || historyStore == null) {
            throw new IllegalArgumentException("all identity stores are required");
        }
        this.faceStore = faceStore;
        this.archiveStore = archiveStore;
        this.historyStore = historyStore;
    }

    public synchronized void deleteIdentity(String identity) {
        if (identity == null || identity.trim().isEmpty()) return;
        String id = identity.trim();
        // Active templates first prevents a concurrent matcher from seeing an identity whose history is already gone.
        faceStore.deleteIdentity(id);
        archiveStore.deleteIdentityData(id);
        historyStore.deleteIdentity(id);
    }
}
