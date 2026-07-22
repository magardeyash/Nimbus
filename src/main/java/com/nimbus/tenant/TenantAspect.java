package com.nimbus.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Aspect to intercept methods annotated with {@link org.springframework.transaction.annotation.Transactional}
 * and set the active tenant identifier in PostgreSQL's session-local context.
 */
@Aspect
@Component
@Order(20000)
public class TenantAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public void setTenantIdInSession() {
        UUID tenantId = TenantContext.getTenantId();
        String tenantVal = (tenantId != null) ? tenantId.toString() : "";
        
        log.debug("Injecting current_tenant_id [{}] into PostgreSQL transaction context.", tenantVal);

        entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantVal)
                .getSingleResult();
    }
}
