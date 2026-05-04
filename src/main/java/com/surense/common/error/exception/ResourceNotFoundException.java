package com.surense.common.error.exception;

import com.surense.common.error.ErrorCode;

/**
 * Thrown when a requested resource (user, customer, ticket, ...) does not
 * exist or is not accessible to the caller. Maps to HTTP 404.
 *
 * <p>Use the static factories ({@link #user(Object)}, {@link #ticket(Object)},
 * {@link #customer(Object)}) so domain code stays compact and message keys
 * stay consistent.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode code, Object... args) {
        super(code, args);
    }

    public static ResourceNotFoundException user(Object userId) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId);
    }

    public static ResourceNotFoundException customer(Object customerId) {
        return new ResourceNotFoundException(ErrorCode.CUSTOMER_NOT_FOUND, customerId);
    }

    public static ResourceNotFoundException ticket(Object ticketId) {
        return new ResourceNotFoundException(ErrorCode.TICKET_NOT_FOUND, ticketId);
    }

    public static ResourceNotFoundException generic() {
        return new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
