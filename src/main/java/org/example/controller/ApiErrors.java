package org.example.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;
import org.slf4j.LoggerFactory;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformed(Exception e) { return ResponseEntity.badRequest().body(Map.of("message", "请求配置无效，请检查数量、种子、时长、事件和目标权重")); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> conflict(IllegalStateException e) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failed(Exception e) {
        LoggerFactory.getLogger(ApiErrors.class).error("Request failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", "服务暂不可用，请检查数据库连接和服务日志"));
    }
}
