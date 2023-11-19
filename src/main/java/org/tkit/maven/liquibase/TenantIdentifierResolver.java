package org.tkit.maven.liquibase;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return "base";
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}