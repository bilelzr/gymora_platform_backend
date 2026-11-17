package com.gymora.common.error;

import org.springframework.http.HttpStatus;

/**
 * A record does not exist, or exists in another gym. Both cases answer the same 404 so a gym can't
 * learn anything about another gym's data.
 */
public class NotFoundException extends GymoraException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "error.not_found", resource, id);
    }
}
