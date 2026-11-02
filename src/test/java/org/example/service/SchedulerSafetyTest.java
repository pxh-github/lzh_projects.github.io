package org.example.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerSafetyTest {
    @Test void concurrentStartsCreateOneWorkerAndStopTimeoutPreventsRestart() throws Exception {
        DispatchService dispatch = mock(DispatchService.class);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(dispatch.runCycleRound(any(), anyString())).thenAnswer(call -> {
            entered.countDown();
            // Simulate an in-flight JDBC operation that cannot immediately honor cancellation.
            boolean interrupted = false;
            while (true) try { release.await(); break; } catch (InterruptedException e) { interrupted = true; }
            if (interrupted) Thread.currentThread().interrupt();
            return List.of();
        });
        CycleScheduler scheduler = new CycleScheduler(dispatch);
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> starts = new ArrayList<>();
            for (int i=0;i<8;i++) starts.add(callers.submit(() -> scheduler.start("BASELINE")));
            for (Future<?> start : starts) start.get(2, TimeUnit.SECONDS);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            verify(dispatch, times(1)).runCycleRound(any(), eq("BASELINE"));
            assertThrows(IllegalStateException.class, () -> scheduler.stopAndAwait(20));
            assertThrows(IllegalStateException.class, () -> scheduler.start("BASELINE"));
        } finally {
            release.countDown(); scheduler.stopAndAwait(2000); callers.shutdownNow();
        }
        assertFalse(scheduler.isRunning());
    }

    @Test void invalidModeDoesNotStartWorkerAndCountersSupportLongValues() {
        CycleScheduler scheduler = new CycleScheduler(mock(DispatchService.class));
        assertThrows(IllegalArgumentException.class, () -> scheduler.start("UNKNOWN"));
        assertFalse(scheduler.isRunning());
        CycleScheduler.CycleStatus status = new CycleScheduler.CycleStatus();
        status.setTotalTasksGenerated((long)Integer.MAX_VALUE + 10);
        assertEquals(2147483657L, status.getTotalTasksGenerated());
    }
    @Test void restoredSceneKeepsCountersWhenResumed() throws Exception {
        DispatchService dispatch=mock(DispatchService.class);
        CountDownLatch entered=new CountDownLatch(1);
        when(dispatch.runCycleRound(any(),anyString())).thenAnswer(call->{entered.countDown();return List.of();});
        CycleScheduler scheduler=new CycleScheduler(dispatch);
        scheduler.restoreSession("saved-scene",java.time.LocalDateTime.of(2026,1,1,0,0));
        scheduler.restoreCounters("BASELINE",42,0);
        try {scheduler.start("BASELINE");assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(42,scheduler.getStatus().getTotalTasksGenerated());assertEquals("saved-scene",scheduler.sceneId());}
        finally{scheduler.stopAndAwait(2000);}
    }
}
