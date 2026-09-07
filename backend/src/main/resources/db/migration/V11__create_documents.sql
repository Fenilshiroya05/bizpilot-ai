-- Phase 13: documents, plus DOCUMENT_DELETE RBAC permission.
--
-- DOCUMENT_READ and DOCUMENT_UPLOAD already exist — CLAUDE.md §9's original
-- permission catalog named them explicitly (unlike PRODUCT_*/INVOICE_*/
-- TASK_*, which all had to be added later), and V4 (Phase 6) already seeded
-- their role mapping. This migration does NOT touch those existing rows.
-- DOCUMENT_UPDATE is intentionally never added — CLAUDE.md §16 defines no
-- metadata-update feature for documents, and Phase 13 implements no
-- PATCH endpoint.
--
-- DOCUMENT_DELETE did not exist and is added here, scoped by
-- p.name = 'DOCUMENT_DELETE' so this migration can never collide with
-- V4/V7/V9/V10's existing rows.
INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'DOCUMENT_DELETE', now(), now());

-- Role -> permission mapping (approved Phase 13 decision): unlike
-- DOCUMENT_READ/DOCUMENT_UPLOAD (SALES also has upload), DOCUMENT_DELETE is
-- restricted to OWNER/ADMIN/MANAGER only — documents may contain sensitive
-- business files, so delete access is deliberately narrower than Task's
-- unusually permissive mapping (Phase 12), not copied from it.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'DOCUMENT_DELETE'
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER');

-- No cross-tenant reference validation is required for documents:
-- CLAUDE.md §16 defines no association with Customer/Lead/Quotation/
-- Invoice/Task at all (unlike every other module so far) — a document is a
-- standalone, organization-owned file. uploaded_by_user_id is a plain UUID
-- attribution reference (mirrors leads.assigned_to_user_id, Phase 8, V6),
-- not a JPA relationship, and is never used for authorization — DOCUMENT_*
-- permissions alone govern access to any document in the organization.
--
-- Binary content is never stored in this table (CLAUDE.md §16, explicit
-- prohibition) — storage_key is an opaque, server-generated locator
-- resolved through documents.service.DocumentStorageService (Phase 13:
-- local filesystem only). It is namespaced per organization
-- ("organizations/{organizationId}/documents/{uuid}") and never derived
-- from original_filename, which is stored purely as inert display metadata
-- and never used to construct a filesystem path.

CREATE TABLE documents (
    id                   UUID PRIMARY KEY,
    organization_id      UUID NOT NULL REFERENCES organizations (id),
    original_filename    VARCHAR(255) NOT NULL,
    storage_key          VARCHAR(500) NOT NULL,
    content_type         VARCHAR(100) NOT NULL
                             CHECK (content_type IN (
                                 'application/pdf',
                                 'text/plain',
                                 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
                             )),
    file_size            BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 20971520), -- 20 MB
    status               VARCHAR(20) NOT NULL
                             CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED')),
    uploaded_by_user_id  UUID REFERENCES users (id),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_documents_storage_key ON documents (storage_key);
CREATE INDEX ix_documents_organization_id ON documents (organization_id);
CREATE INDEX ix_documents_organization_id_status ON documents (organization_id, status);
CREATE INDEX ix_documents_uploaded_by_user_id ON documents (uploaded_by_user_id);
CREATE INDEX ix_documents_content_type ON documents (content_type);
