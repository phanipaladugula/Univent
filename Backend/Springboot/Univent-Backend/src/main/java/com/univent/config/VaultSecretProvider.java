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
        return "fallback-or-vault-secret";
    }

    @Override
    public String getInternalSharedSecret() {
        log.warn("Vault profile active but Vault integration is stubbed for internal secret.");
        return "fallback-or-vault-internal-secret";
    }
}
