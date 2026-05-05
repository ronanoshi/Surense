package com.surense.infra.persistence.auth.entity;

/**
 * Application roles. Step 6 maps these to JWT claims and method security.
 */
public enum UserRole {
    ADMIN,
    AGENT,
    CUSTOMER
}
