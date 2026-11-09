package org.example.sandbox;

import org.example.entity.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** 保存实验的初始状态和任务时序，地图回放无需读取或修改实时业务表。 */
public record SandboxPlayback(int version, LocalDateTime startTime, LocalDateTime endTime,
        List<ReplayVehicle> vehicles, List<ReplayOrder> orders, List<ReplayTask> tasks) {
    public record ReplayVehicle(long id, String code, String type, BigDecimal capacity, String initialLocation) {}
    public record ReplayOrder(long id, String code, String cargoType, BigDecimal weight, String pickup, String delivery,
            LocalDateTime createdAt, LocalDateTime completedAt) {}
    public record ReplayTask(long id, long vehicleId, long orderId, BigDecimal weight, String origin, String pickup,
            String delivery, LocalDateTime startedAt, LocalDateTime pickupAt, LocalDateTime finishedAt,
            BigDecimal emptyKm, BigDecimal loadedKm, BigDecimal vehicleWaitMinutes, BigDecimal cargoWaitTonMinutes,
            BigDecimal capacityKm, BigDecimal tonKm, BigDecimal completedTonnage, BigDecimal carbonKg,
            BigDecimal revenue, BigDecimal cost, BigDecimal profit) {}

    public static SandboxPlayback capture(LocalDateTime start, LocalDateTime end, List<Vehicle> initialFleet,
            List<OrderEntity> orders, List<DispatchTask> tasks, List<SandboxEngine.Event> events) {
        Map<Long,Vehicle> fleet = new HashMap<>();
        Map<Long,String> positions = new HashMap<>();
        List<ReplayVehicle> vehicles = initialFleet.stream().map(v -> {
            fleet.put(v.getId(),v); positions.put(v.getId(),v.getCurrentLocation());
            return new ReplayVehicle(v.getId(),v.getVehicleCode(),v.getVehicleType(),v.getMaxLoadTon(),v.getCurrentLocation());
        }).toList();
        Map<Long,OrderEntity> demand = new HashMap<>(); orders.forEach(o -> demand.put(o.getId(),o));
        Map<Long,LocalDateTime> finishes = new HashMap<>();
        List<ReplayTask> replayTasks = new ArrayList<>();
        // 按每辆车的开始时间排序，连续任务的起点取上一任务的送达点。
        List<DispatchTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparing(DispatchTask::getSimulationStartedAt).thenComparing(DispatchTask::getVehicleId));
        for (DispatchTask task : ordered) {
            Vehicle vehicle = fleet.get(task.getVehicleId()); OrderEntity order = demand.get(task.getOrderId());
            LocalDateTime began = task.getSimulationStartedAt(), finished = task.getSimulationFinishedAt();
            long pickupMinutes = task.getPickupTimeH().multiply(BigDecimal.valueOf(60)).setScale(0,RoundingMode.HALF_UP).longValueExact();
            LocalDateTime pickupAt = began.plusMinutes(Math.max(0,pickupMinutes));
            if (pickupAt.isAfter(finished)) pickupAt = finished;
            BigDecimal distance = task.getPickupDistanceKm().add(task.getTransportDistanceKm());
            BigDecimal effectiveWeight = SandboxEngine.damaged(task,events) ? task.getAssignedWeight().multiply(new BigDecimal("0.90")) : task.getAssignedWeight();
            replayTasks.add(new ReplayTask(replayTasks.size()+1L,vehicle.getId(),order.getId(),task.getAssignedWeight(),
                    positions.get(vehicle.getId()),order.getPickupLocation(),order.getDeliveryLocation(),began,pickupAt,finished,
                    task.getPickupDistanceKm(),task.getTransportDistanceKm(),task.getVehicleWaitTimeH().multiply(BigDecimal.valueOf(60)),
                    task.getCargoWaitTimeH().multiply(BigDecimal.valueOf(60)).multiply(task.getAssignedWeight()),
                    vehicle.getMaxLoadTon().multiply(distance),task.getAssignedWeight().multiply(task.getTransportDistanceKm()),
                    effectiveWeight,distance.multiply(vehicle.getFuelConsumptionPer100km()).multiply(new BigDecimal("0.0268")),
                    task.getTotalRevenue(),task.getTotalCost(),task.getNetProfit()));
            positions.put(vehicle.getId(),order.getDeliveryLocation());
            finishes.merge(order.getId(),finished,(a,b) -> a.isAfter(b) ? a : b);
        }
        List<ReplayOrder> replayOrders = orders.stream().map(o -> new ReplayOrder(o.getId(),o.getOrderCode(),o.getCargoType(),
                o.getCargoTotalWeight(),o.getPickupLocation(),o.getDeliveryLocation(),o.getCreatedAt(),finishes.get(o.getId()))).toList();
        return new SandboxPlayback(1,start,end,vehicles,replayOrders,List.copyOf(replayTasks));
    }
}
