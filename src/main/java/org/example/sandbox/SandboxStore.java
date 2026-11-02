package org.example.sandbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.LocationPoint;
import org.example.service.DaoAdapter;
import org.example.util.Geo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Persistent immutable inputs and outcomes. Lists return bounded summaries, never full scene/task graphs. */
@Repository
public class SandboxStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DaoAdapter locations;
    public SandboxStore(JdbcTemplate jdbc, ObjectMapper mapper, DaoAdapter locations) { this.jdbc = jdbc; this.mapper = mapper; this.locations = locations; }
    @jakarta.annotation.PostConstruct
    public void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sandbox_environment (
                environment_id CHAR(64) PRIMARY KEY, document LONGTEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sandbox_experiment (
                experiment_id VARCHAR(64) PRIMARY KEY, name VARCHAR(60) NOT NULL, config TEXT NOT NULL,
                environment_id CHAR(64) NOT NULL, version BIGINT NOT NULL DEFAULT 1,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS sandbox_run (
                run_id VARCHAR(64) PRIMARY KEY, experiment_id VARCHAR(64) NOT NULL, environment_id CHAR(64) NOT NULL,
                request_key VARCHAR(64) NOT NULL UNIQUE, status VARCHAR(16) NOT NULL, owner_id VARCHAR(64) NOT NULL,
                lease_until TIMESTAMP NOT NULL, document LONGTEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX experiment_history (experiment_id, created_at), INDEX active_lease (status, lease_until))
            """);
    }
    public String json(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("实验数据序列化失败", e); } }
    public Map<String, Object> parse(String value) { try { return mapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>(){}); } catch(Exception e) { throw new IllegalStateException("实验记录读取失败", e); } }
    public ExperimentConfig config(Object value) { return mapper.convertValue(value, ExperimentConfig.class); }
    public Map<String, Object> create(String name, ExperimentConfig config, String existingEnvironment) {
        validateName(name);
        String environment = existingEnvironment;
        if (environment == null) {
            List<LocationPoint> points = locations.loadAllLocations().stream().filter(p -> {
                try { Geo.coordinate(p.getLocation()); return true; } catch (IllegalArgumentException e) { return false; }
            }).sorted(Comparator.comparing(LocationPoint::getId)).limit(10000).toList();
            if (points.size() < 2) throw new IllegalStateException("地图缺少有效POI，无法创建实验");
            String document = json(points); environment = hash(document);
            jdbc.update("INSERT IGNORE INTO sandbox_environment(environment_id,document) VALUES (?,?)", environment, document);
        }
        int count = environment(environment).size();
        config = new ExperimentConfig(config.mapId(), count, config.simulationDuration(), config.vehicleCount(), config.orderGenerationInterval(), config.randomSeed(), config.algorithm(), config.events(),config.orderCount(),config.objectiveWeights());
        String id = "EXP-" + UUID.randomUUID();
        jdbc.update("INSERT INTO sandbox_experiment(experiment_id,name,config,environment_id) VALUES (?,?,?,?)", id, name, json(config), environment);
        return experiment(id);
    }
    public List<LocationPoint> environment(String id) {
        String raw = jdbc.queryForObject("SELECT document FROM sandbox_environment WHERE environment_id=?", String.class, id);
        try { return mapper.readValue(raw, new TypeReference<List<LocationPoint>>(){}); }
        catch(Exception e) { throw new IllegalStateException("环境快照读取失败", e); }
    }
    public Map<String, Object> experiment(String id) {
        List<Map<String,Object>> rows = jdbc.query("SELECT * FROM sandbox_experiment WHERE experiment_id=?", (rs,n) -> {
            Map<String,Object> e = new LinkedHashMap<>(); e.put("experimentId", rs.getString("experiment_id")); e.put("name", rs.getString("name"));
            e.put("config", parse(rs.getString("config"))); e.put("environmentId", rs.getString("environment_id")); e.put("version", rs.getLong("version")); return e;
        }, id);
        if (rows.isEmpty()) throw new IllegalArgumentException("实验方案不存在"); return rows.get(0);
    }
    public Map<String, Object> update(String id, String name, ExperimentConfig config, long version) {
        validateName(name); Map<String,Object> old = experiment(id);
        Map<String,Object> c = new LinkedHashMap<>(mapper.convertValue(config, new TypeReference<Map<String,Object>>(){}));
        c.put("poiCount", ((Map<?,?>)old.get("config")).get("poiCount"));
        if (jdbc.update("UPDATE sandbox_experiment SET name=?,config=?,version=version+1 WHERE experiment_id=? AND version=?", name, json(c), id, version) != 1)
            throw new IllegalStateException("方案已被其他页面修改，请刷新后重试");
        return experiment(id);
    }
    public List<Map<String, Object>> experiments() {
        return jdbc.query("SELECT * FROM sandbox_experiment ORDER BY created_at,experiment_id LIMIT 200", (rs,n) -> {
            Map<String,Object> e = new LinkedHashMap<>(); e.put("experimentId", rs.getString("experiment_id")); e.put("name", rs.getString("name"));
            e.put("config", parse(rs.getString("config"))); e.put("environmentId", rs.getString("environment_id")); e.put("version", rs.getLong("version")); return e;
        });
    }
    public void shutdownOwner(String owner) {
        jdbc.update("UPDATE sandbox_run SET status='FAILED',document=JSON_SET(document,'$.status','FAILED','$.error','服务重启，请创建新的Run重新执行') WHERE owner_id=? AND status IN ('QUEUED','RUNNING')", owner);
    }
    public Map<String, Object> run(String id) {
        List<String> rows = jdbc.query("SELECT document FROM sandbox_run WHERE run_id=?", (rs,n) -> rs.getString(1), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("运行记录不存在"); return parse(rows.get(0));
    }
    public List<Map<String,Object>> runs() {
        return jdbc.query("SELECT document FROM sandbox_run ORDER BY created_at DESC,run_id DESC LIMIT 200", (rs,n) -> parse(rs.getString(1)));
    }
    public String requestRun(String requestKey) {
        List<String> rows = jdbc.query("SELECT run_id FROM sandbox_run WHERE request_key=?", (rs,n) -> rs.getString(1), requestKey);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void insertRun(Map<String,Object> run, String request, String owner) {
        jdbc.update("INSERT INTO sandbox_run(run_id,experiment_id,environment_id,request_key,status,owner_id,lease_until,document) VALUES (?,?,?,?,?,?,DATE_ADD(NOW(),INTERVAL 30 MINUTE),?)",
            run.get("runId"), run.get("experimentId"), run.get("environmentId"), request, "QUEUED", owner, json(run));
    }
    public boolean updateRun(Map<String,Object> run, String owner) {
        return jdbc.update("UPDATE sandbox_run SET status=?,document=?,lease_until=DATE_ADD(NOW(),INTERVAL 4 MINUTE) WHERE run_id=? AND owner_id=? AND status IN ('QUEUED','RUNNING')",
                run.get("status"), json(run), run.get("runId"), owner) == 1;
    }
    public void expireLeases() {
        // MySQL JSON_SET preserves the immutable configuration even after a process crash.
        jdbc.update("""
            UPDATE sandbox_run SET status='FAILED',document=JSON_SET(document,'$.status','FAILED','$.error','执行进程退出或执行租约过期')
            WHERE status IN ('QUEUED','RUNNING') AND lease_until < NOW()
            """);
    }
    private void validateName(String name) { if (name == null || name.isBlank() || name.length() > 60) throw new IllegalArgumentException("方案名称须为1～60字符"); }
    public static String hash(String data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
