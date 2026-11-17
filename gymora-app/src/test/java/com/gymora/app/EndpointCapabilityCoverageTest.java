package com.gymora.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymora.app.support.GymoraIntegrationTest;
import com.gymora.security.capability.CapabilityCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Fails the build if a gym endpoint is added without a capability. Only platform endpoints
 * (PLATFORM_ADMIN role) and /api/v1/me (any signed-in user) are exempt.
 */
@GymoraIntegrationTest
class EndpointCapabilityCoverageTest {

    private static final Set<String> EXEMPT_PREFIXES = Set.of("/api/v1/platform/", "/api/v1/me");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void everyGymEndpointRequiresACapability() {
        List<String> unprotected = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            for (String pattern : info.getPatternValues()) {
                boolean gymApi = pattern.startsWith("/api/v1/")
                        && EXEMPT_PREFIXES.stream().noneMatch(pattern::startsWith);
                if (gymApi && CapabilityCatalog.capabilityOf(handler).isEmpty()) {
                    unprotected.add(info.getMethodsCondition() + " " + pattern + " -> " + handler);
                }
            }
        });
        assertThat(unprotected).as("Endpoints without @RequiresCapability").isEmpty();
    }
}
