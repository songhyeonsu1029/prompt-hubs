package com.mediaproject.prompthubs.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid input value"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "Internal server error"),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "Entity not found"),

    // Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "Invalid email or password"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "Invalid or expired token"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A003", "Token has expired"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A004", "Unauthorized access"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A005", "Access denied"),

    // Account
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AC001", "Email already exists"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "AC002", "Account not found"),

    // Workspace
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "W001", "Workspace not found"),
    WORKSPACE_SLUG_EXISTS(HttpStatus.CONFLICT, "W002", "Workspace slug already exists"),
    NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "W003", "Not a member of this workspace"),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "W004", "Insufficient permission"),

    // Plan Limit
    PLAN_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "P001", "Plan limit exceeded"),

    // Integration (Phase 2)
    INTEGRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "Integration not found"),
    INTEGRATION_ALREADY_CONNECTED(HttpStatus.CONFLICT, "I002", "Integration already connected"),
    INTEGRATION_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "I003", "This integration is disabled on the server"),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "I004", "Invalid or expired API key"),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "I005", "External API call failed"),
    PRE_PROMPTING_INCOMPLETE(HttpStatus.BAD_REQUEST, "I006", "Success criteria or validation method is empty"),
    INTEGRATION_OAUTH_FAILED(HttpStatus.BAD_REQUEST, "I007", "OAuth authorization failed");

    private final HttpStatus status;
    private final String code;
    private final String message;
}