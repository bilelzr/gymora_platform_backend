package com.gymora.common.web;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Error messages follow the caller's Accept-Language: French (default), Arabic or English. */
@Configuration(proxyBeanMethods = false)
public class LocaleConfig {

    public static final Locale FRENCH = Locale.FRENCH;
    public static final Locale ARABIC = Locale.forLanguageTag("ar");

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(FRENCH, ARABIC, Locale.ENGLISH));
        resolver.setDefaultLocale(FRENCH);
        return resolver;
    }
}
