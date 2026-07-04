package com.example.bank.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle JSR-380 input validation errors (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        // Also supply a unified error message string for convenience
        String unifiedError = errors.values().iterator().next();
        Map<String, String> response = new HashMap<>();
        response.put("error", unifiedError);
        errors.forEach(response::put);
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Handle invalid JSON payload errors
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleJsonParseException(HttpMessageNotReadableException ex) {
        return new ResponseEntity<>(
                Map.of("error", "Malformed or unparseable JSON request body"),
                HttpStatus.BAD_REQUEST
        );
    }

    // Handle illegal arguments and format errors
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(
                Map.of("error", ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    // Generic fallback handler to hide internal implementation details
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        // Log to console for developer debugging
        System.err.println("Unexpected runtime exception: " + ex.getMessage());
        ex.printStackTrace();
        
        return new ResponseEntity<>(
                Map.of("error", "An internal server error occurred. Transaction aborted."),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
