package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.domain.ord.state.IllegalTransitionException;

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

    // The order isn't in a state (or queue position) that allows the action, e.g. preparing out of turn.
    @ExceptionHandler({IllegalTransitionException.class, IllegalStateException.class})
    public ResponseEntity<String> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    // Bad input, e.g. a missing rejection reason or an unknown order.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
