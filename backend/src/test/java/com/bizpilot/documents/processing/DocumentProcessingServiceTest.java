package com.bizpilot.documents.processing;

import com.bizpilot.ai.service.AiEmbeddingService;
import com.bizpilot.documents.entity.Document;
import com.bizpilot.documents.exception.DocumentProcessingException;
import com.bizpilot.documents.extraction.TextExtractionService;
import com.bizpilot.documents.repository.DocumentRepository;
import com.bizpilot.documents.service.DocumentStorageService;
import com.bizpilot.organization.entity.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito orchestration tests — {@code process(UUID)} is {@code
 * @Async} only when invoked through a real Spring proxy; instantiated
 * directly here, it runs synchronously on the calling thread, which is
 * exactly what makes deterministic assertions possible.
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private TextExtractionService textExtractionService;

    @Mock
    private DocumentChunkingService documentChunkingService;

    @Mock
    private AiEmbeddingService aiEmbeddingService;

    @Mock
    private DocumentProcessingResultService documentProcessingResultService;

    private final Organization organization = new Organization("Acme Corp");
    private final UUID documentId = UUID.randomUUID();

    private DocumentProcessingService service() {
        return new DocumentProcessingService(documentRepository, documentStorageService, textExtractionService,
                documentChunkingService, aiEmbeddingService, documentProcessingResultService);
    }

    private Document document() {
        return new Document(organization, "contract.pdf", "organizations/x/documents/y",
                "application/pdf", 1024, UUID.randomUUID());
    }

    @BeforeEach
    void stubTransitionSucceeds() {
        when(documentRepository.transitionToProcessing(documentId)).thenReturn(1);
    }

    @Test
    void skipsProcessingWhenTheAtomicTransitionClaimsNoRow() {
        when(documentRepository.transitionToProcessing(documentId)).thenReturn(0);

        service().process(documentId);

        verify(documentRepository, never()).findById(any());
        verifyNoInteractions(textExtractionService, documentChunkingService, aiEmbeddingService,
                documentProcessingResultService);
    }

    @Test
    void doesNothingWhenTheDocumentIsGoneImmediatelyAfterBeingClaimed() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        service().process(documentId);

        verifyNoInteractions(textExtractionService, documentChunkingService, aiEmbeddingService,
                documentProcessingResultService);
    }

    @Test
    void extractsChunksEmbedsAndPersistsOnSuccess() {
        Document document = document();
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        List<TextChunk> chunks = List.of(new TextChunk(0, "chunk zero"), new TextChunk(1, "chunk one"));
        List<float[]> embeddings = List.of(new float[]{0.1f}, new float[]{0.2f});

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorageService.load(document.getStorageKey())).thenReturn(resource);
        when(textExtractionService.extract(eq("application/pdf"), eq(resource))).thenReturn("some extracted text");
        when(documentChunkingService.split("some extracted text")).thenReturn(chunks);
        when(aiEmbeddingService.embedBatch(List.of("chunk zero", "chunk one"))).thenReturn(embeddings);

        service().process(documentId);

        verify(documentProcessingResultService).persistSuccess(documentId, organization.getId(),
                "contract.pdf", "application/pdf", chunks, embeddings);
        verify(documentProcessingResultService, never()).markFailed(any(), any());
    }

    @Test
    void marksFailedWhenExtractionThrows() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorageService.load(anyString())).thenReturn(new ByteArrayResource(new byte[0]));
        when(textExtractionService.extract(anyString(), any()))
                .thenThrow(new DocumentProcessingException("Failed to extract text from PDF content"));

        service().process(documentId);

        verify(documentProcessingResultService).markFailed(eq(documentId), any(DocumentProcessingException.class));
        verify(documentProcessingResultService, never()).persistSuccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void marksFailedWhenExtractedTextIsBlank() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorageService.load(anyString())).thenReturn(new ByteArrayResource(new byte[0]));
        when(textExtractionService.extract(anyString(), any())).thenReturn("   ");

        service().process(documentId);

        verify(documentProcessingResultService).markFailed(eq(documentId), any(DocumentProcessingException.class));
        verify(documentChunkingService, never()).split(any());
    }

    @Test
    void marksFailedWhenEmbeddingThrows() {
        Document document = document();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentStorageService.load(anyString())).thenReturn(new ByteArrayResource(new byte[0]));
        when(textExtractionService.extract(anyString(), any())).thenReturn("some text");
        when(documentChunkingService.split("some text")).thenReturn(List.of(new TextChunk(0, "some text")));
        when(aiEmbeddingService.embedBatch(anyList()))
                .thenThrow(new com.bizpilot.ai.exception.AiProviderException("AI embedding request failed",
                        new RuntimeException("boom")));

        service().process(documentId);

        verify(documentProcessingResultService).markFailed(eq(documentId), any());
        verify(documentProcessingResultService, never()).persistSuccess(any(), any(), any(), any(), any(), any());
    }
}
