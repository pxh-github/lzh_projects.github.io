package org.example.entity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.datasource")
public class Dsp {
    private String url;
    private String username;
    private String password;
    private String tableName;
    private String sql;

    public String getUrl() { return url; }
    public void setUrl(String url){this.url=url;}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql=sql; }
}
