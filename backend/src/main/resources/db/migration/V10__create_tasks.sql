-- Phase 12: tasks, plus TASK_* RBAC permissions.
--
-- CLAUDE.md §9 lists its permission catalog under the heading "Examples:" —
-- the same precedent used to add PRODUCT_* (Phase 9, V7) and INVOICE_*
-- (Phase 11, V9). Adding TASK_* here extends that catalog rather than
-- violating it. This migration ONLY inserts new permission rows and
-- role_permission mappings for those new permissions — it never touches the
-- rows/pairs V4/V7/V9 already committed.

INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'TASK_READ', now(), now()),
    (gen_random_uuid(), 'TASK_CREATE', now(), now()),
    (gen_random_uuid(), 'TASK_UPDATE', now(), now()),
    (gen_random_uuid(), 'TASK_DELETE', now(), now());

-- Role -> permission mapping (approved Phase 12 decision — deliberately
-- different from the Customer/Lead/Quotation/Product/Invoice default of
-- "SALES gets CRUD minus delete, EMPLOYEE gets read-only"): a Task is a
-- general-purpose operational to-do, not a sales/finance document, so every
-- role including EMPLOYEE gets READ/CREATE/UPDATE; only cancellation
-- (TASK_DELETE) is restricted to OWNER/ADMIN/MANAGER.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name IN ('TASK_READ', 'TASK_CREATE', 'TASK_UPDATE')
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES', 'EMPLOYEE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'TASK_DELETE'
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER');

-- Cross-tenant FK integrity note (same as V8/V9's documented reasoning for
-- quotations/invoices): assigned_to_user_id/customer_id/lead_id are plain
-- UUID columns with DB-level FKs (users/customers/leads), not JPA
-- relationships — mirroring leads.assigned_to_user_id (Phase 8, V6). A
-- plain FK only guarantees the referenced row exists *somewhere*;
-- organization_id is not part of any of their referenced primary keys, so
-- cross-tenant integrity is enforced at the application layer in
-- tasks.service.TaskService via the tenant-safe findByIdAndOrganizationId
-- pattern, exactly like every other cross-module reference since Phase 8.
--
-- No task_activities/task_comments/task_history table: CLAUDE.md §23 lists
-- a single "Notes" feature bullet with no accompanying "activities"/
-- "history" bullet (contrast leads/lead_activities, customers/
-- customer_activities) — notes is a single plain column here.

CREATE TABLE tasks (
    id                  UUID PRIMARY KEY,
    organization_id     UUID NOT NULL REFERENCES organizations (id),
    title               VARCHAR(255) NOT NULL,
    description         VARCHAR(2000),
    status              VARCHAR(20) NOT NULL
                            CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    priority            VARCHAR(10) NOT NULL
                            CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    assigned_to_user_id UUID REFERENCES users (id),
    customer_id         UUID REFERENCES customers (id),
    lead_id             UUID REFERENCES leads (id),
    due_date            DATE,
    notes               VARCHAR(2000),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_tasks_organization_id ON tasks (organization_id);
CREATE INDEX ix_tasks_organization_id_status ON tasks (organization_id, status);
CREATE INDEX ix_tasks_assigned_to_user_id ON tasks (assigned_to_user_id);
CREATE INDEX ix_tasks_customer_id ON tasks (customer_id);
CREATE INDEX ix_tasks_lead_id ON tasks (lead_id);
CREATE INDEX ix_tasks_due_date ON tasks (due_date);
