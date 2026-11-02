package org.example.service;

import org.example.config.CostConfig;
import org.example.util.Geo;
import org.example.dao.DispatchTaskDao;
import org.example.dao.OrderDao;
import org.example.dao.VehicleDao;
import org.example.entity.DispatchTask;
import org.example.entity.Dsp;
import org.example.entity.OrderEntity;
import org.example.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DispatchService {

    public static final String MODE_BASELINE = "BASELINE";
    public static final String MODE_OPTIMIZED = "OPTIMIZED";
    public static final String MODE_WEIGHTED = "WEIGHTED";

    private final Dsp dsp;
    private final Database database;
    private final VehicleDao vehicleDao;
    private final OrderDao orderDao;
    private final DispatchTaskDao dispatchTaskDao;
    private final AmapRouteService amapRouteService;
    private final CostConfig costConfig;
    private final ThreadLocal<SimulationPolicy> policy = new ThreadLocal<>();
    public interface SimulationPolicy {
        default ObjectiveWeights objectiveWeights() { return ObjectiveWeights.defaults(); }
        default void weightedDecision(WeightedObjective.Decision decision) {}
        default void adjust(DispatchTask task, Vehicle vehicle, OrderEntity order, LocalDateTime departure) {}
        default void progress(LocalDateTime time, int taskCount) {}
        default void completionProgress(LocalDateTime time, int taskCount, int completedOrders, int totalOrders) { progress(time,taskCount); }
    }
    private void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("调度已取消");
    }

    public String modelId() {
        // Increment the version when route, vehicle, event or planner semantics change.
        String inputs = "dispatch-20261102-weighted-v1|" + costConfig.getRevenuePerTonKm() + "|" + costConfig.getCargoLossExpK()
                + "|" + costConfig.getCargoLossBaseCoeff() + "|" + costConfig.getLoadingCostPerTon()
                + "|" + costConfig.getWaitBonusPerHour() + "|" + costConfig.getWaitBonusCap() + "|" + costConfig.getPenaltyPerTonPerHour();
        return org.example.sandbox.SandboxStore.hash(inputs);
    }

    public DispatchService(Database database, Dsp dsp,
                           VehicleDao vehicleDao,
                           OrderDao orderDao,
                           DispatchTaskDao dispatchTaskDao,
                           AmapRouteService amapRouteService,
                           CostConfig costConfig) {
        this.dsp = dsp;
        this.database = database;
        this.vehicleDao = vehicleDao;
        this.orderDao = orderDao;
        this.dispatchTaskDao = dispatchTaskDao;
        this.amapRouteService = amapRouteService;
        this.costConfig = costConfig;
    }

    public List<DispatchTask> runCycleRound(LocalDateTime simTime) {
        return runCycleRound(simTime, MODE_OPTIMIZED);
    }

    public List<DispatchTask> runCycleRound(LocalDateTime simTime, String mode) {
        return database.write(() -> {
            String normalizedMode = normalizeMode(mode);
            try (Connection conn = database.open()) {

                dispatchTaskDao.completeAvailable(conn, simTime);
                orderDao.repairInconsistent(conn);
                vehicleDao.releaseAvailableVehicles(conn, simTime);

                List<Vehicle> allVehicles = vehicleDao.findAll(conn);
                List<OrderEntity> pendingOrders = orderDao.findPendingOrders(conn);
                if (allVehicles.isEmpty() || pendingOrders.isEmpty()) {

                    return List.of();
                }

                List<Vehicle> idleVehicles = new ArrayList<>();
                for (Vehicle vehicle : allVehicles) {
                    if (!"IDLE".equals(vehicle.getStatus())) {
                        continue;
                    }
                    LocalDateTime availableAt = vehicle.getAvailableAt();
                    if (availableAt == null || !availableAt.isAfter(simTime)) {
                        idleVehicles.add(vehicle);
                    }
                }
                idleVehicles.sort(Comparator.comparing(v -> v.getAvailableAt() != null ? v.getAvailableAt() : simTime));

                List<DispatchTask> allTasks = new ArrayList<>();
                for (Vehicle vehicle : idleVehicles) {
                    checkCancelled();
                    List<DispatchTask> tasks = MODE_BASELINE.equals(normalizedMode)
                            ? runBaselineForVehicle(conn, vehicle, pendingOrders, simTime)
                            : MODE_WEIGHTED.equals(normalizedMode)
                                ? executeTasks(conn, vehicle, buildWeightedTasksForVehicle(vehicle, pendingOrders, simTime), pendingOrders, simTime)
                                : runOptimizedForVehicle(conn, vehicle, pendingOrders, simTime);
                    allTasks.addAll(tasks);
                }

                return allTasks;
            } catch (Exception e) {
                throw new RuntimeException("dispatch round failed, mode=" + normalizedMode, e);
            }
        });
    }

    public LocalDateTime getEarliestAvailableAt(LocalDateTime givenTime) {
        try (Connection conn = database.open()) {
            List<Vehicle> allVehicles = vehicleDao.findAll(conn);
            LocalDateTime earliest = null;
            for (Vehicle vehicle : allVehicles) {
                LocalDateTime availableAt = vehicle.getAvailableAt();
                if (availableAt == null || !availableAt.isAfter(givenTime)) {
                    continue;
                }
                if (earliest == null || availableAt.isBefore(earliest)) {
                    earliest = availableAt;
                }
            }
            return earliest;
        } catch (Exception e) {
            return null;
        }
    }

    public List<DispatchTask> runBatchRound() {
        return runBatchRound(MODE_OPTIMIZED);
    }

    public List<DispatchTask> runBatchRound(String mode) {
        return runCycleRound(LocalDateTime.now(), mode);
    }

    public List<DispatchTask> listTasks() { return listTasks(-1, 1000, true); }
    public List<DispatchTask> listTasks(long afterId, int limit, boolean includeRoute) {
        if (limit < 1 || limit > 1000 || afterId < -1) throw new IllegalArgumentException("任务分页参数无效");
        try (Connection conn = database.open()) {
            return dispatchTaskDao.findAll(conn, afterId, limit, includeRoute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SimulationSnapshot simulateSnapshot(List<Vehicle> vehicles, List<OrderEntity> orders, String mode, LocalDateTime simTime) {
        return simulateSnapshot(vehicles, orders, mode, simTime, simTime.plusDays(30), new SimulationPolicy() {});
    }

    public SimulationSnapshot simulateSnapshot(List<Vehicle> vehicles, List<OrderEntity> orders, String mode,
            LocalDateTime simTime, LocalDateTime endTime, SimulationPolicy observer) {
        String normalizedMode = normalizeMode(mode);
        boolean finishAll = endTime == null;
        if (vehicles.size() > 500 || orders.size() > 10000) throw new IllegalArgumentException("场景超过执行容量");
        List<Vehicle> simVehicles = cloneVehicles(vehicles);
        List<OrderEntity> simOrders = cloneOrders(orders);
        List<DispatchTask> allTasks = new ArrayList<>();
        LocalDateTime currentTime = simTime;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(3);
        policy.set(observer);
        try {
            for (int safety = 0; safety < 20000; safety++) {
                checkCancelled();
                if (System.nanoTime() > deadline) throw new IllegalStateException("实验执行超过三分钟限额，请减小场景规模");
                completeInMemory(allTasks, simOrders, currentTime);
                releaseAvailableVehiclesInMemory(simVehicles, currentTime);
                int completedOrders = (int)simOrders.stream().filter(o -> "DONE".equals(o.getStatus())).count();
                observer.completionProgress(currentTime, allTasks.size(),completedOrders,simOrders.size());
                if (finishAll ? completedOrders == simOrders.size() : !currentTime.isBefore(endTime)) break;
                List<Vehicle> idle = collectReadyVehicles(simVehicles, currentTime);
                int generated = 0;
                for (Vehicle vehicle : idle) {
                    checkCancelled();
                    List<DispatchTask> tasks = MODE_BASELINE.equals(normalizedMode)
                            ? simulateBaselineForVehicle(vehicle, simOrders, currentTime)
                            : MODE_WEIGHTED.equals(normalizedMode)
                                ? applyTasksInMemory(vehicle, buildWeightedTasksForVehicle(vehicle, simOrders, currentTime), simOrders, currentTime)
                                : simulateOptimizedForVehicle(vehicle, simOrders, currentTime);
                    allTasks.addAll(tasks); generated += tasks.size();
                    if (allTasks.size() > 10000) throw new IllegalStateException("任务数量超过执行容量，请减小场景规模");
                }
                LocalDateTime next = earliestAvailableAt(simVehicles, currentTime);
                for (OrderEntity order : simOrders) {
                    if (isPending(order) && order.getCreatedAt() != null && order.getCreatedAt().isAfter(currentTime)
                            && (next == null || order.getCreatedAt().isBefore(next))) next = order.getCreatedAt();
                }
                if (next == null) {
                    if(finishAll) throw new IllegalStateException("仍有未完成订单，但没有可推进的车辆或订单事件");
                    currentTime = endTime; completeInMemory(allTasks, simOrders, currentTime); break;
                }
                currentTime = finishAll || !next.isAfter(endTime) ? next : endTime;
            }
            if (finishAll ? simOrders.stream().anyMatch(o -> !"DONE".equals(o.getStatus())) : currentTime.isBefore(endTime)) throw new IllegalStateException("实验超过仿真步数限额，结果未保存为完成");
            completeInMemory(allTasks, simOrders, currentTime);
            releaseAvailableVehiclesInMemory(simVehicles, currentTime);
            observer.completionProgress(currentTime, allTasks.size(),(int)simOrders.stream().filter(o -> "DONE".equals(o.getStatus())).count(),simOrders.size());
            return new SimulationSnapshot(normalizedMode, currentTime, simVehicles, simOrders, allTasks);
        } finally { policy.remove(); }
    }

    private void completeInMemory(List<DispatchTask> tasks, List<OrderEntity> orders, LocalDateTime time) {
        java.util.Set<Long> active = new java.util.HashSet<>();
        for (DispatchTask task : tasks) {
            if ("RUNNING".equals(task.getStatus()) && !task.getSimulationFinishedAt().isAfter(time)) task.setStatus("DONE");
            if ("RUNNING".equals(task.getStatus())) active.add(task.getOrderId());
        }
        for (OrderEntity order : orders) {
            if (zeroIfNull(order.getPendingWeight()).signum() == 0 && !active.contains(order.getId())) order.setStatus("DONE");
        }
    }

    private List<DispatchTask> runBaselineForVehicle(Connection conn,
                                                     Vehicle vehicle,
                                                     List<OrderEntity> pendingOrders,
                                                     LocalDateTime simTime) throws Exception {
        return executeTasks(conn, vehicle, buildBaselineTasksForVehicle(vehicle, pendingOrders, simTime), pendingOrders, simTime);
    }

    private List<DispatchTask> simulateBaselineForVehicle(Vehicle vehicle,
                                                          List<OrderEntity> pendingOrders,
                                                          LocalDateTime simTime) {
        return applyTasksInMemory(vehicle, buildBaselineTasksForVehicle(vehicle, pendingOrders, simTime), pendingOrders, simTime);
    }

    private List<DispatchTask> runOptimizedForVehicle(Connection conn,
                                                      Vehicle vehicle,
                                                      List<OrderEntity> pendingOrders,
                                                      LocalDateTime simTime) throws Exception {
        List<DispatchTask> tasks = selectPreferredTasks(
                buildBaselineTasksForVehicle(vehicle, pendingOrders, simTime),
                buildOptimizedTasksForVehicle(vehicle, pendingOrders, simTime)
        );
        return executeTasks(conn, vehicle, tasks, pendingOrders, simTime);
    }

    private List<DispatchTask> simulateOptimizedForVehicle(Vehicle vehicle,
                                                           List<OrderEntity> pendingOrders,
                                                           LocalDateTime simTime) {
        return applyTasksInMemory(
                vehicle,
                selectPreferredTasks(
                        buildBaselineTasksForVehicle(vehicle, pendingOrders, simTime),
                        buildOptimizedTasksForVehicle(vehicle, pendingOrders, simTime)
                ),
                pendingOrders,
                simTime
        );
    }

    private List<DispatchTask> buildBaselineTasksForVehicle(Vehicle vehicle,
                                                            List<OrderEntity> pendingOrders,
                                                            LocalDateTime simTime) {
        OrderEntity bestOrder = null;
        for (OrderEntity order : pendingOrders) {
            if (!isPending(order) || (order.getCreatedAt() != null && order.getCreatedAt().isAfter(simTime))) {
                continue;
            }
            if (bestOrder == null
                    || compareOrderPriority(order, bestOrder) < 0) {
                bestOrder = order;
            }
        }
        if (bestOrder == null) {
            return List.of();
        }

        BigDecimal assignedWeight = minPositive(vehicle.getMaxLoadTon(), bestOrder.getPendingWeight());
        if (assignedWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        return List.of(buildSequentialTask(
                vehicle, bestOrder, vehicle.getCurrentLocation(), simTime, assignedWeight, simTime, false
        ));
    }

    private List<DispatchTask> buildOptimizedTasksForVehicle(Vehicle vehicle,
                                                             List<OrderEntity> pendingOrders,
                                                             LocalDateTime simTime) {
        BigDecimal remainingCap = vehicle.getMaxLoadTon();
        List<OrderEntity> candidates = new ArrayList<>();
        for (OrderEntity order : pendingOrders) {
            if (isPending(order) && (order.getCreatedAt() == null || !order.getCreatedAt().isAfter(simTime))) {
                candidates.add(order);
            }
        }
        // Score each candidate once; comparators must not perform network calls or allocate routes.
        Map<Long, Double> ranking = new HashMap<>();
        for (OrderEntity order : candidates) {
            checkCancelled(); ranking.put(order.getId(), evaluateSingleOrderProfitPerHour(vehicle, order, simTime, true));
        }
        candidates.sort(Comparator.comparingDouble((OrderEntity order) -> ranking.get(order.getId())).reversed().thenComparing(OrderEntity::getId));
        if (candidates.size() > 32) candidates = new ArrayList<>(candidates.subList(0, 32));

        List<OrderEntity> planOrders = new ArrayList<>();
        Map<Long, BigDecimal> assignedWeightMap = new HashMap<>();
        PlanScore currentScore = PlanScore.empty();

        for (OrderEntity candidate : candidates) {
            checkCancelled();
            if (planOrders.size() >= 8) break;
            BigDecimal assignedWeight = minPositive(remainingCap, candidate.getPendingWeight());
            if (assignedWeight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            int bestPos = -1;
            PlanScore bestScore = currentScore;
            Map<Long, BigDecimal> bestMap = null;

            for (int pos = 0; pos <= planOrders.size(); pos++) {
                List<OrderEntity> trialPlan = new ArrayList<>(planOrders);
                trialPlan.add(pos, candidate);
                Map<Long, BigDecimal> trialMap = new HashMap<>(assignedWeightMap);
                trialMap.put(candidate.getId(), assignedWeight);
                PlanScore trialScore = calcSequentialPlanScore(vehicle, trialPlan, vehicle.getCurrentLocation(), simTime, trialMap);
                if (trialScore.isBetterThan(bestScore)) {
                    bestScore = trialScore;
                    bestPos = pos;
                    bestMap = trialMap;
                }
            }

            if (bestPos >= 0) {
                planOrders.add(bestPos, candidate);
                assignedWeightMap = bestMap;
                currentScore = bestScore;
                remainingCap = remainingCap.subtract(assignedWeight);
                if (remainingCap.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }
        }

        if (planOrders.isEmpty()) {
            return List.of();
        }

        List<DispatchTask> result = buildTasksFromPlan(vehicle, planOrders, simTime, assignedWeightMap, true);
        return result;
    }

    private record WeightedPlan(List<OrderEntity> orders, Map<Long, BigDecimal> assignments,
            List<DispatchTask> tasks, WeightedObjective.Values values) {}

    /** Bounded weighted insertion search. Every ready order is considered as a single-order plan. */
    private List<DispatchTask> buildWeightedTasksForVehicle(Vehicle vehicle, List<OrderEntity> pendingOrders, LocalDateTime time) {
        List<OrderEntity> ready = pendingOrders.stream()
                .filter(o -> isPending(o) && (o.getCreatedAt() == null || !o.getCreatedAt().isAfter(time)))
                .sorted(this::compareOrderPriority).toList();
        if (ready.isEmpty() || vehicle.getMaxLoadTon().signum() <= 0) return List.of();
        ObjectiveWeights weights = policy.get() == null ? ObjectiveWeights.defaults() : policy.get().objectiveWeights();
        List<WeightedPlan> singles = new ArrayList<>();
        for (OrderEntity order : ready) {
            checkCancelled();
            BigDecimal amount = minPositive(vehicle.getMaxLoadTon(), order.getPendingWeight());
            singles.add(weightedPlan(vehicle, ready, List.of(order), Map.of(order.getId(), amount), time));
        }
        var singleScale = new WeightedObjective.Scale(singles.stream().map(WeightedPlan::values).toList());
        singles.sort(Comparator.comparingDouble((WeightedPlan p) -> singleScale.evaluate(p.values(), weights).score())
                .thenComparing((a, b) -> compareOrderPriority(a.orders().get(0), b.orders().get(0))));
        List<WeightedPlan> alternatives = new ArrayList<>(singles);
        // Include the existing heuristic plan, but never let its profit objective override weighted selection.
        List<DispatchTask> heuristic = buildOptimizedTasksForVehicle(vehicle, pendingOrders, time);
        if (!heuristic.isEmpty()) {
            List<OrderEntity> planOrders = heuristic.stream().map(t -> findOrderById(ready, t.getOrderId())).toList();
            Map<Long, BigDecimal> amounts = new HashMap<>();
            heuristic.forEach(t -> amounts.put(t.getOrderId(), t.getAssignedWeight()));
            alternatives.add(new WeightedPlan(planOrders, amounts, heuristic, weightedValues(vehicle, ready, heuristic, time)));
        }
        WeightedPlan current = singles.get(0);
        for (WeightedPlan single : singles.subList(0, Math.min(32, singles.size()))) {
            checkCancelled();
            if (current.orders().size() >= 8) break;
            OrderEntity candidate = single.orders().get(0);
            if (current.assignments().containsKey(candidate.getId())) continue;
            BigDecimal used = current.assignments().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = minPositive(vehicle.getMaxLoadTon().subtract(used), candidate.getPendingWeight());
            if (amount.signum() <= 0) break;
            List<WeightedPlan> trials = new ArrayList<>(); trials.add(current);
            for (int position = 0; position <= current.orders().size(); position++) {
                List<OrderEntity> sequence = new ArrayList<>(current.orders()); sequence.add(position, candidate);
                Map<Long, BigDecimal> amounts = new HashMap<>(current.assignments()); amounts.put(candidate.getId(), amount);
                WeightedPlan trial = weightedPlan(vehicle, ready, sequence, amounts, time);
                trials.add(trial); alternatives.add(trial);
            }
            var scale = new WeightedObjective.Scale(trials.stream().map(WeightedPlan::values).toList());
            for (WeightedPlan trial : trials) {
                if (scale.evaluate(trial.values(), weights).score() < scale.evaluate(current.values(), weights).score() - 1e-10)
                    current = trial;
            }
        }
        var scale = new WeightedObjective.Scale(alternatives.stream().map(WeightedPlan::values).toList());
        // Stable earliest-arrival tie break, independent of input list order and task identifiers.
        alternatives.sort((a, b) -> {
            int order = compareOrderPriority(a.orders().get(0), b.orders().get(0));
            if (order != 0) return order;
            return Integer.compare(a.orders().size(), b.orders().size());
        });
        WeightedPlan best = alternatives.get(0);
        for (WeightedPlan candidate : alternatives) {
            if (scale.evaluate(candidate.values(), weights).score() < scale.evaluate(best.values(), weights).score() - 1e-10)
                best = candidate;
        }
        best.tasks().forEach(task -> task.setTaskCode("WT"));
        if (policy.get() != null) policy.get().weightedDecision(new WeightedObjective.Decision(vehicle.getId(), time.toString(),
                best.orders().stream().map(OrderEntity::getId).toList(), alternatives.size(), best.values(),
                scale.evaluate(best.values(), weights), scale.minima(), scale.maxima()));
        return best.tasks();
    }

    private WeightedPlan weightedPlan(Vehicle vehicle, List<OrderEntity> ready, List<OrderEntity> sequence,
            Map<Long, BigDecimal> amounts, LocalDateTime time) {
        List<DispatchTask> tasks = buildTasksFromPlan(vehicle, sequence, time, amounts, true);
        return new WeightedPlan(List.copyOf(sequence), Map.copyOf(amounts), tasks, weightedValues(vehicle, ready, tasks, time));
    }

    private WeightedObjective.Values weightedValues(Vehicle vehicle, List<OrderEntity> ready, List<DispatchTask> tasks, LocalDateTime time) {
        double demand = ready.stream().mapToDouble(o -> o.getPendingWeight().doubleValue()).sum();
        double assigned = 0, waiting = 0, empty = 0, loaded = 0, tonKm = 0;
        long elapsed = 0;
        for (DispatchTask task : tasks) {
            double tons = task.getAssignedWeight().doubleValue();
            // Queue area from now to each pickup, plus the unserved queue over the plan horizon.
            waiting += tons * (elapsed + task.getPickupTimeH().doubleValue() * 60);
            assigned += tons; empty += task.getPickupDistanceKm().doubleValue();
            loaded += task.getTransportDistanceKm().doubleValue();
            tonKm += tons * task.getTransportDistanceKm().doubleValue();
            elapsed += taskDurationMinutes(task);
        }
        waiting += Math.max(0, demand - assigned) * elapsed;
        double distance = empty + loaded;
        double utilization = distance <= 0 ? 0
                : Math.min(1, tonKm / (vehicle.getMaxLoadTon().doubleValue() * distance));
        double carbon = distance * vehicle.getFuelConsumptionPer100km().doubleValue() * .0268;
        return new WeightedObjective.Values(waiting, empty, utilization, assigned, carbon);
    }

    private List<DispatchTask> executeTasks(Connection conn,
                                            Vehicle vehicle,
                                            List<DispatchTask> tasks,
                                            List<OrderEntity> pendingOrders,
                                            LocalDateTime simTime) throws Exception {
        if (tasks.isEmpty()) {
            return List.of();
        }

        LocalDateTime finishAt = simTime;
        DispatchTask lastTask = null;
        OrderEntity lastOrder = null;
        List<DispatchTask> persistedTasks = new ArrayList<>();

        for (DispatchTask task : tasks) {
            OrderEntity order = findOrderById(pendingOrders, task.getOrderId());
            if (order == null || !isPending(order)) {
                continue;
            }

            BigDecimal allocWeight = minPositive(task.getAssignedWeight(), order.getPendingWeight());
            if (allocWeight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (allocWeight.compareTo(task.getAssignedWeight()) != 0) {
                task.setAssignedWeight(scale(allocWeight.doubleValue()));
                recomputeTaskMetrics(task, order, simTime);
            }

            checkCancelled();
            task.setTaskCode(task.getTaskCode() + UUID.randomUUID().toString().replace("-", ""));
            String origin = lastOrder == null ? vehicle.getCurrentLocation() : lastOrder.getDeliveryLocation();
            String pickup = order.getPickupLocation(), delivery = order.getDeliveryLocation();
            task.setRoutePolyline(origin + ";" + pickup + ";" + delivery);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { amapRouteService.enrich(() -> {
                    String route = joinPolyline(segmentPolyline(origin, pickup), segmentPolyline(pickup, delivery));
                    try (Connection routeConnection = database.open(); java.sql.PreparedStatement ps = routeConnection.prepareStatement("UPDATE dispatch_task SET route_polyline=? WHERE task_code=?")) {
                        ps.setString(1, route); ps.setString(2, task.getTaskCode()); ps.executeUpdate();
                    } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(DispatchService.class).warn("Route enrichment failed for {}", task.getTaskCode()); }
                }); }
            });
            finishAt = finishAt.plusMinutes(taskDurationMinutes(task));
            orderDao.allocate(conn, order.getId(), task.getAssignedWeight());
            OrderEntity after = orderDao.findById(conn, order.getId());
            if (after == null) {
                throw new RuntimeException("order not found after allocation, id=" + order.getId());
            }

            dispatchTaskDao.save(conn, task);
            order.setAllocatedWeight(after.getAllocatedWeight());
            order.setPendingWeight(after.getPendingWeight());
            order.setStatus(after.getStatus());
            persistedTasks.add(task);
            lastTask = task;
            lastOrder = order;
        }

        if (lastTask == null || lastOrder == null) {
            return List.of();
        }

        vehicleDao.updateAfterDispatch(
                conn,
                vehicle.getId(),
                "BUSY",
                lastOrder.getDeliveryLocationId(),
                lastOrder.getDeliveryLocationName(),
                lastOrder.getDeliveryLocation(),
                sum(persistedTasks, DispatchTask::getPickupDistanceKm),
                sum(persistedTasks, DispatchTask::getTransportDistanceKm),
                sum(persistedTasks, DispatchTask::getTotalRevenue),
                sum(persistedTasks, DispatchTask::getTotalCost),
                sum(persistedTasks, DispatchTask::getNetProfit),
                finishAt
        );
        vehicle.setStatus("BUSY");
        vehicle.setCurrentLocationId(lastOrder.getDeliveryLocationId());
        vehicle.setCurrentLocationName(lastOrder.getDeliveryLocationName());
        vehicle.setCurrentLocation(lastOrder.getDeliveryLocation());
        vehicle.setAvailableAt(finishAt);
        return persistedTasks;
    }

    private List<DispatchTask> applyTasksInMemory(Vehicle vehicle,
                                                  List<DispatchTask> tasks,
                                                  List<OrderEntity> pendingOrders,
                                                  LocalDateTime simTime) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        LocalDateTime finishAt = simTime;
        OrderEntity lastOrder = null;
        List<DispatchTask> persistedTasks = new ArrayList<>();

        for (DispatchTask task : tasks) {
            OrderEntity order = findOrderById(pendingOrders, task.getOrderId());
            if (order == null || !isPending(order)) {
                continue;
            }

            BigDecimal allocWeight = minPositive(task.getAssignedWeight(), order.getPendingWeight());
            if (allocWeight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (allocWeight.compareTo(task.getAssignedWeight()) != 0) {
                task.setAssignedWeight(scale(allocWeight.doubleValue()));
                recomputeTaskMetrics(task, order, simTime);
            }

            finishAt = finishAt.plusMinutes(taskDurationMinutes(task));
            order.setAllocatedWeight(zeroIfNull(order.getAllocatedWeight()).add(task.getAssignedWeight()).setScale(2, RoundingMode.HALF_UP));
            order.setPendingWeight(zeroIfNull(order.getPendingWeight()).subtract(task.getAssignedWeight()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            order.setStatus(order.getPendingWeight().compareTo(BigDecimal.ZERO) == 0 ? "ASSIGNED" : "PARTIAL");

            persistedTasks.add(task);
            lastOrder = order;
        }

        if (lastOrder == null) {
            return List.of();
        }

        vehicle.setStatus("BUSY");
        vehicle.setCurrentLocationId(lastOrder.getDeliveryLocationId());
        vehicle.setCurrentLocationName(lastOrder.getDeliveryLocationName());
        vehicle.setCurrentLocation(lastOrder.getDeliveryLocation());
        vehicle.setAvailableAt(finishAt);
        vehicle.setEmptyMileageKm(zeroIfNull(vehicle.getEmptyMileageKm()).add(sum(persistedTasks, DispatchTask::getPickupDistanceKm)));
        vehicle.setLoadedMileageKm(zeroIfNull(vehicle.getLoadedMileageKm()).add(sum(persistedTasks, DispatchTask::getTransportDistanceKm)));
        vehicle.setTotalMileageKm(
                zeroIfNull(vehicle.getTotalMileageKm())
                        .add(sum(persistedTasks, DispatchTask::getPickupDistanceKm))
                        .add(sum(persistedTasks, DispatchTask::getTransportDistanceKm))
        );
        vehicle.setTotalRevenue(zeroIfNull(vehicle.getTotalRevenue()).add(sum(persistedTasks, DispatchTask::getTotalRevenue)));
        vehicle.setTotalCost(zeroIfNull(vehicle.getTotalCost()).add(sum(persistedTasks, DispatchTask::getTotalCost)));
        vehicle.setTotalProfit(zeroIfNull(vehicle.getTotalProfit()).add(sum(persistedTasks, DispatchTask::getNetProfit)));
        vehicle.setTotalOrders((vehicle.getTotalOrders() == null ? 0 : vehicle.getTotalOrders()) + persistedTasks.size());
        return persistedTasks;
    }

    private void releaseAvailableVehiclesInMemory(List<Vehicle> vehicles, LocalDateTime simTime) {
        for (Vehicle vehicle : vehicles) {
            if (!"BUSY".equals(vehicle.getStatus())) {
                continue;
            }
            if (vehicle.getAvailableAt() != null && !vehicle.getAvailableAt().isAfter(simTime)) {
                vehicle.setStatus("IDLE");
            }
        }
    }

    private List<Vehicle> collectReadyVehicles(List<Vehicle> vehicles, LocalDateTime simTime) {
        List<Vehicle> ready = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!"IDLE".equals(vehicle.getStatus())) {
                continue;
            }
            LocalDateTime availableAt = vehicle.getAvailableAt();
            if (availableAt == null || !availableAt.isAfter(simTime)) {
                ready.add(vehicle);
            }
        }
        ready.sort(Comparator.comparing(v -> v.getAvailableAt() != null ? v.getAvailableAt() : simTime));
        return ready;
    }

    private LocalDateTime earliestAvailableAt(List<Vehicle> vehicles, LocalDateTime currentTime) {
        LocalDateTime earliest = null;
        for (Vehicle vehicle : vehicles) {
            LocalDateTime availableAt = vehicle.getAvailableAt();
            if (availableAt == null || !availableAt.isAfter(currentTime)) {
                continue;
            }
            if (earliest == null || availableAt.isBefore(earliest)) {
                earliest = availableAt;
            }
        }
        return earliest;
    }

    private boolean hasPendingOrders(List<OrderEntity> orders) {
        for (OrderEntity order : orders) {
            if (isPending(order)) {
                return true;
            }
        }
        return false;
    }

    private List<DispatchTask> buildTasksFromPlan(Vehicle vehicle,
                                                  List<OrderEntity> planOrders,
                                                  LocalDateTime baseTime,
                                                  Map<Long, BigDecimal> assignedWeightMap,
                                                  boolean optimizedMode) {
        List<DispatchTask> tasks = new ArrayList<>();
        String currentLoc = vehicle.getCurrentLocation();
        LocalDateTime currentTime = baseTime;

        for (OrderEntity order : planOrders) {
            BigDecimal assignedWeight = assignedWeightMap.get(order.getId());
            if (assignedWeight == null || assignedWeight.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            DispatchTask task = buildSequentialTask(vehicle, order, currentLoc, currentTime, assignedWeight, baseTime, optimizedMode);
            tasks.add(task);
            currentLoc = order.getDeliveryLocation();
            currentTime = currentTime.plusMinutes(taskDurationMinutes(task));
        }
        return tasks;
    }

    private List<DispatchTask> selectPreferredTasks(List<DispatchTask> baselineTasks,
                                                    List<DispatchTask> optimizedTasks) {
        PlanScore baselineScore = scoreTasks(baselineTasks);
        PlanScore optimizedScore = scoreTasks(optimizedTasks);
        return optimizedScore.isBetterThan(baselineScore) ? optimizedTasks : baselineTasks;
    }

    private DispatchTask buildSequentialTask(Vehicle vehicle,
                                             OrderEntity order,
                                             String currentLoc,
                                             LocalDateTime availableAt,
                                             BigDecimal assignedWeight,
                                             LocalDateTime simTime,
                                             boolean optimizedMode) {
        double pickupDistanceKm = estimateRoadDistanceKm(currentLoc, order.getPickupLocation());
        double transportDistanceKm = resolveTransportDistanceKm(order, order.getPickupLocation(), order.getDeliveryLocation());
        double pickupTimeH = pickupDistanceKm / safeSpeed(vehicle);
        double transportTimeH = resolveTransportDurationH(order, transportDistanceKm, vehicle);

        double cargoWaitH = hoursBetween(order.getCreatedAt(), availableAt) + pickupTimeH;
        double cost1 = costConfig.calcCost1(
                0.0,
                pickupTimeH,
                transportTimeH,
                vehicle.getWearCostPerHour().doubleValue(),
                pickupDistanceKm,
                transportDistanceKm,
                vehicle.getFuelCostPerKm().doubleValue()
        );
        double cost2 = costConfig.calcCost2(assignedWeight.doubleValue(), cargoWaitH);
        double cost3 = costConfig.calcCost3(assignedWeight.doubleValue());
        double revenue = calcRevenue(order, assignedWeight.doubleValue(), transportDistanceKm);
        double totalCost = cost1 + cost2 + cost3;
        double netProfit = revenue - totalCost;
        double orderWaitH = hoursBetween(order.getCreatedAt(), simTime);
        double score = costConfig.calcScore(netProfit, orderWaitH, assignedWeight.doubleValue());

        DispatchTask task = new DispatchTask();
        // Trial plans stay small; only selected tasks need globally unique persisted identifiers.
        task.setTaskCode(optimizedMode ? "OT" : "BT");
        task.setOrderId(order.getId());
        task.setVehicleId(vehicle.getId());
        task.setAssignedWeight(scale(assignedWeight.doubleValue()));
        task.setPickupDistanceKm(scale(pickupDistanceKm));
        task.setPickupTimeH(scale(pickupTimeH));
        task.setTransportDistanceKm(scale(transportDistanceKm));
        task.setTransportTimeH(scale(transportTimeH));
        task.setVehicleWaitTimeH(scale(availableAt.equals(simTime) ? hoursBetween(vehicle.getAvailableAt(), simTime) : 0));
        task.setCargoWaitTimeH(scale(cargoWaitH));
        task.setCost1(scale(cost1));
        task.setCost2(scale(cost2));
        task.setCost3(scale(cost3));
        task.setTotalCost(scale(totalCost));
        task.setTotalRevenue(scale(revenue));
        task.setNetProfit(scale(netProfit));
        task.setScore(scale(score));
        task.setRoutePolyline("");
        task.setStatus("RUNNING");
        if (policy.get() != null) {
            double oldPickup = task.getPickupTimeH().doubleValue();
            policy.get().adjust(task, vehicle, order, availableAt);
            task.setCargoWaitTimeH(scale(cargoWaitH + task.getPickupTimeH().doubleValue() - oldPickup));
            task.setCost1(scale(costConfig.calcCost1(0, task.getPickupTimeH().doubleValue(), task.getTransportTimeH().doubleValue(),
                    vehicle.getWearCostPerHour().doubleValue(), pickupDistanceKm, transportDistanceKm, vehicle.getFuelCostPerKm().doubleValue())));
            recomputeTaskMetrics(task, order, simTime);
        }
        task.setSimulationStartedAt(availableAt);
        task.setSimulationFinishedAt(availableAt.plusMinutes(taskDurationMinutes(task)));
        return task;
    }

    private PlanScore calcSequentialPlanScore(Vehicle vehicle,
                                              List<OrderEntity> planOrders,
                                              String startLoc,
                                              LocalDateTime baseTime,
                                              Map<Long, BigDecimal> assignedWeightMap) {
        if (planOrders.isEmpty()) {
            return PlanScore.empty();
        }
        return scoreTasks(buildTasksFromPlan(vehicle, planOrders, baseTime, assignedWeightMap, true));
    }

    private void recomputeTaskMetrics(DispatchTask task, OrderEntity order, LocalDateTime simTime) {
        double assignedWeight = task.getAssignedWeight().doubleValue();
        double cargoWaitH = task.getCargoWaitTimeH().doubleValue();
        double totalRevenue = calcRevenue(order, assignedWeight, task.getTransportDistanceKm().doubleValue());
        boolean optimizedMode = task.getTaskCode() != null && task.getTaskCode().startsWith("OT");
        double cost2 = costConfig.calcCost2(assignedWeight, cargoWaitH);
        double cost3 = costConfig.calcCost3(assignedWeight);
        double totalCost = task.getCost1().doubleValue() + cost2 + cost3;
        double netProfit = totalRevenue - totalCost;
        double score = costConfig.calcScore(netProfit, hoursBetween(order.getCreatedAt(), simTime), assignedWeight);

        task.setCost2(scale(cost2));
        task.setCost3(scale(cost3));
        task.setTotalRevenue(scale(totalRevenue));
        task.setTotalCost(scale(totalCost));
        task.setNetProfit(scale(netProfit));
        task.setScore(scale(score));
    }

    private String buildSequentialPolyline(String startLoc, OrderEntity order) {
        return joinPolyline(
                segmentPolyline(startLoc, order.getPickupLocation()),
                segmentPolyline(order.getPickupLocation(), order.getDeliveryLocation())
        );
    }

    private String segmentPolyline(String from, String to) {
        try {
            AmapRouteService.RouteResult route = amapRouteService.fetchRoute(from, to);
            if (route != null && route.isOk() && route.getPolyline() != null && !route.getPolyline().isEmpty()) {
                return route.getPolyline();
            }
        } catch (Exception ignored) {
        }
        return from + ";" + to;
    }

    private String joinPolyline(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + ";" + second;
    }

    private double estimateRoadDistanceKm(String from, String to) {
        return Geo.roadEstimate(from, to);
    }

    private double evaluateSingleOrderProfitPerHour(Vehicle vehicle,
                                                    OrderEntity order,
                                                    LocalDateTime simTime,
                                                    boolean optimizedMode) {
        if (order == null || !isPending(order)) {
            return Double.NEGATIVE_INFINITY;
        }
        BigDecimal assignedWeight = minPositive(vehicle.getMaxLoadTon(), order.getPendingWeight());
        if (assignedWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        DispatchTask task = buildSequentialTask(
                vehicle, order, vehicle.getCurrentLocation(), simTime, assignedWeight, simTime, optimizedMode
        );
        PlanScore planScore = scoreTasks(List.of(task));
        return planScore.profitPerHour;
    }

    private double resolveTransportDistanceKm(OrderEntity order, String pickupLoc, String deliveryLoc) {
        if (order.getRouteDistanceKm() != null && order.getRouteDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
            return order.getRouteDistanceKm().doubleValue();
        }
        return estimateRoadDistanceKm(pickupLoc, deliveryLoc);
    }

    private double resolveTransportDurationH(OrderEntity order, double transportDistanceKm, Vehicle vehicle) {
        return transportDistanceKm / safeSpeed(vehicle);
    }

    private double calcRevenue(OrderEntity order, double cargoWeight, double routeDistanceKm) {
        if (order.getRevenuePerTonKm() != null && order.getRevenuePerTonKm().compareTo(BigDecimal.ZERO) > 0) {
            return cargoWeight * routeDistanceKm * order.getRevenuePerTonKm().doubleValue();
        }
        return costConfig.calcRevenue(cargoWeight, routeDistanceKm);
    }

    private BigDecimal sum(List<DispatchTask> tasks, ValueGetter getter) {
        BigDecimal total = BigDecimal.ZERO;
        for (DispatchTask task : tasks) {
            BigDecimal value = getter.get(task);
            total = total.add(value == null ? BigDecimal.ZERO : value);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private PlanScore scoreTasks(List<DispatchTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return PlanScore.empty();
        }
        double totalProfit = 0.0;
        long totalMinutes = 0L;
        for (DispatchTask task : tasks) {
            if (task == null || task.getNetProfit() == null) {
                continue;
            }
            totalProfit += task.getNetProfit().doubleValue();
            totalMinutes = Math.addExact(totalMinutes, taskDurationMinutes(task));
        }
        if (totalMinutes <= 0L) {
            return PlanScore.empty();
        }
        double hours = totalMinutes / 60.0;
        return new PlanScore(totalProfit, totalProfit / hours, tasks.size());
    }

    private long taskDurationMinutes(DispatchTask task) {
        long minutes = task.getPickupTimeH()
                .add(task.getTransportTimeH())
                .multiply(new BigDecimal("60"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return Math.max(1, minutes);
    }

    private double safeSpeed(Vehicle vehicle) {
        double speed = vehicle.getActualSpeedKmh() == null ? 0.0 : vehicle.getActualSpeedKmh().doubleValue();
        return speed > 0 ? speed : 60.0;
    }

    private double hoursBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return 0.0;
        }
        double hours = Duration.between(start, end).toMinutes() / 60.0;
        return Math.max(0.0, hours);
    }

    private int compareOrderPriority(OrderEntity left, OrderEntity right) {
        LocalDateTime leftTime = left.getCreatedAt();
        LocalDateTime rightTime = right.getCreatedAt();
        if (leftTime != null && rightTime != null) {
            int compare = leftTime.compareTo(rightTime);
            if (compare != 0) {
                return compare;
            }
        } else if (leftTime != null) {
            return -1;
        } else if (rightTime != null) {
            return 1;
        }
        return Long.compare(left.getId(), right.getId());
    }

    private boolean isPending(OrderEntity order) {
        return order != null
                && order.getPendingWeight() != null
                && order.getPendingWeight().compareTo(BigDecimal.ZERO) > 0
                && ("PENDING".equals(order.getStatus()) || "PARTIAL".equals(order.getStatus()));
    }

    private BigDecimal minPositive(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        return a.min(b).max(BigDecimal.ZERO);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<Vehicle> cloneVehicles(List<Vehicle> vehicles) {
        List<Vehicle> copies = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            Vehicle copy = new Vehicle();
            copy.setId(vehicle.getId());
            copy.setVehicleCode(vehicle.getVehicleCode());
            copy.setVehicleType(vehicle.getVehicleType());
            copy.setMaxLoadTon(vehicle.getMaxLoadTon());
            copy.setActualSpeedKmh(vehicle.getActualSpeedKmh());
            copy.setSimSpeedKms(vehicle.getSimSpeedKms());
            copy.setWearCostPerHour(vehicle.getWearCostPerHour());
            copy.setFuelConsumptionPer100km(vehicle.getFuelConsumptionPer100km());
            copy.setFuelCostPerKm(vehicle.getFuelCostPerKm());
            copy.setCurrentLocationId(vehicle.getCurrentLocationId());
            copy.setCurrentLocationName(vehicle.getCurrentLocationName());
            copy.setCurrentLocation(vehicle.getCurrentLocation());
            copy.setStatus(vehicle.getStatus());
            copy.setAvailableAt(vehicle.getAvailableAt());
            copy.setTotalMileageKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copy.setEmptyMileageKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copy.setLoadedMileageKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copy.setTotalOrders(0);
            copy.setTotalRevenue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copy.setTotalCost(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copy.setTotalProfit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            copies.add(copy);
        }
        return copies;
    }

    private List<OrderEntity> cloneOrders(List<OrderEntity> orders) {
        List<OrderEntity> copies = new ArrayList<>();
        for (OrderEntity order : orders) {
            OrderEntity copy = new OrderEntity();
            copy.setId(order.getId());
            copy.setOrderCode(order.getOrderCode());
            copy.setCargoType(order.getCargoType());
            copy.setCargoTotalWeight(order.getCargoTotalWeight());
            copy.setAllocatedWeight(zeroIfNull(order.getAllocatedWeight()));
            copy.setPendingWeight(zeroIfNull(order.getPendingWeight()));
            copy.setPickupLocationId(order.getPickupLocationId());
            copy.setPickupLocationName(order.getPickupLocationName());
            copy.setPickupLocation(order.getPickupLocation());
            copy.setDeliveryLocationId(order.getDeliveryLocationId());
            copy.setDeliveryLocationName(order.getDeliveryLocationName());
            copy.setDeliveryLocation(order.getDeliveryLocation());
            copy.setRouteDistanceKm(order.getRouteDistanceKm());
            copy.setRouteDurationH(order.getRouteDurationH());
            copy.setRevenuePerTonKm(order.getRevenuePerTonKm());
            copy.setTotalRevenue(zeroIfNull(order.getTotalRevenue()));
            copy.setStatus(order.getStatus());
            copy.setCreatedAt(order.getCreatedAt());
            copies.add(copy);
        }
        return copies;
    }

    private OrderEntity findOrderById(List<OrderEntity> orders, Long orderId) {
        for (OrderEntity order : orders) {
            if (order.getId().equals(orderId)) {
                return order;
            }
        }
        return null;
    }

    private double calcStraightDistance(String p1, String p2) {
        try {
            if (p1 == null || p2 == null || !p1.contains(",") || !p2.contains(",")) {
                return -1;
            }
            String[] a = p1.split(",");
            String[] b = p2.split(",");
            double lng1 = Double.parseDouble(a[0].trim());
            double lat1 = Double.parseDouble(a[1].trim());
            double lng2 = Double.parseDouble(b[0].trim());
            double lat2 = Double.parseDouble(b[1].trim());
            double dx = lng1 - lng2;
            double dy = lat1 - lat2;
            return Math.sqrt(dx * dx + dy * dy);
        } catch (Exception e) {
            return -1;
        }
    }

    private BigDecimal scale(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 999999999999.99d)
            throw new IllegalArgumentException("调度数值超出数据库可存储范围");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_OPTIMIZED;
        }
        String value = mode.trim().toUpperCase();
        if (MODE_BASELINE.equals(value)) {
            return MODE_BASELINE;
        }
        if (MODE_OPTIMIZED.equals(value)) return MODE_OPTIMIZED;
        if (MODE_WEIGHTED.equals(value)) return MODE_WEIGHTED;
        throw new IllegalArgumentException("不支持的调度算法: " + value);
    }

    @FunctionalInterface
    private interface ValueGetter {
        BigDecimal get(DispatchTask task);
    }

    private static class PlanScore {
        private final double totalProfit;
        private final double profitPerHour;
        private final int taskCount;

        private PlanScore(double totalProfit, double profitPerHour, int taskCount) {
            this.totalProfit = totalProfit;
            this.profitPerHour = profitPerHour;
            this.taskCount = taskCount;
        }

        private static PlanScore empty() {
            return new PlanScore(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, 0);
        }

        private boolean isBetterThan(PlanScore other) {
            if (other == null) {
                return true;
            }
            if (profitPerHour > other.profitPerHour + 0.01d) {
                return true;
            }
            if (Math.abs(profitPerHour - other.profitPerHour) <= 0.01d && totalProfit > other.totalProfit + 0.01d) {
                return true;
            }
            return Math.abs(profitPerHour - other.profitPerHour) <= 0.01d
                    && Math.abs(totalProfit - other.totalProfit) <= 0.01d
                    && taskCount > other.taskCount;
        }
    }

    public static class SimulationSnapshot {
        private final String mode;
        private final LocalDateTime simTime;
        private final List<Vehicle> vehicles;
        private final List<OrderEntity> orders;
        private final List<DispatchTask> tasks;

        public SimulationSnapshot(String mode,
                                  LocalDateTime simTime,
                                  List<Vehicle> vehicles,
                                  List<OrderEntity> orders,
                                  List<DispatchTask> tasks) {
            this.mode = mode;
            this.simTime = simTime;
            this.vehicles = vehicles;
            this.orders = orders;
            this.tasks = tasks;
        }

        public String getMode() {
            return mode;
        }

        public LocalDateTime getSimTime() {
            return simTime;
        }

        public List<Vehicle> getVehicles() {
            return vehicles;
        }

        public List<OrderEntity> getOrders() {
            return orders;
        }

        public List<DispatchTask> getTasks() {
            return tasks;
        }
    }
}
