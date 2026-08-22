package com.university.lms.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables the auditing that populates {@link BaseEntity}'s timestamp and actor columns.
 *
 * <p>Kept separate from the main application class so that a slice test can opt into auditing
 * without dragging in the entire application context, and so {@link CreatedBy} resolution has one
 * obvious home.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();
    }
}
