package com.univent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.Arrays;

@Configuration
public class LoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter logFilter(Environment environment) {
        boolean productionLike = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod") || profile.equalsIgnoreCase("production"));

        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(!productionLike);
        filter.setMaxPayloadLength(productionLike ? 0 : 4096);
        filter.setIncludeHeaders(false);
        filter.setAfterMessagePrefix("REQUEST: ");
        return filter;
    }
}
