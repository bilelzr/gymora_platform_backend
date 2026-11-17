package com.gymora.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. {@code code} is a message key resolved in the caller's
 * language (French, Arabic or English) and also returned as-is so clients can react to it.
 */
public class GymoraException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;

    public GymoraException(HttpStatus status, String code, Object... args) {
        super(code);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
