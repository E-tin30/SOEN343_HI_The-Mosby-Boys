package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<String> handlePermissionDenied(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
    
