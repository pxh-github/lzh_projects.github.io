package org.example.sandbox;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SandboxService {
    private final SandboxStore store;
    private final SandboxEngine engine;
    private final String owner = UUID.randomUUID().toString();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), r -> {
        Thread thread = new Thread(r, "sandbox-worker"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy());
    private volatile boolean closing;
    public SandboxService(SandboxStore store, SandboxEngine engine) { this.store = store; this.engine = engine; }
    public Map<String,Object> state() { store.expireLeases(); return Map.of("experiments", store.experiments(), "runs", store.runs()); }
    public Map<String,Object> catalog() {
        return Map.of("algorithms", List.of(Map.of("id","BASELINE","name","Baseline · 基准调度"), Map.of("id","OPTIMIZED","name","Optimized · 收益优化"),
                    Map.of("id","WEIGHTED","name","Weighted · 加权多目标调度")),
                "objectiveDefaults", org.example.service.ObjectiveWeights.defaults(),
                "events", ExperimentConfig.EVENTS, "defaults", ExperimentConfig.defaults(0), "limits", Map.of("workers",2,"queuedRuns",8,"taskCount",10000,"executionSeconds",180));
    }
    public Map<String,Object> create(String name, ExperimentConfig config, String copyFrom) {
        if (store.experiments().size() >= 200) throw new IllegalStateException("当前最多支持200个实验方案");
        String environment = copyFrom == null ? null : (String)store.experiment(copyFrom).get("environmentId");
        return store.create(name, config, environment);
    }
    public Map<String,Object> update(String id, String name, ExperimentConfig config, long version) { return store.update(id,name,config,version); }
    public Map<String,Object> run(String id) { return store.run(id); }
    public Map<String,Object> start(String experimentId, String requestKey, String rerunId) {
        if (closing) throw new IllegalStateException("服务正在停止");
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9-]{8,64}")) throw new IllegalArgumentException("缺少有效请求标识");
        String prior = store.requestRun(requestKey);
        if (prior != null) {
            Map<String,Object> existing = store.run(prior);
            if (!experimentId.equals(existing.get("experimentId"))) throw new IllegalArgumentException("请求标识已被其他方案使用");
            return existing;
        }
        Map<String,Object> experiment = rerunId == null ? store.experiment(experimentId) : store.run(rerunId);
        if (!experimentId.equals(experiment.get("experimentId"))) throw new IllegalArgumentException("原Run不属于当前方案");
        ExperimentConfig runConfig=store.config(experiment.get("config"));
        if(rerunId != null && !runConfig.completeAll()) throw new IllegalStateException("历史Run使用24小时口径，请点击开始实验生成全部订单完成的新结果");
        if(rerunId == null) runConfig=runConfig.forCompletion();
        if (rerunId != null && !Objects.equals(engine.modelId(runConfig), experiment.get("modelId")))
            throw new IllegalStateException("原Run模型版本或计价规则已变化，无法按当前模型复现；请创建新的Run");
        Map<String,Object> run = new LinkedHashMap<>();
        run.put("runId", "RUN-" + UUID.randomUUID()); run.put("experimentId", experimentId);
        run.put("environmentId", experiment.get("environmentId")); run.put("config", runConfig);
        run.put("modelId", engine.modelId(runConfig));
        run.put("completionPolicy",runConfig.completeAll()?"ALL_ORDERS":"LEGACY_24H");
        run.put("orderCount",runConfig.demandCount());run.put("completedOrderCount",0);
        run.put("source", "BACKEND"); run.put("status", "QUEUED"); run.put("progress", 0); run.put("startedAt", System.currentTimeMillis());
        run.put("metrics", null); run.put("events", List.of()); run.put("simulationTime", "2026-01-01T00:00");
        if (rerunId != null) run.put("rerunOf", rerunId);
        try { store.insertRun(run, requestKey, owner); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            String existingId = store.requestRun(requestKey);
            if (existingId == null) throw e;
            Map<String,Object> existing = store.run(existingId);
            if (!experimentId.equals(existing.get("experimentId"))) throw new IllegalArgumentException("请求标识已被其他方案使用");
            return existing;
        }
        String id = (String)run.get("runId");
        try { executor.execute(() -> execute(id)); }
        catch (RejectedExecutionException e) {
            run.put("status", "FAILED"); run.put("error", "执行队列已满，请稍后创建新的Run"); store.updateRun(run, owner);
            throw new IllegalStateException("执行队列已满（2个执行槽，8个等待槽）");
        }
        return run;
    }
    private void execute(String id) {
        try { executeInternal(id); }
        catch (Exception e) { org.slf4j.LoggerFactory.getLogger(SandboxService.class).error("Run {} persistence failed; lease recovery will mark the interrupted run", id, e); }
    }
    private void executeInternal(String id) {
        Map<String,Object> run = store.run(id);
        run.put("status", "RUNNING"); if (!store.updateRun(run, owner)) return;
        long[] lastSaved = {0};
        try {
            if (!Objects.equals(run.get("modelId"), engine.modelId(store.config(run.get("config"))))) throw new IllegalStateException("执行模型与提交时不一致，请创建新的Run");
            SandboxEngine.Result result = engine.execute(store.config(run.get("config")), store.environment((String)run.get("environmentId")), progress -> {
                run.put("progress", progress.progress()); run.put("simulationTime", progress.simulationTime()); run.put("taskCount", progress.taskCount());
                run.put("completedOrderCount",progress.completedOrderCount());run.put("orderCount",progress.orderCount());
                if (System.nanoTime() - lastSaved[0] > TimeUnit.SECONDS.toNanos(1)) {
                    if (!store.updateRun(run, owner)) throw new IllegalStateException("运行租约失效"); lastSaved[0] = System.nanoTime();
                }
            });
            run.put("metrics", result.metrics()); run.put("events", result.events()); run.put("taskCount", result.taskCount());
            run.put("completedTaskCount", result.completedTaskCount()); run.put("pendingOrderCount", result.pendingOrderCount());
            run.put("orderCount",result.orderCount());run.put("completedOrderCount",result.completedOrderCount());
            run.put("simulationTime", result.simulationTime()); run.put("routeModel", result.routeModel()); run.put("carbonModel", result.carbonModel());
            run.put("optimization", result.optimization());
            run.put("status", "COMPLETED"); run.put("progress", 100); run.put("finishedAt", System.currentTimeMillis());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(SandboxService.class).warn("Experiment {} failed", id, e);
            run.put("status", "FAILED"); run.put("error", e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : "实验执行失败，请检查服务日志");
            run.put("finishedAt", System.currentTimeMillis());
        }
        store.updateRun(run, owner);
    }
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (closing) return;
        closing = true; executor.shutdownNow();
        store.shutdownOwner(owner);
        try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
