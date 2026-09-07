package com.bizpilot.documents.service;

import com.bizpilot.documents.exception.DocumentStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link LocalDocumentStorageService} in isolation — no Spring
 * context, direct instantiation against a fresh temp directory per test.
 */
class LocalDocumentStorageServiceTest {

    private Path storageRoot;
    private LocalDocumentStorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createTempDirectory("bizpilot-storage-test-");
        storageService = new LocalDocumentStorageService(storageRoot.toString());
    }

    @Test
    void storeThenLoadRoundTripsTheExactBytes() throws IOException {
        String key = "organizations/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();
        byte[] content = "hello document".getBytes(StandardCharsets.UTF_8);

        storageService.store(key, new ByteArrayInputStream(content));
        Resource loaded = storageService.load(key);

        assertThat(StreamUtils.copyToByteArray(loaded.getInputStream())).isEqualTo(content);
    }

    @Test
    void loadingAMissingKeyThrowsDocumentStorageException() {
        String key = "organizations/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();

        assertThatThrownBy(() -> storageService.load(key)).isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void deleteRemovesTheStoredObject() {
        String key = "organizations/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();
        storageService.store(key, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        storageService.delete(key);

        assertThatThrownBy(() -> storageService.load(key)).isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void deletingAnAlreadyMissingObjectIsANoOpNotAnError() {
        String key = "organizations/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();

        storageService.delete(key); // must not throw — idempotent by design
    }

    @Test
    void differentOrganizationsAreStoredUnderDistinctNamespaces() throws IOException {
        UUID orgA = UUID.randomUUID();
        UUID orgB = UUID.randomUUID();
        UUID documentId = UUID.randomUUID(); // same document id under two different orgs, deliberately
        String keyA = "organizations/" + orgA + "/documents/" + documentId;
        String keyB = "organizations/" + orgB + "/documents/" + documentId;

        storageService.store(keyA, new ByteArrayInputStream("org A content".getBytes(StandardCharsets.UTF_8)));
        storageService.store(keyB, new ByteArrayInputStream("org B content".getBytes(StandardCharsets.UTF_8)));

        assertThat(StreamUtils.copyToByteArray(storageService.load(keyA).getInputStream()))
                .isEqualTo("org A content".getBytes(StandardCharsets.UTF_8));
        assertThat(StreamUtils.copyToByteArray(storageService.load(keyB).getInputStream()))
                .isEqualTo("org B content".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void resolvedPathsNeverEscapeTheStorageRootEvenForAPathTraversalStyleKey() {
        // Storage keys are always server-generated in production and never
        // derived from client input — this proves the defense-in-depth
        // containment check still holds even if one somehow contained
        // traversal segments.
        String maliciousKey = "../../../etc/passwd";

        assertThatThrownBy(() -> storageService.store(maliciousKey,
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(DocumentStorageException.class);
    }
}
