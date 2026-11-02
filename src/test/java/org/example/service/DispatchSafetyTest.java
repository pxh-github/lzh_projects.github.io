package org.example.service;
import org.junit.jupiter.api.*;
import org.example.config.CostConfig;
import org.example.entity.*;
import org.example.sandbox.*;
import org.example.util.Geo;
import org.example.dao.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchSafetyTest {
    DispatchService dispatch;
    VehicleService vehicles;
    final LocalDateTime start = LocalDateTime.of(2026,1,1,0,0);
    @BeforeEach void setup() {
        Database db = mock(Database.class);
        vehicles = new VehicleService(db,mock(VehicleDao.class),mock(DaoAdapter.class),new Dsp(),mock(DispatchTaskDao.class));
        dispatch = new DispatchService(db,new Dsp(),mock(VehicleDao.class),mock(OrderDao.class),mock(DispatchTaskDao.class),mock(AmapRouteService.class),new CostConfig());
    }
    LocationPoint point(String id,String coordinate) { LocationPoint p=new LocationPoint();p.setId(id);p.setName(id);p.setLocation(coordinate);p.setType("170300");return p; }
    Vehicle vehicle() { return vehicles.buildSceneVehicle(1,point("1","104,30"),new Random(3),start); }
    OrderEntity order(LocalDateTime created,String destination) {
        OrderEntity o=new OrderEntity();o.setId(1L);o.setStatus("PENDING");o.setCreatedAt(created);o.setCargoTotalWeight(new BigDecimal("1.0"));o.setPendingWeight(new BigDecimal("1.0"));o.setAllocatedWeight(BigDecimal.ZERO);
        o.setPickupLocation("104,30");o.setDeliveryLocation(destination);o.setDeliveryLocationId("2");o.setDeliveryLocationName("2");o.setRevenuePerTonKm(new BigDecimal("1.50"));return o;
    }
    @Test void excessiveWaitCannotOverflowCargoLoss() {
        CostConfig cost=new CostConfig();
        assertEquals(120,cost.calcCost2(40,1e100));
        assertTrue(Double.isFinite(cost.calcScore(-100,1e100,40)));
        assertThrows(IllegalArgumentException.class,()->cost.calcCost2(40,Double.POSITIVE_INFINITY));
    }
    @Test void algorithmsUseIdenticalCostForIdenticalTask() {
        List<OrderEntity> orders=List.of(order(start.minusYears(30),"104.01,30"));
        var baseline=dispatch.simulateSnapshot(List.of(vehicle()),orders,"BASELINE",start);
        var optimized=dispatch.simulateSnapshot(List.of(vehicle()),orders,"OPTIMIZED",start);
        assertEquals(baseline.getTasks().get(0).getTotalCost(),optimized.getTasks().get(0).getTotalCost());
        assertEquals(new BigDecimal("1.0"),orders.get(0).getPendingWeight()); // Input scene is not mutated.
    }
    @Test void futureOrdersAreNotDispatchedBeforeArrival() {
        var result=dispatch.simulateSnapshot(List.of(vehicle()),List.of(order(start.plusHours(23),"104.01,30")),"BASELINE",start,start.plusHours(24),new DispatchService.SimulationPolicy(){});
        assertEquals(start.plusHours(23),result.getTasks().get(0).getSimulationStartedAt());
    }
    @Test void unfinishedTasksDoNotCountAsCompletedAtHorizon() {
        var result=dispatch.simulateSnapshot(List.of(vehicle()),List.of(order(start.plusHours(23),"110,30")),"BASELINE",start,start.plusHours(24),new DispatchService.SimulationPolicy(){});
        assertEquals("RUNNING",result.getTasks().get(0).getStatus());
        assertEquals("ASSIGNED",result.getOrders().get(0).getStatus());
        assertEquals(BigDecimal.ZERO.setScale(2),SandboxEngine.metrics(result.getTasks(),result.getVehicles(),List.of()).get("completedTonnage"));
    }
    @Test void vehicleSpeedControlsTravelTime() {
        Vehicle slow=vehicle(), fast=vehicle();slow.setActualSpeedKmh(new BigDecimal("20"));fast.setActualSpeedKmh(new BigDecimal("100"));
        var a=dispatch.simulateSnapshot(List.of(slow),List.of(order(start,"105,30")),"BASELINE",start);
        var b=dispatch.simulateSnapshot(List.of(fast),List.of(order(start,"105,30")),"BASELINE",start);
        assertTrue(a.getTasks().get(0).getTransportTimeH().compareTo(b.getTasks().get(0).getTransportTimeH())>0);
    }

    @Test void optimizerCanServeHighestHourlyReturnWhenEveryOrderLosesMoney() {
        Vehicle vehicle=vehicle(); vehicle.setMaxLoadTon(new BigDecimal("1"));
        OrderEntity costly=order(start.minusSeconds(1),"104.01,30"); costly.setPickupLocation("110,30");
        OrderEntity nearby=order(start,"105,30"); nearby.setId(2L);
        nearby.setRevenuePerTonKm(new BigDecimal("0.50"));
        costly.setRevenuePerTonKm(new BigDecimal("0.50"));
        var a=dispatch.simulateSnapshot(List.of(vehicle),List.of(costly,nearby),"BASELINE",start);
        var b=dispatch.simulateSnapshot(List.of(vehicle),List.of(costly,nearby),"OPTIMIZED",start);
        assertEquals(1L,a.getTasks().get(0).getOrderId());
        assertEquals(2L,b.getTasks().get(0).getOrderId());
        assertTrue(b.getTasks().get(0).getNetProfit().signum()<0);
    }
    @Test void seededRealEngineIsReproducible() {
        SandboxEngine engine=new SandboxEngine(dispatch,vehicles);
        ExperimentConfig config=new ExperimentConfig("map",3,24,3,60,123L,"OPTIMIZED",Map.of("WEATHER",true,"CARGO_DAMAGE",true,"SHORTAGE",true));
        List<LocationPoint> points=List.of(point("1","104,30"),point("2","104.01,30.01"),point("3","104.02,30"));
        var a=engine.execute(config,points,p->{}); var b=engine.execute(config,points,p->{});
        assertEquals(a,b);assertTrue(a.completedTaskCount()>0);assertTrue(a.metrics().get("completedTonnage").signum()>0);
    }
    @Test void invalidModeAndSeedRejected() {
        assertThrows(IllegalArgumentException.class,()->DispatchService.normalizeMode("FAKE"));
        assertThrows(IllegalArgumentException.class,()->new ExperimentConfig("map",3,24,5,60,-1L,"BASELINE",Map.of()));
        assertThrows(IllegalArgumentException.class,()->new ExperimentConfig("map",3,24,501,60,123L,"BASELINE",Map.of()));
    }
    @Test void cancelledExecutionTerminates() {
        Thread.currentThread().interrupt();
        try { assertThrows(IllegalStateException.class,()->dispatch.simulateSnapshot(List.of(vehicle()),List.of(order(start,"104.01,30")),"BASELINE",start)); }
        finally { Thread.interrupted(); }
    }
    @Test void geodesicDistanceAndCoordinatesAreBounded() {
        assertThrows(IllegalArgumentException.class,()->Geo.coordinate("NaN,30"));
        assertThrows(IllegalArgumentException.class,()->Geo.coordinate("181,30"));
        assertEquals(0,Geo.roadEstimate("104,30","104,30"));
        assertTrue(Geo.roadEstimate("104,30","105,30")>100);
    }

    @Test void modelFingerprintChangesWhenPricingChanges() {
        CostConfig cost = new CostConfig();
        DispatchService model = new DispatchService(mock(Database.class),new Dsp(),mock(VehicleDao.class),mock(OrderDao.class),mock(DispatchTaskDao.class),mock(AmapRouteService.class),cost);
        String before=model.modelId(); cost.setLoadingCostPerTon(cost.getLoadingCostPerTon()+1);
        assertNotEquals(before,model.modelId());
    }
    @Test void completionModeWaitsForFutureArrivalsAndRoutesBeyondThirtyDays() {
        var snapshot=dispatch.simulateSnapshot(List.of(vehicle()),List.of(order(start.plusDays(40),"110,30")),"BASELINE",start,null,new DispatchService.SimulationPolicy(){});
        assertEquals("DONE",snapshot.getOrders().get(0).getStatus());
        assertEquals("DONE",snapshot.getTasks().get(0).getStatus());
        assertEquals(snapshot.getTasks().get(0).getSimulationFinishedAt(),snapshot.getSimTime());
        assertTrue(snapshot.getSimTime().isAfter(start.plusDays(40)));
    }
    @Test void completionModeFailsInsteadOfReturningPartialSuccessForUnserviceableDemand() {
        assertThrows(IllegalStateException.class,()->dispatch.simulateSnapshot(List.of(),List.of(order(start,"110,30")),"BASELINE",start,null,new DispatchService.SimulationPolicy(){}));
    }
    @Test void countedLongHaulExperimentCompletesEverythingAndIncludesTimeMetrics() {
        var engine=new SandboxEngine(dispatch,vehicles);
        var config=new ExperimentConfig("map",2,0,1,120,222L,"BASELINE",Map.of(),3);
        var progresses=new ArrayList<SandboxEngine.Progress>();
        var result=engine.execute(config,List.of(point("1","104,30"),point("2","140,30")),progresses::add);
        assertEquals(3,result.orderCount());assertEquals(3,result.completedOrderCount());assertEquals(0,result.pendingOrderCount());
        assertEquals(result.taskCount(),result.completedTaskCount());
        assertTrue(result.metrics().get("completedTonnage").signum()>0);
        assertTrue(result.metrics().get("totalCompletionTimeH").compareTo(new BigDecimal("24"))>0);
        assertEquals(BigDecimal.valueOf(java.time.Duration.between(start,LocalDateTime.parse(result.simulationTime())).toMinutes()).divide(new BigDecimal("60"),2,java.math.RoundingMode.HALF_UP),result.metrics().get("totalCompletionTimeH"));
        assertTrue(result.metrics().get("averageOrderCompletionTimeH").signum()>0);
        assertNotEquals(engine.modelId(config),engine.modelId());
        int prior=-1;for(var progress:progresses){assertTrue(progress.progress()>=prior);assertTrue(progress.progress()<100);prior=progress.progress();}
    }
    @Test void finiteOrderCountIsBoundedAndHistoricalWindowIsPreserved() {
        assertThrows(IllegalArgumentException.class,()->new ExperimentConfig("map",2,0,10,60,222L,"BASELINE",Map.of(),0));
        assertThrows(IllegalArgumentException.class,()->new ExperimentConfig("map",2,0,10,60,222L,"BASELINE",Map.of(),1001));
        var legacy=new ExperimentConfig("map",2,24,10,120,222L,"BASELINE",Map.of());
        assertFalse(legacy.completeAll());assertEquals(12,legacy.forCompletion().demandCount());assertTrue(legacy.forCompletion().completeAll());
    }
    @Test void averageCompletionTimeCountsOrdersRatherThanSplitTasks() {
        OrderEntity first=order(start,"104.01,30"),second=order(start.plusHours(2),"104.01,30");
        first.setStatus("DONE");second.setStatus("DONE");second.setId(2L);
        var tasks=new ArrayList<DispatchTask>();
        for(int i=0;i<3;i++) {
            DispatchTask task=new DispatchTask();task.setVehicleId(1L);task.setOrderId(i<2?1L:2L);task.setStatus("DONE");
            task.setSimulationStartedAt(start);task.setSimulationFinishedAt(start.plusHours(i==0?5:i==1?10:3));
            task.setAssignedWeight(BigDecimal.ONE);task.setVehicleWaitTimeH(BigDecimal.ZERO);task.setCargoWaitTimeH(BigDecimal.ZERO);task.setPickupDistanceKm(BigDecimal.ONE);task.setTransportDistanceKm(BigDecimal.ONE);tasks.add(task);
        }
        DispatchService planner=mock(DispatchService.class);
        when(planner.simulateSnapshot(anyList(),anyList(),anyString(),any(),any(),any())).thenReturn(new DispatchService.SimulationSnapshot("BASELINE",start.plusHours(10),List.of(vehicle()),List.of(first,second),tasks));
        var result=new SandboxEngine(planner,vehicles).execute(new ExperimentConfig("map",2,0,1,60,222L,"BASELINE",Map.of(),2),List.of(point("1","104,30"),point("2","104.01,30")),p->{});
        assertEquals(new BigDecimal("10.00"),result.metrics().get("totalCompletionTimeH"));
        // Order 1 finishes after 10 hours; order 2 after 1 hour. Two split tasks do not duplicate order 1.
        assertEquals(new BigDecimal("5.50"),result.metrics().get("averageOrderCompletionTimeH"));
    }
}
