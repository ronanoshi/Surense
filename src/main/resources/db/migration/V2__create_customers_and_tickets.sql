-- Step 5 — CRM core tables (business APIs land in Step 7+).
-- customers: created by an AGENT (or ADMIN); optional link to a CUSTOMER user account.
-- tickets: belong to one customer; optional assignment to an agent.

CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    phone_number VARCHAR(50) NULL,
    user_id BIGINT NULL,
    created_by_agent_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_user_id UNIQUE (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_customers_created_by FOREIGN KEY (created_by_agent_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_to_agent_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tickets_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tickets_assigned_agent FOREIGN KEY (assigned_to_agent_id) REFERENCES users (id) ON DELETE SET NULL,
    KEY idx_tickets_customer_id (customer_id),
    KEY idx_tickets_status (status),
    KEY idx_tickets_assigned_to (assigned_to_agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
