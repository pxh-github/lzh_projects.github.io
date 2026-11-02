package org.example.sandbox;
import java.util.Map;
import org.example.service.DispatchService;
import org.example.service.ObjectiveWeights;
public record ExperimentConfig(String mapId, Integer poiCount, Integer simulationDuration, Integer vehicleCount,
        Integer orderGenerationInterval, Long randomSeed, String algorithm, Map<String, Boolean> events, Integer orderCount,
        ObjectiveWeights objectiveWeights) {
    public ExperimentConfig(String mapId, Integer poiCount, Integer simulationDuration, Integer vehicleCount,
            Integer orderGenerationInterval, Long randomSeed, String algorithm, Map<String, Boolean> events, Integer orderCount) {
        this(mapId,poiCount,simulationDuration,vehicleCount,orderGenerationInterval,randomSeed,algorithm,events,orderCount,null);
    }
    public ExperimentConfig(String mapId, Integer poiCount, Integer simulationDuration, Integer vehicleCount,
            Integer orderGenerationInterval, Long randomSeed, String algorithm, Map<String, Boolean> events) {
        this(mapId,poiCount,simulationDuration,vehicleCount,orderGenerationInterval,randomSeed,algorithm,events,null,null);
    }
    public static final Map<String, String> EVENTS = Map.of("TRAFFIC_JAM", "交通拥堵", "WEATHER", "天气影响", "VEHICLE_FAULT", "车辆故障", "CARGO_DAMAGE", "货损", "SHORTAGE", "缺货");
    public ExperimentConfig {
        if (vehicleCount == null || vehicleCount < 1 || vehicleCount > 500) throw new IllegalArgumentException("车辆数量须为1～500");
        if (orderGenerationInterval == null || orderGenerationInterval < 1 || orderGenerationInterval > 1440) throw new IllegalArgumentException("订单周期须为1～1440分钟");
        if (randomSeed == null || randomSeed < 0 || randomSeed > 4294967295L) throw new IllegalArgumentException("随机种子须为无符号32位整数");
        if (simulationDuration == null) simulationDuration = 0;
        if (simulationDuration != 0 && simulationDuration != 24) throw new IllegalArgumentException("新实验运行至全部订单完成；24仅用于历史复现");
        if (simulationDuration == 0 && orderCount == null) orderCount = 100;
        if (orderCount != null && (orderCount < 1 || orderCount > 1000)) throw new IllegalArgumentException("订单数量须为1～1000");
        algorithm = DispatchService.normalizeMode(algorithm);
        objectiveWeights = DispatchService.MODE_WEIGHTED.equals(algorithm)
                ? (objectiveWeights == null ? ObjectiveWeights.defaults() : objectiveWeights) : null;
        events = events == null ? Map.of() : Map.copyOf(events);
        if (!EVENTS.keySet().containsAll(events.keySet())) throw new IllegalArgumentException("不支持的事件类型");
        mapId = "现有 POI 地图";
    }
    public boolean completeAll() { return simulationDuration == 0; }
    public int demandCount() { return orderCount == null ? (1440 + orderGenerationInterval - 1) / orderGenerationInterval : orderCount; }
    public ExperimentConfig forCompletion() { return new ExperimentConfig(mapId,poiCount,0,vehicleCount,orderGenerationInterval,randomSeed,algorithm,events,demandCount(),objectiveWeights); }
    public static ExperimentConfig defaults(int pois) { return new ExperimentConfig("现有 POI 地图", pois, 0, 50, 10, 20260922L, "BASELINE", Map.of(),100); }
}
