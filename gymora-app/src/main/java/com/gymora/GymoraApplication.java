package com.gymora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GYMORA API. Lives in the root {@code com.gymora} package so that components, entities and
 * repositories of every module ({@code com.gymora.<module>}) are found without extra wiring.
 */
@SpringBootApplication
public class GymoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(GymoraApplication.class, args);
    }
}
