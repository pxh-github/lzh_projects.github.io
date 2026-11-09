package org.example.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.example.dao.OrderDao;
import org.example.sandbox.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import java.sql.*;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Opt-in MySQL tests use a newly-created schema and never mutate live vehicle/order records. */
@EnabledIfEnvironmentVariable(named="MYSQL_INTEGRATION",matches="1")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"stats.sampling.enabled=false","server.address=127.0.0.1"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendMySqlIntegrationTest {
    static String schema,url,user,password;
    static boolean owned;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        Map<?,?> yaml=new Yaml().load(Files.readString(Path.of("src/main/resources/application.yml")));
        Map<?,?> source=(Map<?,?>)((Map<?,?>)yaml.get("spring")).get("datasource");
        url=resolve(source.get("url").toString());user=resolve(source.get("username").toString());password=resolve(source.get("password").toString());
        schema="transport_test_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) {
            String live=c.getCatalog(); assertTrue(live.matches("[A-Za-z0-9_]+"));
            s.execute("CREATE DATABASE `"+schema+"`");owned=true;
            for(String table:List.of("vehicle","transport_order","dispatch_task","location")) s.execute("CREATE TABLE `"+schema+"`."+table+" LIKE `"+live+"`."+table);
            s.execute("INSERT INTO `"+schema+"`.location SELECT * FROM `"+live+"`.location LIMIT 20");
            List<String> locationIds=new ArrayList<>();
            try(ResultSet rs=s.executeQuery("SELECT id FROM `"+schema+"`.location ORDER BY id")){while(rs.next())locationIds.add(rs.getString(1));}
            try(PreparedStatement ps=c.prepareStatement("UPDATE `"+schema+"`.location SET location=?, type='170300',location_name='原料加工工厂' WHERE id=?")){
                for(int i=0;i<locationIds.size();i++){ps.setString(1,(104+i/10000.0)+",30");ps.setString(2,locationIds.get(i));ps.addBatch();}ps.executeBatch();
            }
        }
        String testUrl=url.replaceFirst("/[^/?]+\\?","/"+schema+"?");
        registry.add("spring.datasource.url",()->testUrl);registry.add("spring.datasource.username",()->user);registry.add("spring.datasource.password",()->password);
    }
    static String resolve(String value) {
        if(!value.startsWith("${")) return value;
        String[] fields=value.substring(2,value.length()-1).split(":",2);
        return System.getenv().getOrDefault(fields[0],fields.length>1?fields[1]:"");
    }
    @AfterAll void cleanup() throws Exception {
        try {
            if("1".equals(System.getenv("UI_SMOKE"))) {
                scheduler.reset(()->database.write(()->{vehicleService.initVehicles(3);orderService.generateOrders(4);}));
                dispatch.runCycleRound(scheduler.currentTime().plusSeconds(2),"BASELINE");
                Path done=Path.of("target","ui-smoke-"+UUID.randomUUID()+".done").toAbsolutePath();
                Files.writeString(Path.of("target","ui-smoke-ready.json"),mapper.writeValueAsString(Map.of("port",httpPort,"schema",schema,"doneFile",done.toString(),"processId",ProcessHandle.current().pid())));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(Long.parseLong(System.getenv().getOrDefault("UI_SMOKE_TIMEOUT_SECONDS","180")));
                try {while(!Files.exists(done)&&System.nanoTime()<deadline)Thread.sleep(100);assertTrue(Files.exists(done),"浏览器验收等待超时");}
                finally{Files.deleteIfExists(done);Files.deleteIfExists(Path.of("target","ui-smoke-ready.json"));}
            }
        } finally {
            scheduler.stopAndAwait();sandbox.shutdown();
            if(owned && schema.matches("transport_test_[a-f0-9]{16}")) try(Connection c=DriverManager.getConnection(url,user,password);Statement s=c.createStatement()) { s.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
    @org.springframework.boot.test.mock.mockito.MockBean AmapRouteService routes;
    @Autowired Database database;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderDao orders;
    @Autowired OrderService orderService;
    @Autowired VehicleService vehicleService;
    @Autowired DispatchService dispatch;
    @Autowired SandboxService sandbox;
    @Autowired SandboxStore store;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired LiveStatsService liveStats;
    @Autowired CycleScheduler scheduler;
    @Autowired LiveEventService liveEvents;
    @org.springframework.boot.test.web.server.LocalServerPort int httpPort;
    @Test void failedCompositeMutationRollsBack() {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM vehicle",Integer.class);
        assertThrows(IllegalStateException.class,()->database.write(()->{
            vehicleService.initVehicles(2); throw new IllegalStateException("injected rollback");
        }));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM vehicle",Integer.class));
    }
    @Test void concurrentAllocationCannotDoubleDebit() throws Exception {
        database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);});
        long id=jdbc.queryForObject("SELECT id FROM transport_order LIMIT 1",Long.class);
        jdbc.update("UPDATE transport_order SET cargo_total_weight=10,pending_weight=10,allocated_weight=0,status='PENDING' WHERE id=?",id);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(executor.submit(()->{
                try {database.write(()->{try(Connection c=database.open()){orders.allocate(c,id,new BigDecimal("8"));}catch(SQLException e){throw new RuntimeException(e);}});return true;}
                catch(IllegalStateException e){return false;}
            }));
            assertEquals(1,futures.stream().filter(f->{try{return f.get(10,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}}).count());
            assertEquals(new BigDecimal("2.00"),jdbc.queryForObject("SELECT pending_weight FROM transport_order WHERE id=?",BigDecimal.class,id));
            assertEquals(new BigDecimal("8.00"),jdbc.queryForObject("SELECT allocated_weight FROM transport_order WHERE id=?",BigDecimal.class,id));
        } finally {executor.shutdownNow();}
    }
    @Test void liveTasksFinishOnlyWhenSimulationClockReachesFinish() {
        database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);});
        // DATETIME columns have second precision; start after both records have arrived.
        var time=java.time.LocalDateTime.now().plusSeconds(2).withNano(0);
        var tasks=dispatch.runCycleRound(time,"BASELINE"); assertFalse(tasks.isEmpty());
        assertEquals("RUNNING",tasks.get(0).getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_task WHERE status='DONE'",Integer.class));
        dispatch.runCycleRound(time.plusDays(30),"BASELINE");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_task WHERE status='DONE'",Integer.class)>0);
    }
    @Test void realRunIsIdempotentReproducibleAndPersistent() throws Exception {
        var e=sandbox.create("Integration",ExperimentConfig.defaults(20),null);String id=(String)e.get("experimentId"),request=UUID.randomUUID().toString();
        ExecutorService executor=Executors.newFixedThreadPool(4); Set<String> ids=new HashSet<>();
        try {
            List<Future<Map<String,Object>>> futures=new ArrayList<>();for(int i=0;i<4;i++)futures.add(executor.submit(()->sandbox.start(id,request,null)));
            for(var f:futures)ids.add((String)f.get(10,TimeUnit.SECONDS).get("runId"));assertEquals(1,ids.size());
        } finally {executor.shutdownNow();}
        String runId=ids.iterator().next();Map<String,Object> first=await(runId);
        assertEquals("COMPLETED",first.get("status"),()->String.valueOf(first.get("error")));
        assertEquals("BACKEND",first.get("source"));assertNotNull(first.get("metrics"));
        assertEquals("ALL_ORDERS",first.get("completionPolicy"));assertEquals(first.get("orderCount"),first.get("completedOrderCount"));
        assertEquals(0,((Number)first.get("pendingOrderCount")).intValue());
        assertEquals(first.get("taskCount"),first.get("completedTaskCount"));
        assertTrue(new BigDecimal(((Map<?,?>)first.get("metrics")).get("totalCompletionTimeH").toString()).signum()>0);
        var next=sandbox.start(id,UUID.randomUUID().toString(),runId);Map<String,Object> second=await((String)next.get("runId"));
        assertNotEquals(runId,second.get("runId"));assertEquals(first.get("config"),second.get("config"));assertEquals(first.get("metrics"),second.get("metrics"));
        assertEquals(first.get("metrics"),store.run(runId).get("metrics"));
        String legacyId="RUN-"+UUID.randomUUID();
        var legacy=new LinkedHashMap<>(first);legacy.put("runId",legacyId);
        legacy.put("config",new ExperimentConfig("map",20,24,50,10,20260922L,"BASELINE",Map.of()));
        legacy.put("modelId",dispatch.modelId());legacy.remove("completionPolicy");legacy.remove("playbackAvailable");
        store.insertRun(legacy,UUID.randomUUID().toString(),"legacy-test");
        assertThrows(IllegalStateException.class,()->sandbox.start(id,UUID.randomUUID().toString(),legacyId));
    }
    @Test void deletingEmptyExperimentKeepsItsCopyAndSharedEnvironment() throws Exception {
        var original=sandbox.create("删除空方案",ExperimentConfig.defaults(20),null);
        String id=(String)original.get("experimentId");
        var copy=sandbox.create("保留副本",ExperimentConfig.defaults(20),id);
        assertEquals(200,mvc.perform(delete("/sandbox/experiments/"+id).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("version",original.get("version"))))).andReturn().getResponse().getStatus());
        assertThrows(IllegalArgumentException.class,()->store.experiment(id));
        assertEquals(copy.get("name"),store.experiment((String)copy.get("experimentId")).get("name"));
        assertFalse(store.environment((String)copy.get("environmentId")).isEmpty());
        assertEquals(400,mvc.perform(delete("/sandbox/experiments/"+id).contentType("application/json")
                .content("{\"version\":1}")).andReturn().getResponse().getStatus());
    }
    @Test void deletingFinishedExperimentRemovesAllItsHistoryOnly() throws Exception {
        var config=new ExperimentConfig("map",20,0,2,1,123L,"BASELINE",Map.of(),4);
        var experiment=sandbox.create("删除历史方案",config,null);
        String id=(String)experiment.get("experimentId");
        var completed=await((String)sandbox.start(id,UUID.randomUUID().toString(),null).get("runId"));
        assertEquals("COMPLETED",completed.get("status"));
        var failed=new LinkedHashMap<>(completed); failed.put("runId","RUN-"+UUID.randomUUID()); failed.put("status","FAILED");
        store.insertRun(failed,UUID.randomUUID().toString(),"delete-test");
        assertTrue(store.updateRun(failed,"delete-test"));
        var other=sandbox.create("其他方案",config,id);
        var otherRun=new LinkedHashMap<>(completed); otherRun.put("runId","RUN-"+UUID.randomUUID()); otherRun.put("experimentId",other.get("experimentId"));otherRun.remove("playbackAvailable");
        store.insertRun(otherRun,UUID.randomUUID().toString(),"delete-test");
        assertTrue(store.updateRun(otherRun,"delete-test"));
        assertEquals(2,sandbox.delete(id,1).get("deletedRunCount"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_run WHERE experiment_id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_playback WHERE run_id=?",Integer.class,completed.get("runId")));
        assertThrows(IllegalArgumentException.class,()->store.run((String)completed.get("runId")));
        assertEquals(otherRun.get("runId"),store.run((String)otherRun.get("runId")).get("runId"));
    }
    @Test void playbackIsPersistedAndServedWithoutChangingLiveScene() throws Exception {
        var beforeVehicles=jdbc.queryForList("SELECT * FROM vehicle ORDER BY id");
        var beforeOrders=jdbc.queryForList("SELECT * FROM transport_order ORDER BY id");
        var config=new ExperimentConfig("map",20,0,3,1,321L,"WEIGHTED",Map.of("WEATHER",true),12,ObjectiveWeights.defaults());
        var experiment=sandbox.create("回放持久化验证",config,null);
        var run=await((String)sandbox.start((String)experiment.get("experimentId"),UUID.randomUUID().toString(),null).get("runId"));
        assertEquals("COMPLETED",run.get("status"));assertEquals(true,run.get("playbackAvailable"));
        var playback=store.playback((String)run.get("runId"));
        assertEquals(run.get("taskCount"),((List<?>)playback.get("tasks")).size());
        assertEquals(3,((List<?>)playback.get("vehicles")).size());assertEquals(12,((List<?>)playback.get("orders")).size());
        var response=mvc.perform(get("/sandbox/runs/"+run.get("runId")+"/playback")).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        var body=mapper.readValue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8),Map.class);
        assertEquals(playback,body.get("playback"));assertNotNull(body.get("pois"));
        assertEquals(beforeVehicles,jdbc.queryForList("SELECT * FROM vehicle ORDER BY id"));
        assertEquals(beforeOrders,jdbc.queryForList("SELECT * FROM transport_order ORDER BY id"));
        var legacy=new LinkedHashMap<>(run);legacy.put("runId","RUN-"+UUID.randomUUID());legacy.remove("playbackAvailable");
        store.insertRun(legacy,UUID.randomUUID().toString(),"legacy-playback");store.updateRun(legacy,"legacy-playback");
        assertEquals(409,mvc.perform(get("/sandbox/runs/"+legacy.get("runId")+"/playback")).andReturn().getResponse().getStatus());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_playback WHERE run_id=?",Integer.class,legacy.get("runId")));
    }
    @Test void deletionRejectsStaleVersionAndActiveRunsWithoutRemovingAnything() throws Exception {
        var experiment=sandbox.create("保护方案",ExperimentConfig.defaults(20),null);
        String id=(String)experiment.get("experimentId");
        sandbox.update(id,"其他页面已修改",ExperimentConfig.defaults(20),1);
        assertEquals(409,mvc.perform(delete("/sandbox/experiments/"+id).contentType("application/json")
                .content("{\"version\":1}")).andReturn().getResponse().getStatus());
        for(String status:List.of("QUEUED","RUNNING")) {
            String runId="RUN-"+UUID.randomUUID();
            var run=Map.<String,Object>of("runId",runId,"experimentId",id,"environmentId",experiment.get("environmentId"),"status",status,
                    "config",ExperimentConfig.defaults(20),"events",List.of(),"startedAt",System.currentTimeMillis());
            store.insertRun(run,UUID.randomUUID().toString(),"delete-test");
            assertTrue(store.updateRun(run,"delete-test"));
            var response=mvc.perform(delete("/sandbox/experiments/"+id).contentType("application/json").content("{\"version\":2}")).andReturn().getResponse();
            assertEquals(409,response.getStatus()); assertTrue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("排队或运行中"));
            assertEquals(status,store.run(runId).get("status")); assertNotNull(store.experiment(id));
            jdbc.update("UPDATE sandbox_run SET status='FAILED' WHERE run_id=?",runId);
        }
        assertEquals(2,sandbox.delete(id,2).get("deletedRunCount"));
    }
    @Test void concurrentDeletionAndRunSubmissionCannotCreateOrphanedHistory() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            for(int attempt=0;attempt<6;attempt++) {
                var experiment=sandbox.create("并发删除验证",ExperimentConfig.defaults(20),null);
                String id=(String)experiment.get("experimentId"),runId="RUN-"+UUID.randomUUID();
                var run=Map.<String,Object>of("runId",runId,"experimentId",id,"environmentId",experiment.get("environmentId"),"status","QUEUED",
                        "config",ExperimentConfig.defaults(20),"events",List.of(),"startedAt",System.currentTimeMillis());
                CountDownLatch start=new CountDownLatch(1);
                Future<Boolean> inserted=executor.submit(()->{start.await();try{store.insertRun(run,UUID.randomUUID().toString(),"delete-test");return true;}catch(IllegalArgumentException expected){return false;}});
                Future<Boolean> deleted=executor.submit(()->{start.await();try{sandbox.delete(id,1);return true;}catch(IllegalStateException expected){return false;}});
                start.countDown(); boolean hasRun=inserted.get(10,TimeUnit.SECONDS),removed=deleted.get(10,TimeUnit.SECONDS);
                assertNotEquals(hasRun,removed);
                assertEquals(hasRun?1:0,jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_run WHERE experiment_id=?",Integer.class,id));
                assertEquals(hasRun?1:0,jdbc.queryForObject("SELECT COUNT(*) FROM sandbox_experiment WHERE experiment_id=?",Integer.class,id));
            }
        } finally {executor.shutdownNow();}
    }
    @Test void weightedRunFreezesPreferencesPersistsAuditAndReplaysExactly() throws Exception {
        var weights=new ObjectiveWeights(25,25,20,15,15);
        var config=new ExperimentConfig("map",20,0,3,1,123L,"WEIGHTED",Map.of("WEATHER",true),12,weights);
        var experiment=sandbox.create("Weighted integration",config,null);
        String id=(String)experiment.get("experimentId");
        assertEquals(weights,store.config(experiment.get("config")).objectiveWeights());
        var first=await((String)sandbox.start(id,UUID.randomUUID().toString(),null).get("runId"));
        assertEquals("COMPLETED",first.get("status"),()->String.valueOf(first.get("error")));
        Map<?,?> audit=(Map<?,?>)first.get("optimization");
        assertEquals("LINEAR_WEIGHTED_SUM",audit.get("method"));
        assertTrue(((Number)audit.get("decisionCount")).intValue()>0);
        assertFalse(((List<?>)audit.get("decisionSamples")).isEmpty());
        var second=await((String)sandbox.start(id,UUID.randomUUID().toString(),(String)first.get("runId")).get("runId"));
        assertEquals("COMPLETED",second.get("status"));
        assertEquals(first.get("metrics"),second.get("metrics"));assertEquals(first.get("optimization"),second.get("optimization"));
        var changed=new ExperimentConfig("map",20,0,3,1,123L,"WEIGHTED",Map.of("WEATHER",true),12,new ObjectiveWeights(0,1,0,0,0));
        sandbox.update(id,"Changed preferences",changed,((Number)experiment.get("version")).longValue());
        assertEquals(weights,store.config(store.run((String)first.get("runId")).get("config")).objectiveWeights());
        // Copying freezes the same scene and preserves weights; a stale editor cannot overwrite the saved version.
        var copy=sandbox.create("Weighted copy",config,id);
        assertEquals(experiment.get("environmentId"),copy.get("environmentId"));
        assertEquals(weights,store.config(copy.get("config")).objectiveWeights());
        assertThrows(IllegalStateException.class,()->sandbox.update(id,"Stale edit",config,1));
        for(var invalid:List.of(Map.of("waiting",-1,"emptyDistance",1),Map.of("waiting",0,"emptyDistance",0))) {
            var body=new LinkedHashMap<>(mapper.convertValue(config,Map.class));body.put("objectiveWeights",invalid);
            assertEquals(400,mvc.perform(post("/sandbox/experiments").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("name","Invalid weights","config",body)))).andReturn().getResponse().getStatus());
        }
    }

    Map<String,Object> await(String id) throws Exception {
        for(int i=0;i<200;i++){Map<String,Object> run=store.run(id);if(!List.of("QUEUED","RUNNING").contains(run.get("status")))return run;Thread.sleep(50);}
        fail("Run timeout");return null;
    }
    @Test void invalidRequestsReturn400() throws Exception {
        var request=Map.of("name","Invalid","config",Map.of("simulationDuration",24,"vehicleCount",0,"orderGenerationInterval",10,"randomSeed",12,"algorithm","BASELINE"));
        assertEquals(400,mvc.perform(post("/sandbox/experiments").contentType("application/json").content(mapper.writeValueAsString(request))).andReturn().getResponse().getStatus());
        assertEquals(400,mvc.perform(get("/dispatch/tasks").param("limit","1000000")).andReturn().getResponse().getStatus());
    }

    @Test void realTimeStatisticsAreCalculatedPersistedAndRecoveredByScene() throws Exception {
        scheduler.reset(()->database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);}));
        var before=liveStats.capture(); String scene=(String)before.get("sceneId");
        var again=liveStats.capture(); assertEquals(before.get("sampleId"),again.get("sampleId"));
        dispatch.runCycleRound(java.time.LocalDateTime.now().plusSeconds(2),"BASELINE");
        var assigned=liveStats.capture(); assertNotEquals(before.get("sampleId"),assigned.get("sampleId"));
        dispatch.runCycleRound(java.time.LocalDateTime.now().plusDays(30),"BASELINE");
        var finished=liveStats.capture(); Map<?,?> metrics=(Map<?,?>)finished.get("metrics");
        assertTrue(new BigDecimal(metrics.get("completedTonnage").toString()).signum()>0);
        assertEquals(3,liveStats.history(scene,120).size());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM live_stats_sample WHERE scene_id=?",Integer.class,scene));
        liveStats.initialize(); assertEquals(scene,scheduler.sceneId());
        assertEquals(200,mvc.perform(get("/stats/live")).andReturn().getResponse().getStatus());
        scheduler.reset(()->{});liveStats.capture();assertNotEquals(scene,scheduler.sceneId());
        assertEquals(3,liveStats.history(scene,120).size());
        assertEquals(1,liveStats.history(scheduler.sceneId(),120).size());
    }

    @Test void resettingOneSideCannotLeaveOrphanedAllocationOrBusyVehicles() {
        database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);});
        assertFalse(dispatch.runCycleRound(java.time.LocalDateTime.now().plusSeconds(2),"BASELINE").isEmpty());
        assertTrue(jdbc.queryForObject("SELECT SUM(allocated_weight)>0 FROM transport_order",Boolean.class));
        vehicleService.initVehicles(2);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM transport_order WHERE allocated_weight<>0 OR status<>'PENDING'",Integer.class));
        dispatch.runCycleRound(java.time.LocalDateTime.now().plusSeconds(2),"BASELINE");
        orderService.generateOrders(1);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE status<>'IDLE' OR total_orders<>0",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_task",Integer.class));
    }

    @Test void disruptionDelaysTasksOnceAndSurvivesAsSceneHistory() throws Exception {
        scheduler.reset(()->database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);}));
        var time=java.time.LocalDateTime.now().plusSeconds(2).withNano(0);
        var tasks=dispatch.runCycleRound(time,"BASELINE");assertFalse(tasks.isEmpty());
        long vehicle=tasks.get(0).getVehicleId(),task=tasks.get(0).getId()==null?jdbc.queryForObject("SELECT MIN(id) FROM dispatch_task WHERE vehicle_id=?",Long.class,vehicle):tasks.get(0).getId();
        var finish=jdbc.queryForObject("SELECT finished_at FROM dispatch_task WHERE id=?",java.sql.Timestamp.class,task).toLocalDateTime();
        var wait=jdbc.queryForObject("SELECT vehicle_wait_time_h FROM dispatch_task WHERE id=?",BigDecimal.class,task);
        var profit=jdbc.queryForObject("SELECT total_profit FROM vehicle WHERE id=?",BigDecimal.class,vehicle);
        var request=new LiveEventService.Request("VEHICLE_FAULT",vehicle,10,15,UUID.randomUUID().toString());
        var event=liveEvents.create(scheduler.sceneId(),time,request);
        assertEquals("ACTIVE",event.get("status"));assertEquals("SIMULATION",event.get("source"));
        assertEquals(finish.plusMinutes(15),jdbc.queryForObject("SELECT finished_at FROM dispatch_task WHERE id=?",java.sql.Timestamp.class,task).toLocalDateTime());
        assertEquals(wait.add(new BigDecimal("0.25")),jdbc.queryForObject("SELECT vehicle_wait_time_h FROM dispatch_task WHERE id=?",BigDecimal.class,task));
        assertTrue(jdbc.queryForObject("SELECT total_profit FROM vehicle WHERE id=?",BigDecimal.class,vehicle).compareTo(profit)<0);
        assertEquals(event.get("eventId"),liveEvents.create(scheduler.sceneId(),time.plusMinutes(1),request).get("eventId"));
        assertEquals(finish.plusMinutes(15),jdbc.queryForObject("SELECT finished_at FROM dispatch_task WHERE id=?",java.sql.Timestamp.class,task).toLocalDateTime());
        assertEquals(0,liveEvents.activeCount(scheduler.sceneId(),time.plusMinutes(10)));
        assertEquals("ENDED",liveEvents.history(scheduler.sceneId(),time.plusMinutes(10)).get(0).get("status"));
        assertThrows(IllegalStateException.class,()->liveEvents.create(scheduler.sceneId(),time,new LiveEventService.Request("WEATHER",null,10,15,request.requestKey())));
        assertEquals(200,mvc.perform(get("/dispatch/events")).andReturn().getResponse().getStatus());
        scheduler.reset(()->{});assertTrue(liveEvents.history(scheduler.sceneId(),time).isEmpty());
    }

    @Test void weatherHoldsIdleFleetUntilEventEndAndInvalidEventIsRejected() throws Exception {
        scheduler.reset(()->database.write(()->{vehicleService.initVehicles(2);orderService.generateOrders(1);}));
        var now=scheduler.currentTime();
        var event=liveEvents.create(scheduler.sceneId(),now,new LiveEventService.Request("WEATHER",null,30,15,UUID.randomUUID().toString()));
        assertEquals(2,((List<?>)event.get("affectedVehicleIds")).size());
        assertTrue(dispatch.runCycleRound(now.plusMinutes(1),"BASELINE").isEmpty());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE status='BUSY'",Integer.class));
        assertFalse(dispatch.runCycleRound(now.plusMinutes(30),"BASELINE").isEmpty());
        assertEquals(0,liveEvents.activeCount(scheduler.sceneId(),now.plusMinutes(30)));
        assertEquals(400,mvc.perform(post("/dispatch/events").contentType("application/json").content(mapper.writeValueAsString(new LiveEventService.Request("WEATHER",null,10000,15,UUID.randomUUID().toString())))).andReturn().getResponse().getStatus());
    }
}
