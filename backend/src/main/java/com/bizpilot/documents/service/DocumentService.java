package com.bizpilot.documents.service;

import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.exception.DocumentNotFoundException;
import com.bizpilot.documents.exception.InvalidDocumentException;
import com.bizpilot.documents.repository.DocumentRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Owns all document business logic. Every method resolves the current
 * organization from {@link TenantContext} (never from client input) and
 * every lookup by id is organization-scoped via {@code findByIdAndOrganizationId}.
 *
 * <p><b>Upload ordering</b> (project instructions §16/§17): storage write
 * happens strictly before the database insert. If storage fails, no
 * database row is ever created. If storage succeeds but the subsequent
 * database insert fails, a synchronous best-effort cleanup deletes the
 * just-written object before the failure propagates — this ordering
 * guarantees a persisted {@code Document} row never outlives its backing
 * file (the worse of the two possible inconsistencies), at the cost of a
 * possible harmless orphaned file if the JVM crashes between the two steps
 * — an acceptable, documented tradeoff with no reconciliation job.
 *
 * <p><b>No {@code FAILED} rows.</b> A failed upload (validation or storage
 * failure) never produces a persisted {@code Document} at all — there is
 * nothing to mark as failed, since no row exists yet. {@code FAILED} is
 * reserved for the future Phase 15 processing pipeline to set on an
 * already-{@code UPLOADED} row.
 *
 * <p><b>Delete ordering is the reverse of upload</b>, and deliberately so:
 * storage delete happens first, then the database row delete. This is
 * idempotent by construction — {@code DocumentStorageService.delete} is a
 * no-op if the object is already gone — so if the database delete fails
 * after a successful storage delete, simply calling {@link #delete} again
 * completes the cleanup (the second storage-delete call is a harmless
 * no-op, and the database delete is retried). The reverse ordering
 * (database-first) was rejected: it would leave an orphaned file with no
 * way to ever reach it again through the API once its metadata row is gone,
 * and this project explicitly excludes background reconciliation.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Only the first few bytes are needed for any of the three supported formats' signature checks. */
    private static final int SIGNATURE_PROBE_LENGTH = 8;

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final TenantContext tenantContext;
    private final OrganizationService organizationService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentService(DocumentRepository documentRepository, DocumentStorageService storageService,
                            TenantContext tenantContext, OrganizationService organizationService,
                            CurrentUserProvider currentUserProvider) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.tenantContext = tenantContext;
        this.organizationService = organizationService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public Document upload(MultipartFile file) {
        byte[] content = readBytes(file);
        DocumentValidator.validateSize(content.length);

        String sanitizedFilename = DocumentValidator.sanitizeFilename(file.getOriginalFilename());
        byte[] header = Arrays.copyOf(content, Math.min(content.length, SIGNATURE_PROBE_LENGTH));
        String contentType = DocumentValidator.validateAndResolveContentType(
                sanitizedFilename, file.getContentType(), header);

        Organization organization = organizationService.getCurrentOrganization();
        UUID uploadedByUserId = currentUserProvider.getCurrentUser().map(UserPrincipal::userId).orElse(null);
        String storageKey = buildStorageKey(organization.getId(), UUID.randomUUID());

        storageService.store(storageKey, new ByteArrayInputStream(content));

        try {
            Document document = new Document(organization, sanitizedFilename, storageKey, contentType,
                    content.length, uploadedByUserId);
            return documentRepository.save(document);
        } catch (RuntimeException e) {
            cleanUpOrphanedStorageObject(storageKey);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Document getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Document> search(DocumentSearchCriteria criteria, Pageable pageable) {
        return documentRepository.search(
                tenantContext.currentOrganizationId(), criteria.status(), criteria.contentType(),
                criteria.uploadedByUserId(), normalizeSearch(criteria.search()), pageable);
    }

    @Transactional(readOnly = true)
    public DocumentContent download(UUID id) {
        Document document = findOrThrow(id);
        return new DocumentContent(document, storageService.load(document.getStorageKey()));
    }

    @Transactional
    public void delete(UUID id) {
        Document document = findOrThrow(id);
        storageService.delete(document.getStorageKey());
        documentRepository.delete(document);
    }

    private Document findOrThrow(UUID id) {
        return documentRepository.findByIdAndOrganizationId(id, tenantContext.currentOrganizationId())
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private void cleanUpOrphanedStorageObject(String storageKey) {
        try {
            storageService.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            log.error("Failed to clean up orphaned document storage object {} after a database failure",
                    storageKey, cleanupFailure);
        }
    }

    private static String buildStorageKey(UUID organizationId, UUID storageObjectId) {
        return "organizations/" + organizationId + "/documents/" + storageObjectId;
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("File is empty");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidDocumentException("Failed to read the uploaded file", e);
        }
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }
}
