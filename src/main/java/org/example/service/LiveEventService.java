package org.example.service;

import org.example.config.CostConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

/** Explicit simulation disruptions. Effects and the event record commit atomically. */
@Service
public class LiveEventService {
    public record Request(String eventType, Long vehicleId, int durationMinutes, int delayMinutes, String requestKey) {}
    private final Database database;
    private final JdbcTemplate jdbc;
    private final CostConfig cost;
    public LiveEventService(Database database,JdbcTemplate jdbc,CostConfig cost) {this.database=database;this.jdbc=jdbc;this.cost=cost;}
    @jakarta.annotation.PostConstruct public void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS live_simulation_event (
              id BIGINT AUTO_INCREMENT PRIMARY KEY, request_key VARCHAR(64) NOT NULL UNIQUE,
              scene_id VARCHAR(64) NOT NULL, event_type VARCHAR(32) NOT NULL, vehicle_id BIGINT,
              started_at DATETIME NOT NULL, ends_at DATETIME NOT NULL,
              duration_minutes INT NOT NULL, delay_minutes INT NOT NULL,
              affected_vehicle_ids TEXT NOT NULL, affected_tasks INT NOT NULL,
              INDEX scene_events(scene_id,id))
            """);
    }
    public Map<String,Object> create(String scene,LocalDateTime now,Request request) {
        if(request==null || !Set.of("TRAFFIC_JAM","WEATHER","VEHICLE_FAULT").contains(request.eventType()==null?"":request.eventType())) throw new IllegalArgumentException("请选择拥堵、天气或车辆故障");
        if(request.durationMinutes()<1||request.durationMinutes()>120||request.delayMinutes()<1||request.delayMinutes()>120)throw new IllegalArgumentException("持续时间和延误须为1～120分钟");
        if(request.requestKey()==null||!request.requestKey().matches("[A-Za-z0-9-]{8,64}"))throw new IllegalArgumentException("事件请求标识无效");
        if(!"WEATHER".equals(request.eventType()) && (request.vehicleId()==null||request.vehicleId()<1))throw new IllegalArgumentException("请选择受影响车辆");
        if("WEATHER".equals(request.eventType()) && request.vehicleId()!=null)throw new IllegalArgumentException("天气事件影响全部车辆，无需选择单车");
        return database.write(()->{
            List<Map<String,Object>> prior=jdbc.queryForList("SELECT * FROM live_simulation_event WHERE request_key=?",request.requestKey());
            if(!prior.isEmpty()) {
                Map<String,Object> row=prior.get(0);
                if(!scene.equals(row.get("scene_id"))||!request.eventType().equals(row.get("event_type"))||!Objects.equals(request.vehicleId(),row.get("vehicle_id"))||((Number)row.get("duration_minutes")).intValue()!=request.durationMinutes()||((Number)row.get("delay_minutes")).intValue()!=request.delayMinutes())throw new IllegalStateException("同一事件请求标识不能用于不同参数");
                return describe(row,now);
            }
            if(jdbc.queryForObject("SELECT COUNT(*) FROM live_simulation_event WHERE scene_id=?",Integer.class,scene)>=200)throw new IllegalStateException("本场景已达到200个事件，请使用新场景");
            List<Map<String,Object>> fleet=request.vehicleId()==null ? jdbc.queryForList("SELECT id,available_at,wear_cost_per_hour FROM vehicle ORDER BY id") : jdbc.queryForList("SELECT id,available_at,wear_cost_per_hour FROM vehicle WHERE id=?",request.vehicleId());
            if(fleet.isEmpty())throw new IllegalArgumentException("没有可受影响的车辆");
            LocalDateTime end=now.plusMinutes(request.durationMinutes());
            BigDecimal hours=BigDecimal.valueOf(request.delayMinutes()).divide(BigDecimal.valueOf(60),8,RoundingMode.HALF_UP);
            List<Long> ids=new ArrayList<>(); int affectedTasks=0;
            for(Map<String,Object> vehicle:fleet) {
                long id=((Number)vehicle.get("id")).longValue();ids.add(id);
                List<Map<String,Object>> tasks=jdbc.queryForList("SELECT id,assigned_weight,cargo_wait_time_h,cost2 FROM dispatch_task WHERE vehicle_id=? AND status='RUNNING' AND finished_at>? ORDER BY started_at,id LIMIT 1",id,now);
                if(!tasks.isEmpty()) {
                    Map<String,Object> first=tasks.get(0);
                    BigDecimal cargoWait=((BigDecimal)first.get("cargo_wait_time_h")).add(hours);
                    BigDecimal newLoss=BigDecimal.valueOf(cost.calcCost2(((BigDecimal)first.get("assigned_weight")).doubleValue(),cargoWait.doubleValue())).setScale(2,RoundingMode.HALF_UP);
                    BigDecimal wear=((BigDecimal)vehicle.get("wear_cost_per_hour")).multiply(hours).setScale(2,RoundingMode.HALF_UP);
                    BigDecimal difference=wear.add(newLoss.subtract((BigDecimal)first.get("cost2")));
                    // Count one disruption per vehicle, not once per queued task.
                    jdbc.update("UPDATE dispatch_task SET pickup_time_h=pickup_time_h+?,vehicle_wait_time_h=vehicle_wait_time_h+?,cargo_wait_time_h=?,cost1=cost1+?,cost2=?,total_cost=total_cost+?,net_profit=net_profit-?,score=score-? WHERE id=?",hours,hours,cargoWait,wear,newLoss,difference,difference,difference,first.get("id"));
                    affectedTasks+=jdbc.update("UPDATE dispatch_task SET finished_at=DATE_ADD(finished_at,INTERVAL ? MINUTE),started_at=CASE WHEN started_at>? THEN DATE_ADD(started_at,INTERVAL ? MINUTE) ELSE started_at END WHERE vehicle_id=? AND status='RUNNING' AND finished_at>?",request.delayMinutes(),now,request.delayMinutes(),id,now);
                    jdbc.update("UPDATE vehicle SET total_cost=total_cost+?,total_profit=total_profit-? WHERE id=?",difference,difference,id);
                }
                Object available=vehicle.get("available_at");
                LocalDateTime until=tasks.isEmpty()?end:(available==null?now:time(available)).plusMinutes(request.delayMinutes());
                if(until.isBefore(end))until=end;
                // A busy vehicle may already have a later committed arrival time.
                jdbc.update("UPDATE vehicle SET status='BUSY',available_at=GREATEST(COALESCE(available_at,?),?) WHERE id=?",until,until,id);
            }
            String affected=ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            jdbc.update("INSERT INTO live_simulation_event(request_key,scene_id,event_type,vehicle_id,started_at,ends_at,duration_minutes,delay_minutes,affected_vehicle_ids,affected_tasks) VALUES (?,?,?,?,?,?,?,?,?,?)",request.requestKey(),scene,request.eventType(),request.vehicleId(),now,end,request.durationMinutes(),request.delayMinutes(),affected,affectedTasks);
            return describe(jdbc.queryForList("SELECT * FROM live_simulation_event WHERE request_key=?",request.requestKey()).get(0),now);
        });
    }
    public List<Map<String,Object>> history(String scene,LocalDateTime now) {
        return jdbc.queryForList("SELECT * FROM live_simulation_event WHERE scene_id=? ORDER BY id DESC LIMIT 50",scene).stream().map(row->describe(row,now)).toList();
    }
    public int activeCount(String scene,LocalDateTime now) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM live_simulation_event WHERE scene_id=? AND started_at<=? AND ends_at>?",Integer.class,scene,now,now);
    }
    private Map<String,Object> describe(Map<String,Object> row,LocalDateTime now) {
        LocalDateTime start=time(row.get("started_at")),end=time(row.get("ends_at"));
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("eventId",row.get("id"));event.put("sceneId",row.get("scene_id"));event.put("eventType",row.get("event_type"));event.put("vehicleId",row.get("vehicle_id"));
        event.put("startTime",start.toString());event.put("endTime",end.toString());event.put("durationMinutes",row.get("duration_minutes"));event.put("delayMinutes",row.get("delay_minutes"));
        event.put("affectedVehicleIds",Arrays.stream(((String)row.get("affected_vehicle_ids")).split(",")).filter(s->!s.isEmpty()).map(Long::valueOf).toList());
        event.put("affectedTaskCount",row.get("affected_tasks"));event.put("status",now.isBefore(start)?"SCHEDULED":now.isBefore(end)?"ACTIVE":"ENDED");event.put("source","SIMULATION");
        return event;
    }
    private LocalDateTime time(Object value) {
        if(value instanceof LocalDateTime date)return date;
        if(value instanceof java.sql.Timestamp stamp)return stamp.toLocalDateTime();
        throw new IllegalStateException("事件时间格式不受支持");
    }
}
