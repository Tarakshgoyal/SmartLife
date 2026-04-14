package com.smartlife.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SmartLifeException extends RuntimeException {

    private final HttpStatus status;

    public SmartLifeException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public SmartLifeException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
