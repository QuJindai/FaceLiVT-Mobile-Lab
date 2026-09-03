package com.qujindai.facelivtlab;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.*;

public class IdentityLifecycleTest {
    private static EnrollmentHistoryRecord record(String id) {
        List<EnrollmentHistoryRecord.FrameRecord> frames=new ArrayList<>();
        for(int i=0;i<5;i++) frames.add(new EnrollmentHistoryRecord.FrameRecord(
                FaceQuality.compose(.7f,.7f,.7f,.8f,1f,.9f,0,0,0,.1f), AlignmentGeometry.fallback(4)));
        EnumMap<ModelVariant,EnrollmentHistoryRecord.ModelRecord> models=new EnumMap<>(ModelVariant.class);
        for(ModelVariant v:ModelVariant.values()){
            List<float[]> es=new ArrayList<>(); for(int i=0;i<5;i++) es.add(new float[]{1+i,.5f+i});
            models.put(v,new EnrollmentHistoryRecord.ModelRecord(es,new float[]{1,.5f},new float[]{.9f,.9f,.9f,.9f,.9f},.7f,.9f,.1f,.4f,.5f,.45f,.8f,.85f,0,.82f,true));
        }
        return new EnrollmentHistoryRecord(id,1,1L,"480p",0,5,frames,models);
    }

    @Test public void deletingIdentityRemovesTemplatesLegacyArchiveReferencesAndHistory() throws Exception {
        String name="duplicate";
        InMemorySharedPreferences facePrefs=new InMemorySharedPreferences();
        InMemorySharedPreferences archivePrefs=new InMemorySharedPreferences();
        FaceStore faceStore=new FaceStore(facePrefs);
        EnrollmentArchiveStore archiveStore=new EnrollmentArchiveStore(archivePrefs);
        File root=Files.createTempDirectory("identity-lifecycle").toFile();
        EnrollmentHistoryStore historyStore=new EnrollmentHistoryStore(root);

        for(ModelVariant v:ModelVariant.values()) faceStore.replaceTemplate(name,v,new float[]{1,0},5);
        archiveStore.save(name,"old dossier");
        for(ModelVariant v:ModelVariant.values()) archiveStore.saveReference(name,v,
                new EnrollmentReferenceCodec.Record(java.util.Collections.singletonList(new float[]{1,0}),new float[]{.9f}));
        historyStore.saveVersion(record(name),java.util.Arrays.asList(new byte[]{1},new byte[]{2},new byte[]{3},new byte[]{4},new byte[]{5}));

        assertEquals(1,faceStore.identityCount());
        assertEquals(5,faceStore.sampleCount(name,ModelVariant.S));
        assertTrue(historyStore.hasHistory(name));

        new IdentityLifecycle(faceStore,archiveStore,historyStore).deleteIdentity(name);

        assertEquals(0,faceStore.identityCount());
        for(ModelVariant v:ModelVariant.values()){
            assertFalse(faceStore.hasTemplate(name,v));
            assertEquals(0,faceStore.sampleCount(name,v));
            assertNull(archiveStore.loadReference(name,v));
        }
        assertEquals("",archiveStore.load(name));
        assertFalse(historyStore.hasHistory(name));
    }

    @Test public void deletingSingleModelKeepsIdentityUntilLastTemplateIsGone() {
        InMemorySharedPreferences prefs=new InMemorySharedPreferences();
        FaceStore store=new FaceStore(prefs);
        store.replaceTemplate("p",ModelVariant.S,new float[]{1,0},5);
        store.replaceTemplate("p",ModelVariant.M,new float[]{1,0},5);
        store.deleteTemplate("p",ModelVariant.S);
        assertEquals(1,store.identityCount());
        assertFalse(store.hasTemplate("p",ModelVariant.S));
        assertTrue(store.hasTemplate("p",ModelVariant.M));
        store.deleteTemplate("p",ModelVariant.M);
        assertEquals(0,store.identityCount());
    }
}
