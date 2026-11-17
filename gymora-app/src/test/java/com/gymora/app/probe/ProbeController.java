package com.gymora.app.probe;

import com.gymora.security.capability.RequiresCapability;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint protected by a capability. */
@RestController
public class ProbeController {

    public static final String CAPABILITY = "test.probe.read";

    @GetMapping("/api/v1/test-probes")
    @RequiresCapability(CAPABILITY)
    public Map<String, String> read() {
        return Map.of("status", "ok");
    }
}
