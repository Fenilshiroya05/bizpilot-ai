package com.bizpilot.documents.controller;

import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.DocumentStatus;
import com.bizpilot.documents.mapper.DocumentMapper;
import com.bizpilot.documents.service.DocumentContent;
import com.bizpilot.documents.service.DocumentSearchCriteria;
import com.bizpilot.documents.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Document management (CLAUDE.md §16). Every endpoint requires the matching
 * {@code DOCUMENT_*} permission; the organization is always resolved
 * server-side from the authenticated tenant context inside
 * {@code DocumentService} — no endpoint here accepts an organization id
 * from the client in any form. Mirrors {@code TaskController}/
 * {@code LeadController} in structure.
 *
 * <p>No storage key, filesystem path, or organization id is ever returned
 * by any endpoint (project instructions §26) — see {@code DocumentResponse}.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentMapper documentMapper;

    public DocumentController(DocumentService documentService, DocumentMapper documentMapper) {
        this.documentService = documentService;
        this.documentMapper = documentMapper;
    }

    /**
     * Upload — {@code multipart/form-data} with a single {@code file} part.
     * No JSON metadata is accepted alongside it (CLAUDE.md §16 defines no
     * document field beyond what's derived from the file itself).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentMapper.toResponse(documentService.upload(file));
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentMapper.toResponse(documentService.getById(id));
    }

    /**
     * Listing/search/filter/pagination in one endpoint, mirroring
     * {@code TaskController.search}. {@code q} performs a case-insensitive
     * partial match against {@code originalFilename}. Sorting/pagination
     * use Spring Data's standard {@code page}/{@code size}/{@code sort}
     * parameters, applied at the database level.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public Page<DocumentResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) UUID uploadedByUserId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DocumentSearchCriteria criteria = new DocumentSearchCriteria(status, contentType, uploadedByUserId, q);
        return documentService.search(criteria, pageable).map(documentMapper::toResponse);
    }

    /**
     * Streams the file content — never loaded fully into a {@code byte[]}
     * response body; {@link Resource} is written by Spring's
     * {@code ResourceHttpMessageConverter} directly from the underlying
     * file. {@link ContentDisposition#filename(String, java.nio.charset.Charset)}
     * safely encodes the original filename (RFC 6266), which also closes
     * off CR/LF header-injection — the filename was already sanitized at
     * upload time, so this is defense in depth, not the only control.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('DOCUMENT_READ')")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        DocumentContent content = documentService.download(id);
        String contentDisposition = ContentDisposition.attachment()
                .filename(content.document().getOriginalFilename(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(content.resource());
    }

    /**
     * Hard delete — CLAUDE.md §16 mandates no DB BLOB storage and no soft
     * delete for documents; the physical file and the metadata row are
     * both removed. Repeated calls are idempotent in *effect* (the document
     * is gone either way) but not in *response code*: the first call
     * returns {@code 204}, a subsequent call returns {@code 404} since the
     * resource genuinely no longer exists — unlike Task/Quotation/Invoice's
     * soft-cancel, which keeps returning {@code 204} because that row never
     * disappears.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DOCUMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
