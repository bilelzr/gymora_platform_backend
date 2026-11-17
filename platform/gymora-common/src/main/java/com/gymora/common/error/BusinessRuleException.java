package com.gymora.common.error;

import org.springframework.http.HttpStatus;

/** A valid request that breaks a business rule (for example: freezing a plan that can't be frozen). */
public class BusinessRuleException extends GymoraException {

    public BusinessRuleException(String code, Object... args) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, args);
    }

    public BusinessRuleException(HttpStatus status, String code, Object... args) {
        super(status, code, args);
    }
}
