package com.qujindai.facelivtlab;

/**
 * Single source of truth for which model the recognition microscope is allowed to render.
 * The epoch invalidates camera work that started before a model/focus switch.
 */
public final class MicroscopeSelectionState {
    public static final class Snapshot {
        public final ModelMode mode;
        public final ModelVariant focus;
        public final long epoch;

        Snapshot(ModelMode mode, ModelVariant focus, long epoch) {
            this.mode = mode;
            this.focus = focus;
            this.epoch = epoch;
        }
    }

    private ModelMode mode = ModelMode.S;
    private ModelVariant focus = ModelVariant.S;
    private long epoch = 0L;

    public synchronized Snapshot selectMode(ModelMode requestedMode) {
        ModelMode nextMode = requestedMode == null ? ModelMode.S : requestedMode;
        ModelVariant nextFocus = focus;
        if (!contains(nextMode, nextFocus)) {
            ModelVariant[] variants = nextMode.variants();
            nextFocus = variants.length == 0 ? ModelVariant.S : variants[0];
        }
        mode = nextMode;
        focus = nextFocus;
        epoch++;
        return snapshotUnsafe();
    }

    public synchronized Snapshot selectFocus(ModelVariant requestedFocus) {
        if (requestedFocus != null && contains(mode, requestedFocus) && requestedFocus != focus) {
            focus = requestedFocus;
            epoch++;
        }
        return snapshotUnsafe();
    }

    public synchronized Snapshot snapshot() {
        return snapshotUnsafe();
    }

    public synchronized boolean isCurrent(Snapshot snapshot) {
        return snapshot != null && snapshot.epoch == epoch && snapshot.mode == mode && snapshot.focus == focus;
    }

    private Snapshot snapshotUnsafe() {
        return new Snapshot(mode, focus, epoch);
    }

    private static boolean contains(ModelMode mode, ModelVariant variant) {
        if (mode == null || variant == null) return false;
        for (ModelVariant candidate : mode.variants()) {
            if (candidate == variant) return true;
        }
        return false;
    }
}
