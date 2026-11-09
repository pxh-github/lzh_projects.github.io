package org.example.sandbox;

import org.example.entity.*;
import org.example.service.*;
import org.example.util.Geo;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** Runs the same dispatch planner as live scheduling on an isolated, seeded POI scene. */
@Service
public class SandboxEngine {
    private final DispatchService dispatch;
    private final VehicleService vehicles;
    public SandboxEngine(DispatchService dispatch, VehicleService vehicles) { this.dispatch = dispatch; this.vehicles = vehicles; }
    public String modelId() { return dispatch.modelId(); }
    public String modelId(ExperimentConfig config) { return config.completeAll() ? SandboxStore.hash(dispatch.modelId()+"|sandbox-all-orders-v3") : dispatch.modelId(); }
    public record Event(String eventId, String eventType, LocalDateTime startTime, int duration, int extraDelay,
            List<Long> affectedVehicleIds, List<Long> affectedOrderIds) {}
    public record Progress(int progress, String simulationTime, int taskCount, int completedOrderCount, int orderCount) {}
    public record Result(Map<String, BigDecimal> metrics, List<Event> events, int taskCount, int completedTaskCount,
            int pendingOrderCount, String simulationTime, String routeModel, String carbonModel, int orderCount, int completedOrderCount,
            Map<String, Object> optimization, SandboxPlayback playback) {}
    public Result execute(ExperimentConfig config, List<LocationPoint> locations, Consumer<Progress> progress) {
        if (locations.size() < 2) throw new IllegalArgumentException("实验至少需要两个有效POI");
        Random random = new Random(config.randomSeed());
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0), end = start.plusHours(24);
        List<Vehicle> fleet = new ArrayList<>();
        for (int i = 1; i <= config.vehicleCount(); i++) fleet.add(vehicles.buildSceneVehicle(i, locations.get(random.nextInt(locations.size())), random, start));
        List<OrderEntity> orders = new ArrayList<>();
        double[] weights = {1.2, 3.5, 5, 8, 12, 20, 30, 38};
        String[] cargo = {"钢材", "煤炭", "木材", "水泥", "粮食", "化肥", "砂石", "矿石"};
        for (int index = 0; index < (config.completeAll() ? config.demandCount() : (1440 + config.orderGenerationInterval() - 1) / config.orderGenerationInterval()); index++) {
            int minute = index * config.orderGenerationInterval();
            int pickupIndex = random.nextInt(locations.size()), destinationIndex = random.nextInt(locations.size() - 1);
            if (destinationIndex >= pickupIndex) destinationIndex++;
            LocationPoint pickup = locations.get(pickupIndex), destination = locations.get(destinationIndex);
            OrderEntity order = new OrderEntity();
            order.setId((long) orders.size() + 1); order.setOrderCode("SO" + order.getId()); order.setCargoType(cargo[random.nextInt(cargo.length)]);
            order.setCargoTotalWeight(BigDecimal.valueOf(weights[random.nextInt(weights.length)]));
            order.setAllocatedWeight(BigDecimal.ZERO); order.setPendingWeight(order.getCargoTotalWeight());
            order.setPickupLocationId(pickup.getId()); order.setPickupLocationName(pickup.getName()); order.setPickupLocation(pickup.getLocation());
            order.setDeliveryLocationId(destination.getId()); order.setDeliveryLocationName(destination.getName()); order.setDeliveryLocation(destination.getLocation());
            order.setRouteDistanceKm(BigDecimal.valueOf(Geo.roadEstimate(pickup.getLocation(), destination.getLocation())).setScale(2, RoundingMode.HALF_UP));
            order.setRouteDurationH(BigDecimal.ZERO); order.setRevenuePerTonKm(new BigDecimal("1.50")); order.setTotalRevenue(BigDecimal.ZERO);
            order.setCreatedAt(start.plusMinutes(minute)); order.setStatus("PENDING"); orders.add(order);
        }
        List<Event> events = events(config, start, orders);
        for (Event event : events) if ("SHORTAGE".equals(event.eventType())) {
            OrderEntity order = orders.get(event.affectedOrderIds().get(0).intValue() - 1);
            order.setCreatedAt(order.getCreatedAt().plusMinutes(event.duration()));
        }
        List<WeightedObjective.Decision> decisions = new ArrayList<>();
        int[] decisionCount = {0}, candidateCount = {0};
        DispatchService.SimulationSnapshot snapshot = dispatch.simulateSnapshot(fleet, orders, config.algorithm(), start, config.completeAll() ? null : end, new DispatchService.SimulationPolicy() {
            @Override public ObjectiveWeights objectiveWeights() { return config.objectiveWeights(); }
            @Override public void weightedDecision(WeightedObjective.Decision decision) {
                decisionCount[0]++; candidateCount[0] += decision.candidateCount();
                if (decisions.size() < 20) decisions.add(decision);
            }
            @Override public void progress(LocalDateTime time, int tasks) {
                progress.accept(new Progress((int) Math.min(99, Duration.between(start, time).toMinutes() * 100 / 1440), time.toString(), tasks,0,orders.size()));
            }
            @Override public void completionProgress(LocalDateTime time, int tasks, int completed, int total) {
                if(config.completeAll()) progress.accept(new Progress(Math.min(99,completed*100/Math.max(1,total)),time.toString(),tasks,completed,total));
                else progress(time,tasks);
            }
            @Override public void adjust(DispatchTask task, Vehicle vehicle, OrderEntity order, LocalDateTime departure) {
                for (Event event : events) {
                    if (!Set.of("TRAFFIC_JAM", "WEATHER", "VEHICLE_FAULT").contains(event.eventType())) continue;
                    if (!event.affectedVehicleIds().contains(vehicle.getId())) continue;
                    if (departure.isBefore(event.startTime()) || !departure.isBefore(event.startTime().plusMinutes(event.duration()))) continue;
                    BigDecimal delay = BigDecimal.valueOf(event.extraDelay()).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
                    task.setPickupTimeH(task.getPickupTimeH().add(delay)); task.setVehicleWaitTimeH(task.getVehicleWaitTimeH().add(delay));
                }
            }
        });
        Map<String, BigDecimal> metrics = new LinkedHashMap<>(metrics(snapshot.getTasks(), snapshot.getVehicles(), events));
        if(config.completeAll()) {
            metrics.put("totalCompletionTimeH", hours(start,snapshot.getSimTime()));
            Map<Long,LocalDateTime> finishes = new HashMap<>();
            for(DispatchTask task:snapshot.getTasks()) if("DONE".equals(task.getStatus())) finishes.merge(task.getOrderId(),task.getSimulationFinishedAt(),(a,b)->a.isAfter(b)?a:b);
            long elapsedMinutes = 0;
            for(OrderEntity order:snapshot.getOrders()) elapsedMinutes=Math.addExact(elapsedMinutes,Duration.between(order.getCreatedAt(),finishes.get(order.getId())).toMinutes());
            metrics.put("averageOrderCompletionTimeH", BigDecimal.valueOf(elapsedMinutes).divide(BigDecimal.valueOf(orders.size()*60L),2,RoundingMode.HALF_UP));
        }
        long pending = snapshot.getOrders().stream().filter(o -> !"DONE".equals(o.getStatus())).count();
        return new Result(metrics, events, snapshot.getTasks().size(), (int) snapshot.getTasks().stream().filter(t -> "DONE".equals(t.getStatus())).count(),
                (int) pending, snapshot.getSimTime().toString(), "球面距离×1.25；同一POI快照、相同计价与速度模型", "按车辆油耗×里程×2.68 kg CO₂/L计算，属于排放估算",orders.size(),orders.size()-(int)pending,
                DispatchService.MODE_WEIGHTED.equals(config.algorithm()) ? Map.of("method", "LINEAR_WEIGHTED_SUM",
                    "objectiveKeys", WeightedObjective.KEYS, "weights", config.objectiveWeights(),
                    "normalizedWeights", Arrays.stream(config.objectiveWeights().normalized()).boxed().toList(),
                    "normalization", "MIN_MAX_PER_DECISION", "decisionCount", decisionCount[0], "candidateCount", candidateCount[0],
                    "decisionSamples", decisions, "samplesTruncated", decisionCount[0] > decisions.size()) : Map.of(),
                SandboxPlayback.capture(start,snapshot.getSimTime(),fleet,snapshot.getOrders(),snapshot.getTasks(),events));
    }
    private static BigDecimal hours(LocalDateTime start, LocalDateTime finish) { return BigDecimal.valueOf(Duration.between(start,finish).toMinutes()).divide(BigDecimal.valueOf(60),2,RoundingMode.HALF_UP); }
    private List<Event> events(ExperimentConfig config, LocalDateTime start, List<OrderEntity> orders) {
        Random random = new Random(config.randomSeed() ^ 0x5deece66dL);
        List<Event> events = new ArrayList<>();
        List<Long> allVehicles=java.util.stream.LongStream.rangeClosed(1,config.vehicleCount()).boxed().toList();
        for (String type : new String[]{"TRAFFIC_JAM", "WEATHER", "VEHICLE_FAULT", "CARGO_DAMAGE", "SHORTAGE"}) {
            if (!Boolean.TRUE.equals(config.events().get(type))) continue;
            double probability = switch(type) { case "TRAFFIC_JAM" -> .10; case "WEATHER" -> .08; case "VEHICLE_FAULT" -> .03; default -> .05; };
            int eventHours=config.completeAll() ? Math.max(1,((config.demandCount()-1)*config.orderGenerationInterval())/60+1) : 24;
            for (int hour = 0; hour < eventHours; hour++) {
                if (random.nextDouble() >= probability) continue;
                long vehicle = 1 + random.nextInt(config.vehicleCount()), order = 1 + random.nextInt(orders.size());
                LocalDateTime at = start.plusHours(hour);
                if ("SHORTAGE".equals(type) || "CARGO_DAMAGE".equals(type)) at = orders.get((int) order - 1).getCreatedAt();
                List<Long> affected = "WEATHER".equals(type) ? allVehicles : List.of(vehicle);
                events.add(new Event("EVENT-" + (events.size() + 1), type, at, 10 + random.nextInt(21), 5 + random.nextInt(11), affected, List.of(order)));
            }
        }
        return events;
    }
    public static Map<String, BigDecimal> metrics(List<DispatchTask> tasks, List<Vehicle> vehicles, List<Event> events) {
        BigDecimal wait = BigDecimal.ZERO, empty = BigDecimal.ZERO, loaded = BigDecimal.ZERO, tonnage = BigDecimal.ZERO, carbon = BigDecimal.ZERO;
        BigDecimal cargoWait = BigDecimal.ZERO, tonKm = BigDecimal.ZERO, capacityKm = BigDecimal.ZERO;
        Map<Long, Vehicle> fleet = new HashMap<>(); vehicles.forEach(v -> fleet.put(v.getId(), v));
        for (DispatchTask task : tasks) {
            if (!"DONE".equals(task.getStatus())) continue;
            wait = wait.add(task.getVehicleWaitTimeH().multiply(BigDecimal.valueOf(60)));
            empty = empty.add(task.getPickupDistanceKm()); loaded = loaded.add(task.getTransportDistanceKm());
            tonnage = tonnage.add(task.getAssignedWeight().multiply(damaged(task,events) ? new BigDecimal("0.90") : BigDecimal.ONE));
            Vehicle v = fleet.get(task.getVehicleId());
            cargoWait = cargoWait.add(task.getCargoWaitTimeH().multiply(BigDecimal.valueOf(60)).multiply(task.getAssignedWeight()));
            tonKm = tonKm.add(task.getAssignedWeight().multiply(task.getTransportDistanceKm()));
            capacityKm = capacityKm.add(v.getMaxLoadTon().multiply(task.getPickupDistanceKm().add(task.getTransportDistanceKm())));
            carbon = carbon.add(task.getPickupDistanceKm().add(task.getTransportDistanceKm()).multiply(v.getFuelConsumptionPer100km()).multiply(new BigDecimal("0.0268")));
        }
        BigDecimal total = empty.add(loaded);
        BigDecimal utilization = total.signum() == 0 ? BigDecimal.ZERO : loaded.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal loadUtilization = capacityKm.signum() == 0 ? BigDecimal.ZERO : tonKm.multiply(BigDecimal.valueOf(100)).divide(capacityKm, 2, RoundingMode.HALF_UP);
        return Map.of("totalWaitTime", wait.setScale(2, RoundingMode.HALF_UP), "totalEmptyDistance", empty.setScale(2, RoundingMode.HALF_UP),
                "vehicleUtilization", utilization, "completedTonnage", tonnage.setScale(2, RoundingMode.HALF_UP), "totalCarbonEmission", carbon.setScale(2, RoundingMode.HALF_UP),
                "cargoWaitingTonMinutes", cargoWait.setScale(2, RoundingMode.HALF_UP), "capacityDistanceUtilization", loadUtilization);
    }
    static boolean damaged(DispatchTask task, List<Event> events) {
        return events.stream().anyMatch(e -> "CARGO_DAMAGE".equals(e.eventType()) && e.affectedOrderIds().contains(task.getOrderId())
                && !task.getSimulationFinishedAt().isBefore(e.startTime()) && task.getSimulationStartedAt().isBefore(e.startTime().plusMinutes(e.duration())));
    }
}
