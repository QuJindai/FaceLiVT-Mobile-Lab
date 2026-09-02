package com.qujindai.facelivtlab;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

public final class ThermalProbe {
    public static final class Snapshot {
        public final float batteryC;
        public final int thermalStatus;
        public final String thermalLabel;

        Snapshot(float batteryC, int thermalStatus, String thermalLabel) {
            this.batteryC = batteryC;
            this.thermalStatus = thermalStatus;
            this.thermalLabel = thermalLabel;
        }
    }

    private ThermalProbe() {}

    public static Snapshot read(Context context) {
        float batteryC = Float.NaN;
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int raw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (raw != Integer.MIN_VALUE) batteryC = raw / 10f;
        }

        int status = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) status = pm.getCurrentThermalStatus();
        }
        return new Snapshot(batteryC, status, label(status));
    }

    public static String label(int status) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || status < 0) return "N/A";
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "UNKNOWN(" + status + ")";
        }
    }
}
