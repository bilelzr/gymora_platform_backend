package com.gymora.security;

import com.gymora.security.jwt.GymoraAuthoritiesConverter;
import com.gymora.security.tenant.TenantContextFilter;
import com.gymora.security.tenant.TenantStatusProvider;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Stateless API secured by Keycloak access tokens.
 *
 * <p>Token issuer and audience come from {@code spring.security.oauth2.resourceserver.jwt.*}.
 * Platform endpoints live under {@code /api/v1/platform/**} and need PLATFORM_ADMIN; every other
 * API endpoint is protected by {@code @RequiresCapability}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfig.GymoraSecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    GymoraAuthoritiesConverter authoritiesConverter,
                                    MessageSource messages,
                                    LocaleResolver localeResolver,
                                    ObjectProvider<TenantStatusProvider> tenantStatus) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/platform/**").hasRole(GymoraRoles.PLATFORM_ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)))
                .addFilterAfter(new TenantContextFilter(messages, localeResolver, tenantStatus),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Lets {@code @RequiresCapability("x")} fill its value into the {@code @PreAuthorize} template. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GymoraSecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "If-Match"));
        cors.setExposedHeaders(List.of("Location", "ETag"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @ConfigurationProperties("gymora.security")
    public record GymoraSecurityProperties(List<String> corsAllowedOrigins) {
        public GymoraSecurityProperties {
            corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
        }
    }
}
