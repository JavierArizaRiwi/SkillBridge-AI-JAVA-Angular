package com.riwi.skillbridge.infrastructure.config;

import com.riwi.skillbridge.domain.policy.OfferingAccessPolicy;
import com.riwi.skillbridge.domain.policy.UserManagementPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public OfferingAccessPolicy offeringAccessPolicy() {
        return new OfferingAccessPolicy();
    }

    @Bean
    public UserManagementPolicy userManagementPolicy() {
        return new UserManagementPolicy();
    }
}
