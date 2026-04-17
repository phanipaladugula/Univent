package com.univent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class EnvSecretProvider implements SecretProvider {

    @Value("${INTERNAL_SHARED_SECRET:d3f4ult_c0mpl3x_s3cr3t_key_for_d3v}")
    private String internalSharedSecret;

    @Override
    public String getEncryptionSecret() {
        return secretKey;
    }

    @Override
    public String getInternalSharedSecret() {
        return internalSharedSecret;
    }
}
