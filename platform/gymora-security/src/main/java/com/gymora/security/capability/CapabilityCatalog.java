package com.gymora.security.capability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every capability the application knows, discovered from the {@link RequiresCapability}
 * annotations on its endpoints. Adding an endpoint with a new capability adds it to the catalog:
 * nothing else to register.
 */
@Component
public class CapabilityCatalog {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private volatile Map<String, CapabilityDefinition> definitions;

    public CapabilityCatalog(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    /** All capabilities, sorted by key. */
    public List<CapabilityDefinition> all() {
        return List.copyOf(definitions().values());
    }

    public boolean exists(String key) {
        return definitions().containsKey(key);
    }

    public Set<String> keys() {
        return definitions().keySet();
    }

    public Optional<CapabilityDefinition> find(String key) {
        return Optional.ofNullable(definitions().get(key));
    }

    /** Keys that are not part of the catalog. */
    public Set<String> unknown(Set<String> keys) {
        return keys.stream().filter(key -> !exists(key)).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private Map<String, CapabilityDefinition> definitions() {
        Map<String, CapabilityDefinition> current = definitions;
        if (current == null) {
            synchronized (this) {
                if (definitions == null) {
                    definitions = scan();
                }
                current = definitions;
            }
        }
        return current;
    }

    private Map<String, CapabilityDefinition> scan() {
        Map<String, List<String>> endpointsByKey = new TreeMap<>();
        handlerMappings.orderedStream().forEach(mapping -> {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                capabilityOf(entry.getValue()).ifPresent(key ->
                        endpointsByKey.computeIfAbsent(key, k -> new ArrayList<>()).addAll(describe(entry.getKey())));
            }
        });
        Map<String, CapabilityDefinition> result = new TreeMap<>();
        endpointsByKey.forEach((key, endpoints) -> {
            Collections.sort(endpoints);
            result.put(key, new CapabilityDefinition(key, moduleOf(key), List.copyOf(endpoints)));
        });
        return Collections.unmodifiableMap(result);
    }

    /** The capability protecting a handler method, from the method or its controller class. */
    public static Optional<String> capabilityOf(HandlerMethod handler) {
        RequiresCapability annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresCapability.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresCapability.class);
        }
        return Optional.ofNullable(annotation).map(RequiresCapability::value);
    }

    private static List<String> describe(RequestMappingInfo info) {
        Set<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(Enum::name).collect(Collectors.toCollection(java.util.TreeSet::new));
        List<String> result = new ArrayList<>();
        for (String pattern : info.getPatternValues()) {
            if (methods.isEmpty()) {
                result.add(pattern);
            } else {
                methods.forEach(method -> result.add(method + " " + pattern));
            }
        }
        return result;
    }

    private static String moduleOf(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    /** One capability and the endpoints it protects, e.g. GET /api/v1/members. */
    public record CapabilityDefinition(String key, String module, List<String> endpoints) {
    }
}
