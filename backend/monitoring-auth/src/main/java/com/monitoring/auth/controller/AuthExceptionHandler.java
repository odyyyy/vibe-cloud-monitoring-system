package com.monitoring.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Void> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();

        if ("Invalid password".equals(message)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if ("User not found".equals(message)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if ("Username already exists".equals(message)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
