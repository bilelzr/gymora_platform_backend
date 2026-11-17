package com.gymora.common.tenant;

import com.gymora.common.error.GymoraException;
import org.springframework.http.HttpStatus;

/** Thrown when tenant-scoped data is used without a tenant in the context. */
public class MissingTenantException extends GymoraException {

    public MissingTenantException() {
        super(HttpStatus.FORBIDDEN, "error.tenant_required");
    }
}
