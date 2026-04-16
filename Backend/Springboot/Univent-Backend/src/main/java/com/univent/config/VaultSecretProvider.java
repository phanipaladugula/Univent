package com.univent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("vault")
@Slf4j
public class VaultSecretProvider implements SecretProvider {

    @Override
    public String getEncryptionSecret() {
        log.warn("Vault profile active but Vault integration is stubbed. Falling back to default behaviour or throwing exception if strict.");
        // Normally, you would use Spring Vault here to fetch the secret text from HashiCorp Vault or AWS KMS
        return "fallback-or-vault-secret";
    }
}
