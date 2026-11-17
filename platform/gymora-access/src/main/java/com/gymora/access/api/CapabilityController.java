package com.gymora.access.api;

import com.gymora.access.application.AccessCapabilities;
import com.gymora.security.capability.CapabilityCatalog;
import com.gymora.security.capability.CapabilityCatalog.CapabilityDefinition;
import com.gymora.security.capability.RequiresCapability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
@Tag(name = "Access · Capabilities")
public class CapabilityController {

    private final CapabilityCatalog catalog;
    private final MessageSource messages;

    public CapabilityController(CapabilityCatalog catalog, MessageSource messages) {
        this.catalog = catalog;
        this.messages = messages;
    }

    @GetMapping
    @RequiresCapability(AccessCapabilities.PROFILE_READ)
    @Operation(summary = "Every capability that can go into a profile, grouped by module",
            description = "Labels follow the Accept-Language header. Each capability lists the endpoints it opens.")
    public List<ModuleCapabilities> list() {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, List<CapabilityDefinition>> byModule = catalog.all().stream()
                .collect(Collectors.groupingBy(CapabilityDefinition::module, TreeMap::new, Collectors.toList()));
        return byModule.entrySet().stream()
                .map(entry -> new ModuleCapabilities(
                        entry.getKey(),
                        label("module." + entry.getKey(), entry.getKey(), locale),
                        entry.getValue().stream()
                                .map(def -> new CapabilityItem(def.key(), label("capability." + def.key(), def.key(), locale),
                                        def.endpoints()))
                                .toList()))
                .toList();
    }

    private String label(String code, String fallback, Locale locale) {
        return messages.getMessage(code, null, fallback, locale);
    }

    public record ModuleCapabilities(String module, String label, List<CapabilityItem> capabilities) {
    }

    public record CapabilityItem(String key, String label, List<String> endpoints) {
    }
}
