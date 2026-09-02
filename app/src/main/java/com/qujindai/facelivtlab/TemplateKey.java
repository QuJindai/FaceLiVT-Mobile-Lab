package com.qujindai.facelivtlab;

public final class TemplateKey {
    private TemplateKey() {}

    private static String base(String name) {
        return "id_" + Integer.toHexString(name.hashCode());
    }

    public static String vector(String name, ModelVariant variant) {
        return base(name) + "_" + variant.storageKey + "_vec";
    }

    public static String count(String name, ModelVariant variant) {
        return base(name) + "_" + variant.storageKey + "_count";
    }

    public static String legacyVector(String name) {
        return base(name) + "_vec";
    }

    public static String legacyCount(String name) {
        return base(name) + "_count";
    }
}
