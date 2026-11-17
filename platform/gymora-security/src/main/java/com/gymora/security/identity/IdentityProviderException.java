package com.gymora.security.identity;

import com.gymora.common.error.GymoraException;
import org.springframework.http.HttpStatus;

/** The identity provider (Keycloak) could not be reached or refused the operation. */
public class IdentityProviderException extends GymoraException {

    public IdentityProviderException(String operation, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "error.identity_unavailable", operation);
        initCause(cause);
    }
}
