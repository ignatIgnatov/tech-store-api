package com.techstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
public class AuditConfig {

    @Bean(name = "auditAwareImpl")
    public AuditorAware<String> auditAwareImpl() {
        return new AuditorAwareImpl();
    }

    public static class AuditorAwareImpl implements AuditorAware<String> {
        @Override
        public Optional<String> getCurrentAuditor() {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication == null || !authentication.isAuthenticated()) {
                    return Optional.of("system");
                }

                Object principal = authentication.getPrincipal();

                if (principal instanceof UserDetails) {
                    return Optional.of(((UserDetails) principal).getUsername());
                } else if (principal instanceof String) {
                    return Optional.of((String) principal);
                }

                return Optional.of("system");

            } catch (Exception e) {
                return Optional.of("system");
            }
        }
    }
}