-- Phase 10: quotations + quotation_items (CLAUDE.md §14).
--
-- No RBAC changes here: QUOTATION_READ/CREATE/UPDATE/DELETE were already
-- seeded by V4 (Phase 6), unlike PRODUCT_* (Phase 9, V7) which had to be
-- added — CLAUDE.md's original permission catalog already named these four.
--
-- Cross-tenant FK integrity note (project instructions §20): a plain FK
-- (customer_id/product_id REFERENCING customers/products) only guarantees
-- the referenced row exists *somewhere* — it does NOT by itself prevent a
-- quotation in Organization A from referencing a customer/product belonging
-- to Organization B, since organization_id is not part of the referenced
-- primary key. This is why sales.service.QuotationService validates both
-- references via the tenant-safe findByIdAndOrganizationId pattern
-- (rejecting cross-org references at the application layer, see
-- InvalidCustomerReferenceException/InvalidProductReferenceException)
-- BEFORE ever persisting a quotation/item — the FK alone is not, and cannot
-- be, a sufficient tenant-isolation control here.

CREATE TABLE quotations (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organizations (id),
    customer_id         UUID NOT NULL REFERENCES customers (id),
    status              VARCHAR(20) NOT NULL
                            CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    valid_until         DATE,
    discount_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0
                            CHECK (discount_percentage >= 0 AND discount_percentage <= 100),
    -- Backend-calculated totals (CLAUDE.md §14/§41) — never trusted from the
    -- client; persisted so historical totals remain stable, per
    -- sales.entity.Quotation's Javadoc.
    subtotal            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tax_amount          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_quotations_organization_id ON quotations (organization_id);
CREATE INDEX ix_quotations_organization_id_status ON quotations (organization_id, status);
CREATE INDEX ix_quotations_customer_id ON quotations (customer_id);
CREATE INDEX ix_quotations_valid_until ON quotations (valid_until);

CREATE TABLE quotation_items (
    id                     UUID PRIMARY KEY,
    quotation_id           UUID NOT NULL REFERENCES quotations (id) ON DELETE CASCADE,
    product_id             UUID NOT NULL REFERENCES products (id),
    -- Product snapshot rule (project instructions §5): name/unit_price/tax_percentage
    -- are copied from the product at item-creation time and never re-read
    -- from it afterward, so a later catalog price change can never silently
    -- alter an existing quotation's historical values.
    product_name_snapshot  VARCHAR(255) NOT NULL,
    quantity               NUMERIC(19, 4) NOT NULL CHECK (quantity > 0),
    unit_price             NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    tax_percentage         NUMERIC(5, 2) NOT NULL CHECK (tax_percentage >= 0 AND tax_percentage <= 100),
    line_subtotal          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    line_tax_amount        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_quotation_items_quotation_id ON quotation_items (quotation_id);
CREATE INDEX ix_quotation_items_product_id ON quotation_items (product_id);
