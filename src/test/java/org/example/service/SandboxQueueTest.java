package org.example.service;

import org.example.sandbox.*;
import org.example.entity.LocationPoint;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxQueueTest {
    @Test void saturatedQueueRejectsExtraRunAndPersistsFailure() throws Exception {
        SandboxStore store = mock(SandboxStore.class);
        SandboxEngine engine = mock(SandboxEngine.class);
        Map<String,Map<String,Object>> saved = new ConcurrentHashMap<>();
        var config = ExperimentConfig.defaults(2);
        when(store.experiment("EXP-test")).thenReturn(Map.of("experimentId","EXP-test","environmentId","map","config",config));
        when(store.config(any())).thenReturn(config);
        when(store.environment("map")).thenReturn(List.of(new LocationPoint(),new LocationPoint()));
        doAnswer(call -> { Map<String,Object> run=call.getArgument(0); saved.put((String)run.get("runId"),new LinkedHashMap<>(run)); return null; }).when(store).insertRun(anyMap(),anyString(),anyString());
        when(store.run(anyString())).thenAnswer(call -> new LinkedHashMap<>(saved.get(call.getArgument(0))));
        when(store.updateRun(anyMap(),anyString())).thenAnswer(call -> { Map<String,Object> run=call.getArgument(0); saved.put((String)run.get("runId"),new LinkedHashMap<>(run)); return true; });
        CountDownLatch entered = new CountDownLatch(2), release = new CountDownLatch(1);
        when(engine.execute(any(),anyList(),any())).thenAnswer(call -> { entered.countDown(); release.await(); throw new IllegalStateException("test cancellation"); });
        SandboxService service = new SandboxService(store,engine);
        try {
            for(int i=0;i<2;i++) service.start("EXP-test",UUID.randomUUID().toString(),null);
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<8;i++) service.start("EXP-test",UUID.randomUUID().toString(),null);
            assertThrows(IllegalStateException.class, () -> service.start("EXP-test",UUID.randomUUID().toString(),null));
            assertEquals(11,saved.size());
            assertEquals(1,saved.values().stream().filter(r -> "FAILED".equals(r.get("status"))).count());
            assertTrue(saved.values().stream().filter(r -> "FAILED".equals(r.get("status"))).allMatch(r -> String.valueOf(r.get("error")).contains("队列已满")));
        } finally { service.shutdown(); release.countDown(); }
    }
}
