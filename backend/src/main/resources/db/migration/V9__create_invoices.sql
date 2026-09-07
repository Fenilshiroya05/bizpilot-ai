-- Phase 11: invoices + invoice_items, plus INVOICE_* RBAC permissions.
--
-- CLAUDE.md §9 lists its permission catalog under the heading "Examples:" —
-- the same precedent used to add PRODUCT_* (Phase 9, V7). Adding INVOICE_*
-- here extends that catalog rather than violating it. This migration ONLY
-- inserts new permission rows and role_permission mappings for those new
-- permissions — it never touches the rows/pairs V4 or V7 already committed.
-- Every INSERT below is scoped to `p.name IN ('INVOICE_...')` specifically
-- so it can never collide with the existing role_permissions composite
-- primary key.

INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'INVOICE_READ', now(), now()),
    (gen_random_uuid(), 'INVOICE_CREATE', now(), now()),
    (gen_random_uuid(), 'INVOICE_UPDATE', now(), now()),
    (gen_random_uuid(), 'INVOICE_DELETE', now(), now());

-- Role -> permission mapping, mirroring the same philosophy documented for
-- Customers/Leads/Products/Quotations (docs/security.md): OWNER/ADMIN/MANAGER
-- get full CRUD, SALES gets CRUD minus delete, EMPLOYEE gets read-only.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('INVOICE_READ', 'INVOICE_CREATE', 'INVOICE_UPDATE', 'INVOICE_DELETE')
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('INVOICE_READ', 'INVOICE_CREATE', 'INVOICE_UPDATE')
  AND r.name = 'SALES';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'INVOICE_READ'
  AND r.name = 'EMPLOYEE';

-- Cross-tenant FK integrity note (same as V8's documented reasoning for
-- quotations): a plain FK (customer_id/product_id REFERENCING
-- customers/products) only guarantees the referenced row exists
-- *somewhere* — it does NOT by itself prevent an invoice in Organization A
-- from referencing a customer/product belonging to Organization B, since
-- organization_id is not part of the referenced primary key. This is why
-- sales.service.InvoiceService validates both references via the
-- tenant-safe findByIdAndOrganizationId pattern (rejecting cross-org
-- references, and also archived customers / inactive products, at the
-- application layer via InvalidCustomerReferenceException /
-- InvalidProductReferenceException — the same exception classes already
-- used by QuotationService) BEFORE ever persisting an invoice/item.
--
-- No discount columns: CLAUDE.md §15 never mentions an invoice discount
-- (unlike quotations, §14) — total = subtotal + tax_amount only.
--
-- No quotation_id column: CLAUDE.md never describes converting a quotation
-- into an invoice — invoices are created independently.

CREATE TABLE invoices (
    id              UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    customer_id     UUID NOT NULL REFERENCES customers (id),
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED')),
    due_date        DATE,
    -- Backend-calculated totals — never trusted from the client; persisted
    -- so historical totals remain stable, per sales.entity.Invoice's Javadoc.
    subtotal        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_invoices_organization_id ON invoices (organization_id);
CREATE INDEX ix_invoices_organization_id_status ON invoices (organization_id, status);
CREATE INDEX ix_invoices_customer_id ON invoices (customer_id);
CREATE INDEX ix_invoices_due_date ON invoices (due_date);

CREATE TABLE invoice_items (
    id                     UUID PRIMARY KEY,
    invoice_id             UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    product_id             UUID NOT NULL REFERENCES products (id),
    -- Product snapshot rule (mirrors quotation_items): name/unit_price/tax_percentage
    -- are copied from the product at item-creation time and never re-read
    -- from it afterward, so a later catalog price change can never silently
    -- alter an existing invoice's historical values. Once the parent invoice
    -- leaves DRAFT, these are permanently frozen (enforced at the service
    -- layer, since InvoiceService.update rejects any item replacement on a
    -- non-DRAFT invoice).
    product_name_snapshot  VARCHAR(255) NOT NULL,
    quantity               NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    unit_price             NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    tax_percentage         NUMERIC(5, 2) NOT NULL CHECK (tax_percentage >= 0 AND tax_percentage <= 100),
    line_subtotal          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    line_tax_amount        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_invoice_items_invoice_id ON invoice_items (invoice_id);
CREATE INDEX ix_invoice_items_product_id ON invoice_items (product_id);
