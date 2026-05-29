package com.example.navigation.exception;

public class NoRouteException extends RuntimeException {

    public NoRouteException(String message) {
        super(message);
    }
}
