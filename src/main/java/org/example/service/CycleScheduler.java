package org.example.service;

import org.example.entity.DispatchTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(CycleScheduler.class);

    private final DispatchService dispatchService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalTasksGenerated = new AtomicLong(0);
    private final AtomicLong lastRoundTasks = new AtomicLong(0);
    private volatile LocalDateTime simTime;
    private volatile Thread workerThread;
    private volatile String mode = DispatchService.MODE_OPTIMIZED;
    private volatile String sceneId = java.util.UUID.randomUUID().toString();

    public CycleScheduler(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public synchronized void start() {
        start(DispatchService.MODE_OPTIMIZED);
    }

    public synchronized void start(String mode) {
        String normalizedMode = DispatchService.normalizeMode(mode);
        if (running.get()) return;
        if (workerThread != null && workerThread.isAlive()) throw new IllegalStateException("上一轮调度仍在停止，请稍后重试");
        running.set(true);

        this.mode = normalizedMode;
        if (simTime == null) simTime = LocalDateTime.now().withNano(0);
        workerThread = new Thread(() -> {
            log.info("Cycle scheduler started, mode={}, simTime={}", this.mode, simTime);
            while (running.get()) {
                try {
                    List<DispatchTask> tasks = dispatchService.runCycleRound(simTime, this.mode);
                    lastRoundTasks.set(tasks.size());

                    if (tasks.isEmpty()) {
                        // 没有可用车辆 → 推进模拟时钟到下个最早可用时间
                        LocalDateTime nextTime = dispatchService.getEarliestAvailableAt(simTime);
                        if (nextTime != null && nextTime.isAfter(simTime)) {
                            simTime = nextTime;
                            log.info("Sim clock advanced to {}", simTime);
                        } else {
                            // 没有待处理的车辆，小步前进
                            simTime = simTime.plusSeconds(60);
                        }
                    } else {
                        totalTasksGenerated.addAndGet(tasks.size());
                        log.info("Round assigned {} tasks (total {}), simTime={}",
                                tasks.size(), totalTasksGenerated.get(), simTime);
                        // 任务分配后小步推进时间
                        simTime = simTime.plusSeconds(30);
                    }

                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Cycle round error", e);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            running.set(false);
            log.info("Cycle scheduler stopped, mode={}, total {} tasks", mode, totalTasksGenerated.get());
        }, "cycle-scheduler");

        workerThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public synchronized void stopAndAwait() {
        stopAndAwait(5000L);
    }

    public synchronized void stopAndAwait(long timeoutMs) {
        running.set(false);
        Thread thread = workerThread;
        if (thread == null) {
            return;
        }

        thread.interrupt();
        try {
            thread.join(Math.max(1L, timeoutMs));
            if (thread.isAlive()) throw new IllegalStateException("调度停止超时，不能重置或启动另一轮");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待调度停止被中断，不能继续重置", e);
        } finally {
            if (workerThread == thread && !thread.isAlive()) {
                workerThread = null;
            }
        }
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() { try { stopAndAwait(); } catch (IllegalStateException e) { log.warn("Scheduler shutdown timeout"); } }

    public CycleStatus getStatus() {
        CycleStatus s = new CycleStatus();
        s.setRunning(running.get());
        s.setTotalTasksGenerated(totalTasksGenerated.get());
        s.setLastRoundTasks(lastRoundTasks.get());
        s.setSimTime(simTime != null ? simTime.toString() : "");
        s.setMode(mode);
        return s;
    }

    public boolean isRunning() {
        return running.get();
    }

    public LocalDateTime currentTime() {
        return simTime != null ? simTime : LocalDateTime.now().withNano(0);
    }

    public synchronized void reset(Runnable mutation) {
        stopAndAwait();
        mutation.run();
        sceneId = java.util.UUID.randomUUID().toString();
        simTime = LocalDateTime.now().withNano(0);
        totalTasksGenerated.set(0);
        lastRoundTasks.set(0);
    }

    public String sceneId() { return sceneId; }
    public synchronized void restoreSession(String id, LocalDateTime time) {
        if (running.get() || workerThread != null) throw new IllegalStateException("不能覆盖运行中的仿真时钟");
        sceneId = id; simTime = time;
    }
    public synchronized void restoreCounters(String restoredMode,long total,long last) {
        if(running.get()||workerThread!=null)throw new IllegalStateException("不能覆盖运行中的调度统计");
        mode=DispatchService.normalizeMode(restoredMode);totalTasksGenerated.set(Math.max(0,total));lastRoundTasks.set(Math.max(0,last));
    }

    public static class CycleStatus {
        private boolean running;
        private long totalTasksGenerated;
        private long lastRoundTasks;
        private String simTime;
        private String mode;

        public boolean isRunning() { return running; }
        public void setRunning(boolean v) { this.running = v; }
        public long getTotalTasksGenerated() { return totalTasksGenerated; }
        public void setTotalTasksGenerated(long v) { this.totalTasksGenerated = v; }
        public long getLastRoundTasks() { return lastRoundTasks; }
        public void setLastRoundTasks(long v) { this.lastRoundTasks = v; }
        public String getSimTime() { return simTime; }
        public void setSimTime(String v) { this.simTime = v; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }
}
