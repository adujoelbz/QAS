package com.example.qas.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class AuthenticationExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials() {
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabledAccount() {
        return error(HttpStatus.FORBIDDEN, "Account is disabled");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<Map<String, String>> handleLockedAccount() {
        return error(HttpStatus.FORBIDDEN, "Account is locked");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartError() {
        return error(HttpStatus.BAD_REQUEST,
                "Invalid multipart request. In Postman, use Body > form-data and remove the manual Content-Type header.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
        return error(HttpStatus.valueOf(exception.getStatusCode().value()), exception.getReason());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
