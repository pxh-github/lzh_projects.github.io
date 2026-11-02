package org.example.service;

import org.example.dto.DispatchCompareVO;
import org.example.dto.DispatchStatsVO;
import org.example.entity.DispatchTask;
import org.example.entity.OrderEntity;
import org.example.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final VehicleService vehicleService;
    private final Database database;
    private final java.util.concurrent.Semaphore comparisons = new java.util.concurrent.Semaphore(1);
    private final OrderService orderService;
    private final DispatchService dispatchService;

    public StatsService(Database database, VehicleService vehicleService,
                        OrderService orderService,
                        DispatchService dispatchService) {
        this.vehicleService = vehicleService;
        this.database = database;
        this.orderService = orderService;
        this.dispatchService = dispatchService;
    }

    public DispatchStatsVO overview() {
        try (java.sql.Connection conn = database.open()) {
            conn.setReadOnly(true); conn.setAutoCommit(false);
            DispatchStatsVO vo = overview(conn);
            conn.commit(); return vo;
        } catch (java.sql.SQLException e) { throw new IllegalStateException("统计读取失败", e); }
    }

    public DispatchStatsVO overview(java.sql.Connection conn) throws java.sql.SQLException {
            DispatchStatsVO vo = new DispatchStatsVO();
            try (java.sql.Statement statement = conn.createStatement()) {
                try (java.sql.ResultSet rs = statement.executeQuery("SELECT COUNT(*) c,COALESCE(SUM(status='IDLE'),0) idle,COALESCE(SUM(total_revenue),0) revenue,COALESCE(SUM(total_cost),0) cost,COALESCE(SUM(total_profit),0) profit,COALESCE(SUM(empty_mileage_km),0) empty_km,COALESCE(SUM(loaded_mileage_km),0) loaded_km FROM vehicle")) {
                    rs.next(); vo.setVehicleCount(rs.getInt("c")); vo.setIdleVehicleCount(rs.getInt("idle"));
                    vo.setTotalRevenue(rs.getBigDecimal("revenue")); vo.setTotalCost(rs.getBigDecimal("cost")); vo.setTotalProfit(rs.getBigDecimal("profit"));
                    vo.setTotalEmptyMileageKm(rs.getBigDecimal("empty_km")); vo.setTotalLoadedMileageKm(rs.getBigDecimal("loaded_km"));
                }
                try (java.sql.ResultSet rs = statement.executeQuery("SELECT COUNT(*) c,COALESCE(SUM(status='DONE'),0) done,COALESCE(SUM(pending_weight),0) pending FROM transport_order")) {
                    rs.next(); vo.setOrderCount(rs.getInt("c")); vo.setDoneOrderCount(rs.getInt("done")); vo.setTotalPendingWeight(rs.getBigDecimal("pending"));
                }
                try (java.sql.ResultSet rs = statement.executeQuery("""
                    SELECT COUNT(*) c,COALESCE(SUM(t.pickup_time_h+t.transport_time_h),0) hours,
                      COALESCE(SUM(t.cargo_wait_time_h),0) cargo_wait,
                      COALESCE(AVG(t.assigned_weight/NULLIF(v.max_load_ton,0))*100,0) utilization,
                      COALESCE(SUM(CASE WHEN t.status='DONE' THEN t.vehicle_wait_time_h*60 ELSE 0 END),0) vehicle_wait,
                      COALESCE(SUM(CASE WHEN t.status='DONE' THEN t.assigned_weight ELSE 0 END),0) tonnage,
                      COALESCE(SUM(CASE WHEN t.status='DONE' THEN (t.pickup_distance_km+t.transport_distance_km)*v.fuel_consumption_per_100km*0.0268 ELSE 0 END),0) carbon
                    FROM dispatch_task t LEFT JOIN vehicle v ON v.id=t.vehicle_id
                    """)) {
                    rs.next(); vo.setTaskCount(rs.getInt("c")); vo.setTotalTaskHours(rs.getBigDecimal("hours")); vo.setTotalCargoWaitHours(rs.getBigDecimal("cargo_wait"));
                    vo.setAvgLoadUtilization(rs.getBigDecimal("utilization")); vo.setTotalWaitTime(rs.getBigDecimal("vehicle_wait")); vo.setCompletedTonnage(rs.getBigDecimal("tonnage")); vo.setTotalCarbonEmission(rs.getBigDecimal("carbon"));
                }
            }
            BigDecimal totalKm = vo.getTotalEmptyMileageKm().add(vo.getTotalLoadedMileageKm());
            vo.setEmptyMileageRate(totalKm.signum() == 0 ? BigDecimal.ZERO : vo.getTotalEmptyMileageKm().multiply(new BigDecimal("100")).divide(totalKm,2,RoundingMode.HALF_UP));
            vo.setProfitPerHour(vo.getTotalTaskHours().signum() == 0 ? BigDecimal.ZERO : vo.getTotalProfit().divide(vo.getTotalTaskHours(),2,RoundingMode.HALF_UP));
            return vo;
    }

    /** Full-history SQL totals; the dashboard's bounded task list is only for recent activity. */
    public List<Map<String, Object>> vehicleTotals() {
        try (java.sql.Connection conn = database.open(); java.sql.Statement statement = conn.createStatement();
             java.sql.ResultSet rows = statement.executeQuery("""
                 SELECT vehicle_id,
                   COALESCE(SUM(pickup_distance_km),0) empty_km,
                   COALESCE(SUM(transport_distance_km),0) loaded_km,
                   COALESCE(SUM(CASE WHEN status='DONE' THEN vehicle_wait_time_h*60 ELSE 0 END),0) wait_minutes,
                   COUNT(DISTINCT CASE WHEN status='DONE' THEN order_id END) completed_orders,
                   COALESCE(SUM(CASE WHEN status='DONE' THEN assigned_weight ELSE 0 END),0) tonnage
                 FROM dispatch_task GROUP BY vehicle_id
                 """)) {
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            while (rows.next()) result.add(Map.of("vehicleId", rows.getLong("vehicle_id"),
                    "emptyDistance", rows.getBigDecimal("empty_km"), "loadedDistance", rows.getBigDecimal("loaded_km"),
                    "waitMinutes", rows.getBigDecimal("wait_minutes"), "completedOrders", rows.getLong("completed_orders"),
                    "completedTonnage", rows.getBigDecimal("tonnage")));
            return result;
        } catch (java.sql.SQLException e) { throw new IllegalStateException("车辆统计读取失败", e); }
    }

    public DispatchCompareVO compareCurrentModes() {
        if (!comparisons.tryAcquire()) throw new IllegalStateException("已有模式对比正在执行，请稍后重试");
        try { return compareCurrentModesInternal(); } finally { comparisons.release(); }
    }
    private DispatchCompareVO compareCurrentModesInternal() {
        List<Vehicle> vehicles = vehicleService.listVehicles();
        List<OrderEntity> orders = orderService.listOrders();
        LocalDateTime now = LocalDateTime.now();

        DispatchService.SimulationSnapshot baseline = dispatchService.simulateSnapshot(
                vehicles, orders, DispatchService.MODE_BASELINE, now
        );
        DispatchService.SimulationSnapshot optimized = dispatchService.simulateSnapshot(
                vehicles, orders, DispatchService.MODE_OPTIMIZED, now
        );

        DispatchStatsVO baselineStats = buildStats(baseline.getVehicles(), baseline.getOrders(), baseline.getTasks());
        DispatchStatsVO optimizedStats = buildStats(optimized.getVehicles(), optimized.getOrders(), optimized.getTasks());
        boolean guardApplied = false; // Report measured outcomes without replacing a worse optimized result.

        DispatchCompareVO vo = new DispatchCompareVO();
        vo.setScenarioMode("CURRENT");
        vo.setVehicleCount(vehicles.size());
        vo.setOrderCount(orders.size());
        vo.setComparedAt(now);
        vo.setBaseline(baselineStats);
        vo.setOptimized(optimizedStats);
        vo.setObjectiveMetric("PROFIT_PER_HOUR");
        vo.setGuardApplied(guardApplied);
        vo.setProfitDelta(scale(optimizedStats.getTotalProfit().subtract(baselineStats.getTotalProfit())));
        vo.setProfitDeltaRate(calcRateDelta(optimizedStats.getTotalProfit(), baselineStats.getTotalProfit()));
        vo.setHourlyProfitDelta(scale(optimizedStats.getProfitPerHour().subtract(baselineStats.getProfitPerHour())));
        vo.setHourlyProfitDeltaRate(calcRateDelta(optimizedStats.getProfitPerHour(), baselineStats.getProfitPerHour()));
        vo.setEmptyMileageRateDelta(scale(optimizedStats.getEmptyMileageRate().subtract(baselineStats.getEmptyMileageRate())));
        vo.setWaitHoursDelta(scale(optimizedStats.getTotalCargoWaitHours().subtract(baselineStats.getTotalCargoWaitHours())));
        vo.setDoneOrderDelta(optimizedStats.getDoneOrderCount() - baselineStats.getDoneOrderCount());
        return vo;
    }

    private DispatchStatsVO buildStats(List<Vehicle> vehicles, List<OrderEntity> orders, List<DispatchTask> tasks) {
        DispatchStatsVO vo = new DispatchStatsVO();
        vo.setVehicleCount(vehicles.size());
        vo.setOrderCount(orders.size());
        vo.setTaskCount(tasks.size());

        int idleCount = 0;
        int doneOrderCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalTaskHours = BigDecimal.ZERO;
        BigDecimal totalPendingWeight = BigDecimal.ZERO;
        BigDecimal totalEmptyMileageKm = BigDecimal.ZERO;
        BigDecimal totalLoadedMileageKm = BigDecimal.ZERO;
        BigDecimal totalCargoWaitHours = BigDecimal.ZERO;

        Map<Long, BigDecimal> vehicleCapacityMap = new HashMap<>();
        for (Vehicle vehicle : vehicles) {
            if ("IDLE".equals(vehicle.getStatus())) {
                idleCount++;
            }
            if (vehicle.getMaxLoadTon() != null) {
                vehicleCapacityMap.put(vehicle.getId(), vehicle.getMaxLoadTon());
            }
            totalRevenue = totalRevenue.add(zeroIfNull(vehicle.getTotalRevenue()));
            totalCost = totalCost.add(zeroIfNull(vehicle.getTotalCost()));
            totalProfit = totalProfit.add(zeroIfNull(vehicle.getTotalProfit()));
            totalEmptyMileageKm = totalEmptyMileageKm.add(zeroIfNull(vehicle.getEmptyMileageKm()));
            totalLoadedMileageKm = totalLoadedMileageKm.add(zeroIfNull(vehicle.getLoadedMileageKm()));
        }

        for (OrderEntity order : orders) {
            if ("DONE".equals(order.getStatus())) {
                doneOrderCount++;
            }
            totalPendingWeight = totalPendingWeight.add(zeroIfNull(order.getPendingWeight()));
        }

        BigDecimal totalUtilization = BigDecimal.ZERO;
        int utilizationCount = 0;
        for (DispatchTask task : tasks) {
            totalCargoWaitHours = totalCargoWaitHours.add(zeroIfNull(task.getCargoWaitTimeH()));
            totalTaskHours = totalTaskHours
                    .add(zeroIfNull(task.getPickupTimeH()))
                    .add(zeroIfNull(task.getTransportTimeH()));
            BigDecimal capacity = vehicleCapacityMap.get(task.getVehicleId());
            if (capacity != null && capacity.compareTo(BigDecimal.ZERO) > 0 && task.getAssignedWeight() != null) {
                totalUtilization = totalUtilization.add(
                        task.getAssignedWeight().divide(capacity, 4, RoundingMode.HALF_UP)
                );
                utilizationCount++;
            }
        }

        BigDecimal totalMileage = totalEmptyMileageKm.add(totalLoadedMileageKm);
        BigDecimal emptyMileageRate = BigDecimal.ZERO;
        if (totalMileage.compareTo(BigDecimal.ZERO) > 0) {
            emptyMileageRate = totalEmptyMileageKm
                    .divide(totalMileage, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal avgLoadUtilization = BigDecimal.ZERO;
        if (utilizationCount > 0) {
            avgLoadUtilization = totalUtilization
                    .divide(BigDecimal.valueOf(utilizationCount), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        BigDecimal profitPerHour = BigDecimal.ZERO;
        if (totalTaskHours.compareTo(BigDecimal.ZERO) > 0) {
            profitPerHour = totalProfit.divide(totalTaskHours, 4, RoundingMode.HALF_UP);
        }

        vo.setIdleVehicleCount(idleCount);
        vo.setDoneOrderCount(doneOrderCount);
        vo.setTotalRevenue(scale(totalRevenue));
        vo.setTotalCost(scale(totalCost));
        vo.setTotalProfit(scale(totalProfit));
        vo.setTotalTaskHours(scale(totalTaskHours));
        vo.setProfitPerHour(scale(profitPerHour));
        vo.setTotalPendingWeight(scale(totalPendingWeight));
        vo.setTotalEmptyMileageKm(scale(totalEmptyMileageKm));
        vo.setTotalLoadedMileageKm(scale(totalLoadedMileageKm));
        vo.setEmptyMileageRate(scale(emptyMileageRate));
        vo.setAvgLoadUtilization(scale(avgLoadUtilization));
        Map<String,BigDecimal> core = org.example.sandbox.SandboxEngine.metrics(tasks, vehicles, List.of());
        vo.setTotalWaitTime(core.get("totalWaitTime")); vo.setCompletedTonnage(core.get("completedTonnage")); vo.setTotalCarbonEmission(core.get("totalCarbonEmission"));
        vo.setTotalCargoWaitHours(scale(totalCargoWaitHours));

        return vo;
    }

    private BigDecimal calcRateDelta(BigDecimal current, BigDecimal baseline) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return current.subtract(baseline)
                .divide(baseline.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
