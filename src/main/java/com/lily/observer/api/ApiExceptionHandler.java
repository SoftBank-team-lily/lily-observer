package com.lily.observer.api;

import com.lily.observer.cluster.ClusterUnavailableException;
import com.lily.observer.logs.LogsUnavailableException;
import com.lily.observer.metrics.MetricsUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/** 조회 API 에러를 {"message": "..."} 로 돌려준다 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MetricsUnavailableException.class, LogsUnavailableException.class,
            ClusterUnavailableException.class})
    ResponseEntity<Map<String, String>> unavailable(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("message", "잘못된 요청 값: " + e.getMessage()));
    }

    @ExceptionHandler(Durations.BadRequestException.class)
    ResponseEntity<Map<String, String>> badDuration(Durations.BadRequestException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
