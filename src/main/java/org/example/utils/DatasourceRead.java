package org.example.utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DatasourceRead {
    @Value("${datasource.table_name}")
    public String table_name;

    public List<Map<String, Object>> resToJsonObject(String url, String username, String password) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            conn = DriverManager.getConnection(url, username, password);
            stmt = conn.createStatement();
            String sql = "SELECT * FROM " + table_name;
            rs = stmt.executeQuery(sql);

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                String name=rs.getString("name");
                String location=rs.getString("location");
                row.put("name",name);
                row.put("location",location);
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

//            while (rs.next()) {
//                String name = rs.getString("name");
//                String location = rs.getString("location");
//                sb.append(name).append(",").append(location).append("<br/>");
//            }
