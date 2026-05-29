package com.example.navigation.controller;

import com.example.navigation.model.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Replaces BasicErrorController so forwarded errors use the same JSON shape
// as exceptions handled by GlobalExceptionHandler.
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        int status = readStatus(request);
        String code = codeFor(status);
        String message = readMessage(request);
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    private static int readStatus(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return (attr instanceof Integer i) ? i : 500;
    }

    private static String readMessage(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        return attr == null ? "" : attr.toString();
    }

    private static String codeFor(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.name() : "ERROR";
    }
}
