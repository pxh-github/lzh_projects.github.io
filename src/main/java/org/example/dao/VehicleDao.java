package org.example.dao;

import org.example.entity.Vehicle;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class VehicleDao {
    public void releaseAvailableVehicles(Connection conn) throws SQLException {
        releaseAvailableVehicles(conn, LocalDateTime.now());
    }

    public void releaseAvailableVehicles(Connection conn, LocalDateTime simTime) throws SQLException {
        String sql = """
        UPDATE vehicle
        SET status = 'IDLE'
        WHERE status = 'BUSY'
          AND available_at IS NOT NULL
          AND available_at <= ?
    """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(simTime));
            ps.executeUpdate();
        }
    }
    public void save(Connection conn, Vehicle v) throws SQLException {
        String sql = """
            INSERT INTO vehicle(
                vehicle_code, vehicle_type, max_load_ton, actual_speed_kmh, sim_speed_kms,
                wear_cost_per_hour, fuel_consumption_per_100km, fuel_cost_per_km,
                current_location_id, current_location_name, current_location,
                status, available_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getVehicleCode());
            ps.setString(2, v.getVehicleType());
            ps.setBigDecimal(3, v.getMaxLoadTon());
            ps.setBigDecimal(4, v.getActualSpeedKmh());
            ps.setBigDecimal(5, v.getSimSpeedKms());
            ps.setBigDecimal(6, v.getWearCostPerHour());
            ps.setBigDecimal(7, v.getFuelConsumptionPer100km());
            ps.setBigDecimal(8, v.getFuelCostPerKm());
            ps.setString(9, v.getCurrentLocationId());
            ps.setString(10, v.getCurrentLocationName());
            ps.setString(11, v.getCurrentLocation());
            ps.setString(12, v.getStatus());
            ps.setTimestamp(13, v.getAvailableAt() == null ? null : Timestamp.valueOf(v.getAvailableAt()));
            ps.executeUpdate();
        }
    }

    public void clear(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM vehicle");
        }
    }

    public List<Vehicle> findAll(Connection conn) throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM vehicle ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<Vehicle> findIdleVehicles(Connection conn) throws SQLException {
        List<Vehicle> list = new ArrayList<>();
        String sql = """
        SELECT * FROM vehicle
        WHERE status = 'IDLE'
          AND (available_at IS NULL OR available_at <= NOW())
        ORDER BY id ASC
    """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public Vehicle findById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM vehicle WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        }
    }

    public void updateAfterDispatch(Connection conn,
                                    Long vehicleId,
                                    String newStatus,
                                    String newLocationId,
                                    String newLocationName,
                                    String newLocation,
                                    BigDecimal addEmptyKm,
                                    BigDecimal addLoadedKm,
                                    BigDecimal addRevenue,
                                    BigDecimal addCost,
                                    BigDecimal addProfit,
                                    LocalDateTime availableAt) throws SQLException {

        addEmptyKm = addEmptyKm == null ? BigDecimal.ZERO : addEmptyKm;
        addLoadedKm = addLoadedKm == null ? BigDecimal.ZERO : addLoadedKm;
        addRevenue = addRevenue == null ? BigDecimal.ZERO : addRevenue;
        addCost = addCost == null ? BigDecimal.ZERO : addCost;
        addProfit = addProfit == null ? BigDecimal.ZERO : addProfit;

        String sql = """
        UPDATE vehicle
        SET status = ?,
            current_location_id = ?,
            current_location_name = ?,
            current_location = ?,
            empty_mileage_km = COALESCE(empty_mileage_km, 0) + ?,
            loaded_mileage_km = COALESCE(loaded_mileage_km, 0) + ?,
            total_mileage_km = COALESCE(total_mileage_km, 0) + ? + ?,
            total_orders = COALESCE(total_orders, 0) + 1,
            total_revenue = COALESCE(total_revenue, 0) + ?,
            total_cost = COALESCE(total_cost, 0) + ?,
            total_profit = COALESCE(total_profit, 0) + ?,
            available_at = ?,
            updated_at = NOW()
        WHERE id = ?
    """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, newLocationId);
            ps.setString(3, newLocationName);
            ps.setString(4, newLocation);
            ps.setBigDecimal(5, addEmptyKm);
            ps.setBigDecimal(6, addLoadedKm);
            ps.setBigDecimal(7, addEmptyKm);
            ps.setBigDecimal(8, addLoadedKm);
            ps.setBigDecimal(9, addRevenue);
            ps.setBigDecimal(10, addCost);
            ps.setBigDecimal(11, addProfit);
            ps.setTimestamp(12, availableAt == null ? null : Timestamp.valueOf(availableAt));
            ps.setLong(13, vehicleId);

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new SQLException("更新车辆调度结果失败，vehicleId=" + vehicleId + "，影响行数=" + affected);
            }
        }
    }

    private Vehicle map(ResultSet rs) throws SQLException {
        Vehicle v = new Vehicle();
        v.setId(rs.getLong("id"));
        v.setVehicleCode(rs.getString("vehicle_code"));
        v.setVehicleType(rs.getString("vehicle_type"));
        v.setMaxLoadTon(rs.getBigDecimal("max_load_ton"));
        v.setActualSpeedKmh(rs.getBigDecimal("actual_speed_kmh"));
        v.setSimSpeedKms(rs.getBigDecimal("sim_speed_kms"));
        v.setWearCostPerHour(rs.getBigDecimal("wear_cost_per_hour"));
        v.setFuelConsumptionPer100km(rs.getBigDecimal("fuel_consumption_per_100km"));
        v.setFuelCostPerKm(rs.getBigDecimal("fuel_cost_per_km"));
        v.setCurrentLocationId(rs.getString("current_location_id"));
        v.setCurrentLocationName(rs.getString("current_location_name"));
        v.setCurrentLocation(rs.getString("current_location"));
        v.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("available_at");
        v.setAvailableAt(ts == null ? null : ts.toLocalDateTime());
        v.setTotalMileageKm(rs.getBigDecimal("total_mileage_km"));
        v.setEmptyMileageKm(rs.getBigDecimal("empty_mileage_km"));
        v.setLoadedMileageKm(rs.getBigDecimal("loaded_mileage_km"));
        v.setTotalOrders(rs.getInt("total_orders"));
        v.setTotalRevenue(rs.getBigDecimal("total_revenue"));
        v.setTotalCost(rs.getBigDecimal("total_cost"));
        v.setTotalProfit(rs.getBigDecimal("total_profit"));
        return v;
    }
}
