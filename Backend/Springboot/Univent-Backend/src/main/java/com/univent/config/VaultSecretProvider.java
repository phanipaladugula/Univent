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
        log.error("Vault profile is active but Vault integration is not implemented.");
        throw new IllegalStateException("Vault profile is not supported without a real Vault integration");
    }

    @Override
    public String getInternalSharedSecret() {
        log.error("Vault profile is active but Vault integration is not implemented.");
        throw new IllegalStateException("Vault profile is not supported without a real Vault integration");
    }
}
