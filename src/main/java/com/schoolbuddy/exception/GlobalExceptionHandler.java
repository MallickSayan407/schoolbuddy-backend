package com.schoolbuddy.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiServiceException(
            AiServiceException exception
    ) {

        Map<String, Object> response = Map.of(
                "error", "AI_SERVICE_UNAVAILABLE",
                "message", exception.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }
}