package com.university.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the University LMS modular monolith.
 *
 * <p>A single deployable application whose internals are split into strongly isolated business
 * modules (see {@code docs/modules.md}). Modules communicate only through published {@code api}
 * interfaces so that any one of them can later be extracted into its own service.
 */
@ConfigurationPropertiesScan
@SpringBootApplication
public class UniversityLmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(UniversityLmsApplication.class, args);
    }
}
