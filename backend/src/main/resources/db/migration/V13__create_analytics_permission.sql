-- Phase 19: Analytics summary API, plus the ANALYTICS_READ RBAC permission.
--
-- CLAUDE.md §9 lists its permission catalog under the heading "Examples:" —
-- the same precedent already used to justify PRODUCT_*/INVOICE_*/TASK_*/
-- DOCUMENT_* in V7/V9/V10/V11 applies here: adding ANALYTICS_READ extends
-- that catalog rather than violating it.
--
-- This migration ONLY inserts one new permission row and its role_permission
-- mappings — it never touches any row V4/V7/V9/V10/V11 already committed.
-- The INSERT below is scoped to `p.name = 'ANALYTICS_READ'` specifically so
-- it can never collide with the existing role_permissions composite primary
-- key.
--
-- No new table, no new column, no analytics persistence of any kind — the
-- Phase 19 summary endpoint computes everything on demand from existing
-- business tables (docs/architecture.md §1r).

INSERT INTO permissions (id, name, created_at, updated_at) VALUES
    (gen_random_uuid(), 'ANALYTICS_READ', now(), now());

-- Unlike every prior phase's role-differentiated CRUD mapping, ANALYTICS_READ
-- is granted to all five existing roles (locked Phase 19 decision) — a
-- read-only business summary is treated as broadly visible information,
-- the same "granted to every role" precedent already used for AI_USE and
-- DOCUMENT_READ (V4/V11).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE p.name = 'ANALYTICS_READ'
  AND r.name IN ('OWNER', 'ADMIN', 'MANAGER', 'SALES', 'EMPLOYEE');
