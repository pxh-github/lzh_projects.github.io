package org.example.dao;

import org.example.entity.DispatchTask;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DispatchTaskDao {

    public void completeAvailable(Connection conn, java.time.LocalDateTime time) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("UPDATE dispatch_task SET status='DONE' WHERE status='RUNNING' AND finished_at <= ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(time)); statement.executeUpdate();
        }
        try (PreparedStatement statement = conn.prepareStatement("""
            UPDATE transport_order o SET status='DONE', finished_at=?
            WHERE pending_weight=0 AND status='ASSIGNED'
            AND NOT EXISTS (SELECT 1 FROM dispatch_task t WHERE t.order_id=o.id AND t.status='RUNNING')
            """)) { statement.setTimestamp(1, Timestamp.valueOf(time)); statement.executeUpdate(); }
    }

    public void clear(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM dispatch_task");
        }
    }

    public void save(Connection conn, DispatchTask t) throws SQLException {
        String sql = """
            INSERT INTO dispatch_task(
                task_code, order_id, vehicle_id, assigned_weight,
                pickup_distance_km, pickup_time_h, transport_distance_km, transport_time_h,
                vehicle_wait_time_h, cargo_wait_time_h,
                cost1, cost2, cost3, total_cost, total_revenue, net_profit, score,
                route_polyline, status, started_at, finished_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getTaskCode());
            ps.setLong(2, t.getOrderId());
            ps.setLong(3, t.getVehicleId());
            ps.setBigDecimal(4, t.getAssignedWeight());
            ps.setBigDecimal(5, t.getPickupDistanceKm());
            ps.setBigDecimal(6, t.getPickupTimeH());
            ps.setBigDecimal(7, t.getTransportDistanceKm());
            ps.setBigDecimal(8, t.getTransportTimeH());
            ps.setBigDecimal(9, t.getVehicleWaitTimeH());
            ps.setBigDecimal(10, t.getCargoWaitTimeH());
            ps.setBigDecimal(11, t.getCost1());
            ps.setBigDecimal(12, t.getCost2());
            ps.setBigDecimal(13, t.getCost3());
            ps.setBigDecimal(14, t.getTotalCost());
            ps.setBigDecimal(15, t.getTotalRevenue());
            ps.setBigDecimal(16, t.getNetProfit());
            ps.setBigDecimal(17, t.getScore());
            ps.setString(18, t.getRoutePolyline());
            ps.setString(19, t.getStatus());
            ps.setTimestamp(20, Timestamp.valueOf(t.getSimulationStartedAt()));
            ps.setTimestamp(21, Timestamp.valueOf(t.getSimulationFinishedAt()));
            ps.executeUpdate();
        }
    }

    public List<DispatchTask> findAll(Connection conn) throws SQLException { return findAll(conn, -1, 1000, true); }
    public List<DispatchTask> findAll(Connection conn, long afterId, int limit, boolean includeRoute) throws SQLException {
        List<DispatchTask> list = new ArrayList<>();
        String columns = "id,task_code,order_id,vehicle_id,assigned_weight,pickup_distance_km,pickup_time_h,transport_distance_km,transport_time_h,vehicle_wait_time_h,cargo_wait_time_h,cost1,cost2,cost3,total_cost,total_revenue,net_profit,score,status,started_at,finished_at," + (includeRoute ? "route_polyline" : "NULL AS route_polyline");
        String sql = "SELECT " + columns + " FROM dispatch_task WHERE id>? ORDER BY id " + (afterId >= 0 ? "ASC" : "DESC") + " LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, afterId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DispatchTask t = new DispatchTask();
                t.setId(rs.getLong("id"));
                t.setTaskCode(rs.getString("task_code"));
                t.setOrderId(rs.getLong("order_id"));
                t.setVehicleId(rs.getLong("vehicle_id"));
                t.setAssignedWeight(rs.getBigDecimal("assigned_weight"));
                t.setPickupDistanceKm(rs.getBigDecimal("pickup_distance_km"));
                t.setPickupTimeH(rs.getBigDecimal("pickup_time_h"));
                t.setTransportDistanceKm(rs.getBigDecimal("transport_distance_km"));
                t.setTransportTimeH(rs.getBigDecimal("transport_time_h"));
                t.setVehicleWaitTimeH(rs.getBigDecimal("vehicle_wait_time_h"));
                t.setCargoWaitTimeH(rs.getBigDecimal("cargo_wait_time_h"));
                t.setCost1(rs.getBigDecimal("cost1"));
                t.setCost2(rs.getBigDecimal("cost2"));
                t.setCost3(rs.getBigDecimal("cost3"));
                t.setTotalCost(rs.getBigDecimal("total_cost"));
                t.setTotalRevenue(rs.getBigDecimal("total_revenue"));
                t.setNetProfit(rs.getBigDecimal("net_profit"));
                t.setScore(rs.getBigDecimal("score"));
                t.setRoutePolyline(rs.getString("route_polyline"));
                t.setStatus(rs.getString("status"));
                Timestamp start = rs.getTimestamp("started_at"), finish = rs.getTimestamp("finished_at");
                t.setSimulationStartedAt(start == null ? null : start.toLocalDateTime());
                t.setSimulationFinishedAt(finish == null ? null : finish.toLocalDateTime());
                list.add(t);
            }
        }
        }
        return list;
    }
}