package com.nimbus.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers our TenantResolutionInterceptor to handle workspace endpoints.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantResolutionInterceptor tenantResolutionInterceptor;

    public WebMvcConfig(TenantResolutionInterceptor tenantResolutionInterceptor) {
        this.tenantResolutionInterceptor = tenantResolutionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Intercept all requests starting with /api/v1/workspaces/
        registry.addInterceptor(tenantResolutionInterceptor)
                .addPathPatterns("/api/v1/workspaces/**");
    }
}
