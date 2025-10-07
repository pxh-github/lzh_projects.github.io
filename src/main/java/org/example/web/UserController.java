package org.example.web;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.sql.*;
import java.util.List;
import java.util.Map;

import org.example.utils.DatasourceRead;

@CrossOrigin("*")
@RestController
@RequestMapping("/user")
public class UserController {
    @Value("${datasource.username}")
    public String username;
    @Value("${datasource.password}")
    String password;
    @Value("${datasource.url}")
    String url;
    @Autowired
    private DatasourceRead datasourceRead;
    @GetMapping()
    public List<Map<String, Object>> getData(){
        return datasourceRead.resToJsonObject(url,username,password);
    }
}
