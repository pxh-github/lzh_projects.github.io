import java.sql.*;
import java.nio.file.*;
public class DatabaseProbe {
 public static void main(String[] args) throws Exception {
  String text=Files.readString(Path.of("src/main/resources/application.yml"));
  String url=text.lines().filter(s->s.trim().startsWith("url:")).findFirst().get().split("\"",3)[1];
  String user=text.lines().filter(s->s.trim().startsWith("username:")).findFirst().get().split(":",2)[1].trim();
  String pass=text.lines().filter(s->s.trim().startsWith("password:")).findFirst().get().split(":",2)[1].trim();
  try(Connection c=DriverManager.getConnection(resolve(url),resolve(user.replace("\"","")),resolve(pass.replace("\"","")));Statement s=c.createStatement()) {
   System.out.println("DB connected: "+c.getMetaData().getDatabaseProductVersion());
   for(String table:new String[]{"vehicle","transport_order","dispatch_task","location"}) {
    try(ResultSet r=s.executeQuery("SHOW CREATE TABLE "+table)){r.next();System.out.println(r.getString(2));}
   }
  }
 }
 static String resolve(String value) {
  if(!value.startsWith("${")) return value;
  String[] fields=value.substring(2,value.length()-1).split(":",2);
  return System.getenv().getOrDefault(fields[0],fields.length>1?fields[1]:"");
 }
}
