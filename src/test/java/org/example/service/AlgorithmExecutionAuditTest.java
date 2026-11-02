package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.CostConfig;
import org.example.dao.*;
import org.example.entity.*;
import org.example.sandbox.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Read-only historical input audit. All recomputation runs in isolated memory. */
@EnabledIfSystemProperty(named="algorithm.audit", matches="true")
class AlgorithmExecutionAuditTest {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final Map<String,Object> evidence = new LinkedHashMap<>();
    String resolve(String value) {
        if (!value.startsWith("${")) return value;
        String[] parts=value.substring(2,value.length()-1).split(":",2);
        return System.getenv().getOrDefault(parts[0],parts.length>1?parts[1]:"");
    }
    String setting(String name) throws Exception {
        String line=Files.readString(Path.of("src/main/resources/application.yml")).lines()
                .filter(s->s.trim().startsWith(name+":")).findFirst().orElseThrow();
        String v=line.substring(line.indexOf(':')+1).trim();
        if(v.startsWith("\"")&&v.endsWith("\""))v=v.substring(1,v.length()-1);
        return resolve(v);
    }
    record Audit(SandboxEngine.Result result,List<Map<String,Object>> tasks,int heuristicTrials,int baselineTrials,int chosenHeuristicTasks,int maxDistinctCandidateOrders) {}
    @SuppressWarnings("unchecked")
    Audit compute(ExperimentConfig config,List<LocationPoint> points) {
        Database db=mock(Database.class);
        VehicleService vs=new VehicleService(db,mock(VehicleDao.class),mock(DaoAdapter.class),new Dsp(),mock(DispatchTaskDao.class));
        DispatchService ds=spy(new DispatchService(db,new Dsp(),mock(VehicleDao.class),mock(OrderDao.class),mock(DispatchTaskDao.class),mock(AmapRouteService.class),new CostConfig()));
        AtomicReference<DispatchService.SimulationSnapshot> snapshot=new AtomicReference<>();
        int[] trials={0,0};Map<String,Set<Long>> considered=new LinkedHashMap<>();
        doAnswer(inv->{
            String mode=inv.getArgument(2);assertEquals(config.algorithm(),mode);
            DispatchService.SimulationPolicy original=inv.getArgument(5);
            DispatchService.SimulationPolicy observer=new DispatchService.SimulationPolicy(){
                @Override public void progress(LocalDateTime t,int n){original.progress(t,n);}
                @Override public void adjust(DispatchTask task,Vehicle v,OrderEntity o,LocalDateTime departure){
                    trials[task.getTaskCode().startsWith("OT")?0:1]++;
                    considered.computeIfAbsent(v.getId()+"@"+departure,k->new LinkedHashSet<>()).add(o.getId());
                    original.adjust(task,v,o,departure);
                }
            };
            DispatchService real=new DispatchService(db,new Dsp(),mock(VehicleDao.class),mock(OrderDao.class),mock(DispatchTaskDao.class),mock(AmapRouteService.class),new CostConfig());
            var result=real.simulateSnapshot(inv.getArgument(0),inv.getArgument(1),mode,inv.getArgument(3),inv.getArgument(4),observer);
            snapshot.set(result);return result;
        }).when(ds).simulateSnapshot(anyList(),anyList(),anyString(),any(),any(),any(DispatchService.SimulationPolicy.class));
        var result=new SandboxEngine(ds,vs).execute(config,points,p->{});
        List<Map<String,Object>> tasks=new ArrayList<>();int chosen=0;
        for(DispatchTask t:snapshot.get().getTasks()){
            if(t.getTaskCode().startsWith("OT"))chosen++;
            Map<String,Object> x=new LinkedHashMap<>();x.put("vehicle",t.getVehicleId());x.put("order",t.getOrderId());
            x.put("weight",t.getAssignedWeight());x.put("start",t.getSimulationStartedAt().toString());x.put("finish",t.getSimulationFinishedAt().toString());
            x.put("status",t.getStatus());x.put("emptyKm",t.getPickupDistanceKm());x.put("loadedKm",t.getTransportDistanceKm());
            x.put("profit",t.getNetProfit());x.put("selectedPlanner",t.getTaskCode());tasks.add(x);
        }
        return new Audit(result,tasks,trials[0],trials[1],chosen,considered.values().stream().mapToInt(Set::size).max().orElse(0));
    }
    @Test void reproduceHistoricalPairAndVerifyHeuristicExecution() throws Exception {
        var source=mapper.readTree(Files.readString(Path.of("reports/中期报告同条件实验数据.json")));
        String env=source.path("baseline").path("environmentId").asText();
        List<LocationPoint> points;
        try(Connection c=DriverManager.getConnection(setting("url"),setting("username"),setting("password"))){
            c.setReadOnly(true);c.setAutoCommit(false);
            try(PreparedStatement ps=c.prepareStatement("SELECT document FROM sandbox_environment WHERE environment_id=?")){
                ps.setString(1,env);try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());String raw=rs.getString(1);
                    assertEquals(env,SandboxStore.hash(raw));points=mapper.readValue(raw,new TypeReference<List<LocationPoint>>(){});}
            }
            for(String side:List.of("baseline","candidate"))try(PreparedStatement ps=c.prepareStatement("SELECT document FROM sandbox_run WHERE run_id=?")){
                ps.setString(1,source.path(side).path("runId").asText());try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());
                    var record=mapper.readTree(rs.getString(1));assertEquals(source.path(side).path("config"),record.path("config"));
                    assertEquals(source.path(side).path("metrics"),record.path("metrics"));}
            }
            c.rollback();
        }
        DispatchService ds=new DispatchService(mock(Database.class),new Dsp(),mock(VehicleDao.class),mock(OrderDao.class),mock(DispatchTaskDao.class),mock(AmapRouteService.class),new CostConfig());
        assertEquals(source.path("baseline").path("modelId").asText(),ds.modelId());
        Audit baseline=compute(mapper.treeToValue(source.path("baseline").path("config"),ExperimentConfig.class),points);
        Audit optimized=compute(mapper.treeToValue(source.path("candidate").path("config"),ExperimentConfig.class),points);
        assertEquals(0,baseline.heuristicTrials());assertTrue(optimized.heuristicTrials()>0,"Optimized must actually evaluate heuristic trials");
        for(String side:List.of("baseline","candidate")){
            Audit a=side.equals("baseline")?baseline:optimized;
            for(var e:a.result().metrics().entrySet())assertEquals(0,e.getValue().compareTo(source.path(side).path("metrics").path(e.getKey()).decimalValue()),e.getKey());
            assertEquals(source.path(side).path("taskCount").asInt(),a.result().taskCount());
            evidence.put(side,a);
        }
        evidence.put("environmentHashVerified",env);evidence.put("modelIdVerified",ds.modelId());
        evidence.put("method","Frozen database inputs read in read-only transaction; dispatch recomputed in memory; no live scene writes");
        Files.writeString(Path.of("reports/2026-10-12-启发式执行核查.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        System.out.println("AUDIT heuristicTrials="+optimized.heuristicTrials()+", chosenHeuristicTasks="+optimized.chosenHeuristicTasks()+", maxDistinctCandidates="+optimized.maxDistinctCandidateOrders());
    }
}
