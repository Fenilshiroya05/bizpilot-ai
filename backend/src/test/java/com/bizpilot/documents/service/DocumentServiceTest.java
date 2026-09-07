package com.bizpilot.documents.service;

import com.bizpilot.ai.vectorstore.DocumentVectorStoreService;
import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.documents.exception.DocumentNotFoundException;
import com.bizpilot.documents.exception.DocumentStorageException;
import com.bizpilot.documents.exception.InvalidDocumentException;
import com.bizpilot.documents.processing.DocumentUploadedEvent;
import com.bizpilot.documents.repository.DocumentRepository;
import com.bizpilot.organization.TenantContext;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.organization.service.OrganizationService;
import com.bizpilot.security.CurrentUserProvider;
import com.bizpilot.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 minimal valid pdf body".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorageService storageService;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DocumentVectorStoreService> documentVectorStoreServiceProvider = mock(ObjectProvider.class);

    private final UUID organizationId = UUID.randomUUID();
    private final UUID currentUserId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme Corp");

    @BeforeEach
    void setUp() {
        lenient().when(tenantContext.currentOrganizationId()).thenReturn(organizationId);
        lenient().when(organizationService.getCurrentOrganization()).thenReturn(organization);
        lenient().when(currentUserProvider.getCurrentUser())
                .thenReturn(Optional.of(new UserPrincipal(currentUserId, organizationId, Set.of("DOCUMENT_UPLOAD"))));
        lenient().when(documentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DocumentService service() {
        return new DocumentService(documentRepository, storageService, tenantContext, organizationService,
                currentUserProvider, eventPublisher, documentVectorStoreServiceProvider);
    }

    private MockMultipartFile pdfFile(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", PDF_BYTES);
    }

    @Test
    void uploadPersistsMetadataWithDefaultStatusUploaded() {
        Document uploaded = service().upload(pdfFile("contract.pdf"));

        assertThat(uploaded.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(uploaded.getOriginalFilename()).isEqualTo("contract.pdf");
        assertThat(uploaded.getContentType()).isEqualTo("application/pdf");
        assertThat(uploaded.getFileSize()).isEqualTo(PDF_BYTES.length);
        assertThat(uploaded.getUploadedByUserId()).isEqualTo(currentUserId);
        assertThat(uploaded.getOrganization()).isSameAs(organization);
    }

    @Test
    void uploadWritesToStorageBeforePersistingMetadata() {
        service().upload(pdfFile("contract.pdf"));

        var inOrder = inOrder(storageService, documentRepository);
        inOrder.verify(storageService).store(anyString(), any());
        inOrder.verify(documentRepository).save(any(Document.class));
    }

    @Test
    void uploadGeneratesAServerSideStorageKeyNamespacedByOrganization() {
        // organization.getId() is null here (an unsaved test fixture — in
        // production, organizationService.getCurrentOrganization() always
        // returns an already-persisted Organization with a real id), so
        // this asserts the key's *shape*, not a specific organization id.
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        service().upload(pdfFile("contract.pdf"));

        verify(storageService).store(keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).matches("organizations/.+/documents/[0-9a-f-]{36}");
    }

    @Test
    void uploadSanitizesAPathTraversalFilename() {
        Document uploaded = service().upload(new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf", PDF_BYTES));

        assertThat(uploaded.getOriginalFilename()).isEqualTo("passwd.pdf");
    }

    @Test
    void uploadRejectsAnEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service().upload(empty)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadRejectsAFileOverTwentyMegabytes() {
        byte[] oversized = new byte[(int) DocumentValidator.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> service().upload(file)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void uploadRejectsAnUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", "MZ".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service().upload(file)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void uploadRejectsAMismatchedDeclaredContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> service().upload(file)).isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void uploadRejectsAnExecutableRenamedToPdf() {
        byte[] exeBytes = {0x4D, 0x5A, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "malware.pdf", "application/pdf", exeBytes);

        assertThatThrownBy(() -> service().upload(file)).isInstanceOf(InvalidDocumentException.class);
        verify(storageService, never()).store(any(), any());
    }

    @Test
    void uploadPropagatesAStorageFailureWithoutPersistingMetadata() {
        doThrow(new DocumentStorageException("disk full")).when(storageService).store(anyString(), any());

        assertThatThrownBy(() -> service().upload(pdfFile("contract.pdf")))
                .isInstanceOf(DocumentStorageException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void uploadCleansUpTheStoredObjectWhenTheDatabaseInsertFailsAfterStorageSucceeds() {
        when(documentRepository.save(any(Document.class))).thenThrow(new RuntimeException("db unavailable"));

        assertThatThrownBy(() -> service().upload(pdfFile("contract.pdf")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db unavailable");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService).store(keyCaptor.capture(), any());
        verify(storageService).delete(keyCaptor.getValue());
    }

    @Test
    void uploadDoesNotPropagateAFailureFromTheBestEffortCleanupItself() {
        when(documentRepository.save(any(Document.class))).thenThrow(new RuntimeException("db unavailable"));
        doThrow(new RuntimeException("cleanup also failed")).when(storageService).delete(anyString());

        // The original db failure must still be what the caller sees, not
        // the cleanup failure — the cleanup failure is only logged.
        assertThatThrownBy(() -> service().upload(pdfFile("contract.pdf")))
                .hasMessage("db unavailable");
    }

    @Test
    void getByIdThrowsNotFoundWhenNoDocumentMatchesTheCurrentOrganization() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getById(id)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void downloadReturnsTheDocumentAndItsLoadedResource() {
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        ByteArrayResource resource = new ByteArrayResource(PDF_BYTES);
        when(storageService.load("organizations/x/documents/y")).thenReturn(resource);

        DocumentContent content = service().download(UUID.randomUUID());

        assertThat(content.document()).isSameAs(existing);
        assertThat(content.resource()).isSameAs(resource);
    }

    @Test
    void downloadPropagatesAStorageExceptionWhenThePhysicalFileIsMissing() {
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        when(storageService.load("organizations/x/documents/y"))
                .thenThrow(new DocumentStorageException("missing"));

        assertThatThrownBy(() -> service().download(UUID.randomUUID())).isInstanceOf(DocumentStorageException.class);
    }

    @Test
    void deleteRemovesStorageBeforeTheDatabaseRow() {
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().delete(UUID.randomUUID());

        var inOrder = inOrder(storageService, documentRepository);
        inOrder.verify(storageService).delete("organizations/x/documents/y");
        inOrder.verify(documentRepository).delete(existing);
    }

    @Test
    void deleteThrowsNotFoundForAMissingDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.findByIdAndOrganizationId(id, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(DocumentNotFoundException.class);
        verify(storageService, never()).delete(any());
    }

    @Test
    void deleteToleratesAnAlreadyMissingPhysicalFile() {
        // LocalDocumentStorageService.delete is documented as idempotent —
        // this proves DocumentService.delete relies on that and doesn't
        // itself treat a missing-file scenario as an error.
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        // storageService.delete is a mock — calling it does nothing by
        // default, exactly matching the real idempotent no-op behavior.

        service().delete(UUID.randomUUID());

        verify(documentRepository).delete(existing);
    }

    @Test
    void uploadPublishesADocumentUploadedEventAfterPersisting() {
        Document uploaded = service().upload(pdfFile("contract.pdf"));

        ArgumentCaptor<DocumentUploadedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(uploaded.getId());
    }

    @Test
    void uploadDoesNotPublishAnEventWhenTheDatabaseInsertFails() {
        when(documentRepository.save(any(Document.class))).thenThrow(new RuntimeException("db unavailable"));

        assertThatThrownBy(() -> service().upload(pdfFile("contract.pdf")));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteInvokesTenantScopedVectorCleanupWhenTheAiPipelineIsEnabled() {
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));
        DocumentVectorStoreService vectorStoreService = mock(DocumentVectorStoreService.class);
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<DocumentVectorStoreService>>getArgument(0).accept(vectorStoreService);
            return null;
        }).when(documentVectorStoreServiceProvider).ifAvailable(any());

        UUID documentId = UUID.randomUUID();
        service().delete(documentId);

        verify(vectorStoreService).deleteForDocument(organizationId, documentId);
    }

    @Test
    void deleteToleratesTheAiPipelineBeingDisabled() {
        // documentVectorStoreServiceProvider is an unstubbed mock here —
        // ifAvailable(...) is a no-op by default, exactly matching real
        // Spring behavior when bizpilot.ai.enabled=false (no
        // DocumentVectorStoreService bean exists at all).
        Document existing = new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, currentUserId);
        when(documentRepository.findByIdAndOrganizationId(any(), any())).thenReturn(Optional.of(existing));

        service().delete(UUID.randomUUID());

        verify(documentRepository).delete(existing);
    }

    @Test
    void searchDelegatesToTheRepositoryWithTheCurrentOrganizationId() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Document> emptyPage = new PageImpl<>(List.of());
        when(documentRepository.search(eq(organizationId), any(), any(), any(), any(), eq(pageable)))
                .thenReturn(emptyPage);

        DocumentSearchCriteria criteria = new DocumentSearchCriteria(null, null, null, null);
        service().search(criteria, pageable);

        verify(documentRepository).search(eq(organizationId), any(), any(), any(), any(), eq(pageable));
    }
}
