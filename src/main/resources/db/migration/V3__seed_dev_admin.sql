-- Step 5 — seed baseline ADMIN for local docker MySQL after schema migrations.
-- Password (plain text): AdminChangeMe1! — documented in README; rotate for any non-local use.
-- Idempotent: safe to re-run when username 'admin' already exists (unique key).

INSERT INTO users (username, password_hash, role, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$Fk1RBtZ84ERy4CjzlaFGNeOJWSAtm0YJgmmvrNf0KNqogS6eG3KPG',
    'ADMIN',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
)
ON DUPLICATE KEY UPDATE username = username;
