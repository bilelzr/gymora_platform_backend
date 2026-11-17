package com.gymora.security.capability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Protects an endpoint with one capability, for example
 * {@code @RequiresCapability("membership.member.create")}.
 *
 * <p>Gym owners pass for every capability in their own gym. Staff pass only if one of their
 * profiles grants the capability. Platform admins never pass: gym data isn't theirs.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@capabilityGuard.has('{value}')")
public @interface RequiresCapability {

    /** Capability key: {@code <module>.<resource>.<action>}. */
    String value();
}
