package com.bizpilot.documents.service;

import com.bizpilot.documents.entity.Document;
import org.springframework.core.io.Resource;

/**
 * Bundles a tenant-safe-resolved {@link Document}'s metadata with its
 * loaded, streamable content — returned by {@code DocumentService.download}
 * so the controller can build the response headers (filename, content
 * type) and body from a single service call, without a second lookup.
 */
public record DocumentContent(Document document, Resource resource) {
}
