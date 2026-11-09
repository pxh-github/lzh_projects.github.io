package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.*;
import org.example.sandbox.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WeightedDispatchTest {
    final DispatchSafetyTest scene = new DispatchSafetyTest();
    final LocalDateTime start = LocalDateTime.of(2026,1,1,0,0);
    @BeforeEach void setup() { scene.setup(); }

    Vehicle vehicle() { Vehicle v=scene.vehicle(); v.setMaxLoadTon(BigDecimal.TEN); return v; }
    List<OrderEntity> demand() {
        OrderEntity heavy=scene.order(start.minusMinutes(1),"105.01,30");
        heavy.setPickupLocation("105,30"); heavy.setCargoTotalWeight(BigDecimal.TEN); heavy.setPendingWeight(BigDecimal.TEN);
        OrderEntity nearby=scene.order(start,"104.01,30"); nearby.setId(2L);
        return List.of(heavy,nearby);
    }
    DispatchService.SimulationSnapshot run(ObjectiveWeights weights, List<WeightedObjective.Decision> audit) {
        return scene.dispatch.simulateSnapshot(List.of(vehicle()),demand(),"WEIGHTED",start,null,new DispatchService.SimulationPolicy() {
            @Override public ObjectiveWeights objectiveWeights() { return weights; }
            @Override public void weightedDecision(WeightedObjective.Decision decision) { audit.add(decision); }
        });
    }
    @Test void weightsRejectInvalidValuesAndNormalizeRelativePreferences() {
        assertThrows(IllegalArgumentException.class,()->new ObjectiveWeights(0,0,0,0,0));
        for(double v:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,1000001})
            assertThrows(IllegalArgumentException.class,()->new ObjectiveWeights(v,1,1,1,1));
        assertArrayEquals(ObjectiveWeights.defaults().normalized(),new ObjectiveWeights(25,25,20,15,15).normalized(),1e-12);
    }
    @Test void mixedDirectionsAndConstantObjectivesAreHandledWithoutUnitBias() {
        var a=new WeightedObjective.Values(10000,100,0.2,1,100000);
        var b=new WeightedObjective.Values(0,0,0.9,10,0);
        var scale=new WeightedObjective.Scale(List.of(a,b));
        assertEquals(1,scale.evaluate(a,ObjectiveWeights.defaults()).score(),1e-12);
        assertEquals(0,scale.evaluate(b,ObjectiveWeights.defaults()).score(),1e-12);
        assertEquals(0,new WeightedObjective.Scale(List.of(a,a)).evaluate(a,ObjectiveWeights.defaults()).score());
    }
    @Test void changingOnlyWeightsChangesActualDispatchAndBothRunsCompleteAllDemand() {
        var empty=run(new ObjectiveWeights(0,1,0,0,0),new ArrayList<>());
        var tonnage=run(new ObjectiveWeights(0,0,0,1,0),new ArrayList<>());
        assertEquals(2L,empty.getTasks().get(0).getOrderId());
        assertEquals(1L,tonnage.getTasks().get(0).getOrderId());
        for(var result:List.of(empty,tonnage)) {
            assertTrue(result.getTasks().stream().allMatch(t->"DONE".equals(t.getStatus()) && t.getTaskCode().equals("WT")));
            assertTrue(result.getOrders().stream().allMatch(o->"DONE".equals(o.getStatus()) && o.getPendingWeight().signum()==0));
            assertEquals(0,new BigDecimal("11").compareTo(result.getTasks().stream().map(DispatchTask::getAssignedWeight).reduce(BigDecimal.ZERO,BigDecimal::add)));
            assertTrue(result.getTasks().stream().allMatch(t->t.getAssignedWeight().compareTo(BigDecimal.TEN)<=0));
        }
        assertEquals(0,BigDecimal.TEN.compareTo(demand().get(0).getPendingWeight()));
    }
    @Test void decisionAuditReconstructsTheSelectedWeightedScore() {
        List<WeightedObjective.Decision> decisions=new ArrayList<>();
        var weights=ObjectiveWeights.defaults();run(weights,decisions);
        assertFalse(decisions.isEmpty());
        for(var decision:decisions) {
            assertTrue(decision.candidateCount()>0);
            assertEquals(decision.evaluation().score(),decision.evaluation().contributions().stream().mapToDouble(Double::doubleValue).sum(),1e-12);
            double[] losses=decision.objectives().losses();
            for(int i=0;i<5;i++) {
                double range=decision.maxima().get(i)-decision.minima().get(i);
                double expected=range<=1e-12?0:(losses[i]-decision.minima().get(i))/range;
                assertEquals(expected,decision.evaluation().normalizedLosses().get(i),1e-12);
                assertTrue(expected>=-1e-12 && expected<=1+1e-12);
            }
        }
    }
    @Test void parallelPreferencesStayIsolatedAndDefaultPolicyIsRestored() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->run(new ObjectiveWeights(0,1,0,0,0),new ArrayList<>()));
            var b=pool.submit(()->run(new ObjectiveWeights(0,0,0,1,0),new ArrayList<>()));
            assertEquals(2L,a.get(10,TimeUnit.SECONDS).getTasks().get(0).getOrderId());
            assertEquals(1L,b.get(10,TimeUnit.SECONDS).getTasks().get(0).getOrderId());
            var defaultRun=scene.dispatch.simulateSnapshot(List.of(vehicle()),demand(),"WEIGHTED",start,null,new DispatchService.SimulationPolicy(){});
            assertEquals(run(ObjectiveWeights.defaults(),new ArrayList<>()).getTasks().get(0).getOrderId(),defaultRun.getTasks().get(0).getOrderId());
        } finally {pool.shutdownNow();}
    }
    @Test void weightedConfigSurvivesSerializationCopiesAndCompletionMigration() throws Exception {
        var original=new ExperimentConfig("map",2,0,3,60,222L,"WEIGHTED",Map.of(),20,new ObjectiveWeights(25,25,20,15,15));
        var mapper=new ObjectMapper();
        var restored=mapper.readValue(mapper.writeValueAsString(original),ExperimentConfig.class);
        assertEquals(original,restored);assertEquals(original,restored.forCompletion());
        var legacy=mapper.readValue("{\"vehicleCount\":3,\"orderGenerationInterval\":60,\"randomSeed\":222,\"simulationDuration\":24,\"algorithm\":\"BASELINE\"}",ExperimentConfig.class);
        assertNull(legacy.objectiveWeights());assertFalse(legacy.completeAll());
    }
    @Test void seededWeightedEngineProducesReproducibleAuditedResultsWithEvents() {
        var config=new ExperimentConfig("map",3,0,3,1,123L,"WEIGHTED",Map.of("WEATHER",true,"SHORTAGE",true,"CARGO_DAMAGE",true),30,ObjectiveWeights.defaults());
        var points=List.of(scene.point("1","104,30"),scene.point("2","104.1,30.1"),scene.point("3","104.2,30"));
        var engine=new SandboxEngine(scene.dispatch,scene.vehicles);
        var a=engine.execute(config,points,p->{});var b=engine.execute(config,points,p->{});
        assertEquals(a,b);assertEquals(30,a.completedOrderCount());assertEquals(0,a.pendingOrderCount());
        assertTrue(((Number)a.optimization().get("decisionCount")).intValue()>0);
        assertTrue(((List<?>)a.optimization().get("decisionSamples")).size()<=20);
        assertTrue(a.metrics().get("cargoWaitingTonMinutes").signum()>=0);
        assertTrue(a.metrics().get("capacityDistanceUtilization").compareTo(new BigDecimal("100"))<=0);
        assertEquals(a.taskCount(),a.playback().tasks().size());
        assertEquals(3,a.playback().vehicles().size());assertEquals(30,a.playback().orders().size());
        Map<Long,String> locations=new HashMap<>();a.playback().vehicles().forEach(v->locations.put(v.id(),v.initialLocation()));
        BigDecimal tons=BigDecimal.ZERO,carbon=BigDecimal.ZERO;
        for(var task:a.playback().tasks()) {
            assertEquals(locations.get(task.vehicleId()),task.origin());locations.put(task.vehicleId(),task.delivery());
            assertFalse(task.pickupAt().isBefore(task.startedAt()));assertFalse(task.finishedAt().isBefore(task.pickupAt()));
            tons=tons.add(task.completedTonnage());carbon=carbon.add(task.carbonKg());
        }
        assertEquals(0,a.metrics().get("completedTonnage").compareTo(tons.setScale(2,java.math.RoundingMode.HALF_UP)));
        assertEquals(0,a.metrics().get("totalCarbonEmission").compareTo(carbon.setScale(2,java.math.RoundingMode.HALF_UP)));
    }
}
