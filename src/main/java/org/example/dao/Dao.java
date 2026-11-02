package org.example.dao;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class Dao {
    private final org.example.service.Database database;
    public Dao(org.example.service.Database database) { this.database = database; }
    public void clearCarStateTable(String url, String username, String password) {
        try (Connection conn = database.open();
             Statement st = conn.createStatement()) {

            // DELETE 更通用（TRUNCATE 遇到外键/权限可能失败）
            st.executeUpdate("DELETE FROM car_state");
            // st.executeUpdate("TRUNCATE TABLE car_state");

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public void upsertCarState(String url, String username, String password, Map<String, Object> payload) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = database.open();

            // REPLACE INTO：如果 car_id 已存在，会先删除旧行再插入新行（符合你“删除上一次”的需求）
            String sql = """
            REPLACE INTO car_state
            (car_id, status, start_id, start_name, start_location, dest_id, dest_name, dest_location, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
        """;

            ps = conn.prepareStatement(sql);

            ps.setInt(1, Integer.parseInt(payload.get("carId").toString()));
            ps.setString(2, payload.get("status").toString());

            ps.setString(3, payload.getOrDefault("startId", "").toString());
            ps.setString(4, payload.getOrDefault("startName", "").toString());
            ps.setString(5, payload.getOrDefault("startLocation", "").toString());

            ps.setString(6, payload.getOrDefault("destId", "").toString());
            ps.setString(7, payload.getOrDefault("destName", "").toString());
            ps.setString(8, payload.getOrDefault("destLocation", "").toString());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void deleteCarState(String url, String username, String password, int carId) {
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = database.open();
            ps = conn.prepareStatement("DELETE FROM car_state WHERE car_id = ?");
            ps.setInt(1, carId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public List<Map<String, Object>> resToJsonObject(String url, String username, String password,String table_name) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            conn = database.open();
            stmt = conn.createStatement();
            if (!table_name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("无效表名");
            String sql = "SELECT location_name,location,type,id FROM `" + table_name + "` ORDER BY id";
            rs = stmt.executeQuery(sql);


            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                String name=rs.getString("location_name");
                String location=rs.getString("location");
                String type=rs.getString("type");
                String id=rs.getString("id");
                row.put("name",name);
                row.put("location",location);
                row.put("type",type);
                row.put("id",id);
                resultList.add(row);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        return resultList;
    }
}
