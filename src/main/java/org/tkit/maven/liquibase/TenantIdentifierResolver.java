package org.tkit.maven.liquibase;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tenant resolver.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver {


    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        return "base";
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}