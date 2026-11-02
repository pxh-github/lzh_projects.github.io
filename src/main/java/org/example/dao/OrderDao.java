package org.example.dao;

import org.example.entity.OrderEntity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderDao {

    public void clear(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM transport_order");
        }
    }
    public void deleteById(Connection conn, Long orderId) throws SQLException {
        String sql = "DELETE FROM transport_order WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }
    public void save(Connection conn, OrderEntity o) throws SQLException {
        String sql = """
            INSERT INTO transport_order(
                order_code, cargo_type, cargo_total_weight, allocated_weight, pending_weight,
                pickup_location_id, pickup_location_name, pickup_location,
                delivery_location_id, delivery_location_name, delivery_location,
                route_distance_km, route_duration_h, revenue_per_ton_km, total_revenue, status, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, o.getOrderCode());
            ps.setString(2, o.getCargoType());
            ps.setBigDecimal(3, o.getCargoTotalWeight());
            ps.setBigDecimal(4, o.getAllocatedWeight());
            ps.setBigDecimal(5, o.getPendingWeight());
            ps.setString(6, o.getPickupLocationId());
            ps.setString(7, o.getPickupLocationName());
            ps.setString(8, o.getPickupLocation());
            ps.setString(9, o.getDeliveryLocationId());
            ps.setString(10, o.getDeliveryLocationName());
            ps.setString(11, o.getDeliveryLocation());
            ps.setBigDecimal(12, o.getRouteDistanceKm());
            ps.setBigDecimal(13, o.getRouteDurationH());
            ps.setBigDecimal(14, o.getRevenuePerTonKm());
            ps.setBigDecimal(15, o.getTotalRevenue());
            ps.setString(16, o.getStatus());
            ps.setTimestamp(17, Timestamp.valueOf(o.getCreatedAt() == null ? LocalDateTime.now().withNano(0) : o.getCreatedAt().withNano(0)));
            ps.executeUpdate();
        }
    }

    public List<OrderEntity> findPendingOrders(Connection conn) throws SQLException {
        List<OrderEntity> list = new ArrayList<>();
        String sql = """
            SELECT * FROM transport_order
            WHERE status IN ('PENDING', 'PARTIAL')
              AND pending_weight > 0
            ORDER BY created_at ASC, id ASC
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public List<OrderEntity> findAll(Connection conn) throws SQLException {
        List<OrderEntity> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM transport_order ORDER BY id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public OrderEntity findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM transport_order WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        }
    }

    public void allocate(Connection conn, Long orderId, BigDecimal addWeight) throws SQLException {
        if (addWeight == null || addWeight.signum() <= 0) throw new IllegalArgumentException("分配重量必须为正数");
        String sql = """
            UPDATE transport_order SET
              pending_weight = pending_weight - ?,
              allocated_weight = allocated_weight + ?,
              status = CASE WHEN pending_weight = 0 THEN 'ASSIGNED' ELSE 'PARTIAL' END,
              assigned_at = NOW()
            WHERE id = ? AND pending_weight >= ? AND status IN ('PENDING', 'PARTIAL')
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, addWeight); ps.setBigDecimal(2, addWeight);
            ps.setLong(3, orderId); ps.setBigDecimal(4, addWeight);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("订单货量已变化，分配已回滚");
        }
    }

    /**
     * 修复 pending_weight 不一致的订单
     */
    public void repairInconsistent(Connection conn) throws SQLException {
        String sql = """
            UPDATE transport_order
            SET pending_weight = GREATEST(0, cargo_total_weight - allocated_weight)
            WHERE cargo_total_weight IS NOT NULL
              AND allocated_weight IS NOT NULL
              AND ABS(cargo_total_weight - allocated_weight - COALESCE(pending_weight, 0)) > 0.01
        """;
        try (Statement st = conn.createStatement()) {
            int fixed = st.executeUpdate(sql);
            if (fixed > 0) {
                System.out.println("Repaired " + fixed + " inconsistent orders");
            }
        }
    }

    public void markDone(Connection conn, Long orderId) throws SQLException {
        String sql = """
            UPDATE transport_order
            SET status = 'DONE', finished_at = NOW()
            WHERE id = ? AND pending_weight <= 0
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.executeUpdate();
        }
    }
    public void updatePendingWeight(Connection conn, Long orderId, BigDecimal pendingWeight) throws SQLException {
        String sql = """
        UPDATE transport_order
        SET pending_weight = ?,
            assigned_at = NOW()
        WHERE id = ?
    """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, pendingWeight);
            ps.setLong(2, orderId);
            ps.executeUpdate();
        }
    }
    private OrderEntity map(ResultSet rs) throws SQLException {
        OrderEntity o = new OrderEntity();
        o.setId(rs.getLong("id"));
        o.setOrderCode(rs.getString("order_code"));
        o.setCargoType(rs.getString("cargo_type"));
        o.setCargoTotalWeight(rs.getBigDecimal("cargo_total_weight"));
        o.setAllocatedWeight(rs.getBigDecimal("allocated_weight"));
        o.setPendingWeight(rs.getBigDecimal("pending_weight"));
        o.setPickupLocationId(rs.getString("pickup_location_id"));
        o.setPickupLocationName(rs.getString("pickup_location_name"));
        o.setPickupLocation(rs.getString("pickup_location"));
        o.setDeliveryLocationId(rs.getString("delivery_location_id"));
        o.setDeliveryLocationName(rs.getString("delivery_location_name"));
        o.setDeliveryLocation(rs.getString("delivery_location"));
        o.setRouteDistanceKm(rs.getBigDecimal("route_distance_km"));
        o.setRouteDurationH(rs.getBigDecimal("route_duration_h"));
        o.setRevenuePerTonKm(rs.getBigDecimal("revenue_per_ton_km"));
        o.setTotalRevenue(rs.getBigDecimal("total_revenue"));
        o.setStatus(rs.getString("status"));
        Timestamp created = rs.getTimestamp("created_at");
        o.setCreatedAt(created == null ? LocalDateTime.now() : created.toLocalDateTime());
        return o;
    }
}
