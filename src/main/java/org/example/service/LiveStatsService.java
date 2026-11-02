package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.DispatchStatsVO;
import org.example.sandbox.SandboxStore;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

/** Server-side calculation and durable trend samples, independent of open browser tabs. */
@Service
public class LiveStatsService {
    private final Database database;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final StatsService stats;
    private final CycleScheduler cycle;
    private final LiveEventService events;
    @Value("${stats.sampling.enabled:true}") private boolean enabled;
    public LiveStatsService(Database database, JdbcTemplate jdbc, ObjectMapper mapper, StatsService stats, CycleScheduler cycle, LiveEventService events) {
        this.database=database;this.jdbc=jdbc;this.mapper=mapper;this.stats=stats;this.cycle=cycle;
        this.events=events;
    }
    @jakarta.annotation.PostConstruct public void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS live_stats_sample (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, sample_key CHAR(64) NOT NULL UNIQUE,
              scene_id VARCHAR(64) NOT NULL, simulation_time DATETIME NOT NULL,
              captured_at DATETIME(6) NOT NULL, document TEXT NOT NULL,
              INDEX scene_history(scene_id,id))
            """);
        List<String> previous=jdbc.query("SELECT document FROM live_stats_sample ORDER BY id DESC LIMIT 1",(rs,n)->rs.getString(1));
        if(!previous.isEmpty()) {
            Map<String,Object> last=parse(previous.get(0));
            cycle.restoreSession((String)last.get("sceneId"),LocalDateTime.parse((String)last.get("simulationTime")));
            if(last.get("cycle") instanceof Map<?,?> state)cycle.restoreCounters(String.valueOf(state.get("mode")),((Number)state.get("totalTasksGenerated")).longValue(),((Number)state.get("lastRoundTasks")).longValue());
        } else cycle.restoreSession(cycle.sceneId(),LocalDateTime.now().withNano(0));
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${stats.sampling.interval-ms:3000}")
    public void samplePeriodically() {
        if(!enabled) return;
        try { capture(); } catch(Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Live statistics sample failed",e); }
    }
    public Map<String,Object> capture() {
        synchronized(cycle) {
        return database.write(()->{
            try(var conn=database.open()) {
                DispatchStatsVO overview=stats.overview(conn);
                CycleScheduler.CycleStatus status=cycle.getStatus();
                String time=status.getSimTime().isBlank()?LocalDateTime.now().withNano(0).toString():status.getSimTime();
                Map<String,Object> metrics=new LinkedHashMap<>();
                metrics.put("totalWaitTime",overview.getTotalWaitTime()); metrics.put("totalEmptyDistance",overview.getTotalEmptyMileageKm());
                BigDecimal totalKm=overview.getTotalEmptyMileageKm().add(overview.getTotalLoadedMileageKm());
                metrics.put("vehicleUtilization",totalKm.signum()==0?BigDecimal.ZERO:overview.getTotalLoadedMileageKm().multiply(BigDecimal.valueOf(100)).divide(totalKm,2,RoundingMode.HALF_UP));
                metrics.put("completedTonnage",overview.getCompletedTonnage()); metrics.put("totalCarbonEmission",overview.getTotalCarbonEmission());
                metrics.put("runningVehicleCount",overview.getVehicleCount()-overview.getIdleVehicleCount());metrics.put("totalVehicleCount",overview.getVehicleCount());
                metrics.put("pendingOrderCount",overview.getOrderCount()-overview.getDoneOrderCount());
                metrics.put("orderCompletionRate",overview.getOrderCount()==0?BigDecimal.ZERO:BigDecimal.valueOf(overview.getDoneOrderCount()*100L).divide(BigDecimal.valueOf(overview.getOrderCount()),2,RoundingMode.HALF_UP));
                metrics.put("simulationTime",time);
                metrics.put("activeEventCount",events.activeCount(cycle.sceneId(),LocalDateTime.parse(time)));
                Map<String,Object> sample=new LinkedHashMap<>();sample.put("sceneId",cycle.sceneId());sample.put("simulationTime",time);sample.put("stats",overview);sample.put("metrics",metrics);
                sample.put("cycle",Map.of("running",status.isRunning(),"mode",status.getMode(),"simTime",time,"totalTasksGenerated",status.getTotalTasksGenerated(),"lastRoundTasks",status.getLastRoundTasks()));
                String key=SandboxStore.hash(json(sample));
                sample.put("capturedAt",LocalDateTime.now().toString());
                jdbc.update("INSERT INTO live_stats_sample(sample_key,scene_id,simulation_time,captured_at,document) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE sample_key=VALUES(sample_key)",key,sample.get("sceneId"),LocalDateTime.parse(time),LocalDateTime.now(),json(sample));
                String document=jdbc.queryForObject("SELECT document FROM live_stats_sample WHERE sample_key=?",String.class,key);
                Map<String,Object> persisted=parse(document);persisted.put("sampleId",jdbc.queryForObject("SELECT id FROM live_stats_sample WHERE sample_key=?",Long.class,key));
                return persisted;
            } catch(java.sql.SQLException e) { throw new IllegalStateException("实时统计保存失败",e); }
        });
        }
    }
    public List<Map<String,Object>> history(String sceneId,int limit) {
        if(limit<1||limit>500) throw new IllegalArgumentException("历史采样数量须为1～500");
        List<Map<String,Object>> rows=jdbc.query("SELECT id,document FROM live_stats_sample WHERE scene_id=? ORDER BY id DESC LIMIT ?",(rs,n)->{
            Map<String,Object> row=parse(rs.getString("document"));row.put("sampleId",rs.getLong("id"));return row;
        },sceneId,limit);Collections.reverse(rows);return rows;
    }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("实时统计序列化失败",e);} }
    private Map<String,Object> parse(String value) { try{return mapper.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException("实时统计读取失败",e);} }
}
