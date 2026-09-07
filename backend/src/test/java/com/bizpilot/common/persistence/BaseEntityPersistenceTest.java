package com.bizpilot.common.persistence;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link BaseEntity}'s id generation and created_at/updated_at
 * auditing work through a real persistence round-trip against the
 * containerized PostgreSQL from {@link TestcontainersConfiguration} — not
 * just that the annotations are present.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class BaseEntityPersistenceTest {

    @Autowired
    private SampleEntityRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void generatesIdAndPopulatesAuditTimestampsOnInsert() {
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("initial"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void updatingAnEntityAdvancesUpdatedAtButNeverCreatedAt() throws InterruptedException {
        SampleEntity saved = repository.saveAndFlush(new SampleEntity("initial"));
        UUID id = saved.getId();
        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        entityManager.clear();
        Thread.sleep(5);

        SampleEntity toUpdate = repository.findById(id).orElseThrow();
        toUpdate.setName("changed");
        repository.saveAndFlush(toUpdate);
        entityManager.clear();

        Optional<SampleEntity> reloaded = repository.findById(id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getName()).isEqualTo("changed");
        assertThat(reloaded.get().getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(reloaded.get().getUpdatedAt()).isAfter(originalUpdatedAt);
    }
}
