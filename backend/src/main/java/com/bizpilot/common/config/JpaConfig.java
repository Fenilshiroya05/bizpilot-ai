package com.bizpilot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate}/{@code @LastModifiedDate} auditing so
 * {@link com.bizpilot.common.persistence.BaseEntity} timestamps populate
 * automatically for every entity that extends it.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
