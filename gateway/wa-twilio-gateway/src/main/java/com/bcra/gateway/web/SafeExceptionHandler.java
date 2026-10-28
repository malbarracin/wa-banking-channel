package com.bcra.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class SafeExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> unexpectedFailure(Exception exception) {
        LOGGER.error("Unexpected gateway request failure");
        return ResponseEntity.internalServerError().build();
    }
}
