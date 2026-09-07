package com.bizpilot.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Test-only fixture entity (backed by {@code sample_entities}, see
 * db/testmigration/V1__test_fixtures.sql) used exclusively to prove
 * {@link BaseEntity}'s id generation and auditing timestamps work through a
 * real persistence round-trip. Not part of the production domain model.
 */
@Entity
@Table(name = "sample_entities")
public class SampleEntity extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected SampleEntity() {
        // required by JPA
    }

    public SampleEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
