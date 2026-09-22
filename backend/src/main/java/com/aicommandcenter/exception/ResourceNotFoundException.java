package com.aicommandcenter.exception;

import org.springframework.http.HttpStatus;

/**
 * Used for both "does not exist" and "belongs to somebody else" so that the API
 * never leaks the existence of another user's resources (IDOR protection).
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " not found: " + id);
    }
}
