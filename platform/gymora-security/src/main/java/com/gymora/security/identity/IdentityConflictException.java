package com.gymora.security.identity;

import com.gymora.common.error.GymoraException;
import org.springframework.http.HttpStatus;

/** The email already belongs to another login account. */
public class IdentityConflictException extends GymoraException {

    public IdentityConflictException(String email) {
        super(HttpStatus.CONFLICT, "error.identity_email_taken", email);
    }
}
