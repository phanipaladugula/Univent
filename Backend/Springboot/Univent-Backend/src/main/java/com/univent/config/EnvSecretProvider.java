package com.univent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class EnvSecretProvider implements SecretProvider {

    @Value("${encryption.secret}")
    private String secretKey;

    @Override
    public String getEncryptionSecret() {
        return secretKey;
    }
}
