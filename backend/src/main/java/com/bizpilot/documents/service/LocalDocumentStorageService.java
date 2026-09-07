package com.bizpilot.documents.service;

import com.bizpilot.documents.exception.DocumentStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Local filesystem implementation of {@link DocumentStorageService} —
 * Phase 13's only storage implementation (CLAUDE.md §16: "local filesystem
 * can be used for development"). Storage keys are always relative,
 * forward-slash-delimited paths built by {@code DocumentService}
 * (e.g. {@code organizations/{orgId}/documents/{uuid}}) and are resolved
 * segment-by-segment under {@link #storageRoot}, never by naive string
 * concatenation, so a storage key can never escape the storage root even in
 * principle — defense in depth, since storage keys are already always
 * server-generated and never derived from client input.
 */
@Service
public class LocalDocumentStorageService implements DocumentStorageService {

    private final Path storageRoot;

    public LocalDocumentStorageService(@Value("${bizpilot.documents.storage.path}") String storagePath) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to initialize document storage root", e);
        }
    }

    @Override
    public void store(String storageKey, InputStream content) {
        Path target = resolveSafePath(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to store document: " + storageKey, e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveSafePath(storageKey);
        if (!Files.isRegularFile(target)) {
            throw new DocumentStorageException("Stored document is missing: " + storageKey);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveSafePath(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new DocumentStorageException("Failed to delete document: " + storageKey, e);
        }
    }

    /**
     * Resolves a storage key to an absolute path one segment at a time
     * (never via a single {@code Path.of(root, key)} string concatenation,
     * which would be vulnerable if a key ever contained {@code ..}
     * segments), then verifies the normalized result is still contained
     * within {@link #storageRoot}.
     */
    private Path resolveSafePath(String storageKey) {
        Path resolved = storageRoot;
        for (String segment : storageKey.split("/")) {
            resolved = resolved.resolve(segment);
        }
        resolved = resolved.normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new DocumentStorageException("Resolved storage path escapes the storage root");
        }
        return resolved;
    }
}
