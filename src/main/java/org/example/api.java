package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.MysqlDataSource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class api {
        public static void main(String[] args) {
            String key="1d830d8718ed7ad1452d6bbdfe3f447a";
            for(int i=1;i<=20;i++){
                String apiUrl="https://restapi.amap.com/v5/place/text?types=030100&region=成都市&city_limit=true&page_size=10&page_num="+i+"&key="+key;
                String jsonResponse=GetJson(apiUrl);
                try {
                    String[] info=processJsonResponse(jsonResponse);
                    for (String station : info) {
                        System.out.println(station);}
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        public static String GetJson(String url){
            OkHttpClient client = new OkHttpClient();
            String ans="";
            //GET请求
            Request getRequest = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(getRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    ans= response.body().string();
                }
            } catch (Exception e) {
                ans="error";
            }
            return ans;
        }
        public static void insert(String name,String location,String id){
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setUrl("jdbc:mysql://localhost/tran_db?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai");
            dataSource.setUser("root");
            dataSource.setPassword("123456");
            Connection connection;
            try {
                connection = dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            String sql="INSERT IGNORE INTO poi(name,location,id) value(?,?,?)";
            PreparedStatement preparedStatement;
            try {
                preparedStatement = connection.prepareStatement(sql);
                preparedStatement.setString(1,name);
                preparedStatement.setString(2,location);
                preparedStatement.setString(3,id);
                preparedStatement.executeUpdate();
                preparedStatement.close();
                connection.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        public static String[] processJsonResponse(String jsonString) throws Exception {

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode poisNode = rootNode.get("pois");

            java.util.List<String> resultList = new java.util.ArrayList<>();

            if (poisNode.isArray()) {
                for (JsonNode poiNode : poisNode) {
                    // 提取需要的信息组成字符串
                    String name = poiNode.get("name").asText();
                    String location = poiNode.get("location").asText();
                    String id=poiNode.get("id").asText();
                    resultList.add(name);
                    resultList.add(location);
                    insert(name,location,id);
                }
            }

            return resultList.toArray(new String[0]);
        }

}
