package com.qujindai.facelivtlab;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TemplateKeyTest {
    @Test public void modelSpacesUseDifferentPreferenceKeys() {
        String xs = TemplateKey.vector("Alice", ModelVariant.XS);
        String s = TemplateKey.vector("Alice", ModelVariant.S);
        String m = TemplateKey.vector("Alice", ModelVariant.M);
        assertNotEquals(xs, s);
        assertNotEquals(s, m);
        assertTrue(xs.endsWith("_XS_vec"));
        assertTrue(s.endsWith("_S_vec"));
        assertTrue(m.endsWith("_M_vec"));
    }

    @Test public void legacySKeyRemainsAddressableForMigration() {
        assertTrue(TemplateKey.legacyVector("Alice").endsWith("_vec"));
        assertNotEquals(TemplateKey.legacyVector("Alice"), TemplateKey.vector("Alice", ModelVariant.S));
    }
}
