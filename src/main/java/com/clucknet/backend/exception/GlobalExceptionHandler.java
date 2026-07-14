package com.clucknet.backend.exception;

import com.clucknet.backend.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. Handle Domain Specific CustomException (including ResourceNotFoundException)
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException ex) {
        log.warn("Domain Exception Intercepted: {} (Status: {})", ex.getMessage(), ex.getStatus());
        ApiResponse<Void> response = ApiResponse.error(ex.getMessage());
        return new ResponseEntity<>(response, ex.getStatus());
    }

    // 2. Handle REST Parameter Validation Failures (MethodArgumentNotValidException)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Payload Validation Failure: {} field errors detected.", errors.size());
        
        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .message("Payload parameter validations failed.")
                .data(errors)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 3. Handle Method Security Access Denials (AccessDeniedException)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Security Access Denied: Unauthorized request attempted. Message: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error("Access denied. You do not hold required privileges to perform this operation.");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // 4. Fallback Catch-All for Uncaught System Exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Internal Server Error: Uncaught system anomaly: {}", ex.getMessage(), ex);
        
        // Hide internal stack trace details from public endpoints to prevent vulnerability disclosure
        ApiResponse<Void> response = ApiResponse.error("An unexpected server anomaly occurred. Please try again later.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
