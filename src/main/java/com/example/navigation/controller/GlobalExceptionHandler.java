package com.example.navigation.controller;

import com.example.navigation.exception.NoRouteException;
import com.example.navigation.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoRouteException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(NoRouteException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("NO_ROUTE", ex.getMessage()));
    }
}
