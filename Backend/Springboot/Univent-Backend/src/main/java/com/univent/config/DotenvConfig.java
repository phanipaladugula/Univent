package com.univent.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class DotenvConfig implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvConfig.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> envMap = new HashMap<>();
            dotenv.entries().forEach(entry -> envMap.put(entry.getKey(), entry.getValue()));

            if (!envMap.isEmpty()) {
                // Add the .env source with lower precedence so real deployment environment
                // variables continue to win in staging and production.
                environment.getPropertySources().addLast(new MapPropertySource("dotenv", envMap));
                log.info("Loaded {} properties from local .env file", envMap.size());
            }
        } catch (Exception e) {
            log.debug("No local .env file loaded", e);
        }
    }
}
