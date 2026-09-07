package com.bizpilot.common.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Test-only repository for {@link SampleEntity}. */
public interface SampleEntityRepository extends JpaRepository<SampleEntity, UUID> {
}
