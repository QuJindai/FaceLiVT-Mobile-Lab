package com.qujindai.facelivtlab;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.*;

public class EnrollmentHistoryStoreTest {
    private static EnrollmentHistoryRecord record(String id, int version) {
        List<EnrollmentHistoryRecord.FrameRecord> frames = new ArrayList<>();
        for (int i=0;i<5;i++) {
            FaceQuality.Snapshot q=FaceQuality.compose(.7f,.7f,.7f,.8f,1f,.9f,0,0,0,.1f);
            frames.add(new EnrollmentHistoryRecord.FrameRecord(q, AlignmentGeometry.fallback(4)));
        }
        EnumMap<ModelVariant,EnrollmentHistoryRecord.ModelRecord> models=new EnumMap<>(ModelVariant.class);
        for(ModelVariant v:ModelVariant.values()) {
            List<float[]> es=new ArrayList<>(); for(int i=0;i<5;i++) es.add(new float[]{1+i,.5f+i});
            models.put(v,new EnrollmentHistoryRecord.ModelRecord(es,new float[]{1,.5f},new float[]{.9f,.9f,.9f,.9f,.9f},.7f,.9f,.1f,.4f,.5f,.45f,.8f,.85f,0,.82f,true));
        }
        return new EnrollmentHistoryRecord(id,version,1000L+version,"480p",5,10,frames,models);
    }

    private static List<byte[]> thumbs(int seed) {
        List<byte[]> out=new ArrayList<>(); for(int i=0;i<5;i++) out.add(new byte[]{(byte)(seed+i),1,2,3}); return out;
    }

    @Test public void versionsAreImmutableAndLatestIsHighest() throws Exception {
        File root=Files.createTempDirectory("r5history").toFile();
        EnrollmentHistoryStore store=new EnrollmentHistoryStore(root);
        assertEquals(1,store.nextVersion("p/a"));
        store.saveVersion(record("p/a",1),thumbs(10));
        store.saveVersion(record("p/a",2),thumbs(20));
        assertEquals(2,store.latest("p/a").version);
        assertEquals(java.util.Arrays.asList(1,2),store.versions("p/a"));
        assertArrayEquals(new byte[]{10,1,2,3},store.loadFiveFrames("p/a",1).get(0));
        assertArrayEquals(new byte[]{20,1,2,3},store.loadFiveFrames("p/a",2).get(0));
        boolean threw=false; try { store.saveVersion(record("p/a",1),thumbs(99)); } catch(IllegalStateException expected){ threw=true; }
        assertTrue(threw);
        assertArrayEquals(new byte[]{10,1,2,3},store.loadFiveFrames("p/a",1).get(0));
    }

    @Test public void deleteIdentityRemovesMetadataAndFiveFrames() throws Exception {
        File root=Files.createTempDirectory("r5history-delete").toFile();
        EnrollmentHistoryStore store=new EnrollmentHistoryStore(root);
        store.saveVersion(record("张/三",1),thumbs(1));
        assertTrue(store.hasHistory("张/三"));
        store.deleteIdentity("张/三");
        assertFalse(store.hasHistory("张/三"));
        assertTrue(store.versions("张/三").isEmpty());
        assertNull(store.latest("张/三"));
        assertTrue(store.loadFiveFrames("张/三",1).isEmpty());
    }

    @Test public void rawIdentityIsNotUsedAsDirectoryName() throws Exception {
        File root=Files.createTempDirectory("r5history-safe").toFile();
        EnrollmentHistoryStore store=new EnrollmentHistoryStore(root);
        store.saveVersion(record("../unsafe/name",1),thumbs(1));
        File historyRoot=new File(root,"enrollment_history");
        assertTrue(historyRoot.isDirectory());
        for(File child: historyRoot.listFiles()) assertFalse(child.getName().contains("unsafe"));
    }
}
